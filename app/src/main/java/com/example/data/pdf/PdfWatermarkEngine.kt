package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.PdfMetadataConfig
import com.example.model.PdfWatermarkConfig
import com.example.model.WatermarkPlacement
import com.example.model.WatermarkType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class PdfWatermarkEngine(private val context: Context) {

    private val pdfDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    private fun ensureInitialized() {
        if (!resourcesInitialized) {
            synchronized(initLock) {
                if (!resourcesInitialized) {
                    runCatching {
                        PDFBoxResourceLoader.init(context.applicationContext)
                    }
                    resourcesInitialized = true
                }
            }
        }
    }

    suspend fun applyWatermarkInPlace(
        filePath: String,
        watermark: PdfWatermarkConfig
    ): Long = withContext(Dispatchers.IO) {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            throw IllegalStateException("PDF file not found for watermarking.")
        }
        if (!watermark.isActive()) {
            return@withContext sourceFile.length()
        }

        ensureInitialized()
        val tempFile = File(sourceFile.parentFile, "${sourceFile.name}.wm.tmp")
        var watermarkBitmap: Bitmap? = null
        try {
            if (watermark.type == WatermarkType.IMAGE) {
                watermarkBitmap = decodeWatermarkBitmap(watermark.imageUriString)
            }
            PDDocument.load(sourceFile).use { document ->
                stampDocumentPages(
                    document = document,
                    watermark = watermark,
                    imageBitmap = watermarkBitmap,
                    pageRangeExpr = "all"
                )
                document.save(tempFile)
            }
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Failed to write watermarked PDF.")
            }
            if (!sourceFile.delete() || !tempFile.renameTo(sourceFile)) {
                tempFile.copyTo(sourceFile, overwrite = true)
                tempFile.delete()
            }
            sourceFile.length()
        } catch (t: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            throw t
        } finally {
            watermarkBitmap?.recycle()
        }
    }

    suspend fun watermarkExistingPdfToNewFile(
        uri: Uri?,
        localFilePath: String?,
        existingPassword: String?,
        outputBaseName: String,
        watermark: PdfWatermarkConfig,
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        if (!watermark.isReadyToApply()) {
            throw IllegalArgumentException(
                if (watermark.type == WatermarkType.IMAGE) {
                    "Select an image or logo to use as watermark."
                } else {
                    "Enter watermark text before applying."
                }
            )
        }

        ensureInitialized()
        val safeBase = sanitizeBase(outputBaseName.removeSuffix(".pdf"))
        val outFile = createUniquePdfFile(safeBase)
        val pwd = existingPassword.orEmpty()

        onProgress("Loading PDF document...")
        val document = when {
            localFilePath != null -> {
                val f = File(localFilePath)
                if (!f.exists()) throw IllegalStateException("Source PDF file not found.")
                if (pwd.isNotEmpty()) PDDocument.load(f, pwd) else PDDocument.load(f)
            }
            uri != null -> {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    if (pwd.isNotEmpty()) PDDocument.load(input, pwd) else PDDocument.load(input)
                } ?: throw IllegalStateException("Unable to open selected PDF.")
            }
            else -> throw IllegalStateException("No PDF source selected.")
        }

        var watermarkBitmap: Bitmap? = null
        var pageCount = 0
        var extractedText = ""
        try {
            if (watermark.type == WatermarkType.IMAGE) {
                onProgress("Preparing transparent image stamp...")
                watermarkBitmap = decodeWatermarkBitmap(watermark.imageUriString)
            }

            document.use { doc ->
                pageCount = doc.numberOfPages
                if (doc.isEncrypted) {
                    doc.isAllSecurityToBeRemoved = true
                }

                onProgress("Stamping watermark across PDF pages...")
                stampDocumentPages(
                    document = doc,
                    watermark = watermark.copy(enabled = true),
                    imageBitmap = watermarkBitmap,
                    pageRangeExpr = watermark.pageRangeExpr
                )

                if (metadata.hasAnyCustomMetadata()) {
                    val info = doc.documentInformation ?: PDDocumentInformation()
                    if (metadata.title.isNotBlank()) info.title = metadata.title.trim()
                    if (metadata.author.isNotBlank()) info.author = metadata.author.trim()
                    if (metadata.subject.isNotBlank()) info.subject = metadata.subject.trim()
                    val kws = metadata.parsedKeywordList().joinToString(", ")
                    if (kws.isNotBlank()) info.keywords = kws
                    if (metadata.creator.isNotBlank()) info.creator = metadata.creator.trim()
                    info.producer = "Folio PDF"
                    info.modificationDate = Calendar.getInstance()
                    doc.documentInformation = info
                }

                extractedText = runCatching {
                    PDFTextStripper().getText(doc).trim()
                }.getOrDefault("")

                onProgress("Saving watermarked PDF...")
                FileOutputStream(outFile).use { fos ->
                    doc.save(fos)
                }
            }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            watermarkBitmap?.recycle()
        }

        GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pageCount,
            fileSizeBytes = outFile.length(),
            fullText = extractedText,
            pageTextsJson = "[]"
        )
    }

    private suspend fun stampDocumentPages(
        document: PDDocument,
        watermark: PdfWatermarkConfig,
        imageBitmap: Bitmap?,
        pageRangeExpr: String
    ) {
        val totalPages = document.numberOfPages
        val targetPages = parsePageRange(pageRangeExpr, totalPages)
        if (targetPages.isEmpty()) {
            throw IllegalArgumentException("No valid pages match \"$pageRangeExpr\" (PDF has $totalPages pages).")
        }

        val pdImage: PDImageXObject? = if (watermark.type == WatermarkType.IMAGE && imageBitmap != null) {
            LosslessFactory.createFromImage(document, imageBitmap)
        } else {
            null
        }

        val font = PDType1Font.HELVETICA_BOLD
        val cleanText = if (watermark.type == WatermarkType.TEXT) {
            sanitizePdfWinAnsiText(watermark.text, font)
        } else {
            ""
        }

        val alpha = watermark.opacity.coerceIn(0.05f, 0.95f)
        val graphicsState = PDExtendedGraphicsState().apply {
            setNonStrokingAlphaConstant(alpha)
            setStrokingAlphaConstant(alpha)
            setAlphaSourceFlag(true)
        }

        val red = Color.red(watermark.color.argb)
        val green = Color.green(watermark.color.argb)
        val blue = Color.blue(watermark.color.argb)

        for (pageIndex in targetPages) {
            coroutineContext.ensureActive()
            val page = document.getPage(pageIndex)
            val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
            drawWatermarkOnPage(
                document = document,
                page = page,
                box = box,
                watermark = watermark,
                graphicsState = graphicsState,
                font = font,
                cleanText = cleanText,
                colorRgb = Triple(red, green, blue),
                pdImage = pdImage
            )
        }
    }

    private fun drawWatermarkOnPage(
        document: PDDocument,
        page: PDPage,
        box: PDRectangle,
        watermark: PdfWatermarkConfig,
        graphicsState: PDExtendedGraphicsState,
        font: PDType1Font,
        cleanText: String,
        colorRgb: Triple<Int, Int, Int>,
        pdImage: PDImageXObject?
    ) {
        val pageW = box.width.coerceAtLeast(72f)
        val pageH = box.height.coerceAtLeast(72f)
        val originX = box.lowerLeftX
        val originY = box.lowerLeftY
        val centerX = originX + pageW / 2f
        val centerY = originY + pageH / 2f

        // Negate Compose clockwise rotation into PDF counter-clockwise Y-up coordinates
        val effectiveAngleDeg = when (watermark.placement) {
            WatermarkPlacement.DIAGONAL_CENTER -> {
                if (kotlin.math.abs(watermark.rotationDegrees) < 1f) 35f else -watermark.rotationDegrees
            }
            WatermarkPlacement.TILED_GRID -> {
                if (kotlin.math.abs(watermark.rotationDegrees) < 1f) 30f else -watermark.rotationDegrees
            }
            WatermarkPlacement.TOP_HEADER,
            WatermarkPlacement.BOTTOM_FOOTER -> 0f
            WatermarkPlacement.CENTER,
            WatermarkPlacement.BOTTOM_RIGHT -> -watermark.rotationDegrees
        }

        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true
        ).use { cs ->
            cs.saveGraphicsState()
            cs.setGraphicsStateParameters(graphicsState)

            when (watermark.type) {
                WatermarkType.TEXT -> {
                    val baseFontSize = when (watermark.placement) {
                        WatermarkPlacement.TOP_HEADER,
                        WatermarkPlacement.BOTTOM_FOOTER -> (watermark.textSize.pt * 0.34f).coerceIn(10f, 18f)
                        WatermarkPlacement.BOTTOM_RIGHT -> (watermark.textSize.pt * 0.40f).coerceIn(11f, 22f)
                        WatermarkPlacement.TILED_GRID -> (watermark.textSize.pt * 0.56f).coerceIn(14f, 34f)
                        else -> watermark.textSize.pt
                    }
                    val scaleRefWidth = when (watermark.placement) {
                        WatermarkPlacement.TILED_GRID -> pageW * 0.36f
                        WatermarkPlacement.BOTTOM_RIGHT -> pageW * 0.42f
                        else -> pageW * 0.84f
                    }
                    val rawTextWidth = (font.getStringWidth(cleanText) / 1000f) * baseFontSize
                    val fittedFontSize = if (rawTextWidth > scaleRefWidth && rawTextWidth > 1f) {
                        (baseFontSize * (scaleRefWidth / rawTextWidth)).coerceAtLeast(8f)
                    } else {
                        baseFontSize
                    }
                    val textWidth = (font.getStringWidth(cleanText) / 1000f) * fittedFontSize
                    val capHeight = fittedFontSize * 0.72f

                    cs.setNonStrokingColor(colorRgb.first, colorRgb.second, colorRgb.third)

                    val positions: List<Pair<Float, Float>> = when (watermark.placement) {
                        WatermarkPlacement.DIAGONAL_CENTER,
                        WatermarkPlacement.CENTER -> listOf(centerX to centerY)
                        WatermarkPlacement.TOP_HEADER -> listOf(centerX to (originY + pageH - 30f - capHeight / 2f))
                        WatermarkPlacement.BOTTOM_FOOTER -> listOf(centerX to (originY + 28f + capHeight / 2f))
                        WatermarkPlacement.BOTTOM_RIGHT -> {
                            val marginX = 28f + textWidth / 2f
                            val marginY = 28f + capHeight / 2f
                            listOf((originX + pageW - marginX).coerceAtLeast(centerX) to (originY + marginY))
                        }
                        WatermarkPlacement.TILED_GRID -> {
                            val cols = listOf(0.22f, 0.50f, 0.78f)
                            val rows = listOf(0.22f, 0.50f, 0.78f)
                            buildList {
                                for (rf in rows) {
                                    for (cf in cols) {
                                        add((originX + pageW * cf) to (originY + pageH * rf))
                                    }
                                }
                            }
                        }
                    }

                    val rad = Math.toRadians(effectiveAngleDeg.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val halfW = textWidth / 2f
                    val halfH = capHeight / 2f

                    for ((cx, cy) in positions) {
                        val tx = cx - (halfW * cosA - halfH * sinA)
                        val ty = cy - (halfW * sinA + halfH * cosA)
                        cs.beginText()
                        cs.setFont(font, fittedFontSize)
                        cs.setTextMatrix(Matrix(cosA, sinA, -sinA, cosA, tx, ty))
                        cs.showText(cleanText)
                        cs.endText()
                    }
                }

                WatermarkType.IMAGE -> {
                    val img = pdImage ?: return@use
                    val aspect = if (img.width > 0) img.height.toFloat() / img.width.toFloat() else 1f
                    val baseFrac = watermark.imageWidthFraction.coerceIn(0.10f, 0.85f)
                    val targetW = when (watermark.placement) {
                        WatermarkPlacement.TILED_GRID -> (pageW * (baseFrac * 0.52f)).coerceIn(36f, pageW * 0.28f)
                        WatermarkPlacement.TOP_HEADER,
                        WatermarkPlacement.BOTTOM_FOOTER -> (pageW * (baseFrac * 0.45f)).coerceIn(36f, pageW * 0.26f)
                        WatermarkPlacement.BOTTOM_RIGHT -> (pageW * (baseFrac * 0.55f)).coerceIn(42f, pageW * 0.32f)
                        else -> (pageW * baseFrac).coerceIn(48f, pageW * 0.88f)
                    }
                    val rawH = targetW * aspect
                    val maxAllowedH = when (watermark.placement) {
                        WatermarkPlacement.TOP_HEADER,
                        WatermarkPlacement.BOTTOM_FOOTER -> pageH * 0.14f
                        WatermarkPlacement.TILED_GRID -> pageH * 0.22f
                        else -> pageH * 0.82f
                    }
                    val (drawW, drawH) = if (rawH > maxAllowedH && rawH > 1f) {
                        val scale = maxAllowedH / rawH
                        (targetW * scale) to maxAllowedH
                    } else {
                        targetW to rawH
                    }

                    val positions: List<Pair<Float, Float>> = when (watermark.placement) {
                        WatermarkPlacement.DIAGONAL_CENTER,
                        WatermarkPlacement.CENTER -> listOf(centerX to centerY)
                        WatermarkPlacement.TOP_HEADER -> listOf(centerX to (originY + pageH - 22f - drawH / 2f))
                        WatermarkPlacement.BOTTOM_FOOTER -> listOf(centerX to (originY + 22f + drawH / 2f))
                        WatermarkPlacement.BOTTOM_RIGHT -> {
                            listOf((originX + pageW - 24f - drawW / 2f) to (originY + 24f + drawH / 2f))
                        }
                        WatermarkPlacement.TILED_GRID -> {
                            val cols = listOf(0.22f, 0.50f, 0.78f)
                            val rows = listOf(0.22f, 0.50f, 0.78f)
                            buildList {
                                for (rf in rows) {
                                    for (cf in cols) {
                                        add((originX + pageW * cf) to (originY + pageH * rf))
                                    }
                                }
                            }
                        }
                    }

                    val rad = Math.toRadians(effectiveAngleDeg.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()

                    for ((cx, cy) in positions) {
                        val tx = cx - (0.5f * drawW * cosA - 0.5f * drawH * sinA)
                        val ty = cy - (0.5f * drawW * sinA + 0.5f * drawH * cosA)
                        val imgMatrix = Matrix(
                            drawW * cosA,
                            drawW * sinA,
                            -drawH * sinA,
                            drawH * cosA,
                            tx,
                            ty
                        )
                        cs.drawImage(img, imgMatrix)
                    }
                }
            }

            cs.restoreGraphicsState()
        }
    }

    private fun decodeWatermarkBitmap(uriString: String?): Bitmap {
        val raw = uriString?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Select an image file to use as an image watermark.")
        val uri = Uri.parse(raw)

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openUriStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) {
            throw IllegalStateException("Could not decode the selected watermark image.")
        }

        val maxEdge = 960
        var sampleSize = 1
        while ((origW / sampleSize) > maxEdge * 2 || (origH / sampleSize) > maxEdge * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = openUriStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Unable to load the selected watermark image.")

        val longest = maxOf(decoded.width, decoded.height)
        return if (longest > maxEdge) {
            val scale = maxEdge.toFloat() / longest.toFloat()
            val scaledW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val scaledH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else {
            decoded
        }
    }

    private fun openUriStream(uri: Uri) =
        if (uri.scheme.equals("file", ignoreCase = true) && !uri.path.isNullOrBlank()) {
            File(uri.path!!).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }

    fun resolveImageDisplayName(uri: Uri): String {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.lastPathSegment ?: "Watermark_Image"
        }
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment ?: "Watermark_Image"
    }

    fun parsePageRange(expr: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val trimmed = expr.trim()
        if (trimmed.isBlank() || trimmed.equals("all", ignoreCase = true)) {
            return (0 until totalPages).toList()
        }
        val selected = linkedSetOf<Int>()
        for (part in trimmed.split(",")) {
            val token = part.trim()
            if (token.isBlank()) continue
            if (token.contains("-")) {
                val bounds = token.split("-", limit = 2)
                val start = bounds[0].trim().toIntOrNull() ?: continue
                val end = bounds[1].trim().toIntOrNull() ?: continue
                val lo = minOf(start, end).coerceIn(1, totalPages)
                val hi = maxOf(start, end).coerceIn(1, totalPages)
                for (p in lo..hi) selected.add(p - 1)
            } else {
                val p = token.toIntOrNull() ?: continue
                if (p in 1..totalPages) selected.add(p - 1)
            }
        }
        return selected.toList()
    }

    fun sanitizePdfWinAnsiText(raw: String, font: PDType1Font = PDType1Font.HELVETICA_BOLD): String {
        val replaced = raw
            .replace("\u2122", "(TM)")
            .replace("\u2022", "*")
            .replace("\u2014", "-")
            .replace("\u2013", "-")
            .replace("\u2018", "'")
            .replace("\u2019", "'")
            .replace("\u201C", "\"")
            .replace("\u201D", "\"")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
        val sb = StringBuilder()
        var i = 0
        while (i < replaced.length) {
            val cp = replaced.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val encodable = runCatching { font.encode(ch) }.isSuccess
            if (encodable) {
                sb.append(ch)
            }
            i += Character.charCount(cp)
        }
        val cleaned = sb.toString().trim()
        return if (cleaned.isNotEmpty()) cleaned else "CONFIDENTIAL"
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

    private fun sanitizeBase(raw: String): String {
        val cleaned = raw
            .trim()
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (cleaned.isBlank()) "Folio_${System.currentTimeMillis()}" else cleaned.take(64)
    }

    companion object {
        private val initLock = Any()

        @Volatile
        private var resourcesInitialized = false
    }
}
