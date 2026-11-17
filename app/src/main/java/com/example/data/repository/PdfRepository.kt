package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.ai.NotesAiRepository
import com.example.data.ai.PdfTranslationAiRepository
import com.example.data.cloud.CloudStorageRepository
import com.example.data.cloud.GoogleAuthLaunchOutcome
import com.example.data.local.CloudTokenStore
import com.example.data.local.ConvertedPdfDao
import com.example.data.local.ConvertedPdfEntity
import com.example.data.local.FolioDatabase
import com.example.data.pdf.DocumentContentParser
import com.example.data.pdf.ExportedImageInfo
import com.example.data.pdf.GeneratedPdfInfo
import com.example.data.pdf.ImageExportFormat
import com.example.data.pdf.OcrProcessor
import com.example.data.pdf.PdfCompressionLevel
import com.example.data.pdf.PdfCompressionOutcome
import com.example.data.pdf.PdfConversionEngine
import com.example.data.pdf.PdfMetadataEngine
import com.example.data.pdf.PdfMetadataReadResult
import com.example.data.pdf.PdfPrintEngine
import com.example.data.pdf.PdfSearchHit
import com.example.data.pdf.PdfSecurityEngine
import com.example.data.pdf.PdfSecurityInspection
import com.example.data.pdf.PdfToolboxEngine
import com.example.data.pdf.PdfWatermarkEngine
import com.example.data.pdf.PrintJobSummary
import com.example.data.pdf.PrintSheet
import com.example.data.pdf.QrAndScannerEngine
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.CloudUploadRecord
import com.example.model.ConversionSettings
import com.example.model.ExtractedNoteResult
import com.example.model.NoteAiModelTier
import com.example.model.NoteBatchItem
import com.example.model.NoteExtractionStyle
import com.example.model.PdfMetadataConfig
import com.example.model.PdfSecurityConfig
import com.example.model.PdfTranslationResult
import com.example.model.PdfTranslationTone
import com.example.model.PdfWatermarkConfig
import com.example.model.PrintColorMode
import com.example.model.PrintConfig
import com.example.model.PrintLayoutTool
import com.example.model.PrintOrientationMode
import com.example.model.PrintPageSubset
import com.example.model.PrintPagesPerSheet
import com.example.model.PrintPaperSize
import com.example.model.QrColorPreset
import com.example.model.QrContentType
import com.example.model.QueuedSourceItem
import com.example.model.RemoteDrivePdfItem
import com.example.model.ScannedBarcodeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

data class DriveSyncReport(
    val uploadedCount: Int,
    val downloadedCount: Int,
    val linkedCount: Int,
    val remoteFiles: List<RemoteDrivePdfItem>,
    val syncedAtMs: Long
)

data class PrintSourceMetadata(
    val fileName: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val requiresPassword: Boolean,
    val allowPrinting: Boolean
)

class PdfRepository(private val context: Context) {
    private val database = FolioDatabase.getInstance(context)
    private val dao: ConvertedPdfDao = database.convertedPdfDao()
    private val tokenStore = CloudTokenStore(context)
    private val parser = DocumentContentParser(context)
    private val ocrProcessor = OcrProcessor()
    private val engine = PdfConversionEngine(context, parser, ocrProcessor)
    private val toolboxEngine = PdfToolboxEngine(context, ocrProcessor)
    private val securityEngine = PdfSecurityEngine(context)
    private val metadataEngine = PdfMetadataEngine(context)
    private val watermarkEngine = PdfWatermarkEngine(context)
    private val printEngine = PdfPrintEngine(context)
    val qrEngine = QrAndScannerEngine(context)
    private val cloudRepo = CloudStorageRepository(context, tokenStore)
    private val notesAiRepo = NotesAiRepository(context)
    private val translationAiRepo = PdfTranslationAiRepository(context)

    val allDocuments: Flow<List<ConvertedPdfEntity>> = dao.observeAll()
    val settingsFlow: Flow<ConversionSettings> = tokenStore.settingsFlow
    val cloudAccountsFlow: Flow<List<CloudAccountState>> = cloudRepo.observeAccounts()
    val autoSyncDriveFlow: Flow<Boolean> = tokenStore.autoSyncDriveFlow
    val lastDriveSyncAtFlow: Flow<Long> = tokenStore.lastDriveSyncAtFlow

    suspend fun saveSettings(settings: ConversionSettings) {
        tokenStore.saveSettings(settings)
    }

    suspend fun setAutoSyncDrive(enabled: Boolean) {
        tokenStore.setAutoSyncDrive(enabled)
    }

    suspend fun inspectUris(uris: List<Uri>): List<QueuedSourceItem> {
        return uris.map { parser.inspectUri(it) }
    }

    suspend fun convertAndSave(
        items: List<QueuedSourceItem>,
        desiredName: String,
        settings: ConversionSettings,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        batchId: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        val generated = engine.convertToPdf(
            items = items,
            desiredOutputName = desiredName,
            settings = settings,
            onProgress = onProgress
        )

        val afterWatermarkBytes = applyOptionalWatermark(
            filePath = generated.filePath,
            initialSizeBytes = generated.fileSizeBytes,
            watermark = watermark,
            onProgress = onProgress
        )

        val afterMetadataBytes = applyOptionalMetadata(
            filePath = generated.filePath,
            initialSizeBytes = afterWatermarkBytes,
            metadata = metadata,
            fallbackTitle = desiredName.removeSuffix(".pdf"),
            onProgress = onProgress
        )

        val finalSizeBytes = applyOptionalSecurity(
            filePath = generated.filePath,
            initialSizeBytes = afterMetadataBytes,
            security = security,
            onProgress = onProgress
        )

        val summary = buildString {
            if (items.size == 1) {
                append(items.first().sourceKind.badge)
                append(" · ")
                append(items.first().displayName)
            } else {
                append("${items.size} files")
            }
            if (settings.enableOcr && generated.fullText.isNotBlank()) {
                append(" · OCR")
            }
            if (watermark.isActive()) {
                append(" · Watermarked")
            }
            if (security.hasAnyRestrictionOrPassword()) {
                append(" · Protected")
            }
        }

        val entity = ConvertedPdfEntity(
            fileName = generated.fileName,
            filePath = generated.filePath,
            pageCount = generated.pageCount,
            fileSizeBytes = finalSizeBytes,
            sourceSummary = summary,
            ocrEnabled = settings.enableOcr && generated.fullText.isNotBlank(),
            ocrText = generated.fullText,
            pageTextsJson = generated.pageTextsJson,
            batchId = batchId,
            isPasswordProtected = security.hasAnyRestrictionOrPassword() && security.userPassword.isNotEmpty(),
            allowPrinting = if (security.hasAnyRestrictionOrPassword()) security.allowPrinting else true,
            allowCopying = if (security.hasAnyRestrictionOrPassword()) security.allowCopying else true,
            allowModifying = if (security.hasAnyRestrictionOrPassword()) security.allowModifying else false,
            securitySummary = if (security.hasAnyRestrictionOrPassword()) security.buildSummary() else null
        )
        val newId = dao.insert(entity)
        val inserted = entity.copy(id = newId)
        return maybeAutoBackupToDrive(inserted)
    }

    suspend fun getDocumentById(id: Long): ConvertedPdfEntity? = dao.getById(id)

    fun observeDocumentById(id: Long): Flow<ConvertedPdfEntity?> = dao.observeById(id)

    suspend fun deleteDocument(entity: ConvertedPdfEntity) {
        val file = File(entity.filePath)
        if (file.exists()) {
            file.delete()
        }
        dao.deleteById(entity.id)
    }

    suspend fun saveToDownloads(entity: ConvertedPdfEntity): Result<String> =
        engine.savePdfToDownloads(entity.filePath, entity.fileName)

    suspend fun exportToUri(entity: ConvertedPdfEntity, destinationUri: Uri): Boolean =
        engine.exportPdfToUri(entity.filePath, destinationUri)

    suspend fun needsPasswordToOpen(filePath: String): Boolean =
        securityEngine.needsPasswordToOpen(filePath)

    suspend fun verifyPdfPassword(filePath: String, password: String): Result<PdfSecurityInspection> =
        securityEngine.verifyPasswordAndInspect(filePath, password)

    suspend fun renderPdfPage(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int,
        password: String? = null
    ): Bitmap? {
        if (!password.isNullOrEmpty()) {
            return securityEngine.renderProtectedPage(filePath, password, pageIndex, targetWidthPx)
        }
        return try {
            engine.renderPdfPage(filePath, pageIndex, targetWidthPx)
        } catch (_: SecurityException) {
            // Handle owner-locked PDFs (empty user password) on API levels where PdfRenderer rejects encryption
            runCatching {
                securityEngine.renderProtectedPage(filePath, "", pageIndex, targetWidthPx)
            }.getOrNull()
        }
    }

    suspend fun searchPdf(
        entity: ConvertedPdfEntity,
        query: String
    ): List<PdfSearchHit> = engine.searchPdf(entity.filePath, query, entity.pageTextsJson)

    fun parsePageTexts(pageTextsJson: String): List<String> =
        engine.parsePageTexts(pageTextsJson)

    suspend fun authorizeGoogleDrive(): GoogleAuthLaunchOutcome =
        cloudRepo.authorizeGoogleDrive()

    suspend fun handleGoogleAuthIntentResult(data: Intent?): Result<CloudAccountState> =
        cloudRepo.handleGoogleAuthIntentResult(data)

    suspend fun launchBrowserOAuth(provider: CloudProvider): Result<Unit> =
        cloudRepo.launchBrowserOAuth(provider)

    suspend fun handleOAuthRedirect(uri: Uri): Result<CloudAccountState> =
        cloudRepo.handleOAuthRedirect(uri)

    suspend fun disconnectCloud(provider: CloudProvider) =
        cloudRepo.disconnect(provider)

    suspend fun uploadToCloud(
        entity: ConvertedPdfEntity,
        provider: CloudProvider
    ): Result<CloudUploadRecord> {
        val props = buildDriveMetadataProperties(entity)
        val result = cloudRepo.uploadPdf(
            provider = provider,
            filePath = entity.filePath,
            fileName = entity.fileName,
            existingDriveFileId = if (provider == CloudProvider.GOOGLE_DRIVE) entity.driveFileId else null,
            metadataProperties = if (provider == CloudProvider.GOOGLE_DRIVE) props else emptyMap()
        )
        result.onSuccess { record ->
            if (provider == CloudProvider.GOOGLE_DRIVE) {
                dao.updateDriveSync(
                    id = entity.id,
                    providerName = provider.displayName,
                    remoteUrl = record.remotePathOrLink,
                    driveFileId = record.remoteFileId,
                    syncedAtMs = record.uploadedAtMs
                )
            } else {
                dao.updateCloudSync(entity.id, provider.displayName, record.remotePathOrLink)
            }
        }
        return result
    }

    suspend fun listGoogleDrivePdfsAnnotated(): Result<List<RemoteDrivePdfItem>> {
        return cloudRepo.listGoogleDrivePdfs().map { remoteList ->
            val localDocs = dao.getAllOnce()
            remoteList.map { remote ->
                val matched = localDocs.firstOrNull { local ->
                    local.driveFileId == remote.driveFileId ||
                        (local.fileName.equals(remote.fileName, ignoreCase = true) && File(local.filePath).exists())
                }
                remote.copy(localDocumentId = matched?.id)
            }
        }
    }

    suspend fun downloadDrivePdfToVault(remote: RemoteDrivePdfItem): Result<ConvertedPdfEntity> = runCatching {
        val existing = dao.getByDriveFileId(remote.driveFileId)
            ?: dao.getByFileName(remote.fileName)?.takeIf { File(it.filePath).exists() }
        if (existing != null && File(existing.filePath).exists()) {
            dao.updateDriveSync(
                id = existing.id,
                providerName = CloudProvider.GOOGLE_DRIVE.displayName,
                remoteUrl = remote.webViewLink ?: remote.driveFileId,
                driveFileId = remote.driveFileId,
                syncedAtMs = System.currentTimeMillis()
            )
            return@runCatching existing.copy(
                driveFileId = remote.driveFileId,
                lastUploadedProvider = CloudProvider.GOOGLE_DRIVE.displayName,
                lastUploadedUrl = remote.webViewLink ?: remote.driveFileId
            )
        }

        val pdfDir = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }
        val cleanName = remote.fileName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .let { if (it.lowercase().endsWith(".pdf")) it else "$it.pdf" }
        var destFile = File(pdfDir, cleanName)
        var counter = 2
        while (destFile.exists()) {
            val base = cleanName.removeSuffix(".pdf")
            destFile = File(pdfDir, "${base}_$counter.pdf")
            counter++
        }

        cloudRepo.downloadGoogleDrivePdf(remote.driveFileId, destFile).getOrThrow()

        val needsPwd = securityEngine.needsPasswordToOpen(destFile.absolutePath)
        val inspection = if (!needsPwd) {
            securityEngine.verifyPasswordAndInspect(destFile.absolutePath, "").getOrNull()
        } else null

        val inspectedPageCount = if (remote.pageCountHint > 0) {
            remote.pageCountHint
        } else {
            runCatching {
                android.os.ParcelFileDescriptor.open(
                    destFile,
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
                ).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
                }
            }.getOrDefault(1)
        }

        val now = System.currentTimeMillis()
        val entity = ConvertedPdfEntity(
            fileName = destFile.name,
            filePath = destFile.absolutePath,
            pageCount = inspectedPageCount.coerceAtLeast(1),
            fileSizeBytes = destFile.length(),
            createdAtMs = remote.modifiedTimeMs.takeIf { it > 0L } ?: now,
            sourceSummary = "Google Drive Sync",
            ocrEnabled = remote.ocrEnabledHint,
            ocrText = "",
            pageTextsJson = "[]",
            lastUploadedProvider = CloudProvider.GOOGLE_DRIVE.displayName,
            lastUploadedUrl = remote.webViewLink ?: remote.driveFileId,
            isPasswordProtected = needsPwd || remote.isPasswordProtectedHint,
            allowPrinting = inspection?.allowPrinting ?: true,
            allowCopying = inspection?.allowCopying ?: true,
            allowModifying = inspection?.allowModifying ?: false,
            securitySummary = remote.securitySummaryHint,
            driveFileId = remote.driveFileId,
            lastSyncedAtMs = now
        )
        val newId = dao.insert(entity)
        entity.copy(id = newId)
    }

    suspend fun syncAllWithGoogleDrive(downloadMissingFromCloud: Boolean = true): Result<DriveSyncReport> = runCatching {
        val initialRemote = cloudRepo.listGoogleDrivePdfs().getOrThrow()
        val localDocs = dao.getAllOnce()
        val remoteById = initialRemote.associateBy { it.driveFileId }
        val remoteByName = initialRemote.associateBy { it.fileName.lowercase() }

        var uploadedCount = 0
        var linkedCount = 0
        var downloadedCount = 0
        val now = System.currentTimeMillis()

        // 1. Back up local Vault PDFs that are not yet backed up on Google Drive
        for (doc in localDocs) {
            val file = File(doc.filePath)
            if (!file.exists()) continue
            val existingRemote = doc.driveFileId?.let { remoteById[it] }
                ?: remoteByName[doc.fileName.lowercase()]

            if (existingRemote == null) {
                val uploadRes = uploadToCloud(doc, CloudProvider.GOOGLE_DRIVE)
                if (uploadRes.isSuccess) {
                    uploadedCount++
                }
            } else if (doc.driveFileId != existingRemote.driveFileId) {
                dao.updateDriveSync(
                    id = doc.id,
                    providerName = CloudProvider.GOOGLE_DRIVE.displayName,
                    remoteUrl = existingRemote.webViewLink ?: existingRemote.driveFileId,
                    driveFileId = existingRemote.driveFileId,
                    syncedAtMs = now
                )
                linkedCount++
            }
        }

        // 2. Restore remote PDFs created on other devices
        if (downloadMissingFromCloud) {
            val refreshedLocals = dao.getAllOnce()
            for (remote in initialRemote) {
                val alreadyLocal = refreshedLocals.any { local ->
                    (local.driveFileId == remote.driveFileId || local.fileName.equals(remote.fileName, ignoreCase = true)) &&
                        File(local.filePath).exists()
                }
                if (!alreadyLocal) {
                    val dlRes = downloadDrivePdfToVault(remote)
                    if (dlRes.isSuccess) {
                        downloadedCount++
                    }
                }
            }
        }

        tokenStore.setLastDriveSyncAt(now)
        val finalAnnotated = listGoogleDrivePdfsAnnotated().getOrDefault(initialRemote)
        DriveSyncReport(
            uploadedCount = uploadedCount,
            downloadedCount = downloadedCount,
            linkedCount = linkedCount,
            remoteFiles = finalAnnotated,
            syncedAtMs = now
        )
    }

    suspend fun deleteDriveBackup(driveFileId: String): Result<Unit> {
        val result = cloudRepo.deleteGoogleDrivePdf(driveFileId)
        result.onSuccess {
            val linked = dao.getByDriveFileId(driveFileId)
            if (linked != null) {
                dao.updateDriveSync(
                    id = linked.id,
                    providerName = "",
                    remoteUrl = null,
                    driveFileId = null,
                    syncedAtMs = 0L
                )
            }
        }
        return result
    }

    private suspend fun maybeAutoBackupToDrive(entity: ConvertedPdfEntity): ConvertedPdfEntity {
        val autoEnabled = runCatching { tokenStore.autoSyncDriveFlow.first() }.getOrDefault(false)
        val hasGoogleToken = runCatching { tokenStore.getToken(CloudProvider.GOOGLE_DRIVE) != null }.getOrDefault(false)
        if (!autoEnabled || !hasGoogleToken) return entity

        val res = uploadToCloud(entity, CloudProvider.GOOGLE_DRIVE)
        return res.fold(
            onSuccess = { rec ->
                val now = rec.uploadedAtMs
                runCatching { tokenStore.setLastDriveSyncAt(now) }
                entity.copy(
                    lastUploadedProvider = CloudProvider.GOOGLE_DRIVE.displayName,
                    lastUploadedUrl = rec.remotePathOrLink,
                    driveFileId = rec.remoteFileId,
                    lastSyncedAtMs = now
                )
            },
            onFailure = { entity }
        )
    }

    private fun buildDriveMetadataProperties(entity: ConvertedPdfEntity): Map<String, String> = mapOf(
        "pageCount" to entity.pageCount.toString(),
        "ocrEnabled" to entity.ocrEnabled.toString(),
        "isPasswordProtected" to entity.isPasswordProtected.toString(),
        "securitySummary" to entity.securitySummary.orEmpty()
    )

    // --- Print Preview & All Printing PDF Tools ---

    fun computePrintSheetPlan(totalPages: Int, config: PrintConfig): PrintJobSummary =
        printEngine.computeSheetPlan(totalPages, config)

    suspend fun inspectPdfForPrint(localFilePath: String?, uri: Uri?): PrintSourceMetadata {
        if (localFilePath != null) {
            val f = File(localFilePath)
            val needsPwd = securityEngine.needsPasswordToOpen(localFilePath)
            val inspection = if (!needsPwd) {
                securityEngine.verifyPasswordAndInspect(localFilePath, "").getOrNull()
            } else null
            val pageCount = if (!needsPwd) {
                runCatching {
                    android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
                    }
                }.getOrDefault(0)
            } else 0
            return PrintSourceMetadata(
                fileName = f.name,
                pageCount = pageCount,
                fileSizeBytes = f.length(),
                requiresPassword = needsPwd,
                allowPrinting = inspection?.allowPrinting ?: true
            )
        } else if (uri != null) {
            val (name, pages, size) = toolboxEngine.inspectPdfUri(uri)
            return PrintSourceMetadata(
                fileName = name,
                pageCount = pages,
                fileSizeBytes = size,
                requiresPassword = pages == 0,
                allowPrinting = true
            )
        }
        throw IllegalStateException("No PDF source provided for print preview.")
    }

    suspend fun renderPrintPreviewSheet(
        localFilePath: String?,
        uri: Uri?,
        documentTitle: String,
        sheet: PrintSheet,
        totalSheets: Int,
        config: PrintConfig,
        password: String?,
        targetWidthPx: Int
    ): Bitmap? = printEngine.renderSheetPreview(
        localFilePath = localFilePath,
        uri = uri,
        documentTitle = documentTitle,
        sheet = sheet,
        totalSheets = totalSheets,
        config = config,
        password = password,
        targetWidthPx = targetWidthPx
    )

    suspend fun generateTempPrintPdf(
        localFilePath: String?,
        uri: Uri?,
        documentTitle: String,
        config: PrintConfig,
        password: String?,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): File {
        val printCacheDir = File(context.cacheDir, "print_jobs").apply { if (!exists()) mkdirs() }
        val outFile = File(printCacheDir, "folio_print_${System.currentTimeMillis()}.pdf")
        printEngine.generatePrintReadyPdf(
            localFilePath = localFilePath,
            uri = uri,
            documentTitle = documentTitle,
            config = config,
            password = password,
            outputFile = outFile,
            onProgress = onProgress
        )
        return outFile
    }

    suspend fun savePrintReadyPdfToVault(
        localFilePath: String?,
        uri: Uri?,
        documentTitle: String,
        config: PrintConfig,
        password: String?,
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): ConvertedPdfEntity {
        val suffix = buildString {
            append(config.layoutTool.label)
            if (config.pagesPerSheet.count > 1) append("_${config.pagesPerSheet.label}")
            if (config.colorMode == com.example.model.PrintColorMode.GRAYSCALE) append("_bw")
        }
        val outFile = printEngine.createVaultPrintOutputFile(documentTitle, suffix)
        val generated = printEngine.generatePrintReadyPdf(
            localFilePath = localFilePath,
            uri = uri,
            documentTitle = documentTitle,
            config = config,
            password = password,
            outputFile = outFile,
            onProgress = onProgress
        )
        val updatedSize = applyOptionalMetadata(
            filePath = generated.filePath,
            initialSizeBytes = generated.fileSizeBytes,
            metadata = metadata,
            fallbackTitle = documentTitle.removeSuffix(".pdf")
        )
        val summary = "Print Layout (${config.layoutTool.label} · ${config.paperSize.label} · ${config.colorMode.label})"
        val entity = ConvertedPdfEntity(
            fileName = generated.fileName,
            filePath = generated.filePath,
            pageCount = generated.pageCount,
            fileSizeBytes = updatedSize,
            sourceSummary = summary,
            ocrEnabled = false,
            ocrText = "",
            pageTextsJson = "[]"
        )
        val newId = dao.insert(entity)
        return maybeAutoBackupToDrive(entity.copy(id = newId))
    }

    fun launchSystemPrint(
        activityContext: Context,
        jobName: String,
        printReadyPdfFile: File,
        config: PrintConfig
    ) {
        printEngine.launchSystemPrint(activityContext, jobName, printReadyPdfFile, config)
    }

    suspend fun inspectNoteUris(uris: List<Uri>): List<NoteBatchItem> =
        notesAiRepo.inspectNoteUris(uris)

    suspend fun extractNotes(
        item: NoteBatchItem,
        style: NoteExtractionStyle,
        modelTier: NoteAiModelTier
    ): ExtractedNoteResult = notesAiRepo.extractNotes(item, style, modelTier)

    suspend fun saveNotesAsPdf(
        noteResult: ExtractedNoteResult,
        sourceName: String,
        settings: ConversionSettings,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        batchId: String? = null
    ): ConvertedPdfEntity {
        val baseTitle = "${sourceName.substringBeforeLast('.')}_Notes"
        val generated = engine.convertMarkdownToPdf(
            title = baseTitle,
            markdownContent = noteResult.toMarkdown(),
            settings = settings
        )
        val afterWatermark = applyOptionalWatermark(
            filePath = generated.filePath,
            initialSizeBytes = generated.fileSizeBytes,
            watermark = watermark
        )
        val afterMeta = applyOptionalMetadata(
            filePath = generated.filePath,
            initialSizeBytes = afterWatermark,
            metadata = metadata,
            fallbackTitle = baseTitle
        )
        val finalSize = applyOptionalSecurity(
            filePath = generated.filePath,
            initialSizeBytes = afterMeta,
            security = security
        )
        val hasSec = security.hasAnyRestrictionOrPassword()
        val entity = ConvertedPdfEntity(
            fileName = generated.fileName,
            filePath = generated.filePath,
            pageCount = generated.pageCount,
            fileSizeBytes = finalSize,
            sourceSummary = "AI Notes · $sourceName",
            ocrEnabled = true,
            ocrText = generated.fullText,
            pageTextsJson = generated.pageTextsJson,
            batchId = batchId,
            isPasswordProtected = hasSec && security.userPassword.isNotEmpty(),
            allowPrinting = if (hasSec) security.allowPrinting else true,
            allowCopying = if (hasSec) security.allowCopying else true,
            allowModifying = if (hasSec) security.allowModifying else false,
            securitySummary = if (hasSec) security.buildSummary() else null
        )
        val id = dao.insert(entity)
        return maybeAutoBackupToDrive(entity.copy(id = id))
    }

    // --- PDF Tools (PDF <-> Images, Merge, Split, Compress, Rotate) ---

    suspend fun inspectPdfUri(uri: Uri): Triple<String, Int, Long> =
        toolboxEngine.inspectPdfUri(uri)

    suspend fun convertPdfToImages(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        format: ImageExportFormat,
        dpi: Int,
        pageRangeExpr: String,
        existingPassword: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): List<ExportedImageInfo> = toolboxEngine.convertPdfToImages(
        uri = uri,
        localFilePath = localFilePath,
        baseName = baseName,
        format = format,
        dpi = dpi,
        pageRangeExpr = pageRangeExpr,
        existingPassword = existingPassword,
        onProgress = onProgress
    )

    suspend fun splitPdfAndSave(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        splitEachPage: Boolean,
        pageRangeExpr: String,
        enableOcr: Boolean,
        existingPassword: String? = null,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): List<ConvertedPdfEntity> {
        val generated = toolboxEngine.splitPdf(
            uri = uri,
            localFilePath = localFilePath,
            baseName = baseName,
            splitEachPage = splitEachPage,
            pageRangeExpr = pageRangeExpr,
            enableOcr = enableOcr,
            existingPassword = existingPassword,
            onProgress = onProgress
        )
        val batchId = if (generated.size > 1) UUID.randomUUID().toString() else null
        return generated.map { info ->
            insertGeneratedPdf(
                info = info,
                summary = if (splitEachPage) "Split · $baseName" else "Extracted ($pageRangeExpr) · $baseName",
                ocrEnabled = enableOcr && info.fullText.isNotBlank(),
                batchId = batchId,
                security = security,
                metadata = metadata,
                watermark = watermark,
                onProgress = onProgress
            )
        }
    }

    suspend fun compressPdfAndSave(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        level: PdfCompressionLevel,
        existingPassword: String? = null,
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): Pair<ConvertedPdfEntity, PdfCompressionOutcome> {
        val outcome = toolboxEngine.compressPdf(
            uri = uri,
            localFilePath = localFilePath,
            baseName = baseName,
            level = level,
            existingPassword = existingPassword,
            onProgress = onProgress
        )
        val entity = insertGeneratedPdf(
            info = outcome.pdfInfo,
            summary = "Compressed (${level.label}) · $baseName",
            ocrEnabled = false,
            batchId = null,
            metadata = metadata
        )
        return entity to outcome
    }

    suspend fun rotatePdfAndSave(
        uri: Uri?,
        localFilePath: String?,
        baseName: String,
        rotationDegrees: Int,
        enableOcr: Boolean,
        existingPassword: String? = null,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        val info = toolboxEngine.rotatePdf(
            uri = uri,
            localFilePath = localFilePath,
            baseName = baseName,
            rotationDegrees = rotationDegrees,
            enableOcr = enableOcr,
            existingPassword = existingPassword,
            onProgress = onProgress
        )
        return insertGeneratedPdf(
            info = info,
            summary = "Rotated ${rotationDegrees}° · $baseName",
            ocrEnabled = enableOcr && info.fullText.isNotBlank(),
            batchId = null,
            security = security,
            metadata = metadata,
            watermark = watermark,
            onProgress = onProgress
        )
    }

    suspend fun convertNUpPdfAndSave(
        uri: Uri?,
        localFilePath: String?,
        sourceDisplayName: String,
        outputBaseName: String,
        pagesPerSheet: PrintPagesPerSheet,
        paperSize: PrintPaperSize,
        orientation: PrintOrientationMode,
        pageRangeExpr: String,
        showBorders: Boolean,
        grayscale: Boolean,
        existingPassword: String?,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        val cleanBase = outputBaseName.removeSuffix(".pdf").ifBlank {
            "${sourceDisplayName.removeSuffix(".pdf")}_${pagesPerSheet.count}in1"
        }
        val trimmedRange = pageRangeExpr.trim()
        val subset = if (trimmedRange.isBlank() || trimmedRange.equals("all", ignoreCase = true)) {
            PrintPageSubset.ALL
        } else {
            PrintPageSubset.CUSTOM
        }
        val config = PrintConfig(
            layoutTool = PrintLayoutTool.N_UP,
            pagesPerSheet = pagesPerSheet,
            paperSize = paperSize,
            orientation = orientation,
            pageSubset = subset,
            customPageRange = trimmedRange,
            colorMode = if (grayscale) PrintColorMode.GRAYSCALE else PrintColorMode.COLOR,
            showPageBorders = showBorders,
            showHeaderFooter = true,
            dpi = 220
        )
        val outFile = printEngine.createVaultPrintOutputFile(cleanBase, "${pagesPerSheet.count}in1")
        val generated = printEngine.generatePrintReadyPdf(
            localFilePath = localFilePath,
            uri = uri,
            documentTitle = cleanBase,
            config = config,
            password = existingPassword?.ifBlank { null },
            outputFile = outFile,
            onProgress = { cur, total ->
                onProgress("Building ${pagesPerSheet.count}-in-1 sheet $cur of $total...")
            }
        )
        return insertGeneratedPdf(
            info = generated,
            summary = "${pagesPerSheet.count}-in-1 Page (${paperSize.label}) · $sourceDisplayName",
            ocrEnabled = false,
            batchId = null,
            security = security,
            metadata = metadata,
            watermark = watermark,
            onProgress = onProgress
        )
    }

    suspend fun createQuickTextPdfAndSave(
        title: String,
        markdownOrPlainText: String,
        settings: ConversionSettings,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        val cleanTitle = title.trim().ifBlank { "Quick_Notes" }
        onProgress("Formatting text with ${settings.fontFamily.label}...")
        val generated = engine.convertMarkdownToPdf(
            title = cleanTitle,
            markdownContent = markdownOrPlainText,
            settings = settings
        )
        return insertGeneratedPdf(
            info = generated,
            summary = "Text Studio (${settings.fontFamily.label}) · ${generated.pageCount} page(s)",
            ocrEnabled = generated.fullText.isNotBlank(),
            batchId = null,
            security = security,
            metadata = metadata,
            watermark = watermark,
            onProgress = onProgress
        )
    }

    suspend fun mergePdfsAndSave(
        sources: List<Pair<Uri?, String?>>,
        outputBaseName: String,
        enableOcr: Boolean,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        val info = toolboxEngine.mergePdfs(
            sources = sources,
            outputBaseName = outputBaseName,
            enableOcr = enableOcr,
            onProgress = onProgress
        )
        return insertGeneratedPdf(
            info = info,
            summary = "Merged ${sources.size} PDFs",
            ocrEnabled = enableOcr && info.fullText.isNotBlank(),
            batchId = null,
            security = security,
            metadata = metadata,
            watermark = watermark,
            onProgress = onProgress
        )
    }

    suspend fun protectExistingPdfAndSave(
        uri: Uri?,
        localFilePath: String?,
        existingPassword: String?,
        baseName: String,
        security: PdfSecurityConfig,
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        onProgress("Applying encryption & permissions...")
        val info = securityEngine.protectExistingPdfToNewFile(
            uri = uri,
            localFilePath = localFilePath,
            existingPassword = existingPassword,
            baseName = baseName,
            security = security,
            metadata = metadata
        )
        val hasSec = security.hasAnyRestrictionOrPassword()
        val entity = ConvertedPdfEntity(
            fileName = info.fileName,
            filePath = info.filePath,
            pageCount = info.pageCount,
            fileSizeBytes = info.fileSizeBytes,
            sourceSummary = "Protected · $baseName",
            ocrEnabled = false,
            ocrText = "",
            pageTextsJson = "[]",
            batchId = null,
            isPasswordProtected = hasSec && security.userPassword.isNotEmpty(),
            allowPrinting = if (hasSec) security.allowPrinting else true,
            allowCopying = if (hasSec) security.allowCopying else true,
            allowModifying = if (hasSec) security.allowModifying else false,
            securitySummary = if (hasSec) security.buildSummary() else null
        )
        val newId = dao.insert(entity)
        return maybeAutoBackupToDrive(entity.copy(id = newId))
    }

    suspend fun inspectPdfMetadata(
        uri: Uri?,
        localFilePath: String?,
        password: String? = null
    ): Result<PdfMetadataReadResult> =
        metadataEngine.readMetadata(uri = uri, localFilePath = localFilePath, password = password)

    suspend fun editPdfMetadataAndSave(
        uri: Uri?,
        localFilePath: String?,
        existingPassword: String?,
        outputBaseName: String,
        metadata: PdfMetadataConfig,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        preserveOcrEnabled: Boolean = false,
        preserveOcrText: String = "",
        preservePageTextsJson: String = "[]",
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        onProgress("Embedding PDF metadata...")
        val info = metadataEngine.updateExistingPdfMetadataToNewFile(
            uri = uri,
            localFilePath = localFilePath,
            existingPassword = existingPassword,
            outputBaseName = outputBaseName,
            metadata = metadata
        )
        val enrichedInfo = info.copy(
            fullText = preserveOcrText,
            pageTextsJson = preservePageTextsJson
        )
        val metaSummary = metadata.buildSummary().ifBlank { "Updated Properties" }
        return insertGeneratedPdf(
            info = enrichedInfo,
            summary = "Metadata ($metaSummary)",
            ocrEnabled = preserveOcrEnabled && preserveOcrText.isNotBlank(),
            batchId = null,
            security = security,
            metadata = PdfMetadataConfig(), // Already written inside updateExistingPdfMetadataToNewFile
            watermark = watermark,
            onProgress = onProgress
        )
    }

    fun resolveWatermarkImageName(uri: Uri): String =
        watermarkEngine.resolveImageDisplayName(uri)

    suspend fun applyWatermarkAndSave(
        uri: Uri?,
        localFilePath: String?,
        existingPassword: String?,
        sourceDisplayName: String,
        outputBaseName: String,
        watermark: PdfWatermarkConfig,
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        security: PdfSecurityConfig = PdfSecurityConfig(),
        preserveOcrEnabled: Boolean = false,
        preserveOcrText: String = "",
        preservePageTextsJson: String = "[]",
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        val cleanBase = outputBaseName.removeSuffix(".pdf").ifBlank {
            "${sourceDisplayName.removeSuffix(".pdf")}_watermarked"
        }
        val generated = watermarkEngine.watermarkExistingPdfToNewFile(
            uri = uri,
            localFilePath = localFilePath,
            existingPassword = existingPassword,
            outputBaseName = cleanBase,
            watermark = watermark,
            metadata = metadata,
            onProgress = onProgress
        )
        val effectiveText = preserveOcrText.ifBlank { generated.fullText }
        val enrichedInfo = generated.copy(
            fullText = effectiveText,
            pageTextsJson = preservePageTextsJson
        )
        val wmLabel = watermark.copy(enabled = true).buildSummary()
        return insertGeneratedPdf(
            info = enrichedInfo,
            summary = "Watermarked ($wmLabel)",
            ocrEnabled = (preserveOcrEnabled && preserveOcrText.isNotBlank()) || generated.fullText.isNotBlank(),
            batchId = null,
            security = security,
            metadata = PdfMetadataConfig(), // Already written inside watermarkExistingPdfToNewFile
            watermark = PdfWatermarkConfig(), // Already stamped above
            onProgress = onProgress
        )
    }

    suspend fun translatePdfAndSave(
        uri: Uri?,
        localFilePath: String?,
        sourceDisplayName: String,
        outputBaseName: String,
        sourceLanguage: String,
        targetLanguage: String,
        tone: PdfTranslationTone,
        pageRangeExpr: String,
        existingPassword: String?,
        settings: ConversionSettings,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): Pair<ConvertedPdfEntity, PdfTranslationResult> {
        val translation = translationAiRepo.translatePdf(
            uri = uri,
            localFilePath = localFilePath,
            displayName = sourceDisplayName,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            tone = tone,
            pageRangeExpr = pageRangeExpr,
            existingPassword = existingPassword,
            onProgress = onProgress
        )
        val cleanBase = outputBaseName.removeSuffix(".pdf").ifBlank {
            val safeLang = targetLanguage.trim().replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_')
            "${sourceDisplayName.removeSuffix(".pdf")}_${safeLang.ifBlank { "translated" }}"
        }
        onProgress("Formatting ${translation.targetLanguage} PDF pages...")
        val generated = engine.convertMarkdownToPdf(
            title = cleanBase,
            markdownContent = translation.toMarkdown(),
            settings = settings
        )
        val effectiveMetadata = if (metadata.title.isBlank() && translation.translatedTitle.isNotBlank()) {
            metadata.copy(title = translation.translatedTitle)
        } else {
            metadata
        }
        val entity = insertGeneratedPdf(
            info = generated,
            summary = "Translated (${translation.detectedSourceLanguage} → ${translation.targetLanguage})",
            ocrEnabled = generated.fullText.isNotBlank(),
            batchId = null,
            security = security,
            metadata = effectiveMetadata,
            watermark = watermark,
            onProgress = onProgress
        )
        return entity to translation
    }

    // --- QR Code & Document Scanner Helpers ---

    fun buildQrPayload(
        type: QrContentType,
        primary: String,
        secondary: String = "",
        tertiary: String = "",
        wifiSecurity: String = "WPA"
    ): String = qrEngine.buildQrPayload(type, primary, secondary, tertiary, wifiSecurity)

    fun generateQrBitmap(
        payload: String,
        sizePx: Int = 720,
        preset: QrColorPreset = QrColorPreset.CLASSIC,
        caption: String = ""
    ): Bitmap = qrEngine.generateQrBitmap(payload, sizePx, preset, caption)

    suspend fun saveQrPng(bitmap: Bitmap, baseName: String): Pair<File, String> =
        qrEngine.saveQrPng(bitmap, baseName)

    suspend fun saveQrAsPdfToVault(
        bitmap: Bitmap,
        payload: String,
        caption: String,
        outputBaseName: String,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig()
    ): ConvertedPdfEntity {
        val generated = qrEngine.generateQrPdf(
            bitmap = bitmap,
            payload = payload,
            caption = caption,
            outputBaseName = outputBaseName
        )
        return insertGeneratedPdf(
            info = generated,
            summary = "QR Code PDF · ${caption.ifBlank { outputBaseName }}",
            ocrEnabled = false,
            batchId = null,
            security = security,
            metadata = metadata,
            watermark = watermark
        )
    }

    suspend fun scanBarcodesFromImageUri(uri: Uri): List<ScannedBarcodeItem> =
        qrEngine.scanBarcodesFromImageUri(uri)

    suspend fun importScannedPdfFromUri(
        pdfUri: Uri,
        desiredName: String,
        pageCountHint: Int,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig()
    ): ConvertedPdfEntity {
        val pdfDir = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }
        val cleanBase = engine.sanitizeFileName(desiredName.removeSuffix(".pdf"))
        var dest = File(pdfDir, "$cleanBase.pdf")
        var counter = 2
        while (dest.exists()) {
            dest = File(pdfDir, "${cleanBase}_$counter.pdf")
            counter++
        }
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to read scanned PDF.")

        val actualPages = runCatching {
            android.os.ParcelFileDescriptor.open(dest, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
            }
        }.getOrDefault(pageCountHint.coerceAtLeast(1))

        val info = GeneratedPdfInfo(
            fileName = dest.name,
            filePath = dest.absolutePath,
            pageCount = actualPages,
            fileSizeBytes = dest.length(),
            fullText = "",
            pageTextsJson = "[]"
        )
        return insertGeneratedPdf(
            info = info,
            summary = "Document Scan ($actualPages page${if (actualPages == 1) "" else "s"})",
            ocrEnabled = false,
            batchId = null,
            security = security,
            metadata = metadata,
            watermark = watermark
        )
    }

    private suspend fun applyOptionalWatermark(
        filePath: String,
        initialSizeBytes: Long,
        watermark: PdfWatermarkConfig,
        onProgress: suspend (String) -> Unit = {}
    ): Long {
        if (!watermark.isActive()) return initialSizeBytes
        onProgress("Stamping copyright watermark...")
        return watermarkEngine.applyWatermarkInPlace(filePath, watermark)
    }

    private suspend fun applyOptionalMetadata(
        filePath: String,
        initialSizeBytes: Long,
        metadata: PdfMetadataConfig,
        fallbackTitle: String? = null,
        onProgress: suspend (String) -> Unit = {}
    ): Long {
        val effectiveTitle = metadata.title.ifBlank { fallbackTitle.orEmpty() }
        if (!metadata.hasAnyCustomMetadata() && effectiveTitle.isBlank()) {
            return initialSizeBytes
        }
        onProgress("Writing PDF metadata (Title, Author, Keywords)...")
        return metadataEngine.applyMetadataInPlace(
            filePath = filePath,
            metadata = metadata,
            fallbackTitle = fallbackTitle
        )
    }

    private suspend fun applyOptionalSecurity(
        filePath: String,
        initialSizeBytes: Long,
        security: PdfSecurityConfig,
        onProgress: suspend (String) -> Unit = {}
    ): Long {
        if (!security.hasAnyRestrictionOrPassword()) return initialSizeBytes
        onProgress("Encrypting PDF & embedding permissions...")
        return securityEngine.applyProtectionInPlace(filePath, security)
    }

    private suspend fun insertGeneratedPdf(
        info: GeneratedPdfInfo,
        summary: String,
        ocrEnabled: Boolean,
        batchId: String?,
        security: PdfSecurityConfig = PdfSecurityConfig(),
        metadata: PdfMetadataConfig = PdfMetadataConfig(),
        watermark: PdfWatermarkConfig = PdfWatermarkConfig(),
        onProgress: suspend (String) -> Unit = {}
    ): ConvertedPdfEntity {
        val afterWatermark = applyOptionalWatermark(
            filePath = info.filePath,
            initialSizeBytes = info.fileSizeBytes,
            watermark = watermark,
            onProgress = onProgress
        )
        val afterMeta = applyOptionalMetadata(
            filePath = info.filePath,
            initialSizeBytes = afterWatermark,
            metadata = metadata,
            fallbackTitle = info.fileName.removeSuffix(".pdf"),
            onProgress = onProgress
        )
        val finalSize = applyOptionalSecurity(
            filePath = info.filePath,
            initialSizeBytes = afterMeta,
            security = security,
            onProgress = onProgress
        )
        val finalSummary = if (watermark.isActive()) "$summary · Watermarked" else summary
        val hasSec = security.hasAnyRestrictionOrPassword()
        val entity = ConvertedPdfEntity(
            fileName = info.fileName,
            filePath = info.filePath,
            pageCount = info.pageCount,
            fileSizeBytes = finalSize,
            sourceSummary = finalSummary,
            ocrEnabled = ocrEnabled,
            ocrText = info.fullText,
            pageTextsJson = info.pageTextsJson,
            batchId = batchId,
            isPasswordProtected = hasSec && security.userPassword.isNotEmpty(),
            allowPrinting = if (hasSec) security.allowPrinting else true,
            allowCopying = if (hasSec) security.allowCopying else true,
            allowModifying = if (hasSec) security.allowModifying else false,
            securitySummary = if (hasSec) security.buildSummary() else null
        )
        val newId = dao.insert(entity)
        return maybeAutoBackupToDrive(entity.copy(id = newId))
    }
}
