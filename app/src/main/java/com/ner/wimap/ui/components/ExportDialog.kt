package com.ner.wimap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ner.wimap.ui.viewmodel.ExportFormat
import com.ner.wimap.ui.viewmodel.ExportAction

@Composable
fun ExportFormatDialog(
    title: String = "Export WiFi Networks",
    onFormatAndActionSelected: (ExportFormat, ExportAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var showActionDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select the format you want to export:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExportFormatOption(
                    icon = Icons.Default.TableChart,
                    title = "CSV File",
                    description = "Spreadsheet format for data analysis",
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = {
                        selectedFormat = ExportFormat.CSV
                        showActionDialog = true
                    }
                )

                ExportFormatOption(
                    icon = Icons.Default.Map,
                    title = "Google Maps (KML)",
                    description = "View networks on Google Maps/Earth",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        selectedFormat = ExportFormat.GOOGLE_MAPS
                        showActionDialog = true
                    }
                )

                ExportFormatOption(
                    icon = Icons.Default.PictureAsPdf,
                    title = "PDF Report",
                    description = "Professional document with network details",
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        selectedFormat = ExportFormat.PDF
                        showActionDialog = true
                    }
                )
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )

    // Export Action Dialog
    if (showActionDialog && selectedFormat != null) {
        ExportActionDialog(
            format = selectedFormat!!,
            onActionSelected = { action ->
                onFormatAndActionSelected(selectedFormat!!, action)
                showActionDialog = false
            },
            onDismiss = {
                showActionDialog = false
                selectedFormat = null
            }
        )
    }
}

@Composable
private fun ExportActionDialog(
    format: ExportFormat,
    onActionSelected: (ExportAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Export Action",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "What would you like to do with the ${format.name} file?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ExportActionOption(
                    icon = Icons.Default.Save,
                    title = "Save to Device",
                    description = "Save file to your device storage",
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = { onActionSelected(ExportAction.SAVE_ONLY) }
                )

                ExportActionOption(
                    icon = Icons.Default.Share,
                    title = "Share File",
                    description = "Share immediately via apps",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onActionSelected(ExportAction.SHARE_ONLY) }
                )

                ExportActionOption(
                    icon = Icons.Default.CloudDownload,
                    title = "Save & Share",
                    description = "Save to device and share",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { onActionSelected(ExportAction.SAVE_AND_SHARE) }
                )
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun ExportFormatOption(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7F8C8D)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ExportActionOption(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7F8C8D)
                )
            }

            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Select",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}