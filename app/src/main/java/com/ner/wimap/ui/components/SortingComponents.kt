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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.ner.wimap.presentation.viewmodel.SortingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortingControl(
    currentSortingMode: SortingMode,
    onSortingModeChanged: (SortingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .clickable { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = getSortingIcon(currentSortingMode),
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(9.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sort by",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = getSortingDisplayText(currentSortingMode),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(8.dp)
                    )
                }
            }
        }
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            SortingMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = getSortingIcon(mode),
                                contentDescription = null,
                                tint = if (mode == currentSortingMode) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = getSortingDisplayText(mode),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (mode == currentSortingMode) 
                                        FontWeight.SemiBold 
                                    else 
                                        FontWeight.Normal
                                ),
                                color = if (mode == currentSortingMode) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    onClick = {
                        onSortingModeChanged(mode)
                        expanded = false
                    },
                    modifier = Modifier.background(
                        if (mode == currentSortingMode) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else 
                            Color.Transparent
                    )
                )
            }
        }
    }
}

/**
 * Get the appropriate icon for each sorting mode
 */
fun getSortingIcon(mode: SortingMode): androidx.compose.ui.graphics.vector.ImageVector {
    return when (mode) {
        SortingMode.LAST_SEEN -> Icons.Default.Schedule
        SortingMode.SIGNAL_STRENGTH -> Icons.Default.SignalWifi4Bar
        SortingMode.SSID_ALPHABETICAL -> Icons.Default.SortByAlpha
    }
}

/**
 * Get the display text for each sorting mode
 */
fun getSortingDisplayText(mode: SortingMode): String {
    return when (mode) {
        SortingMode.LAST_SEEN -> "Last Seen"
        SortingMode.SIGNAL_STRENGTH -> "Signal Strength"
        SortingMode.SSID_ALPHABETICAL -> "Name (A-Z)"
    }
}