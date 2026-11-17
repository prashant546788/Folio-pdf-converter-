package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.model.PrintColorMode
import com.example.model.PrintConfig
import com.example.model.PrintLayoutTool
import com.example.model.PrintOrientationMode
import com.example.model.PrintPageSubset
import com.example.model.PrintPagesPerSheet
import com.example.model.PrintPaperSize
import com.example.model.PrintScaleMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer as PdfBoxRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PrintSheet(
    val sheetIndex: Int,
    /** Source 0-based page indices placed on this sheet. -1 indicates an intentional blank slot (e.g. Booklet padding). */
    val pageSlots: List<Int>
)

data class PrintJobSummary(
    val totalSourcePages: Int,
    val selectedPageCount: Int,
    val sheets: List<PrintSheet>,
    val sheetWidthPt: Int,
    val sheetHeightPt: Int
)

class PdfPrintEngine(private val context: Context) {

    private val pdfDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    private fun ensurePdfBoxInit() {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
    }

    fun computeSheetPlan(
        totalPages: Int,
        config: PrintConfig
    ): PrintJobSummary {
        if (totalPages <= 0) {
            val (w, h) = resolveSheetPoints(config)
            return PrintJobSummary(0, 0, emptyList(), w, h)
        }

        val selectedPages = when (config.pageSubset) {
            PrintPageSubset.ALL -> (0 until totalPages).toList()
            PrintPageSubset.ODD_ONLY -> (0 until totalPages).filter { it % 2 == 0 } // 1-based odd
            PrintPageSubset.EVEN_ONLY -> (0 until totalPages).filter { it % 2 == 1 } // 1-based even
            PrintPageSubset.CUSTOM -> parsePageRange(config.customPageRange, totalPages)
        }

        val effectivePerSheet = when (config.layoutTool) {
            PrintLayoutTool.BOOKLET -> 2
            PrintLayoutTool.N_UP -> max(2, config.pagesPerSheet.count)
            PrintLayoutTool.STANDARD, PrintLayoutTool.POSTER_FIT -> config.pagesPerSheet.count
        }

        val orderedSlots: List<Int> = if (config.layoutTool == PrintLayoutTool.BOOKLET) {
            buildBookletSlotOrder(selectedPages)
        } else {
            selectedPages
        }

        val sheets = if (orderedSlots.isEmpty()) {
            emptyList()
        } else {
            orderedSlots.chunked(effectivePerSheet).mapIndexed { index, slots ->
                PrintSheet(sheetIndex = index, pageSlots = slots)
            }
        }

        val (sheetW, sheetH) = resolveSheetPoints(config, effectivePerSheet)
        return PrintJobSummary(
            totalSourcePages = totalPages,
            selectedPageCount = selectedPages.size,
            sheets = sheets,
            sheetWidthPt = sheetW,
            sheetHeightPt = sheetH
        )
    }

    fun buildBookletSlotOrder(selectedPages: List<Int>): List<Int> {
        if (selectedPages.isEmpty()) return emptyList()
        val count = selectedPages.size
        val paddedCount = ((count + 3) / 4) * 4
        val padded = List(paddedCount) { idx -> selectedPages.getOrNull(idx) ?: -1 }
        val slots = ArrayList<Int>(paddedCount)
        val pairs = paddedCount / 4
        for (i in 0 until pairs) {
            val low1 = 2 * i
            val low2 = 2 * i + 1
            val high1 = paddedCount - 1 - 2 * i
            val high2 = paddedCount - 2 - 2 * i
            // Front of folded sheet: [high1, low1]
            slots.add(padded[high1])
            slots.add(padded[low1])
            // Back of folded sheet: [low2, high2]
            slots.add(padded[low2])
            slots.add(padded[high2])
        }
        return slots
    }

    fun resolveSheetPoints(
        config: PrintConfig,
        effectivePerSheet: Int = when (config.layoutTool) {
            PrintLayoutTool.BOOKLET -> 2
            PrintLayoutTool.N_UP -> max(2, config.pagesPerSheet.count)
            else -> config.pagesPerSheet.count
        }
    ): Pair<Int, Int> {
        val baseW = config.paperSize.widthPt
        val baseH = config.paperSize.heightPt
        val useLandscape = when (config.orientation) {
            PrintOrientationMode.LANDSCAPE -> true
            PrintOrientationMode.PORTRAIT -> false
            PrintOrientationMode.AUTO -> effectivePerSheet == 2 || effectivePerSheet == 6
        }
        return if (useLandscape) max(baseW, baseH) to min(baseW, baseH)
        else min(baseW, baseH) to max(baseW, baseH)
    }

    fun parsePageRange(expr: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val trimmed = expr.trim().lowercase()
        if (trimmed.isBlank() || trimmed == "all") {
            return (0 until totalPages).toList()
        }
        val result = linkedSetOf<Int>()
        trimmed.split(",").forEach { token ->
            val part = token.trim()
            if (part.isEmpty()) return@forEach
            if (part.contains("-")) {
                val bounds = part.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (bounds.size == 2) {
                    val start = bounds[0].coerceIn(1, totalPages)
                    val end = bounds[1].coerceIn(1, totalPages)
                    val range = if (start <= end) start..end else end..start
                    range.forEach { pageOneBased -> result.add(pageOneBased - 1) }
                }
            } else {
                part.toIntOrNull()?.let { oneBased ->
                    if (oneBased in 1..totalPages) result.add(oneBased - 1)
                }
            }
        }
        return result.toList()
    }

    suspend fun renderSheetPreview(
        localFilePath: String?,
        uri: Uri?,
        documentTitle: String,
        sheet: PrintSheet,
        totalSheets: Int,
        config: PrintConfig,
        password: String?,
        targetWidthPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val targetPerSheet = when (config.layoutTool) {
            PrintLayoutTool.BOOKLET -> 2
            PrintLayoutTool.N_UP -> max(2, config.pagesPerSheet.count)
            else -> config.pagesPerSheet.count
        }
        val (sheetW, sheetH) = resolveSheetPoints(
            config = config,
            effectivePerSheet = targetPerSheet
        )
        val previewWidth = targetWidthPx.coerceIn(480, 1800)
        val previewHeight = ((previewWidth.toFloat() * sheetH) / sheetW.toFloat()).roundToInt().coerceAtLeast(240)
        val outBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        val scaleFromPt = previewWidth.toFloat() / sheetW.toFloat()

        canvas.save()
        canvas.scale(scaleFromPt, scaleFromPt)
        drawSheetOntoCanvas(
            canvas = canvas,
            sheetW = sheetW,
            sheetH = sheetH,
            sheet = sheet,
            totalSheets = totalSheets,
            documentTitle = documentTitle,
            config = config,
            renderSourcePage = { pageIdx, cellWidthPx, cellHeightPx ->
                renderSingleSourcePageBitmap(
                    localFilePath = localFilePath,
                    uri = uri,
                    pageIndex = pageIdx,
                    targetEdgePx = max(cellWidthPx, cellHeightPx).coerceIn(360, 1800),
                    password = password
                )
            }
        )
        canvas.restore()
        outBitmap
    }

    suspend fun generatePrintReadyPdf(
        localFilePath: String?,
        uri: Uri?,
        documentTitle: String,
        config: PrintConfig,
        password: String?,
        outputFile: File,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        val totalPages = countSourcePages(localFilePath, uri, password)
        if (totalPages <= 0) {
            throw IllegalStateException("PDF has no printable pages or requires unlocking.")
        }
        val plan = computeSheetPlan(totalPages, config)
        if (plan.sheets.isEmpty()) {
            throw IllegalStateException("No pages match the selected print page range or subset.")
        }

        val copyCount = config.copies.coerceIn(1, 10)
        val totalOutputPages = plan.sheets.size * copyCount
        val document = PdfDocument()
        try {
            val renderScale = (config.dpi / 72f).coerceIn(1.5f, 4.0f)
            val totalSheets = plan.sheets.size
            var outPageNum = 1
            for (copy in 0 until copyCount) {
                for (sheet in plan.sheets) {
                    coroutineContext.ensureActive()
                    onProgress(outPageNum, totalOutputPages)
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        plan.sheetWidthPt,
                        plan.sheetHeightPt,
                        outPageNum
                    ).create()
                    val page = document.startPage(pageInfo)
                    drawSheetOntoCanvas(
                        canvas = page.canvas,
                        sheetW = plan.sheetWidthPt,
                        sheetH = plan.sheetHeightPt,
                        sheet = sheet,
                        totalSheets = totalSheets,
                        documentTitle = documentTitle,
                        config = config,
                        renderSourcePage = { sourceIdx, cellW, cellH ->
                            val targetEdge = (max(cellW, cellH) * renderScale).roundToInt().coerceIn(720, 2600)
                            renderSingleSourcePageBitmap(
                                localFilePath = localFilePath,
                                uri = uri,
                                pageIndex = sourceIdx,
                                targetEdgePx = targetEdge,
                                password = password
                            )
                        }
                    )
                    document.finishPage(page)
                    outPageNum++
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
            }
        } finally {
            document.close()
        }

        GeneratedPdfInfo(
            fileName = outputFile.name,
            filePath = outputFile.absolutePath,
            pageCount = totalOutputPages,
            fileSizeBytes = outputFile.length(),
            fullText = "",
            pageTextsJson = "[]"
        )
    }

    fun createVaultPrintOutputFile(baseName: String, suffixLabel: String): File {
        val cleaned = baseName
            .removeSuffix(".pdf")
            .trim()
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifBlank { "Print_Layout" }
            .take(48)
        val tag = suffixLabel.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        var candidate = File(pdfDir, "${cleaned}_${tag}.pdf")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(pdfDir, "${cleaned}_${tag}_$counter.pdf")
            counter++
        }
        return candidate
    }

    fun launchSystemPrint(
        activityContext: Context,
        jobName: String,
        printReadyPdfFile: File,
        config: PrintConfig
    ) {
        val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: throw IllegalStateException("Android Print Service is unavailable on this device.")

        val mediaSizeBase = when (config.paperSize) {
            PrintPaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PrintPaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
            PrintPaperSize.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
            PrintPaperSize.A5 -> PrintAttributes.MediaSize.ISO_A5
            PrintPaperSize.PHOTO_4X6 -> PrintAttributes.MediaSize.NA_INDEX_4X6
        }
        val effectiveLandscape = when (config.orientation) {
            PrintOrientationMode.LANDSCAPE -> true
            PrintOrientationMode.PORTRAIT -> false
            PrintOrientationMode.AUTO ->
                config.layoutTool == PrintLayoutTool.BOOKLET ||
                    (config.layoutTool == PrintLayoutTool.N_UP &&
                        (config.pagesPerSheet == PrintPagesPerSheet.TWO || config.pagesPerSheet == PrintPagesPerSheet.SIX))
        }
        val orientedMediaSize = if (effectiveLandscape) mediaSizeBase.asLandscape() else mediaSizeBase.asPortrait()

        val colorAttr = if (config.colorMode == PrintColorMode.GRAYSCALE) {
            PrintAttributes.COLOR_MODE_MONOCHROME
        } else {
            PrintAttributes.COLOR_MODE_COLOR
        }

        val marginMils = ((config.marginPreset.points / 72f) * 1000f).roundToInt()
        val attributes = PrintAttributes.Builder()
            .setMediaSize(orientedMediaSize)
            .setColorMode(colorAttr)
            .setResolution(PrintAttributes.Resolution("folio_dpi", "Folio Print", config.dpi, config.dpi))
            .setMinMargins(PrintAttributes.Margins(marginMils, marginMils, marginMils, marginMils))
            .build()

        val adapter = FolioPrintDocumentAdapter(
            pdfFile = printReadyPdfFile,
            jobName = jobName
        )
        printManager.print(jobName, adapter, attributes)
    }

    private suspend fun drawSheetOntoCanvas(
        canvas: Canvas,
        sheetW: Int,
        sheetH: Int,
        sheet: PrintSheet,
        totalSheets: Int,
        documentTitle: String,
        config: PrintConfig,
        renderSourcePage: suspend (Int, Int, Int) -> Bitmap?
    ) {
        canvas.drawColor(Color.WHITE)

        val marginPt = if (config.layoutTool == PrintLayoutTool.POSTER_FIT) {
            0f
        } else {
            config.marginPreset.points.toFloat()
        }
        val headerReserve = if (config.showHeaderFooter) 16f else 0f
        val footerReserve = if (config.showHeaderFooter) 16f else 0f

        val contentLeft = marginPt
        val contentTop = marginPt + headerReserve
        val contentRight = (sheetW - marginPt).coerceAtLeast(contentLeft + 20f)
        val contentBottom = (sheetH - marginPt - footerReserve).coerceAtLeast(contentTop + 20f)
        val contentW = contentRight - contentLeft
        val contentH = contentBottom - contentTop

        val targetSlots = when (config.layoutTool) {
            PrintLayoutTool.BOOKLET -> 2
            PrintLayoutTool.N_UP -> max(2, config.pagesPerSheet.count)
            else -> config.pagesPerSheet.count
        }.coerceAtLeast(1)
        val (cols, rows) = when (targetSlots) {
            1 -> 1 to 1
            2 -> if (sheetW >= sheetH) 2 to 1 else 1 to 2
            4 -> 2 to 2
            6 -> if (sheetW >= sheetH) 3 to 2 else 2 to 3
            9 -> 3 to 3
            else -> 2 to 2
        }
        val cellGap = if (targetSlots > 1 && config.layoutTool != PrintLayoutTool.POSTER_FIT) 10f else 0f
        val cellW = (contentW - cellGap * (cols - 1)) / cols
        val cellH = (contentH - cellGap * (rows - 1)) / rows

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            if (config.colorMode == PrintColorMode.GRAYSCALE) {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                colorFilter = ColorMatrixColorFilter(cm)
            }
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(120, 110, 116, 128)
            strokeWidth = 0.75f
        }

        for (slotIndex in 0 until (cols * rows)) {
            val pageIdx = sheet.pageSlots.getOrNull(slotIndex) ?: -1
            val col = slotIndex % cols
            val row = slotIndex / cols
            val left = contentLeft + col * (cellW + cellGap)
            val top = contentTop + row * (cellH + cellGap)
            val cellRect = RectF(left, top, left + cellW, top + cellH)

            if (pageIdx >= 0) {
                val bmp = renderSourcePage(pageIdx, cellW.roundToInt(), cellH.roundToInt())
                if (bmp != null) {
                    try {
                        drawBitmapInCell(canvas, bmp, cellRect, config.scaleMode, bitmapPaint)
                    } finally {
                        bmp.recycle()
                    }
                }
            }
            if (config.showPageBorders && pageIdx >= 0) {
                canvas.drawRect(cellRect, borderPaint)
            }
        }

        if (config.showHeaderFooter) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 68, 72, 82)
                textSize = 8.5f
                typeface = Typeface.SANS_SERIF
            }
            val headerTitle = documentTitle.take(44)
            canvas.drawText(headerTitle, contentLeft, (marginPt + 10f).coerceAtLeast(11f), textPaint)

            val footerLabel = "Sheet ${sheet.sheetIndex + 1} of $totalSheets"
            val labelWidth = textPaint.measureText(footerLabel)
            canvas.drawText(
                footerLabel,
                (contentRight - labelWidth).coerceAtLeast(contentLeft),
                (sheetH - marginPt - 4f).coerceAtMost(sheetH - 4f),
                textPaint
            )
        }
    }

    private fun drawBitmapInCell(
        canvas: Canvas,
        bmp: Bitmap,
        cellRect: RectF,
        scaleMode: PrintScaleMode,
        paint: Paint
    ) {
        if (bmp.width <= 0 || bmp.height <= 0) return
        val srcRatio = bmp.width.toFloat() / bmp.height.toFloat()
        val dstRatio = cellRect.width() / cellRect.height()

        val destRect = when (scaleMode) {
            PrintScaleMode.FIT_PAGE -> {
                if (srcRatio > dstRatio) {
                    val drawnH = cellRect.width() / srcRatio
                    val dy = (cellRect.height() - drawnH) / 2f
                    RectF(cellRect.left, cellRect.top + dy, cellRect.right, cellRect.top + dy + drawnH)
                } else {
                    val drawnW = cellRect.height() * srcRatio
                    val dx = (cellRect.width() - drawnW) / 2f
                    RectF(cellRect.left + dx, cellRect.top, cellRect.left + dx + drawnW, cellRect.bottom)
                }
            }
            PrintScaleMode.FILL_PAGE -> {
                canvas.save()
                canvas.clipRect(cellRect)
                val drawnRect = if (srcRatio > dstRatio) {
                    val drawnW = cellRect.height() * srcRatio
                    val dx = (cellRect.width() - drawnW) / 2f
                    RectF(cellRect.left + dx, cellRect.top, cellRect.left + dx + drawnW, cellRect.bottom)
                } else {
                    val drawnH = cellRect.width() / srcRatio
                    val dy = (cellRect.height() - drawnH) / 2f
                    RectF(cellRect.left, cellRect.top + dy, cellRect.right, cellRect.top + dy + drawnH)
                }
                canvas.drawBitmap(bmp, null, drawnRect, paint)
                canvas.restore()
                return
            }
            PrintScaleMode.ACTUAL_SIZE -> {
                val scale = min(1f, min(cellRect.width() / bmp.width, cellRect.height() / bmp.height))
                val drawnW = (bmp.width * scale).coerceAtMost(cellRect.width())
                val drawnH = (bmp.height * scale).coerceAtMost(cellRect.height())
                val dx = (cellRect.width() - drawnW) / 2f
                val dy = (cellRect.height() - drawnH) / 2f
                RectF(cellRect.left + dx, cellRect.top + dy, cellRect.left + dx + drawnW, cellRect.top + dy + drawnH)
            }
        }
        canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), destRect, paint)
    }

    private fun countSourcePages(localFilePath: String?, uri: Uri?, password: String?): Int {
        if (!password.isNullOrEmpty()) {
            ensurePdfBoxInit()
            return openPdfBoxDocument(localFilePath, uri, password)?.use { it.numberOfPages } ?: 0
        }
        return try {
            val pfd = openParcelFileDescriptor(localFilePath, uri) ?: return 0
            pfd.use { fd ->
                PdfRenderer(fd).use { it.pageCount }
            }
        } catch (_: SecurityException) {
            ensurePdfBoxInit()
            openPdfBoxDocument(localFilePath, uri, "")?.use { it.numberOfPages } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun renderSingleSourcePageBitmap(
        localFilePath: String?,
        uri: Uri?,
        pageIndex: Int,
        targetEdgePx: Int,
        password: String?
    ): Bitmap? {
        if (!password.isNullOrEmpty()) {
            return renderWithPdfBox(localFilePath, uri, password, pageIndex, targetEdgePx)
        }
        return try {
            val pfd = openParcelFileDescriptor(localFilePath, uri) ?: return null
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.width.toFloat() / max(1, page.height).toFloat()
                        val (bmpW, bmpH) = if (ratio >= 1f) {
                            targetEdgePx to (targetEdgePx / ratio).roundToInt().coerceAtLeast(120)
                        } else {
                            (targetEdgePx * ratio).roundToInt().coerceAtLeast(120) to targetEdgePx
                        }
                        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bmp
                    }
                }
            }
        } catch (_: SecurityException) {
            renderWithPdfBox(localFilePath, uri, "", pageIndex, targetEdgePx)
        } catch (_: Exception) {
            null
        }
    }

    private fun renderWithPdfBox(
        localFilePath: String?,
        uri: Uri?,
        password: String,
        pageIndex: Int,
        targetEdgePx: Int
    ): Bitmap? {
        ensurePdfBoxInit()
        return runCatching {
            openPdfBoxDocument(localFilePath, uri, password)?.use { doc ->
                if (pageIndex !in 0 until doc.numberOfPages) return@use null
                val media = doc.getPage(pageIndex).mediaBox
                val maxPt = max(media?.width ?: 595f, media?.height ?: 842f).coerceAtLeast(100f)
                val scale = (targetEdgePx.toFloat() / maxPt).coerceIn(0.6f, 3.5f)
                PdfBoxRenderer(doc).renderImage(pageIndex, scale)
            }
        }.getOrNull()
    }

    private fun openPdfBoxDocument(localFilePath: String?, uri: Uri?, password: String): PDDocument? {
        return when {
            localFilePath != null -> {
                val f = File(localFilePath)
                if (!f.exists()) null else PDDocument.load(f, password)
            }
            uri != null -> {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input, password)
                }
            }
            else -> null
        }
    }

    private fun openParcelFileDescriptor(localFilePath: String?, uri: Uri?): ParcelFileDescriptor? {
        return when {
            localFilePath != null -> {
                val file = File(localFilePath)
                if (!file.exists()) null
                else ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            uri != null -> context.contentResolver.openFileDescriptor(uri, "r")
            else -> null
        }
    }

    private class FolioPrintDocumentAdapter(
        private val pdfFile: File,
        private val jobName: String
    ) : PrintDocumentAdapter() {

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val pageCount = runCatching {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { it.pageCount }
                }
            }.getOrDefault(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)

            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build()
            callback?.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onWriteCancelled()
                return
            }
            if (destination == null) {
                callback?.onWriteFailed("Missing print destination.")
                return
            }
            try {
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onWriteCancelled()
                                return
                            }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.localizedMessage ?: "Failed writing print stream.")
            }
        }
    }
}
