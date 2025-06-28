package com.ner.wimap.ui

import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
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
    onClearAllData: () -> Unit,
    onBack: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Modern Material 3 Settings Top Bar
        ModernSettingsTopBar(onBack = onBack)

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Scan Filters Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.FilterList,
                    title = "WiFi Scan Filters",
                    subtitle = "Customize your scanning preferences",
                    gradientColors = listOf(Color(0xFF667eea), Color(0xFF764ba2)),
                    content = {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ModernTextField(
                                value = ssidFilter,
                                onValueChange = onSsidFilterChange,
                                label = "SSID Filter",
                                placeholder = "Enter network name to filter"
                            )

                            // BSSID Filter with info icon
                            BssidFilterSection(
                                value = bssidFilter,
                                onValueChange = onBssidFilterChange
                            )

                            // Modern expandable multi-select Security Filter
                            ModernSecurityFilterSection(
                                selectedTypes = securityFilter,
                                availableTypes = availableSecurityTypes,
                                onSelectionChange = onSecurityFilterChange
                            )

                            // RSSI Threshold Slider (matching connection style)
                            RssiThresholdSliderSection(
                                rssiThreshold = rssiThreshold,
                                onRssiThresholdChange = onRssiThresholdChange
                            )
                        }
                    }
                )
            }

            // Connection Settings Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.Construction,
                    title = "Connection Settings",
                    subtitle = "Configure connection behavior and manage WiFi passwords",
                    gradientColors = listOf(Color(0xFFf093fb), Color(0xFFf5576c)),
                    content = {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Max Retries
                            Column {
                                Text(
                                    text = "Max Retries: $maxRetries",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF2C3E50)
                                )
                                Slider(
                                    value = maxRetries.toFloat(),
                                    onValueChange = { onMaxRetriesChange(it.toInt()) },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFf5576c),
                                        activeTrackColor = Color(0xFFf093fb)
                                    )
                                )
                                Text(
                                    text = "Number of attempts per password",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7F8C8D)
                                )
                            }

                            // Connection Timeout
                            Column {
                                Text(
                                    text = "Connection Timeout: ${connectionTimeoutSeconds}s",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF2C3E50)
                                )
                                Slider(
                                    value = connectionTimeoutSeconds.toFloat(),
                                    onValueChange = { onConnectionTimeoutChange(it.toInt()) },
                                    valueRange = 5f..60f,
                                    steps = 10,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFf5576c),
                                        activeTrackColor = Color(0xFFf093fb)
                                    )
                                )
                                Text(
                                    text = "Seconds to wait per connection attempt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7F8C8D)
                                )
                            }

                            // RSSI Threshold for Connection
                            Column {
                                Text(
                                    text = "Min RSSI for Connection: ${rssiThresholdForConnection}dBm",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color(0xFF2C3E50)
                                )
                                Slider(
                                    value = rssiThresholdForConnection.toFloat(),
                                    onValueChange = { onRssiThresholdForConnectionChange(it.toInt()) },
                                    valueRange = -100f..-30f,
                                    steps = 13,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFFf5576c),
                                        activeTrackColor = Color(0xFFf093fb)
                                    )
                                )
                                Text(
                                    text = "Don't attempt connection if signal is weaker",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF7F8C8D)
                                )
                            }

                            // Divider between connection settings and password management
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )

                            // Password Management within Connection Settings
                            Text(
                                text = "WiFi Password Management",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF2C3E50)
                            )
                            
                            // Add password section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ModernTextField(
                                    value = newPassword,
                                    onValueChange = { newPassword = it },
                                    label = "New Password",
                                    placeholder = "Enter password to save",
                                    modifier = Modifier.weight(1f)
                                    // No visualTransformation - show clear text
                                )

                                FloatingActionButton(
                                    onClick = {
                                        if (newPassword.isNotBlank()) {
                                            onAddPassword(newPassword)
                                            newPassword = ""
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                    containerColor = Color(0xFF27AE60),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Password",
                                        tint = Color.White,
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
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color(0xFF2C3E50)
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

            // Background Scanning Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.CloudSync,
                    title = "Background Scanning",
                    subtitle = "Automatic WiFi scanning when app is in background",
                    gradientColors = listOf(Color(0xFF43E97B), Color(0xFF38F9D7)),
                    content = {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Enable/Disable Background Scanning
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable Background Scanning",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Continue scanning for WiFi networks when app is in background",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = isBackgroundScanningEnabled,
                                    onCheckedChange = onToggleBackgroundScanning
                                )
                            }

                            if (isBackgroundScanningEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Scan Interval Slider
                                Column {
                                    Text(
                                        text = "Scan Interval: $backgroundScanIntervalMinutes minutes",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "How often to automatically scan for new WiFi networks when the app is running in the background. Shorter intervals find networks faster but use more battery.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Slider(
                                        value = backgroundScanIntervalMinutes.toFloat(),
                                        onValueChange = { onSetBackgroundScanInterval(it.toInt()) },
                                        valueRange = 5f..60f,
                                        steps = 10,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF43E97B),
                                            activeTrackColor = Color(0xFF43E97B)
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "5 min",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "60 min",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                    icon = Icons.Default.FilterList,
                    title = "Network Management",
                    subtitle = "Automatic network cleanup settings",
                    gradientColors = listOf(Color(0xFF667eea), Color(0xFF764ba2)),
                    content = {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Hide networks unseen for X seconds (30s to 1 hour)
                            HideUnseenNetworksSection(
                                hideNetworksUnseenForSeconds = hideNetworksUnseenForSeconds,
                                onHideNetworksUnseenForSecondsChange = onHideNetworksUnseenForSecondsChange
                            )
                        }
                    }
                )
            }


            // Data Management Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.DeleteSweep,
                    title = "Data Management",
                    subtitle = "Clear all stored data and settings",
                    gradientColors = listOf(Color(0xFFE74C3C), Color(0xFFC0392B)),
                    content = {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ClearAllDataSection(onClearAllData = onClearAllData)
                        }
                    }
                )
            }

            // Add some bottom padding
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun ModernSettingsCategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(gradientColors),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Content
            content()
        }
    }
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        },
        placeholder = {
            Text(
                placeholder,
                color = Color(0xFFBDC3C7)
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF3498DB),
            unfocusedBorderColor = Color(0xFFE0E6ED),
            focusedLabelColor = Color(0xFF3498DB),
            unfocusedLabelColor = Color(0xFF7F8C8D)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ModernPasswordItem(
    password: String,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8F9FA)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = null,
                    tint = Color(0xFF3498DB),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = password, // Show clear password instead of dots
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF2C3E50)
                )
                Text(
                    text = "(${password.length} chars)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7F8C8D)
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Color(0xFFE74C3C).copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Password",
                    tint = Color(0xFFE74C3C),
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning text
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFF39C12),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "This action will permanently delete all your data",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF2C3E50)
            )
        }

        // Description
        Text(
            text = "• All saved passwords\n• All pinned networks\n• Connection history\n• App settings and preferences",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7F8C8D),
            modifier = Modifier.padding(start = 36.dp)
        )

        // Clear All button
        Button(
            onClick = { showConfirmDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE74C3C)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Clear All Data",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
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
                    tint = Color(0xFFE74C3C),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Clear All Data",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF2C3E50)
                )
            },
            text = {
                Text(
                    text = "This will erase all saved data (passwords, pinned networks, etc.). Continue?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF7F8C8D)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onClearAllData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Erase All",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF7F8C8D),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = Color(0xFFBDC3C7),
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "No saved passwords",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF7F8C8D)
        )
        Text(
            text = "Add passwords above to store them securely",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFBDC3C7)
        )
    }
}

@Composable
fun SecurityFilterSection(
    selectedTypes: Set<String>,
    availableTypes: List<String>,
    onSelectionChange: (Set<String>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Security Type Filter",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF7F8C8D)
        )
        
        if (availableTypes.isEmpty()) {
            Text(
                text = "No security types available",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFBDC3C7),
                modifier = Modifier.padding(8.dp)
            )
        } else {
            // Multi-select chips
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .border(
                        1.dp,
                        Color(0xFFE0E6ED),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableTypes) { securityType ->
                    SecurityTypeChip(
                        securityType = securityType,
                        isSelected = selectedTypes.contains(securityType),
                        onToggle = { isSelected ->
                            val newSelection = if (isSelected) {
                                selectedTypes + securityType
                            } else {
                                selectedTypes - securityType
                            }
                            onSelectionChange(newSelection)
                        }
                    )
                }
            }
        }
        
        // Show selected count
        if (selectedTypes.isNotEmpty()) {
            Text(
                text = "${selectedTypes.size} security type(s) selected: ${selectedTypes.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3498DB)
            )
        } else {
            Text(
                text = "All security types will be shown",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7F8C8D)
            )
        }
    }
}

@Composable
fun SecurityTypeChip(
    securityType: String,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!isSelected) }
            .background(
                if (isSelected) Color(0xFF3498DB).copy(alpha = 0.1f) else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF3498DB),
                uncheckedColor = Color(0xFF7F8C8D)
            )
        )
        
        Text(
            text = securityType,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) Color(0xFF3498DB) else Color(0xFF2C3E50),
            modifier = Modifier.weight(1f)
        )
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFF3498DB),
                modifier = Modifier.size(16.dp)
            )
        }
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "BSSID Filter",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF7F8C8D),
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "BSSID Filter Info",
                    tint = Color(0xFF3498DB),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    "Enter BSSID(s) separated by commas",
                    color = Color(0xFFBDC3C7)
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3498DB),
                unfocusedBorderColor = Color(0xFFE0E6ED),
                focusedLabelColor = Color(0xFF3498DB),
                unfocusedLabelColor = Color(0xFF7F8C8D)
            ),
            modifier = Modifier.fillMaxWidth()
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
                    tint = Color(0xFF3498DB),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "BSSID Filter Usage",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF2C3E50)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Enter one or more BSSIDs (MAC addresses), separated by commas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "Example:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "00:11:22:33:44:55, 66:77:88:99:AA:BB",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF3498DB)
                    )
                    Text(
                        text = "• Partial matches are supported\n• Case insensitive\n• Only networks matching any of the specified BSSIDs will be shown",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7F8C8D)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoDialog = false }
                ) {
                    Text(
                        text = "Got it",
                        color = Color(0xFF3498DB),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
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
        // Header with expand/collapse
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedTypes.isNotEmpty()) 
                    Color(0xFF3498DB).copy(alpha = 0.1f) 
                else 
                    Color(0xFFF8F9FA)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        text = "Security Type Filter",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = if (selectedTypes.isEmpty()) {
                            "All security types"
                        } else {
                            "${selectedTypes.size} type(s) selected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedTypes.isNotEmpty()) Color(0xFF3498DB) else Color(0xFF7F8C8D)
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF3498DB),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Expandable content
        if (isExpanded && availableTypes.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableTypes) { securityType ->
                        ModernSecurityTypeChip(
                            securityType = securityType,
                            isSelected = selectedTypes.contains(securityType),
                            onToggle = { isSelected ->
                                val newSelection = if (isSelected) {
                                    selectedTypes + securityType
                                } else {
                                    selectedTypes - securityType
                                }
                                onSelectionChange(newSelection)
                            }
                        )
                    }
                }
            }
        }
        
        // Selected types summary
        if (selectedTypes.isNotEmpty()) {
            Text(
                text = "Selected: ${selectedTypes.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3498DB),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun ModernSecurityTypeChip(
    securityType: String,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle(!isSelected) }
            .background(
                if (isSelected) Color(0xFF3498DB).copy(alpha = 0.1f) else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF3498DB),
                uncheckedColor = Color(0xFF7F8C8D),
                checkmarkColor = Color.White
            )
        )
        
        Text(
            text = securityType,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) Color(0xFF3498DB) else Color(0xFF2C3E50),
            modifier = Modifier.weight(1f)
        )
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFF3498DB),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun RssiThresholdSliderSection(
    rssiThreshold: String,
    onRssiThresholdChange: (String) -> Unit
) {
    val currentRssi = rssiThreshold.toIntOrNull() ?: -70
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Min RSSI Threshold: ${currentRssi}dBm",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF2C3E50)
        )
        
        Slider(
            value = currentRssi.toFloat(),
            onValueChange = { newValue ->
                onRssiThresholdChange(newValue.toInt().toString())
            },
            valueRange = -100f..0f,
            steps = 99, // 1 dBm steps
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF667eea),
                activeTrackColor = Color(0xFF764ba2),
                inactiveTrackColor = Color(0xFFE0E6ED)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "-100dBm",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7F8C8D)
            )
            Text(
                text = "0dBm",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7F8C8D)
            )
        }
        
        Text(
            text = "Networks with signal strength below this threshold will be filtered out",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7F8C8D)
        )
    }
}

@Composable
fun HideUnseenNetworksSection(
    hideNetworksUnseenForSeconds: Int,
    onHideNetworksUnseenForSecondsChange: (Int) -> Unit
) {
    // Seconds-based system: 30 seconds to 10 minutes in 30-second increments
    val currentSeconds = hideNetworksUnseenForSeconds.coerceIn(30, 600) // 30 seconds to 10 minutes
    
    // Helper function to format seconds nicely
    fun formatSeconds(seconds: Int): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds % 60 == 0 -> "${seconds / 60}m"
            else -> {
                val mins = seconds / 60
                val secs = seconds % 60
                "${mins}m ${secs}s"
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title with current value prominently displayed - updated text for offline behavior
        Text(
            text = "Move unseen networks offline after: ${formatSeconds(currentSeconds)}",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFF2C3E50)
        )
        
        // Add explanation for the new behavior
        Text(
            text = "Networks not seen for this duration will be moved to the 'Out of Range' section. They remain editable but cannot be connected to until they reappear.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7F8C8D)
        )
        
        // Enhanced slider with tick marks
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Slider(
                value = currentSeconds.toFloat(),
                onValueChange = { newSeconds ->
                    // Round to nearest 30-second increment
                    val roundedSeconds = (newSeconds / 30).toInt() * 30
                    val clampedSeconds = roundedSeconds.coerceIn(30, 600)
                    onHideNetworksUnseenForSecondsChange(clampedSeconds)
                },
                valueRange = 30f..600f, // 30 seconds to 10 minutes
                steps = 18, // (600-30)/30 - 1 = 18 steps for 30-second intervals
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF764ba2),
                    activeTrackColor = Color(0xFF667eea),
                    inactiveTrackColor = Color(0xFFE0E6ED)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Tick marks and labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Major tick labels: 30s, 1m, 2m, 5m, 10m
                listOf(30, 60, 120, 300, 600).forEach { seconds ->
                    Text(
                        text = formatSeconds(seconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentSeconds == seconds) Color(0xFF3498DB) else Color(0xFF7F8C8D),
                        fontWeight = if (currentSeconds == seconds) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        
        // Helpful description
        Text(
            text = "Networks not seen for this duration will be automatically removed from the list",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7F8C8D),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            if (remainingSeconds == 0) {
                "${minutes}m"
            } else {
                "${minutes}m ${remainingSeconds}s"
            }
        }
        else -> {
            val hours = seconds / 3600
            val remainingMinutes = (seconds % 3600) / 60
            if (remainingMinutes == 0) {
                "${hours}h"
            } else {
                "${hours}h ${remainingMinutes}m"
            }
        }
    }
}

private fun formatSecondsCompact(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds % 60 == 0 -> "${seconds / 60}m"
        else -> {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            "${minutes}m${remainingSeconds}s"
        }
    }
}

private fun formatSecondsDetailed(seconds: Int): String {
    return when {
        seconds < 60 -> "$seconds seconds"
        seconds == 60 -> "1 minute"
        seconds % 60 == 0 -> "${seconds / 60} minutes"
        else -> {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            "$minutes minutes and $remainingSeconds seconds"
        }
    }
}

@Composable
fun ModernSettingsTopBar(onBack: () -> Unit) {
    // Animated gradient colors
    val infiniteTransition = rememberInfiniteTransition(label = "settings_gradient")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "settings_gradient_offset"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        tonalElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF667eea).copy(alpha = 0.9f + animatedOffset * 0.1f),
                            Color(0xFF764ba2).copy(alpha = 0.9f + animatedOffset * 0.1f),
                            Color(0xFF667eea).copy(alpha = 0.9f + animatedOffset * 0.1f)
                        ),
                        startX = animatedOffset * 300f,
                        endX = (animatedOffset + 1f) * 300f
                    )
                )
                .padding(top = 24.dp) // Status bar padding
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Back button with animation
                ModernBackButton(onClick = onBack)
                
                // Settings icon with pulse animation
                AnimatedSettingsIcon()
                
                // Title section
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Customize your experience",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernBackButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "back_button_scale"
    )
    
    Surface(
        onClick = {
            isPressed = true
            onClick()
            isPressed = false
        },
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.15f),
        tonalElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AnimatedSettingsIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "settings_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "settings_rotation"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer {
                    rotationZ = rotation
                }
        )
    }
}

