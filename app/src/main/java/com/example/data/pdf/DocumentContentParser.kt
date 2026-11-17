package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import android.text.Spanned
import android.util.Xml
import com.example.model.QueuedSourceItem
import com.example.model.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

sealed class DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock()
    data class Paragraph(val text: String) : DocBlock()
    data class Bullet(val text: String) : DocBlock()
    data class Quote(val text: String) : DocBlock()
    data class CodeLine(val text: String) : DocBlock()
    data class TableRow(val cells: List<String>, val isHeader: Boolean) : DocBlock()
    data object Divider : DocBlock()
    data class RichHtml(val spanned: Spanned) : DocBlock()
}

class DocumentContentParser(private val context: Context) {

    suspend fun inspectUri(uri: Uri): QueuedSourceItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        var displayName = "Document"
        var sizeBytes = 0L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        val kind = detectSourceKind(displayName, mimeType)
        QueuedSourceItem(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sourceKind = kind
        )
    }

    fun detectSourceKind(displayName: String, mimeType: String): SourceKind {
        val lowerName = displayName.lowercase()
        val lowerMime = mimeType.lowercase()
        return when {
            lowerMime.startsWith("image/") ||
                lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".webp") ||
                lowerName.endsWith(".bmp") ||
                lowerName.endsWith(".heic") -> SourceKind.IMAGE

            lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> SourceKind.PDF

            lowerMime.contains("wordprocessingml") || lowerName.endsWith(".docx") -> SourceKind.DOCX

            lowerMime == "text/markdown" || lowerName.endsWith(".md") || lowerName.endsWith(".markdown") ->
                SourceKind.MARKDOWN

            lowerMime == "text/csv" ||
                lowerMime == "text/comma-separated-values" ||
                lowerName.endsWith(".csv") ||
                lowerName.endsWith(".tsv") -> SourceKind.CSV

            lowerMime == "text/html" || lowerName.endsWith(".html") || lowerName.endsWith(".htm") ->
                SourceKind.HTML

            else -> SourceKind.TEXT
        }
    }

    suspend fun decodeBitmap(
        uri: Uri,
        maxEdgePx: Int,
        extraRotationDegrees: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val exifRotation = try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > maxEdgePx * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@withContext null

        val totalRotation = (exifRotation + extraRotationDegrees) % 360
        val longEdge = maxOf(decoded.width, decoded.height)
        val scale = if (longEdge > maxEdgePx) maxEdgePx.toFloat() / longEdge.toFloat() else 1f

        if (totalRotation == 0 && scale >= 0.99f) {
            return@withContext decoded
        }

        val matrix = Matrix().apply {
            if (scale < 0.99f) postScale(scale, scale)
            if (totalRotation != 0) postRotate(totalRotation.toFloat())
        }
        val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (transformed != decoded) {
            decoded.recycle()
        }
        transformed
    }

    suspend fun parseDocument(uri: Uri, kind: SourceKind): List<DocBlock> = withContext(Dispatchers.IO) {
        when (kind) {
            SourceKind.MARKDOWN -> parseMarkdown(readText(uri))
            SourceKind.CSV -> parseCsv(readText(uri))
            SourceKind.HTML -> {
                val html = readText(uri)
                val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                listOf(DocBlock.RichHtml(spanned))
            }
            SourceKind.DOCX -> parseDocx(uri)
            SourceKind.TEXT -> parsePlainText(readText(uri))
            else -> emptyList()
        }
    }

    private fun readText(uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    sb.append(line).append('\n')
                    line = reader.readLine()
                }
            }
        }
        return sb.toString()
    }

    private fun parsePlainText(raw: String): List<DocBlock> {
        if (raw.isBlank()) return listOf(DocBlock.Paragraph("(Empty document)"))
        return raw.lines().map { line ->
            if (line.isBlank()) DocBlock.Paragraph("")
            else DocBlock.Paragraph(line)
        }
    }

    fun parseMarkdown(raw: String): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        var inCodeBlock = false
        for (rawLine in raw.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            if (inCodeBlock) {
                blocks.add(DocBlock.CodeLine(rawLine))
                continue
            }
            when {
                trimmed == "---" || trimmed == "***" -> blocks.add(DocBlock.Divider)
                trimmed.startsWith("### ") -> blocks.add(DocBlock.Heading(3, cleanInlineMd(trimmed.drop(4))))
                trimmed.startsWith("## ") -> blocks.add(DocBlock.Heading(2, cleanInlineMd(trimmed.drop(3))))
                trimmed.startsWith("# ") -> blocks.add(DocBlock.Heading(1, cleanInlineMd(trimmed.drop(2))))
                trimmed.startsWith("> ") -> blocks.add(DocBlock.Quote(cleanInlineMd(trimmed.drop(2))))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                    blocks.add(DocBlock.Bullet(cleanInlineMd(trimmed.drop(2))))
                else -> blocks.add(DocBlock.Paragraph(cleanInlineMd(rawLine)))
            }
        }
        return blocks
    }

    private fun cleanInlineMd(input: String): String {
        return input
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
    }

    private fun parseCsv(raw: String): List<DocBlock> {
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return listOf(DocBlock.Paragraph("(Empty CSV)"))
        val delimiter = if (lines.first().contains('\t') && !lines.first().contains(',')) '\t' else ','
        return lines.mapIndexed { index, line ->
            val cells = splitCsvLine(line, delimiter)
            DocBlock.TableRow(cells = cells, isHeader = index == 0)
        }
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    cells.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString().trim())
        return cells
    }

    private fun parseDocx(uri: Uri): List<DocBlock> {
        val blocks = mutableListOf<DocBlock>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val parser = Xml.newPullParser()
                            parser.setInput(zip, "UTF-8")
                            var eventType = parser.eventType
                            val paragraphBuilder = StringBuilder()
                            var headingLevel = 0
                            var inParagraph = false

                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                val tag = parser.name
                                when (eventType) {
                                    XmlPullParser.START_TAG -> {
                                        when (tag) {
                                            "p" -> {
                                                inParagraph = true
                                                paragraphBuilder.clear()
                                                headingLevel = 0
                                            }
                                            "pStyle" -> {
                                                val styleVal = parser.getAttributeValue(null, "val")
                                                    ?: parser.getAttributeValue(
                                                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
                                                        "val"
                                                    )
                                                if (styleVal != null) {
                                                    when {
                                                        styleVal.contains("Heading1", ignoreCase = true) ||
                                                            styleVal.equals("Title", ignoreCase = true) -> headingLevel = 1
                                                        styleVal.contains("Heading2", ignoreCase = true) -> headingLevel = 2
                                                        styleVal.contains("Heading3", ignoreCase = true) -> headingLevel = 3
                                                    }
                                                }
                                            }
                                            "t" -> {
                                                if (inParagraph) {
                                                    paragraphBuilder.append(parser.nextText())
                                                }
                                            }
                                            "tab" -> if (inParagraph) paragraphBuilder.append("    ")
                                            "br" -> if (inParagraph) paragraphBuilder.append('\n')
                                        }
                                    }
                                    XmlPullParser.END_TAG -> {
                                        if (tag == "p" && inParagraph) {
                                            val text = paragraphBuilder.toString().trim()
                                            if (text.isNotEmpty()) {
                                                if (headingLevel > 0) {
                                                    blocks.add(DocBlock.Heading(headingLevel, text))
                                                } else {
                                                    blocks.add(DocBlock.Paragraph(text))
                                                }
                                            } else {
                                                blocks.add(DocBlock.Paragraph(""))
                                            }
                                            inParagraph = false
                                        }
                                    }
                                }
                                eventType = parser.next()
                            }
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            blocks.add(DocBlock.Paragraph("Unable to extract DOCX text: ${e.localizedMessage}"))
        }
        if (blocks.isEmpty()) {
            blocks.add(DocBlock.Paragraph("(No readable text found in DOCX document)"))
        }
        return blocks
    }
}
