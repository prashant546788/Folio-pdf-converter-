package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.model.PdfMetadataConfig
import com.example.model.PdfSecurityConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PdfSecurityInspection(
    val isPasswordRequiredToOpen: Boolean,
    val isEncrypted: Boolean,
    val allowPrinting: Boolean,
    val allowCopying: Boolean,
    val allowModifying: Boolean,
    val allowAnnotations: Boolean
)

class PdfSecurityEngine(private val context: Context) {

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

    suspend fun applyProtectionInPlace(
        filePath: String,
        security: PdfSecurityConfig
    ): Long = withContext(Dispatchers.IO) {
        if (!security.hasAnyRestrictionOrPassword()) {
            return@withContext File(filePath).length()
        }
        ensureInitialized()
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            throw IllegalStateException("PDF file not found for encryption.")
        }

        val tempFile = File(sourceFile.parentFile, "${sourceFile.name}.enc.tmp")
        try {
            PDDocument.load(sourceFile).use { document ->
                protectDocument(document, security)
                document.save(tempFile)
            }
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw IllegalStateException("Failed to write encrypted PDF.")
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

    suspend fun protectExistingPdfToNewFile(
        uri: Uri?,
        localFilePath: String?,
        existingPassword: String?,
        baseName: String,
        security: PdfSecurityConfig,
        metadata: PdfMetadataConfig = PdfMetadataConfig()
    ): GeneratedPdfInfo = withContext(Dispatchers.IO) {
        if (!security.hasAnyRestrictionOrPassword()) {
            throw IllegalArgumentException("Enable at least a password or permission restriction.")
        }
        ensureInitialized()
        val safeBase = sanitizeBase("${baseName.removeSuffix(".pdf")}_protected")
        val outFile = createUniquePdfFile(safeBase)
        val pwd = existingPassword.orEmpty()

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

        var pageCount = 0
        try {
            document.use { doc ->
                pageCount = doc.numberOfPages
                // Strip any prior encryption wrapper before applying the new security policy
                doc.isAllSecurityToBeRemoved = true
                if (metadata.hasAnyCustomMetadata()) {
                    val info = doc.documentInformation ?: PDDocumentInformation()
                    if (metadata.title.isNotBlank()) info.title = metadata.title.trim()
                    if (metadata.author.isNotBlank()) info.author = metadata.author.trim()
                    if (metadata.subject.isNotBlank()) info.subject = metadata.subject.trim()
                    val kws = metadata.parsedKeywordList().joinToString(", ")
                    if (kws.isNotBlank()) info.keywords = kws
                    if (metadata.creator.isNotBlank()) info.creator = metadata.creator.trim()
                    info.producer = "Folio PDF"
                    info.modificationDate = java.util.Calendar.getInstance()
                    doc.documentInformation = info
                }
                protectDocument(doc, security)
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

    suspend fun verifyPasswordAndInspect(
        filePath: String,
        password: String
    ): Result<PdfSecurityInspection> = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching {
            val file = File(filePath)
            if (!file.exists()) error("PDF file missing.")
            PDDocument.load(file, password).use { doc ->
                val ap = doc.currentAccessPermission
                PdfSecurityInspection(
                    isPasswordRequiredToOpen = false,
                    isEncrypted = doc.isEncrypted,
                    allowPrinting = ap?.canPrint() ?: true,
                    allowCopying = ap?.canExtractContent() ?: true,
                    allowModifying = ap?.canModify() ?: true,
                    allowAnnotations = ap?.canModifyAnnotations() ?: true
                )
            }
        }
    }

    suspend fun needsPasswordToOpen(filePath: String): Boolean = withContext(Dispatchers.IO) {
        ensureInitialized()
        val file = File(filePath)
        if (!file.exists()) return@withContext false
        try {
            PDDocument.load(file).use { false }
        } catch (_: InvalidPasswordException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun renderProtectedPage(
        filePath: String,
        password: String,
        pageIndex: Int,
        targetWidthPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        ensureInitialized()
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        PDDocument.load(file, password).use { doc ->
            if (pageIndex !in 0 until doc.numberOfPages) return@withContext null
            val page = doc.getPage(pageIndex)
            val pageWidthPt = page.mediaBox?.width?.coerceAtLeast(100f) ?: 595f
            val scale = (targetWidthPx.toFloat() / pageWidthPt).coerceIn(0.6f, 3.2f)
            val renderer = PDFRenderer(doc)
            renderer.renderImage(pageIndex, scale)
        }
    }

    private fun protectDocument(document: PDDocument, security: PdfSecurityConfig) {
        val ap = AccessPermission().apply {
            setCanPrint(security.allowPrinting)
            setCanPrintDegraded(security.allowPrinting)
            setCanExtractContent(security.allowCopying)
            setCanExtractForAccessibility(security.allowCopying)
            setCanModify(security.allowModifying)
            setCanAssembleDocument(security.allowModifying)
            setCanModifyAnnotations(security.allowAnnotations)
            setCanFillInForm(security.allowModifying || security.allowAnnotations)
        }

        // Standard PDF protection requires an owner password to enforce permission flags.
        val effectiveOwnerPassword = when {
            security.ownerPassword.isNotEmpty() -> security.ownerPassword
            security.userPassword.isNotEmpty() -> "${security.userPassword}_owner_${UUID.randomUUID().toString().take(8)}"
            else -> "folio_owner_${UUID.randomUUID().toString().take(12)}"
        }

        val spp = StandardProtectionPolicy(
            effectiveOwnerPassword,
            security.userPassword,
            ap
        ).apply {
            encryptionKeyLength = security.strength.keyBits
        }
        document.protect(spp)
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
