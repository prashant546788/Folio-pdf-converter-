package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.PdfWatermarkConfig
import com.example.model.WatermarkColorPreset
import com.example.model.WatermarkPlacement
import com.example.model.WatermarkTextSize
import com.example.model.WatermarkType
import kotlin.math.abs
import kotlin.math.roundToInt

private val COPYRIGHT_PRESETS = listOf(
    "© CONFIDENTIAL",
    "DO NOT COPY",
    "© All Rights Reserved",
    "DRAFT",
    "SAMPLE"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfWatermarkSettingsCard(
    watermark: PdfWatermarkConfig,
    onUpdate: ((PdfWatermarkConfig) -> PdfWatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Copyright Watermark",
    subtitle: String = "Add translucent text or logo stamps to protect PDF pages",
    alwaysExpanded: Boolean = false,
    showPageRange: Boolean = false,
    pageCountHint: Int = 0
) {
    val context = LocalContext.current
    var expanded by remember(alwaysExpanded) {
        mutableStateOf(alwaysExpanded || watermark.enabled)
    }
    val isActive = watermark.isActive()
    val isBodyVisible = alwaysExpanded || watermark.enabled || expanded

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            var displayName = uri.lastPathSegment ?: "Watermark_Logo"
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val resolved = cursor.getString(idx)
                            if (!resolved.isNullOrBlank()) displayName = resolved
                        }
                    }
                }
            }
            onUpdate {
                it.copy(
                    enabled = true,
                    type = WatermarkType.IMAGE,
                    imageUriString = uri.toString(),
                    imageDisplayName = displayName
                )
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (alwaysExpanded || isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!alwaysExpanded) {
                            Modifier.clickable { expanded = !expanded }
                        } else Modifier
                    )
                    .testTag("pdf_watermark_header"),
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
                            .background(
                                if (alwaysExpanded || watermark.enabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Approval,
                            contentDescription = "PDF Watermark",
                            tint = if (alwaysExpanded || watermark.enabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = if (watermark.enabled || alwaysExpanded) {
                                watermark.copy(enabled = true).buildSummary()
                            } else {
                                subtitle
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!alwaysExpanded) {
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = watermark.enabled,
                        onCheckedChange = { checked ->
                            expanded = checked
                            onUpdate { it.copy(enabled = checked) }
                        },
                        modifier = Modifier.testTag("pdf_watermark_enable_switch")
                    )
                    Icon(
                        imageVector = if (isBodyVisible) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isBodyVisible) "Collapse watermark options" else "Expand watermark options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isBodyVisible) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Live Mini-Page Preview
                    WatermarkMiniPagePreview(watermark = watermark)

                    // Watermark Type Selector
                    Text("Watermark Mode", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = watermark.type == WatermarkType.TEXT,
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        enabled = if (alwaysExpanded) it.enabled else true,
                                        type = WatermarkType.TEXT
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(WatermarkType.TEXT.label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("watermark_type_text_chip")
                        )
                        FilterChip(
                            selected = watermark.type == WatermarkType.IMAGE,
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        enabled = if (alwaysExpanded) it.enabled else true,
                                        type = WatermarkType.IMAGE
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(WatermarkType.IMAGE.label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("watermark_type_image_chip")
                        )
                    }

                    if (watermark.type == WatermarkType.TEXT) {
                        OutlinedTextField(
                            value = watermark.text,
                            onValueChange = { value ->
                                onUpdate { it.copy(text = value) }
                            },
                            label = { Text("Watermark Text") },
                            placeholder = { Text("e.g. © 2026 Company Name · Confidential") },
                            singleLine = true,
                            trailingIcon = {
                                if (watermark.text.isNotEmpty()) {
                                    IconButton(onClick = { onUpdate { it.copy(text = "") } }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear text")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("watermark_text_input")
                        )

                        Text("Quick Copyright Stamps", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            COPYRIGHT_PRESETS.forEach { preset ->
                                FilterChip(
                                    selected = watermark.text.equals(preset, ignoreCase = true),
                                    onClick = { onUpdate { it.copy(text = preset) } },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Text("Stamp Color", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WatermarkColorPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = watermark.color == preset,
                                    onClick = { onUpdate { it.copy(color = preset) } },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.argb))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                    shape = CircleShape
                                                )
                                        )
                                    },
                                    label = { Text(preset.label) }
                                )
                            }
                        }

                        Text("Text Size", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WatermarkTextSize.entries.forEach { sz ->
                                FilterChip(
                                    selected = watermark.textSize == sz,
                                    onClick = { onUpdate { it.copy(textSize = sz) } },
                                    label = { Text(sz.label) }
                                )
                            }
                        }
                    } else {
                        // Image / Logo watermark picker
                        if (watermark.imageUriString != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = Uri.parse(watermark.imageUriString),
                                    contentDescription = "Selected watermark image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .padding(4.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = watermark.imageDisplayName.ifBlank { "Selected Logo / Stamp" },
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "PNG alpha transparency preserved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onUpdate { it.copy(imageUriString = null, imageDisplayName = "") }
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear image")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (watermark.imageUriString == null) {
                                Button(
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("watermark_pick_image_btn")
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Select Logo or Signature Image")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        imagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("watermark_change_image_btn")
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Change Watermark Image")
                                }
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Image Size", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "${(watermark.imageWidthFraction * 100).roundToInt()}% page width",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = watermark.imageWidthFraction,
                                onValueChange = { value ->
                                    onUpdate { it.copy(imageWidthFraction = value.coerceIn(0.12f, 0.80f)) }
                                },
                                valueRange = 0.12f..0.80f,
                                modifier = Modifier.testTag("watermark_image_size_slider")
                            )
                        }
                    }

                    // Placement Presets
                    Text("Page Placement", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WatermarkPlacement.entries.forEach { place ->
                            FilterChip(
                                selected = watermark.placement == place,
                                onClick = {
                                    onUpdate {
                                        val suggestedAngle = when (place) {
                                            WatermarkPlacement.DIAGONAL_CENTER -> -35f
                                            WatermarkPlacement.TILED_GRID -> -30f
                                            WatermarkPlacement.TOP_HEADER,
                                            WatermarkPlacement.BOTTOM_FOOTER,
                                            WatermarkPlacement.BOTTOM_RIGHT -> 0f
                                            WatermarkPlacement.CENTER -> 0f
                                        }
                                        it.copy(placement = place, rotationDegrees = suggestedAngle)
                                    }
                                },
                                label = { Text(place.label) },
                                modifier = Modifier.testTag("watermark_place_${place.name.lowercase()}")
                            )
                        }
                    }

                    // Opacity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Opacity", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${(watermark.opacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = watermark.opacity,
                            onValueChange = { value ->
                                onUpdate { it.copy(opacity = value.coerceIn(0.08f, 0.90f)) }
                            },
                            valueRange = 0.08f..0.90f,
                            modifier = Modifier.testTag("watermark_opacity_slider")
                        )
                    }

                    // Angle Selector (shown unless locked to Header/Footer)
                    if (watermark.placement != WatermarkPlacement.TOP_HEADER &&
                        watermark.placement != WatermarkPlacement.BOTTOM_FOOTER
                    ) {
                        Text("Rotation Angle", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-45f, -35f, 0f, 35f).forEach { deg ->
                                val selected = abs(watermark.rotationDegrees - deg) < 2f
                                FilterChip(
                                    selected = selected,
                                    onClick = { onUpdate { it.copy(rotationDegrees = deg) } },
                                    label = {
                                        val txt = if (deg > 0f) "+${deg.roundToInt()}°" else "${deg.roundToInt()}°"
                                        Text(txt)
                                    }
                                )
                            }
                        }
                    }

                    if (showPageRange) {
                        OutlinedTextField(
                            value = watermark.pageRangeExpr,
                            onValueChange = { value ->
                                onUpdate { it.copy(pageRangeExpr = value) }
                            },
                            label = { Text("Pages to Watermark") },
                            placeholder = { Text("all or 1-3, 5") },
                            supportingText = {
                                Text(
                                    if (pageCountHint > 0) {
                                        "Enter \"all\" or page numbers between 1 and $pageCountHint (e.g. 1-3, 5)"
                                    } else {
                                        "Enter \"all\" or ranges like 1-3, 5"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("watermark_page_range_input")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatermarkMiniPagePreview(watermark: PdfWatermarkConfig) {
    val stampColor = Color(watermark.color.argb)
    val previewAlpha = watermark.opacity.coerceIn(0.10f, 0.92f)
    val previewRotation = when (watermark.placement) {
        WatermarkPlacement.TOP_HEADER,
        WatermarkPlacement.BOTTOM_FOOTER -> 0f
        else -> watermark.rotationDegrees
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Live Watermark Preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier
                .width(118.dp)
                .aspectRatio(0.707f),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                // Faint document placeholder lines
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(9) { idx ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (idx % 3 == 2) 0.65f else 0.92f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFE5E7EB))
                        )
                    }
                }

                // Overlay stamp preview
                when (watermark.placement) {
                    WatermarkPlacement.TILED_GRID -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(3) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    repeat(3) {
                                        PreviewStampElement(
                                            watermark = watermark,
                                            stampColor = stampColor,
                                            alphaValue = previewAlpha,
                                            rotation = previewRotation,
                                            compactScale = 0.45f
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val align = when (watermark.placement) {
                            WatermarkPlacement.TOP_HEADER -> Alignment.TopCenter
                            WatermarkPlacement.BOTTOM_FOOTER -> Alignment.BottomCenter
                            WatermarkPlacement.BOTTOM_RIGHT -> Alignment.BottomEnd
                            else -> Alignment.Center
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = align
                        ) {
                            PreviewStampElement(
                                watermark = watermark,
                                stampColor = stampColor,
                                alphaValue = previewAlpha,
                                rotation = previewRotation,
                                compactScale = if (
                                    watermark.placement == WatermarkPlacement.TOP_HEADER ||
                                    watermark.placement == WatermarkPlacement.BOTTOM_FOOTER ||
                                    watermark.placement == WatermarkPlacement.BOTTOM_RIGHT
                                ) 0.65f else 1f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewStampElement(
    watermark: PdfWatermarkConfig,
    stampColor: Color,
    alphaValue: Float,
    rotation: Float,
    compactScale: Float
) {
    if (watermark.type == WatermarkType.TEXT) {
        val label = watermark.text.trim().ifBlank { "© STAMP" }
        val baseSp = when (watermark.textSize) {
            WatermarkTextSize.SMALL -> 8f
            WatermarkTextSize.MEDIUM -> 10.5f
            WatermarkTextSize.LARGE -> 13f
            WatermarkTextSize.EXTRA_LARGE -> 15.5f
        } * compactScale
        Text(
            text = label,
            color = stampColor.copy(alpha = alphaValue),
            fontSize = baseSp.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    } else {
        val sizeDp = (46f * watermark.imageWidthFraction.coerceIn(0.15f, 0.8f) * 2f * compactScale)
            .coerceIn(14f, 64f).dp
        if (watermark.imageUriString != null) {
            AsyncImage(
                model = Uri.parse(watermark.imageUriString),
                contentDescription = "Watermark preview image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(sizeDp)
                    .alpha(alphaValue)
                    .graphicsLayer { rotationZ = rotation }
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(sizeDp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}
