package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PdfMetadataConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfMetadataSettingsCard(
    metadata: PdfMetadataConfig,
    onUpdate: ((PdfMetadataConfig) -> PdfMetadataConfig) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "PDF Document Metadata",
    subtitle: String = "Set Title, Author, Subject & Keywords before finalizing",
    suggestedTitle: String = "",
    alwaysExpanded: Boolean = false
) {
    var expanded by remember(alwaysExpanded) {
        mutableStateOf(alwaysExpanded || metadata.hasAnyCustomMetadata())
    }
    val hasCustom = metadata.hasAnyCustomMetadata()
    val isBodyVisible = alwaysExpanded || expanded

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (hasCustom) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)
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
                    .testTag("pdf_metadata_header"),
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
                                if (hasCustom) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Badge,
                            contentDescription = "PDF Metadata",
                            tint = if (hasCustom) {
                                MaterialTheme.colorScheme.onTertiaryContainer
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
                            text = if (hasCustom) metadata.buildSummary() else subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!alwaysExpanded) {
                    Spacer(Modifier.width(8.dp))
                    if (hasCustom) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Edited",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = if (isBodyVisible) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isBodyVisible) "Collapse metadata fields" else "Expand metadata fields",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = isBodyVisible) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = metadata.title,
                        onValueChange = { value -> onUpdate { it.copy(title = value) } },
                        label = { Text("Document Title") },
                        placeholder = { Text("e.g. Q4 Financial Report 2026") },
                        leadingIcon = { Icon(Icons.Filled.Title, contentDescription = null) },
                        trailingIcon = {
                            if (suggestedTitle.isNotBlank() && metadata.title.isBlank()) {
                                TextButton(
                                    onClick = { onUpdate { it.copy(title = suggestedTitle.removeSuffix(".pdf")) } }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Use Name")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_meta_title_input")
                    )

                    OutlinedTextField(
                        value = metadata.author,
                        onValueChange = { value -> onUpdate { it.copy(author = value) } },
                        label = { Text("Author") },
                        placeholder = { Text("e.g. Alex Rivera") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_meta_author_input")
                    )

                    OutlinedTextField(
                        value = metadata.subject,
                        onValueChange = { value -> onUpdate { it.copy(subject = value) } },
                        label = { Text("Subject / Description") },
                        placeholder = { Text("e.g. Annual Audit & Budget Summary") },
                        leadingIcon = { Icon(Icons.Filled.Subject, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_meta_subject_input")
                    )

                    OutlinedTextField(
                        value = metadata.keywords,
                        onValueChange = { value -> onUpdate { it.copy(keywords = value) } },
                        label = { Text("Keywords (comma-separated)") },
                        placeholder = { Text("report, finance, 2026, approved") },
                        leadingIcon = { Icon(Icons.Filled.Sell, contentDescription = null) },
                        supportingText = { Text("Separate tags with commas for PDF indexers and search tools.") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_meta_keywords_input")
                    )

                    val keywordList = metadata.parsedKeywordList()
                    if (keywordList.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            keywordList.forEachIndexed { idx, tag ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        val remaining = keywordList.filterIndexed { i, _ -> i != idx }
                                        onUpdate { it.copy(keywords = remaining.joinToString(", ")) }
                                    },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Remove keyword $tag",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = metadata.creator,
                            onValueChange = { value -> onUpdate { it.copy(creator = value) } },
                            label = { Text("Creator Tool") },
                            placeholder = { Text("Folio PDF") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pdf_meta_creator_input")
                        )
                        if (hasCustom) {
                            TextButton(
                                onClick = { onUpdate { PdfMetadataConfig() } },
                                modifier = Modifier.testTag("pdf_meta_clear_btn")
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }
    }
}
