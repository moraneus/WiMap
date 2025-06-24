package com.ner.wimap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.ui.components.*
import com.ner.wimap.ui.dialogs.*
import com.ner.wimap.ui.viewmodel.ExportFormat
import com.ner.wimap.ui.viewmodel.ExportAction


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    wifiNetworks: List<WifiNetwork>,
    isScanning: Boolean,
    connectionStatus: String?,
    uploadStatus: String?,
    showPasswordDialog: Boolean,
    networkForPasswordInput: WifiNetwork?,
    showPermissionRationaleDialog: Boolean,
    permissionsRationaleMessage: String?,
    isBackgroundScanningEnabled: Boolean,
    backgroundScanIntervalMinutes: Int,
    isAutoUploadEnabled: Boolean,
    pinnedNetworks: List<PinnedNetwork>,
    isConnecting: Boolean,
    connectingNetworks: Set<String> = emptySet(),
    connectionProgress: String?,
    successfulPasswords: Map<String, String> = emptyMap(),
    currentPassword: String?,
    currentAttempt: Int,
    totalAttempts: Int,
    connectingNetworkName: String?,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (WifiNetwork) -> Unit,
    onPasswordEntered: (String) -> Unit,
    onDismissPasswordDialog: () -> Unit,
    onDismissPermissionRationaleDialog: () -> Unit,
    onRationalePermissionsRequest: () -> Unit,
    onRationaleOpenSettings: () -> Unit,
    onToggleBackgroundScanning: (Boolean) -> Unit,
    onSetBackgroundScanInterval: (Int) -> Unit,
    onToggleAutoUpload: (Boolean) -> Unit,
    onUploadScanResults: () -> Unit,
    onClearUploadStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    onExportWithFormatAndAction: (ExportFormat, ExportAction) -> Unit,
    onShareCsv: () -> Unit,
    onClearNetworks: () -> Unit,
    onOpenPinnedNetworks: () -> Unit,
    onPinNetwork: (WifiNetwork, String?, String?, String?) -> Unit,
    onUnpinNetwork: (String) -> Unit,
    onClearConnectionProgress: () -> Unit,
    onUpdateNetworkData: (WifiNetwork, String?, String?, String?) -> Unit,
    onOpenMaps: () -> Unit // New parameter for opening maps
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isProduction = remember { BuildConfig.BUILD_TYPE == "release" }

    // Dialog states
    var showExportDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(connectionStatus) {
        connectionStatus?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(uploadStatus) {
        if (!isProduction && uploadStatus != null) {
            snackbarHostState.showSnackbar(uploadStatus)
            onClearUploadStatus()
        } else if (uploadStatus != null) {
            onClearUploadStatus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color(0xFFF8F9FA),
            topBar = {
                MainTopAppBar(
                    onOpenPinnedNetworks = onOpenPinnedNetworks,
                    onOpenSettings = onOpenSettings
                )
            },
            bottomBar = {
                ModernBottomNavigationBar(
                    isScanning = isScanning,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan,
                    onShareExportClicked = { showExportDialog = true },
                    onClearNetworks = onClearNetworks,
                    onOpenMaps = onOpenMaps,
                    onShowAbout = { showAboutDialog = true },
                    onShowTerms = { showTermsDialog = true }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                key("network_count_${wifiNetworks.size}") {
                    NetworkCountHeader(
                        networkCount = wifiNetworks.size,
                        networksWithLocationCount = wifiNetworks.count {
                            it.latitude != null && it.longitude != null &&
                                    it.latitude != 0.0 && it.longitude != 0.0
                        }
                    )
                }
            }

            items(
                items = wifiNetworks,
                key = { network -> "${network.bssid}_${network.ssid}_${network.rssi}" }
            ) { network ->
                WifiNetworkCard(
                    network = network,
                    onConnectClicked = { onConnect(network) },
                    pinnedNetworks = pinnedNetworks,
                    onPinNetwork = onPinNetwork,
                    onUnpinNetwork = onUnpinNetwork,
                    isConnecting = connectingNetworks.contains(network.bssid),
                    successfulPasswords = successfulPasswords,
                    onUpdateNetworkData = { updatedNetwork, newComment, newPassword, newPhotoUri ->
                        onUpdateNetworkData(updatedNetwork, newComment, newPassword, newPhotoUri)
                    }
                )
            }

            item {
                key("bottom_spacer") {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
        }

        // Sticky Dialogs - positioned directly below the top app bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 64.dp), // Account for top app bar height
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Scanning dialog (appears first, narrower)
            StickyScanProgressDialog(
                isVisible = isScanning,
                onCancel = onStopScan
            )

            // Connection dialog (appears directly below scanning dialog, full width)
            StickyConnectionProgressDialog(
                isVisible = isConnecting && connectionProgress != null,
                networkName = connectingNetworkName ?: "",
                currentAttempt = currentAttempt,
                totalAttempts = totalAttempts,
                currentPassword = currentPassword,
                onCancel = onClearConnectionProgress
            )
        }
    }

    // Export Format Dialog with Action Selection
    if (showExportDialog) {
        ExportFormatDialog(
            title = "Share WiFi Networks",
            onFormatAndActionSelected = { format, action ->
                onExportWithFormatAndAction(format, action)
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false }
        )
    }

    // Other Dialogs
    if (showPasswordDialog && networkForPasswordInput != null) {
        key("password_dialog_${networkForPasswordInput.bssid}") {
            ModernPasswordDialog(
                network = networkForPasswordInput,
                onPasswordEntered = onPasswordEntered,
                onDismiss = onDismissPasswordDialog
            )
        }
    }

    if (showPermissionRationaleDialog) {
        key("permission_dialog") {
            ModernPermissionDialog(
                message = permissionsRationaleMessage,
                onGrantPermissions = onRationalePermissionsRequest,
                onOpenSettings = onRationaleOpenSettings,
                onDismiss = onDismissPermissionRationaleDialog
            )
        }
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    // Terms of Use Dialog
    if (showTermsDialog) {
        TermsOfUseDialog(
            onDismiss = { showTermsDialog = false }
        )
    }
}

@Composable
fun ModernBottomNavigationBar(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onShareExportClicked: () -> Unit,
    onClearNetworks: () -> Unit,
    onOpenMaps: () -> Unit,
    onShowAbout: () -> Unit,
    onShowTerms: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Share Button
            ModernNavButton(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = onShareExportClicked
            )
            
            // Clear Button  
            ModernNavButton(
                icon = Icons.Default.Clear,
                label = "Clear",
                onClick = onClearNetworks
            )
            
            // Integrated Scan Button (Center, Prominent)
            IntegratedScanButton(
                isScanning = isScanning,
                onStartScan = onStartScan,
                onStopScan = onStopScan
            )
            
            // Maps Button
            ModernNavButton(
                icon = Icons.Default.Map,
                label = "Maps",
                onClick = onOpenMaps
            )
            
            // More/Menu Button with dropdown
            MoreMenuButton(
                onShowAbout = onShowAbout,
                onShowTerms = onShowTerms
            )
        }
    }
}

@Composable
private fun ModernNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun IntegratedScanButton(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = if (isScanning) 6.dp else 4.dp,
                    shape = CircleShape,
                    ambientColor = if (isScanning) 
                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    else 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                .background(
                    color = if (isScanning) 
                        MaterialTheme.colorScheme.errorContainer 
                    else 
                        MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                )
                .clickable { if (isScanning) onStopScan() else onStartScan() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isScanning) "Stop Scan" else "Start Scan",
                tint = if (isScanning) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isScanning) "Stop" else "Scan",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isScanning) 
                MaterialTheme.colorScheme.error 
            else 
                MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MoreMenuButton(
    onShowAbout: () -> Unit,
    onShowTerms: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "More",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onShowAbout()
                }
            )
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Terms of Use",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onShowTerms()
                }
            )
        }
    }
}

@Composable
private fun NetworkCountHeader(
    networkCount: Int,
    networksWithLocationCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Networks Found",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF2C3E50)
            )
            if (networksWithLocationCount > 0) {
                Text(
                    text = "$networksWithLocationCount with GPS location",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF27AE60)
                )
            }
        }
        Text(
            text = "$networkCount",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF3498DB)
        )
    }
}

@Composable
private fun AboutDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "About WiMap",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "WiMap is a comprehensive WiFi network scanner and mapping application designed to help you discover, analyze, and manage wireless networks in your area.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Key Features:",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "• Real-time WiFi network scanning\n• GPS location mapping\n• Network security analysis\n• Export and sharing capabilities\n• Pinned network management\n• Background scanning support",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Version 1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("OK")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun TermsOfUseDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Terms of Use",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Legal Usage Notice",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    Text(
                        text = "This application is intended for legitimate network analysis and educational purposes only. By using WiMap, you agree to the following terms:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text(
                        text = "Prohibited Activities:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                item {
                    Text(
                        text = "• Unauthorized access to networks you do not own\n• Attempting to crack or bypass network security\n• Using this app for illegal surveillance or hacking\n• Violating local privacy and cybersecurity laws\n• Commercial exploitation without permission",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text(
                        text = "Data Collection:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                item {
                    Text(
                        text = "Network scan data, including SSIDs, signal strengths, and GPS coordinates, may be transmitted to our servers for statistical analysis and service improvement. This data helps us understand WiFi coverage patterns and enhance the application's functionality.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item {
                    Text(
                        text = "User Responsibility:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                item {
                    Text(
                        text = "Users are solely responsible for ensuring their use of this application complies with all applicable local, state, and federal laws. The developers assume no liability for misuse of this application.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("I Understand")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

object BuildConfig {
    const val BUILD_TYPE = "debug"
}