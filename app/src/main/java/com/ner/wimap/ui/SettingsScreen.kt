package com.ner.wimap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    ssidFilter: String,
    onSsidFilterChange: (String) -> Unit,
    securityFilter: String,
    onSecurityFilterChange: (String) -> Unit,
    rssiThreshold: String,
    onRssiThresholdChange: (String) -> Unit,
    passwords: List<String>,
    onAddPassword: (String) -> Unit,
    onRemovePassword: (String) -> Unit,
    maxRetries: Int,
    onMaxRetriesChange: (Int) -> Unit,
    connectionTimeoutSeconds: Int,
    onConnectionTimeoutChange: (Int) -> Unit,
    rssiThresholdForConnection: Int,
    onRssiThresholdForConnectionChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Custom compact top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color(0xFF667eea))
                .padding(top = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )

                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }

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

                            ModernTextField(
                                value = securityFilter,
                                onValueChange = onSecurityFilterChange,
                                label = "Security Type Filter",
                                placeholder = "e.g., WPA2, WPA3, OPEN"
                            )

                            ModernTextField(
                                value = rssiThreshold,
                                onValueChange = onRssiThresholdChange,
                                label = "Min RSSI Threshold (dBm)",
                                placeholder = "e.g., -70"
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
                    subtitle = "Configure automatic connection behavior",
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
                        }
                    }
                )
            }

            // Password Management Section
            item {
                ModernSettingsCategoryCard(
                    icon = Icons.Default.Security,
                    title = "Password Management",
                    subtitle = "Store and manage WiFi passwords",
                    gradientColors = listOf(Color(0xFF4ECDC4), Color(0xFF44A08D)),
                    content = {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
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