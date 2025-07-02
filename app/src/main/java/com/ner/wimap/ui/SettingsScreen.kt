package com.ner.wimap.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.ner.wimap.R

@Composable
fun SettingsScreen(
    ssidFilter: String,
    onSsidFilterChange: (String) -> Unit,
    securityFilter: Set<String>,
    onSecurityFilterChange: (Set<String>) -> Unit,
    rssiThreshold: String,
    onRssiThresholdChange: (String) -> Unit,
    bssidFilter: String = "",
    onBssidFilterChange: (String) -> Unit = {},
    availableSecurityTypes: List<String> = emptyList(),
    passwords: List<String>,
    onAddPassword: (String) -> Unit,
    onRemovePassword: (String) -> Unit,
    maxRetries: Int,
    onMaxRetriesChange: (Int) -> Unit,
    connectionTimeoutSeconds: Int,
    onConnectionTimeoutChange: (Int) -> Unit,
    rssiThresholdForConnection: Int,
    onRssiThresholdForConnectionChange: (Int) -> Unit,
    hideNetworksUnseenForSeconds: Int,
    onHideNetworksUnseenForSecondsChange: (Int) -> Unit,
    isBackgroundScanningEnabled: Boolean = false,
    onToggleBackgroundScanning: (Boolean) -> Unit = {},
    backgroundScanIntervalMinutes: Int = 15,
    onSetBackgroundScanInterval: (Int) -> Unit = {},
    isAutoUploadEnabled: Boolean = true,
    onToggleAutoUpload: (Boolean) -> Unit = {},
    onClearAllData: () -> Unit,
    onBack: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Modern Material 3 Settings Top Bar
        ModernSettingsTopBar(onBack = onBack)

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Background Scanning Section - MOVED TO TOP
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.Autorenew,
                    title = "Background Scanning",
                    subtitle = "Automatic network discovery when app is minimized",
                    content = {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Enable/Disable Background Scanning
                            SettingItem(
                                icon = Icons.Default.Autorenew,
                                title = "Enable Background Scanning",
                                description = "Continuously discover new networks in background"
                            ) {
                                Switch(
                                    checked = isBackgroundScanningEnabled,
                                    onCheckedChange = onToggleBackgroundScanning
                                )
                            }

                            if (isBackgroundScanningEnabled) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                
                                // Scan Interval
                                SettingItem(
                                    icon = Icons.Default.Schedule,
                                    title = "Scan Frequency",
                                    description = "How often to scan for networks"
                                ) {
                                    ModernSlider(
                                        value = backgroundScanIntervalMinutes,
                                        onValueChange = onSetBackgroundScanInterval,
                                        valueRange = 5..60,
                                        label = "Every $backgroundScanIntervalMinutes minutes",
                                        helper = "Lower intervals use more battery"
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // Visual separator after Background Scanning
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Scan Filters Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.FilterList,
                    title = "Scan Filters",
                    subtitle = "Control which networks appear in your scan results",
                    content = {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // SSID Filter
                            SettingItem(
                                icon = Icons.Default.Wifi,
                                title = "Network Name Filter",
                                description = "Only show networks containing this text"
                            ) {
                                ModernTextField(
                                    value = ssidFilter,
                                    onValueChange = onSsidFilterChange,
                                    placeholder = "Enter network name to filter",
                                    helper = "Example: 'Office' will show 'Office-WiFi', 'Main-Office', etc."
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // BSSID Filter
                            SettingItem(
                                icon = Icons.Default.Router,
                                title = "MAC Address Filter",
                                description = "Filter by specific router MAC addresses"
                            ) {
                                BssidFilterSection(
                                    value = bssidFilter,
                                    onValueChange = onBssidFilterChange
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Security Filter
                            SettingItem(
                                icon = Icons.Default.Security,
                                title = "Security Type Filter",
                                description = "Show only networks with selected security types"
                            ) {
                                ModernSecurityFilterSection(
                                    selectedTypes = securityFilter,
                                    availableTypes = availableSecurityTypes,
                                    onSelectionChange = onSecurityFilterChange
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // RSSI Threshold
                            SettingItem(
                                icon = Icons.Default.SignalCellularAlt,
                                title = "Signal Strength Filter",
                                description = "Hide networks with weak signals (default: -95 dBm)"
                            ) {
                                RssiThresholdSliderSection(
                                    rssiThreshold = rssiThreshold,
                                    onRssiThresholdChange = onRssiThresholdChange,
                                    label = "Minimum Signal Strength"
                                )
                            }
                        }
                    }
                )
            }

            // Connection Settings Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.Construction,
                    title = "Connection Settings",
                    subtitle = "Configure automatic connection behavior",
                    content = {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Max Retries
                            SettingItem(
                                icon = Icons.Default.NetworkCheck,
                                title = "Connection Attempts",
                                description = "How many times to try each password"
                            ) {
                                ModernSlider(
                                    value = maxRetries,
                                    onValueChange = onMaxRetriesChange,
                                    valueRange = 1..10,
                                    label = "Max Retries: $maxRetries",
                                    helper = "Higher values increase success rate but take longer"
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Connection Timeout
                            SettingItem(
                                icon = Icons.Default.Timer,
                                title = "Connection Timeout",
                                description = "Maximum time to wait for each connection attempt"
                            ) {
                                ModernSlider(
                                    value = connectionTimeoutSeconds,
                                    onValueChange = onConnectionTimeoutChange,
                                    valueRange = 5..60,
                                    label = "Timeout: ${connectionTimeoutSeconds}s",
                                    helper = "Longer timeouts help with slow networks"
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // RSSI Threshold for Connection
                            SettingItem(
                                icon = Icons.Default.SignalCellularAlt,
                                title = "Minimum Signal for Connection",
                                description = "Don't attempt connection if signal is too weak (default: -80 dBm)"
                            ) {
                                RssiThresholdSliderSection(
                                    rssiThreshold = rssiThresholdForConnection.toString(),
                                    onRssiThresholdChange = { onRssiThresholdForConnectionChange(it.toInt()) },
                                    label = "Min RSSI: ${rssiThresholdForConnection}dBm"
                                )
                            }
                        }
                    }
                )
            }

            // Password Management Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.VpnKey,
                    title = "Password Management",
                    subtitle = "Manage WiFi passwords for automatic connection",
                    content = {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Info card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Passwords are stored locally and tried in order when connecting to networks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Add password section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ModernTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it },
                                    placeholder = "Enter password to save",
                                    modifier = Modifier.weight(1f)
                                )

                                FloatingActionButton(
                                    onClick = {
                                        if (newPassword.isNotBlank()) {
                                            onAddPassword(newPassword)
                                            newPassword = ""
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Password",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Passwords list
                            if (passwords.isEmpty()) {
                                EmptyPasswordsState()
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Saved Passwords (${passwords.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    passwords.forEach { pwd ->
                                        ModernPasswordItem(
                                            password = pwd,
                                            onRemove = { onRemovePassword(pwd) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // Network Management Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.Timer,
                    title = "Network Cleanup",
                    subtitle = "Automatically manage old networks",
                    content = {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SettingItem(
                                icon = Icons.Default.Schedule,
                                title = "Auto-Hide Timeout",
                                description = "Move networks offline when not seen for this duration"
                            ) {
                                HideUnseenNetworksSection(
                                    hideNetworksUnseenForSeconds = hideNetworksUnseenForSeconds,
                                    onHideNetworksUnseenForSecondsChange = onHideNetworksUnseenForSecondsChange
                                )
                            }
                        }
                    }
                )
            }

            // Data Management Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.DeleteSweep,
                    title = "Data Management",
                    subtitle = "Manage your data and privacy settings",
                    content = {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Share Statistics Toggle
                            SettingItem(
                                icon = Icons.Default.CloudSync,
                                title = "Share Statistics",
                                description = "Help improve the app by sharing anonymous usage data"
                            ) {
                                Switch(
                                    checked = isAutoUploadEnabled,
                                    onCheckedChange = onToggleAutoUpload
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            ClearAllDataSection(onClearAllData = onClearAllData)
                        }
                    }
                )
            }

            // Add bottom padding
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ModernSettingsCategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Content
            content()
        }
    }
}

@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Box(modifier = Modifier.padding(start = 32.dp)) {
            content()
        }
    }
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    helper: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        helper?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ModernSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    label: String,
    helper: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = valueRange.last - valueRange.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = valueRange.first.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueRange.last.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ModernPasswordItem(
    password: String,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = password,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "(${password.length} chars)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Password",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ClearAllDataSection(onClearAllData: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Warning card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "This will permanently delete:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = "• All saved passwords\n• All pinned networks\n• Scan history and statistics\n• Custom settings and preferences",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp)
                )
            }
        }

        // Clear button
        Button(
            onClick = { showConfirmDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteSweep,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Clear All Data",
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Clear All Data?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This action cannot be undone. All your saved data will be permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun EmptyPasswordsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "No saved passwords",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Add passwords above to enable automatic connection",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BssidFilterSection(
    value: String,
    onValueChange: (String) -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        "e.g. 00:11:22:33:44:55",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "BSSID Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
        
        Text(
            text = "Separate multiple MAC addresses with commas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
    
    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("MAC Address Filter") },
            text = {
                Text(
                    "Filter networks by router MAC address (BSSID).\n\n" +
                    "• Enter full or partial MAC addresses\n" +
                    "• Separate multiple entries with commas\n" +
                    "• Case insensitive\n\n" +
                    "Example: 00:11:22, AA:BB:CC:DD:EE:FF"
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun ModernSecurityFilterSection(
    selectedTypes: Set<String>,
    availableTypes: List<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedTypes.isNotEmpty()) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedTypes.isEmpty()) {
                            "All security types"
                        } else {
                            "${selectedTypes.size} selected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (selectedTypes.isNotEmpty()) {
                        Text(
                            text = selectedTypes.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Expandable content
        if (isExpanded && availableTypes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableTypes) { securityType ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { 
                                    val newSelection = if (selectedTypes.contains(securityType)) {
                                        selectedTypes - securityType
                                    } else {
                                        selectedTypes + securityType
                                    }
                                    onSelectionChange(newSelection)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedTypes.contains(securityType),
                                onCheckedChange = null // Handled by row click
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = securityType,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RssiThresholdSliderSection(
    rssiThreshold: String,
    onRssiThresholdChange: (String) -> Unit,
    label: String
) {
    val currentRssi = rssiThreshold.toIntOrNull() ?: -70
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${currentRssi}dBm",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(60.dp)
            )
            
            Slider(
                value = currentRssi.toFloat(),
                onValueChange = { newValue ->
                    onRssiThresholdChange(newValue.toInt().toString())
                },
                valueRange = -100f..0f,
                steps = 99,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Weak (-100)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Strong (0)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HideUnseenNetworksSection(
    hideNetworksUnseenForSeconds: Int,
    onHideNetworksUnseenForSecondsChange: (Int) -> Unit
) {
    val currentSeconds = hideNetworksUnseenForSeconds.coerceIn(30, 600)
    
    fun formatSeconds(seconds: Int): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds % 60 == 0 -> "${seconds / 60}m"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = formatSeconds(currentSeconds),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(60.dp)
            )
            
            Slider(
                value = currentSeconds.toFloat(),
                onValueChange = { newSeconds ->
                    val roundedSeconds = (newSeconds / 30).toInt() * 30
                    val clampedSeconds = roundedSeconds.coerceIn(30, 600)
                    onHideNetworksUnseenForSecondsChange(clampedSeconds)
                },
                valueRange = 30f..600f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("30s", "2m", "5m", "10m").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Text(
            text = "Networks will be moved to 'Out of Range' section but remain editable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ModernSettingsTopBar(onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}