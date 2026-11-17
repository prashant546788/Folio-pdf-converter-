package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConvertedPdfEntity
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.ViewerThemeMode
import com.example.ui.PdfToolMode
import com.example.ui.ScannerTab
import com.example.ui.ViewerUiState
import com.example.ui.components.CloudUploadSheet
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.sharePdfFile
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfViewerScreen(
    state: ViewerUiState,
    cloudAccounts: List<CloudAccountState>,
    onBack: () -> Unit,
    onSelectPage: (Int) -> Unit,
    onSetZoom: (Float) -> Unit,
    onSetThemeMode: (ViewerThemeMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPasswordInputChange: (String) -> Unit,
    onUnlockPdf: () -> Unit,
    onUploadToCloud: (ConvertedPdfEntity, CloudProvider) -> Unit,
    onConnectCloud: (CloudProvider) -> Unit,
    onSaveToDownloads: (ConvertedPdfEntity) -> Unit,
    onOpenPrintPreview: (Long) -> Unit,
    onLaunchToolForDocument: (ConvertedPdfEntity, PdfToolMode) -> Unit,
    onExtractAiNotes: (ConvertedPdfEntity) -> Unit,
    onOpenScanner: (ScannerTab) -> Unit,
    onOpenSettings: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val doc = state.document

    var showSearchBar by remember { mutableStateOf(false) }
    var showToolsBar by remember { mutableStateOf(true) }
    var showOcrSheet by remember { mutableStateOf(false) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var showAllToolsSheet by remember { mutableStateOf(false) }
    var showCloudSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }
    var showPasswordPlain by remember { mutableStateOf(false) }

    val pageColorFilter = remember(state.themeMode) {
        when (state.themeMode) {
            ViewerThemeMode.ORIGINAL -> null
            ViewerThemeMode.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.96f, 0.04f, 0.02f, 0f, 8f,
                        0.02f, 0.89f, 0.02f, 0f, 4f,
                        0.00f, 0.03f, 0.72f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ViewerThemeMode.NIGHT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = doc?.fileName ?: "PDF Reader",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (doc != null) {
                            Text(
                                text = "Page ${state.currentPage + 1} of ${doc.pageCount}" +
                                    (if (doc.ocrEnabled) " · OCR" else "") +
                                    (if (!doc.securitySummary.isNullOrBlank()) " · ${doc.securitySummary}" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.testTag("viewer_search_toggle_btn")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search inside PDF")
                    }
                    IconButton(
                        onClick = { showMetadataSheet = true },
                        modifier = Modifier.testTag("viewer_info_btn")
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "PDF Metadata & Properties")
                    }
                    if (doc != null) {
                        IconButton(
                            onClick = { showAllToolsSheet = true },
                            modifier = Modifier.testTag("viewer_all_tools_btn")
                        ) {
                            Icon(Icons.Filled.Build, contentDescription = "All PDF Tools")
                        }
                        IconButton(
                            onClick = { onOpenPrintPreview(doc.id) },
                            modifier = Modifier.testTag("viewer_print_btn")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "Print Preview")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (doc != null) {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        // Reader Theme & Quick Actions Strip
                        if (showToolsBar) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ViewerThemeMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = state.themeMode == mode,
                                        onClick = { onSetThemeMode(mode) },
                                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("viewer_theme_${mode.name.lowercase()}")
                                    )
                                }
                                AssistChip(
                                    onClick = { showOcrSheet = true },
                                    leadingIcon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("OCR Text") },
                                    modifier = Modifier.testTag("viewer_ocr_text_btn")
                                )
                                AssistChip(
                                    onClick = { showAllToolsSheet = true },
                                    leadingIcon = { Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("All Tools") },
                                    modifier = Modifier.testTag("viewer_strip_all_tools_btn")
                                )
                                AssistChip(
                                    onClick = { showCloudSheet = true },
                                    leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Cloud") },
                                    modifier = Modifier.testTag("viewer_cloud_btn")
                                )
                                AssistChip(
                                    onClick = { onLaunchToolForDocument(doc, PdfToolMode.TRANSLATE_PDF) },
                                    leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Translate") }
                                )
                                AssistChip(
                                    onClick = { onLaunchToolForDocument(doc, PdfToolMode.WATERMARK_PDF) },
                                    leadingIcon = { Icon(Icons.Filled.Approval, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Watermark") }
                                )
                                AssistChip(
                                    onClick = { onLaunchToolForDocument(doc, PdfToolMode.EDIT_METADATA) },
                                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Metadata") }
                                )
                                AssistChip(
                                    onClick = { onLaunchToolForDocument(doc, PdfToolMode.PROTECT_PDF) },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Protect") }
                                )
                                AssistChip(
                                    onClick = { onLaunchToolForDocument(doc, PdfToolMode.SPLIT_PDF) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Split") }
                                )
                                AssistChip(
                                    onClick = { onLaunchToolForDocument(doc, PdfToolMode.COMPRESS_PDF) },
                                    leadingIcon = { Icon(Icons.Filled.Compress, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Compress") }
                                )
                                AssistChip(
                                    onClick = { onLaunchToolForDocument(doc, PdfToolMode.ROTATE_PDF) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("Rotate") }
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onSelectPage(state.currentPage - 1) },
                                    enabled = state.currentPage > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clickable(enabled = doc.pageCount > 1) { showJumpDialog = true }
                                        .testTag("viewer_page_pill")
                                ) {
                                    Text(
                                        text = "${state.currentPage + 1} / ${doc.pageCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onSelectPage(state.currentPage + 1) },
                                    enabled = state.currentPage < doc.pageCount - 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onSetZoom(state.zoomScale - 0.5f) },
                                    enabled = state.zoomScale > 1f
                                ) {
                                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                                }
                                Text(
                                    text = "${String.format("%.1f", state.zoomScale)}x",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                IconButton(
                                    onClick = { onSetZoom(state.zoomScale + 0.5f) },
                                    enabled = state.zoomScale < 2.5f
                                ) {
                                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = showSearchBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search embedded OCR & document text...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("viewer_search_input")
                    )

                    if (state.searchQuery.isNotBlank()) {
                        if (state.isSearching) {
                            Text("Searching pages...", style = MaterialTheme.typography.bodySmall)
                        } else if (state.searchHits.isEmpty()) {
                            Text(
                                text = "No matches found in this PDF.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(112.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(state.searchHits) { hit ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectPage(hit.pageIndex) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    text = "P.${hit.pageIndex + 1} (${hit.matchCount})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = hit.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val readerBg = when (state.themeMode) {
                ViewerThemeMode.ORIGINAL -> MaterialTheme.colorScheme.surfaceContainerHigh
                ViewerThemeMode.SEPIA -> Color(0xFFEBE0CC)
                ViewerThemeMode.NIGHT -> Color(0xFF101216)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(readerBg),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = state.pageBitmap
                if (state.requiresPassword) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .padding(20.dp)
                            .testTag("viewer_password_prompt_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Password protected PDF",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Password Required",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "This PDF is encrypted. Enter its open password to view pages.",
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
                                            contentDescription = if (showPasswordPlain) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("viewer_unlock_password_input")
                            )
                            Button(
                                onClick = onUnlockPdf,
                                enabled = !state.isLoadingPage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("viewer_unlock_btn")
                            ) {
                                Text(if (state.isLoadingPage) "Unlocking..." else "Unlock PDF")
                            }
                        }
                    }
                } else if (state.isLoadingPage && bitmap == null) {
                    CircularProgressIndicator()
                } else if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .pointerInput(state.zoomScale, showToolsBar) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        val nextZoom = if (state.zoomScale > 1.2f) 1f else 2f
                                        onSetZoom(nextZoom)
                                    },
                                    onTap = {
                                        showToolsBar = !showToolsBar
                                    }
                                )
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shadowElevation = 6.dp,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "PDF Page ${state.currentPage + 1}",
                                contentScale = ContentScale.Fit,
                                colorFilter = pageColorFilter,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Unable to render PDF page",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!state.requiresPassword && state.isLoadingPage && bitmap != null) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(28.dp)
                    )
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog && doc != null) {
        var sliderPos by remember(state.currentPage) { mutableFloatStateOf((state.currentPage + 1).toFloat()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Page ${sliderPos.roundToInt()} of ${doc.pageCount}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = sliderPos,
                        onValueChange = { sliderPos = it },
                        valueRange = 1f..doc.pageCount.coerceAtLeast(1).toFloat(),
                        steps = (doc.pageCount - 2).coerceAtLeast(0)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSelectPage((sliderPos.roundToInt() - 1).coerceIn(0, doc.pageCount - 1))
                    showJumpDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // All PDF Tools Sheet inside Reader View
    if (showAllToolsSheet && doc != null) {
        ModalBottomSheet(onDismissRequest = { showAllToolsSheet = false }) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "All PDF Reader Tools",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${doc.fileName} · ${doc.pageCount} page(s) · ${formatFileSize(doc.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val toolEntries: List<Triple<ImageVector, String, () -> Unit>> = listOf(
                    Triple(Icons.Filled.Translate, "Translate PDF to Any Language") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.TRANSLATE_PDF)
                    },
                    Triple(Icons.Filled.GridView, "4-in-1 Page Converter (2 / 4 / 6 / 9 per sheet)") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.FOUR_IN_ONE)
                    },
                    Triple(Icons.Filled.Approval, "Add Text or Image Copyright Watermark") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.WATERMARK_PDF)
                    },
                    Triple(Icons.Filled.Print, "Print Preview & Printing Tools") {
                        showAllToolsSheet = false
                        onOpenPrintPreview(doc.id)
                    },
                    Triple(Icons.Filled.Badge, "Edit PDF Metadata (Title, Author, Keywords)") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.EDIT_METADATA)
                    },
                    Triple(Icons.Filled.Lock, "Set Password & Permissions") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.PROTECT_PDF)
                    },
                    Triple(Icons.Filled.Image, "Export PDF Pages to PNG / JPG") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.PDF_TO_IMAGES)
                    },
                    Triple(Icons.AutoMirrored.Filled.CallSplit, "Split Pages or Extract Range") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.SPLIT_PDF)
                    },
                    Triple(Icons.Filled.Compress, "Compress & Shrink File Size") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.COMPRESS_PDF)
                    },
                    Triple(Icons.AutoMirrored.Filled.RotateRight, "Rotate All Pages (90° / 180° / 270°)") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.ROTATE_PDF)
                    },
                    Triple(Icons.AutoMirrored.Filled.MergeType, "Add PDF to Merge Queue") {
                        showAllToolsSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.MERGE_PDFS)
                    },
                    Triple(Icons.Filled.AutoAwesome, "Extract AI Study Notes from PDF") {
                        showAllToolsSheet = false
                        onExtractAiNotes(doc)
                    },
                    Triple(Icons.Filled.DocumentScanner, "View & Copy Extracted OCR Text") {
                        showAllToolsSheet = false
                        showOcrSheet = true
                    },
                    Triple(Icons.Filled.CloudUpload, "Upload / Sync to Cloud Storage") {
                        showAllToolsSheet = false
                        showCloudSheet = true
                    },
                    Triple(Icons.Filled.Download, "Save PDF to Downloads") {
                        showAllToolsSheet = false
                        onSaveToDownloads(doc)
                    },
                    Triple(Icons.Filled.Share, "Share PDF File") {
                        showAllToolsSheet = false
                        sharePdfFile(context, doc.filePath, doc.fileName)
                    },
                    Triple(Icons.Filled.QrCodeScanner, "Document & QR Code Scanner / Generator") {
                        showAllToolsSheet = false
                        onOpenScanner(ScannerTab.DOCUMENT)
                    },
                    Triple(Icons.Filled.Settings, "PDF Text Fonts, Colors & Settings") {
                        showAllToolsSheet = false
                        onOpenSettings()
                    }
                )

                toolEntries.forEach { (icon, title, action) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = action),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }
        }
    }

    // Metadata & Document Properties Sheet
    if (showMetadataSheet && doc != null) {
        ModalBottomSheet(onDismissRequest = { showMetadataSheet = false }) {
            val meta = state.metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PDF Metadata & Properties", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${doc.fileName} · ${doc.pageCount} page(s) · ${formatFileSize(doc.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Badge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetadataRow("Title", meta.title.ifBlank { "Not set" })
                        MetadataRow("Author", meta.author.ifBlank { "Not set" })
                        MetadataRow("Subject", meta.subject.ifBlank { "Not set" })
                        MetadataRow("Creator", meta.creator.ifBlank { "Folio PDF" })
                        MetadataRow("Producer", state.producer.ifBlank { "Folio PDF" })

                        val kw = meta.parsedKeywordList()
                        if (kw.isNotEmpty()) {
                            Text(
                                text = "Keywords",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                kw.forEach { tag ->
                                    StatusPill(
                                        text = tag,
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        showMetadataSheet = false
                        onLaunchToolForDocument(doc, PdfToolMode.EDIT_METADATA)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("viewer_edit_metadata_btn")
                ) {
                    Icon(Icons.Filled.Badge, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit PDF Metadata")
                }
            }
        }
    }

    if (showOcrSheet && doc != null) {
        ModalBottomSheet(onDismissRequest = {
            showOcrSheet = false
            copiedNotice = false
        }) {
            val pageText = state.pageTexts.getOrNull(state.currentPage).orEmpty()
                .ifBlank { doc.ocrText }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Extracted PDF Text", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (doc.ocrEnabled) "Embedded via ML Kit OCR (selectable & searchable)" else "Document text layer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (pageText.isNotBlank() && state.canCopyContent) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(doc.ocrText.ifBlank { pageText }))
                            copiedNotice = true
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (copiedNotice) "Copied" else "Copy All")
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        if (!state.canCopyContent) {
                            Text(
                                text = "Copying and text extraction are disabled by this PDF's permission policy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (pageText.isBlank()) {
                            Text(
                                text = "No text was detected on this page (or OCR was disabled during conversion).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    text = pageText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCloudSheet && doc != null) {
        CloudUploadSheet(
            title = "Save ${doc.fileName}",
            accounts = cloudAccounts,
            onSelectProviderToUpload = { provider -> onUploadToCloud(doc, provider) },
            onConnectProvider = onConnectCloud,
            onDismiss = { showCloudSheet = false }
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
