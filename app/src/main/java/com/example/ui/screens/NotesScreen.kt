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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConvertedPdfEntity
import com.example.model.NoteAiModelTier
import com.example.model.NoteBatchItem
import com.example.model.NoteBatchStatus
import com.example.model.NoteExtractionStyle
import com.example.model.NoteSourceKind
import com.example.ui.NoteBatchUiState
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize

private val NOTE_MIXED_MIMES = arrayOf(
    "video/*",
    "application/pdf"
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    state: NoteBatchUiState,
    vaultDocuments: List<ConvertedPdfEntity>,
    onAddFiles: (List<android.net.Uri>) -> Unit,
    onAddVaultPdf: (ConvertedPdfEntity) -> Unit,
    onSelectStyle: (NoteExtractionStyle) -> Unit,
    onSelectTier: (NoteAiModelTier) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearQueue: () -> Unit,
    onStartExtraction: () -> Unit,
    onCancelExtraction: () -> Unit,
    onExportNotePdf: (String) -> Unit,
    onExportAllNotesPdf: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showVaultPdfPicker by remember { mutableStateOf(false) }

    val mixedMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) onAddFiles(uris)
    }

    val pendingCount = remember(state.items) {
        state.items.count { it.status != NoteBatchStatus.COMPLETED }
    }
    val exportableCount = remember(state.items) {
        state.items.count { it.status == NoteBatchStatus.COMPLETED && it.exportedPdfId == null }
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
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Batch AI Notes",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    StatusPill(
                                        text = if (state.modelTier.isPaidTier) "PRO TIER" else "GEMINI AI",
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        icon = if (state.modelTier.isPaidTier) Icons.Filled.WorkspacePremium else null
                                    )
                                }
                                Text(
                                    text = "Batch extract structured notes, takeaways, and timestamps/pages from multiple videos and PDFs.",
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
                                onClick = { mixedMediaPicker.launch(NOTE_MIXED_MIMES) },
                                enabled = !state.isExtracting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("notes_select_mixed_btn")
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Videos & PDFs")
                            }
                            FilledTonalButton(
                                onClick = {
                                    videoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                enabled = !state.isExtracting,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("notes_pick_videos_btn")
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pick Videos")
                            }
                        }

                        if (vaultDocuments.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showVaultPdfPicker = true },
                                enabled = !state.isExtracting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("notes_pick_vault_pdf_btn")
                            ) {
                                Icon(Icons.Filled.FolderCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Add PDF from Vault (${vaultDocuments.size})")
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Note Format",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NoteExtractionStyle.entries.forEach { style ->
                                    FilterChip(
                                        selected = state.style == style,
                                        onClick = { onSelectStyle(style) },
                                        enabled = !state.isExtracting,
                                        label = { Text(style.label) }
                                    )
                                }
                            }
                        }

                        Column {
                            Text(
                                text = "Extraction Engine",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NoteAiModelTier.entries.forEach { tier ->
                                    FilterChip(
                                        selected = state.modelTier == tier,
                                        onClick = { onSelectTier(tier) },
                                        enabled = !state.isExtracting,
                                        leadingIcon = if (tier.isPaidTier) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.WorkspacePremium,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        label = { Text(tier.label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.isExtracting) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Extracting notes (${state.completedCount}/${state.totalCount})...",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                TextButton(onClick = onCancelExtraction) {
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

            if (state.lastSummary != null && !state.isExtracting) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = state.lastSummary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            if (exportableCount > 0) {
                                FilledTonalButton(onClick = onExportAllNotesPdf) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save All PDFs")
                                }
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
                            text = "Note Extraction Queue (${state.items.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = onClearQueue,
                            enabled = !state.isExtracting
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    NoteBatchItemCard(
                        item = item,
                        isBatchRunning = state.isExtracting,
                        onRemove = { onRemoveItem(item.id) },
                        onExportPdf = { onExportNotePdf(item.id) },
                        onOpenPdf = { item.exportedPdfId?.let(onOpenViewer) }
                    )
                }

                item {
                    Button(
                        onClick = onStartExtraction,
                        enabled = !state.isExtracting && pendingCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("notes_extract_all_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (pendingCount > 0) {
                                "Extract Notes for $pendingCount File${if (pendingCount == 1) "" else "s"}"
                            } else {
                                "All Queued Files Extracted"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showVaultPdfPicker) {
        ModalBottomSheet(onDismissRequest = { showVaultPdfPicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Vault PDFs", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Tap any PDF from your Vault to add it to the Note Extraction queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                vaultDocuments.take(15).forEach { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAddVaultPdf(doc)
                                showVaultPdfPicker = false
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${doc.pageCount}p · ${formatFileSize(doc.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteBatchItemCard(
    item: NoteBatchItem,
    isBatchRunning: Boolean,
    onRemove: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenPdf: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(item.status) { mutableStateOf(item.status == NoteBatchStatus.COMPLETED) }
    var copied by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.kind == NoteSourceKind.VIDEO) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.kind == NoteSourceKind.VIDEO) {
                            Icons.Filled.VideoLibrary
                        } else {
                            Icons.Filled.PictureAsPdf
                        },
                        contentDescription = item.displayName,
                        tint = if (item.kind == NoteSourceKind.VIDEO) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPill(
                            text = item.kind.badge,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.sizeBytes > 0L) {
                            Text(
                                text = formatFileSize(item.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when (item.status) {
                    NoteBatchStatus.ANALYZING -> {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    }
                    NoteBatchStatus.COMPLETED -> {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "Collapse notes" else "Expand notes"
                            )
                        }
                    }
                    NoteBatchStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    NoteBatchStatus.QUEUED -> {
                        IconButton(
                            onClick = onRemove,
                            enabled = !isBatchRunning
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove")
                        }
                    }
                }
            }

            if (item.status == NoteBatchStatus.FAILED && !item.errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = item.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else if (item.status != NoteBatchStatus.COMPLETED) {
                Text(
                    text = item.progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val noteResult = item.result
            AnimatedVisibility(visible = expanded && noteResult != null) {
                if (noteResult != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = noteResult.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = noteResult.summary,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (noteResult.keyTakeaways.isNotEmpty()) {
                                    Text(
                                        text = "Key Takeaways",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    noteResult.keyTakeaways.forEach { takeaway ->
                                        Text(
                                            text = "• $takeaway",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                noteResult.sections.forEach { sec ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = sec.heading,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (sec.reference.isNotBlank()) {
                                                    StatusPill(
                                                        text = sec.reference,
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                            sec.bullets.forEach { b ->
                                                Text(
                                                    text = "• $b",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }

                                if (noteResult.actionItems.isNotEmpty()) {
                                    Text(
                                        text = "Action Items",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    noteResult.actionItems.forEach { action ->
                                        Text(
                                            text = "☐ $action",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(noteResult.toMarkdown()))
                                    copied = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (copied) "Copied" else "Copy")
                            }

                            if (item.exportedPdfId != null) {
                                FilledTonalButton(
                                    onClick = onOpenPdf,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("View PDF")
                                }
                            } else {
                                Button(
                                    onClick = onExportPdf,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save as PDF")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
