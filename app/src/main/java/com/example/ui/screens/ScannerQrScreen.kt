package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.model.PdfMetadataConfig
import com.example.model.PdfSecurityConfig
import com.example.model.PdfWatermarkConfig
import com.example.model.QrColorPreset
import com.example.model.QrContentType
import com.example.model.ScannedBarcodeItem
import com.example.ui.QrGeneratorUiState
import com.example.ui.ScannerTab
import com.example.ui.ScannerUiState
import com.example.ui.components.PdfMetadataSettingsCard
import com.example.ui.components.PdfSecuritySettingsCard
import com.example.ui.components.PdfWatermarkSettingsCard
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScannerQrScreen(
    state: ScannerUiState,
    metadata: PdfMetadataConfig,
    security: PdfSecurityConfig,
    watermark: PdfWatermarkConfig,
    barcodeScannerOptions: BarcodeScannerOptions,
    onSelectTab: (ScannerTab) -> Unit,
    onBack: () -> Unit,
    onDocumentScanCompleted: (List<Uri>, Uri?) -> Unit,
    onAddFallbackPhotos: (List<Uri>) -> Unit,
    onRemoveScannedPage: (String) -> Unit,
    onOutputNameChange: (String) -> Unit,
    onUpdateMetadata: ((PdfMetadataConfig) -> PdfMetadataConfig) -> Unit,
    onUpdateSecurity: ((PdfSecurityConfig) -> PdfSecurityConfig) -> Unit,
    onUpdateWatermark: ((PdfWatermarkConfig) -> PdfWatermarkConfig) -> Unit,
    onSendToStudio: () -> Unit,
    onSaveScannedPdf: (Boolean) -> Unit,
    onClearScanSession: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    onPrintDocument: (Long) -> Unit,
    onToggleTorch: (Boolean) -> Unit,
    onToggleCameraPaused: () -> Unit,
    onLiveBarcodesDetected: (List<Barcode>) -> Unit,
    onScanImageBarcode: (Uri) -> Unit,
    onClearBarcodes: () -> Unit,
    onGenerateQrFromValue: (String) -> Unit,
    onUpdateQrGenerator: ((QrGeneratorUiState) -> QrGeneratorUiState) -> Unit,
    onSaveQrPng: () -> Unit,
    onShareQrPng: () -> Unit,
    onSaveQrPdf: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val docScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                val pageUris = scanResult.pages?.map { it.imageUri }.orEmpty()
                val pdfUri = scanResult.pdf?.uri
                if (pageUris.isNotEmpty() || pdfUri != null) {
                    onDocumentScanCompleted(pageUris, pdfUri)
                }
            }
        }
    }

    val fallbackPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) onAddFallbackPhotos(uris)
    }

    val qrImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onScanImageBarcode(uri)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission denied. You can still scan QR codes from photos.")
            }
        }
    }

    fun launchAiDocumentScanner() {
        val activity = context as? Activity
        if (activity == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Activity unavailable for camera scanner. Use Import Photos instead.")
            }
            return
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(25)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                docScanLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { err ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        err.localizedMessage ?: "Google Document Scanner unavailable on this device. Use Import Photos."
                    )
                }
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Scanner & QR Studio",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = state.activeTab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scanner_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScannerTab.entries.forEach { tab ->
                            val icon = when (tab) {
                                ScannerTab.DOCUMENT -> Icons.Filled.DocumentScanner
                                ScannerTab.QR_SCAN -> Icons.Filled.QrCodeScanner
                                ScannerTab.QR_GENERATE -> Icons.Filled.QrCode2
                            }
                            FilterChip(
                                selected = state.activeTab == tab,
                                onClick = { onSelectTab(tab) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("scanner_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                when (state.activeTab) {
                    ScannerTab.DOCUMENT -> {
                        item {
                            DocumentScannerHeroCard(
                                onLaunchScanner = { launchAiDocumentScanner() },
                                onImportPhotos = {
                                    fallbackPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                isFinalizing = state.isFinalizingScan
                            )
                        }

                        if (state.scannedPages.isNotEmpty() || state.scannedDirectPdfUri != null) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Scanned Pages (${state.scannedPages.size.coerceAtLeast(1)})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(
                                        onClick = onClearScanSession,
                                        enabled = !state.isFinalizingScan
                                    ) {
                                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Clear")
                                    }
                                }
                            }

                            if (state.scannedPages.isNotEmpty()) {
                                item {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(state.scannedPages, key = { it.id }) { page ->
                                            Card(
                                                modifier = Modifier.width(124.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(156.dp)
                                                ) {
                                                    AsyncImage(
                                                        model = page.imageUri,
                                                        contentDescription = "Scanned page",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    IconButton(
                                                        onClick = { onRemoveScannedPage(page.id) },
                                                        enabled = !state.isFinalizingScan,
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
                                                            .size(30.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Close,
                                                            contentDescription = "Remove scanned page",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                OutlinedTextField(
                                    value = state.scanOutputName,
                                    onValueChange = onOutputNameChange,
                                    label = { Text("Scanned PDF Filename") },
                                    suffix = { Text(".pdf") },
                                    singleLine = true,
                                    enabled = !state.isFinalizingScan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("scan_output_name_input")
                                )
                            }

                            item {
                                PdfMetadataSettingsCard(
                                    metadata = metadata,
                                    onUpdate = onUpdateMetadata,
                                    suggestedTitle = state.scanOutputName,
                                    subtitle = "Embed Title, Author, Subject & Keywords into scanned PDF"
                                )
                            }

                            item {
                                PdfWatermarkSettingsCard(
                                    watermark = watermark,
                                    onUpdate = onUpdateWatermark,
                                    subtitle = "Optionally stamp text or logo copyright watermark on scanned pages"
                                )
                            }

                            item {
                                PdfSecuritySettingsCard(
                                    security = security,
                                    onUpdate = onUpdateSecurity,
                                    subtitle = "Optionally lock your scanned document with password & permissions"
                                )
                            }

                            item {
                                AnimatedVisibility(visible = state.isFinalizingScan) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        Text(
                                            text = state.scanProgressMessage.ifBlank { "Saving scanned PDF..." },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (state.scannedPages.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = {
                                                onSendToStudio()
                                                onBack()
                                            },
                                            enabled = !state.isFinalizingScan,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                                .testTag("scan_send_studio_btn")
                                        ) {
                                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("To Studio")
                                        }
                                    }
                                    Button(
                                        onClick = { onSaveScannedPdf(true) },
                                        enabled = !state.isFinalizingScan,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(52.dp)
                                            .testTag("scan_save_ocr_pdf_btn")
                                    ) {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save OCR PDF")
                                    }
                                }
                            }
                        }

                        if (state.lastScannedDoc != null) {
                            val doc = state.lastScannedDoc
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("scan_saved_doc_card"),
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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = doc.fileName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${doc.pageCount} page(s) · ${formatFileSize(doc.fileSizeBytes)}",
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
                                            ) {
                                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Open Reader")
                                            }
                                            OutlinedButton(
                                                onClick = { onPrintDocument(doc.id) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                            ) {
                                                Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Print")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ScannerTab.QR_SCAN -> {
                        item {
                            QrScannerLiveCard(
                                hasCameraPermission = hasCameraPermission,
                                isTorchEnabled = state.isTorchEnabled,
                                isPaused = state.isLiveCameraPaused,
                                isScanningImage = state.isScanningImageBarcode,
                                barcodeScannerOptions = barcodeScannerOptions,
                                onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                onToggleTorch = { onToggleTorch(!state.isTorchEnabled) },
                                onTogglePause = onToggleCameraPaused,
                                onPickImageForQr = {
                                    qrImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onBarcodesDetected = onLiveBarcodesDetected
                            )
                        }

                        if (state.scannedBarcodes.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Detected Codes (${state.scannedBarcodes.size})",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    TextButton(onClick = onClearBarcodes) {
                                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Clear")
                                    }
                                }
                            }

                            items(state.scannedBarcodes, key = { it.id }) { item ->
                                DetectedBarcodeCard(
                                    item = item,
                                    onGenerateMatchingQr = { onGenerateQrFromValue(item.rawValue) },
                                    onCopied = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Copied QR content to clipboard")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    ScannerTab.QR_GENERATE -> {
                        item {
                            QrGeneratorSection(
                                gen = state.qrGenerator,
                                metadata = metadata,
                                security = security,
                                watermark = watermark,
                                onUpdateQr = onUpdateQrGenerator,
                                onUpdateMetadata = onUpdateMetadata,
                                onUpdateSecurity = onUpdateSecurity,
                                onUpdateWatermark = onUpdateWatermark,
                                onSavePng = onSaveQrPng,
                                onSharePng = onShareQrPng,
                                onSavePdf = onSaveQrPdf,
                                onOpenViewer = onOpenViewer,
                                onPrintDocument = onPrintDocument
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun DocumentScannerHeroCard(
    onLaunchScanner: () -> Unit,
    onImportPhotos: () -> Unit,
    isFinalizing: Boolean
) {
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
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = "Document Scanner",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Document Scanner",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Auto-detect corners, de-skew pages, clean shadows, and build searchable OCR PDFs.",
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
                    onClick = onLaunchScanner,
                    enabled = !isFinalizing,
                    modifier = Modifier
                        .weight(1.25f)
                        .height(50.dp)
                        .testTag("launch_doc_scanner_btn")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Pages")
                }
                FilledTonalButton(
                    onClick = onImportPhotos,
                    enabled = !isFinalizing,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("scan_import_photos_btn")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photos")
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun QrScannerLiveCard(
    hasCameraPermission: Boolean,
    isTorchEnabled: Boolean,
    isPaused: Boolean,
    isScanningImage: Boolean,
    barcodeScannerOptions: BarcodeScannerOptions,
    onRequestCamera: () -> Unit,
    onToggleTorch: () -> Unit,
    onTogglePause: () -> Unit,
    onPickImageForQr: () -> Unit,
    onBarcodesDetected: (List<Barcode>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(cameraRef, isTorchEnabled) {
        runCatching {
            cameraRef?.cameraControl?.enableTorch(isTorchEnabled)
        }
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("QR & Barcode Scanner", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Scan QR links, Wi-Fi, vCards, ISBNs, or barcodes live or from gallery photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (hasCameraPermission) {
                val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
                val scanner = remember(barcodeScannerOptions) {
                    BarcodeScanning.getClient(barcodeScannerOptions)
                }
                val previewView = remember {
                    PreviewView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }

                DisposableEffect(lifecycleOwner, isPaused) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    var activeProvider: ProcessCameraProvider? = null
                    val listener = Runnable {
                        val provider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@Runnable
                        activeProvider = provider
                        provider.unbindAll()
                        if (!isPaused) {
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { imageAnalysis ->
                                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val inputImage = InputImage.fromMediaImage(
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
                                            )
                                            scanner.process(inputImage)
                                                .addOnSuccessListener { barcodes ->
                                                    if (barcodes.isNotEmpty()) {
                                                        onBarcodesDetected(barcodes)
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }
                            runCatching {
                                val cam = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                                cameraRef = cam
                                cam.cameraControl.enableTorch(isTorchEnabled)
                            }
                        }
                    }
                    cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

                    onDispose {
                        runCatching { activeProvider?.unbindAll() }
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        runCatching { scanner.close() }
                        runCatching { analysisExecutor.shutdown() }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .size(164.dp)
                            .border(
                                width = 2.dp,
                                color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(18.dp)
                            )
                    )
                    if (isPaused) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Camera Paused",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleTorch,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_torch_btn")
                    ) {
                        Icon(
                            imageVector = if (isTorchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isTorchEnabled) "Torch On" else "Torch")
                    }
                    OutlinedButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Camera permission enables instant live QR & barcode scanning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRequestCamera,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("qr_grant_camera_btn")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Enable Camera Scanner")
                        }
                    }
                }
            }

            if (isScanningImage) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            FilledTonalButton(
                onClick = onPickImageForQr,
                enabled = !isScanningImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("qr_scan_image_btn")
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan QR / Barcode from Photo")
            }
        }
    }
}

@Composable
private fun DetectedBarcodeCard(
    item: ScannedBarcodeItem,
    onGenerateMatchingQr: () -> Unit,
    onCopied: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detected_qr_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = item.formatLabel,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                StatusPill(
                    text = item.typeLabel,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = item.displayValue,
                style = MaterialTheme.typography.bodyLarge
            )
            if (item.displayValue != item.rawValue) {
                Text(
                    text = item.rawValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(item.rawValue))
                        onCopied()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                if (item.isUrl) {
                    Button(
                        onClick = { runCatching { uriHandler.openUri(item.rawValue) } },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open")
                    }
                }
                FilledTonalButton(
                    onClick = onGenerateMatchingQr,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Make QR")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QrGeneratorSection(
    gen: QrGeneratorUiState,
    metadata: PdfMetadataConfig,
    security: PdfSecurityConfig,
    watermark: PdfWatermarkConfig,
    onUpdateQr: ((QrGeneratorUiState) -> QrGeneratorUiState) -> Unit,
    onUpdateMetadata: ((PdfMetadataConfig) -> PdfMetadataConfig) -> Unit,
    onUpdateSecurity: ((PdfSecurityConfig) -> PdfSecurityConfig) -> Unit,
    onUpdateWatermark: ((PdfWatermarkConfig) -> PdfWatermarkConfig) -> Unit,
    onSavePng: () -> Unit,
    onSharePng: () -> Unit,
    onSavePdf: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    onPrintDocument: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                Text("QR Content Type", style = MaterialTheme.typography.titleMedium)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QrContentType.entries.forEach { type ->
                        FilterChip(
                            selected = gen.contentType == type,
                            onClick = { onUpdateQr { it.copy(contentType = type) } },
                            label = { Text(type.label) },
                            modifier = Modifier.testTag("qr_type_${type.name.lowercase()}")
                        )
                    }
                }

                when (gen.contentType) {
                    QrContentType.TEXT_URL -> {
                        OutlinedTextField(
                            value = gen.primaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(primaryText = v) } },
                            label = { Text("URL or Text Content") },
                            placeholder = { Text("https://example.com or any note") },
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_primary_input")
                        )
                    }
                    QrContentType.WIFI -> {
                        OutlinedTextField(
                            value = gen.primaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(primaryText = v) } },
                            label = { Text("Wi-Fi Network Name (SSID)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_primary_input")
                        )
                        OutlinedTextField(
                            value = gen.secondaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(secondaryText = v) } },
                            label = { Text("Wi-Fi Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("WPA", "WEP", "nopass").forEach { secMode ->
                                FilterChip(
                                    selected = gen.wifiSecurity == secMode,
                                    onClick = { onUpdateQr { it.copy(wifiSecurity = secMode) } },
                                    label = { Text(if (secMode == "nopass") "Open" else secMode) }
                                )
                            }
                        }
                    }
                    QrContentType.EMAIL -> {
                        OutlinedTextField(
                            value = gen.primaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(primaryText = v) } },
                            label = { Text("Recipient Email Address") },
                            placeholder = { Text("name@company.com") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_primary_input")
                        )
                        OutlinedTextField(
                            value = gen.secondaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(secondaryText = v) } },
                            label = { Text("Subject (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = gen.tertiaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(tertiaryText = v) } },
                            label = { Text("Body Message (optional)") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    QrContentType.PHONE -> {
                        OutlinedTextField(
                            value = gen.primaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(primaryText = v) } },
                            label = { Text("Phone Number") },
                            placeholder = { Text("+1 555 010 9988") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_primary_input")
                        )
                    }
                    QrContentType.VCARD -> {
                        OutlinedTextField(
                            value = gen.primaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(primaryText = v) } },
                            label = { Text("Full Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_primary_input")
                        )
                        OutlinedTextField(
                            value = gen.secondaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(secondaryText = v) } },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = gen.tertiaryText,
                            onValueChange = { v -> onUpdateQr { it.copy(tertiaryText = v) } },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = gen.caption,
                    onValueChange = { v -> onUpdateQr { it.copy(caption = v) } },
                    label = { Text("QR Label / Caption (optional)") },
                    placeholder = { Text("Guest Wi-Fi · Scan to Connect") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_caption_input")
                )

                Text("Style Preset", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QrColorPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = gen.colorPreset == preset,
                            onClick = { onUpdateQr { it.copy(colorPreset = preset) } },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.fgArgb))
                                )
                            },
                            label = { Text(preset.label) }
                        )
                    }
                }
            }
        }

        // Live QR Preview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_preview_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val bmp = gen.previewBitmap
                if (bmp != null) {
                    Surface(
                        shadowElevation = 4.dp,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Generated QR Code",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(220.dp)
                                .padding(8.dp)
                        )
                    }
                }
                if (gen.encodedPayload.isNotBlank()) {
                    Text(
                        text = gen.encodedPayload,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSavePng,
                        enabled = bmp != null && !gen.isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_save_png_btn")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save PNG")
                    }
                    OutlinedButton(
                        onClick = onSharePng,
                        enabled = bmp != null && !gen.isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("qr_share_png_btn")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
                Button(
                    onClick = onSavePdf,
                    enabled = bmp != null && !gen.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("qr_save_pdf_btn")
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (gen.isSaving) "Saving..." else "Save Printable QR Card PDF")
                }
            }
        }

        if (gen.lastSavedPdfDoc != null) {
            val savedDoc = gen.lastSavedPdfDoc
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = savedDoc.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Saved to Vault · ${formatFileSize(savedDoc.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onOpenViewer(savedDoc.id) }) {
                        Icon(Icons.Filled.Visibility, contentDescription = "Open Reader")
                    }
                    IconButton(onClick = { onPrintDocument(savedDoc.id) }) {
                        Icon(Icons.Filled.Print, contentDescription = "Print")
                    }
                }
            }
        }

        PdfWatermarkSettingsCard(
            watermark = watermark,
            onUpdate = onUpdateWatermark,
            subtitle = "Optionally stamp text or logo copyright watermark on QR Card PDF"
        )

        PdfMetadataSettingsCard(
            metadata = metadata,
            onUpdate = onUpdateMetadata,
            suggestedTitle = gen.caption.ifBlank { "Folio_QR_Card" },
            subtitle = "Embed Title, Author, Subject & Keywords into QR Card PDF"
        )

        PdfSecuritySettingsCard(
            security = security,
            onUpdate = onUpdateSecurity,
            subtitle = "Protect generated QR Card PDF with password & permissions"
        )
    }
}
