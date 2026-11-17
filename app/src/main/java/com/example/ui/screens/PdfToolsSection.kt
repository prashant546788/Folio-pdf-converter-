package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.ConvertedPdfEntity
import com.example.data.pdf.ImageExportFormat
import com.example.data.pdf.PdfCompressionLevel
import com.example.model.PdfMetadataConfig
import com.example.model.PdfSecurityConfig
import com.example.model.PdfTranslationResult
import com.example.model.PdfTranslationTone
import com.example.model.PdfWatermarkConfig
import com.example.model.PrintOrientationMode
import com.example.model.PrintPagesPerSheet
import com.example.model.PrintPaperSize
import com.example.model.TranslationLanguageCatalog
import com.example.ui.PdfToolMode
import com.example.ui.PdfToolsUiState
import com.example.ui.SelectedPdfSource
import com.example.ui.components.PdfMetadataSettingsCard
import com.example.ui.components.PdfSecuritySettingsCard
import com.example.ui.components.PdfWatermarkSettingsCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.shareImageFile
import com.example.ui.components.sharePdfFile
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsSection(
    toolsState: PdfToolsUiState,
    security: PdfSecurityConfig,
    metadata: PdfMetadataConfig,
    watermark: PdfWatermarkConfig,
    vaultDocuments: List<ConvertedPdfEntity>,
    onPickSingleUri: (Uri) -> Unit,
    onPickSingleVaultDoc: (ConvertedPdfEntity) -> Unit,
    onAddMergeUris: (List<Uri>) -> Unit,
    onAddMergeVaultDoc: (ConvertedPdfEntity) -> Unit,
    onMoveMergeUp: (Int) -> Unit,
    onMoveMergeDown: (Int) -> Unit,
    onRemoveMergeItem: (String) -> Unit,
    onClearMergeList: () -> Unit,
    onMoveMergePageUp: (Int) -> Unit,
    onMoveMergePageDown: (Int) -> Unit,
    onRemoveMergePageItem: (String) -> Unit,
    onMergeOutputNameChange: (String) -> Unit,
    onImageFormatChange: (ImageExportFormat) -> Unit,
    onImageDpiChange: (Int) -> Unit,
    onPageRangeChange: (String) -> Unit,
    onSplitEachPageChange: (Boolean) -> Unit,
    onCompressionLevelChange: (PdfCompressionLevel) -> Unit,
    onRotationDegreesChange: (Int) -> Unit,
    onExistingPdfPasswordChange: (String) -> Unit,
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
    onOpenSettings: () -> Unit,
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
    onRequestCloudUpload: (ConvertedPdfEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showVaultPickerForMerge by remember { mutableStateOf<Boolean?>(null) }
    var showExistingPwd by remember { mutableStateOf(false) }
    // null = closed, true = picking source language, false = picking target language
    var languagePickerIsSource by remember { mutableStateOf<Boolean?>(null) }

    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickSingleUri(uri)
    }

    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onAddMergeUris(uris)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tool Header & Source Selector
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val toolIcon = when (toolsState.activeTool) {
                        PdfToolMode.TRANSLATE_PDF -> Icons.Filled.Translate
                        PdfToolMode.FOUR_IN_ONE -> Icons.Filled.GridView
                        PdfToolMode.QUICK_TEXT -> Icons.Filled.EditNote
                        PdfToolMode.WATERMARK_PDF -> Icons.Filled.Approval
                        PdfToolMode.EDIT_METADATA -> Icons.Filled.Badge
                        PdfToolMode.PDF_TO_IMAGES -> Icons.Filled.Image
                        PdfToolMode.MERGE_PDFS -> Icons.AutoMirrored.Filled.MergeType
                        PdfToolMode.SPLIT_PDF -> Icons.AutoMirrored.Filled.CallSplit
                        PdfToolMode.COMPRESS_PDF -> Icons.Filled.Compress
                        PdfToolMode.ROTATE_PDF -> Icons.AutoMirrored.Filled.RotateRight
                        PdfToolMode.PROTECT_PDF -> Icons.Filled.Lock
                        PdfToolMode.PRINT_PREVIEW -> Icons.Filled.Print
                        PdfToolMode.CREATE_PDF -> Icons.Filled.PictureAsPdf
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = toolIcon,
                            contentDescription = toolsState.activeTool.label,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = toolsState.activeTool.label,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = toolsState.activeTool.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (toolsState.activeTool == PdfToolMode.QUICK_TEXT) {
                    Text(
                        text = "No file needed! Type or paste any notes below and Folio PDF automatically wraps pages with your Fonts & Colors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (toolsState.activeTool == PdfToolMode.MERGE_PDFS) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tool_pick_merge_device_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Device PDFs")
                        }
                        FilledTonalButton(
                            onClick = { showVaultPickerForMerge = true },
                            enabled = !toolsState.isProcessing && vaultDocuments.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tool_pick_merge_vault_btn")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("From Vault")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { singlePdfPicker.launch(arrayOf("application/pdf")) },
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tool_pick_single_device_btn")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (toolsState.singleSource == null) "Select PDF" else "Change PDF")
                        }
                        FilledTonalButton(
                            onClick = { showVaultPickerForMerge = false },
                            enabled = !toolsState.isProcessing && vaultDocuments.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tool_pick_single_vault_btn")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("From Vault")
                        }
                    }

                    val selected = toolsState.singleSource
                    if (selected != null) {
                        SelectedPdfSourceCard(source = selected)
                    } else {
                        Text(
                            text = "Choose any PDF from your device or from your Folio Vault to begin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Tool-specific controls
        when (toolsState.activeTool) {
            PdfToolMode.FOUR_IN_ONE -> {
                val nUpOptions = listOf(
                    PrintPagesPerSheet.FOUR,
                    PrintPagesPerSheet.TWO,
                    PrintPagesPerSheet.SIX,
                    PrintPagesPerSheet.NINE
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Pages Per 1 Sheet", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            nUpOptions.forEach { preset ->
                                FilterChip(
                                    selected = toolsState.nUpPagesPerSheet == preset,
                                    onClick = { onNUpPagesPerSheetChange(preset) },
                                    label = {
                                        val label = if (preset == PrintPagesPerSheet.FOUR) "4 in 1 (2×2)" else "${preset.count} in 1"
                                        Text(label)
                                    },
                                    modifier = Modifier.testTag("nup_chip_${preset.count}")
                                )
                            }
                        }

                        // Beginner visual grid preview
                        NUpMiniGridPreview(pagesPerSheet = toolsState.nUpPagesPerSheet.count)

                        Text("Sheet Size", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                PrintPaperSize.A4,
                                PrintPaperSize.LETTER,
                                PrintPaperSize.LEGAL,
                                PrintPaperSize.A5
                            ).forEach { paper ->
                                FilterChip(
                                    selected = toolsState.nUpPaperSize == paper,
                                    onClick = { onNUpPaperSizeChange(paper) },
                                    label = { Text(paper.label) }
                                )
                            }
                        }

                        Text("Orientation", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrintOrientationMode.entries.forEach { orient ->
                                FilterChip(
                                    selected = toolsState.nUpOrientation == orient,
                                    onClick = { onNUpOrientationChange(orient) },
                                    label = { Text(orient.label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = toolsState.pageRangeExpr,
                            onValueChange = onPageRangeChange,
                            label = { Text("Source Pages (optional)") },
                            placeholder = { Text("all or 1-8, 12") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Thin Cell Borders", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Draw light divider boxes around each mini page",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = toolsState.nUpShowBorders,
                                onCheckedChange = onNUpShowBordersChange
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Black & White (Ink-Saver)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Great for printing 4-in-1 lecture handouts cleanly",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = toolsState.nUpGrayscale,
                                onCheckedChange = onNUpGrayscaleChange
                            )
                        }

                        OutlinedTextField(
                            value = toolsState.existingPdfPassword,
                            onValueChange = onExistingPdfPasswordChange,
                            label = { Text("Source PDF Password (only if locked)") },
                            singleLine = true,
                            visualTransformation = if (showExistingPwd) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showExistingPwd = !showExistingPwd }) {
                                    Icon(
                                        imageVector = if (showExistingPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showExistingPwd) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = toolsState.nUpOutputName,
                            onValueChange = onNUpOutputNameChange,
                            label = { Text("Output PDF Filename") },
                            suffix = { Text(".pdf") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_nup_output_input")
                        )

                        PdfWatermarkSettingsCard(
                            watermark = watermark,
                            onUpdate = onUpdateWatermark,
                            subtitle = "Optionally stamp text or logo copyright watermark on combined sheets"
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.nUpOutputName
                        )

                        PdfSecuritySettingsCard(
                            security = security,
                            onUpdate = onUpdateSecurity,
                            subtitle = "Optionally lock the combined PDF"
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunFourInOne,
                            enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_four_in_one")
                        ) {
                            Icon(Icons.Filled.GridView, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Convert ${toolsState.nUpPagesPerSheet.count} Pages → 1 Page PDF")
                        }
                    }
                }
            }

            PdfToolMode.QUICK_TEXT -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Beginner Starter Templates", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onApplyQuickTextTemplate(
                                        "Study_Notes",
                                        "# Chapter Summary\n\n## Key Concepts\n- Main idea #1 explained clearly in plain words\n- Important formula or definition to remember\n\n> Tip: Review flashcards for 10 minutes before class."
                                    )
                                },
                                label = { Text("Study Notes") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onApplyQuickTextTemplate(
                                        "Meeting_Summary",
                                        "# Team Meeting Notes\n\n## Attendees\n- Product, Design, Engineering\n\n## Decisions & Action Items\n- Finalize release checklist by Friday\n- Share PDF deck with stakeholders"
                                    )
                                },
                                label = { Text("Meeting Notes") }
                            )
                            FilterChip(
                                selected = false,
                                onClick = {
                                    onApplyQuickTextTemplate(
                                        "Checklist",
                                        "# Weekly Checklist\n\n- [ ] Review project milestones\n- [ ] Backup converted PDFs to Google Drive\n- [ ] Print 4-in-1 handout for study group"
                                    )
                                },
                                label = { Text("Checklist") }
                            )
                        }

                        OutlinedTextField(
                            value = toolsState.quickTextTitle,
                            onValueChange = onQuickTextTitleChange,
                            label = { Text("Document Title & Filename") },
                            suffix = { Text(".pdf") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_text_title_input")
                        )

                        OutlinedTextField(
                            value = toolsState.quickTextBody,
                            onValueChange = onQuickTextBodyChange,
                            label = { Text("Write or Paste Text / Markdown") },
                            placeholder = { Text("# My Title\n\nStart typing paragraphs, - bullet points, or > quotes...") },
                            minLines = 6,
                            maxLines = 14,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_text_body_input")
                        )

                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Customize PDF Font, Text Color & Page Theme")
                        }

                        PdfWatermarkSettingsCard(
                            watermark = watermark,
                            onUpdate = onUpdateWatermark,
                            subtitle = "Optionally stamp text or logo watermark on every page"
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.quickTextTitle
                        )

                        PdfSecuritySettingsCard(
                            security = security,
                            onUpdate = onUpdateSecurity,
                            subtitle = "Optionally protect your document with a password"
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunQuickTextToPdf,
                            enabled = !toolsState.isProcessing && toolsState.quickTextBody.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_quick_text")
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Styled PDF")
                        }
                    }
                }
            }

            PdfToolMode.WATERMARK_PDF -> {
                PdfWatermarkSettingsCard(
                    watermark = watermark,
                    onUpdate = onUpdateWatermark,
                    title = "Watermark Text or Image Stamp",
                    subtitle = "Choose text copyright stamp or transparent PNG/JPG logo",
                    alwaysExpanded = true,
                    showPageRange = true,
                    pageCountHint = toolsState.singleSource?.pageCount ?: 0
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = toolsState.existingPdfPassword,
                            onValueChange = onExistingPdfPasswordChange,
                            label = { Text("Source PDF Password (only if locked)") },
                            placeholder = { Text("Leave blank if PDF is unlocked") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            visualTransformation = if (showExistingPwd) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showExistingPwd = !showExistingPwd }) {
                                    Icon(
                                        imageVector = if (showExistingPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showExistingPwd) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_watermark_existing_pwd_input")
                        )

                        OutlinedTextField(
                            value = toolsState.watermarkOutputName,
                            onValueChange = onWatermarkOutputNameChange,
                            label = { Text("Output PDF Filename") },
                            suffix = { Text(".pdf") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_watermark_output_name_input")
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.watermarkOutputName
                        )

                        PdfSecuritySettingsCard(
                            security = security,
                            onUpdate = onUpdateSecurity,
                            subtitle = "Optionally lock the watermarked PDF with a password & permissions"
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunWatermarkPdf,
                            enabled = toolsState.singleSource != null &&
                                watermark.isReadyToApply() &&
                                !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_watermark_pdf")
                        ) {
                            Icon(Icons.Filled.Approval, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Apply Watermark & Save PDF")
                        }
                    }
                }
            }

            PdfToolMode.EDIT_METADATA -> {
                if (toolsState.metadataNeedsPassword) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Unlock Protected PDF to Inspect Metadata", style = MaterialTheme.typography.titleSmall)
                            }
                            OutlinedTextField(
                                value = toolsState.existingPdfPassword,
                                onValueChange = onExistingPdfPasswordChange,
                                label = { Text("Current PDF Password") },
                                isError = toolsState.metadataPasswordError != null,
                                supportingText = toolsState.metadataPasswordError?.let { err ->
                                    { Text(err, color = MaterialTheme.colorScheme.error) }
                                },
                                singleLine = true,
                                visualTransformation = if (showExistingPwd) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showExistingPwd = !showExistingPwd }) {
                                        Icon(
                                            imageVector = if (showExistingPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showExistingPwd) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tool_meta_unlock_input")
                            )
                            Button(
                                onClick = onUnlockMetadata,
                                enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("tool_meta_unlock_btn")
                            ) {
                                Text("Unlock & Read Existing Metadata")
                            }
                        }
                    }
                } else {
                    if (toolsState.metadataProducerInfo.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(
                                text = "Producer: ${toolsState.metadataProducerInfo}",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    PdfMetadataSettingsCard(
                        metadata = metadata,
                        onUpdate = onUpdateMetadata,
                        title = "Edit Document Metadata",
                        subtitle = "Set Title, Author, Subject, Keywords & Creator before saving",
                        suggestedTitle = toolsState.singleSource?.displayName.orEmpty(),
                        alwaysExpanded = true
                    )

                    PdfWatermarkSettingsCard(
                        watermark = watermark,
                        onUpdate = onUpdateWatermark,
                        subtitle = "Optionally add a copyright watermark while updating metadata"
                    )

                    PdfSecuritySettingsCard(
                        security = security,
                        onUpdate = onUpdateSecurity,
                        subtitle = "Optionally apply password & permissions while saving metadata"
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = toolsState.metadataOutputName,
                                onValueChange = onMetadataOutputNameChange,
                                label = { Text("Output PDF Filename") },
                                suffix = { Text(".pdf") },
                                singleLine = true,
                                enabled = !toolsState.isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tool_meta_output_name_input")
                            )

                            ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                            Button(
                                onClick = onRunSaveMetadata,
                                enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("tool_run_save_metadata")
                            ) {
                                Icon(Icons.Filled.Badge, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Finalize & Save PDF with Metadata")
                            }
                        }
                    }
                }
            }

            PdfToolMode.PDF_TO_IMAGES -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Image Format", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageExportFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = toolsState.imageFormat == fmt,
                                    onClick = { onImageFormatChange(fmt) },
                                    label = { Text(fmt.label) }
                                )
                            }
                        }

                        Text("Resolution (DPI)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(150, 220, 300).forEach { dpi ->
                                FilterChip(
                                    selected = toolsState.imageDpi == dpi,
                                    onClick = { onImageDpiChange(dpi) },
                                    label = { Text("$dpi DPI") }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = toolsState.pageRangeExpr,
                            onValueChange = onPageRangeChange,
                            label = { Text("Pages to Export") },
                            placeholder = { Text("all or 1-3, 5") },
                            supportingText = {
                                val max = toolsState.singleSource?.pageCount ?: 0
                                Text(if (max > 0) "Enter \"all\" or page numbers between 1 and $max (e.g. 1-3, 5)" else "Enter \"all\" or ranges like 1-3, 5")
                            },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_page_range_input")
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunPdfToImages,
                            enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_pdf_to_images")
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export PDF Pages as ${toolsState.imageFormat.extension.uppercase()}")
                        }
                    }
                }
            }

            PdfToolMode.MERGE_PDFS -> {
                if (toolsState.mergeSources.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PDFs to Merge (${toolsState.mergeSources.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onClearMergeList, enabled = !toolsState.isProcessing) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    toolsState.mergeSources.forEachIndexed { index, src ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = src.displayName,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = src.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${src.pageCount} page(s) · ${formatFileSize(src.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (toolsState.mergeSources.size > 1) {
                                    IconButton(
                                        onClick = { onMoveMergeUp(index) },
                                        enabled = !toolsState.isProcessing && index > 0
                                    ) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                                    }
                                    IconButton(
                                        onClick = { onMoveMergeDown(index) },
                                        enabled = !toolsState.isProcessing && index < toolsState.mergeSources.lastIndex
                                    ) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveMergeItem(src.id) },
                                    enabled = !toolsState.isProcessing
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove PDF")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Page Reorder Queue (${toolsState.mergePages.size} pages)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Rearrange or remove individual pages before merging:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    toolsState.mergePages.forEachIndexed { pIdx, pageItem ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${pIdx + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pageItem.sourceDisplayName} (Page ${pageItem.pageNumber1Based})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveMergePageUp(pIdx) },
                                    enabled = !toolsState.isProcessing && pIdx > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move page up", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onMoveMergePageDown(pIdx) },
                                    enabled = !toolsState.isProcessing && pIdx < toolsState.mergePages.lastIndex,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move page down", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { onRemoveMergePageItem(pageItem.id) },
                                    enabled = !toolsState.isProcessing && toolsState.mergePages.size > 1,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove page", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = toolsState.mergeOutputName,
                            onValueChange = onMergeOutputNameChange,
                            label = { Text("Merged Output Name") },
                            suffix = { Text(".pdf") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_merge_name_input")
                        )

                        PdfWatermarkSettingsCard(
                            watermark = watermark,
                            onUpdate = onUpdateWatermark,
                            subtitle = "Optionally stamp a watermark across merged pages"
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.mergeOutputName
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunMergePdfs,
                            enabled = toolsState.mergePages.isNotEmpty() && !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_merge_pdfs")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (toolsState.mergePages.isEmpty()) "Add PDFs to Merge"
                                else "Merge ${toolsState.mergePages.size} Pages into 1 PDF"
                            )
                        }
                    }
                }
            }

            PdfToolMode.SPLIT_PDF -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Split Every Page Separately", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Creates one individual PDF per page, or turn off to extract a custom range.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = toolsState.splitEachPage,
                                onCheckedChange = onSplitEachPageChange,
                                enabled = !toolsState.isProcessing,
                                modifier = Modifier.testTag("tool_split_each_switch")
                            )
                        }

                        AnimatedVisibility(visible = !toolsState.splitEachPage) {
                            OutlinedTextField(
                                value = toolsState.pageRangeExpr,
                                onValueChange = onPageRangeChange,
                                label = { Text("Pages to Extract") },
                                placeholder = { Text("1-3, 5") },
                                supportingText = { Text("Example: 1-3, 5") },
                                singleLine = true,
                                enabled = !toolsState.isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tool_split_range_input")
                            )
                        }

                        PdfWatermarkSettingsCard(
                            watermark = watermark,
                            onUpdate = onUpdateWatermark,
                            subtitle = "Optionally add a watermark to each split PDF"
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.singleSource?.displayName.orEmpty()
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunSplitPdf,
                            enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_split_pdf")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (toolsState.splitEachPage) "Split All Pages" else "Extract Selected Pages")
                        }
                    }
                }
            }

            PdfToolMode.COMPRESS_PDF -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Compression Preset", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfCompressionLevel.entries.forEach { level ->
                                FilterChip(
                                    selected = toolsState.compressionLevel == level,
                                    onClick = { onCompressionLevelChange(level) },
                                    label = { Text(level.label) }
                                )
                            }
                        }

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunCompressPdf,
                            enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_compress_pdf")
                        ) {
                            Icon(Icons.Filled.Compress, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Compress & Save PDF")
                        }
                    }
                }
            }

            PdfToolMode.ROTATE_PDF -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Rotation Angle (Clockwise)", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(90, 180, 270).forEach { deg ->
                                FilterChip(
                                    selected = toolsState.rotationDegrees == deg,
                                    onClick = { onRotationDegreesChange(deg) },
                                    label = { Text("${deg}°") }
                                )
                            }
                        }

                        PdfWatermarkSettingsCard(
                            watermark = watermark,
                            onUpdate = onUpdateWatermark,
                            subtitle = "Optionally add a watermark onto rotated pages"
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.singleSource?.displayName.orEmpty()
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunRotatePdf,
                            enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_rotate_pdf")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Rotate Pages ${toolsState.rotationDegrees}° & Save")
                        }
                    }
                }
            }

            PdfToolMode.PROTECT_PDF -> {
                PdfSecuritySettingsCard(
                    security = security,
                    onUpdate = onUpdateSecurity,
                    title = "Encryption & Permissions Policy",
                    subtitle = "Toggle on to set an open password and restrict printing or copying"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = toolsState.existingPdfPassword,
                            onValueChange = onExistingPdfPasswordChange,
                            label = { Text("Current PDF Password (if already locked)") },
                            placeholder = { Text("Leave blank if PDF is unlocked") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            visualTransformation = if (showExistingPwd) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showExistingPwd = !showExistingPwd }) {
                                    Icon(
                                        imageVector = if (showExistingPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showExistingPwd) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tool_existing_password_input")
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.singleSource?.displayName.orEmpty()
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunProtectPdf,
                            enabled = toolsState.singleSource != null &&
                                security.hasAnyRestrictionOrPassword() &&
                                !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_protect_pdf")
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Encrypt & Save Protected PDF")
                        }
                    }
                }
            }

            PdfToolMode.PRINT_PREVIEW -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Included Printing PDF Tools",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Configure live print sheet preview with Booklet imposition, N-Up (2-up & 4-up), Grayscale Ink-Saver, Odd/Even Duplex & Custom Range, Paper Size (A4 / Letter / Legal / A5 / 4×6), Margins, Scaling, Page Borders, Header & Footer, Collated Copies, System Print, or Export Print-Ready PDF.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Booklet",
                                "N-Up 2/4-up",
                                "Edge-to-Edge",
                                "Ink-Saver Gray",
                                "Odd/Even Duplex",
                                "Margins & Scale",
                                "Header & Footer",
                                "System Print"
                            ).forEach { badge ->
                                StatusPill(
                                    text = badge,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Button(
                            onClick = onOpenPrintPreviewForTool,
                            enabled = toolsState.singleSource != null && !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_open_print_preview")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Print Preview & Tools")
                        }
                    }
                }
            }

            PdfToolMode.TRANSLATE_PDF -> {
                val quickTargetPresets = remember {
                    listOf(
                        "English",
                        "Spanish",
                        "French",
                        "German",
                        "Arabic",
                        "Hindi",
                        "Chinese (Simplified)",
                        "Japanese",
                        "Korean",
                        "Portuguese",
                        "Russian",
                        "Bengali"
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From Language", style = MaterialTheme.typography.titleSmall)
                            TextButton(
                                onClick = { languagePickerIsSource = true },
                                enabled = !toolsState.isProcessing,
                                modifier = Modifier.testTag("translate_browse_source_btn")
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Browse All")
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = toolsState.translationSourceLanguage.equals(
                                    TranslationLanguageCatalog.AUTO_DETECT,
                                    ignoreCase = true
                                ),
                                onClick = { onTranslationSourceLanguageChange(TranslationLanguageCatalog.AUTO_DETECT) },
                                enabled = !toolsState.isProcessing,
                                label = { Text("Auto-Detect") },
                                modifier = Modifier.testTag("translate_source_auto")
                            )
                            listOf("English", "Spanish", "French", "Arabic", "Hindi", "Japanese", "Chinese (Simplified)").forEach { lang ->
                                FilterChip(
                                    selected = toolsState.translationSourceLanguage.equals(lang, ignoreCase = true),
                                    onClick = { onTranslationSourceLanguageChange(lang) },
                                    enabled = !toolsState.isProcessing,
                                    label = { Text(lang) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = toolsState.translationSourceLanguage,
                            onValueChange = onTranslationSourceLanguageChange,
                            label = { Text("Source Language (or type ANY language)") },
                            placeholder = { Text("Auto-Detect, Korean, Urdu, Swahili...") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("translate_source_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilledTonalButton(
                                onClick = onSwapTranslationLanguages,
                                enabled = !toolsState.isProcessing &&
                                    !toolsState.translationSourceLanguage.equals(
                                        TranslationLanguageCatalog.AUTO_DETECT,
                                        ignoreCase = true
                                    ),
                                modifier = Modifier
                                    .height(42.dp)
                                    .testTag("translate_swap_btn")
                            ) {
                                Icon(Icons.Filled.SwapVert, contentDescription = "Swap languages", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Swap Languages")
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("To Target Language", style = MaterialTheme.typography.titleSmall)
                            TextButton(
                                onClick = { languagePickerIsSource = false },
                                enabled = !toolsState.isProcessing,
                                modifier = Modifier.testTag("translate_browse_target_btn")
                            ) {
                                Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Browse All")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickTargetPresets.forEach { lang ->
                                FilterChip(
                                    selected = toolsState.translationTargetLanguage.equals(lang, ignoreCase = true),
                                    onClick = { onTranslationTargetLanguageChange(lang) },
                                    enabled = !toolsState.isProcessing,
                                    label = { Text(lang) },
                                    modifier = Modifier.testTag("translate_target_${lang.lowercase().replace(" ", "_")}")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = toolsState.translationTargetLanguage,
                            onValueChange = onTranslationTargetLanguageChange,
                            label = { Text("Target Language (type ANY language in the world)") },
                            placeholder = { Text("Spanish, Arabic, Tamil, Swahili, French...") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("translate_target_input")
                        )

                        Text("Translation Style", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PdfTranslationTone.entries.forEach { tone ->
                                FilterChip(
                                    selected = toolsState.translationTone == tone,
                                    onClick = { onTranslationToneChange(tone) },
                                    enabled = !toolsState.isProcessing,
                                    label = { Text(tone.label) },
                                    modifier = Modifier.testTag("translate_tone_${tone.name.lowercase()}")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = toolsState.pageRangeExpr,
                            onValueChange = onPageRangeChange,
                            label = { Text("Pages to Translate") },
                            placeholder = { Text("all or 1-5, 8") },
                            supportingText = {
                                val max = toolsState.singleSource?.pageCount ?: 0
                                Text(
                                    if (max > 0) "Works on text & scanned PDFs · Enter \"all\" or 1–$max"
                                    else "Works on text & scanned image PDFs · Enter \"all\" or e.g. 1-5"
                                )
                            },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("translate_page_range_input")
                        )

                        OutlinedTextField(
                            value = toolsState.existingPdfPassword,
                            onValueChange = onExistingPdfPasswordChange,
                            label = { Text("Source PDF Password (only if locked)") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            visualTransformation = if (showExistingPwd) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showExistingPwd = !showExistingPwd }) {
                                    Icon(
                                        imageVector = if (showExistingPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (showExistingPwd) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = toolsState.translationOutputName,
                            onValueChange = onTranslationOutputNameChange,
                            label = { Text("Translated Output Filename") },
                            suffix = { Text(".pdf") },
                            singleLine = true,
                            enabled = !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("translate_output_name_input")
                        )

                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Customize PDF Font, Text Color & Page Theme")
                        }

                        PdfWatermarkSettingsCard(
                            watermark = watermark,
                            onUpdate = onUpdateWatermark,
                            subtitle = "Optionally stamp a copyright watermark onto translated PDF pages"
                        )

                        PdfMetadataSettingsCard(
                            metadata = metadata,
                            onUpdate = onUpdateMetadata,
                            suggestedTitle = toolsState.translationOutputName
                        )

                        PdfSecuritySettingsCard(
                            security = security,
                            onUpdate = onUpdateSecurity,
                            subtitle = "Optionally lock the translated PDF with a password & permissions"
                        )

                        ToolProgressBlock(toolsState.isProcessing, toolsState.progressMessage)

                        Button(
                            onClick = onRunTranslatePdf,
                            enabled = toolsState.singleSource != null &&
                                toolsState.translationTargetLanguage.isNotBlank() &&
                                !toolsState.isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("tool_run_translate_pdf")
                        ) {
                            Icon(Icons.Filled.Translate, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Translate PDF → ${toolsState.translationTargetLanguage.trim().ifBlank { "Target" }}"
                            )
                        }
                    }
                }
            }

            PdfToolMode.CREATE_PDF -> Unit
        }

        // Translated Text Preview Card
        val translationResult = toolsState.lastTranslationResult
        if (toolsState.activeTool == PdfToolMode.TRANSLATE_PDF && translationResult != null) {
            TranslatedDocumentPreviewCard(
                result = translationResult,
                onCopyAll = {
                    clipboard.setText(AnnotatedString(translationResult.toPlainText()))
                }
            )
        }

        // Exported Images Preview Card
        if (toolsState.exportedImages.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tool_exported_images_card"),
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
                        Column {
                            Text(
                                text = "Exported ${toolsState.exportedImages.size} Image(s)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = toolsState.exportedImages.first().savedLocationLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(toolsState.exportedImages, key = { it.cachePath }) { img ->
                            Card(
                                modifier = Modifier.width(156.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AsyncImage(
                                        model = File(img.cachePath),
                                        contentDescription = img.fileName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Text(
                                        text = "Page ${img.pageNumber} · ${formatFileSize(img.sizeBytes)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            shareImageFile(
                                                context = context,
                                                filePath = img.cachePath,
                                                fileName = img.fileName,
                                                mimeType = toolsState.imageFormat.mimeType
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Resulting PDFs Card (Merge / Split / Compress / Rotate)
        if (toolsState.lastToolPdfResults.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tool_pdf_results_card"),
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
                        Column {
                            Text(
                                text = if (toolsState.lastToolPdfResults.size == 1) {
                                    toolsState.lastToolPdfResults.first().fileName
                                } else {
                                    "Saved ${toolsState.lastToolPdfResults.size} PDFs to Vault"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = toolsState.compressionSummary ?: run {
                                val first = toolsState.lastToolPdfResults.first()
                                if (toolsState.lastToolPdfResults.size == 1) {
                                    "${first.pageCount} page(s) · ${formatFileSize(first.fileSizeBytes)}"
                                } else {
                                    "Tap any generated PDF below to view or export"
                                }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    toolsState.lastToolPdfResults.forEach { doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = doc.fileName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${doc.pageCount}p · ${formatFileSize(doc.fileSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onOpenViewer(doc.id) }) {
                                Icon(Icons.Filled.Visibility, contentDescription = "View PDF")
                            }
                            IconButton(onClick = { onPrintResultDoc(doc.id) }) {
                                Icon(Icons.Filled.Print, contentDescription = "Print Preview")
                            }
                            IconButton(onClick = { onRequestCloudUpload(doc) }) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = "Upload to Cloud")
                            }
                            IconButton(onClick = { onSaveToDownloads(doc) }) {
                                Icon(Icons.Filled.Download, contentDescription = "Save to Downloads")
                            }
                            IconButton(onClick = { sharePdfFile(context, doc.filePath, doc.fileName) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                            }
                        }
                    }
                }
            }
        }
    }

    val pickerMode = showVaultPickerForMerge
    if (pickerMode != null) {
        ModalBottomSheet(onDismissRequest = { showVaultPickerForMerge = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (pickerMode) "Add Vault PDF to Merge" else "Select PDF from Vault",
                    style = MaterialTheme.typography.titleLarge
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vaultDocuments, key = { it.id }) { doc ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (pickerMode) onAddMergeVaultDoc(doc)
                                    else onPickSingleVaultDoc(doc)
                                    showVaultPickerForMerge = null
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = doc.fileName,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
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

    val isSourceLang = languagePickerIsSource
    if (isSourceLang != null) {
        var langSearch by remember(isSourceLang) { mutableStateOf("") }
        val filteredLangs = remember(isSourceLang, langSearch) {
            val allOptions = if (isSourceLang) {
                listOf(TranslationLanguageCatalog.AUTO_DETECT) + TranslationLanguageCatalog.popularLanguages
            } else {
                TranslationLanguageCatalog.popularLanguages
            }
            val q = langSearch.trim().lowercase()
            if (q.isBlank()) allOptions else allOptions.filter { it.lowercase().contains(q) }
        }
        ModalBottomSheet(onDismissRequest = { languagePickerIsSource = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isSourceLang) "Select Source Language" else "Select Target Language",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = langSearch,
                    onValueChange = { langSearch = it },
                    label = { Text("Search or type any custom language") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (langSearch.trim().isNotBlank() && filteredLangs.none { it.equals(langSearch.trim(), ignoreCase = true) }) {
                    Button(
                        onClick = {
                            val custom = langSearch.trim()
                            if (isSourceLang) onTranslationSourceLanguageChange(custom)
                            else onTranslationTargetLanguageChange(custom)
                            languagePickerIsSource = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use \"${langSearch.trim()}\"")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLangs) { lang ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSourceLang) onTranslationSourceLanguageChange(lang)
                                    else onTranslationTargetLanguageChange(lang)
                                    languagePickerIsSource = null
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lang, style = MaterialTheme.typography.bodyLarge)
                                Icon(
                                    imageVector = Icons.Filled.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
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
private fun TranslatedDocumentPreviewCard(
    result: PdfTranslationResult,
    onCopyAll: () -> Unit
) {
    var copiedJustNow by remember(result) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tool_translation_preview_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.translatedTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${result.detectedSourceLanguage} → ${result.targetLanguage} · ${result.tone.label} · ${result.pages.size} page(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = {
                        onCopyAll()
                        copiedJustNow = true
                    },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copiedJustNow) "Copied" else "Copy Text")
                }
            }

            result.pages.forEach { page ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (page.heading.isNotBlank()) "Page ${page.pageNumber} · ${page.heading}" else "Page ${page.pageNumber}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = page.translatedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPdfSourceCard(source: SelectedPdfSource) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = source.displayName,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${source.pageCount} page(s) · ${formatFileSize(source.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusPill(
            text = "${source.pageCount}p",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ToolProgressBlock(isProcessing: Boolean, message: String) {
    AnimatedVisibility(visible = isProcessing) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = message.ifBlank { "Processing PDF..." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NUpMiniGridPreview(pagesPerSheet: Int) {
    val (cols, rows) = when (pagesPerSheet) {
        2 -> 2 to 1
        6 -> 3 to 2
        9 -> 3 to 3
        else -> 2 to 2
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Sheet Preview: $pagesPerSheet pages arranged on 1 sheet (${cols}×${rows})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.width(if (cols > rows) 132.dp else 104.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            var pageNum = 1
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0 until cols) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (rows >= 3) 26.dp else 36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${pageNum++}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

