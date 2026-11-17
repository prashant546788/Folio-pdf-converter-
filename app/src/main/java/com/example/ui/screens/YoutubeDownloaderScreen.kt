package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.video.DownloadedVideoResult
import com.example.data.video.YouTubeVideoInfo
import com.example.ui.components.formatFileSize
import com.example.ui.components.shareImageFile
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeDownloaderScreen(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    videoInfo: YouTubeVideoInfo?,
    selectedFormat: String,
    onFormatChange: (String) -> Unit,
    isProcessing: Boolean,
    progressMessage: String,
    downloadResult: DownloadedVideoResult?,
    onFetchInfo: () -> Unit,
    onDownload: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val formats = listOf("1080p MP4", "720p MP4", "480p MP4", "Audio MP3")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "YouTube Downloader",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            }
        }

        item {
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
                        text = "Enter YouTube URL or Video ID",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = onUrlChange,
                            label = { Text("https://youtube.com/watch?v=...") },
                            singleLine = true,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("youtube_url_input")
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.getText()?.text?.let { clipText ->
                                    if (clipText.isNotBlank()) onUrlChange(clipText)
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste from clipboard")
                        }
                    }

                    Button(
                        onClick = onFetchInfo,
                        enabled = urlInput.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("youtube_fetch_btn")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Fetch Video Details")
                    }
                }
            }
        }

        if (videoInfo != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = videoInfo.thumbnailUrl,
                            contentDescription = videoInfo.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = videoInfo.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Channel: ${videoInfo.authorName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Select Quality / Format",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            formats.forEach { fmt ->
                                FilterChip(
                                    selected = selectedFormat == fmt,
                                    onClick = { onFormatChange(fmt) },
                                    label = { Text(fmt) },
                                    enabled = !isProcessing
                                )
                            }
                        }

                        if (isProcessing) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = progressMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Button(
                            onClick = onDownload,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("youtube_download_btn")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Download $selectedFormat")
                        }
                    }
                }
            }
        }

        if (downloadResult != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Download Successful!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "File: ${downloadResult.fileName}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Size: ${formatFileSize(downloadResult.fileSizeBytes)} · Format: ${downloadResult.format}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        shareImageFile(
                                            context = context,
                                            filePath = downloadResult.filePath,
                                            fileName = downloadResult.fileName,
                                            mimeType = if (downloadResult.format.contains("MP3")) "audio/mpeg" else "video/mp4"
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }
}
