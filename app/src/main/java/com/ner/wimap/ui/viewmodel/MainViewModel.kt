package com.ner.wimap.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ner.wimap.FirebaseRepository
import com.ner.wimap.Result
import com.ner.wimap.LocationProvider
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.data.database.AppDatabase
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.wifi.WifiScanner
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val locationProvider = LocationProvider(application)
    private val wifiScanner = WifiScanner(application, locationProvider.currentLocation)
    private val firebaseRepository = FirebaseRepository()
    private val database = AppDatabase.getDatabase(application)
    private val pinnedNetworkDao = database.pinnedNetworkDao()

    // Managers
    private val scanManager = ScanManager(application, wifiScanner, locationProvider, viewModelScope)
    private val connectionManager = ConnectionManager(wifiScanner, firebaseRepository, pinnedNetworkDao, viewModelScope)
    private val pinnedNetworksManager = PinnedNetworksManager(pinnedNetworkDao, viewModelScope)
    private val exportManager = ExportManager(viewModelScope)

    // Dialog states
    private val _showPasswordDialog = MutableStateFlow(false)
    val showPasswordDialog: StateFlow<Boolean> = _showPasswordDialog

    private val _networkForPasswordInput = MutableStateFlow<WifiNetwork?>(null)
    val networkForPasswordInput: StateFlow<WifiNetwork?> = _networkForPasswordInput

    private val _showPermissionRationaleDialog = MutableStateFlow(false)
    val showPermissionRationaleDialog: StateFlow<Boolean> = _showPermissionRationaleDialog

    private val _permissionsRationaleMessage = MutableStateFlow<String?>(null)
    val permissionsRationaleMessage: StateFlow<String?> = _permissionsRationaleMessage

    // Permission actions
    private val _requestPermissionsAction = MutableStateFlow<List<String>?>(null)
    val requestPermissionsAction: StateFlow<List<String>?> = _requestPermissionsAction

    private val _navigateToAppSettingsAction = MutableStateFlow(false)
    val navigateToAppSettingsAction: StateFlow<Boolean> = _navigateToAppSettingsAction

    // Settings
    private val _isBackgroundScanningEnabled = MutableStateFlow(false)
    val isBackgroundScanningEnabled: StateFlow<Boolean> = _isBackgroundScanningEnabled

    private val _backgroundScanIntervalMinutes = MutableStateFlow(15)
    val backgroundScanIntervalMinutes: StateFlow<Int> = _backgroundScanIntervalMinutes

    private val _isAutoUploadEnabled = MutableStateFlow(true)
    val isAutoUploadEnabled: StateFlow<Boolean> = _isAutoUploadEnabled

    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus

    // Filter settings
    private val _ssidFilter = MutableStateFlow("")
    val ssidFilter: StateFlow<String> = _ssidFilter

    private val _securityFilter = MutableStateFlow("")
    val securityFilter: StateFlow<String> = _securityFilter

    private val _rssiThreshold = MutableStateFlow("-70")
    val rssiThreshold: StateFlow<String> = _rssiThreshold

    // Expose manager states
    val wifiNetworks = scanManager.wifiNetworks
    val isScanning = scanManager.isScanning
    val isConnecting = connectionManager.isConnecting
    val connectingNetworks = connectionManager.connectingNetworks
    val connectionProgress = connectionManager.connectionProgress
    val connectionStatus = connectionManager.connectionStatus
    val successfulPasswords = connectionManager.successfulPasswords
    val pinnedNetworks = pinnedNetworksManager.pinnedNetworks
    val passwords = connectionManager.passwords
    val maxRetries = connectionManager.maxRetries
    val connectionTimeoutSeconds = connectionManager.connectionTimeoutSeconds
    val rssiThresholdForConnection = connectionManager.rssiThresholdForConnection

    // Export manager states
    val exportStatus = exportManager.exportStatus
    val exportError = exportManager.errorMessage

    init {
        // Initialize managers
        scanManager.initialize()
        connectionManager.initializeTestPasswords()
        pinnedNetworksManager.initialize()

        // Set up auto-upload
        viewModelScope.launch {
            wifiNetworks.collect { networks ->
                if (_isAutoUploadEnabled.value && networks.isNotEmpty()) {
                    uploadScanResultsToFirebase(showNotifications = false)
                }
            }
        }
    }

    // Scanning functions
    fun startScan() = scanManager.startScan { message ->
        _permissionsRationaleMessage.value = message
        _showPermissionRationaleDialog.value = true
    }

    fun stopScan() = scanManager.stopScan()

    fun clearNetworks() {
        scanManager.clearNetworks()
        pinnedNetworksManager.clearStatusMessage()
    }

    // Connection functions
    fun connectToNetwork(network: WifiNetwork) {
        connectionManager.connectToNetwork(network) { networkForPassword ->
            _networkForPasswordInput.value = networkForPassword
            _showPasswordDialog.value = true
        }
    }

    fun onPasswordEntered(password: String) {
        _networkForPasswordInput.value?.let { network ->
            connectionManager.connectWithManualPassword(network, password)
        }
        dismissPasswordDialog()
    }

    fun connectToPinnedNetwork(network: PinnedNetwork) {
        val wifiNetwork = WifiNetwork(
            ssid = network.ssid,
            bssid = network.bssid,
            rssi = network.rssi,
            channel = network.channel,
            security = network.security,
            latitude = network.latitude,
            longitude = network.longitude,
            timestamp = network.timestamp
        )

        if (!network.savedPassword.isNullOrEmpty()) {
            connectionManager.connectWithManualPassword(wifiNetwork, network.savedPassword)
        } else {
            connectToNetwork(wifiNetwork)
        }
    }

    fun clearConnectionProgress() = connectionManager.clearConnectionProgress()

    // Pinned network functions
    fun pinNetwork(network: WifiNetwork, comment: String?, password: String?, photoUri: String?) =
        pinnedNetworksManager.pinNetwork(network, comment, password, photoUri)

    fun unpinNetwork(bssid: String) = pinnedNetworksManager.unpinNetwork(bssid)

    fun deletePinnedNetwork(network: PinnedNetwork) = pinnedNetworksManager.deletePinnedNetwork(network)

    fun updateNetworkData(network: WifiNetwork, comment: String?, password: String?, photoUri: String?) =
        pinnedNetworksManager.updateNetworkData(network, comment, password, photoUri)

    // Legacy export/share functions (keep for backward compatibility)
    fun exportToCsv(context: Context): String = scanManager.exportToCsv(context)

    fun shareCsv(context: Context) = scanManager.shareCsv(context)

    fun exportPinnedNetworkToCsv(context: Context, network: PinnedNetwork): String =
        pinnedNetworksManager.exportPinnedNetworkToCsv(context, network)

    fun sharePinnedNetwork(context: Context, network: PinnedNetwork) =
        pinnedNetworksManager.sharePinnedNetwork(context, network)

    // New export functions with format and action options
    fun exportWifiNetworks(context: Context, format: ExportFormat, action: ExportAction) {
        exportManager.exportWifiNetworks(context, wifiNetworks.value, format, action)
    }

    fun exportPinnedNetwork(context: Context, network: PinnedNetwork, format: ExportFormat, action: ExportAction) {
        exportManager.exportPinnedNetwork(context, network, format, action)
    }

    fun clearExportStatus() = exportManager.clearStatus()

    fun clearExportError() = exportManager.clearError()

    // Dialog management
    fun dismissPasswordDialog() {
        _showPasswordDialog.value = false
        _networkForPasswordInput.value = null
    }

    fun dismissPermissionRationaleDialog() {
        _showPermissionRationaleDialog.value = false
    }

    fun onUserApprovesRationaleRequest() {
        _showPermissionRationaleDialog.value = false
        _requestPermissionsAction.value = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE
        )
    }

    fun onUserRequestsOpenSettings() {
        _showPermissionRationaleDialog.value = false
        _navigateToAppSettingsAction.value = true
    }

    fun onPermissionsRequestLaunched() {
        _requestPermissionsAction.value = null
    }

    fun onAppSettingsOpened() {
        _navigateToAppSettingsAction.value = false
    }

    fun handlePermissionsResult(grantedPermissionsMap: Map<String, Boolean>) {
        val allGranted = grantedPermissionsMap.values.all { it }
        if (allGranted) {
            startScan()
        }
    }

    // Settings functions
    fun toggleBackgroundScanning(isEnabled: Boolean) {
        _isBackgroundScanningEnabled.value = isEnabled
    }

    fun setBackgroundScanInterval(minutes: Int) {
        _backgroundScanIntervalMinutes.value = minutes
    }

    fun toggleAutoUpload(isEnabled: Boolean) {
        _isAutoUploadEnabled.value = isEnabled
        if (isEnabled && wifiNetworks.value.isNotEmpty()) {
            uploadScanResultsToFirebase()
        }
    }

    fun onSsidFilterChange(newFilter: String) {
        _ssidFilter.value = newFilter
    }

    fun onSecurityFilterChange(newFilter: String) {
        _securityFilter.value = newFilter
    }

    fun onRssiThresholdChange(newThreshold: String) {
        _rssiThreshold.value = newThreshold
    }

    fun onAddPassword(password: String) = connectionManager.addPassword(password)

    fun onRemovePassword(password: String) = connectionManager.removePassword(password)

    fun setMaxRetries(retries: Int) = connectionManager.setMaxRetries(retries)

    fun setConnectionTimeoutSeconds(timeout: Int) = connectionManager.setConnectionTimeoutSeconds(timeout)

    fun setRssiThresholdForConnection(threshold: Int) = connectionManager.setRssiThresholdForConnection(threshold)

    // Firebase functions
    fun uploadScanResultsToFirebase(showNotifications: Boolean = true) {
        val networks = wifiNetworks.value
        if (networks.isEmpty()) {
            if (showNotifications) {
                _uploadStatus.value = "No networks to upload"
            }
            return
        }

        viewModelScope.launch {
            if (showNotifications) {
                _uploadStatus.value = "Uploading ${networks.size} networks to Firebase..."
            }

            when (val result = firebaseRepository.uploadWifiNetworks(networks)) {
                is Result.Success -> {
                    if (showNotifications) {
                        _uploadStatus.value = result.data
                    }
                }
                is Result.Failure -> {
                    if (showNotifications) {
                        _uploadStatus.value = "Upload failed: ${result.exception.message}"
                    }
                }
            }
        }
    }

    fun clearUploadStatus() {
        _uploadStatus.value = null
    }

    override fun onCleared() {
        super.onCleared()
        scanManager.cleanup()
    }
}