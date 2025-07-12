package com.ner.wimap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.ner.wimap.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.ui.components.*
import com.ner.wimap.ui.components.SwipeIndicatorWithLabels
import com.ner.wimap.ui.dialogs.*
// Removed enhanced permission imports - camera permissions handled directly
import com.ner.wimap.ui.viewmodel.ExportFormat
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ads.NativeAdCard
import com.ner.wimap.ads.WorkingNativeAdCard
import com.ner.wimap.ads.ClickableNativeAdCard
import com.ner.wimap.ads.WorkingClickableNativeAd
import com.ner.wimap.ads.StableNativeAdCard


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    wifiNetworks: List<WifiNetwork>,
    isScanning: Boolean,
    hasEverScanned: Boolean,
    connectionStatus: String?,
    uploadStatus: String?,
    showPasswordDialog: Boolean,
    networkForPasswordInput: WifiNetwork?,
    showPermissionRationaleDialog: Boolean,
    permissionsRationaleMessage: String?,
    showEmptyPasswordListDialog: Boolean,
    networkForEmptyPasswordDialog: WifiNetwork?,
    isBackgroundScanningEnabled: Boolean,
    isBackgroundServiceActive: Boolean,
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
    currentSortingMode: com.ner.wimap.presentation.viewmodel.SortingMode,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (WifiNetwork) -> Unit,
    onPasswordEntered: (String) -> Unit,
    onDismissPasswordDialog: () -> Unit,
    onDismissPermissionRationaleDialog: () -> Unit,
    onRationalePermissionsRequest: () -> Unit,
    onRationaleOpenSettings: () -> Unit,
    onDismissEmptyPasswordListDialog: () -> Unit,
    onOpenPasswordManagement: () -> Unit,
    onToggleBackgroundScanning: (Boolean) -> Unit,
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
    onUpdateNetworkDataWithPhotoDeletion: (WifiNetwork, String?, String?, String?, Boolean) -> Unit = { network, comment, password, photoUri, _ -> onUpdateNetworkData(network, comment, password, photoUri) },
    onOpenMaps: () -> Unit,
    onSortingModeChanged: (com.ner.wimap.presentation.viewmodel.SortingMode) -> Unit,
    onClearConnectionStatus: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isProduction = remember { BuildConfig.BUILD_TYPE == "release" }

    // Dialog states
    var showExportDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showNoDataSnackbar by remember { mutableStateOf(false) }

    LaunchedEffect(connectionStatus) {
        connectionStatus?.let { 
            snackbarHostState.showSnackbar(it)
            onClearConnectionStatus()
        }
    }

    LaunchedEffect(uploadStatus) {
        if (!isProduction && uploadStatus != null) {
            snackbarHostState.showSnackbar(uploadStatus)
            onClearUploadStatus()
        } else if (uploadStatus != null) {
            onClearUploadStatus()
        }
    }

    LaunchedEffect(showNoDataSnackbar) {
        if (showNoDataSnackbar) {
            snackbarHostState.showSnackbar("No networks found to share. Start scanning to discover networks.")
            showNoDataSnackbar = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MainTopAppBar(
                    onOpenPinnedNetworks = onOpenPinnedNetworks,
                    onOpenSettings = onOpenSettings,
                    isBackgroundServiceActive = isBackgroundServiceActive,
                    showNavigationActions = true,
                    onShowAbout = { showAboutDialog = true },
                    onShowTerms = { showTermsDialog = true },
                    currentPage = 2, // Main screen is now page 2 (after WiFi Locator and Pinned)
                    onNavigateToPage = { page ->
                        when (page) {
                            1 -> onOpenPinnedNetworks() // Pinned is now page 1
                            3 -> onOpenMaps() // Maps is now page 3
                        }
                    }
                )
            },
            bottomBar = {
                ModernBottomBar(
                    isScanning = isScanning,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan,
                    onShareExportClicked = { showExportDialog = true },
                    onClearNetworks = onClearNetworks,
                    networkCount = wifiNetworks.size,
                    onShowNoDataSnackbar = {
                        showNoDataSnackbar = true
                    }
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            // Show empty state animation when no networks are found
            if (wifiNetworks.isEmpty()) {
                // Determine the appropriate status text based on app state
                val statusText = when {
                    !hasEverScanned -> "Tap Start to discover nearby Wi-Fi networks."
                    isScanning -> "Scanning..."
                    else -> "Tap Start to discover nearby Wi-Fi networks."
                }
                
                EmptyNetworksAnimation(
                    statusText = statusText,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            } else {
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
                                    it.peakRssiLatitude != null && it.peakRssiLongitude != null &&
                                            it.peakRssiLatitude != 0.0 && it.peakRssiLongitude != 0.0
                                }
                            )
                        }
                    }

                    item {
                        key("sorting_control") {
                            SortingControl(
                                currentSortingMode = currentSortingMode,
                                onSortingModeChanged = onSortingModeChanged
                            )
                        }
                    }
                    
                    // Persistent native ad as first card
                    item {
                        key("persistent_native_ad") {
                            StableNativeAdCard(
                                modifier = Modifier.padding(vertical = 8.dp),
                                isPersistent = true
                            )
                        }
                    }
                    
                    // Separate online and offline networks
                    val onlineNetworks = wifiNetworks.filter { !it.isOffline }
                    val offlineNetworks = wifiNetworks.filter { it.isOffline }
                    
                    // Online networks section with ads
                    itemsIndexed(
                        items = onlineNetworks,
                        key = { index, network -> "${network.bssid}_online" } // Simplified stable key to prevent memory issues
                    ) { index, network ->
                        // Show native ad after every 3 cards exactly
                        if (index > 0 && index % 3 == 0) {
                            StableNativeAdCard(
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        // Check if this network is currently pinned
                        val isCurrentlyPinned = pinnedNetworks.any { it.bssid == network.bssid }
                        
                        EnhancedWifiNetworkCard(
                            network = network,
                            isConnecting = connectingNetworks.contains(network.bssid),
                            connectionStatus = if (connectingNetworks.contains(network.bssid)) connectionProgress else null,
                            onPinClick = { bssid, pin -> 
                                if (pin) {
                                    onPinNetwork(network, null, null, null)
                                } else {
                                    onUnpinNetwork(bssid)
                                }
                            },
                            onConnectClick = { onConnect(network) },
                            onCancelConnectionClick = onClearConnectionProgress,
                            onMoreInfoClick = { /* No-op for now */ },
                            onUpdateData = { bssid, ssid, comment, password, photoPath ->
                                onUpdateNetworkData(network, comment, password, photoPath)
                            },
                            onUpdateDataWithPhotoDeletion = { bssid, ssid, comment, password, photoPath, clearPhoto ->
                                onUpdateNetworkDataWithPhotoDeletion(network, comment, password, photoPath, clearPhoto)
                            },
                            isPinned = isCurrentlyPinned, // Explicitly pass the current pin status
                            successfulPasswords = successfulPasswords
                        )
                    }
                    
                    // Offline networks section (if any exist)
                    if (offlineNetworks.isNotEmpty()) {
                        item {
                            key("offline_separator") {
                                OfflineNetworksSeparator(
                                    offlineCount = offlineNetworks.size
                                )
                            }
                        }
                        
                        itemsIndexed(
                            items = offlineNetworks,
                            key = { index, network -> "${network.bssid}_offline" } // Simplified stable key to prevent memory issues
                        ) { index, network ->
                            // Show native ad after every 3 cards exactly
                            if (index > 0 && index % 3 == 0) {
                                StableNativeAdCard(
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            // Check if this network is currently pinned
                            val isCurrentlyPinned = pinnedNetworks.any { it.bssid == network.bssid }
                            
                            EnhancedWifiNetworkCard(
                                network = network,
                                isConnecting = false, // Never show connecting for offline networks
                                connectionStatus = null,
                                onPinClick = { bssid, pin -> 
                                    if (pin) {
                                        onPinNetwork(network, null, null, null)
                                    } else {
                                        onUnpinNetwork(bssid)
                                    }
                                },
                                onConnectClick = { /* Disabled for offline networks */ },
                                onCancelConnectionClick = onClearConnectionProgress,
                                onMoreInfoClick = { /* No-op for now */ },
                                onUpdateData = { bssid, ssid, comment, password, photoPath ->
                                    onUpdateNetworkData(network, comment, password, photoPath)
                                },
                                onUpdateDataWithPhotoDeletion = { bssid, ssid, comment, password, photoPath, clearPhoto ->
                                    onUpdateNetworkDataWithPhotoDeletion(network, comment, password, photoPath, clearPhoto)
                                },
                                isPinned = isCurrentlyPinned, // Explicitly pass the current pin status
                                successfulPasswords = successfulPasswords
                            )
                        }
                    }

                    item {
                        key("bottom_spacer") {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
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

    // Camera permissions now handled directly by AutoPermissionCameraLauncher
    // No global permission handler needed to avoid settings dialogs
    
    // Restore original WiFi/Location permission dialog with explanation
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

    // Empty Password List Dialog
    if (showEmptyPasswordListDialog && networkForEmptyPasswordDialog != null) {
        key("empty_password_dialog_${networkForEmptyPasswordDialog.bssid}") {
            EmptyPasswordListDialog(
                networkName = networkForEmptyPasswordDialog.ssid,
                onOpenSettings = onOpenPasswordManagement,
                onDismiss = onDismissEmptyPasswordListDialog
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
                color = MaterialTheme.colorScheme.onBackground
            )
            if (networksWithLocationCount > 0) {
                Text(
                    text = "$networksWithLocationCount with GPS location",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Text(
            text = "$networkCount",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary
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
                        text = "Network scan data, including SSIDs, signal strengths, and GPS coordinates, may be transmitted to our servers for statistical analysis and service improvement. Device identification (ADID) is mandatory for proper app functionality and enables us to: analyze advertising effectiveness across different locations, improve ad relevance based on WiFi coverage areas, and provide personalized advertising experiences that support app development and maintenance.",
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

@Composable
private fun OfflineNetworksSeparator(
    offlineCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Simple icon without background circle - cleaner look
        Icon(
            imageVector = Icons.Default.SignalWifiOff,
            contentDescription = "Offline Networks",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Out of Range Networks",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = "Networks not seen recently",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        // Simple text count without background - minimal design
        Text(
            text = "$offlineCount",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
    }
}

object BuildConfig {
    const val BUILD_TYPE = "debug"
}