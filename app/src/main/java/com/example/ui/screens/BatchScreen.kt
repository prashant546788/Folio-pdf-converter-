package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.BatchItemState
import com.example.model.BatchStatus
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.ConversionSettings
import com.example.model.PdfMetadataConfig
import com.example.model.PdfSecurityConfig
import com.example.model.PdfWatermarkConfig
import com.example.model.SourceKind
import com.example.ui.BatchUiState
import com.example.ui.components.CloudUploadSheet
import com.example.ui.components.ConversionSettingsCard
import com.example.ui.components.PdfMetadataSettingsCard
import com.example.ui.components.PdfSecuritySettingsCard
import com.example.ui.components.PdfWatermarkSettingsCard
import com.example.ui.components.SourceKindBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize

private val BATCH_MIXED_MIMES = arrayOf(
    "image/*",
    "text/plain",
    "text/markdown",
    "text/csv",
    "text/comma-separated-values",
    "text/html",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchScreen(
    state: BatchUiState,
    settings: ConversionSettings,
    security: PdfSecurityConfig,
    metadata: PdfMetadataConfig,
    watermark: PdfWatermarkConfig,
    cloudAccounts: List<CloudAccountState>,
    onAddFiles: (List<android.net.Uri>) -> Unit,
    onUpdateOutputName: (String, String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearQueue: () -> Unit,
    onSetAutoUploadProvider: (CloudProvider?) -> Unit,
    onUpdateSettings: ((ConversionSettings) -> ConversionSettings) -> Unit,
    onUpdateSecurity: ((PdfSecurityConfig) -> PdfSecurityConfig) -> Unit,
    onUpdateMetadata: ((PdfMetadataConfig) -> PdfMetadataConfig) -> Unit,
    onUpdateWatermark: ((PdfWatermarkConfig) -> PdfWatermarkConfig) -> Unit,
    onStartBatch: () -> Unit,
    onCancelBatch: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    onUploadAllCompletedToCloud: (CloudProvider) -> Unit,
    onConnectCloud: (CloudProvider) -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showBatchCloudSheet by remember { mutableStateOf(false) }

    val mixedFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    val completedItems = remember(state.items) {
        state.items.filter { it.status == BatchStatus.COMPLETED }
    }
    val pendingCount = remember(state.items) {
        state.items.count { it.status != BatchStatus.COMPLETED }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DynamicFeed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Batch PDF Converter",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Select multiple images & documents at once and convert each file into its own individual PDF.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { mixedFilePicker.launch(BATCH_MIXED_MIMES) },
                                enabled = !state.isRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("batch_select_files_btn")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Select Files")
                            }
                            FilledTonalButton(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !state.isRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("batch_add_photos_btn")
                            ) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Photos")
                            }
                        }
                    }
                }
            }

            if (state.isRunning) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_progress_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Converting ${state.completedCount} of ${state.totalCount} files...",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                TextButton(onClick = onCancelBatch) {
                                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cancel")
                                }
                            }
                            val progress = if (state.totalCount > 0) {
                                state.completedCount.toFloat() / state.totalCount.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            if (state.lastBatchSummary != null && !state.isRunning) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_summary_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
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
                                Text(
                                    text = state.lastBatchSummary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onOpenVault,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Open Vault")
                                }
                                if (completedItems.isNotEmpty()) {
                                    Button(
                                        onClick = { showBatchCloudSheet = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("batch_upload_all_btn")
                                    ) {
                                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Upload All")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Auto-Save Batch to Cloud",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Optionally upload each generated PDF immediately to a connected cloud account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.autoUploadProvider == null,
                                onClick = { onSetAutoUploadProvider(null) },
                                enabled = !state.isRunning,
                                label = { Text("Local Only") }
                            )
                            cloudAccounts.forEach { acct ->
                                FilterChip(
                                    selected = state.autoUploadProvider == acct.provider,
                                    onClick = {
                                        if (acct.isConnected) {
                                            onSetAutoUploadProvider(acct.provider)
                                        } else {
                                            onConnectCloud(acct.provider)
                                        }
                                    },
                                    enabled = !state.isRunning,
                                    label = {
                                        Text(
                                            if (acct.isConnected) acct.provider.displayName
                                            else "${acct.provider.displayName} (Connect)"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.items.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Batch Queue (${state.items.size} individual PDFs)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = onClearQueue,
                            enabled = !state.isRunning
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    BatchQueueItemCard(
                        item = item,
                        isBatchRunning = state.isRunning,
                        onOutputNameChange = { newName -> onUpdateOutputName(item.id, newName) },
                        onRemove = { onRemoveItem(item.id) },
                        onOpenViewer = { item.resultDocumentId?.let(onOpenViewer) }
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
                    PdfMetadataSettingsCard(
                        metadata = metadata,
                        onUpdate = onUpdateMetadata,
                        subtitle = "Set shared Author, Subject, Keywords & Title before batch finalization"
                    )
                }

                item {
                    PdfWatermarkSettingsCard(
                        watermark = watermark,
                        onUpdate = onUpdateWatermark,
                        subtitle = "Stamp a copyright text or logo watermark onto every batch PDF"
                    )
                }

                item {
                    PdfSecuritySettingsCard(
                        security = security,
                        onUpdate = onUpdateSecurity,
                        subtitle = "Apply password and print/copy restrictions to every batch PDF"
                    )
                }

                item {
                    Button(
                        onClick = onStartBatch,
                        enabled = !state.isRunning && pendingCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("batch_convert_all_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (pendingCount > 0) {
                                "Convert All ($pendingCount) to Individual PDFs"
                            } else {
                                "All Queued Files Converted"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            } else {
                item {
                    ConversionSettingsCard(
                        settings = settings,
                        onUpdate = onUpdateSettings,
                        onOpenFullSettings = onOpenSettings
                    )
                }
                item {
                    PdfMetadataSettingsCard(
                        metadata = metadata,
                        onUpdate = onUpdateMetadata,
                        subtitle = "Set shared Author, Subject, Keywords & Title before batch finalization"
                    )
                }
                item {
                    PdfWatermarkSettingsCard(
                        watermark = watermark,
                        onUpdate = onUpdateWatermark,
                        subtitle = "Stamp a copyright text or logo watermark onto every batch PDF"
                    )
                }
                item {
                    PdfSecuritySettingsCard(
                        security = security,
                        onUpdate = onUpdateSecurity,
                        subtitle = "Apply password and print/copy restrictions to every batch PDF"
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showBatchCloudSheet) {
        CloudUploadSheet(
            title = "Upload ${completedItems.size} Batch PDFs",
            accounts = cloudAccounts,
            onSelectProviderToUpload = onUploadAllCompletedToCloud,
            onConnectProvider = onConnectCloud,
            onDismiss = { showBatchCloudSheet = false }
        )
    }
}

@Composable
private fun BatchQueueItemCard(
    item: BatchItemState,
    isBatchRunning: Boolean,
    onOutputNameChange: (String) -> Unit,
    onRemove: () -> Unit,
    onOpenViewer: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.status == BatchStatus.COMPLETED && item.resultDocumentId != null) {
                    Modifier.clickable(onClick = onOpenViewer)
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.sourceItem.sourceKind == SourceKind.IMAGE) {
                        AsyncImage(
                            model = item.sourceItem.uri,
                            contentDescription = item.sourceItem.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.sourceItem.sourceKind == SourceKind.PDF) {
                                Icons.Filled.PictureAsPdf
                            } else {
                                Icons.Filled.Description
                            },
                            contentDescription = item.sourceItem.displayName,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sourceItem.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SourceKindBadge(kind = item.sourceItem.sourceKind)
                        Text(
                            text = formatFileSize(item.sourceItem.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (item.status) {
                    BatchStatus.RUNNING_OCR, BatchStatus.CONVERTING, BatchStatus.UPLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                    BatchStatus.COMPLETED -> {
                        IconButton(onClick = onOpenViewer) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "View converted PDF",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    BatchStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    BatchStatus.QUEUED -> {
                        IconButton(
                            onClick = onRemove,
                            enabled = !isBatchRunning
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from batch")
                        }
                    }
                }
            }

            if (item.status == BatchStatus.QUEUED && !isBatchRunning) {
                OutlinedTextField(
                    value = item.outputFileName,
                    onValueChange = onOutputNameChange,
                    label = { Text("Individual PDF Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.outputFileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    when (item.status) {
                        BatchStatus.COMPLETED -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item.ocrCharCount > 0) {
                                    StatusPill(
                                        text = "OCR",
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                if (item.uploadedProvider != null) {
                                    StatusPill(
                                        text = item.uploadedProvider.displayName,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        icon = Icons.Filled.CloudDone
                                    )
                                }
                                StatusPill(
                                    text = "${item.pageCount}p · ${formatFileSize(item.outputSizeBytes)}",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        BatchStatus.FAILED -> {
                            StatusPill(
                                text = item.errorMessage ?: "Failed",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        else -> {
                            StatusPill(
                                text = item.progressMessage,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
