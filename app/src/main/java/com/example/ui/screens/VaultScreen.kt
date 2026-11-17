package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ConvertedPdfEntity
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.VaultFilter
import com.example.ui.PdfToolMode
import com.example.ui.components.CloudUploadSheet
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.components.sharePdfFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    documents: List<ConvertedPdfEntity>,
    searchQuery: String,
    activeFilter: VaultFilter,
    cloudAccounts: List<CloudAccountState>,
    uploadingDocIds: Set<Long>,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (VaultFilter) -> Unit,
    onOpenViewer: (Long) -> Unit,
    onUploadToCloud: (ConvertedPdfEntity, CloudProvider) -> Unit,
    onConnectCloud: (CloudProvider) -> Unit,
    onSaveToDownloads: (ConvertedPdfEntity) -> Unit,
    onExportToUri: (ConvertedPdfEntity, android.net.Uri) -> Unit,
    onDeleteDocument: (ConvertedPdfEntity) -> Unit,
    onPrintDocument: (Long) -> Unit,
    onSendToTool: (ConvertedPdfEntity, PdfToolMode) -> Unit,
    onGoToStudio: () -> Unit,
    onGoToBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cloudTargetDoc by remember { mutableStateOf<ConvertedPdfEntity?>(null) }
    var safExportTarget by remember { mutableStateOf<ConvertedPdfEntity?>(null) }
    var toolsTargetDoc by remember { mutableStateOf<ConvertedPdfEntity?>(null) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val doc = safExportTarget
        if (uri != null && doc != null) {
            onExportToUri(doc, uri)
        }
        safExportTarget = null
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PDF Vault",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search filenames or OCR text inside PDFs...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick = { onFilterChange(filter) },
                                label = { Text(filter.label) }
                            )
                        }
                    }
                }
            }

            if (documents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_vault),
                                contentDescription = "Empty PDF vault",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Text(
                                text = if (searchQuery.isBlank()) "No PDFs in your Vault yet" else "No matching PDFs",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "Convert images or documents in Studio, or convert a full stack at once in Batch mode."
                                } else {
                                    "Try searching another keyword or clearing active filters."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onGoToStudio,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Open Studio")
                                }
                                FilledTonalButton(
                                    onClick = onGoToBatch,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Text("Batch Convert")
                                }
                            }
                        }
                    }
                }
            } else {
                items(documents, key = { it.id }) { doc ->
                    val isUploading = uploadingDocIds.contains(doc.id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenViewer(doc.id) }
                            .testTag("vault_doc_card_${doc.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PictureAsPdf,
                                        contentDescription = doc.fileName,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${doc.pageCount}p · ${formatFileSize(doc.fileSizeBytes)} · ${formatTimestamp(doc.createdAtMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (doc.ocrEnabled) {
                                    StatusPill(
                                        text = "OCR Searchable",
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        icon = Icons.Filled.DocumentScanner
                                    )
                                }
                                if (doc.isPasswordProtected || !doc.securitySummary.isNullOrBlank()) {
                                    StatusPill(
                                        text = doc.securitySummary ?: "Protected",
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        icon = Icons.Filled.Lock
                                    )
                                }
                                if (doc.batchId != null) {
                                    StatusPill(
                                        text = "Batch",
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                if (!doc.lastUploadedProvider.isNullOrBlank()) {
                                    StatusPill(
                                        text = doc.lastUploadedProvider,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        icon = Icons.Filled.CloudDone
                                    )
                                }
                                if (!doc.driveFileId.isNullOrBlank()) {
                                    StatusPill(
                                        text = "Drive Synced",
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        icon = Icons.Filled.CloudDone
                                    )
                                }
                            }

                            if (doc.ocrText.isNotBlank()) {
                                Text(
                                    text = doc.ocrText.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { cloudTargetDoc = doc },
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Cloud")
                                    }
                                    FilledTonalButton(
                                        onClick = { toolsTargetDoc = doc },
                                        modifier = Modifier
                                            .height(40.dp)
                                            .testTag("vault_tools_btn_${doc.id}")
                                    ) {
                                        Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tools")
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = { onPrintDocument(doc.id) },
                                        modifier = Modifier.testTag("vault_print_btn_${doc.id}")
                                    ) {
                                        Icon(Icons.Filled.Print, contentDescription = "Print Preview")
                                    }
                                    IconButton(onClick = {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            onSaveToDownloads(doc)
                                        } else {
                                            safExportTarget = doc
                                            saveAsLauncher.launch(doc.fileName)
                                        }
                                    }) {
                                        Icon(Icons.Filled.Download, contentDescription = "Save to Downloads")
                                    }
                                    IconButton(onClick = { sharePdfFile(context, doc.filePath, doc.fileName) }) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                                    }
                                    IconButton(onClick = { onDeleteDocument(doc) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete PDF")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val target = cloudTargetDoc
    if (target != null) {
        CloudUploadSheet(
            title = "Upload ${target.fileName}",
            accounts = cloudAccounts,
            onSelectProviderToUpload = { provider -> onUploadToCloud(target, provider) },
            onConnectProvider = onConnectCloud,
            onDismiss = { cloudTargetDoc = null }
        )
    }

    val toolTarget = toolsTargetDoc
    if (toolTarget != null) {
        ModalBottomSheet(onDismissRequest = { toolsTargetDoc = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PDF Tools · ${toolTarget.fileName}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val toolOptions = listOf(
                    Triple(PdfToolMode.TRANSLATE_PDF, Icons.Filled.Translate, "Translate PDF to Any Language"),
                    Triple(PdfToolMode.FOUR_IN_ONE, Icons.Filled.GridView, "4-in-1 Page Converter (2 / 4 / 6 / 9 per sheet)"),
                    Triple(PdfToolMode.WATERMARK_PDF, Icons.Filled.Approval, "Add Text or Image Copyright Watermark"),
                    Triple(PdfToolMode.PRINT_PREVIEW, Icons.Filled.Print, "Print Preview & All Printing Tools"),
                    Triple(PdfToolMode.EDIT_METADATA, Icons.Filled.Badge, "Edit PDF Metadata (Title, Author, Keywords)"),
                    Triple(PdfToolMode.PROTECT_PDF, Icons.Filled.Lock, "Set Password & Permissions"),
                    Triple(PdfToolMode.PDF_TO_IMAGES, Icons.Filled.Image, "Export Pages to PNG or JPG Images"),
                    Triple(PdfToolMode.SPLIT_PDF, Icons.AutoMirrored.Filled.CallSplit, "Split Pages or Extract Range"),
                    Triple(PdfToolMode.COMPRESS_PDF, Icons.Filled.Compress, "Compress PDF File Size"),
                    Triple(PdfToolMode.ROTATE_PDF, Icons.AutoMirrored.Filled.RotateRight, "Rotate All Pages (90° / 180° / 270°)"),
                    Triple(PdfToolMode.MERGE_PDFS, Icons.AutoMirrored.Filled.MergeType, "Add to Merge Queue")
                )
                toolOptions.forEach { (mode, icon, label) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (mode == PdfToolMode.PRINT_PREVIEW) {
                                    onPrintDocument(toolTarget.id)
                                } else {
                                    onSendToTool(toolTarget, mode)
                                }
                                toolsTargetDoc = null
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
