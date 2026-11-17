package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ConvertedPdfEntity
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.CloudUploadRecord
import com.example.model.RemoteDrivePdfItem
import com.example.ui.DriveSyncUiState
import com.example.ui.components.StatusPill
import com.example.ui.components.cloudProviderColor
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    accounts: List<CloudAccountState>,
    vaultDocuments: List<ConvertedPdfEntity>,
    uploadLogs: List<CloudUploadRecord>,
    driveSyncState: DriveSyncUiState,
    onConnectProvider: (CloudProvider) -> Unit,
    onDisconnectProvider: (CloudProvider) -> Unit,
    onUploadDocumentToCloud: (ConvertedPdfEntity, CloudProvider) -> Unit,
    onToggleAutoSyncDrive: (Boolean) -> Unit,
    onRefreshDriveSync: () -> Unit,
    onSyncAllDrive: () -> Unit,
    onDownloadDriveFile: (RemoteDrivePdfItem) -> Unit,
    onDeleteDriveFile: (RemoteDrivePdfItem) -> Unit,
    onOpenViewer: (Long) -> Unit,
    onOpenPrintPreview: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var pickDocForProvider by remember { mutableStateOf<CloudProvider?>(null) }
    val googleConnected = accounts.firstOrNull { it.provider == CloudProvider.GOOGLE_DRIVE }?.isConnected == true

    LaunchedEffect(googleConnected) {
        if (googleConnected && driveSyncState.remoteFiles.isEmpty() && !driveSyncState.isRefreshing) {
            onRefreshDriveSync()
        }
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Cloud Sync & Storage",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Back up PDFs to Google Drive for multi-device access, or connect Dropbox and OneDrive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (googleConnected) {
                item {
                    GoogleDriveSyncCard(
                        driveSyncState = driveSyncState,
                        localPdfCount = vaultDocuments.size,
                        onToggleAutoSync = onToggleAutoSyncDrive,
                        onRefresh = onRefreshDriveSync,
                        onSyncAll = onSyncAllDrive
                    )
                }

                if (driveSyncState.remoteFiles.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Google Drive Backups (${driveSyncState.remoteFiles.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "My Drive / FolioPDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(driveSyncState.remoteFiles, key = { it.driveFileId }) { remote ->
                        RemoteDriveBackupRow(
                            remote = remote,
                            isDownloading = driveSyncState.downloadingDriveIds.contains(remote.driveFileId),
                            onDownload = { onDownloadDriveFile(remote) },
                            onDelete = { onDeleteDriveFile(remote) },
                            onOpenViewer = onOpenViewer,
                            onOpenPrintPreview = onOpenPrintPreview
                        )
                    }
                }
            }

            items(accounts, key = { it.provider.name }) { account ->
                CloudProviderCard(
                    account = account,
                    hasVaultDocs = vaultDocuments.isNotEmpty(),
                    onConnect = { onConnectProvider(account.provider) },
                    onDisconnect = { onDisconnectProvider(account.provider) },
                    onPickAndUpload = { pickDocForProvider = account.provider }
                )
            }

            if (uploadLogs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Recent Cloud Transfers",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(uploadLogs, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                                imageVector = if (log.success) Icons.Filled.CloudDone else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (log.success) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${log.fileName} → ${log.provider.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${log.message} · ${formatTimestamp(log.uploadedAtMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val targetProvider = pickDocForProvider
    if (targetProvider != null) {
        ModalBottomSheet(onDismissRequest = { pickDocForProvider = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Upload PDF to ${targetProvider.displayName}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Select a converted PDF from your Vault to upload to ${targetProvider.folderLabel}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                vaultDocuments.take(15).forEach { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUploadDocumentToCloud(doc, targetProvider)
                                pickDocForProvider = null
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
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = "Upload",
                                tint = cloudProviderColor(targetProvider)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudProviderCard(
    account: CloudAccountState,
    hasVaultDocs: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPickAndUpload: () -> Unit
) {
    val accent = cloudProviderColor(account.provider)
    val icon = when (account.provider) {
        CloudProvider.GOOGLE_DRIVE -> Icons.Filled.AddToDrive
        CloudProvider.DROPBOX -> Icons.Filled.FolderShared
        CloudProvider.ONEDRIVE -> Icons.Filled.CloudQueue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cloud_card_${account.provider.name.lowercase()}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(
            width = if (account.isConnected) 1.5.dp else 1.dp,
            color = if (account.isConnected) accent.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = account.provider.displayName,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.provider.displayName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (account.isConnected) {
                            account.accountEmail ?: account.accountName ?: "Authenticated"
                        } else {
                            account.provider.subtitle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(
                    text = if (account.isConnected) "Connected" else "Offline",
                    containerColor = if (account.isConnected) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (account.isConnected) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    icon = if (account.isConnected) Icons.Filled.CheckCircle else Icons.Filled.CloudOff
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Destination: ${account.provider.folderLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!account.isConfigured && !account.isConnected) {
                        Text(
                            text = "Key Required",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (account.configHint != null && !account.isConnected) {
                Text(
                    text = account.configHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (account.isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickAndUpload,
                        enabled = hasVaultDocs,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cloud_upload_btn_${account.provider.name.lowercase()}"),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (hasVaultDocs) "Upload a PDF" else "No PDFs in Vault")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.LinkOff, contentDescription = "Disconnect", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign Out")
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cloud_connect_btn_${account.provider.name.lowercase()}")
                ) {
                    Icon(Icons.Filled.Cloud, contentDescription = null, tint = accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Authenticate ${account.provider.displayName}")
                }
            }
        }
    }
}

@Composable
private fun GoogleDriveSyncCard(
    driveSyncState: DriveSyncUiState,
    localPdfCount: Int,
    onToggleAutoSync: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onSyncAll: () -> Unit
) {
    val accent = cloudProviderColor(CloudProvider.GOOGLE_DRIVE)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google_drive_sync_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudSync,
                        contentDescription = "Google Drive multi-device sync",
                        tint = accent
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cross-Device Drive Sync",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "$localPdfCount local PDF(s) · ${driveSyncState.remoteFiles.size} in Drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !driveSyncState.isRefreshing && !driveSyncState.isSyncing,
                    modifier = Modifier.testTag("drive_refresh_btn")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Google Drive files")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-backup new PDFs",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Automatically mirror newly created or edited PDFs to Google Drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = driveSyncState.autoSyncEnabled,
                    onCheckedChange = onToggleAutoSync,
                    modifier = Modifier.testTag("drive_auto_sync_switch")
                )
            }

            if (driveSyncState.isSyncing || driveSyncState.isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val statusText = driveSyncState.statusSummary ?: if (driveSyncState.lastSyncedAtMs > 0L) {
                "Last synced ${formatTimestamp(driveSyncState.lastSyncedAtMs)}"
            } else {
                "Back up local PDFs and restore files from your other devices."
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onSyncAll,
                enabled = !driveSyncState.isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("drive_sync_now_btn")
            ) {
                Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (driveSyncState.isSyncing) "Syncing Devices..." else "Sync Now (Backup & Restore)")
            }
        }
    }
}

@Composable
private fun RemoteDriveBackupRow(
    remote: RemoteDrivePdfItem,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onOpenViewer: (Long) -> Unit,
    onOpenPrintPreview: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drive_remote_item_${remote.driveFileId}"),
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
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = remote.fileName,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = remote.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            if (remote.pageCountHint > 0) append("${remote.pageCountHint}p · ")
                            append(formatFileSize(remote.sizeBytes))
                            append(" · ")
                            append(formatTimestamp(remote.modifiedTimeMs))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (remote.isSyncedLocally) "Synced" else "Cloud Only",
                    containerColor = if (remote.isSyncedLocally) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (remote.isSyncedLocally) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val localDocId = remote.localDocumentId
                if (localDocId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { onOpenViewer(localDocId) },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open")
                        }
                        OutlinedButton(
                            onClick = { onOpenPrintPreview(localDocId) },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Print")
                        }
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        modifier = Modifier.height(40.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (isDownloading) "Restoring..." else "Restore to Device")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete from Google Drive")
                }
            }
        }
    }
}

