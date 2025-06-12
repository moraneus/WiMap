package com.ner.wimap.ui

import androidx.compose.foundation.background
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
    val fabSize = 72.dp
    val fabRadius = fabSize / 2
    val notchMargin = 8.dp
    val notchRadiusPx = with(LocalDensity.current) { (fabRadius + notchMargin).toPx() }
    val customNotchShape = remember(notchRadiusPx) { ModernNotchShape(notchRadiusPx) }

    // Export dialog state
    var showExportDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = Color(0xFFF8F9FA),
        topBar = {
            MainTopAppBar(
                onOpenPinnedNetworks = onOpenPinnedNetworks,
                onOpenSettings = onOpenSettings
            )
        },
        floatingActionButton = {
            ScanFab(
                isScanning = isScanning,
                fabSize = fabSize,
                onStartScan = onStartScan,
                onStopScan = onStopScan
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            ModernBottomAppBar(
                customNotchShape = customNotchShape,
                onShareExportClicked = { showExportDialog = true }, // Consolidated share/export
                onClearNetworks = onClearNetworks,
                onOpenMaps = onOpenMaps // New maps button
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
            if (isScanning) {
                item {
                    key("scanning_status") {
                        ScanningStatusCard()
                    }
                }
            }

            if (isConnecting && connectionProgress != null) {
                item {
                    key("connection_progress_$connectionProgress") {
                        ConnectionProgressCard(
                            message = connectionProgress,
                            onDismiss = onClearConnectionProgress
                        )
                    }
                }
            }

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
}

@Composable
fun ModernBottomAppBar(
    customNotchShape: Shape,
    onShareExportClicked: () -> Unit, // Consolidated function
    onClearNetworks: () -> Unit,
    onOpenMaps: () -> Unit // New maps function
) {
    BottomAppBar(
        modifier = Modifier
            .clip(customNotchShape)
            .shadow(12.dp, customNotchShape),
        containerColor = Color(0xFF2C3E50),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        actions = {
            BottomBarButton(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = onShareExportClicked
            )
            BottomBarButton(
                icon = Icons.Default.Clear,
                label = "Clear",
                onClick = onClearNetworks
            )
            Spacer(Modifier.weight(1f))
            BottomBarButton(
                icon = Icons.Default.Map,
                label = "Maps",
                onClick = onOpenMaps
            )
        }
    )
}

@Composable
private fun BottomBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
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

object BuildConfig {
    const val BUILD_TYPE = "debug"
}