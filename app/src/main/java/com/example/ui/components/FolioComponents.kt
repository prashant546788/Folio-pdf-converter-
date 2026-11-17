package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.CloudAccountState
import com.example.model.CloudProvider
import com.example.model.ConversionSettings
import com.example.model.MarginOption
import com.example.model.PageOrientation
import com.example.model.PageSizeOption
import com.example.model.PdfFontFamily
import com.example.model.PdfPageTheme
import com.example.model.PdfTextColorPreset
import com.example.model.QualityPreset
import com.example.model.SourceKind
import com.example.ui.theme.DropboxBlue
import com.example.ui.theme.GoogleDriveBlue
import com.example.ui.theme.OneDriveBlue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

fun formatTimestamp(ms: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(ms))
}

fun sharePdfFile(context: Context, filePath: String, fileName: String) {
    val file = File(filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share $fileName"))
}

fun shareImageFile(context: Context, filePath: String, fileName: String, mimeType: String) {
    val file = File(filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share $fileName"))
}

fun cloudProviderColor(provider: CloudProvider): Color = when (provider) {
    CloudProvider.GOOGLE_DRIVE -> GoogleDriveBlue
    CloudProvider.DROPBOX -> DropboxBlue
    CloudProvider.ONEDRIVE -> OneDriveBlue
}

@Composable
fun SourceKindBadge(kind: SourceKind, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        SourceKind.IMAGE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SourceKind.PDF -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        SourceKind.DOCX, SourceKind.HTML, SourceKind.MARKDOWN ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SourceKind.CSV, SourceKind.TEXT ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = kind.badge,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversionSettingsCard(
    settings: ConversionSettings,
    onUpdate: ((ConversionSettings) -> ConversionSettings) -> Unit,
    modifier: Modifier = Modifier,
    onOpenFullSettings: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Prominent OCR Switch always visible
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DocumentScanner,
                            contentDescription = "OCR text recognition",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Searchable OCR Text",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Extract & embed selectable text from scans/images",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = settings.enableOcr,
                    onCheckedChange = { checked -> onUpdate { it.copy(enableOcr = checked) } },
                    modifier = Modifier.testTag("ocr_toggle_switch")
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp)
                    .testTag("toggle_advanced_settings"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${settings.pageSize.label} · ${settings.fontFamily.label} · ${settings.textColor.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse options" else "Expand options"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text("Page Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageSizeOption.entries.forEach { option ->
                                FilterChip(
                                    selected = settings.pageSize == option,
                                    onClick = { onUpdate { it.copy(pageSize = option) } },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }

                    Column {
                        Text("Orientation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageOrientation.entries.forEach { option ->
                                FilterChip(
                                    selected = settings.orientation == option,
                                    onClick = { onUpdate { it.copy(orientation = option) } },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }

                    Column {
                        Text("Margins", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MarginOption.entries.forEach { option ->
                                FilterChip(
                                    selected = settings.margin == option,
                                    onClick = { onUpdate { it.copy(margin = option) } },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }

                    Column {
                        Text("Render Quality", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityPreset.entries.forEach { option ->
                                FilterChip(
                                    selected = settings.quality == option,
                                    onClick = { onUpdate { it.copy(quality = option) } },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Include Page Numbers", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.addPageNumbers,
                            onCheckedChange = { checked -> onUpdate { it.copy(addPageNumbers = checked) } }
                        )
                    }

                    Column {
                        Text("PDF Text Font", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfFontFamily.entries.forEach { family ->
                                FilterChip(
                                    selected = settings.fontFamily == family,
                                    onClick = { onUpdate { it.copy(fontFamily = family) } },
                                    label = { Text(family.label) }
                                )
                            }
                        }
                    }

                    Column {
                        Text("Text Ink Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfTextColorPreset.entries.forEach { ink ->
                                FilterChip(
                                    selected = settings.textColor == ink,
                                    onClick = { onUpdate { it.copy(textColor = ink) } },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color(ink.headingArgb))
                                        )
                                    },
                                    label = { Text(ink.label) }
                                )
                            }
                        }
                    }

                    Column {
                        Text("Page Background Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfPageTheme.entries.forEach { theme ->
                                FilterChip(
                                    selected = settings.pageTheme == theme,
                                    onClick = {
                                        onUpdate { current ->
                                            val adjustedInk = if (theme == PdfPageTheme.DARK_OBSIDIAN) {
                                                PdfTextColorPreset.SNOW_INK
                                            } else if (current.textColor == PdfTextColorPreset.SNOW_INK) {
                                                PdfTextColorPreset.CHARCOAL
                                            } else {
                                                current.textColor
                                            }
                                            current.copy(pageTheme = theme, textColor = adjustedInk)
                                        }
                                    },
                                    label = { Text(theme.label) }
                                )
                            }
                        }
                    }

                    if (onOpenFullSettings != null) {
                        TextButton(
                            onClick = onOpenFullSettings,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("open_full_settings_link")
                        ) {
                            Text("All Font Sizes, Watermark & PDF Settings →")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudUploadSheet(
    title: String,
    accounts: List<CloudAccountState>,
    onSelectProviderToUpload: (CloudProvider) -> Unit,
    onConnectProvider: (CloudProvider) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Choose a cloud storage service to save your PDF directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            accounts.forEach { account ->
                val accent = cloudProviderColor(account.provider)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cloud_sheet_${account.provider.name.lowercase()}"),
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
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (account.isConnected) Icons.Filled.CloudUpload else Icons.Filled.CloudQueue,
                                contentDescription = account.provider.displayName,
                                tint = accent
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.provider.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (account.isConnected) {
                                    account.accountEmail ?: account.accountName ?: "Connected"
                                } else {
                                    account.configHint ?: "Sign in to upload to ${account.provider.folderLabel}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (account.isConnected) {
                            Button(
                                onClick = {
                                    onSelectProviderToUpload(account.provider)
                                    onDismiss()
                                }
                            ) {
                                Text("Upload")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onConnectProvider(account.provider) }
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderConfigDialog(
    provider: CloudProvider,
    instructions: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                tint = cloudProviderColor(provider)
            )
        },
        title = {
            Text("${provider.displayName} App Key Required")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = instructions,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Open the Secrets panel in AI Studio, add the key, and rebuild to sign in directly via OAuth 2.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
