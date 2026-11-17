package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.PdfEncryptionStrength
import com.example.model.PdfSecurityConfig

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfSecuritySettingsCard(
    security: PdfSecurityConfig,
    onUpdate: ((PdfSecurityConfig) -> PdfSecurityConfig) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Password & Permissions",
    subtitle: String = "Encrypt generated PDFs and control printing or copying"
) {
    var showUserPwd by remember { mutableStateOf(false) }
    var showOwnerPwd by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (security.enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(
                                if (security.enabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (security.enabled) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = "PDF Security",
                            tint = if (security.enabled) {
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
                            text = if (security.hasAnyRestrictionOrPassword()) {
                                security.buildSummary()
                            } else {
                                subtitle
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = security.enabled,
                    onCheckedChange = { checked -> onUpdate { it.copy(enabled = checked) } },
                    modifier = Modifier.testTag("pdf_security_switch")
                )
            }

            AnimatedVisibility(visible = security.enabled) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = security.userPassword,
                        onValueChange = { value -> onUpdate { it.copy(userPassword = value) } },
                        label = { Text("Open Password (Optional)") },
                        placeholder = { Text("Require password to view PDF") },
                        supportingText = {
                            Text("Leave blank if you only want to restrict printing or copying without a view prompt.")
                        },
                        singleLine = true,
                        visualTransformation = if (showUserPwd) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showUserPwd = !showUserPwd }) {
                                Icon(
                                    imageVector = if (showUserPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showUserPwd) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_user_password_input")
                    )

                    OutlinedTextField(
                        value = security.ownerPassword,
                        onValueChange = { value -> onUpdate { it.copy(ownerPassword = value) } },
                        label = { Text("Owner Permissions Password (Optional)") },
                        placeholder = { Text("Auto-generated if blank") },
                        supportingText = {
                            Text("Master password required to override PDF restrictions.")
                        },
                        singleLine = true,
                        visualTransformation = if (showOwnerPwd) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showOwnerPwd = !showOwnerPwd }) {
                                Icon(
                                    imageVector = if (showOwnerPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showOwnerPwd) "Hide owner password" else "Show owner password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_owner_password_input")
                    )

                    Text(
                        text = "Document Permissions",
                        style = MaterialTheme.typography.labelLarge
                    )

                    PermissionToggleRow(
                        icon = Icons.Filled.Print,
                        title = "Allow Printing",
                        subtitle = if (security.allowPrinting) "Readers can print this PDF" else "Printing disabled",
                        checked = security.allowPrinting,
                        onCheckedChange = { checked -> onUpdate { it.copy(allowPrinting = checked) } },
                        testTag = "perm_print_switch"
                    )

                    PermissionToggleRow(
                        icon = Icons.Filled.ContentCopy,
                        title = "Allow Copying Text & Images",
                        subtitle = if (security.allowCopying) "Text selection & copying allowed" else "Content extraction blocked",
                        checked = security.allowCopying,
                        onCheckedChange = { checked -> onUpdate { it.copy(allowCopying = checked) } },
                        testTag = "perm_copy_switch"
                    )

                    PermissionToggleRow(
                        icon = Icons.Filled.Edit,
                        title = "Allow Modifying Content",
                        subtitle = if (security.allowModifying) "Document editing permitted" else "Read-only content",
                        checked = security.allowModifying,
                        onCheckedChange = { checked -> onUpdate { it.copy(allowModifying = checked) } },
                        testTag = "perm_modify_switch"
                    )

                    PermissionToggleRow(
                        icon = Icons.Filled.Comment,
                        title = "Allow Annotations & Forms",
                        subtitle = if (security.allowAnnotations) "Comments & form fill allowed" else "Annotations disabled",
                        checked = security.allowAnnotations,
                        onCheckedChange = { checked -> onUpdate { it.copy(allowAnnotations = checked) } },
                        testTag = "perm_annotate_switch"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Encryption Strength",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfEncryptionStrength.entries.forEach { option ->
                                FilterChip(
                                    selected = security.strength == option,
                                    onClick = { onUpdate { it.copy(strength = option) } },
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
