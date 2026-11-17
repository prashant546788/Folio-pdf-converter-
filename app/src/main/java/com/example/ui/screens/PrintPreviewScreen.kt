package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PrintColorMode
import com.example.model.PrintConfig
import com.example.model.PrintLayoutTool
import com.example.model.PrintMarginPreset
import com.example.model.PrintOrientationMode
import com.example.model.PrintPageSubset
import com.example.model.PrintPagesPerSheet
import com.example.model.PrintPaperSize
import com.example.model.PrintScaleMode
import com.example.ui.PrintPreviewUiState
import com.example.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrintPreviewScreen(
    state: PrintPreviewUiState,
    onBack: () -> Unit,
    onUpdateConfig: ((PrintConfig) -> PrintConfig) -> Unit,
    onSelectSheet: (Int) -> Unit,
    onSetZoom: (Float) -> Unit,
    onPasswordInputChange: (String) -> Unit,
    onUnlockPdf: () -> Unit,
    onPrintNow: (android.content.Context) -> Unit,
    onSavePrintPdf: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val totalSheets = state.jobSummary.sheets.size
    val currentSheet = state.jobSummary.sheets.getOrNull(state.currentSheetIndex)
    var showToolsPanel by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Print Preview",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.fileName} · ${totalSheets} sheet(s) · ${state.config.paperSize.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("print_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showToolsPanel = !showToolsPanel },
                        modifier = Modifier.testTag("print_toggle_tools_btn")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "Toggle print tools")
                    }
                    IconButton(
                        onClick = onSavePrintPdf,
                        enabled = !state.isGeneratingPrintPdf && !state.requiresPassword && state.allowPrinting && totalSheets > 0,
                        modifier = Modifier.testTag("print_save_pdf_icon_btn")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save print layout PDF")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isGeneratingPrintPdf) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = state.progressMessage.ifBlank { "Preparing print output..." },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onSavePrintPdf,
                            enabled = !state.isGeneratingPrintPdf && !state.requiresPassword && state.allowPrinting && totalSheets > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("print_save_layout_btn")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { onPrintNow(context) },
                            enabled = !state.isGeneratingPrintPdf && !state.requiresPassword && state.allowPrinting && totalSheets > 0,
                            modifier = Modifier
                                .weight(1.25f)
                                .height(48.dp)
                                .testTag("print_now_btn")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.config.copies > 1) "Print (${state.config.copies}x)" else "Print Now",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 840.dp

            if (isWideScreen) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PrintPreviewStage(
                        state = state,
                        totalSheets = totalSheets,
                        currentSheetPagesLabel = formatSheetPagesLabel(currentSheet?.pageSlots.orEmpty()),
                        onSelectSheet = onSelectSheet,
                        onSetZoom = onSetZoom,
                        onPasswordInputChange = onPasswordInputChange,
                        onUnlockPdf = onUnlockPdf,
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                    )
                    PrintToolsPanel(
                        config = state.config,
                        totalSourcePages = state.totalSourcePages,
                        totalSheets = totalSheets,
                        allowPrinting = state.allowPrinting,
                        onUpdateConfig = onUpdateConfig,
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    PrintPreviewStage(
                        state = state,
                        totalSheets = totalSheets,
                        currentSheetPagesLabel = formatSheetPagesLabel(currentSheet?.pageSlots.orEmpty()),
                        onSelectSheet = onSelectSheet,
                        onSetZoom = onSetZoom,
                        onPasswordInputChange = onPasswordInputChange,
                        onUnlockPdf = onUnlockPdf,
                        modifier = Modifier
                            .weight(if (showToolsPanel) 1f else 1.8f)
                            .fillMaxWidth()
                    )
                    AnimatedVisibility(visible = showToolsPanel) {
                        Surface(
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        ) {
                            PrintToolsPanel(
                                config = state.config,
                                totalSourcePages = state.totalSourcePages,
                                totalSheets = totalSheets,
                                allowPrinting = state.allowPrinting,
                                onUpdateConfig = onUpdateConfig,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintPreviewStage(
    state: PrintPreviewUiState,
    totalSheets: Int,
    currentSheetPagesLabel: String,
    onSelectSheet: (Int) -> Unit,
    onSetZoom: (Float) -> Unit,
    onPasswordInputChange: (String) -> Unit,
    onUnlockPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPasswordPlain by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (!state.allowPrinting) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Printing is restricted by this PDF's owner security policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Top sheet navigation & zoom strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSelectSheet(state.currentSheetIndex - 1) },
                    enabled = state.currentSheetIndex > 0 && !state.requiresPassword
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous sheet")
                }
                Column {
                    Text(
                        text = if (totalSheets > 0) "Sheet ${state.currentSheetIndex + 1} / $totalSheets" else "No Sheets",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (currentSheetPagesLabel.isNotBlank()) {
                        Text(
                            text = currentSheetPagesLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { onSelectSheet(state.currentSheetIndex + 1) },
                    enabled = state.currentSheetIndex < totalSheets - 1 && !state.requiresPassword
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next sheet")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSetZoom(state.zoomScale - 0.3f) },
                    enabled = state.zoomScale > 0.8f
                ) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out preview")
                }
                Text(
                    text = "${(state.zoomScale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
                IconButton(
                    onClick = { onSetZoom(state.zoomScale + 0.3f) },
                    enabled = state.zoomScale < 2.5f
                ) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in preview")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (state.requiresPassword) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 400.dp)
                        .padding(18.dp)
                        .testTag("print_password_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked PDF",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text("Unlock Protected PDF", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Enter the open password to render print preview and check print permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = state.passwordInput,
                            onValueChange = onPasswordInputChange,
                            label = { Text("PDF Password") },
                            isError = state.passwordError != null,
                            supportingText = state.passwordError?.let { err ->
                                { Text(err, color = MaterialTheme.colorScheme.error) }
                            },
                            singleLine = true,
                            visualTransformation = if (showPasswordPlain) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPasswordPlain = !showPasswordPlain }) {
                                    Icon(
                                        imageVector = if (showPasswordPlain) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = onUnlockPdf,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("print_unlock_btn")
                        ) {
                            Text("Unlock for Print Preview")
                        }
                    }
                }
            } else if (state.isRenderingPreview && state.previewBitmap == null) {
                CircularProgressIndicator()
            } else if (totalSheets == 0) {
                Text(
                    text = "No pages match the active page filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (state.previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shadowElevation = 10.dp,
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Image(
                            bitmap = state.previewBitmap.asImageBitmap(),
                            contentDescription = "Print preview sheet ${state.currentSheetIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.widthIn(min = 240.dp, max = 560.dp)
                        )
                    }
                }
            }

            if (!state.requiresPassword && state.isRenderingPreview && state.previewBitmap != null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(26.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }

        // Quick Sheet Picker Strip
        if (totalSheets > 1 && !state.requiresPassword) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(state.jobSummary.sheets) { idx, sheet ->
                    val selected = idx == state.currentSheetIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.clickable { onSelectSheet(idx) }
                    ) {
                        Text(
                            text = "S${idx + 1} (${formatCompactSlots(sheet.pageSlots)})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrintToolsPanel(
    config: PrintConfig,
    totalSourcePages: Int,
    totalSheets: Int,
    allowPrinting: Boolean,
    onUpdateConfig: ((PrintConfig) -> PrintConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Printing PDF Tools", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$totalSourcePages source page(s) → $totalSheets output sheet(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = config.colorMode.label,
                containerColor = if (config.colorMode == PrintColorMode.GRAYSCALE) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (config.colorMode == PrintColorMode.GRAYSCALE) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }

        // 1. Print Layout Tools
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Print Layout Tool", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrintLayoutTool.entries.forEach { tool ->
                    val icon = when (tool) {
                        PrintLayoutTool.STANDARD -> Icons.Filled.Print
                        PrintLayoutTool.N_UP -> Icons.Filled.GridView
                        PrintLayoutTool.BOOKLET -> Icons.Filled.AutoStories
                        PrintLayoutTool.POSTER_FIT -> Icons.Filled.CropFree
                    }
                    FilterChip(
                        selected = config.layoutTool == tool,
                        onClick = { onUpdateConfig { it.copy(layoutTool = tool) } },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(tool.label) },
                        modifier = Modifier.testTag("print_layout_${tool.name.lowercase()}")
                    )
                }
            }
            Text(
                text = config.layoutTool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2. Pages Per Sheet (when not booklet or poster)
        if (config.layoutTool != PrintLayoutTool.BOOKLET && config.layoutTool != PrintLayoutTool.POSTER_FIT) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pages Per Sheet (N-Up)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrintPagesPerSheet.entries.forEach { nUp ->
                        FilterChip(
                            selected = config.pagesPerSheet == nUp,
                            onClick = {
                                onUpdateConfig {
                                    it.copy(
                                        pagesPerSheet = nUp,
                                        layoutTool = if (nUp.count > 1) PrintLayoutTool.N_UP else PrintLayoutTool.STANDARD
                                    )
                                }
                            },
                            label = { Text(nUp.label) },
                            modifier = Modifier.testTag("print_nup_${nUp.count}")
                        )
                    }
                }
            }
        }

        // 3. Page Subset & Manual Duplex (All / Odd / Even / Custom)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Pages & Manual Duplex Filter", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrintPageSubset.entries.forEach { subset ->
                    FilterChip(
                        selected = config.pageSubset == subset,
                        onClick = { onUpdateConfig { it.copy(pageSubset = subset) } },
                        label = { Text(subset.label) },
                        modifier = Modifier.testTag("print_subset_${subset.name.lowercase()}")
                    )
                }
            }
            AnimatedVisibility(visible = config.pageSubset == PrintPageSubset.CUSTOM) {
                OutlinedTextField(
                    value = config.customPageRange,
                    onValueChange = { expr -> onUpdateConfig { it.copy(customPageRange = expr) } },
                    label = { Text("Custom Page Range") },
                    placeholder = { Text("1-3, 5") },
                    supportingText = { Text("1 to $totalSourcePages (e.g. 1-4, 7)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("print_custom_range_input")
                )
            }
        }

        // 4. Paper Size & Orientation
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Paper Size", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrintPaperSize.entries.forEach { size ->
                    FilterChip(
                        selected = config.paperSize == size,
                        onClick = { onUpdateConfig { it.copy(paperSize = size) } },
                        label = { Text(size.label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Orientation", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintOrientationMode.entries.forEach { ori ->
                    FilterChip(
                        selected = config.orientation == ori,
                        onClick = { onUpdateConfig { it.copy(orientation = ori) } },
                        label = { Text(ori.label) }
                    )
                }
            }
        }

        // 5. Color Mode & Print Scale
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ink & Color Mode", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintColorMode.entries.forEach { color ->
                    FilterChip(
                        selected = config.colorMode == color,
                        onClick = { onUpdateConfig { it.copy(colorMode = color) } },
                        leadingIcon = if (color == PrintColorMode.GRAYSCALE) {
                            { Icon(Icons.Filled.FilterBAndW, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = { Text(color.label) },
                        modifier = Modifier.testTag("print_color_${color.name.lowercase()}")
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Scaling & Margins", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrintScaleMode.entries.forEach { scale ->
                    FilterChip(
                        selected = config.scaleMode == scale,
                        onClick = { onUpdateConfig { it.copy(scaleMode = scale) } },
                        label = { Text(scale.label) }
                    )
                }
            }
            if (config.layoutTool != PrintLayoutTool.POSTER_FIT) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrintMarginPreset.entries.forEach { m ->
                        FilterChip(
                            selected = config.marginPreset == m,
                            onClick = { onUpdateConfig { it.copy(marginPreset = m) } },
                            label = { Text("Margin: ${m.label}") }
                        )
                    }
                }
            }
        }

        // 6. Copies & DPI Quality
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Collated Copies", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Applied to print job & saved print PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onUpdateConfig { it.copy(copies = (it.copies - 1).coerceAtLeast(1)) } },
                    enabled = config.copies > 1
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease copies")
                }
                Text(
                    text = config.copies.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onUpdateConfig { it.copy(copies = (it.copies + 1).coerceAtMost(10)) } },
                    enabled = config.copies < 10
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase copies")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Print Resolution", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(150, 300, 600).forEach { dpi ->
                    FilterChip(
                        selected = config.dpi == dpi,
                        onClick = { onUpdateConfig { it.copy(dpi = dpi) } },
                        label = { Text("$dpi DPI") }
                    )
                }
            }
        }

        // 7. Finishing Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.BorderOuter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Draw Cell & Page Borders", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = config.showPageBorders,
                onCheckedChange = { checked -> onUpdateConfig { it.copy(showPageBorders = checked) } }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Print Filename Header & Sheet Footer", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Adds document title & Sheet X of Y",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.showHeaderFooter,
                onCheckedChange = { checked -> onUpdateConfig { it.copy(showHeaderFooter = checked) } }
            )
        }

        if (!allowPrinting) {
            Text(
                text = "Note: Printing is locked by this PDF's permissions.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

private fun formatSheetPagesLabel(slots: List<Int>): String {
    if (slots.isEmpty()) return ""
    return slots.joinToString(", ") { idx -> if (idx < 0) "Blank" else "P.${idx + 1}" }
}

private fun formatCompactSlots(slots: List<Int>): String {
    if (slots.isEmpty()) return "-"
    return slots.joinToString(",") { idx -> if (idx < 0) "·" else "${idx + 1}" }
}
