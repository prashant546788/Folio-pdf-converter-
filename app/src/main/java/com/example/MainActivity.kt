package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ai.configureAppCheck
import com.example.model.CloudProvider
import com.example.ui.FolioEvent
import com.example.ui.FolioViewModel
import com.example.ui.ScannerTab
import com.example.ui.components.ProviderConfigDialog
import com.example.ui.components.StatusPill
import com.example.ui.screens.BatchScreen
import com.example.ui.screens.CloudScreen
import com.example.ui.screens.NotesScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.PrintPreviewScreen
import com.example.ui.screens.ScannerQrScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.FolioPdfTheme

private const val ROUTE_STUDIO = "studio"
private const val ROUTE_BATCH = "batch"
private const val ROUTE_NOTES = "notes"
private const val ROUTE_VAULT = "vault"
private const val ROUTE_CLOUD = "cloud"
private const val ROUTE_VIEWER = "viewer"
private const val ROUTE_PRINT_PREVIEW = "print_preview"
private const val ROUTE_SCANNER = "scanner_qr"
private const val ROUTE_SETTINGS = "settings"

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TOP_TABS = listOf(
    NavTab(ROUTE_STUDIO, "Studio", Icons.Filled.AutoFixHigh, Icons.Outlined.AutoFixHigh),
    NavTab(ROUTE_BATCH, "Batch", Icons.Filled.DynamicFeed, Icons.Outlined.DynamicFeed),
    NavTab(ROUTE_NOTES, "Notes", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    NavTab(ROUTE_VAULT, "Vault", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy),
    NavTab(ROUTE_CLOUD, "Cloud", Icons.Filled.Cloud, Icons.Outlined.Cloud)
)

class MainActivity : ComponentActivity() {
    private val viewModel: FolioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        setContent {
            FolioPdfTheme {
                FolioPdfApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "foliopdf" && data.host == "oauth") {
            viewModel.onOAuthDeepLink(data)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioPdfApp(viewModel: FolioViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    val vaultDocs by viewModel.vaultDocuments.collectAsStateWithLifecycle()
    val vaultQuery by viewModel.vaultQuery.collectAsStateWithLifecycle()
    val vaultFilter by viewModel.vaultFilter.collectAsStateWithLifecycle()
    val cloudAccounts by viewModel.cloudAccounts.collectAsStateWithLifecycle()
    val uploadLogs by viewModel.uploadLogs.collectAsStateWithLifecycle()
    val uploadingDocIds by viewModel.uploadingDocIds.collectAsStateWithLifecycle()
    val viewerState by viewModel.viewerState.collectAsStateWithLifecycle()
    val noteBatchState by viewModel.noteBatchState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pdfToolsState by viewModel.pdfToolsState.collectAsStateWithLifecycle()
    val securityConfig by viewModel.securityConfig.collectAsStateWithLifecycle()
    val metadataConfig by viewModel.metadataConfig.collectAsStateWithLifecycle()
    val watermarkConfig by viewModel.watermarkConfig.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val driveSyncState by viewModel.driveSyncState.collectAsStateWithLifecycle()
    val printPreviewState by viewModel.printPreviewState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var configDialogData by remember { mutableStateOf<Pair<CloudProvider, String>?>(null) }

    val googleResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.onGoogleAuthResolutionResult(activityResult.data)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FolioEvent.Snackbar -> snackbarHostState.showSnackbar(event.message)
                is FolioEvent.OpenViewer -> {
                    if (currentRoute != ROUTE_VIEWER) {
                        navController.navigate(ROUTE_VIEWER)
                    }
                }
                is FolioEvent.OpenPrintPreview -> {
                    if (currentRoute != ROUTE_PRINT_PREVIEW) {
                        navController.navigate(ROUTE_PRINT_PREVIEW)
                    }
                }
                is FolioEvent.OpenScannerQr -> {
                    if (currentRoute != ROUTE_SCANNER) {
                        navController.navigate(ROUTE_SCANNER)
                    }
                }
                is FolioEvent.LaunchGoogleResolution -> {
                    googleResolutionLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build()
                    )
                }
                is FolioEvent.ProviderConfigRequired -> {
                    configDialogData = event.provider to event.instructions
                }
            }
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isViewerScreen = currentRoute == ROUTE_VIEWER ||
        currentRoute == ROUTE_PRINT_PREVIEW ||
        currentRoute == ROUTE_SCANNER ||
        currentRoute == ROUTE_SETTINGS
    val connectedCount = remember(cloudAccounts) { cloudAccounts.count { it.isConnected } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                if (!isViewerScreen) {
                    TopAppBar(
                        title = {
                            Row {
                                Text(
                                    text = "Folio PDF",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        actions = {
                            if (watermarkConfig.isActive()) {
                                StatusPill(
                                    text = "Watermark",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (securityConfig.hasAnyRestrictionOrPassword()) {
                                StatusPill(
                                    text = "Locked",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (settings.enableOcr) {
                                StatusPill(
                                    text = "OCR",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (connectedCount > 0) {
                                StatusPill(
                                    text = "$connectedCount Cloud",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            IconButton(
                                onClick = { viewModel.openScannerWorkspace(ScannerTab.DOCUMENT) },
                                modifier = Modifier.testTag("topbar_doc_scanner_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DocumentScanner,
                                    contentDescription = "Document Scanner"
                                )
                            }
                            IconButton(
                                onClick = { viewModel.openScannerWorkspace(ScannerTab.QR_SCAN) },
                                modifier = Modifier.testTag("topbar_qr_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QrCodeScanner,
                                    contentDescription = "QR Scanner and Generator"
                                )
                            }
                            IconButton(
                                onClick = { navController.navigate(ROUTE_SETTINGS) },
                                modifier = Modifier.testTag("topbar_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "PDF Fonts, Colors and Settings"
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!isViewerScreen && !useNavRail) {
                    NavigationBar {
                        TOP_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val badgeCount = when (tab.route) {
                                ROUTE_BATCH -> batchState.items.size
                                ROUTE_NOTES -> noteBatchState.items.size
                                ROUTE_VAULT -> vaultDocs.size
                                else -> 0
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isViewerScreen && useNavRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        TOP_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val badgeCount = when (tab.route) {
                                ROUTE_BATCH -> batchState.items.size
                                ROUTE_NOTES -> noteBatchState.items.size
                                ROUTE_VAULT -> vaultDocs.size
                                else -> 0
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (badgeCount > 0) {
                                                Badge { Text(badgeCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_STUDIO
                    ) {
                        composable(ROUTE_STUDIO) {
                            StudioScreen(
                                state = studioState,
                                toolsState = pdfToolsState,
                                vaultDocuments = vaultDocs,
                                settings = settings,
                                security = securityConfig,
                                metadata = metadataConfig,
                                watermark = watermarkConfig,
                                cloudAccounts = cloudAccounts,
                                onAddFiles = viewModel::addStudioFiles,
                                onOutputNameChange = viewModel::updateStudioOutputName,
                                onMoveUp = viewModel::moveStudioItemUp,
                                onMoveDown = viewModel::moveStudioItemDown,
                                onRotate = viewModel::rotateStudioItem,
                                onRemove = viewModel::removeStudioItem,
                                onClearQueue = viewModel::clearStudioQueue,
                                onUpdateSettings = viewModel::updateSettings,
                                onUpdateSecurity = viewModel::updateSecurityConfig,
                                onUpdateMetadata = viewModel::updateMetadataConfig,
                                onUpdateWatermark = viewModel::updateWatermarkConfig,
                                onWatermarkOutputNameChange = viewModel::setToolWatermarkOutputName,
                                onRunWatermarkPdf = viewModel::runWatermarkPdf,
                                onTranslationSourceLanguageChange = viewModel::setTranslationSourceLanguage,
                                onTranslationTargetLanguageChange = viewModel::setTranslationTargetLanguage,
                                onSwapTranslationLanguages = viewModel::swapTranslationLanguages,
                                onTranslationToneChange = viewModel::setTranslationTone,
                                onTranslationOutputNameChange = viewModel::setTranslationOutputName,
                                onRunTranslatePdf = viewModel::runTranslatePdf,
                                onConvert = viewModel::convertStudioToPdf,
                                onSelectToolMode = viewModel::selectPdfToolMode,
                                onPickSinglePdfUri = viewModel::setSinglePdfSourceFromUri,
                                onPickSingleVaultPdf = viewModel::setSinglePdfSourceFromVault,
                                onAddMergePdfUris = viewModel::addMergePdfsFromUris,
                                onAddMergeVaultPdf = viewModel::addMergePdfFromVault,
                                onMoveMergePdfUp = viewModel::moveMergePdfUp,
                                onMoveMergePdfDown = viewModel::moveMergePdfDown,
                                onRemoveMergePdf = viewModel::removeMergePdf,
                                onClearMergePdfs = viewModel::clearMergePdfs,
                                onMergeOutputNameChange = viewModel::setMergeOutputName,
                                onImageFormatChange = viewModel::setToolImageFormat,
                                onImageDpiChange = viewModel::setToolImageDpi,
                                onToolPageRangeChange = viewModel::setToolPageRangeExpr,
                                onToolSplitEachPageChange = viewModel::setToolSplitEachPage,
                                onToolCompressionLevelChange = viewModel::setToolCompressionLevel,
                                onToolRotationDegreesChange = viewModel::setToolRotationDegrees,
                                onExistingPdfPasswordChange = viewModel::setToolExistingPdfPassword,
                                onMetadataOutputNameChange = viewModel::setToolMetadataOutputName,
                                onUnlockMetadata = viewModel::unlockAndInspectMetadata,
                                onRunSaveMetadata = viewModel::runSavePdfWithMetadata,
                                onNUpPagesPerSheetChange = viewModel::setToolNUpPagesPerSheet,
                                onNUpPaperSizeChange = viewModel::setToolNUpPaperSize,
                                onNUpOrientationChange = viewModel::setToolNUpOrientation,
                                onNUpShowBordersChange = viewModel::setToolNUpShowBorders,
                                onNUpGrayscaleChange = viewModel::setToolNUpGrayscale,
                                onNUpOutputNameChange = viewModel::setToolNUpOutputName,
                                onRunFourInOne = viewModel::runFourInOnePdf,
                                onQuickTextTitleChange = viewModel::setQuickTextTitle,
                                onQuickTextBodyChange = viewModel::setQuickTextBody,
                                onApplyQuickTextTemplate = viewModel::applyQuickTextTemplate,
                                onRunQuickTextToPdf = viewModel::runQuickTextToPdf,
                                onRunPdfToImages = viewModel::runPdfToImages,
                                onRunMergePdfs = viewModel::runMergePdfs,
                                onRunSplitPdf = viewModel::runSplitPdf,
                                onRunCompressPdf = viewModel::runCompressPdf,
                                onRunRotatePdf = viewModel::runRotatePdf,
                                onRunProtectPdf = viewModel::runProtectPdf,
                                onOpenViewer = viewModel::openDocumentInViewer,
                                onOpenPrintPreviewForTool = viewModel::openPrintPreviewFromToolsSource,
                                onPrintResultDoc = viewModel::openPrintPreviewForDocument,
                                onSaveToDownloads = viewModel::saveToDownloads,
                                onUploadToCloud = viewModel::uploadDocumentToCloud,
                                onConnectCloud = viewModel::connectCloudProvider,
                                onGoToBatch = { navigateToTab(ROUTE_BATCH) },
                                onOpenScanner = { tab -> viewModel.openScannerWorkspace(tab) },
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                            )
                        }
                        composable(ROUTE_BATCH) {
                            BatchScreen(
                                state = batchState,
                                settings = settings,
                                security = securityConfig,
                                metadata = metadataConfig,
                                watermark = watermarkConfig,
                                cloudAccounts = cloudAccounts,
                                onAddFiles = viewModel::addBatchFiles,
                                onUpdateOutputName = viewModel::updateBatchOutputName,
                                onRemoveItem = viewModel::removeBatchItem,
                                onClearQueue = viewModel::clearBatchQueue,
                                onSetAutoUploadProvider = viewModel::setBatchAutoUploadProvider,
                                onUpdateSettings = viewModel::updateSettings,
                                onUpdateSecurity = viewModel::updateSecurityConfig,
                                onUpdateMetadata = viewModel::updateMetadataConfig,
                                onUpdateWatermark = viewModel::updateWatermarkConfig,
                                onStartBatch = viewModel::startBatchConversion,
                                onCancelBatch = viewModel::cancelBatchConversion,
                                onOpenViewer = viewModel::openDocumentInViewer,
                                onUploadAllCompletedToCloud = viewModel::uploadCompletedBatchToCloud,
                                onConnectCloud = viewModel::connectCloudProvider,
                                onOpenVault = { navigateToTab(ROUTE_VAULT) },
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                            )
                        }
                        composable(ROUTE_NOTES) {
                            NotesScreen(
                                state = noteBatchState,
                                vaultDocuments = vaultDocs,
                                onAddFiles = viewModel::addNoteFiles,
                                onAddVaultPdf = viewModel::addVaultPdfToNotes,
                                onSelectStyle = viewModel::setNoteExtractionStyle,
                                onSelectTier = viewModel::setNoteAiModelTier,
                                onRemoveItem = viewModel::removeNoteItem,
                                onClearQueue = viewModel::clearNoteQueue,
                                onStartExtraction = viewModel::startBatchNoteExtraction,
                                onCancelExtraction = viewModel::cancelBatchNoteExtraction,
                                onExportNotePdf = viewModel::exportNoteItemToPdf,
                                onExportAllNotesPdf = viewModel::exportAllNotesToPdf,
                                onOpenViewer = viewModel::openDocumentInViewer
                            )
                        }
                        composable(ROUTE_VAULT) {
                            VaultScreen(
                                documents = vaultDocs,
                                searchQuery = vaultQuery,
                                activeFilter = vaultFilter,
                                cloudAccounts = cloudAccounts,
                                uploadingDocIds = uploadingDocIds,
                                onSearchQueryChange = viewModel::setVaultQuery,
                                onFilterChange = viewModel::setVaultFilter,
                                onOpenViewer = viewModel::openDocumentInViewer,
                                onUploadToCloud = viewModel::uploadDocumentToCloud,
                                onConnectCloud = viewModel::connectCloudProvider,
                                onSaveToDownloads = viewModel::saveToDownloads,
                                onExportToUri = viewModel::exportToUri,
                                onDeleteDocument = viewModel::deleteDocument,
                                onPrintDocument = viewModel::openPrintPreviewForDocument,
                                onSendToTool = { doc, mode ->
                                    viewModel.launchVaultDocInTool(doc, mode)
                                    navigateToTab(ROUTE_STUDIO)
                                },
                                onGoToStudio = { navigateToTab(ROUTE_STUDIO) },
                                onGoToBatch = { navigateToTab(ROUTE_BATCH) }
                            )
                        }
                        composable(ROUTE_CLOUD) {
                            CloudScreen(
                                accounts = cloudAccounts,
                                vaultDocuments = vaultDocs,
                                uploadLogs = uploadLogs,
                                driveSyncState = driveSyncState,
                                onConnectProvider = viewModel::connectCloudProvider,
                                onDisconnectProvider = viewModel::disconnectCloudProvider,
                                onUploadDocumentToCloud = viewModel::uploadDocumentToCloud,
                                onToggleAutoSyncDrive = viewModel::setAutoSyncGoogleDrive,
                                onRefreshDriveSync = viewModel::refreshGoogleDriveSync,
                                onSyncAllDrive = { viewModel.syncAllWithGoogleDrive(downloadMissingFromCloud = true) },
                                onDownloadDriveFile = viewModel::downloadDriveFile,
                                onDeleteDriveFile = viewModel::deleteDriveBackup,
                                onOpenViewer = viewModel::openDocumentInViewer,
                                onOpenPrintPreview = viewModel::openPrintPreviewForDocument
                            )
                        }
                        composable(ROUTE_VIEWER) {
                            PdfViewerScreen(
                                state = viewerState,
                                cloudAccounts = cloudAccounts,
                                onBack = { navController.popBackStack() },
                                onSelectPage = viewModel::loadViewerPage,
                                onSetZoom = viewModel::setViewerZoom,
                                onSetThemeMode = viewModel::setViewerThemeMode,
                                onSearchQueryChange = viewModel::updateViewerSearchQuery,
                                onPasswordInputChange = viewModel::updateViewerPasswordInput,
                                onUnlockPdf = viewModel::unlockViewerDocument,
                                onUploadToCloud = viewModel::uploadDocumentToCloud,
                                onConnectCloud = viewModel::connectCloudProvider,
                                onSaveToDownloads = viewModel::saveToDownloads,
                                onOpenPrintPreview = viewModel::openPrintPreviewForDocument,
                                onLaunchToolForDocument = { doc, mode ->
                                    viewModel.launchVaultDocInTool(doc, mode)
                                    navController.popBackStack()
                                    navigateToTab(ROUTE_STUDIO)
                                },
                                onExtractAiNotes = { doc ->
                                    viewModel.addVaultPdfToNotes(doc)
                                    navController.popBackStack()
                                    navigateToTab(ROUTE_NOTES)
                                },
                                onOpenScanner = { tab -> viewModel.openScannerWorkspace(tab) },
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                            )
                        }
                        composable(ROUTE_PRINT_PREVIEW) {
                            PrintPreviewScreen(
                                state = printPreviewState,
                                onBack = { navController.popBackStack() },
                                onUpdateConfig = viewModel::updatePrintConfig,
                                onSelectSheet = viewModel::selectPrintSheet,
                                onSetZoom = viewModel::setPrintPreviewZoom,
                                onPasswordInputChange = viewModel::updatePrintPasswordInput,
                                onUnlockPdf = viewModel::unlockPrintPdf,
                                onPrintNow = viewModel::executeSystemPrint,
                                onSavePrintPdf = viewModel::savePrintReadyPdfToVault
                            )
                        }
                        composable(ROUTE_SCANNER) {
                            ScannerQrScreen(
                                state = scannerState,
                                metadata = metadataConfig,
                                security = securityConfig,
                                watermark = watermarkConfig,
                                barcodeScannerOptions = viewModel.barcodeScannerOptions,
                                onSelectTab = viewModel::selectScannerTab,
                                onBack = { navController.popBackStack() },
                                onDocumentScanCompleted = viewModel::onDocumentScanCompleted,
                                onAddFallbackPhotos = viewModel::addScannedPhotosFallback,
                                onRemoveScannedPage = viewModel::removeScannedPage,
                                onOutputNameChange = viewModel::updateScanOutputName,
                                onUpdateMetadata = viewModel::updateMetadataConfig,
                                onUpdateSecurity = viewModel::updateSecurityConfig,
                                onUpdateWatermark = viewModel::updateWatermarkConfig,
                                onSendToStudio = {
                                    viewModel.sendScannedPagesToStudio()
                                    navController.popBackStack()
                                    navigateToTab(ROUTE_STUDIO)
                                },
                                onSaveScannedPdf = viewModel::saveScannedDocumentAsPdf,
                                onClearScanSession = viewModel::clearScannedSession,
                                onOpenViewer = viewModel::openDocumentInViewer,
                                onPrintDocument = viewModel::openPrintPreviewForDocument,
                                onToggleTorch = viewModel::setScannerTorchEnabled,
                                onToggleCameraPaused = viewModel::toggleScannerCameraPaused,
                                onLiveBarcodesDetected = viewModel::handleLiveBarcodes,
                                onScanImageBarcode = viewModel::scanBarcodesFromImage,
                                onClearBarcodes = viewModel::clearScannedBarcodes,
                                onGenerateQrFromValue = viewModel::useScannedValueInGenerator,
                                onUpdateQrGenerator = viewModel::updateQrGenerator,
                                onSaveQrPng = { viewModel.saveGeneratedQrPng() },
                                onShareQrPng = {
                                    viewModel.saveGeneratedQrPng(
                                        contextForShare = context,
                                        shareAfterSave = true
                                    )
                                },
                                onSaveQrPdf = viewModel::saveGeneratedQrPdfToVault
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                autoSyncDrive = driveSyncState.autoSyncEnabled,
                                isGoogleDriveConnected = cloudAccounts.any {
                                    it.provider == CloudProvider.GOOGLE_DRIVE && it.isConnected
                                },
                                onBack = { navController.popBackStack() },
                                onUpdateSettings = viewModel::updateSettings,
                                onToggleAutoSyncDrive = viewModel::setAutoSyncGoogleDrive,
                                onConnectGoogleDrive = {
                                    viewModel.connectCloudProvider(CloudProvider.GOOGLE_DRIVE)
                                },
                                onResetDefaults = viewModel::resetSettings
                            )
                        }
                    }
                }
            }
        }
    }

    val dialogPair = configDialogData
    if (dialogPair != null) {
        ProviderConfigDialog(
            provider = dialogPair.first,
            instructions = dialogPair.second,
            onDismiss = { configDialogData = null }
        )
    }
}
