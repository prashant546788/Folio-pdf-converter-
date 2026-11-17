package com.example.data.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.ConversionSettings
import com.example.model.PageSizeOption
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

enum class ImageExportFormat(val label: String, val extension: String, val mimeType: String) {
    PNG("PNG (Lossless)", "png", "image/png"),
    JPG("JPG (Compact)", "jpg", "image/jpeg")
}

enum class PdfCompressionLevel(
    val label: String,
    val renderDpi: Int,
    val jpegQuality: Int
) {
    LIGHT("Light · 200 DPI", 200, 88),
    BALANCED("Balanced · 140 DPI", 140, 72),
    COMPACT("Compact · 96 DPI", 96, 54)
}

data class ExportedImageInfo(
    val fileName: String,
    val cachePath: String,
    val savedLocationLabel: String,
    val pageNumber: Int,
    val sizeBytes: Long
)

data class PdfCompressionOutcome(
    val pdfInfo: GeneratedPdfInfo,
    val originalBytes: Long,
    val compressedBytes: Long
)

class PdfToolboxEngine(
    private val context: Context,
    private val ocrProcessor: OcrProcessor
) {
    private val pdfDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    private val exportedImgDir: File
        get() = File(context.cacheDir, "exported_images").apply { if (!exists()) mkdirs() }

    suspend fun inspectPdfUri(uri: Uri): Triple<String, Int, Long> = withContext(Dispatchers.IO) {
        var name = "Document.pdf"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nIdx >= 0) name = cursor.getString(nIdx) ?: name
                if (sIdx >= 0 && !cursor.isNull(sIdx)) size = cursor.getLong(sIdx)
            }
        }
        val pageCount = runCatching {
            openPdfDescriptor(uri, null)?.use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            } ?: 0
        }.getOrDefault(0)
        Triple(name, pageCount, size)
    }

    suspend fun convertPdfToImages(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        format: ImageExportFormat,
        dpi: Int,
        pageRangeExpr: String,
        existingPassword: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): List<ExportedImageInfo> = withContext(Dispatchers.IO) {
        val safeBase = sanitizeBase(baseName.removeSuffix(".pdf"))
        val results = mutableListOf<ExportedImageInfo>()
        val scale = (dpi / 72f).coerceIn(1f, 4.2f)

        withReadablePdfDescriptor(uri, localFilePath, existingPassword) { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val targetPages = parsePageRange(pageRangeExpr, renderer.pageCount)
                if (targetPages.isEmpty()) {
                    throw IllegalArgumentException("No valid pages selected (PDF has ${renderer.pageCount} pages).")
                }

                for ((idx, pageIndex) in targetPages.withIndex()) {
                    coroutineContext.ensureActive()
                    onProgress("Exporting page ${pageIndex + 1} (${idx + 1}/${targetPages.size})...")
                    renderer.openPage(pageIndex).use { page ->
                        val bmpW = (page.width * scale).toInt().coerceIn(300, 2800)
                        val bmpH = (page.height * scale).toInt().coerceIn(300, 3800)
                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val imgFileName = "${safeBase}_p${pageIndex + 1}.${format.extension}"
                            val cacheFile = File(exportedImgDir, imgFileName)
                            FileOutputStream(cacheFile).use { out ->
                                val compressFmt = if (format == ImageExportFormat.PNG) {
                                    Bitmap.CompressFormat.PNG
                                } else {
                                    Bitmap.CompressFormat.JPEG
                                }
                                bitmap.compress(compressFmt, 92, out)
                            }

                            val savedLabel = saveImageToPictures(cacheFile, imgFileName, format.mimeType)
                            results.add(
                                ExportedImageInfo(
                                    fileName = imgFileName,
                                    cachePath = cacheFile.absolutePath,
                                    savedLocationLabel = savedLabel,
                                    pageNumber = pageIndex + 1,
                                    sizeBytes = cacheFile.length()
                                )
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        results
    }

    suspend fun splitPdf(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        splitEachPage: Boolean,
        pageRangeExpr: String,
        enableOcr: Boolean,
        existingPassword: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): List<GeneratedPdfInfo> = withContext(Dispatchers.IO) {
        val safeBase = sanitizeBase(baseName.removeSuffix(".pdf"))
        val generatedList = mutableListOf<GeneratedPdfInfo>()

        withReadablePdfDescriptor(uri, localFilePath, existingPassword) { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val total = renderer.pageCount
                val pages = if (splitEachPage) {
                    (0 until total).toList()
                } else {
                    parsePageRange(pageRangeExpr, total)
                }
                if (pages.isEmpty()) {
                    throw IllegalArgumentException("No valid pages found in range (1–$total).")
                }

                if (splitEachPage) {
                    for ((idx, pageIndex) in pages.withIndex()) {
                        coroutineContext.ensureActive()
                        onProgress("Splitting page ${pageIndex + 1} (${idx + 1}/${pages.size})...")
                        val info = renderSubsetToPdf(
                            renderer = renderer,
                            pageIndices = listOf(pageIndex),
                            outBaseName = "${safeBase}_page_${pageIndex + 1}",
                            dpi = 200,
                            jpegQuality = 90,
                            rotationDegrees = 0,
                            enableOcr = enableOcr
                        )
                        generatedList.add(info)
                    }
                } else {
                    onProgress("Extracting ${pages.size} selected page(s)...")
                    val info = renderSubsetToPdf(
                        renderer = renderer,
                        pageIndices = pages,
                        outBaseName = "${safeBase}_extracted",
                        dpi = 200,
                        jpegQuality = 90,
                        rotationDegrees = 0,
                        enableOcr = enableOcr
                    )
                    generatedList.add(info)
                }
            }
        }
        generatedList
    }

    suspend fun compressPdf(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        level: PdfCompressionLevel,
        existingPassword: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): PdfCompressionOutcome = withContext(Dispatchers.IO) {
        val originalSize = estimateSizeBytes(uri, localFilePath)
        val safeBase = sanitizeBase(baseName.removeSuffix(".pdf"))

        val pdfInfo = withReadablePdfDescriptor(uri, localFilePath, existingPassword) { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val allPages = (0 until renderer.pageCount).toList()
                onProgress("Optimizing ${allPages.size} page(s) at ${level.renderDpi} DPI...")
                renderSubsetToPdf(
                    renderer = renderer,
                    pageIndices = allPages,
                    outBaseName = "${safeBase}_compressed",
                    dpi = level.renderDpi,
                    jpegQuality = level.jpegQuality,
                    rotationDegrees = 0,
                    enableOcr = false
                )
            }
        }
        PdfCompressionOutcome(
            pdfInfo = pdfInfo,
            originalBytes = originalSize,
            compressedBytes = pdfInfo.fileSizeBytes
        )
    }

    suspend fun rotatePdf(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        rotationDegrees: Int,
        enableOcr: Boolean,
        existingPassword: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        val safeBase = sanitizeBase(baseName.removeSuffix(".pdf"))

        withReadablePdfDescriptor(uri, localFilePath, existingPassword) { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val allPages = (0 until renderer.pageCount).toList()
                onProgress("Rotating ${allPages.size} page(s) by $rotationDegrees°...")
                renderSubsetToPdf(
                    renderer = renderer,
                    pageIndices = allPages,
                    outBaseName = "${safeBase}_rotated",
                    dpi = 200,
                    jpegQuality = 92,
                    rotationDegrees = rotationDegrees,
                    enableOcr = enableOcr
                )
            }
        }
    }

    suspend fun mergePdfs(
        sources: List<Pair<Uri?, String?>>,
        outputBaseName: String,
        enableOcr: Boolean,
        onProgress: suspend (String) -> Unit = {}
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        val safeBase = sanitizeBase(outputBaseName.removeSuffix(".pdf"))
        val outFile = createUniquePdfFile(safeBase)
        val document = PdfDocument()
        val pageTexts = mutableListOf<String>()
        var pageNum = 1

        try {
            for ((srcIdx, pair) in sources.withIndex()) {
                withReadablePdfDescriptor(pair.first, pair.second, null) { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        for (p in 0 until renderer.pageCount) {
                            coroutineContext.ensureActive()
                            onProgress("Merging file ${srcIdx + 1}/${sources.size}, page ${p + 1}/${renderer.pageCount}...")
                            renderer.openPage(p).use { srcPage ->
                                val scale = 200f / 72f
                                val bmpW = (srcPage.width * scale).toInt().coerceIn(400, 2200)
                                val bmpH = (srcPage.height * scale).toInt().coerceIn(400, 3000)
                                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                try {
                                    bmp.eraseColor(Color.WHITE)
                                    srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    val ocrResult = if (enableOcr) {
                                        ocrProcessor.recognizeBitmap(bmp)
                                    } else {
                                        OcrPageResult("", emptyList())
                                    }
                                    val pageW = srcPage.width.coerceAtLeast(200)
                                    val pageH = srcPage.height.coerceAtLeast(200)
                                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
                                    val outPage = document.startPage(pageInfo)
                                    drawFullBleedBitmap(outPage.canvas, bmp, ocrResult, pageW, pageH)
                                    document.finishPage(outPage)
                                    pageTexts.add(ocrResult.fullText)
                                    pageNum++
                                } finally {
                                    bmp.recycle()
                                }
                            }
                        }
                    }
                }
            }
            if (pageNum == 1) error("No pages found to merge.")
            FileOutputStream(outFile).use { fos -> document.writeTo(fos) }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            document.close()
        }

        val combined = pageTexts.filter { it.isNotBlank() }.joinToString("\n\n")
        val jsonArr = JSONArray().apply { pageTexts.forEach { put(it) } }
        GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pageTexts.size,
            fileSizeBytes = outFile.length(),
            fullText = combined,
            pageTextsJson = jsonArr.toString()
        )
    }

    suspend fun mergeCustomPages(
        pages: List<com.example.model.MergePageItem>,
        outputBaseName: String,
        enableOcr: Boolean,
        onProgress: suspend (String) -> Unit = {}
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        val safeBase = sanitizeBase(outputBaseName.removeSuffix(".pdf"))
        val outFile = createUniquePdfFile(safeBase)
        val document = PdfDocument()
        val pageTexts = mutableListOf<String>()

        try {
            for ((idx, pageItem) in pages.withIndex()) {
                coroutineContext.ensureActive()
                onProgress("Merging page ${idx + 1}/${pages.size} (${pageItem.sourceDisplayName} p.${pageItem.pageNumber1Based})...")
                withReadablePdfDescriptor(pageItem.sourceUri, pageItem.sourceLocalPath, null) { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (pageItem.pageIndex0Based >= 0 && pageItem.pageIndex0Based < renderer.pageCount) {
                            renderer.openPage(pageItem.pageIndex0Based).use { srcPage ->
                                val scale = 200f / 72f
                                val bmpW = (srcPage.width * scale).toInt().coerceIn(400, 2200)
                                val bmpH = (srcPage.height * scale).toInt().coerceIn(400, 3000)
                                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                try {
                                    bmp.eraseColor(Color.WHITE)
                                    srcPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    val ocrResult = if (enableOcr) {
                                        ocrProcessor.recognizeBitmap(bmp)
                                    } else {
                                        OcrPageResult("", emptyList())
                                    }
                                    val pageW = srcPage.width.coerceAtLeast(200)
                                    val pageH = srcPage.height.coerceAtLeast(200)
                                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                                    val outPage = document.startPage(pageInfo)
                                    drawFullBleedBitmap(outPage.canvas, bmp, ocrResult, pageW, pageH)
                                    document.finishPage(outPage)
                                    pageTexts.add(ocrResult.fullText)
                                } finally {
                                    bmp.recycle()
                                }
                            }
                        }
                    }
                }
            }
            if (pages.isEmpty()) error("No pages selected to merge.")
            FileOutputStream(outFile).use { fos -> document.writeTo(fos) }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            document.close()
        }

        val combined = pageTexts.filter { it.isNotBlank() }.joinToString("\n\n")
        val jsonArr = JSONArray().apply { pageTexts.forEach { put(it) } }
        GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pages.size,
            fileSizeBytes = outFile.length(),
            fullText = combined,
            pageTextsJson = jsonArr.toString()
        )
    }


    private suspend fun renderSubsetToPdf(
        renderer: PdfRenderer,
        pageIndices: List<Int>,
        outBaseName: String,
        dpi: Int,
        jpegQuality: Int,
        rotationDegrees: Int,
        enableOcr: Boolean
    ): GeneratedPdfInfo {
        val outFile = createUniquePdfFile(outBaseName)
        val document = PdfDocument()
        val pageTexts = mutableListOf<String>()
        val scale = (dpi / 72f).coerceIn(1f, 3.5f)

        try {
            pageIndices.forEachIndexed { idx, pageIndex ->
                coroutineContext.ensureActive()
                renderer.openPage(pageIndex).use { srcPage ->
                    val rawW = (srcPage.width * scale).toInt().coerceIn(300, 2200)
                    val rawH = (srcPage.height * scale).toInt().coerceIn(300, 3000)
                    val rawBmp = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.ARGB_8888)
                    rawBmp.eraseColor(Color.WHITE)
                    srcPage.render(rawBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val rotatedBmp = if (rotationDegrees % 360 != 0) {
                        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true).also {
                            if (it != rawBmp) rawBmp.recycle()
                        }
                    } else {
                        rawBmp
                    }

                    // Apply JPEG compression pass if quality < 90 to genuinely shrink PDF byte size
                    val finalBmp = if (jpegQuality < 90) {
                        val baos = ByteArrayOutputStream()
                        rotatedBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                        rotatedBmp.recycle()
                        val bytes = baos.toByteArray()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        rotatedBmp
                    }

                    try {
                        val ocrResult = if (enableOcr) {
                            ocrProcessor.recognizeBitmap(finalBmp)
                        } else {
                            OcrPageResult("", emptyList())
                        }
                        val swapDims = rotationDegrees % 180 != 0
                        val pageW = (if (swapDims) srcPage.height else srcPage.width).coerceAtLeast(200)
                        val pageH = (if (swapDims) srcPage.width else srcPage.height).coerceAtLeast(200)
                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                        val outPage = document.startPage(pageInfo)
                        drawFullBleedBitmap(outPage.canvas, finalBmp, ocrResult, pageW, pageH)
                        document.finishPage(outPage)
                        pageTexts.add(ocrResult.fullText)
                    } finally {
                        finalBmp.recycle()
                    }
                }
            }
            FileOutputStream(outFile).use { fos -> document.writeTo(fos) }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            document.close()
        }

        val combined = pageTexts.filter { it.isNotBlank() }.joinToString("\n\n")
        val jsonArr = JSONArray().apply { pageTexts.forEach { put(it) } }
        return GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pageTexts.size,
            fileSizeBytes = outFile.length(),
            fullText = combined,
            pageTextsJson = jsonArr.toString()
        )
    }

    private fun drawFullBleedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        ocrResult: OcrPageResult,
        pageW: Int,
        pageH: Int
    ) {
        canvas.drawColor(Color.WHITE)
        val destRect = RectF(0f, 0f, pageW.toFloat(), pageH.toFloat())
        val scale = minOf(pageW.toFloat() / bitmap.width.toFloat(), pageH.toFloat() / bitmap.height.toFloat())
        if (ocrResult.lines.isNotEmpty()) {
            val ocrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(1, 0, 0, 0)
            }
            for (line in ocrResult.lines) {
                val words = if (line.words.isNotEmpty()) line.words else listOf(OcrWordBox(line.text, line.boundingBox))
                for (word in words) {
                    val box = word.boundingBox
                    val targetX = box.left * scale
                    val targetW = (box.width() * scale).coerceAtLeast(4f)
                    val targetH = (box.height() * scale).coerceAtLeast(5f)
                    ocrPaint.textSize = targetH * 0.88f
                    ocrPaint.textScaleX = 1f
                    val measured = ocrPaint.measureText(word.text).coerceAtLeast(1f)
                    ocrPaint.textScaleX = (targetW / measured).coerceIn(0.35f, 3.0f)
                    val baselineY = box.bottom * scale - (targetH * 0.12f)
                    canvas.drawText(word.text, targetX, baselineY, ocrPaint)
                }
            }
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(bitmap, null, destRect, paint)
    }

    private inline fun <T> withReadablePdfDescriptor(
        uri: Uri?,
        localFilePath: String?,
        existingPassword: String?,
        block: (ParcelFileDescriptor) -> T
    ): T {
        if (existingPassword.isNullOrEmpty()) {
            val rawPfd = openPdfDescriptor(uri, localFilePath)
                ?: throw IllegalStateException("Cannot open PDF file.")
            try {
                return rawPfd.use { block(it) }
            } catch (_: SecurityException) {
                // Fall through to PDFBox owner-only check or clear error message
                return decryptToTempAndRun(uri, localFilePath, "", block)
            }
        }
        return decryptToTempAndRun(uri, localFilePath, existingPassword, block)
    }

    private inline fun <T> decryptToTempAndRun(
        uri: Uri?,
        localFilePath: String?,
        password: String,
        block: (ParcelFileDescriptor) -> T
    ): T {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        val tempDecrypted = File(context.cacheDir, "toolbox_tmp_${System.nanoTime()}.pdf")
        try {
            val pdDoc = try {
                when {
                    localFilePath != null -> {
                        val f = File(localFilePath)
                        if (!f.exists()) throw IllegalStateException("PDF file not found.")
                        PDDocument.load(f, password)
                    }
                    uri != null -> {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            PDDocument.load(input, password)
                        } ?: throw IllegalStateException("Unable to read selected PDF.")
                    }
                    else -> throw IllegalStateException("No PDF selected.")
                }
            } catch (_: InvalidPasswordException) {
                throw IllegalStateException("This PDF is password-protected. Enter the password first.")
            }

            pdDoc.use { doc ->
                if (doc.isEncrypted) {
                    doc.isAllSecurityToBeRemoved = true
                }
                doc.save(tempDecrypted)
            }
            val tempPfd = ParcelFileDescriptor.open(tempDecrypted, ParcelFileDescriptor.MODE_READ_ONLY)
            return tempPfd.use { block(it) }
        } finally {
            if (tempDecrypted.exists()) tempDecrypted.delete()
        }
    }

    private fun openPdfDescriptor(uri: Uri?, localFilePath: String?): ParcelFileDescriptor? {
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

    private fun estimateSizeBytes(uri: Uri?, localFilePath: String?): Long {
        if (localFilePath != null) {
            return File(localFilePath).length()
        }
        if (uri != null) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sIdx >= 0 && !cursor.isNull(sIdx)) return cursor.getLong(sIdx)
                }
            }
        }
        return 0L
    }

    private fun saveImageToPictures(cacheFile: File, fileName: String, mimeType: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FolioPDF")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    cacheFile.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Pictures/FolioPDF/$fileName"
            }
        }
        return "App Cache (tap Share to save)"
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
}
