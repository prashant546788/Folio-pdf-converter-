package com.example.data.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.model.ConversionSettings
import com.example.model.PageOrientation
import com.example.model.PageSizeOption
import com.example.model.PdfFontFamily
import com.example.model.PdfPageTheme
import com.example.model.PdfTextAlignment
import com.example.model.PdfTextColorPreset
import com.example.model.QueuedSourceItem
import com.example.model.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class GeneratedPdfInfo(
    val fileName: String,
    val filePath: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val fullText: String,
    val pageTextsJson: String
)

data class PdfSearchHit(
    val pageIndex: Int,
    val matchCount: Int,
    val snippet: String
)

class PdfConversionEngine(
    private val context: Context,
    private val parser: DocumentContentParser,
    private val ocrProcessor: OcrProcessor
) {
    private val pdfDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    suspend fun convertToPdf(
        items: List<QueuedSourceItem>,
        desiredOutputName: String,
        settings: ConversionSettings,
        onProgress: suspend (String) -> Unit = {}
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        val safeBase = sanitizeFileName(desiredOutputName.removeSuffix(".pdf"))
        val outFile = createUniquePdfFile(safeBase)
        val document = PdfDocument()
        val pageTexts = mutableListOf<String>()
        var pageNumber = 1

        try {
            for ((index, item) in items.withIndex()) {
                coroutineContext.ensureActive()
                onProgress("Processing ${index + 1}/${items.size}: ${item.displayName}")

                when (item.sourceKind) {
                    SourceKind.IMAGE -> {
                        val bitmap = parser.decodeBitmap(
                            uri = item.uri,
                            maxEdgePx = settings.quality.maxPixelEdge,
                            extraRotationDegrees = item.rotationDegrees
                        ) ?: throw IllegalStateException("Could not decode image: ${item.displayName}")

                        try {
                            val ocrResult = if (settings.enableOcr) {
                                onProgress("Running OCR on ${item.displayName}...")
                                ocrProcessor.recognizeBitmap(bitmap)
                            } else {
                                OcrPageResult("", emptyList())
                            }

                            val (pageW, pageH) = resolvePageDimensions(
                                settings = settings,
                                contentWidth = bitmap.width,
                                contentHeight = bitmap.height
                            )

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
                            val page = document.startPage(pageInfo)
                            drawImagePageWithOcr(
                                canvas = page.canvas,
                                bitmap = bitmap,
                                ocrResult = ocrResult,
                                pageW = pageW,
                                pageH = pageH,
                                marginPt = settings.margin.points,
                                pageNumber = if (settings.addPageNumbers) pageNumber else null,
                                settings = settings
                            )
                            document.finishPage(page)
                            pageTexts.add(ocrResult.fullText)
                            pageNumber++
                        } finally {
                            bitmap.recycle()
                        }
                    }

                    SourceKind.PDF -> {
                        val pfd = context.contentResolver.openFileDescriptor(item.uri, "r")
                            ?: throw IllegalStateException("Unable to open PDF: ${item.displayName}")
                        pfd.use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                val total = renderer.pageCount
                                val renderScale = settings.quality.targetDpi / 72f
                                for (p in 0 until total) {
                                    coroutineContext.ensureActive()
                                    onProgress("Rendering ${item.displayName} (page ${p + 1}/$total)...")
                                    renderer.openPage(p).use { srcPage ->
                                        val bmpW = (srcPage.width * renderScale).toInt().coerceIn(400, 2400)
                                        val bmpH = (srcPage.height * renderScale).toInt().coerceIn(400, 3200)
                                        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                        try {
                                            bmp.eraseColor(Color.WHITE)
                                            srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                            val ocrResult = if (settings.enableOcr) {
                                                ocrProcessor.recognizeBitmap(bmp)
                                            } else {
                                                OcrPageResult("", emptyList())
                                            }
                                            val (pageW, pageH) = resolvePageDimensions(
                                                settings = settings,
                                                contentWidth = srcPage.width,
                                                contentHeight = srcPage.height
                                            )
                                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
                                            val page = document.startPage(pageInfo)
                                            drawImagePageWithOcr(
                                                canvas = page.canvas,
                                                bitmap = bmp,
                                                ocrResult = ocrResult,
                                                pageW = pageW,
                                                pageH = pageH,
                                                marginPt = settings.margin.points,
                                                pageNumber = if (settings.addPageNumbers) pageNumber else null,
                                                settings = settings
                                            )
                                            document.finishPage(page)
                                            pageTexts.add(ocrResult.fullText)
                                            pageNumber++
                                        } finally {
                                            bmp.recycle()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        val blocks = parser.parseDocument(item.uri, item.sourceKind)
                        val generatedPages = renderDocumentBlocksToPages(
                            document = document,
                            blocks = blocks,
                            settings = settings,
                            startingPageNumber = pageNumber,
                            docTitle = item.displayName.substringBeforeLast('.')
                        )
                        pageTexts.addAll(generatedPages)
                        pageNumber += generatedPages.size
                    }
                }
            }

            if (pageNumber == 1) {
                throw IllegalStateException("No convertible pages were generated.")
            }

            FileOutputStream(outFile).use { fos ->
                document.writeTo(fos)
            }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            document.close()
        }

        val combinedText = pageTexts
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val jsonArray = JSONArray().apply {
            pageTexts.forEach { put(it) }
        }

        GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pageTexts.size,
            fileSizeBytes = outFile.length(),
            fullText = combinedText,
            pageTextsJson = jsonArray.toString()
        )
    }

    suspend fun convertMarkdownToPdf(
        title: String,
        markdownContent: String,
        settings: ConversionSettings
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        val safeBase = sanitizeFileName(title.removeSuffix(".pdf"))
        val outFile = createUniquePdfFile(safeBase)
        val document = PdfDocument()
        val pageTexts: List<String>

        try {
            val blocks = parser.parseMarkdown(markdownContent)
            pageTexts = renderDocumentBlocksToPages(
                document = document,
                blocks = blocks,
                settings = settings,
                startingPageNumber = 1,
                docTitle = title
            )
            FileOutputStream(outFile).use { fos ->
                document.writeTo(fos)
            }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            document.close()
        }

        val combinedText = pageTexts.filter { it.isNotBlank() }.joinToString("\n\n")
        val jsonArray = JSONArray().apply { pageTexts.forEach { put(it) } }
        GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pageTexts.size,
            fileSizeBytes = outFile.length(),
            fullText = combinedText,
            pageTextsJson = jsonArray.toString()
        )
    }

    private fun resolvePageDimensions(
        settings: ConversionSettings,
        contentWidth: Int,
        contentHeight: Int
    ): Pair<Int, Int> {
        if (settings.pageSize == PageSizeOption.FIT_TO_IMAGE && contentWidth > 0 && contentHeight > 0) {
            val aspect = contentHeight.toFloat() / contentWidth.toFloat()
            val targetW = 595
            val targetH = (targetW * aspect).toInt().coerceIn(280, 1400)
            return when (settings.orientation) {
                PageOrientation.PORTRAIT -> minOf(targetW, targetH) to maxOf(targetW, targetH)
                PageOrientation.LANDSCAPE -> maxOf(targetW, targetH) to minOf(targetW, targetH)
                PageOrientation.AUTO -> targetW to targetH
            }
        }

        val baseW = settings.pageSize.widthPt
        val baseH = settings.pageSize.heightPt
        val isLandscape = when (settings.orientation) {
            PageOrientation.PORTRAIT -> false
            PageOrientation.LANDSCAPE -> true
            PageOrientation.AUTO -> contentWidth > contentHeight * 1.05f
        }
        return if (isLandscape) baseH to baseW else baseW to baseH
    }

    private fun drawImagePageWithOcr(
        canvas: Canvas,
        bitmap: Bitmap,
        ocrResult: OcrPageResult,
        pageW: Int,
        pageH: Int,
        marginPt: Int,
        pageNumber: Int?,
        settings: ConversionSettings = ConversionSettings()
    ) {
        canvas.drawColor(settings.pageTheme.bgArgb)
        val footerReserve = if (pageNumber != null) 20f else 0f
        val availW = (pageW - marginPt * 2).coerceAtLeast(100).toFloat()
        val availH = (pageH - marginPt * 2 - footerReserve).coerceAtLeast(100f)

        val scale = minOf(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val left = marginPt + (availW - drawW) / 2f
        val top = marginPt + (availH - drawH) / 2f
        val destRect = RectF(left, top, left + drawW, top + drawH)

        // 1. Emit selectable & searchable OCR text layer underneath the scanned image.
        // Using alpha = 1 ensures Skia's PDF backend writes text glyph operators (BT/Tj/ET)
        // while keeping the scanned page visually identical.
        if (ocrResult.lines.isNotEmpty()) {
            val ocrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(1, 0, 0, 0)
                typeface = Typeface.SANS_SERIF
            }
            for (line in ocrResult.lines) {
                val words = if (line.words.isNotEmpty()) {
                    line.words
                } else {
                    listOf(OcrWordBox(line.text, line.boundingBox))
                }
                for (word in words) {
                    val box = word.boundingBox
                    val targetX = destRect.left + box.left * scale
                    val targetW = (box.width() * scale).coerceAtLeast(4f)
                    val targetH = (box.height() * scale).coerceAtLeast(5f)
                    ocrPaint.textSize = targetH * 0.88f
                    ocrPaint.textScaleX = 1f
                    val measured = ocrPaint.measureText(word.text).coerceAtLeast(1f)
                    ocrPaint.textScaleX = (targetW / measured).coerceIn(0.35f, 3.0f)
                    val baselineY = destRect.top + box.bottom * scale - (targetH * 0.12f)
                    canvas.drawText(word.text, targetX, baselineY, ocrPaint)
                }
            }
        }

        // 2. Render crisp scanned image bitmap over the OCR text layer
        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)

        // 3. Optional diagonal watermark
        if (settings.showWatermark && settings.watermarkText.isNotBlank()) {
            drawPageWatermark(canvas, pageW, pageH, settings.watermarkText, settings.textColor.mutedArgb)
        }

        // 4. Optional footer page number
        if (pageNumber != null) {
            drawPageFooter(canvas, pageW, pageH, pageNumber, settings.textColor.mutedArgb, resolveTypeface(settings.fontFamily))
        }
    }

    private fun renderDocumentBlocksToPages(
        document: PdfDocument,
        blocks: List<DocBlock>,
        settings: ConversionSettings,
        startingPageNumber: Int,
        docTitle: String
    ): List<String> {
        val (pageW, pageH) = when (settings.orientation) {
            PageOrientation.LANDSCAPE -> settings.pageSize.heightPt to settings.pageSize.widthPt
            else -> settings.pageSize.widthPt to settings.pageSize.heightPt
        }
        val margin = maxOf(settings.margin.points, 32).toFloat()
        val contentWidth = (pageW - margin * 2).toInt().coerceAtLeast(200)
        val maxY = pageH - margin - 24f

        val isDarkBg = settings.pageTheme == PdfPageTheme.DARK_OBSIDIAN
        val effectiveColors = if (isDarkBg && settings.textColor != PdfTextColorPreset.SNOW_INK) {
            PdfTextColorPreset.SNOW_INK
        } else {
            settings.textColor
        }

        val normalTypeface = resolveTypeface(settings.fontFamily, bold = false, italic = false)
        val boldTypeface = resolveTypeface(settings.fontFamily, bold = true, italic = false)
        val italicTypeface = resolveTypeface(settings.fontFamily, bold = false, italic = true)
        val monoTypeface = resolveTypeface(PdfFontFamily.MONO_CODE, bold = false, italic = false)

        val layoutAlignment = when (settings.textAlignment) {
            PdfTextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            PdfTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            PdfTextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveColors.headingArgb
            textSize = settings.fontSize.h1Sp
            typeface = boldTypeface
        }
        val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveColors.headingArgb
            textSize = settings.fontSize.h2Sp
            typeface = boldTypeface
        }
        val h3Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveColors.headingArgb
            textSize = settings.fontSize.h3Sp
            typeface = boldTypeface
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveColors.bodyArgb
            textSize = settings.fontSize.bodySp
            typeface = normalTypeface
        }
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveColors.mutedArgb
            textSize = (settings.fontSize.bodySp - 0.3f).coerceAtLeast(8f)
            typeface = italicTypeface
        }
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveColors.bodyArgb
            textSize = (settings.fontSize.bodySp - 1.2f).coerceAtLeast(7.8f)
            typeface = monoTypeface
        }
        val headerSubtlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = effectiveColors.mutedArgb
            textSize = 8.5f
            typeface = normalTypeface
        }
        val rulePaint = Paint().apply {
            color = settings.pageTheme.ruleArgb
            strokeWidth = 0.8f
        }
        val tableHeaderBgPaint = Paint().apply {
            color = settings.pageTheme.tableHeaderArgb
        }
        val tableStripeBgPaint = Paint().apply {
            color = settings.pageTheme.tableStripeArgb
        }

        val pageTexts = mutableListOf<String>()
        var currentPageNum = startingPageNumber
        var currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, currentPageNum).create())
        var canvas = currentPage.canvas

        fun preparePageCanvas() {
            canvas.drawColor(settings.pageTheme.bgArgb)
            if (settings.includeHeaderTitle && docTitle.isNotBlank()) {
                canvas.drawText(docTitle, margin, margin - 10f, headerSubtlePaint)
            }
            if (settings.showWatermark && settings.watermarkText.isNotBlank()) {
                drawPageWatermark(canvas, pageW, pageH, settings.watermarkText, effectiveColors.mutedArgb)
            }
        }

        preparePageCanvas()
        var cursorY = margin + 4f
        val currentPageText = StringBuilder()

        fun startNextPage() {
            if (settings.addPageNumbers) {
                drawPageFooter(canvas, pageW, pageH, currentPageNum, effectiveColors.mutedArgb, normalTypeface)
            }
            document.finishPage(currentPage)
            pageTexts.add(currentPageText.toString().trim())
            currentPageText.clear()
            currentPageNum++
            currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, currentPageNum).create())
            canvas = currentPage.canvas
            preparePageCanvas()
            cursorY = margin + 4f
        }

        var csvRowCounter = 0

        for (block in blocks) {
            when (block) {
                is DocBlock.Divider -> {
                    if (cursorY + 14f > maxY) startNextPage()
                    cursorY += 6f
                    canvas.drawLine(margin, cursorY, pageW - margin, cursorY, rulePaint)
                    cursorY += 10f
                }

                is DocBlock.TableRow -> {
                    val colCount = block.cells.size.coerceAtLeast(1)
                    val colWidth = contentWidth.toFloat() / colCount
                    val cellPaint = if (block.isHeader) h3Paint else bodyPaint
                    val rowHeight = (settings.fontSize.bodySp * 2.1f).coerceAtLeast(20f)
                    if (cursorY + rowHeight > maxY) startNextPage()

                    if (block.isHeader) {
                        canvas.drawRect(margin, cursorY, pageW - margin, cursorY + rowHeight, tableHeaderBgPaint)
                    } else if (csvRowCounter % 2 == 1) {
                        canvas.drawRect(margin, cursorY, pageW - margin, cursorY + rowHeight, tableStripeBgPaint)
                    }
                    csvRowCounter++

                    block.cells.forEachIndexed { colIdx, cell ->
                        val cellX = margin + colIdx * colWidth + 4f
                        val clipped = if (cell.length > 26) cell.take(24) + "…" else cell
                        canvas.drawText(clipped, cellX, cursorY + rowHeight * 0.68f, cellPaint)
                    }
                    canvas.drawLine(margin, cursorY + rowHeight, pageW - margin, cursorY + rowHeight, rulePaint)
                    cursorY += rowHeight
                    currentPageText.append(block.cells.joinToString(" | ")).append('\n')
                }

                is DocBlock.RichHtml -> {
                    val layout = StaticLayout.Builder
                        .obtain(block.spanned, 0, block.spanned.length, bodyPaint, contentWidth)
                        .setAlignment(layoutAlignment)
                        .setLineSpacing(2f, settings.lineSpacing.multiplier)
                        .build()

                    for (lineIdx in 0 until layout.lineCount) {
                        val lineH = (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)).toFloat()
                        if (cursorY + lineH > maxY) {
                            startNextPage()
                        }
                        val lineStart = layout.getLineStart(lineIdx)
                        val lineEnd = layout.getLineEnd(lineIdx)
                        val lineText = block.spanned.subSequence(lineStart, lineEnd).toString()
                        val baseline = cursorY + (layout.getLineBaseline(lineIdx) - layout.getLineTop(lineIdx))
                        val lineX = margin + layout.getLineLeft(lineIdx)
                        canvas.drawText(lineText.trimEnd('\n'), lineX, baseline, bodyPaint)
                        cursorY += lineH
                        currentPageText.append(lineText)
                    }
                }

                else -> {
                    val (text, paint, indent, extraBefore, extraAfter) = when (block) {
                        is DocBlock.Heading -> when (block.level) {
                            1 -> Tuple5(block.text, h1Paint, 0f, 8f, 6f)
                            2 -> Tuple5(block.text, h2Paint, 0f, 7f, 5f)
                            else -> Tuple5(block.text, h3Paint, 0f, 5f, 4f)
                        }
                        is DocBlock.Bullet -> Tuple5("•  ${block.text}", bodyPaint, 10f, 1f, 3f)
                        is DocBlock.Quote -> Tuple5("│  ${block.text}", quotePaint, 12f, 2f, 4f)
                        is DocBlock.CodeLine -> Tuple5(block.text, codePaint, 8f, 0f, 2f)
                        is DocBlock.Paragraph -> Tuple5(block.text, bodyPaint, 0f, 0f, 3f)
                        else -> Tuple5("", bodyPaint, 0f, 0f, 0f)
                    }

                    if (text.isEmpty()) {
                        cursorY += 8f
                        continue
                    }

                    val availW = (contentWidth - indent.toInt()).coerceAtLeast(100)
                    val layout = StaticLayout.Builder
                        .obtain(text, 0, text.length, paint, availW)
                        .setAlignment(layoutAlignment)
                        .setLineSpacing(1.5f, settings.lineSpacing.multiplier)
                        .build()

                    cursorY += extraBefore
                    for (lineIdx in 0 until layout.lineCount) {
                        val lineH = (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)).toFloat()
                        if (cursorY + lineH > maxY) {
                            startNextPage()
                        }
                        val lineStart = layout.getLineStart(lineIdx)
                        val lineEnd = layout.getLineEnd(lineIdx)
                        val lineStr = text.substring(lineStart, lineEnd).trimEnd('\n')
                        val baseline = cursorY + (layout.getLineBaseline(lineIdx) - layout.getLineTop(lineIdx))
                        val lineX = margin + indent + layout.getLineLeft(lineIdx)
                        canvas.drawText(lineStr, lineX, baseline, paint)
                        cursorY += lineH
                    }
                    cursorY += extraAfter
                    currentPageText.append(text).append('\n')
                }
            }
        }

        if (settings.addPageNumbers) {
            drawPageFooter(canvas, pageW, pageH, currentPageNum, effectiveColors.mutedArgb, normalTypeface)
        }
        document.finishPage(currentPage)
        pageTexts.add(currentPageText.toString().trim())
        return pageTexts
    }

    private fun resolveTypeface(
        family: PdfFontFamily,
        bold: Boolean = false,
        italic: Boolean = false
    ): Typeface {
        val resId = when (family) {
            PdfFontFamily.CLASSIC_SANS -> R.font.inter
            PdfFontFamily.EDITORIAL_SERIF -> R.font.merriweather
            PdfFontFamily.LITERARY_LORA -> R.font.lora
            PdfFontFamily.DISPLAY_PLAYFAIR -> R.font.playfair_display
            PdfFontFamily.MONO_CODE -> R.font.jetbrains_mono
        }
        val base = runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
            ?: when (family) {
                PdfFontFamily.CLASSIC_SANS -> Typeface.SANS_SERIF
                PdfFontFamily.EDITORIAL_SERIF, PdfFontFamily.LITERARY_LORA, PdfFontFamily.DISPLAY_PLAYFAIR -> Typeface.SERIF
                PdfFontFamily.MONO_CODE -> Typeface.MONOSPACE
            }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return if (style == Typeface.NORMAL) base else Typeface.create(base, style)
    }

    private fun drawPageWatermark(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        watermarkText: String,
        mutedColor: Int
    ) {
        val clean = watermarkText.trim().take(32)
        if (clean.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(36, Color.red(mutedColor), Color.green(mutedColor), Color.blue(mutedColor))
            textSize = (pageW * 0.085f).coerceIn(28f, 54f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(-34f, pageW / 2f, pageH / 2f)
        canvas.drawText(clean, pageW / 2f, pageH / 2f, paint)
        canvas.restore()
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    private fun drawPageFooter(
        canvas: Canvas,
        pageW: Int,
        pageH: Int,
        pageNumber: Int,
        mutedColor: Int = Color.rgb(120, 124, 132),
        footerTypeface: Typeface = Typeface.SANS_SERIF
    ) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = 9f
            textAlign = Paint.Align.CENTER
            typeface = footerTypeface
        }
        canvas.drawText("Page $pageNumber", pageW / 2f, pageH - 12f, footerPaint)
    }

    private fun createUniquePdfFile(baseName: String): File {
        var candidate = File(pdfDir, "$baseName.pdf")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(pdfDir, "${baseName}_$counter.pdf")
            counter++
        }
        return candidate
    }

    fun sanitizeFileName(raw: String): String {
        val cleaned = raw
            .trim()
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (cleaned.isBlank()) "Folio_${System.currentTimeMillis()}" else cleaned.take(64)
    }

    suspend fun exportPdfToUri(sourcePath: String, destinationUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val file = File(sourcePath)
            if (!file.exists()) return@withContext false
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            } != null
        }

    suspend fun savePdfToDownloads(sourcePath: String, displayName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(sourcePath)
                if (!file.exists()) error("PDF file missing")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/FolioPDF")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Failed to create Downloads entry")
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(file).use { input -> input.copyTo(out) }
                    } ?: error("Failed to write Downloads file")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "Downloads/FolioPDF/$displayName"
                } else {
                    error("Use 'Save As' on Android 9 or earlier")
                }
            }
        }

    suspend fun renderPdfPage(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                renderer.openPage(pageIndex).use { page ->
                    val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                    val bmpW = targetWidthPx.coerceIn(300, 2200)
                    val bmpH = (bmpW * ratio).toInt().coerceIn(300, 3200)
                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    suspend fun searchPdf(
        filePath: String,
        query: String,
        pageTextsJson: String
    ): List<PdfSearchHit> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val cachedPages = parsePageTexts(pageTextsJson)
        val hits = mutableListOf<PdfSearchHit>()

        // On Android 15+ (API 35+), query native PdfRenderer.Page.searchText directly too!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val file = File(filePath)
            if (file.exists()) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    pfd.use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val nativeMatches = page.searchText(trimmed)
                                    val cachedText = cachedPages.getOrNull(i).orEmpty()
                                    val fallbackMatches = countOccurrences(cachedText, trimmed)
                                    val totalMatches = maxOf(nativeMatches.size, fallbackMatches)
                                    if (totalMatches > 0) {
                                        hits.add(
                                            PdfSearchHit(
                                                pageIndex = i,
                                                matchCount = totalMatches,
                                                snippet = extractSnippet(cachedText, trimmed)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    return@withContext hits
                } catch (_: Exception) {
                    // Fall through to cached text search
                }
            }
        }

        cachedPages.forEachIndexed { index, text ->
            val count = countOccurrences(text, trimmed)
            if (count > 0) {
                hits.add(
                    PdfSearchHit(
                        pageIndex = index,
                        matchCount = count,
                        snippet = extractSnippet(text, trimmed)
                    )
                )
            }
        }
        hits
    }

    fun parsePageTexts(pageTextsJson: String): List<String> {
        return try {
            val array = JSONArray(pageTextsJson)
            List(array.length()) { idx -> array.optString(idx, "") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (haystack.isBlank() || needle.isBlank()) return 0
        val lowerH = haystack.lowercase()
        val lowerN = needle.lowercase()
        var count = 0
        var idx = 0
        while (true) {
            val found = lowerH.indexOf(lowerN, idx)
            if (found < 0) break
            count++
            idx = found + lowerN.length
        }
        return count
    }

    private fun extractSnippet(text: String, query: String): String {
        if (text.isBlank()) return "Matched inside PDF content"
        val idx = text.lowercase().indexOf(query.lowercase())
        if (idx < 0) return text.take(90)
        val start = (idx - 28).coerceAtLeast(0)
        val end = (idx + query.length + 52).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace('\n', ' ') + suffix
    }
}
