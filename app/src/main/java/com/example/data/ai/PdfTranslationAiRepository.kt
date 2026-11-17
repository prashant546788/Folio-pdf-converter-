package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.model.PdfTranslationResult
import com.example.model.PdfTranslationTone
import com.example.model.TranslatedPdfPage
import com.example.model.TranslationLanguageCatalog
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

private const val TAG = "PdfTranslationAiRepo"

data class PdfPagePayload(
    val pageNumber: Int,
    val extractedText: String,
    val renderedBitmap: Bitmap? = null
)

data class TranslationBatchResponse(
    val detectedSourceLanguage: String,
    val translatedTitle: String,
    val pages: List<TranslatedPdfPage>
)

class PdfTranslationAiRepository(private val context: Context) {

    private val translationSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "detectedSourceLanguage" to Schema.string(
                    description = "Detected or confirmed source language name of the document in English (e.g. Spanish, Arabic, Japanese)"
                ),
                "translatedTitle" to Schema.string(
                    description = "Translated document title in the requested target language"
                ),
                "pages" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "pageNumber" to Schema.integer(description = "1-based original PDF page number"),
                            "heading" to Schema.string(description = "Short translated topic heading for this page"),
                            "translatedText" to Schema.string(
                                description = "Complete translated text for this page preserving paragraphs, bullet lists using '- ', and key structure"
                            )
                        )
                    ),
                    description = "Translated output for each page in the batch"
                )
            )
        )
    }

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
            }
        )
    }

    suspend fun translatePdf(
        uri: Uri?,
        localFilePath: String?,
        displayName: String,
        sourceLanguage: String,
        targetLanguage: String,
        tone: PdfTranslationTone,
        pageRangeExpr: String = "all",
        existingPassword: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): PdfTranslationResult {
        val cleanTarget = targetLanguage.trim()
        if (cleanTarget.isBlank()) {
            throw IllegalArgumentException("Please enter or select a target language.")
        }
        val cleanSource = sourceLanguage.trim().ifBlank { TranslationLanguageCatalog.AUTO_DETECT }
        val baseTitle = displayName.removeSuffix(".pdf").ifBlank { "Document" }

        onProgress("Reading PDF pages & extracting text...")
        val extractedPages = withContext(Dispatchers.IO) {
            extractSelectedPages(
                uri = uri,
                localFilePath = localFilePath,
                pageRangeExpr = pageRangeExpr,
                existingPassword = existingPassword
            )
        }

        if (extractedPages.isEmpty()) {
            throw IllegalStateException("No matching pages found to translate.")
        }

        val batches = partitionIntoBatches(extractedPages)
        val allTranslatedPages = mutableListOf<TranslatedPdfPage>()
        var detectedSource = if (cleanSource.equals(TranslationLanguageCatalog.AUTO_DETECT, ignoreCase = true)) {
            "Detected Language"
        } else {
            cleanSource
        }
        var translatedTitle = "$baseTitle ($cleanTarget)"

        try {
            batches.forEachIndexed { batchIdx, batch ->
                coroutineContext.ensureActive()
                val firstPage = batch.first().pageNumber
                val lastPage = batch.last().pageNumber
                val rangeLabel = if (firstPage == lastPage) "page $firstPage" else "pages $firstPage–$lastPage"
                onProgress("Translating $rangeLabel to $cleanTarget (${batchIdx + 1}/${batches.size})...")

                val prompt = buildBatchInstruction(
                    documentTitle = baseTitle,
                    sourceLanguage = cleanSource,
                    targetLanguage = cleanTarget,
                    tone = tone,
                    batch = batch
                )

                val multimodalContent = content {
                    text(prompt)
                    batch.forEach { page ->
                        page.renderedBitmap?.let { bmp ->
                            text("[Image of Page ${page.pageNumber} for visual OCR & translation]:")
                            image(bmp)
                        }
                    }
                }

                val response = try {
                    withTimeout(60_000L) {
                        translationModel.generateContent(multimodalContent)
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Translation batch timed out on $rangeLabel", e)
                    throw IllegalStateException(
                        "Translation timed out on $rangeLabel after 60 seconds. Try translating fewer pages at once."
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Translation request failed on $rangeLabel", e)
                    throw e
                } finally {
                    batch.forEach { it.renderedBitmap?.let { bmp -> if (!bmp.isRecycled) bmp.recycle() } }
                }

                val jsonText = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gemini returned an empty translation for $rangeLabel.")

                val parsed = parseTranslationBatchJson(
                    jsonString = jsonText,
                    fallbackSourceLanguage = detectedSource,
                    fallbackTitle = translatedTitle,
                    expectedPages = batch.map { it.pageNumber }
                )

                if (batchIdx == 0) {
                    detectedSource = parsed.detectedSourceLanguage
                    translatedTitle = parsed.translatedTitle
                }
                allTranslatedPages.addAll(parsed.pages)
            }
        } finally {
            extractedPages.forEach { it.renderedBitmap?.let { bmp -> if (!bmp.isRecycled) bmp.recycle() } }
        }

        return PdfTranslationResult(
            detectedSourceLanguage = detectedSource,
            targetLanguage = cleanTarget,
            translatedTitle = translatedTitle,
            tone = tone,
            pages = allTranslatedPages.sortedBy { it.pageNumber }
        )
    }

    private fun extractSelectedPages(
        uri: Uri?,
        localFilePath: String?,
        pageRangeExpr: String,
        existingPassword: String?
    ): List<PdfPagePayload> {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
        val tempPdf = File(context.cacheDir, "translate_src_${System.nanoTime()}.pdf")
        try {
            val pwd = existingPassword.orEmpty()
            val document = try {
                when {
                    localFilePath != null -> {
                        val file = File(localFilePath)
                        if (!file.exists()) throw IllegalStateException("PDF file not found.")
                        if (pwd.isEmpty()) PDDocument.load(file) else PDDocument.load(file, pwd)
                    }
                    uri != null -> {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            if (pwd.isEmpty()) PDDocument.load(input) else PDDocument.load(input, pwd)
                        } ?: throw IllegalStateException("Unable to read selected PDF.")
                    }
                    else -> throw IllegalStateException("Select a PDF file first.")
                }
            } catch (_: InvalidPasswordException) {
                throw IllegalStateException("This PDF is password-protected. Enter the PDF password to translate it.")
            }

            val pageTexts = mutableMapOf<Int, String>()
            val selectedZeroBased: List<Int>

            document.use { doc ->
                val totalPages = doc.numberOfPages
                selectedZeroBased = parsePageRange(pageRangeExpr, totalPages)
                if (selectedZeroBased.isEmpty()) {
                    throw IllegalArgumentException("No valid pages selected (PDF has $totalPages page(s)).")
                }

                val stripper = PDFTextStripper()
                for (pageIdx in selectedZeroBased) {
                    stripper.startPage = pageIdx + 1
                    stripper.endPage = pageIdx + 1
                    val rawText = runCatching { stripper.getText(doc) }.getOrDefault("").trim()
                    pageTexts[pageIdx] = rawText
                }

                val needsImageFallback = selectedZeroBased.any { idx ->
                    pageTexts[idx].orEmpty().count { !it.isWhitespace() } < 25
                }
                if (needsImageFallback) {
                    if (doc.isEncrypted) {
                        doc.isAllSecurityToBeRemoved = true
                    }
                    doc.save(tempPdf)
                }
            }

            val bitmapsByIndex = mutableMapOf<Int, Bitmap>()
            val imagePageIndices = selectedZeroBased.filter { idx ->
                pageTexts[idx].orEmpty().count { !it.isWhitespace() } < 25
            }
            if (imagePageIndices.isNotEmpty() && tempPdf.exists()) {
                ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (idx in imagePageIndices) {
                            if (idx !in 0 until renderer.pageCount) continue
                            renderer.openPage(idx).use { page ->
                                val maxEdge = 1100
                                val scale = minOf(
                                    maxEdge.toFloat() / page.width.coerceAtLeast(1),
                                    maxEdge.toFloat() / page.height.coerceAtLeast(1),
                                    2.0f
                                )
                                val bmpW = (page.width * scale).toInt().coerceIn(320, maxEdge)
                                val bmpH = (page.height * scale).toInt().coerceIn(320, maxEdge)
                                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmapsByIndex[idx] = bmp
                            }
                        }
                    }
                }
            }

            return selectedZeroBased.map { idx ->
                PdfPagePayload(
                    pageNumber = idx + 1,
                    extractedText = pageTexts[idx].orEmpty(),
                    renderedBitmap = bitmapsByIndex[idx]
                )
            }
        } finally {
            if (tempPdf.exists()) tempPdf.delete()
        }
    }

    internal fun partitionIntoBatches(pages: List<PdfPagePayload>): List<List<PdfPagePayload>> {
        val batches = mutableListOf<List<PdfPagePayload>>()
        var current = mutableListOf<PdfPagePayload>()
        var currentChars = 0
        var currentImages = 0

        for (page in pages) {
            val pageChars = page.extractedText.length
            val hasImg = page.renderedBitmap != null
            val exceedsTextBatch = current.isNotEmpty() && (current.size >= 6 || currentChars + pageChars > 14_000)
            val exceedsImageBatch = current.isNotEmpty() && (hasImg && currentImages >= 3)

            if (exceedsTextBatch || exceedsImageBatch) {
                batches.add(current)
                current = mutableListOf()
                currentChars = 0
                currentImages = 0
            }
            current.add(page)
            currentChars += pageChars
            if (hasImg) currentImages++
        }
        if (current.isNotEmpty()) {
            batches.add(current)
        }
        return batches
    }

    fun buildBatchInstruction(
        documentTitle: String,
        sourceLanguage: String,
        targetLanguage: String,
        tone: PdfTranslationTone,
        batch: List<PdfPagePayload>
    ): String = buildString {
        append("You are an expert multilingual document translator.\n")
        append("Document title: \"").append(documentTitle).append("\"\n")
        if (sourceLanguage.equals(TranslationLanguageCatalog.AUTO_DETECT, ignoreCase = true)) {
            append("Source language: Auto-detect the language of the document.\n")
        } else {
            append("Source language: ").append(sourceLanguage).append("\n")
        }
        append("Target language: ").append(targetLanguage).append("\n")
        append("Translation style requirement: ").append(tone.instruction).append("\n")
        append("Rules:\n")
        append("- Translate ALL text faithfully into ").append(targetLanguage)
            .append(" using native script and proper grammar.\n")
        append("- Preserve paragraph breaks, numbered/bulleted lists (prefix bullets with '- '), headings, and numbers/dates.\n")
        append("- Do NOT summarize or omit content.\n")
        append("- If a page image is attached (scanned PDF page), read all visible text on that page image and translate it completely.\n")
        append("- Return one entry in `pages` for each of these exact page numbers: ")
            .append(batch.joinToString(", ") { it.pageNumber.toString() }).append(".\n\n")

        batch.forEach { page ->
            append("=== PAGE ").append(page.pageNumber).append(" ===\n")
            if (page.extractedText.isNotBlank()) {
                append(page.extractedText).append("\n\n")
            } else {
                append("[Scanned page image provided below — read and translate all visible text from Page ")
                    .append(page.pageNumber).append("]\n\n")
            }
        }
    }

    fun parseTranslationBatchJson(
        jsonString: String,
        fallbackSourceLanguage: String,
        fallbackTitle: String,
        expectedPages: List<Int>
    ): TranslationBatchResponse {
        val json = JSONObject(jsonString)
        val detectedSource = json.optString("detectedSourceLanguage")
            .trim()
            .ifBlank { fallbackSourceLanguage }
        val translatedTitle = json.optString("translatedTitle")
            .trim()
            .ifBlank { fallbackTitle }

        val parsedPages = mutableListOf<TranslatedPdfPage>()
        val pagesArr = json.optJSONArray("pages")
        if (pagesArr != null) {
            for (i in 0 until pagesArr.length()) {
                val obj = pagesArr.optJSONObject(i) ?: continue
                val fallbackNum = expectedPages.getOrElse(i) { i + 1 }
                val pageNum = obj.optInt("pageNumber", fallbackNum).takeIf { it > 0 } ?: fallbackNum
                val heading = obj.optString("heading").trim()
                val body = obj.optString("translatedText").trim()
                if (body.isNotBlank()) {
                    parsedPages.add(
                        TranslatedPdfPage(
                            pageNumber = pageNum,
                            heading = heading,
                            translatedText = body
                        )
                    )
                }
            }
        }

        if (parsedPages.isEmpty()) {
            throw IllegalStateException("No translated page content was returned by the model.")
        }

        return TranslationBatchResponse(
            detectedSourceLanguage = detectedSource,
            translatedTitle = translatedTitle,
            pages = parsedPages
        )
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
}
