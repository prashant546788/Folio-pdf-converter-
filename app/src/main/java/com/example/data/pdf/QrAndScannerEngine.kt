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
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.QrColorPreset
import com.example.model.QrContentType
import com.example.model.ScannedBarcodeItem
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class QrAndScannerEngine(private val context: Context) {

    private val qrExportDir: File
        get() = File(context.cacheDir, "qr_exports").apply { if (!exists()) mkdirs() }

    private val pdfDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    val scannerOptions: BarcodeScannerOptions by lazy {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    }

    fun buildQrPayload(
        type: QrContentType,
        primaryText: String,
        secondaryText: String = "",
        tertiaryText: String = "",
        wifiSecurity: String = "WPA"
    ): String {
        val p = primaryText.trim()
        val s = secondaryText.trim()
        val t = tertiaryText.trim()
        return when (type) {
            QrContentType.TEXT_URL -> p
            QrContentType.WIFI -> {
                val safeSsid = escapeWifiField(p)
                val safePass = escapeWifiField(s)
                if (wifiSecurity.equals("nopass", ignoreCase = true) || safePass.isEmpty()) {
                    "WIFI:T:nopass;S:$safeSsid;;"
                } else {
                    "WIFI:T:$wifiSecurity;S:$safeSsid;P:$safePass;;"
                }
            }
            QrContentType.EMAIL -> {
                val params = mutableListOf<String>()
                if (s.isNotEmpty()) params.add("subject=${Uri.encode(s)}")
                if (t.isNotEmpty()) params.add("body=${Uri.encode(t)}")
                val query = if (params.isEmpty()) "" else "?${params.joinToString("&")}"
                "mailto:$p$query"
            }
            QrContentType.PHONE -> "tel:$p"
            QrContentType.VCARD -> buildString {
                appendLine("BEGIN:VCARD")
                appendLine("VERSION:3.0")
                appendLine("FN:$p")
                if (s.isNotEmpty()) appendLine("TEL:$s")
                if (t.isNotEmpty()) appendLine("EMAIL:$t")
                append("END:VCARD")
            }
        }
    }

    fun generateQrBitmap(
        payload: String,
        sizePx: Int = 720,
        preset: QrColorPreset = QrColorPreset.CLASSIC,
        caption: String = ""
    ): Bitmap {
        val safePayload = payload.ifBlank { "https://foliopdf.app" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(safePayload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val cleanCaption = caption.trim()
        val footerHeight = if (cleanCaption.isNotEmpty()) (sizePx * 0.14f).toInt().coerceAtLeast(64) else 0

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) preset.fgArgb else preset.bgArgb
            }
        }

        val qrOnly = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        if (footerHeight == 0) return qrOnly

        val combined = Bitmap.createBitmap(width, height + footerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(preset.bgArgb)
        canvas.drawBitmap(qrOnly, 0f, 0f, null)
        qrOnly.recycle()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = preset.fgArgb
            textSize = (footerHeight * 0.42f).coerceIn(20f, 40f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val clipped = if (cleanCaption.length > 42) cleanCaption.take(40) + "…" else cleanCaption
        canvas.drawText(clipped, width / 2f, height + footerHeight * 0.62f, textPaint)
        return combined
    }

    suspend fun saveQrPng(
        bitmap: Bitmap,
        baseName: String
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        val safeBase = sanitizeBase(baseName.ifBlank { "Folio_QR" })
        val fileName = "${safeBase}_${System.currentTimeMillis() % 10000}.png"
        val cacheFile = File(qrExportDir, fileName)
        FileOutputStream(cacheFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val locationLabel = saveImageToPictures(cacheFile, fileName)
        cacheFile to locationLabel
    }

    suspend fun generateQrPdf(
        bitmap: Bitmap,
        payload: String,
        caption: String,
        outputBaseName: String
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        val safeBase = sanitizeBase(outputBaseName.ifBlank { "Folio_QR_Card" })
        val outFile = createUniquePdfFile(safeBase)
        val document = PdfDocument()
        val pageW = 595
        val pageH = 842
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 25, 28)
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(90, 95, 105)
                textSize = 11f
                typeface = Typeface.SANS_SERIF
                textAlign = Paint.Align.CENTER
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(215, 218, 224)
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
            }

            val title = caption.trim().ifBlank { "QR Code" }
            canvas.drawText(title, pageW / 2f, 110f, titlePaint)

            val qrDrawSize = 310f
            val left = (pageW - qrDrawSize) / 2f
            val top = 160f
            val boxRect = RectF(left - 18f, top - 18f, left + qrDrawSize + 18f, top + qrDrawSize + 18f)
            canvas.drawRoundRect(boxRect, 16f, 16f, borderPaint)

            val aspect = bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
            val destRect = RectF(left, top, left + qrDrawSize, top + qrDrawSize * aspect)
            canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))

            val payloadY = destRect.bottom + 42f
            val clippedPayload = if (payload.length > 78) payload.take(76) + "…" else payload
            canvas.drawText(clippedPayload, pageW / 2f, payloadY, bodyPaint)
            canvas.drawText("Generated by Folio PDF", pageW / 2f, pageH - 36f, bodyPaint)

            document.finishPage(page)
            FileOutputStream(outFile).use { fos -> document.writeTo(fos) }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        } finally {
            document.close()
        }

        val pageTexts = listOf(listOf(caption, payload).filter { it.isNotBlank() }.joinToString("\n"))
        val jsonArr = JSONArray().apply { pageTexts.forEach { put(it) } }
        GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = 1,
            fileSizeBytes = outFile.length(),
            fullText = pageTexts.first(),
            pageTextsJson = jsonArr.toString()
        )
    }

    suspend fun scanBarcodesFromImageUri(uri: Uri): List<ScannedBarcodeItem> = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val scanner = BarcodeScanning.getClient(scannerOptions)
        try {
            val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
                scanner.process(image)
                    .addOnSuccessListener { list -> if (cont.isActive) cont.resume(list) }
                    .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
            }
            barcodes.mapNotNull { toScannedItem(it) }
        } finally {
            scanner.close()
        }
    }

    fun toScannedItem(barcode: Barcode): ScannedBarcodeItem? {
        val raw = barcode.rawValue?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val display = barcode.displayValue?.trim().takeUnless { it.isNullOrEmpty() } ?: raw
        val isUrl = barcode.valueType == Barcode.TYPE_URL ||
            raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        return ScannedBarcodeItem(
            rawValue = raw,
            displayValue = display,
            formatLabel = formatLabel(barcode.format),
            typeLabel = valueTypeLabel(barcode.valueType),
            isUrl = isUrl
        )
    }

    private fun formatLabel(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        else -> "Barcode"
    }

    private fun valueTypeLabel(type: Int): String = when (type) {
        Barcode.TYPE_URL -> "Web Link"
        Barcode.TYPE_WIFI -> "Wi-Fi Network"
        Barcode.TYPE_EMAIL -> "Email"
        Barcode.TYPE_PHONE -> "Phone Number"
        Barcode.TYPE_CONTACT_INFO -> "Contact Card"
        Barcode.TYPE_SMS -> "SMS"
        Barcode.TYPE_GEO -> "Location"
        Barcode.TYPE_ISBN -> "ISBN"
        Barcode.TYPE_PRODUCT -> "Product"
        else -> "Text"
    }

    private fun escapeWifiField(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
    }

    private fun saveImageToPictures(cacheFile: File, fileName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
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
        return if (cleaned.isBlank()) "Folio_QR_${System.currentTimeMillis()}" else cleaned.take(64)
    }
}
