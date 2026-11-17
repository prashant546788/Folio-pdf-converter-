package com.example.data.pdf

import android.content.Context
import android.net.Uri
import com.example.model.PdfMetadataConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

data class PdfMetadataReadResult(
    val metadata: PdfMetadataConfig,
    val pageCount: Int,
    val producer: String,
    val isEncrypted: Boolean,
    val requiresPassword: Boolean
)

class PdfMetadataEngine(private val context: Context) {

    private val pdfDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    private fun ensureInitialized() {
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    suspend fun readMetadata(
        uri: Uri?,
        localFilePath: String?,
        password: String? = null
    ): Result<PdfMetadataReadResult> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val pwd = password.orEmpty()
        try {
            val document = openDocument(uri, localFilePath, pwd)
            document.use { doc ->
                val info = doc.documentInformation
                val meta = PdfMetadataConfig(
                    title = info?.title.orEmpty(),
                    author = info?.author.orEmpty(),
                    subject = info?.subject.orEmpty(),
                    keywords = info?.keywords.orEmpty(),
                    creator = info?.creator?.takeIf { it.isNotBlank() } ?: "Folio PDF"
                )
                Result.success(
                    PdfMetadataReadResult(
                        metadata = meta,
                        pageCount = doc.numberOfPages,
                        producer = info?.producer.orEmpty(),
                        isEncrypted = doc.isEncrypted,
                        requiresPassword = false
                    )
                )
            }
        } catch (_: InvalidPasswordException) {
            Result.success(
                PdfMetadataReadResult(
                    metadata = PdfMetadataConfig(),
                    pageCount = 0,
                    producer = "",
                    isEncrypted = true,
                    requiresPassword = true
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun applyMetadataInPlace(
        filePath: String,
        metadata: PdfMetadataConfig,
        fallbackTitle: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val effectiveTitle = metadata.title.ifBlank { fallbackTitle.orEmpty() }
        val shouldWrite = metadata.hasAnyCustomMetadata() || effectiveTitle.isNotBlank()
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            throw IllegalStateException("PDF file not found for metadata update.")
        }
        if (!shouldWrite) {
            return@withContext sourceFile.length()
        }

        ensureInitialized()
        val tempFile = File(sourceFile.parentFile, "${sourceFile.name}.meta.tmp")
        try {
            PDDocument.load(sourceFile).use { document ->
                writeDocumentInfo(
                    document = document,
                    metadata = metadata.copy(title = effectiveTitle)
                )
                document.save(tempFile)
            }
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Failed to embed PDF metadata.")
            }
            if (!sourceFile.delete() || !tempFile.renameTo(sourceFile)) {
                tempFile.copyTo(sourceFile, overwrite = true)
                tempFile.delete()
            }
            sourceFile.length()
        } catch (t: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            throw t
        }
    }

    suspend fun updateExistingPdfMetadataToNewFile(
        uri: Uri?,
        localFilePath: String?,
        existingPassword: String?,
        outputBaseName: String,
        metadata: PdfMetadataConfig
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        ensureInitialized()
        val safeBase = sanitizeBase(outputBaseName.removeSuffix(".pdf"))
        val outFile = createUniquePdfFile(safeBase)
        val pwd = existingPassword.orEmpty()

        val document = openDocument(uri, localFilePath, pwd)
        var pageCount = 0
        try {
            document.use { doc ->
                pageCount = doc.numberOfPages
                if (doc.isEncrypted) {
                    doc.isAllSecurityToBeRemoved = true
                }
                writeDocumentInfo(doc, metadata)
                FileOutputStream(outFile).use { fos ->
                    doc.save(fos)
                }
            }
        } catch (t: Throwable) {
            if (outFile.exists()) outFile.delete()
            throw t
        }

        GeneratedPdfInfo(
            fileName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pageCount,
            fileSizeBytes = outFile.length(),
            fullText = "",
            pageTextsJson = "[]"
        )
    }

    private fun openDocument(uri: Uri?, localFilePath: String?, pwd: String): PDDocument {
        return when {
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
    }

    private fun writeDocumentInfo(document: PDDocument, metadata: PdfMetadataConfig) {
        val info: PDDocumentInformation = document.documentInformation ?: PDDocumentInformation()
        val now = Calendar.getInstance()
        info.title = metadata.title.trim().ifEmpty { null }
        info.author = metadata.author.trim().ifEmpty { null }
        info.subject = metadata.subject.trim().ifEmpty { null }
        val normalizedKeywords = metadata.parsedKeywordList().joinToString(", ")
        info.keywords = normalizedKeywords.ifEmpty { null }
        info.creator = metadata.creator.trim().ifEmpty { "Folio PDF" }
        info.producer = "Folio PDF"
        if (info.creationDate == null) {
            info.creationDate = now
        }
        info.modificationDate = now
        document.documentInformation = info
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
