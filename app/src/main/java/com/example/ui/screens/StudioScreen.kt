package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ConvertedPdfEntity
import com.example.data.pdf.ImageExportFormat
import com.example.data.pdf.PdfCompressionLevel
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.ConversionSettings
import com.example.model.PdfMetadataConfig
import com.example.model.PdfSecurityConfig
import com.example.model.PdfTranslationTone
import com.example.model.PdfWatermarkConfig
import com.example.model.PrintOrientationMode
import com.example.model.PrintPagesPerSheet
import com.example.model.PrintPaperSize
import com.example.model.QueuedSourceItem
import com.example.model.SourceKind
import com.example.ui.PdfToolMode
import com.example.ui.PdfToolsUiState
import com.example.ui.ScannerTab
import com.example.ui.StudioUiState
import com.example.ui.components.CloudUploadSheet
import com.example.ui.components.ConversionSettingsCard
import com.example.ui.components.PdfMetadataSettingsCard
import com.example.ui.components.PdfSecuritySettingsCard
import com.example.ui.components.PdfWatermarkSettingsCard
import com.example.ui.components.SourceKindBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.sharePdfFile

private val SUPPORTED_DOC_MIMES = arrayOf(
    "image/*",
    "text/plain",
    "text/markdown",
    "text/csv",
    "text/comma-separated-values",
    "text/html",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
)

@Composable
fun StudioScreen(
    state: StudioUiState,
    toolsState: PdfToolsUiState,
    vaultDocuments: List<ConvertedPdfEntity>,
    settings: ConversionSettings,
    security: PdfSecurityConfig,
    metadata: PdfMetadataConfig,
    watermark: PdfWatermarkConfig,
    cloudAccounts: List<CloudAccountState>,
    onAddFiles: (List<Uri>) -> Unit,
    onOutputNameChange: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRotate: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearQueue: () -> Unit,
    onUpdateSettings: ((ConversionSettings) -> ConversionSettings) -> Unit,
    onUpdateSecurity: ((PdfSecurityConfig) -> PdfSecurityConfig) -> Unit,
    onUpdateMetadata: ((PdfMetadataConfig) -> PdfMetadataConfig) -> Unit,
    onUpdateWatermark: ((PdfWatermarkConfig) -> PdfWatermarkConfig) -> Unit,
    onWatermarkOutputNameChange: (String) -> Unit,
    onRunWatermarkPdf: () -> Unit,
    onTranslationSourceLanguageChange: (String) -> Unit,
    onTranslationTargetLanguageChange: (String) -> Unit,
    onSwapTranslationLanguages: () -> Unit,
    onTranslationToneChange: (PdfTranslationTone) -> Unit,
    onTranslationOutputNameChange: (String) -> Unit,
    onRunTranslatePdf: () -> Unit,
    onConvert: () -> Unit,
    onSelectToolMode: (PdfToolMode) -> Unit,
    onPickSinglePdfUri: (Uri) -> Unit,
    onPickSingleVaultPdf: (ConvertedPdfEntity) -> Unit,
    onAddMergePdfUris: (List<Uri>) -> Unit,
    onAddMergeVaultPdf: (ConvertedPdfEntity) -> Unit,
    onMoveMergePdfUp: (Int) -> Unit,
    onMoveMergePdfDown: (Int) -> Unit,
    onRemoveMergePdf: (String) -> Unit,
    onClearMergePdfs: () -> Unit,
    onMergeOutputNameChange: (String) -> Unit,
    onImageFormatChange: (ImageExportFormat) -> Unit,
    onImageDpiChange: (Int) -> Unit,
    onToolPageRangeChange: (String) -> Unit,
    onToolSplitEachPageChange: (Boolean) -> Unit,
    onToolCompressionLevelChange: (PdfCompressionLevel) -> Unit,
    onToolRotationDegreesChange: (Int) -> Unit,
    onExistingPdfPasswordChange: (String) -> Unit,
    onMetadataOutputNameChange: (String) -> Unit,
    onUnlockMetadata: () -> Unit,
    onRunSaveMetadata: () -> Unit,
    onNUpPagesPerSheetChange: (PrintPagesPerSheet) -> Unit,
    onNUpPaperSizeChange: (PrintPaperSize) -> Unit,
    onNUpOrientationChange: (PrintOrientationMode) -> Unit,
    onNUpShowBordersChange: (Boolean) -> Unit,
    onNUpGrayscaleChange: (Boolean) -> Unit,
    onNUpOutputNameChange: (String) -> Unit,
    onRunFourInOne: () -> Unit,
    onQuickTextTitleChange: (String) -> Unit,
    onQuickTextBodyChange: (String) -> Unit,
    onApplyQuickTextTemplate: (String, String) -> Unit,
    onRunQuickTextToPdf: () -> Unit,
    onRunPdfToImages: () -> Unit,
    onRunMergePdfs: () -> Unit,
    onRunSplitPdf: () -> Unit,
    onRunCompressPdf: () -> Unit,
    onRunRotatePdf: () -> Unit,
    onRunProtectPdf: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    onOpenPrintPreviewForTool: () -> Unit,
    onPrintResultDoc: (Long) -> Unit,
    onSaveToDownloads: (ConvertedPdfEntity) -> Unit,
    onUploadToCloud: (ConvertedPdfEntity, CloudProvider) -> Unit,
    onConnectCloud: (CloudProvider) -> Unit,
    onGoToBatch: () -> Unit,
    onOpenScanner: (ScannerTab) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCloudSheetForDoc by remember { mutableStateOf<ConvertedPdfEntity?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 40)
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PDF & Image Tools",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfToolMode.entries.forEach { mode ->
                            val icon = when (mode) {
                                PdfToolMode.CREATE_PDF -> Icons.Filled.PictureAsPdf
                                PdfToolMode.TRANSLATE_PDF -> Icons.Filled.Translate
                                PdfToolMode.FOUR_IN_ONE -> Icons.Filled.GridView
                                PdfToolMode.QUICK_TEXT -> Icons.Filled.EditNote
                                PdfToolMode.WATERMARK_PDF -> Icons.Filled.Approval
                                PdfToolMode.EDIT_METADATA -> Icons.Filled.Badge
                                PdfToolMode.PRINT_PREVIEW -> Icons.Filled.Print
                                PdfToolMode.PDF_TO_IMAGES -> Icons.Filled.Image
                                PdfToolMode.MERGE_PDFS -> Icons.AutoMirrored.Filled.MergeType
                                PdfToolMode.SPLIT_PDF -> Icons.AutoMirrored.Filled.CallSplit
                                PdfToolMode.COMPRESS_PDF -> Icons.Filled.Compress
                                PdfToolMode.ROTATE_PDF -> Icons.AutoMirrored.Filled.RotateRight
                                PdfToolMode.PROTECT_PDF -> Icons.Filled.Lock
                            }
                            FilterChip(
                                selected = toolsState.activeTool == mode,
                                onClick = { onSelectToolMode(mode) },
                                enabled = !state.isConverting && !toolsState.isProcessing,
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("tool_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (toolsState.activeTool != PdfToolMode.CREATE_PDF) {
                item {
                    PdfToolsSection(
                        toolsState = toolsState,
                        security = security,
                        metadata = metadata,
                        watermark = watermark,
                        vaultDocuments = vaultDocuments,
                        onPickSingleUri = onPickSinglePdfUri,
                        onPickSingleVaultDoc = onPickSingleVaultPdf,
                        onAddMergeUris = onAddMergePdfUris,
                        onAddMergeVaultDoc = onAddMergeVaultPdf,
                        onMoveMergeUp = onMoveMergePdfUp,
                        onMoveMergeDown = onMoveMergePdfDown,
                        onRemoveMergeItem = onRemoveMergePdf,
                        onClearMergeList = onClearMergePdfs,
                        onMergeOutputNameChange = onMergeOutputNameChange,
                        onImageFormatChange = onImageFormatChange,
                        onImageDpiChange = onImageDpiChange,
                        onPageRangeChange = onToolPageRangeChange,
                        onSplitEachPageChange = onToolSplitEachPageChange,
                        onCompressionLevelChange = onToolCompressionLevelChange,
                        onRotationDegreesChange = onToolRotationDegreesChange,
                        onExistingPdfPasswordChange = onExistingPdfPasswordChange,
                        onUpdateSecurity = onUpdateSecurity,
                        onUpdateMetadata = onUpdateMetadata,
                        onUpdateWatermark = onUpdateWatermark,
                        onWatermarkOutputNameChange = onWatermarkOutputNameChange,
                        onRunWatermarkPdf = onRunWatermarkPdf,
                        onTranslationSourceLanguageChange = onTranslationSourceLanguageChange,
                        onTranslationTargetLanguageChange = onTranslationTargetLanguageChange,
                        onSwapTranslationLanguages = onSwapTranslationLanguages,
                        onTranslationToneChange = onTranslationToneChange,
                        onTranslationOutputNameChange = onTranslationOutputNameChange,
                        onRunTranslatePdf = onRunTranslatePdf,
                        onMetadataOutputNameChange = onMetadataOutputNameChange,
                        onUnlockMetadata = onUnlockMetadata,
                        onRunSaveMetadata = onRunSaveMetadata,
                        onNUpPagesPerSheetChange = onNUpPagesPerSheetChange,
                        onNUpPaperSizeChange = onNUpPaperSizeChange,
                        onNUpOrientationChange = onNUpOrientationChange,
                        onNUpShowBordersChange = onNUpShowBordersChange,
                        onNUpGrayscaleChange = onNUpGrayscaleChange,
                        onNUpOutputNameChange = onNUpOutputNameChange,
                        onRunFourInOne = onRunFourInOne,
                        onQuickTextTitleChange = onQuickTextTitleChange,
                        onQuickTextBodyChange = onQuickTextBodyChange,
                        onApplyQuickTextTemplate = onApplyQuickTextTemplate,
                        onRunQuickTextToPdf = onRunQuickTextToPdf,
                        onOpenSettings = onOpenSettings,
                        onRunPdfToImages = onRunPdfToImages,
                        onRunMergePdfs = onRunMergePdfs,
                        onRunSplitPdf = onRunSplitPdf,
                        onRunCompressPdf = onRunCompressPdf,
                        onRunRotatePdf = onRunRotatePdf,
                        onRunProtectPdf = onRunProtectPdf,
                        onOpenViewer = onOpenViewer,
                        onOpenPrintPreviewForTool = onOpenPrintPreviewForTool,
                        onPrintResultDoc = onPrintResultDoc,
                        onSaveToDownloads = onSaveToDownloads,
                        onRequestCloudUpload = { doc -> showCloudSheetForDoc = doc }
                    )
                }
            } else {
                item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_studio_banner),
                                contentDescription = "Folio PDF Studio editorial banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            ) {
                                Text(
                                    text = if (settings.enableOcr) "OCR Ready" else "Standard Mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (settings.enableOcr) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Combine & Convert Studio",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Merge scans, photos, Markdown, TXT, CSV, HTML, DOCX, or PDFs into one crisp searchable PDF.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("studio_add_photos_btn")
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Photos")
                                }

                                FilledTonalButton(
                                    onClick = { documentPicker.launch(SUPPORTED_DOC_MIMES) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("studio_add_docs_btn")
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Documents")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenScanner(ScannerTab.DOCUMENT) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("studio_scan_doc_btn")
                                ) {
                                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan Doc")
                                }
                                OutlinedButton(
                                    onClick = { onOpenScanner(ScannerTab.QR_SCAN) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("studio_qr_tools_btn")
                                ) {
                                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("QR Tools")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onSelectToolMode(PdfToolMode.FOUR_IN_ONE) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("studio_quick_four_in_one_btn")
                                ) {
                                    Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("4-in-1 Page")
                                }
                                OutlinedButton(
                                    onClick = { onSelectToolMode(PdfToolMode.QUICK_TEXT) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("studio_quick_write_pdf_btn")
                                ) {
                                    Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Write → PDF")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = onGoToBatch,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("studio_switch_to_batch_btn")
                            ) {
                                Text("Need separate PDFs per file? Use Batch Convert →")
                            }
                        }
                    }
                }
            }

            if (state.lastGeneratedDoc != null) {
                item {
                    val doc = state.lastGeneratedDoc
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_result_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"} · ${formatFileSize(doc.fileSizeBytes)}" +
                                            (if (doc.ocrEnabled) " · Searchable OCR" else "") +
                                            (if (!doc.securitySummary.isNullOrBlank()) " · ${doc.securitySummary}" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onOpenViewer(doc.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("studio_preview_result_btn")
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("View")
                                }
                                FilledTonalButton(
                                    onClick = { showCloudSheetForDoc = doc },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("studio_cloud_result_btn")
                                ) {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cloud")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onPrintResultDoc(doc.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("studio_print_result_btn")
                                ) {
                                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Print")
                                }
                                OutlinedButton(
                                    onClick = { onSaveToDownloads(doc) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = { sharePdfFile(context, doc.filePath, doc.fileName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            }

            if (state.queue.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Page Queue (${state.queue.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = onClearQueue,
                            enabled = !state.isConverting
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }

                itemsIndexed(state.queue, key = { _, item -> item.id }) { index, item ->
                    StudioQueueItemCard(
                        index = index,
                        total = state.queue.size,
                        item = item,
                        enabled = !state.isConverting,
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onRotate = { onRotate(item.id) },
                        onRemove = { onRemove(item.id) }
                    )
                }

                item {
                    OutlinedTextField(
                        value = state.outputName,
                        onValueChange = onOutputNameChange,
                        label = { Text("Output PDF Filename") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        enabled = !state.isConverting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_output_name_input")
                    )
                }

                item {
                    PdfMetadataSettingsCard(
                        metadata = metadata,
                        onUpdate = onUpdateMetadata,
                        suggestedTitle = state.outputName
                    )
                }

                item {
                    ConversionSettingsCard(
                        settings = settings,
                        onUpdate = onUpdateSettings,
                        onOpenFullSettings = onOpenSettings
                    )
                }

                item {
                    PdfWatermarkSettingsCard(
                        watermark = watermark,
                        onUpdate = onUpdateWatermark
                    )
                }

                item {
                    PdfSecuritySettingsCard(
                        security = security,
                        onUpdate = onUpdateSecurity
                    )
                }

                item {
                    AnimatedVisibility(visible = state.isConverting) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = state.progressMessage.ifBlank { "Generating PDF..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onConvert,
                        enabled = !state.isConverting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("studio_convert_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (state.isConverting) "Converting..." else "Convert to High-Quality PDF",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                } else {
                    item {
                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = state.outputName
                        )
                    }
                    item {
                        ConversionSettingsCard(
                            settings = settings,
                            onUpdate = onUpdateSettings,
                            onOpenFullSettings = onOpenSettings
                        )
                    }
                    item {
                        PdfWatermarkSettingsCard(
                            watermark = watermark,
                            onUpdate = onUpdateWatermark
                        )
                    }
                    item {
                        PdfSecuritySettingsCard(
                            security = security,
                            onUpdate = onUpdateSecurity
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val cloudTargetDoc = showCloudSheetForDoc
    if (cloudTargetDoc != null) {
        CloudUploadSheet(
            title = "Save ${cloudTargetDoc.fileName}",
            accounts = cloudAccounts,
            onSelectProviderToUpload = { provider -> onUploadToCloud(cloudTargetDoc, provider) },
            onConnectProvider = onConnectCloud,
            onDismiss = { showCloudSheetForDoc = null }
        )
    }
}

@Composable
private fun StudioQueueItemCard(
    index: Int,
    total: Int,
    item: QueuedSourceItem,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRotate: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (item.sourceKind == SourceKind.IMAGE) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(item.rotationDegrees.toFloat())
                    )
                } else {
                    Icon(
                        imageVector = if (item.sourceKind == SourceKind.PDF) {
                            Icons.Filled.PictureAsPdf
                        } else {
                            Icons.Filled.Description
                        },
                        contentDescription = item.displayName,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SourceKindBadge(kind = item.sourceKind)
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.rotationDegrees != 0) {
                        StatusPill(
                            text = "${item.rotationDegrees}°",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (item.sourceKind == SourceKind.IMAGE) {
                IconButton(
                    onClick = onRotate,
                    enabled = enabled
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
                }
            }
            if (total > 1) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = enabled && index > 0
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = enabled && index < total - 1
                ) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
            }
            IconButton(
                onClick = onRemove,
                enabled = enabled
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove item")
            }
        }
    }
}
