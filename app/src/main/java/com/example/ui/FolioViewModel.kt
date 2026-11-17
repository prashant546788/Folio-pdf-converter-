package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.GoogleAuthLaunchOutcome
import com.example.data.local.ConvertedPdfEntity
import com.example.data.pdf.ExportedImageInfo
import com.example.data.pdf.ImageExportFormat
import com.example.data.pdf.PdfCompressionLevel
import com.example.data.pdf.PdfSearchHit
import com.example.data.repository.PdfRepository
import com.example.ui.components.formatFileSize
import com.example.model.BatchItemState
import com.example.model.BatchStatus
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.CloudUploadRecord
import com.example.model.ConversionSettings
import com.example.model.NoteAiModelTier
import com.example.model.NoteBatchItem
import com.example.model.NoteBatchStatus
import com.example.model.NoteExtractionStyle
import com.example.model.NoteSourceKind
import com.example.model.PdfMetadataConfig
import com.example.model.PdfSecurityConfig
import com.example.model.PdfTranslationResult
import com.example.model.PdfTranslationTone
import com.example.model.PdfWatermarkConfig
import com.example.model.QrColorPreset
import com.example.model.QrContentType
import com.example.model.QueuedSourceItem
import com.example.model.ScannedBarcodeItem
import com.example.model.ScannedDocPage
import com.example.model.TranslationLanguageCatalog
import com.example.model.VaultFilter
import com.example.model.ViewerThemeMode
import com.example.ui.components.shareImageFile
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import com.example.data.pdf.PrintJobSummary
import com.example.model.PrintConfig
import com.example.model.PrintLayoutTool
import com.example.model.PrintOrientationMode
import com.example.model.PrintPagesPerSheet
import com.example.model.PrintPaperSize
import com.example.model.RemoteDrivePdfItem
import java.util.UUID

enum class ScannerTab(val label: String) {
    DOCUMENT("Document Scan"),
    QR_SCAN("Scan QR / Barcode"),
    QR_GENERATE("QR Generator")
}

sealed class FolioEvent {
    data class Snackbar(val message: String) : FolioEvent()
    data class OpenViewer(val documentId: Long) : FolioEvent()
    data object OpenPrintPreview : FolioEvent()
    data class OpenScannerQr(val initialTab: ScannerTab = ScannerTab.DOCUMENT) : FolioEvent()
    data class LaunchGoogleResolution(val intentSender: IntentSender) : FolioEvent()
    data class ProviderConfigRequired(val provider: CloudProvider, val instructions: String) : FolioEvent()
}

data class StudioUiState(
    val queue: List<QueuedSourceItem> = emptyList(),
    val outputName: String = "Folio_Document",
    val isConverting: Boolean = false,
    val progressMessage: String = "",
    val lastGeneratedDoc: ConvertedPdfEntity? = null
)

data class BatchUiState(
    val items: List<BatchItemState> = emptyList(),
    val isRunning: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val autoUploadProvider: CloudProvider? = null,
    val lastBatchSummary: String? = null
)

data class ViewerUiState(
    val document: ConvertedPdfEntity? = null,
    val currentPage: Int = 0,
    val zoomScale: Float = 1f,
    val pageBitmap: Bitmap? = null,
    val isLoadingPage: Boolean = false,
    val searchQuery: String = "",
    val searchHits: List<PdfSearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val pageTexts: List<String> = emptyList(),
    val requiresPassword: Boolean = false,
    val unlockedPassword: String? = null,
    val passwordInput: String = "",
    val passwordError: String? = null,
    val canCopyContent: Boolean = true,
    val canPrint: Boolean = true,
    val metadata: PdfMetadataConfig = PdfMetadataConfig(),
    val producer: String = "",
    val themeMode: ViewerThemeMode = ViewerThemeMode.ORIGINAL
)

data class NoteBatchUiState(
    val items: List<NoteBatchItem> = emptyList(),
    val style: NoteExtractionStyle = NoteExtractionStyle.STUDY_GUIDE,
    val modelTier: NoteAiModelTier = NoteAiModelTier.STANDARD,
    val isExtracting: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val lastSummary: String? = null
)

enum class PdfToolMode(val label: String, val subtitle: String) {
    CREATE_PDF("Create PDF", "Photos & files → PDF"),
    TRANSLATE_PDF("Translate PDF", "Translate any PDF from any language to any language"),
    FOUR_IN_ONE("4-in-1 Page", "Combine 2, 4, 6, or 9 PDF pages on 1 sheet"),
    QUICK_TEXT("Write → PDF", "Type or paste text directly into a styled PDF"),
    WATERMARK_PDF("Watermark", "Add text or image copyright stamps to PDF pages"),
    EDIT_METADATA("Edit Metadata", "Edit Title, Author, Subject & Keywords"),
    PRINT_PREVIEW("Print & Layout", "Print preview, N-up, booklet & ink-saver"),
    PDF_TO_IMAGES("PDF → Images", "Export PNG or JPG pages"),
    MERGE_PDFS("Merge PDFs", "Combine multiple PDFs"),
    SPLIT_PDF("Split PDF", "Extract or split pages"),
    COMPRESS_PDF("Compress PDF", "Shrink file size"),
    ROTATE_PDF("Rotate PDF", "Rotate all pages"),
    PROTECT_PDF("Protect PDF", "Passwords & permissions")
}

data class DriveSyncUiState(
    val autoSyncEnabled: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val downloadingDriveIds: Set<String> = emptySet(),
    val remoteFiles: List<RemoteDrivePdfItem> = emptyList(),
    val statusSummary: String? = null
)

data class PrintPreviewUiState(
    val documentId: Long? = null,
    val localFilePath: String? = null,
    val uri: Uri? = null,
    val fileName: String = "",
    val totalSourcePages: Int = 0,
    val fileSizeBytes: Long = 0L,
    val config: PrintConfig = PrintConfig(),
    val jobSummary: PrintJobSummary = PrintJobSummary(0, 0, emptyList(), 595, 842),
    val currentSheetIndex: Int = 0,
    val zoomScale: Float = 1f,
    val previewBitmap: Bitmap? = null,
    val isRenderingPreview: Boolean = false,
    val isGeneratingPrintPdf: Boolean = false,
    val progressMessage: String = "",
    val requiresPassword: Boolean = false,
    val unlockedPassword: String? = null,
    val passwordInput: String = "",
    val passwordError: String? = null,
    val allowPrinting: Boolean = true
)

data class SelectedPdfSource(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val localFilePath: String? = null,
    val displayName: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val vaultDoc: ConvertedPdfEntity? = null
)

data class PdfToolsUiState(
    val activeTool: PdfToolMode = PdfToolMode.CREATE_PDF,
    val singleSource: SelectedPdfSource? = null,
    val mergeSources: List<SelectedPdfSource> = emptyList(),
    val imageFormat: ImageExportFormat = ImageExportFormat.PNG,
    val imageDpi: Int = 220,
    val pageRangeExpr: String = "all",
    val splitEachPage: Boolean = true,
    val compressionLevel: PdfCompressionLevel = PdfCompressionLevel.BALANCED,
    val rotationDegrees: Int = 90,
    val mergeOutputName: String = "Merged_Folio",
    val existingPdfPassword: String = "",
    val metadataOutputName: String = "",
    val metadataNeedsPassword: Boolean = false,
    val metadataUnlockedPassword: String? = null,
    val metadataProducerInfo: String = "",
    val metadataPasswordError: String? = null,
    val nUpPagesPerSheet: PrintPagesPerSheet = PrintPagesPerSheet.FOUR,
    val nUpPaperSize: PrintPaperSize = PrintPaperSize.A4,
    val nUpOrientation: PrintOrientationMode = PrintOrientationMode.AUTO,
    val nUpShowBorders: Boolean = true,
    val nUpGrayscale: Boolean = false,
    val nUpOutputName: String = "",
    val quickTextTitle: String = "My_Notes",
    val quickTextBody: String = "",
    val watermarkOutputName: String = "",
    val translationSourceLanguage: String = TranslationLanguageCatalog.AUTO_DETECT,
    val translationTargetLanguage: String = "English",
    val translationTone: PdfTranslationTone = PdfTranslationTone.NATURAL,
    val translationOutputName: String = "",
    val lastTranslationResult: PdfTranslationResult? = null,
    val isProcessing: Boolean = false,
    val progressMessage: String = "",
    val exportedImages: List<ExportedImageInfo> = emptyList(),
    val lastToolPdfResults: List<ConvertedPdfEntity> = emptyList(),
    val compressionSummary: String? = null
)

data class QrGeneratorUiState(
    val contentType: QrContentType = QrContentType.TEXT_URL,
    val primaryText: String = "",
    val secondaryText: String = "",
    val tertiaryText: String = "",
    val wifiSecurity: String = "WPA",
    val caption: String = "",
    val colorPreset: QrColorPreset = QrColorPreset.CLASSIC,
    val encodedPayload: String = "",
    val previewBitmap: Bitmap? = null,
    val isSaving: Boolean = false,
    val lastSavedImagePath: String? = null,
    val lastSavedPdfDoc: ConvertedPdfEntity? = null
)

data class ScannerUiState(
    val activeTab: ScannerTab = ScannerTab.DOCUMENT,
    val scannedPages: List<ScannedDocPage> = emptyList(),
    val scannedDirectPdfUri: Uri? = null,
    val scanOutputName: String = "Scanned_Document",
    val isFinalizingScan: Boolean = false,
    val scanProgressMessage: String = "",
    val lastScannedDoc: ConvertedPdfEntity? = null,
    val isLiveCameraPaused: Boolean = false,
    val isTorchEnabled: Boolean = false,
    val scannedBarcodes: List<ScannedBarcodeItem> = emptyList(),
    val isScanningImageBarcode: Boolean = false,
    val qrGenerator: QrGeneratorUiState = QrGeneratorUiState()
)

class FolioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application.applicationContext)

    private val _events = MutableSharedFlow<FolioEvent>()
    val events: SharedFlow<FolioEvent> = _events.asSharedFlow()

    val settings: StateFlow<ConversionSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversionSettings())

    val cloudAccounts: StateFlow<List<CloudAccountState>> = repository.cloudAccountsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CloudProvider.entries.map { CloudAccountState(provider = it) }
        )

    private val _vaultQuery = MutableStateFlow("")
    val vaultQuery: StateFlow<String> = _vaultQuery.asStateFlow()

    private val _vaultFilter = MutableStateFlow(VaultFilter.ALL)
    val vaultFilter: StateFlow<VaultFilter> = _vaultFilter.asStateFlow()

    val vaultDocuments: StateFlow<List<ConvertedPdfEntity>> = combine(
        repository.allDocuments,
        _vaultQuery,
        _vaultFilter
    ) { docs, query, filter ->
        val filteredByChip = when (filter) {
            VaultFilter.ALL -> docs
            VaultFilter.OCR -> docs.filter { it.ocrEnabled && it.ocrText.isNotBlank() }
            VaultFilter.PROTECTED -> docs.filter { it.isPasswordProtected || !it.securitySummary.isNullOrBlank() }
            VaultFilter.BATCH -> docs.filter { it.batchId != null }
            VaultFilter.CLOUD -> docs.filter { !it.lastUploadedProvider.isNullOrBlank() }
        }
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) {
            filteredByChip
        } else {
            filteredByChip.filter { doc ->
                doc.fileName.lowercase().contains(trimmed) ||
                    doc.sourceSummary.lowercase().contains(trimmed) ||
                    doc.ocrText.lowercase().contains(trimmed)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _batchState = MutableStateFlow(BatchUiState())
    val batchState: StateFlow<BatchUiState> = _batchState.asStateFlow()

    private val _viewerState = MutableStateFlow(ViewerUiState())
    val viewerState: StateFlow<ViewerUiState> = _viewerState.asStateFlow()

    private val _uploadLogs = MutableStateFlow<List<CloudUploadRecord>>(emptyList())
    val uploadLogs: StateFlow<List<CloudUploadRecord>> = _uploadLogs.asStateFlow()

    private val _uploadingDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val uploadingDocIds: StateFlow<Set<Long>> = _uploadingDocIds.asStateFlow()

    private val _noteBatchState = MutableStateFlow(NoteBatchUiState())
    val noteBatchState: StateFlow<NoteBatchUiState> = _noteBatchState.asStateFlow()

    private val _pdfToolsState = MutableStateFlow(PdfToolsUiState())
    val pdfToolsState: StateFlow<PdfToolsUiState> = _pdfToolsState.asStateFlow()

    private val _securityConfig = MutableStateFlow(PdfSecurityConfig())
    val securityConfig: StateFlow<PdfSecurityConfig> = _securityConfig.asStateFlow()

    private val _metadataConfig = MutableStateFlow(PdfMetadataConfig())
    val metadataConfig: StateFlow<PdfMetadataConfig> = _metadataConfig.asStateFlow()

    private val _watermarkConfig = MutableStateFlow(PdfWatermarkConfig())
    val watermarkConfig: StateFlow<PdfWatermarkConfig> = _watermarkConfig.asStateFlow()

    private val _scannerState = MutableStateFlow(ScannerUiState())
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    val barcodeScannerOptions: BarcodeScannerOptions = repository.qrEngine.scannerOptions

    val autoSyncDrive: StateFlow<Boolean> = repository.autoSyncDriveFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastDriveSyncAtMs: StateFlow<Long> = repository.lastDriveSyncAtFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _driveSyncState = MutableStateFlow(DriveSyncUiState())
    val driveSyncState: StateFlow<DriveSyncUiState> = _driveSyncState.asStateFlow()

    private val _printPreviewState = MutableStateFlow(PrintPreviewUiState())
    val printPreviewState: StateFlow<PrintPreviewUiState> = _printPreviewState.asStateFlow()

    private var batchJob: Job? = null
    private var notesBatchJob: Job? = null
    private var printPreviewJob: Job? = null

    init {
        viewModelScope.launch {
            autoSyncDrive.collect { enabled ->
                _driveSyncState.update { it.copy(autoSyncEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            lastDriveSyncAtMs.collect { ts ->
                _driveSyncState.update { it.copy(lastSyncedAtMs = ts) }
            }
        }
        regenerateQrPreview()
    }

    fun updateSettings(transform: (ConversionSettings) -> ConversionSettings) {
        viewModelScope.launch {
            val updated = transform(settings.value)
            repository.saveSettings(updated)
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            repository.saveSettings(ConversionSettings())
            _events.emit(FolioEvent.Snackbar("PDF typography, color & layout settings reset to defaults"))
        }
    }

    fun updateSecurityConfig(transform: (PdfSecurityConfig) -> PdfSecurityConfig) {
        _securityConfig.update(transform)
    }

    fun updateMetadataConfig(transform: (PdfMetadataConfig) -> PdfMetadataConfig) {
        _metadataConfig.update(transform)
    }

    fun updateWatermarkConfig(transform: (PdfWatermarkConfig) -> PdfWatermarkConfig) {
        _watermarkConfig.update(transform)
    }

    // --- Studio (Single / Combined PDF) ---

    fun addStudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val inspected = repository.inspectUris(uris)
            _studioState.update { state ->
                val newQueue = state.queue + inspected
                val suggestedName = if (state.queue.isEmpty() && inspected.isNotEmpty()) {
                    inspected.first().displayName.substringBeforeLast('.').ifBlank { "Folio_Document" }
                } else {
                    state.outputName
                }
                state.copy(
                    queue = newQueue,
                    outputName = suggestedName,
                    lastGeneratedDoc = null
                )
            }
        }
    }

    fun updateStudioOutputName(name: String) {
        _studioState.update { it.copy(outputName = name) }
    }

    fun moveStudioItemUp(index: Int) {
        if (index <= 0) return
        _studioState.update { state ->
            val mutable = state.queue.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            state.copy(queue = mutable)
        }
    }

    fun moveStudioItemDown(index: Int) {
        _studioState.update { state ->
            if (index >= state.queue.lastIndex) return@update state
            val mutable = state.queue.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            state.copy(queue = mutable)
        }
    }

    fun rotateStudioItem(id: String) {
        _studioState.update { state ->
            state.copy(
                queue = state.queue.map {
                    if (it.id == id) it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
                    else it
                }
            )
        }
    }

    fun removeStudioItem(id: String) {
        _studioState.update { state ->
            state.copy(queue = state.queue.filterNot { it.id == id })
        }
    }

    fun clearStudioQueue() {
        _studioState.update { it.copy(queue = emptyList(), lastGeneratedDoc = null) }
    }

    fun convertStudioToPdf() {
        val current = _studioState.value
        if (current.queue.isEmpty() || current.isConverting) return
        viewModelScope.launch {
            _studioState.update {
                it.copy(isConverting = true, progressMessage = "Preparing pages...", lastGeneratedDoc = null)
            }
            try {
                val created = repository.convertAndSave(
                    items = current.queue,
                    desiredName = current.outputName.ifBlank { "Folio_Document" },
                    settings = settings.value,
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value,
                    onProgress = { msg ->
                        _studioState.update { it.copy(progressMessage = msg) }
                    }
                )
                _studioState.update {
                    it.copy(
                        isConverting = false,
                        progressMessage = "",
                        lastGeneratedDoc = created
                    )
                }
                val ocrNote = if (created.ocrEnabled) " with searchable OCR text" else ""
                _events.emit(FolioEvent.Snackbar("Created ${created.fileName} (${created.pageCount} pages)$ocrNote"))
            } catch (e: Exception) {
                _studioState.update { it.copy(isConverting = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar("Conversion failed: ${e.localizedMessage ?: "Unknown error"}"))
            }
        }
    }

    // --- Batch Conversion (Multiple Images & Docs -> Individual PDFs) ---

    fun addBatchFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val inspected = repository.inspectUris(uris)
            val newBatchItems = inspected.map { src ->
                val baseName = src.displayName.substringBeforeLast('.').ifBlank { "Document" }
                BatchItemState(
                    sourceItem = src,
                    outputFileName = "${baseName}.pdf",
                    status = BatchStatus.QUEUED,
                    progressMessage = "Queued (${src.sourceKind.badge})"
                )
            }
            _batchState.update { state ->
                val updatedList = state.items + newBatchItems
                state.copy(
                    items = updatedList,
                    totalCount = updatedList.size,
                    lastBatchSummary = null
                )
            }
        }
    }

    fun updateBatchOutputName(itemId: String, newName: String) {
        _batchState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.id == itemId) it.copy(outputFileName = newName) else it
                }
            )
        }
    }

    fun removeBatchItem(itemId: String) {
        _batchState.update { state ->
            val updated = state.items.filterNot { it.id == itemId }
            state.copy(items = updated, totalCount = updated.size)
        }
    }

    fun clearBatchQueue() {
        if (_batchState.value.isRunning) return
        _batchState.update {
            BatchUiState(autoUploadProvider = it.autoUploadProvider)
        }
    }

    fun setBatchAutoUploadProvider(provider: CloudProvider?) {
        _batchState.update { it.copy(autoUploadProvider = provider) }
    }

    fun startBatchConversion() {
        val snapshot = _batchState.value
        val runnableItems = snapshot.items.filter { it.status != BatchStatus.COMPLETED }
        if (runnableItems.isEmpty() || snapshot.isRunning) return

        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            val batchId = UUID.randomUUID().toString()
            val currentSettings = settings.value
            val autoProvider = _batchState.value.autoUploadProvider
            var completed = _batchState.value.items.count { it.status == BatchStatus.COMPLETED }
            val total = _batchState.value.items.size
            var successCount = 0
            var failCount = 0

            _batchState.update {
                it.copy(
                    isRunning = true,
                    completedCount = completed,
                    totalCount = total,
                    lastBatchSummary = null
                )
            }

            for (item in runnableItems) {
                try {
                    val isImageOrPdf = item.sourceItem.sourceKind.name in setOf("IMAGE", "PDF")
                    updateBatchItem(item.id) {
                        it.copy(
                            status = if (isImageOrPdf && currentSettings.enableOcr) {
                                BatchStatus.RUNNING_OCR
                            } else {
                                BatchStatus.CONVERTING
                            },
                            progressMessage = if (isImageOrPdf && currentSettings.enableOcr) {
                                "Running OCR & generating PDF..."
                            } else {
                                "Rendering PDF..."
                            },
                            errorMessage = null
                        )
                    }

                    val entity = repository.convertAndSave(
                        items = listOf(item.sourceItem),
                        desiredName = item.outputFileName.ifBlank { item.sourceItem.displayName },
                        settings = currentSettings,
                        security = _securityConfig.value,
                        metadata = _metadataConfig.value,
                        watermark = _watermarkConfig.value,
                        batchId = batchId,
                        onProgress = { msg ->
                            updateBatchItem(item.id) {
                                val nextStatus = if (msg.contains("OCR", ignoreCase = true)) {
                                    BatchStatus.RUNNING_OCR
                                } else {
                                    BatchStatus.CONVERTING
                                }
                                it.copy(status = nextStatus, progressMessage = msg)
                            }
                        }
                    )

                    var uploadedProvider: CloudProvider? = null
                    if (autoProvider != null) {
                        updateBatchItem(item.id) {
                            it.copy(
                                status = BatchStatus.UPLOADING,
                                progressMessage = "Uploading to ${autoProvider.displayName}..."
                            )
                        }
                        val uploadRes = repository.uploadToCloud(entity, autoProvider)
                        uploadRes.onSuccess { rec ->
                            uploadedProvider = autoProvider
                            _uploadLogs.update { listOf(rec) + it }
                        }.onFailure { err ->
                            _uploadLogs.update {
                                listOf(
                                    CloudUploadRecord(
                                        provider = autoProvider,
                                        fileName = entity.fileName,
                                        remotePathOrLink = null,
                                        uploadedAtMs = System.currentTimeMillis(),
                                        success = false,
                                        message = err.localizedMessage ?: "Upload failed"
                                    )
                                ) + it
                            }
                        }
                    }

                    completed++
                    successCount++
                    updateBatchItem(item.id) {
                        it.copy(
                            status = BatchStatus.COMPLETED,
                            outputFileName = entity.fileName,
                            progressMessage = "Done · ${entity.pageCount}p",
                            resultDocumentId = entity.id,
                            pageCount = entity.pageCount,
                            outputSizeBytes = entity.fileSizeBytes,
                            ocrCharCount = entity.ocrText.length,
                            uploadedProvider = uploadedProvider
                        )
                    }
                    _batchState.update { it.copy(completedCount = completed) }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    failCount++
                    completed++
                    updateBatchItem(item.id) {
                        it.copy(
                            status = BatchStatus.FAILED,
                            progressMessage = "Failed",
                            errorMessage = e.localizedMessage ?: "Conversion failed"
                        )
                    }
                    _batchState.update { it.copy(completedCount = completed) }
                }
            }

            val summary = if (failCount == 0) {
                "Batch complete: $successCount individual PDF${if (successCount == 1) "" else "s"} created"
            } else {
                "Batch finished: $successCount succeeded, $failCount failed"
            }
            _batchState.update { it.copy(isRunning = false, lastBatchSummary = summary) }
            _events.emit(FolioEvent.Snackbar(summary))
        }
    }

    fun cancelBatchConversion() {
        batchJob?.cancel()
        _batchState.update { state ->
            state.copy(
                isRunning = false,
                items = state.items.map {
                    if (it.status == BatchStatus.RUNNING_OCR ||
                        it.status == BatchStatus.CONVERTING ||
                        it.status == BatchStatus.UPLOADING
                    ) {
                        it.copy(status = BatchStatus.QUEUED, progressMessage = "Cancelled")
                    } else it
                }
            )
        }
    }

    fun uploadCompletedBatchToCloud(provider: CloudProvider) {
        val docIds = _batchState.value.items.mapNotNull { it.resultDocumentId }
        if (docIds.isEmpty()) return
        viewModelScope.launch {
            var uploaded = 0
            for (docId in docIds) {
                val doc = repository.getDocumentById(docId) ?: continue
                val res = repository.uploadToCloud(doc, provider)
                res.onSuccess { rec ->
                    uploaded++
                    _uploadLogs.update { listOf(rec) + it }
                    _batchState.update { state ->
                        state.copy(
                            items = state.items.map {
                                if (it.resultDocumentId == docId) it.copy(uploadedProvider = provider) else it
                            }
                        )
                    }
                }.onFailure { err ->
                    _uploadLogs.update {
                        listOf(
                            CloudUploadRecord(
                                provider = provider,
                                fileName = doc.fileName,
                                remotePathOrLink = null,
                                uploadedAtMs = System.currentTimeMillis(),
                                success = false,
                                message = err.localizedMessage ?: "Upload failed"
                            )
                        ) + it
                    }
                }
            }
            _events.emit(FolioEvent.Snackbar("Uploaded $uploaded/${docIds.size} batch PDFs to ${provider.displayName}"))
        }
    }

    private fun updateBatchItem(itemId: String, transform: (BatchItemState) -> BatchItemState) {
        _batchState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.id == itemId) transform(it) else it
                }
            )
        }
    }

    // --- Vault & Export ---

    fun setVaultQuery(query: String) {
        _vaultQuery.value = query
    }

    fun setVaultFilter(filter: VaultFilter) {
        _vaultFilter.value = filter
    }

    fun deleteDocument(doc: ConvertedPdfEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _events.emit(FolioEvent.Snackbar("Deleted ${doc.fileName}"))
        }
    }

    fun saveToDownloads(doc: ConvertedPdfEntity) {
        viewModelScope.launch {
            repository.saveToDownloads(doc)
                .onSuccess { path ->
                    _events.emit(FolioEvent.Snackbar("Saved to $path"))
                }
                .onFailure { err ->
                    _events.emit(FolioEvent.Snackbar(err.localizedMessage ?: "Save failed"))
                }
        }
    }

    fun exportToUri(doc: ConvertedPdfEntity, uri: Uri) {
        viewModelScope.launch {
            val ok = repository.exportToUri(doc, uri)
            _events.emit(
                FolioEvent.Snackbar(if (ok) "Exported ${doc.fileName}" else "Could not export PDF")
            )
        }
    }

    // --- PDF Viewer & In-PDF Search ---

    fun openDocumentInViewer(docId: Long) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(docId)
            if (doc == null) {
                _events.emit(FolioEvent.Snackbar("PDF file not found"))
                return@launch
            }
            val parsedPages = repository.parsePageTexts(doc.pageTextsJson)
            val needsPwd = doc.isPasswordProtected || repository.needsPasswordToOpen(doc.filePath)
            _viewerState.value = ViewerUiState(
                document = doc,
                currentPage = 0,
                zoomScale = 1f,
                pageBitmap = null,
                isLoadingPage = !needsPwd,
                searchQuery = "",
                searchHits = emptyList(),
                pageTexts = parsedPages,
                requiresPassword = needsPwd,
                unlockedPassword = null,
                passwordInput = "",
                passwordError = null,
                canCopyContent = doc.allowCopying,
                canPrint = doc.allowPrinting
            )
            _events.emit(FolioEvent.OpenViewer(docId))
            if (!needsPwd) {
                loadViewerPage(0)
                loadViewerMetadata(doc.filePath, null)
            }
        }
    }

    private fun loadViewerMetadata(filePath: String, password: String?) {
        viewModelScope.launch {
            repository.inspectPdfMetadata(uri = null, localFilePath = filePath, password = password)
                .onSuccess { res ->
                    if (!res.requiresPassword) {
                        _viewerState.update {
                            it.copy(metadata = res.metadata, producer = res.producer)
                        }
                    }
                }
        }
    }

    fun updateViewerPasswordInput(password: String) {
        _viewerState.update { it.copy(passwordInput = password, passwordError = null) }
    }

    fun unlockViewerDocument() {
        val current = _viewerState.value
        val doc = current.document ?: return
        val entered = current.passwordInput
        if (entered.isEmpty()) {
            _viewerState.update { it.copy(passwordError = "Enter the PDF password") }
            return
        }
        viewModelScope.launch {
            _viewerState.update { it.copy(isLoadingPage = true, passwordError = null) }
            repository.verifyPdfPassword(doc.filePath, entered)
                .onSuccess { inspection ->
                    _viewerState.update {
                        it.copy(
                            requiresPassword = false,
                            unlockedPassword = entered,
                            passwordError = null,
                            canCopyContent = doc.allowCopying && inspection.allowCopying,
                            canPrint = doc.allowPrinting && inspection.allowPrinting
                        )
                    }
                    loadViewerPage(0)
                    loadViewerMetadata(doc.filePath, entered)
                }
                .onFailure {
                    _viewerState.update {
                        it.copy(
                            isLoadingPage = false,
                            passwordError = "Incorrect password. Please try again."
                        )
                    }
                }
        }
    }

    fun loadViewerPage(pageIndex: Int) {
        val stateSnapshot = _viewerState.value
        val doc = stateSnapshot.document ?: return
        if (stateSnapshot.requiresPassword) return
        val clamped = pageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        viewModelScope.launch {
            _viewerState.update { it.copy(currentPage = clamped, isLoadingPage = true) }
            val widthPx = (1080 * _viewerState.value.zoomScale).toInt().coerceIn(720, 2100)
            val bmp = repository.renderPdfPage(
                filePath = doc.filePath,
                pageIndex = clamped,
                targetWidthPx = widthPx,
                password = stateSnapshot.unlockedPassword
            )
            _viewerState.update { it.copy(pageBitmap = bmp, isLoadingPage = false) }
        }
    }

    fun setViewerZoom(scale: Float) {
        val clamped = scale.coerceIn(1f, 2.5f)
        _viewerState.update { it.copy(zoomScale = clamped) }
        loadViewerPage(_viewerState.value.currentPage)
    }

    fun setViewerThemeMode(mode: ViewerThemeMode) {
        _viewerState.update { it.copy(themeMode = mode) }
    }

    fun updateViewerSearchQuery(query: String) {
        _viewerState.update { it.copy(searchQuery = query) }
        val doc = _viewerState.value.document ?: return
        if (query.isBlank()) {
            _viewerState.update { it.copy(searchHits = emptyList(), isSearching = false) }
            return
        }
        viewModelScope.launch {
            _viewerState.update { it.copy(isSearching = true) }
            val hits = repository.searchPdf(doc, query)
            _viewerState.update { it.copy(searchHits = hits, isSearching = false) }
        }
    }

    // --- Cloud Authentication & Uploads ---

    fun connectCloudProvider(provider: CloudProvider) {
        viewModelScope.launch {
            when (provider) {
                CloudProvider.GOOGLE_DRIVE -> {
                    when (val outcome = repository.authorizeGoogleDrive()) {
                        is GoogleAuthLaunchOutcome.Authorized -> {
                            _events.emit(
                                FolioEvent.Snackbar(
                                    "Connected Google Drive (${outcome.account.accountEmail ?: "Ready"})"
                                )
                            )
                            refreshGoogleDriveSync()
                        }
                        is GoogleAuthLaunchOutcome.NeedsResolution -> {
                            _events.emit(FolioEvent.LaunchGoogleResolution(outcome.intentSender))
                        }
                        is GoogleAuthLaunchOutcome.Failed -> {
                            _events.emit(FolioEvent.Snackbar(outcome.message))
                        }
                    }
                }
                CloudProvider.DROPBOX, CloudProvider.ONEDRIVE -> {
                    repository.launchBrowserOAuth(provider)
                        .onFailure { err ->
                            val msg = err.localizedMessage ?: "Configuration required"
                            _events.emit(FolioEvent.ProviderConfigRequired(provider, msg))
                        }
                }
            }
        }
    }

    fun onGoogleAuthResolutionResult(data: Intent?) {
        viewModelScope.launch {
            repository.handleGoogleAuthIntentResult(data)
                .onSuccess { account ->
                    _events.emit(
                        FolioEvent.Snackbar(
                            "Google Drive connected (${account.accountEmail ?: "Authorized"})"
                        )
                    )
                    refreshGoogleDriveSync()
                }
                .onFailure { err ->
                    _events.emit(
                        FolioEvent.Snackbar(
                            err.localizedMessage ?: "Google Drive authorization cancelled"
                        )
                    )
                }
        }
    }

    fun onOAuthDeepLink(uri: Uri) {
        viewModelScope.launch {
            repository.handleOAuthRedirect(uri)
                .onSuccess { account ->
                    _events.emit(
                        FolioEvent.Snackbar(
                            "Connected ${account.provider.displayName} (${account.accountEmail ?: "Ready"})"
                        )
                    )
                    if (account.provider == CloudProvider.GOOGLE_DRIVE) {
                        refreshGoogleDriveSync()
                    }
                }
                .onFailure { err ->
                    _events.emit(
                        FolioEvent.Snackbar(
                            err.localizedMessage ?: "OAuth sign-in failed"
                        )
                    )
                }
        }
    }

    fun disconnectCloudProvider(provider: CloudProvider) {
        viewModelScope.launch {
            repository.disconnectCloud(provider)
            _events.emit(FolioEvent.Snackbar("Disconnected ${provider.displayName}"))
        }
    }

    fun uploadDocumentToCloud(doc: ConvertedPdfEntity, provider: CloudProvider) {
        viewModelScope.launch {
            _uploadingDocIds.update { it + doc.id }
            val result = repository.uploadToCloud(doc, provider)
            _uploadingDocIds.update { it - doc.id }
            result.onSuccess { record ->
                _uploadLogs.update { listOf(record) + it }
                _events.emit(FolioEvent.Snackbar("${doc.fileName} saved to ${provider.displayName}"))
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Upload failed"
                _uploadLogs.update {
                    listOf(
                        CloudUploadRecord(
                            provider = provider,
                            fileName = doc.fileName,
                            remotePathOrLink = null,
                            uploadedAtMs = System.currentTimeMillis(),
                            success = false,
                            message = msg
                        )
                    ) + it
                }
                _events.emit(FolioEvent.Snackbar(msg))
            }
        }
    }

    // --- Batch Extract Video & PDF Notes ---

    fun addNoteFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val inspected = repository.inspectNoteUris(uris)
            _noteBatchState.update { state ->
                val updated = state.items + inspected
                state.copy(items = updated, totalCount = updated.size, lastSummary = null)
            }
        }
    }

    fun addVaultPdfToNotes(doc: ConvertedPdfEntity) {
        val item = NoteBatchItem(
            uri = null,
            localFilePath = doc.filePath,
            displayName = doc.fileName,
            mimeType = "application/pdf",
            sizeBytes = doc.fileSizeBytes,
            kind = NoteSourceKind.PDF
        )
        _noteBatchState.update { state ->
            val updated = state.items + item
            state.copy(items = updated, totalCount = updated.size, lastSummary = null)
        }
    }

    fun setNoteExtractionStyle(style: NoteExtractionStyle) {
        _noteBatchState.update { it.copy(style = style) }
    }

    fun setNoteAiModelTier(tier: NoteAiModelTier) {
        _noteBatchState.update { it.copy(modelTier = tier) }
    }

    fun removeNoteItem(itemId: String) {
        if (_noteBatchState.value.isExtracting) return
        _noteBatchState.update { state ->
            val updated = state.items.filterNot { it.id == itemId }
            state.copy(items = updated, totalCount = updated.size)
        }
    }

    fun clearNoteQueue() {
        if (_noteBatchState.value.isExtracting) return
        _noteBatchState.update {
            NoteBatchUiState(style = it.style, modelTier = it.modelTier)
        }
    }

    fun startBatchNoteExtraction() {
        val snapshot = _noteBatchState.value
        val runnable = snapshot.items.filter { it.status != NoteBatchStatus.COMPLETED }
        if (runnable.isEmpty() || snapshot.isExtracting) return

        notesBatchJob?.cancel()
        notesBatchJob = viewModelScope.launch {
            var completed = snapshot.items.count { it.status == NoteBatchStatus.COMPLETED }
            val total = snapshot.items.size
            var successCount = 0
            var failCount = 0

            _noteBatchState.update {
                it.copy(
                    isExtracting = true,
                    completedCount = completed,
                    totalCount = total,
                    lastSummary = null
                )
            }

            for (item in runnable) {
                try {
                    updateNoteItem(item.id) {
                        it.copy(
                            status = NoteBatchStatus.ANALYZING,
                            progressText = if (it.kind == NoteSourceKind.VIDEO) {
                                "Analyzing video with Gemini..."
                            } else {
                                "Extracting PDF notes with Gemini..."
                            },
                            errorMessage = null
                        )
                    }

                    val extracted = repository.extractNotes(
                        item = item,
                        style = _noteBatchState.value.style,
                        modelTier = _noteBatchState.value.modelTier
                    )

                    completed++
                    successCount++
                    updateNoteItem(item.id) {
                        it.copy(
                            status = NoteBatchStatus.COMPLETED,
                            progressText = "${extracted.sections.size} sections · ${extracted.keyTakeaways.size} takeaways",
                            result = extracted
                        )
                    }
                    _noteBatchState.update { it.copy(completedCount = completed) }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    failCount++
                    completed++
                    updateNoteItem(item.id) {
                        it.copy(
                            status = NoteBatchStatus.FAILED,
                            progressText = "Failed",
                            errorMessage = e.localizedMessage ?: "AI note extraction failed."
                        )
                    }
                    _noteBatchState.update { it.copy(completedCount = completed) }
                }
            }

            val summary = if (failCount == 0) {
                "Extracted notes for $successCount file${if (successCount == 1) "" else "s"}"
            } else {
                "Finished: $successCount extracted, $failCount failed"
            }
            _noteBatchState.update { it.copy(isExtracting = false, lastSummary = summary) }
            _events.emit(FolioEvent.Snackbar(summary))
        }
    }

    fun cancelBatchNoteExtraction() {
        notesBatchJob?.cancel()
        _noteBatchState.update { state ->
            state.copy(
                isExtracting = false,
                items = state.items.map {
                    if (it.status == NoteBatchStatus.ANALYZING) {
                        it.copy(status = NoteBatchStatus.QUEUED, progressText = "Cancelled")
                    } else it
                }
            )
        }
    }

    fun exportNoteItemToPdf(itemId: String) {
        val item = _noteBatchState.value.items.firstOrNull { it.id == itemId } ?: return
        val noteResult = item.result ?: return
        viewModelScope.launch {
            try {
                val saved = repository.saveNotesAsPdf(
                    noteResult = noteResult,
                    sourceName = item.displayName,
                    settings = settings.value,
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value
                )
                updateNoteItem(itemId) { it.copy(exportedPdfId = saved.id) }
                _events.emit(FolioEvent.Snackbar("Saved ${saved.fileName} to Vault"))
            } catch (e: Exception) {
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to save notes PDF"))
            }
        }
    }

    fun exportAllNotesToPdf() {
        val completed = _noteBatchState.value.items.filter {
            it.status == NoteBatchStatus.COMPLETED && it.result != null && it.exportedPdfId == null
        }
        if (completed.isEmpty()) return
        viewModelScope.launch {
            val batchId = UUID.randomUUID().toString()
            var count = 0
            for (item in completed) {
                val noteResult = item.result ?: continue
                try {
                    val saved = repository.saveNotesAsPdf(
                        noteResult = noteResult,
                        sourceName = item.displayName,
                        settings = settings.value,
                        security = _securityConfig.value,
                        metadata = _metadataConfig.value,
                        watermark = _watermarkConfig.value,
                        batchId = batchId
                    )
                    updateNoteItem(item.id) { it.copy(exportedPdfId = saved.id) }
                    count++
                } catch (_: Exception) {
                }
            }
            _events.emit(FolioEvent.Snackbar("Saved $count note PDFs to Vault"))
        }
    }

    private fun updateNoteItem(itemId: String, transform: (NoteBatchItem) -> NoteBatchItem) {
        _noteBatchState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.id == itemId) transform(it) else it
                }
            )
        }
    }

    // --- All PDF Tools (PDF <-> Images, Merge, Split, Compress, Rotate) ---

    fun selectPdfToolMode(mode: PdfToolMode) {
        _pdfToolsState.update {
            it.copy(
                activeTool = mode,
                progressMessage = "",
                exportedImages = emptyList(),
                lastToolPdfResults = emptyList(),
                lastTranslationResult = null,
                compressionSummary = null,
                metadataPasswordError = null
            )
        }
        if (mode == PdfToolMode.EDIT_METADATA) {
            _pdfToolsState.value.singleSource?.let { inspectToolMetadataForSource(it) }
        }
    }

    private fun buildDefaultTranslationOutputName(sourceName: String, targetLang: String): String {
        val base = sourceName.removeSuffix(".pdf")
        val langSlug = targetLang.trim().replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_')
        return if (langSlug.isNotBlank()) "${base}_$langSlug" else "${base}_translated"
    }

    fun setSinglePdfSourceFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, pages, size) = repository.inspectPdfUri(uri)
                val source = SelectedPdfSource(
                    uri = uri,
                    displayName = name,
                    pageCount = pages,
                    sizeBytes = size
                )
                _pdfToolsState.update {
                    it.copy(
                        singleSource = source,
                        metadataOutputName = "${name.removeSuffix(".pdf")}_metadata",
                        nUpOutputName = "${name.removeSuffix(".pdf")}_${it.nUpPagesPerSheet.count}in1",
                        watermarkOutputName = "${name.removeSuffix(".pdf")}_watermarked",
                        translationOutputName = buildDefaultTranslationOutputName(name, it.translationTargetLanguage),
                        lastTranslationResult = null,
                        metadataUnlockedPassword = null,
                        metadataNeedsPassword = false,
                        metadataPasswordError = null,
                        exportedImages = emptyList(),
                        lastToolPdfResults = emptyList(),
                        compressionSummary = null
                    )
                }
                if (_pdfToolsState.value.activeTool == PdfToolMode.EDIT_METADATA) {
                    inspectToolMetadataForSource(source)
                }
            } catch (e: Exception) {
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Unable to read PDF"))
            }
        }
    }

    fun setSinglePdfSourceFromVault(doc: ConvertedPdfEntity) {
        val source = SelectedPdfSource(
            localFilePath = doc.filePath,
            displayName = doc.fileName,
            pageCount = doc.pageCount,
            sizeBytes = doc.fileSizeBytes,
            vaultDoc = doc
        )
        _pdfToolsState.update {
            it.copy(
                singleSource = source,
                metadataOutputName = "${doc.fileName.removeSuffix(".pdf")}_metadata",
                nUpOutputName = "${doc.fileName.removeSuffix(".pdf")}_${it.nUpPagesPerSheet.count}in1",
                watermarkOutputName = "${doc.fileName.removeSuffix(".pdf")}_watermarked",
                translationOutputName = buildDefaultTranslationOutputName(doc.fileName, it.translationTargetLanguage),
                lastTranslationResult = null,
                metadataUnlockedPassword = null,
                metadataNeedsPassword = false,
                metadataPasswordError = null,
                exportedImages = emptyList(),
                lastToolPdfResults = emptyList(),
                compressionSummary = null
            )
        }
        if (_pdfToolsState.value.activeTool == PdfToolMode.EDIT_METADATA) {
            inspectToolMetadataForSource(source)
        }
    }

    fun launchVaultDocInTool(doc: ConvertedPdfEntity, mode: PdfToolMode) {
        val source = SelectedPdfSource(
            localFilePath = doc.filePath,
            displayName = doc.fileName,
            pageCount = doc.pageCount,
            sizeBytes = doc.fileSizeBytes,
            vaultDoc = doc
        )
        _pdfToolsState.update { state ->
            if (mode == PdfToolMode.MERGE_PDFS) {
                state.copy(
                    activeTool = mode,
                    mergeSources = state.mergeSources + source,
                    exportedImages = emptyList(),
                    lastToolPdfResults = emptyList(),
                    lastTranslationResult = null,
                    compressionSummary = null
                )
            } else {
                state.copy(
                    activeTool = mode,
                    singleSource = source,
                    metadataOutputName = "${doc.fileName.removeSuffix(".pdf")}_metadata",
                    nUpOutputName = "${doc.fileName.removeSuffix(".pdf")}_${state.nUpPagesPerSheet.count}in1",
                    watermarkOutputName = "${doc.fileName.removeSuffix(".pdf")}_watermarked",
                    translationOutputName = buildDefaultTranslationOutputName(doc.fileName, state.translationTargetLanguage),
                    lastTranslationResult = null,
                    metadataUnlockedPassword = null,
                    metadataNeedsPassword = false,
                    metadataPasswordError = null,
                    exportedImages = emptyList(),
                    lastToolPdfResults = emptyList(),
                    compressionSummary = null
                )
            }
        }
        if (mode == PdfToolMode.EDIT_METADATA) {
            inspectToolMetadataForSource(source)
        }
    }

    fun addMergePdfsFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val inspected = uris.mapNotNull { uri ->
                runCatching {
                    val (name, pages, size) = repository.inspectPdfUri(uri)
                    SelectedPdfSource(
                        uri = uri,
                        displayName = name,
                        pageCount = pages,
                        sizeBytes = size
                    )
                }.getOrNull()
            }
            if (inspected.isNotEmpty()) {
                _pdfToolsState.update {
                    it.copy(
                        mergeSources = it.mergeSources + inspected,
                        lastToolPdfResults = emptyList()
                    )
                }
            }
        }
    }

    fun addMergePdfFromVault(doc: ConvertedPdfEntity) {
        val source = SelectedPdfSource(
            localFilePath = doc.filePath,
            displayName = doc.fileName,
            pageCount = doc.pageCount,
            sizeBytes = doc.fileSizeBytes
        )
        _pdfToolsState.update {
            it.copy(
                mergeSources = it.mergeSources + source,
                lastToolPdfResults = emptyList()
            )
        }
    }

    fun moveMergePdfUp(index: Int) {
        if (index <= 0) return
        _pdfToolsState.update { state ->
            val list = state.mergeSources.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            state.copy(mergeSources = list)
        }
    }

    fun moveMergePdfDown(index: Int) {
        _pdfToolsState.update { state ->
            if (index >= state.mergeSources.lastIndex) return@update state
            val list = state.mergeSources.toMutableList()
            val item = list.removeAt(index)
            list.add(index + 1, item)
            state.copy(mergeSources = list)
        }
    }

    fun removeMergePdf(id: String) {
        _pdfToolsState.update { state ->
            state.copy(mergeSources = state.mergeSources.filterNot { it.id == id })
        }
    }

    fun clearMergePdfs() {
        _pdfToolsState.update { it.copy(mergeSources = emptyList(), lastToolPdfResults = emptyList()) }
    }

    fun setMergeOutputName(name: String) {
        _pdfToolsState.update { it.copy(mergeOutputName = name) }
    }

    fun setToolImageFormat(format: ImageExportFormat) {
        _pdfToolsState.update { it.copy(imageFormat = format) }
    }

    fun setToolImageDpi(dpi: Int) {
        _pdfToolsState.update { it.copy(imageDpi = dpi) }
    }

    fun setToolPageRangeExpr(expr: String) {
        _pdfToolsState.update { it.copy(pageRangeExpr = expr) }
    }

    fun setToolSplitEachPage(eachPage: Boolean) {
        _pdfToolsState.update { it.copy(splitEachPage = eachPage) }
    }

    fun setToolCompressionLevel(level: PdfCompressionLevel) {
        _pdfToolsState.update { it.copy(compressionLevel = level) }
    }

    fun setToolRotationDegrees(degrees: Int) {
        _pdfToolsState.update { it.copy(rotationDegrees = degrees) }
    }

    fun setToolExistingPdfPassword(password: String) {
        _pdfToolsState.update { it.copy(existingPdfPassword = password) }
    }

    fun setToolNUpPagesPerSheet(perSheet: PrintPagesPerSheet) {
        _pdfToolsState.update { state ->
            val srcName = state.singleSource?.displayName?.removeSuffix(".pdf")
            state.copy(
                nUpPagesPerSheet = perSheet,
                nUpOutputName = if (srcName != null) "${srcName}_${perSheet.count}in1" else state.nUpOutputName
            )
        }
    }

    fun setToolNUpPaperSize(paperSize: PrintPaperSize) {
        _pdfToolsState.update { it.copy(nUpPaperSize = paperSize) }
    }

    fun setToolNUpOrientation(orientation: PrintOrientationMode) {
        _pdfToolsState.update { it.copy(nUpOrientation = orientation) }
    }

    fun setToolNUpShowBorders(show: Boolean) {
        _pdfToolsState.update { it.copy(nUpShowBorders = show) }
    }

    fun setToolNUpGrayscale(grayscale: Boolean) {
        _pdfToolsState.update { it.copy(nUpGrayscale = grayscale) }
    }

    fun setToolNUpOutputName(name: String) {
        _pdfToolsState.update { it.copy(nUpOutputName = name) }
    }

    fun setQuickTextTitle(title: String) {
        _pdfToolsState.update { it.copy(quickTextTitle = title) }
    }

    fun setQuickTextBody(body: String) {
        _pdfToolsState.update { it.copy(quickTextBody = body) }
    }

    fun applyQuickTextTemplate(title: String, body: String) {
        _pdfToolsState.update {
            it.copy(
                quickTextTitle = title,
                quickTextBody = body,
                lastToolPdfResults = emptyList()
            )
        }
    }

    fun runFourInOnePdf() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        if (state.isProcessing) return
        viewModelScope.launch {
            val count = state.nUpPagesPerSheet.count.coerceAtLeast(2)
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Combining $count pages onto 1 sheet...",
                    lastToolPdfResults = emptyList(),
                    compressionSummary = null
                )
            }
            try {
                val result = repository.convertNUpPdfAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    sourceDisplayName = src.displayName,
                    outputBaseName = state.nUpOutputName.ifBlank {
                        "${src.displayName.removeSuffix(".pdf")}_${count}in1"
                    },
                    pagesPerSheet = state.nUpPagesPerSheet,
                    paperSize = state.nUpPaperSize,
                    orientation = state.nUpOrientation,
                    pageRangeExpr = state.pageRangeExpr,
                    showBorders = state.nUpShowBorders,
                    grayscale = state.nUpGrayscale,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(result)
                    )
                }
                _events.emit(
                    FolioEvent.Snackbar(
                        "Converted to ${count}-in-1 PDF: ${result.fileName} (${result.pageCount} sheet(s))"
                    )
                )
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "4-in-1 conversion failed"))
            }
        }
    }

    fun runQuickTextToPdf() {
        val state = _pdfToolsState.value
        if (state.isProcessing) return
        if (state.quickTextBody.isBlank()) {
            viewModelScope.launch {
                _events.emit(FolioEvent.Snackbar("Type or paste some text or pick a starter template first."))
            }
            return
        }
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Creating styled PDF...",
                    lastToolPdfResults = emptyList(),
                    compressionSummary = null
                )
            }
            try {
                val created = repository.createQuickTextPdfAndSave(
                    title = state.quickTextTitle.ifBlank { "My_Notes" },
                    markdownOrPlainText = state.quickTextBody,
                    settings = settings.value,
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(created)
                    )
                }
                _events.emit(FolioEvent.Snackbar("Created ${created.fileName} (${created.pageCount} page(s))"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Text to PDF failed"))
            }
        }
    }

    fun setToolWatermarkOutputName(name: String) {
        _pdfToolsState.update { it.copy(watermarkOutputName = name) }
    }

    fun runWatermarkPdf() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        val wm = _watermarkConfig.value
        if (state.isProcessing) return
        if (!wm.isReadyToApply()) {
            viewModelScope.launch {
                _events.emit(
                    FolioEvent.Snackbar(
                        if (wm.type == com.example.model.WatermarkType.IMAGE) {
                            "Select a watermark image or logo first."
                        } else {
                            "Enter watermark text first."
                        }
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Stamping copyright watermark...",
                    lastToolPdfResults = emptyList(),
                    compressionSummary = null
                )
            }
            try {
                val outName = state.watermarkOutputName.ifBlank {
                    "${src.displayName.removeSuffix(".pdf")}_watermarked"
                }
                val vaultDoc = src.vaultDoc
                val saved = repository.applyWatermarkAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    sourceDisplayName = src.displayName,
                    outputBaseName = outName,
                    watermark = wm.copy(enabled = true),
                    metadata = _metadataConfig.value,
                    security = _securityConfig.value,
                    preserveOcrEnabled = vaultDoc?.ocrEnabled ?: false,
                    preserveOcrText = vaultDoc?.ocrText.orEmpty(),
                    preservePageTextsJson = vaultDoc?.pageTextsJson ?: "[]",
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(saved)
                    )
                }
                _events.emit(FolioEvent.Snackbar("Watermarked PDF saved as ${saved.fileName}"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to watermark PDF"))
            }
        }
    }

    // --- Translate PDF (Any Language to Any Language) ---

    fun setTranslationSourceLanguage(lang: String) {
        _pdfToolsState.update { it.copy(translationSourceLanguage = lang) }
    }

    fun setTranslationTargetLanguage(lang: String) {
        _pdfToolsState.update { state ->
            val srcName = state.singleSource?.displayName
            state.copy(
                translationTargetLanguage = lang,
                translationOutputName = if (srcName != null) {
                    buildDefaultTranslationOutputName(srcName, lang)
                } else {
                    state.translationOutputName
                }
            )
        }
    }

    fun swapTranslationLanguages() {
        _pdfToolsState.update { state ->
            val currentSource = state.translationSourceLanguage.trim()
            val currentTarget = state.translationTargetLanguage.trim()
            if (currentSource.equals(TranslationLanguageCatalog.AUTO_DETECT, ignoreCase = true)) {
                return@update state
            }
            val srcName = state.singleSource?.displayName
            state.copy(
                translationSourceLanguage = currentTarget.ifBlank { TranslationLanguageCatalog.AUTO_DETECT },
                translationTargetLanguage = currentSource.ifBlank { "English" },
                translationOutputName = if (srcName != null) {
                    buildDefaultTranslationOutputName(srcName, currentSource.ifBlank { "English" })
                } else {
                    state.translationOutputName
                }
            )
        }
    }

    fun setTranslationTone(tone: PdfTranslationTone) {
        _pdfToolsState.update { it.copy(translationTone = tone) }
    }

    fun setTranslationOutputName(name: String) {
        _pdfToolsState.update { it.copy(translationOutputName = name) }
    }

    fun runTranslatePdf() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        if (state.isProcessing) return
        val targetLang = state.translationTargetLanguage.trim()
        if (targetLang.isBlank()) {
            viewModelScope.launch {
                _events.emit(FolioEvent.Snackbar("Enter or select a target language first."))
            }
            return
        }
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Preparing PDF for translation...",
                    lastToolPdfResults = emptyList(),
                    lastTranslationResult = null,
                    compressionSummary = null
                )
            }
            try {
                val outBase = state.translationOutputName.ifBlank {
                    buildDefaultTranslationOutputName(src.displayName, targetLang)
                }
                val (entity, translationResult) = repository.translatePdfAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    sourceDisplayName = src.displayName,
                    outputBaseName = outBase,
                    sourceLanguage = state.translationSourceLanguage,
                    targetLanguage = targetLang,
                    tone = state.translationTone,
                    pageRangeExpr = state.pageRangeExpr,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    settings = settings.value,
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(entity),
                        lastTranslationResult = translationResult
                    )
                }
                _events.emit(
                    FolioEvent.Snackbar(
                        "Translated ${translationResult.pages.size} page(s) from ${translationResult.detectedSourceLanguage} → ${translationResult.targetLanguage}"
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "PDF translation failed"))
            }
        }
    }

    fun runPdfToImages() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        if (state.isProcessing) return
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Rendering PDF pages...",
                    exportedImages = emptyList()
                )
            }
            try {
                val exported = repository.convertPdfToImages(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    baseName = src.displayName,
                    format = state.imageFormat,
                    dpi = state.imageDpi,
                    pageRangeExpr = state.pageRangeExpr,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(isProcessing = false, progressMessage = "", exportedImages = exported)
                }
                _events.emit(
                    FolioEvent.Snackbar("Exported ${exported.size} ${state.imageFormat.extension.uppercase()} image(s) to Pictures/FolioPDF")
                )
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "PDF to Image failed"))
            }
        }
    }

    fun runMergePdfs() {
        val state = _pdfToolsState.value
        if (state.mergeSources.size < 2 || state.isProcessing) return
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Merging PDFs...",
                    lastToolPdfResults = emptyList()
                )
            }
            try {
                val merged = repository.mergePdfsAndSave(
                    sources = state.mergeSources.map { it.uri to it.localFilePath },
                    outputBaseName = state.mergeOutputName.ifBlank { "Merged_Folio" },
                    enableOcr = settings.value.enableOcr,
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(merged)
                    )
                }
                _events.emit(FolioEvent.Snackbar("Merged into ${merged.fileName} (${merged.pageCount} pages)"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Merge failed"))
            }
        }
    }

    fun runSplitPdf() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        if (state.isProcessing) return
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Splitting PDF...",
                    lastToolPdfResults = emptyList()
                )
            }
            try {
                val results = repository.splitPdfAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    baseName = src.displayName,
                    splitEachPage = state.splitEachPage,
                    pageRangeExpr = state.pageRangeExpr,
                    enableOcr = settings.value.enableOcr,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = results
                    )
                }
                _events.emit(FolioEvent.Snackbar("Created ${results.size} split PDF(s) in Vault"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Split failed"))
            }
        }
    }

    fun runCompressPdf() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        if (state.isProcessing) return
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Compressing PDF...",
                    lastToolPdfResults = emptyList(),
                    compressionSummary = null
                )
            }
            try {
                val (entity, outcome) = repository.compressPdfAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    baseName = src.displayName,
                    level = state.compressionLevel,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    metadata = _metadataConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                val summary = if (outcome.originalBytes > 0L) {
                    val deltaPct = ((outcome.originalBytes - outcome.compressedBytes) * 100.0 / outcome.originalBytes).toInt()
                    val deltaText = if (deltaPct >= 0) "$deltaPct% smaller" else "${-deltaPct}% larger"
                    "${formatFileSize(outcome.originalBytes)} → ${formatFileSize(outcome.compressedBytes)} ($deltaText)"
                } else {
                    "Compressed size: ${formatFileSize(outcome.compressedBytes)}"
                }
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(entity),
                        compressionSummary = summary
                    )
                }
                _events.emit(FolioEvent.Snackbar("Saved compressed PDF: $summary"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Compression failed"))
            }
        }
    }

    fun runRotatePdf() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        if (state.isProcessing) return
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Rotating PDF...",
                    lastToolPdfResults = emptyList()
                )
            }
            try {
                val rotated = repository.rotatePdfAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    baseName = src.displayName,
                    rotationDegrees = state.rotationDegrees,
                    enableOcr = settings.value.enableOcr,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(rotated)
                    )
                }
                _events.emit(FolioEvent.Snackbar("Rotated PDF saved as ${rotated.fileName}"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Rotate failed"))
            }
        }
    }

    fun runProtectPdf() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        val sec = _securityConfig.value
        if (state.isProcessing) return
        if (!sec.hasAnyRestrictionOrPassword()) {
            viewModelScope.launch {
                _events.emit(FolioEvent.Snackbar("Enable protection and set a password or restrict permissions first."))
            }
            return
        }
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Encrypting PDF & applying permissions...",
                    lastToolPdfResults = emptyList()
                )
            }
            try {
                val protectedDoc = repository.protectExistingPdfAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    existingPassword = state.existingPdfPassword.ifBlank { null },
                    baseName = src.displayName,
                    security = sec,
                    metadata = _metadataConfig.value,
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        existingPdfPassword = "",
                        lastToolPdfResults = listOf(protectedDoc)
                    )
                }
                _events.emit(FolioEvent.Snackbar("Protected PDF saved (${protectedDoc.securitySummary ?: "Locked"})"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Encryption failed"))
            }
        }
    }

    // --- PDF Metadata Editor Tool ---

    fun setToolMetadataOutputName(name: String) {
        _pdfToolsState.update { it.copy(metadataOutputName = name) }
    }

    private fun inspectToolMetadataForSource(source: SelectedPdfSource, password: String? = null) {
        viewModelScope.launch {
            repository.inspectPdfMetadata(
                uri = source.uri,
                localFilePath = source.localFilePath,
                password = password
            ).onSuccess { readRes ->
                if (readRes.requiresPassword) {
                    _pdfToolsState.update {
                        it.copy(
                            metadataNeedsPassword = true,
                            metadataPasswordError = if (!password.isNullOrEmpty()) "Incorrect password" else null
                        )
                    }
                } else {
                    _metadataConfig.value = readRes.metadata
                    _pdfToolsState.update {
                        it.copy(
                            metadataNeedsPassword = false,
                            metadataUnlockedPassword = password?.ifEmpty { null },
                            metadataProducerInfo = readRes.producer,
                            metadataPasswordError = null,
                            metadataOutputName = it.metadataOutputName.ifBlank {
                                "${source.displayName.removeSuffix(".pdf")}_metadata"
                            }
                        )
                    }
                }
            }.onFailure { err ->
                _events.emit(FolioEvent.Snackbar(err.localizedMessage ?: "Could not inspect PDF metadata"))
            }
        }
    }

    fun unlockAndInspectMetadata() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        val pwd = state.existingPdfPassword
        if (pwd.isEmpty()) {
            _pdfToolsState.update { it.copy(metadataPasswordError = "Enter PDF password") }
            return
        }
        inspectToolMetadataForSource(src, pwd)
    }

    fun runSavePdfWithMetadata() {
        val state = _pdfToolsState.value
        val src = state.singleSource ?: return
        if (state.isProcessing) return
        viewModelScope.launch {
            _pdfToolsState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Embedding PDF metadata...",
                    lastToolPdfResults = emptyList()
                )
            }
            try {
                val pwd = state.metadataUnlockedPassword ?: state.existingPdfPassword.ifBlank { null }
                val outName = state.metadataOutputName.ifBlank {
                    "${src.displayName.removeSuffix(".pdf")}_metadata"
                }
                val vaultDoc = src.vaultDoc
                val saved = repository.editPdfMetadataAndSave(
                    uri = src.uri,
                    localFilePath = src.localFilePath,
                    existingPassword = pwd,
                    outputBaseName = outName,
                    metadata = _metadataConfig.value,
                    security = _securityConfig.value,
                    watermark = _watermarkConfig.value,
                    preserveOcrEnabled = vaultDoc?.ocrEnabled ?: false,
                    preserveOcrText = vaultDoc?.ocrText.orEmpty(),
                    preservePageTextsJson = vaultDoc?.pageTextsJson ?: "[]",
                    onProgress = { msg ->
                        _pdfToolsState.update { it.copy(progressMessage = msg) }
                    }
                )
                _pdfToolsState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "",
                        lastToolPdfResults = listOf(saved)
                    )
                }
                _events.emit(FolioEvent.Snackbar("Saved ${saved.fileName} with updated metadata"))
            } catch (e: Exception) {
                _pdfToolsState.update { it.copy(isProcessing = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to save PDF metadata"))
            }
        }
    }

    // --- Google Drive Multi-Device Synchronization ---

    fun setAutoSyncGoogleDrive(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSyncDrive(enabled)
            _events.emit(
                FolioEvent.Snackbar(
                    if (enabled) "Auto-backup to Google Drive enabled"
                    else "Auto-backup to Google Drive paused"
                )
            )
        }
    }

    fun refreshGoogleDriveSync() {
        if (_driveSyncState.value.isRefreshing || _driveSyncState.value.isSyncing) return
        viewModelScope.launch {
            _driveSyncState.update { it.copy(isRefreshing = true) }
            repository.listGoogleDrivePdfsAnnotated()
                .onSuccess { files ->
                    _driveSyncState.update {
                        it.copy(
                            isRefreshing = false,
                            remoteFiles = files,
                            statusSummary = "${files.size} PDF backup(s) found in My Drive / FolioPDF"
                        )
                    }
                }
                .onFailure { err ->
                    _driveSyncState.update {
                        it.copy(
                            isRefreshing = false,
                            statusSummary = err.localizedMessage ?: "Unable to fetch Google Drive files"
                        )
                    }
                }
        }
    }

    fun syncAllWithGoogleDrive(downloadMissingFromCloud: Boolean = true) {
        if (_driveSyncState.value.isSyncing) return
        viewModelScope.launch {
            _driveSyncState.update {
                it.copy(
                    isSyncing = true,
                    statusSummary = "Syncing Vault PDFs with Google Drive..."
                )
            }
            repository.syncAllWithGoogleDrive(downloadMissingFromCloud)
                .onSuccess { report ->
                    val summary = buildString {
                        append("Synced · ")
                        append("${report.uploadedCount} backed up, ")
                        append("${report.downloadedCount} restored")
                        if (report.linkedCount > 0) append(", ${report.linkedCount} linked")
                    }
                    _driveSyncState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncedAtMs = report.syncedAtMs,
                            remoteFiles = report.remoteFiles,
                            statusSummary = summary
                        )
                    }
                    _events.emit(FolioEvent.Snackbar(summary))
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Google Drive sync failed"
                    _driveSyncState.update {
                        it.copy(isSyncing = false, statusSummary = msg)
                    }
                    _events.emit(FolioEvent.Snackbar(msg))
                }
        }
    }

    fun downloadDriveFile(remote: RemoteDrivePdfItem) {
        if (_driveSyncState.value.downloadingDriveIds.contains(remote.driveFileId)) return
        viewModelScope.launch {
            _driveSyncState.update {
                it.copy(downloadingDriveIds = it.downloadingDriveIds + remote.driveFileId)
            }
            repository.downloadDrivePdfToVault(remote)
                .onSuccess { entity ->
                    _driveSyncState.update { state ->
                        state.copy(
                            downloadingDriveIds = state.downloadingDriveIds - remote.driveFileId,
                            remoteFiles = state.remoteFiles.map {
                                if (it.driveFileId == remote.driveFileId) it.copy(localDocumentId = entity.id) else it
                            }
                        )
                    }
                    _events.emit(FolioEvent.Snackbar("Restored ${entity.fileName} to Vault"))
                }
                .onFailure { err ->
                    _driveSyncState.update {
                        it.copy(downloadingDriveIds = it.downloadingDriveIds - remote.driveFileId)
                    }
                    _events.emit(FolioEvent.Snackbar(err.localizedMessage ?: "Download failed"))
                }
        }
    }

    fun deleteDriveBackup(remote: RemoteDrivePdfItem) {
        viewModelScope.launch {
            repository.deleteDriveBackup(remote.driveFileId)
                .onSuccess {
                    _driveSyncState.update { state ->
                        state.copy(
                            remoteFiles = state.remoteFiles.filterNot { it.driveFileId == remote.driveFileId }
                        )
                    }
                    _events.emit(FolioEvent.Snackbar("Removed ${remote.fileName} from Google Drive"))
                }
                .onFailure { err ->
                    _events.emit(FolioEvent.Snackbar(err.localizedMessage ?: "Failed to remove Drive backup"))
                }
        }
    }

    // --- Print Preview & All Printing PDF Tools ---

    fun openPrintPreviewForDocument(docId: Long) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(docId)
            if (doc == null) {
                _events.emit(FolioEvent.Snackbar("PDF file not found"))
                return@launch
            }
            val meta = repository.inspectPdfForPrint(doc.filePath, null)
            val effectivePages = meta.pageCount.takeIf { it > 0 } ?: doc.pageCount
            val effectiveAllowPrint = doc.allowPrinting && meta.allowPrinting
            val initialConfig = _printPreviewState.value.config
            val plan = repository.computePrintSheetPlan(effectivePages, initialConfig)
            _printPreviewState.value = PrintPreviewUiState(
                documentId = doc.id,
                localFilePath = doc.filePath,
                uri = null,
                fileName = doc.fileName,
                totalSourcePages = effectivePages,
                fileSizeBytes = doc.fileSizeBytes,
                config = initialConfig,
                jobSummary = plan,
                currentSheetIndex = 0,
                zoomScale = 1f,
                previewBitmap = null,
                isRenderingPreview = !meta.requiresPassword,
                requiresPassword = meta.requiresPassword,
                allowPrinting = effectiveAllowPrint
            )
            _events.emit(FolioEvent.OpenPrintPreview)
            if (!meta.requiresPassword) {
                renderCurrentPrintSheet()
            }
        }
    }

    fun openPrintPreviewFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val meta = repository.inspectPdfForPrint(null, uri)
                val initialConfig = _printPreviewState.value.config
                val plan = repository.computePrintSheetPlan(meta.pageCount, initialConfig)
                _printPreviewState.value = PrintPreviewUiState(
                    documentId = null,
                    localFilePath = null,
                    uri = uri,
                    fileName = meta.fileName,
                    totalSourcePages = meta.pageCount,
                    fileSizeBytes = meta.fileSizeBytes,
                    config = initialConfig,
                    jobSummary = plan,
                    currentSheetIndex = 0,
                    zoomScale = 1f,
                    previewBitmap = null,
                    isRenderingPreview = !meta.requiresPassword,
                    requiresPassword = meta.requiresPassword,
                    allowPrinting = meta.allowPrinting
                )
                _events.emit(FolioEvent.OpenPrintPreview)
                if (!meta.requiresPassword) {
                    renderCurrentPrintSheet()
                }
            } catch (e: Exception) {
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Unable to open PDF for printing"))
            }
        }
    }

    fun openPrintPreviewFromToolsSource() {
        val src = _pdfToolsState.value.singleSource ?: return
        viewModelScope.launch {
            try {
                val meta = repository.inspectPdfForPrint(src.localFilePath, src.uri)
                val effectivePages = meta.pageCount.takeIf { it > 0 } ?: src.pageCount
                val initialConfig = _printPreviewState.value.config
                val plan = repository.computePrintSheetPlan(effectivePages, initialConfig)
                _printPreviewState.value = PrintPreviewUiState(
                    documentId = null,
                    localFilePath = src.localFilePath,
                    uri = src.uri,
                    fileName = src.displayName,
                    totalSourcePages = effectivePages,
                    fileSizeBytes = src.sizeBytes,
                    config = initialConfig,
                    jobSummary = plan,
                    currentSheetIndex = 0,
                    zoomScale = 1f,
                    previewBitmap = null,
                    isRenderingPreview = !meta.requiresPassword,
                    requiresPassword = meta.requiresPassword,
                    allowPrinting = meta.allowPrinting
                )
                _events.emit(FolioEvent.OpenPrintPreview)
                if (!meta.requiresPassword) {
                    renderCurrentPrintSheet()
                }
            } catch (e: Exception) {
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Unable to prepare print preview"))
            }
        }
    }

    fun updatePrintConfig(transform: (PrintConfig) -> PrintConfig) {
        val current = _printPreviewState.value
        val nextConfig = normalizePrintConfig(transform(current.config), current.config)
        val plan = repository.computePrintSheetPlan(current.totalSourcePages, nextConfig)
        val nextSheetIdx = current.currentSheetIndex.coerceIn(0, (plan.sheets.size - 1).coerceAtLeast(0))
        _printPreviewState.update {
            it.copy(
                config = nextConfig,
                jobSummary = plan,
                currentSheetIndex = nextSheetIdx
            )
        }
        if (current.localFilePath != null || current.uri != null) {
            renderCurrentPrintSheet()
        }
    }

    private fun normalizePrintConfig(next: PrintConfig, prev: PrintConfig): PrintConfig {
        var adjusted = next
        if (next.layoutTool != prev.layoutTool) {
            adjusted = when (next.layoutTool) {
                PrintLayoutTool.BOOKLET -> next.copy(pagesPerSheet = PrintPagesPerSheet.TWO)
                PrintLayoutTool.N_UP -> if (next.pagesPerSheet == PrintPagesPerSheet.ONE) {
                    next.copy(pagesPerSheet = PrintPagesPerSheet.TWO)
                } else next
                PrintLayoutTool.STANDARD -> if (prev.layoutTool == PrintLayoutTool.BOOKLET) {
                    next.copy(pagesPerSheet = PrintPagesPerSheet.ONE)
                } else next
                PrintLayoutTool.POSTER_FIT -> next.copy(pagesPerSheet = PrintPagesPerSheet.ONE)
            }
        }
        return adjusted.copy(copies = adjusted.copies.coerceIn(1, 10))
    }

    fun selectPrintSheet(sheetIndex: Int) {
        val total = _printPreviewState.value.jobSummary.sheets.size
        if (total == 0) return
        val clamped = sheetIndex.coerceIn(0, total - 1)
        _printPreviewState.update { it.copy(currentSheetIndex = clamped) }
        renderCurrentPrintSheet()
    }

    fun setPrintPreviewZoom(scale: Float) {
        val clamped = scale.coerceIn(0.8f, 2.5f)
        _printPreviewState.update { it.copy(zoomScale = clamped) }
        renderCurrentPrintSheet()
    }

    fun updatePrintPasswordInput(password: String) {
        _printPreviewState.update { it.copy(passwordInput = password, passwordError = null) }
    }

    fun unlockPrintPdf() {
        val state = _printPreviewState.value
        val path = state.localFilePath
        if (path == null) {
            _printPreviewState.update {
                it.copy(passwordError = "Unlock encrypted device PDFs by saving/importing them into Vault first.")
            }
            return
        }
        val entered = state.passwordInput
        if (entered.isEmpty()) {
            _printPreviewState.update { it.copy(passwordError = "Enter PDF password") }
            return
        }
        viewModelScope.launch {
            _printPreviewState.update { it.copy(isRenderingPreview = true, passwordError = null) }
            repository.verifyPdfPassword(path, entered)
                .onSuccess { inspection ->
                    val canPrint = inspection.allowPrinting
                    val total = state.totalSourcePages.takeIf { it > 0 } ?: 1
                    val plan = repository.computePrintSheetPlan(total, state.config)
                    _printPreviewState.update {
                        it.copy(
                            requiresPassword = false,
                            unlockedPassword = entered,
                            allowPrinting = canPrint,
                            jobSummary = plan,
                            passwordError = null
                        )
                    }
                    renderCurrentPrintSheet()
                }
                .onFailure {
                    _printPreviewState.update {
                        it.copy(
                            isRenderingPreview = false,
                            passwordError = "Incorrect password. Try again."
                        )
                    }
                }
        }
    }

    private fun renderCurrentPrintSheet() {
        printPreviewJob?.cancel()
        printPreviewJob = viewModelScope.launch {
            val state = _printPreviewState.value
            if (state.requiresPassword) return@launch
            val sheet = state.jobSummary.sheets.getOrNull(state.currentSheetIndex)
            if (sheet == null) {
                _printPreviewState.update { it.copy(previewBitmap = null, isRenderingPreview = false) }
                return@launch
            }
            _printPreviewState.update { it.copy(isRenderingPreview = true) }
            val widthPx = (960 * state.zoomScale).toInt().coerceIn(640, 1800)
            val bmp = repository.renderPrintPreviewSheet(
                localFilePath = state.localFilePath,
                uri = state.uri,
                documentTitle = state.fileName,
                sheet = sheet,
                totalSheets = state.jobSummary.sheets.size,
                config = state.config,
                password = state.unlockedPassword,
                targetWidthPx = widthPx
            )
            _printPreviewState.update { it.copy(previewBitmap = bmp, isRenderingPreview = false) }
        }
    }

    fun executeSystemPrint(activityContext: Context) {
        val state = _printPreviewState.value
        if (state.isGeneratingPrintPdf || state.requiresPassword) return
        if (!state.allowPrinting) {
            viewModelScope.launch {
                _events.emit(FolioEvent.Snackbar("Printing is disabled by this PDF's security policy."))
            }
            return
        }
        viewModelScope.launch {
            _printPreviewState.update {
                it.copy(isGeneratingPrintPdf = true, progressMessage = "Composing print sheets...")
            }
            try {
                val printFile = repository.generateTempPrintPdf(
                    localFilePath = state.localFilePath,
                    uri = state.uri,
                    documentTitle = state.fileName,
                    config = state.config,
                    password = state.unlockedPassword,
                    onProgress = { cur, total ->
                        _printPreviewState.update {
                            it.copy(progressMessage = "Preparing sheet $cur of $total...")
                        }
                    }
                )
                _printPreviewState.update { it.copy(isGeneratingPrintPdf = false, progressMessage = "") }
                val jobName = "${state.fileName.removeSuffix(".pdf")} (${state.config.layoutTool.label})"
                repository.launchSystemPrint(
                    activityContext = activityContext,
                    jobName = jobName,
                    printReadyPdfFile = printFile,
                    config = state.config
                )
            } catch (e: Exception) {
                _printPreviewState.update { it.copy(isGeneratingPrintPdf = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Unable to launch print job"))
            }
        }
    }

    fun savePrintReadyPdfToVault() {
        val state = _printPreviewState.value
        if (state.isGeneratingPrintPdf || state.requiresPassword) return
        if (!state.allowPrinting) {
            viewModelScope.launch {
                _events.emit(FolioEvent.Snackbar("Printing/re-layout export is disabled by this PDF's security policy."))
            }
            return
        }
        viewModelScope.launch {
            _printPreviewState.update {
                it.copy(isGeneratingPrintPdf = true, progressMessage = "Saving print layout PDF...")
            }
            try {
                val saved = repository.savePrintReadyPdfToVault(
                    localFilePath = state.localFilePath,
                    uri = state.uri,
                    documentTitle = state.fileName,
                    config = state.config,
                    password = state.unlockedPassword,
                    metadata = _metadataConfig.value,
                    onProgress = { cur, total ->
                        _printPreviewState.update {
                            it.copy(progressMessage = "Rendering sheet $cur of $total...")
                        }
                    }
                )
                _printPreviewState.update { it.copy(isGeneratingPrintPdf = false, progressMessage = "") }
                _events.emit(
                    FolioEvent.Snackbar("Saved print-ready PDF ${saved.fileName} (${saved.pageCount} sheet(s))")
                )
            } catch (e: Exception) {
                _printPreviewState.update { it.copy(isGeneratingPrintPdf = false, progressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to save print PDF"))
            }
        }
    }

    // --- Document Scanner, QR Code Scanner & QR Generator ---

    fun openScannerWorkspace(tab: ScannerTab = ScannerTab.DOCUMENT) {
        _scannerState.update { it.copy(activeTab = tab) }
        viewModelScope.launch {
            _events.emit(FolioEvent.OpenScannerQr(tab))
        }
    }

    fun selectScannerTab(tab: ScannerTab) {
        _scannerState.update { it.copy(activeTab = tab) }
    }

    fun setScannerTorchEnabled(enabled: Boolean) {
        _scannerState.update { it.copy(isTorchEnabled = enabled) }
    }

    fun toggleScannerCameraPaused() {
        _scannerState.update { it.copy(isLiveCameraPaused = !it.isLiveCameraPaused) }
    }

    fun onDocumentScanCompleted(pageUris: List<Uri>, directPdfUri: Uri?) {
        val pages = pageUris.map { ScannedDocPage(imageUri = it) }
        val defaultName = "Scan_${android.text.format.DateFormat.format("yyyyMMdd_HHmm", System.currentTimeMillis())}"
        _scannerState.update {
            it.copy(
                activeTab = ScannerTab.DOCUMENT,
                scannedPages = pages,
                scannedDirectPdfUri = directPdfUri,
                scanOutputName = defaultName,
                lastScannedDoc = null
            )
        }
    }

    fun addScannedPhotosFallback(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val newPages = uris.map { ScannedDocPage(imageUri = it) }
        _scannerState.update { state ->
            val updated = state.scannedPages + newPages
            val name = if (state.scannedPages.isEmpty()) {
                "Scan_${android.text.format.DateFormat.format("yyyyMMdd_HHmm", System.currentTimeMillis())}"
            } else {
                state.scanOutputName
            }
            state.copy(
                scannedPages = updated,
                scannedDirectPdfUri = null,
                scanOutputName = name,
                lastScannedDoc = null
            )
        }
    }

    fun removeScannedPage(pageId: String) {
        _scannerState.update { state ->
            val updated = state.scannedPages.filterNot { it.id == pageId }
            state.copy(scannedPages = updated, scannedDirectPdfUri = null)
        }
    }

    fun updateScanOutputName(name: String) {
        _scannerState.update { it.copy(scanOutputName = name) }
    }

    fun clearScannedSession() {
        _scannerState.update {
            it.copy(
                scannedPages = emptyList(),
                scannedDirectPdfUri = null,
                lastScannedDoc = null,
                scanProgressMessage = ""
            )
        }
    }

    fun sendScannedPagesToStudio() {
        val pages = _scannerState.value.scannedPages
        if (pages.isEmpty()) return
        val uris = pages.map { it.imageUri }
        viewModelScope.launch {
            val inspected = repository.inspectUris(uris)
            val suggestedName = _scannerState.value.scanOutputName.ifBlank { "Scanned_Document" }
            _studioState.update { state ->
                state.copy(
                    queue = state.queue + inspected,
                    outputName = if (state.queue.isEmpty()) suggestedName else state.outputName,
                    lastGeneratedDoc = null
                )
            }
            _pdfToolsState.update { it.copy(activeTool = PdfToolMode.CREATE_PDF) }
            _events.emit(FolioEvent.Snackbar("Added ${inspected.size} scanned page(s) to Studio to edit metadata or convert"))
        }
    }

    fun saveScannedDocumentAsPdf(runOcrPipeline: Boolean = true) {
        val state = _scannerState.value
        if (state.isFinalizingScan) return
        val pageUris = state.scannedPages.map { it.imageUri }
        val directPdf = state.scannedDirectPdfUri
        if (pageUris.isEmpty() && directPdf == null) return

        viewModelScope.launch {
            _scannerState.update {
                it.copy(isFinalizingScan = true, scanProgressMessage = "Finalizing scanned document...")
            }
            try {
                val outName = state.scanOutputName.ifBlank { "Scanned_Document" }
                val saved = if (runOcrPipeline && pageUris.isNotEmpty()) {
                    val items = repository.inspectUris(pageUris)
                    repository.convertAndSave(
                        items = items,
                        desiredName = outName,
                        settings = settings.value,
                        security = _securityConfig.value,
                        metadata = _metadataConfig.value,
                        watermark = _watermarkConfig.value,
                        onProgress = { msg ->
                            _scannerState.update { it.copy(scanProgressMessage = msg) }
                        }
                    )
                } else if (directPdf != null) {
                    repository.importScannedPdfFromUri(
                        pdfUri = directPdf,
                        desiredName = outName,
                        pageCountHint = pageUris.size.coerceAtLeast(1),
                        security = _securityConfig.value,
                        metadata = _metadataConfig.value,
                        watermark = _watermarkConfig.value
                    )
                } else {
                    val items = repository.inspectUris(pageUris)
                    repository.convertAndSave(
                        items = items,
                        desiredName = outName,
                        settings = settings.value,
                        security = _securityConfig.value,
                        metadata = _metadataConfig.value,
                        watermark = _watermarkConfig.value
                    )
                }
                _scannerState.update {
                    it.copy(
                        isFinalizingScan = false,
                        scanProgressMessage = "",
                        lastScannedDoc = saved
                    )
                }
                _events.emit(FolioEvent.Snackbar("Saved scanned PDF ${saved.fileName} (${saved.pageCount} page(s))"))
            } catch (e: Exception) {
                _scannerState.update { it.copy(isFinalizingScan = false, scanProgressMessage = "") }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to save scanned PDF"))
            }
        }
    }

    fun handleLiveBarcodes(barcodes: List<Barcode>) {
        if (barcodes.isEmpty() || _scannerState.value.isLiveCameraPaused) return
        val newItems = barcodes.mapNotNull { repository.qrEngine.toScannedItem(it) }
        if (newItems.isEmpty()) return
        _scannerState.update { state ->
            val currentTop = state.scannedBarcodes.firstOrNull()?.rawValue
            val filtered = newItems.filter { it.rawValue != currentTop }
            if (filtered.isEmpty()) state
            else {
                val merged = (filtered + state.scannedBarcodes).distinctBy { it.rawValue }.take(25)
                state.copy(scannedBarcodes = merged)
            }
        }
    }

    fun scanBarcodesFromImage(uri: Uri) {
        viewModelScope.launch {
            _scannerState.update { it.copy(isScanningImageBarcode = true) }
            try {
                val items = repository.scanBarcodesFromImageUri(uri)
                if (items.isEmpty()) {
                    _scannerState.update { it.copy(isScanningImageBarcode = false) }
                    _events.emit(FolioEvent.Snackbar("No QR code or barcode detected in that image."))
                } else {
                    _scannerState.update { state ->
                        val merged = (items + state.scannedBarcodes).distinctBy { it.rawValue }.take(25)
                        state.copy(
                            isScanningImageBarcode = false,
                            scannedBarcodes = merged
                        )
                    }
                    _events.emit(FolioEvent.Snackbar("Detected ${items.size} QR / barcode result(s)"))
                }
            } catch (e: Exception) {
                _scannerState.update { it.copy(isScanningImageBarcode = false) }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to scan image"))
            }
        }
    }

    fun clearScannedBarcodes() {
        _scannerState.update { it.copy(scannedBarcodes = emptyList()) }
    }

    fun useScannedValueInGenerator(value: String) {
        _scannerState.update { state ->
            state.copy(
                activeTab = ScannerTab.QR_GENERATE,
                qrGenerator = state.qrGenerator.copy(
                    contentType = QrContentType.TEXT_URL,
                    primaryText = value
                )
            )
        }
        regenerateQrPreview()
    }

    fun updateQrGenerator(transform: (QrGeneratorUiState) -> QrGeneratorUiState) {
        _scannerState.update { state ->
            state.copy(qrGenerator = transform(state.qrGenerator).copy(lastSavedPdfDoc = null))
        }
        regenerateQrPreview()
    }

    private fun regenerateQrPreview() {
        val gen = _scannerState.value.qrGenerator
        val payload = repository.buildQrPayload(
            type = gen.contentType,
            primary = gen.primaryText,
            secondary = gen.secondaryText,
            tertiary = gen.tertiaryText,
            wifiSecurity = gen.wifiSecurity
        )
        val bmp = runCatching {
            repository.generateQrBitmap(
                payload = payload,
                sizePx = 640,
                preset = gen.colorPreset,
                caption = gen.caption
            )
        }.getOrNull()
        _scannerState.update { state ->
            state.copy(
                qrGenerator = state.qrGenerator.copy(
                    encodedPayload = payload,
                    previewBitmap = bmp
                )
            )
        }
    }

    fun saveGeneratedQrPng(contextForShare: Context? = null, shareAfterSave: Boolean = false) {
        val gen = _scannerState.value.qrGenerator
        val bmp = gen.previewBitmap ?: return
        if (gen.isSaving) return
        viewModelScope.launch {
            _scannerState.update { it.copy(qrGenerator = it.qrGenerator.copy(isSaving = true)) }
            try {
                val baseName = gen.caption.ifBlank { "Folio_QR" }
                val (cacheFile, locationLabel) = repository.saveQrPng(bmp, baseName)
                _scannerState.update {
                    it.copy(
                        qrGenerator = it.qrGenerator.copy(
                            isSaving = false,
                            lastSavedImagePath = cacheFile.absolutePath
                        )
                    )
                }
                if (shareAfterSave && contextForShare != null) {
                    shareImageFile(contextForShare, cacheFile.absolutePath, cacheFile.name, "image/png")
                } else {
                    _events.emit(FolioEvent.Snackbar("Saved QR image to $locationLabel"))
                }
            } catch (e: Exception) {
                _scannerState.update { it.copy(qrGenerator = it.qrGenerator.copy(isSaving = false)) }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to save QR image"))
            }
        }
    }

    fun saveGeneratedQrPdfToVault() {
        val gen = _scannerState.value.qrGenerator
        val bmp = gen.previewBitmap ?: return
        if (gen.isSaving) return
        viewModelScope.launch {
            _scannerState.update { it.copy(qrGenerator = it.qrGenerator.copy(isSaving = true)) }
            try {
                val outBase = gen.caption.ifBlank { "Folio_QR_Code" }
                val doc = repository.saveQrAsPdfToVault(
                    bitmap = bmp,
                    payload = gen.encodedPayload,
                    caption = gen.caption.ifBlank { gen.contentType.label },
                    outputBaseName = outBase,
                    security = _securityConfig.value,
                    metadata = _metadataConfig.value,
                    watermark = _watermarkConfig.value
                )
                _scannerState.update {
                    it.copy(
                        qrGenerator = it.qrGenerator.copy(
                            isSaving = false,
                            lastSavedPdfDoc = doc
                        )
                    )
                }
                _events.emit(FolioEvent.Snackbar("Saved ${doc.fileName} to Vault"))
            } catch (e: Exception) {
                _scannerState.update { it.copy(qrGenerator = it.qrGenerator.copy(isSaving = false)) }
                _events.emit(FolioEvent.Snackbar(e.localizedMessage ?: "Failed to save QR PDF"))
            }
        }
    }
}
