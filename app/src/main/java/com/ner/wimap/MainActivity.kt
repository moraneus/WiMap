package com.ner.wimap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ner.wimap.ui.MainScreen
import com.ner.wimap.ui.PinnedNetworksScreen
import com.ner.wimap.ui.SettingsScreen
import com.ner.wimap.ui.theme.WiMapTheme
import com.ner.wimap.ui.viewmodel.MainViewModel
import com.ner.wimap.ui.viewmodel.ExportFormat
import com.ner.wimap.ui.viewmodel.ExportAction

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.handlePermissionsResult(permissions)
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            viewModel = viewModel<MainViewModel>()

            WiMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WiMapApp(viewModel)
                }
            }
        }
    }

    @Composable
    fun WiMapApp(viewModel: MainViewModel) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        // Main app state
        val wifiNetworks by viewModel.wifiNetworks.collectAsState()
        val isScanning by viewModel.isScanning.collectAsState()
        val connectionStatus by viewModel.connectionStatus.collectAsState()
        val uploadStatus by viewModel.uploadStatus.collectAsState()
        val showPasswordDialog by viewModel.showPasswordDialog.collectAsState()
        val networkForPasswordInput by viewModel.networkForPasswordInput.collectAsState()
        val showPermissionRationaleDialog by viewModel.showPermissionRationaleDialog.collectAsState()
        val permissionsRationaleMessage by viewModel.permissionsRationaleMessage.collectAsState()
        val isBackgroundScanningEnabled by viewModel.isBackgroundScanningEnabled.collectAsState()
        val backgroundScanIntervalMinutes by viewModel.backgroundScanIntervalMinutes.collectAsState()
        val isAutoUploadEnabled by viewModel.isAutoUploadEnabled.collectAsState()
        val pinnedNetworks by viewModel.pinnedNetworks.collectAsState()
        val isConnecting by viewModel.isConnecting.collectAsState()
        val connectingNetworks by viewModel.connectingNetworks.collectAsState()
        val connectionProgress by viewModel.connectionProgress.collectAsState()
        val successfulPasswords by viewModel.successfulPasswords.collectAsState()

        // Export states
        val exportStatus by viewModel.exportStatus.collectAsState()
        val exportError by viewModel.exportError.collectAsState()

        // Settings state
        val ssidFilter by viewModel.ssidFilter.collectAsState()
        val securityFilter by viewModel.securityFilter.collectAsState()
        val rssiThreshold by viewModel.rssiThreshold.collectAsState()
        val passwords by viewModel.passwords.collectAsState()
        val maxRetries by viewModel.maxRetries.collectAsState()
        val connectionTimeoutSeconds by viewModel.connectionTimeoutSeconds.collectAsState()
        val rssiThresholdForConnection by viewModel.rssiThresholdForConnection.collectAsState()

        // Navigation actions
        val requestPermissionsAction by viewModel.requestPermissionsAction.collectAsState()
        val navigateToAppSettingsAction by viewModel.navigateToAppSettingsAction.collectAsState()

        // Handle export status/error feedback
        LaunchedEffect(exportStatus) {
            exportStatus?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearExportStatus()
            }
        }

        LaunchedEffect(exportError) {
            exportError?.let {
                snackbarHostState.showSnackbar("Export Error: $it")
                viewModel.clearExportError()
            }
        }

        // Handle permission requests
        LaunchedEffect(requestPermissionsAction) {
            requestPermissionsAction?.let { permissions ->
                requestPermissionsLauncher.launch(permissions.toTypedArray())
                viewModel.onPermissionsRequestLaunched()
            }
        }

        // Handle navigation to app settings
        LaunchedEffect(navigateToAppSettingsAction) {
            if (navigateToAppSettingsAction) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
                viewModel.onAppSettingsOpened()
            }
        }

        NavHost(
            navController = navController,
            startDestination = "main"
        ) {
            composable("main") {
                MainScreen(
                    wifiNetworks = wifiNetworks,
                    isScanning = isScanning,
                    connectionStatus = connectionStatus,
                    uploadStatus = uploadStatus,
                    showPasswordDialog = showPasswordDialog,
                    networkForPasswordInput = networkForPasswordInput,
                    showPermissionRationaleDialog = showPermissionRationaleDialog,
                    permissionsRationaleMessage = permissionsRationaleMessage,
                    isBackgroundScanningEnabled = isBackgroundScanningEnabled,
                    backgroundScanIntervalMinutes = backgroundScanIntervalMinutes,
                    isAutoUploadEnabled = isAutoUploadEnabled,
                    pinnedNetworks = pinnedNetworks,
                    isConnecting = isConnecting,
                    connectingNetworks = connectingNetworks,
                    connectionProgress = connectionProgress,
                    successfulPasswords = successfulPasswords,
                    onStartScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onConnect = { network -> viewModel.connectToNetwork(network) },
                    onPasswordEntered = { password -> viewModel.onPasswordEntered(password) },
                    onDismissPasswordDialog = { viewModel.dismissPasswordDialog() },
                    onDismissPermissionRationaleDialog = { viewModel.dismissPermissionRationaleDialog() },
                    onRationalePermissionsRequest = { viewModel.onUserApprovesRationaleRequest() },
                    onRationaleOpenSettings = { viewModel.onUserRequestsOpenSettings() },
                    onToggleBackgroundScanning = { enabled -> viewModel.toggleBackgroundScanning(enabled) },
                    onSetBackgroundScanInterval = { minutes -> viewModel.setBackgroundScanInterval(minutes) },
                    onToggleAutoUpload = { enabled -> viewModel.toggleAutoUpload(enabled) },
                    onUploadScanResults = { viewModel.uploadScanResultsToFirebase() },
                    onClearUploadStatus = { viewModel.clearUploadStatus() },
                    onOpenSettings = { navController.navigate("settings") },
                    onExportWithFormatAndAction = { format, action ->
                        viewModel.exportWifiNetworks(this@MainActivity, format, action)
                    },
                    onShareCsv = { viewModel.shareCsv(this@MainActivity) },
                    onClearNetworks = { viewModel.clearNetworks() },
                    onOpenPinnedNetworks = { navController.navigate("pinned_networks") },
                    onPinNetwork = { network, comment, password, photoUri ->
                        viewModel.pinNetwork(network, comment, password, photoUri)
                    },
                    onUnpinNetwork = { bssid -> viewModel.unpinNetwork(bssid) },
                    onClearConnectionProgress = { viewModel.clearConnectionProgress() },
                    onCancelConnection = { bssid -> viewModel.cancelConnection(bssid) },
                    onUpdateNetworkData = { network, comment, password, photoUri ->
                        viewModel.updateNetworkData(network, comment, password, photoUri)
                    },
                    onOpenMaps = {
                        // Open Google Maps activity with current networks
                        val intent = MapsActivity.createIntent(this@MainActivity, wifiNetworks)
                        startActivity(intent)
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    ssidFilter = ssidFilter,
                    onSsidFilterChange = { viewModel.onSsidFilterChange(it) },
                    securityFilter = securityFilter,
                    onSecurityFilterChange = { viewModel.onSecurityFilterChange(it) },
                    rssiThreshold = rssiThreshold,
                    onRssiThresholdChange = { viewModel.onRssiThresholdChange(it) },
                    passwords = passwords,
                    onAddPassword = { viewModel.onAddPassword(it) },
                    onRemovePassword = { viewModel.onRemovePassword(it) },
                    maxRetries = maxRetries,
                    onMaxRetriesChange = { viewModel.setMaxRetries(it) },
                    connectionTimeoutSeconds = connectionTimeoutSeconds,
                    onConnectionTimeoutChange = { viewModel.setConnectionTimeoutSeconds(it) },
                    rssiThresholdForConnection = rssiThresholdForConnection,
                    onRssiThresholdForConnectionChange = { viewModel.setRssiThresholdForConnection(it) },
                    onClearAllData = { viewModel.clearAllLocalData() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("pinned_networks") {
                PinnedNetworksScreen(
                    pinnedNetworks = pinnedNetworks,
                    successfulPasswords = successfulPasswords,
                    connectingNetworks = connectingNetworks,
                    onBack = { navController.popBackStack() },
                    onDeletePinnedNetwork = { network ->
                        viewModel.deletePinnedNetwork(network)
                    },
                    onConnectToPinnedNetwork = { network ->
                        viewModel.connectToPinnedNetwork(network)
                    },
                    onSharePinnedNetwork = { network ->
                        viewModel.sharePinnedNetwork(this@MainActivity, network)
                    },
                    onExportPinnedNetwork = { network, format, action ->
                        viewModel.exportPinnedNetwork(this@MainActivity, network, format, action)
                    }
                )
            }
        }
    }
}