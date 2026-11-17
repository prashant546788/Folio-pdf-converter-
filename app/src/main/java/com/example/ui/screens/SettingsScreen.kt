package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ConversionSettings
import com.example.model.MarginOption
import com.example.model.PageOrientation
import com.example.model.PageSizeOption
import com.example.model.PdfFontFamily
import com.example.model.PdfFontSizePreset
import com.example.model.PdfLineSpacingPreset
import com.example.model.PdfPageTheme
import com.example.model.PdfTextAlignment
import com.example.model.PdfTextColorPreset
import com.example.model.QualityPreset

fun PdfFontFamily.toComposeFontFamily(): FontFamily {
    val resId = when (this) {
        PdfFontFamily.CLASSIC_SANS -> R.font.inter
        PdfFontFamily.EDITORIAL_SERIF -> R.font.merriweather
        PdfFontFamily.LITERARY_LORA -> R.font.lora
        PdfFontFamily.DISPLAY_PLAYFAIR -> R.font.playfair_display
        PdfFontFamily.MONO_CODE -> R.font.jetbrains_mono
    }
    return FontFamily(Font(resId))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: ConversionSettings,
    autoSyncDrive: Boolean,
    isGoogleDriveConnected: Boolean,
    onBack: () -> Unit,
    onUpdateSettings: ((ConversionSettings) -> ConversionSettings) -> Unit,
    onToggleAutoSyncDrive: (Boolean) -> Unit,
    onConnectGoogleDrive: () -> Unit,
    onResetDefaults: () -> Unit
) {
    BackHandler(onBack = onBack)

    val composeFont = remember(settings.fontFamily) {
        settings.fontFamily.toComposeFontFamily()
    }
    val effectiveColors = remember(settings.pageTheme, settings.textColor) {
        if (settings.pageTheme == PdfPageTheme.DARK_OBSIDIAN && settings.textColor != PdfTextColorPreset.SNOW_INK) {
            PdfTextColorPreset.SNOW_INK
        } else {
            settings.textColor
        }
    }
    val textAlign = when (settings.textAlignment) {
        PdfTextAlignment.LEFT -> TextAlign.Start
        PdfTextAlignment.CENTER -> TextAlign.Center
        PdfTextAlignment.RIGHT -> TextAlign.End
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PDF Settings & Styling", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Fonts, colors, themes, watermarks & defaults",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Live PDF Typography & Color Preview
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_live_preview_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Live PDF Style Preview", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${settings.fontFamily.label} · ${settings.fontSize.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(settings.pageTheme.bgArgb),
                                border = BorderStroke(1.dp, Color(settings.pageTheme.ruleArgb)),
                                shadowElevation = 3.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp)
                                ) {
                                    if (settings.showWatermark && settings.watermarkText.isNotBlank()) {
                                        Text(
                                            text = settings.watermarkText.trim().take(24).uppercase(),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(effectiveColors.mutedArgb).copy(alpha = 0.22f),
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .rotate(-24f)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (settings.includeHeaderTitle) {
                                            Text(
                                                text = "Folio_Document · Export Preview",
                                                fontFamily = composeFont,
                                                fontSize = 10.sp,
                                                color = Color(effectiveColors.mutedArgb)
                                            )
                                        }
                                        Text(
                                            text = "Executive Summary & Typography",
                                            fontFamily = composeFont,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (settings.fontSize.h1Sp).sp,
                                            color = Color(effectiveColors.headingArgb),
                                            textAlign = textAlign,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        HorizontalDivider(color = Color(settings.pageTheme.ruleArgb))
                                        val previewBodySize = settings.fontSize.bodySp
                                        val previewLineHeight = previewBodySize * settings.lineSpacing.multiplier * 1.25f
                                        Text(
                                            text = "Every generated document, Markdown note, DOCX file, and AI Study Guide uses this typeface, ink palette, page tint, and spacing.",
                                            fontFamily = composeFont,
                                            fontSize = previewBodySize.sp,
                                            lineHeight = previewLineHeight.sp,
                                            color = Color(effectiveColors.bodyArgb),
                                            textAlign = textAlign,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (settings.addPageNumbers) {
                                            Text(
                                                text = "Page 1",
                                                fontFamily = composeFont,
                                                fontSize = 10.sp,
                                                color = Color(effectiveColors.mutedArgb),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Font Family & Typography
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FontDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("PDF Text Font & Typography", style = MaterialTheme.typography.titleMedium)
                            }

                            Text("Font Family", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfFontFamily.entries.forEach { family ->
                                    val famFont = remember(family) { family.toComposeFontFamily() }
                                    FilterChip(
                                        selected = settings.fontFamily == family,
                                        onClick = { onUpdateSettings { it.copy(fontFamily = family) } },
                                        label = {
                                            Text(
                                                text = family.label,
                                                fontFamily = famFont,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.testTag("settings_font_${family.name.lowercase()}")
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FormatSize, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text("Font Size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PdfFontSizePreset.entries.forEach { sizePreset ->
                                    FilterChip(
                                        selected = settings.fontSize == sizePreset,
                                        onClick = { onUpdateSettings { it.copy(fontSize = sizePreset) } },
                                        label = { Text(sizePreset.label) },
                                        modifier = Modifier.testTag("settings_fontsize_${sizePreset.name.lowercase()}")
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FormatLineSpacing, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text("Line Spacing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PdfLineSpacingPreset.entries.forEach { spacing ->
                                    FilterChip(
                                        selected = settings.lineSpacing == spacing,
                                        onClick = { onUpdateSettings { it.copy(lineSpacing = spacing) } },
                                        label = { Text(spacing.label) }
                                    )
                                }
                            }

                            Text("Text Alignment", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PdfTextAlignment.entries.forEach { align ->
                                    val alignIcon = when (align) {
                                        PdfTextAlignment.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                        PdfTextAlignment.CENTER -> Icons.Filled.FormatAlignCenter
                                        PdfTextAlignment.RIGHT -> Icons.Filled.FormatAlignRight
                                    }
                                    FilterChip(
                                        selected = settings.textAlignment == align,
                                        onClick = { onUpdateSettings { it.copy(textAlignment = align) } },
                                        leadingIcon = { Icon(alignIcon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text(align.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Ink Colors & Page Background Themes
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("PDF Ink Colors & Page Theme", style = MaterialTheme.typography.titleMedium)
                            }

                            Text("Text & Heading Ink Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfTextColorPreset.entries.forEach { ink ->
                                    val selected = settings.textColor == ink
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .clickable { onUpdateSettings { it.copy(textColor = ink) } }
                                            .testTag("settings_ink_${ink.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(ink.headingArgb))
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (selected) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = if (ink == PdfTextColorPreset.SNOW_INK) Color.Black else Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                            Text(ink.label, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text("Page Background Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PdfPageTheme.entries.forEach { theme ->
                                    val selected = settings.pageTheme == theme
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .clickable {
                                                onUpdateSettings { current ->
                                                    val adjustedInk = if (theme == PdfPageTheme.DARK_OBSIDIAN) {
                                                        PdfTextColorPreset.SNOW_INK
                                                    } else if (current.textColor == PdfTextColorPreset.SNOW_INK) {
                                                        PdfTextColorPreset.CHARCOAL
                                                    } else {
                                                        current.textColor
                                                    }
                                                    current.copy(pageTheme = theme, textColor = adjustedInk)
                                                }
                                            }
                                            .testTag("settings_pagetheme_${theme.name.lowercase()}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(theme.bgArgb))
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            )
                                            Text(theme.label, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Watermark, Header, OCR & Page Layout Defaults
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Page Setup, OCR & Watermark", style = MaterialTheme.typography.titleMedium)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Embed Searchable OCR Text", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Recognize text in photos and scanned PDFs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = settings.enableOcr,
                                    onCheckedChange = { checked -> onUpdateSettings { it.copy(enableOcr = checked) } }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Show Document Title Header", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = settings.includeHeaderTitle,
                                    onCheckedChange = { checked -> onUpdateSettings { it.copy(includeHeaderTitle = checked) } }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Include Page Numbers in Footer", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = settings.addPageNumbers,
                                    onCheckedChange = { checked -> onUpdateSettings { it.copy(addPageNumbers = checked) } }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.BrandingWatermark, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Diagonal Page Watermark", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Stamp faint watermark text across generated PDF pages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = settings.showWatermark,
                                    onCheckedChange = { checked ->
                                        onUpdateSettings {
                                            it.copy(
                                                showWatermark = checked,
                                                watermarkText = if (checked && it.watermarkText.isBlank()) "CONFIDENTIAL" else it.watermarkText
                                            )
                                        }
                                    },
                                    modifier = Modifier.testTag("settings_watermark_switch")
                                )
                            }

                            AnimatedVisibility(visible = settings.showWatermark) {
                                OutlinedTextField(
                                    value = settings.watermarkText,
                                    onValueChange = { v -> onUpdateSettings { it.copy(watermarkText = v) } },
                                    label = { Text("Watermark Label") },
                                    placeholder = { Text("CONFIDENTIAL / DRAFT / DO NOT COPY") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_watermark_input")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text("Default Page Size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageSizeOption.entries.forEach { option ->
                                    FilterChip(
                                        selected = settings.pageSize == option,
                                        onClick = { onUpdateSettings { it.copy(pageSize = option) } },
                                        label = { Text(option.label) }
                                    )
                                }
                            }

                            Text("Default Orientation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PageOrientation.entries.forEach { option ->
                                    FilterChip(
                                        selected = settings.orientation == option,
                                        onClick = { onUpdateSettings { it.copy(orientation = option) } },
                                        label = { Text(option.label) }
                                    )
                                }
                            }

                            Text("Page Margins", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MarginOption.entries.forEach { option ->
                                    FilterChip(
                                        selected = settings.margin == option,
                                        onClick = { onUpdateSettings { it.copy(margin = option) } },
                                        label = { Text(option.label) }
                                    )
                                }
                            }

                            Text("Render Quality", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QualityPreset.entries.forEach { option ->
                                    FilterChip(
                                        selected = settings.quality == option,
                                        onClick = { onUpdateSettings { it.copy(quality = option) } },
                                        label = { Text(option.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Cloud Backup & Reset
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Auto-Backup PDFs to Google Drive", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = if (isGoogleDriveConnected) "Google Drive connected" else "Connect Google Drive to sync automatically",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = autoSyncDrive,
                                    onCheckedChange = { checked ->
                                        if (checked && !isGoogleDriveConnected) {
                                            onConnectGoogleDrive()
                                        } else {
                                            onToggleAutoSyncDrive(checked)
                                        }
                                    }
                                )
                            }

                            OutlinedButton(
                                onClick = onResetDefaults,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_reset_btn")
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Reset All PDF Settings to Defaults")
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
