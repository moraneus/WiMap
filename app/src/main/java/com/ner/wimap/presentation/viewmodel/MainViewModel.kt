package com.ner.wimap.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.domain.usecase.ConnectToNetworkUseCase
import com.ner.wimap.domain.usecase.ExportNetworksUseCase
import com.ner.wimap.domain.usecase.ManagePinnedNetworksUseCase
import com.ner.wimap.domain.usecase.ScanWifiNetworksUseCase
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ui.viewmodel.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val scanWifiNetworksUseCase: ScanWifiNetworksUseCase,
    private val connectToNetworkUseCase: ConnectToNetworkUseCase,
    private val managePinnedNetworksUseCase: ManagePinnedNetworksUseCase,
    private val exportNetworksUseCase: ExportNetworksUseCase
) : AndroidViewModel(application) {

    // UI State
    private val _showPasswordDialog = MutableStateFlow(false)
    val showPasswordDialog: StateFlow<Boolean> = _showPasswordDialog.asStateFlow()

    private val _networkForPasswordInput = MutableStateFlow<WifiNetwork?>(null)
    val networkForPasswordInput: StateFlow<WifiNetwork?> = _networkForPasswordInput.asStateFlow()

    private val _showPermissionRationaleDialog = MutableStateFlow(false)
    val showPermissionRationaleDialog: StateFlow<Boolean> = _showPermissionRationaleDialog.asStateFlow()

    private val _permissionsRationaleMessage = MutableStateFlow<String?>(null)
    val permissionsRationaleMessage: StateFlow<String?> = _permissionsRationaleMessage.asStateFlow()

    // Permission actions
    private val _requestPermissionsAction = MutableStateFlow<List<String>?>(null)
    val requestPermissionsAction: StateFlow<List<String>?> = _requestPermissionsAction.asStateFlow()

    private val _navigateToAppSettingsAction = MutableStateFlow(false)
    val navigateToAppSettingsAction: StateFlow<Boolean> = _navigateToAppSettingsAction.asStateFlow()

    // Settings
    private val _isBackgroundScanningEnabled = MutableStateFlow(false)
    val isBackgroundScanningEnabled: StateFlow<Boolean> = _isBackgroundScanningEnabled.asStateFlow()

    private val _backgroundScanIntervalMinutes = MutableStateFlow(15)
    val backgroundScanIntervalMinutes: StateFlow<Int> = _backgroundScanIntervalMinutes.asStateFlow()

    private val _isAutoUploadEnabled = MutableStateFlow(true)
    val isAutoUploadEnabled: StateFlow<Boolean> = _isAutoUploadEnabled.asStateFlow()

    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus.asStateFlow()

    // Filter settings
    private val _ssidFilter = MutableStateFlow("")
    val ssidFilter: StateFlow<String> = _ssidFilter.asStateFlow()

    private val _securityFilter = MutableStateFlow("")
    val securityFilter: StateFlow<String> = _securityFilter.asStateFlow()

    private val _rssiThreshold = MutableStateFlow("-70")
    val rssiThreshold: StateFlow<String> = _rssiThreshold.asStateFlow()

    // Use case flows
    val wifiNetworks = scanWifiNetworksUseCase.getWifiNetworks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val isScanning = scanWifiNetworksUseCase.isScanning()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val connectionStatus = connectToNetworkUseCase.getConnectionStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val connectionProgress = connectToNetworkUseCase.getConnectionProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val connectingNetworks = connectToNetworkUseCase.getConnectingNetworks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptySet())

    val successfulPasswords = connectToNetworkUseCase.getSuccessfulPasswords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    // Real-time connection progress data
    val currentPassword = connectToNetworkUseCase.getCurrentPassword()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val currentAttempt = connectToNetworkUseCase.getCurrentAttempt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val totalAttempts = connectToNetworkUseCase.getTotalAttempts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0)

    val connectingNetworkName = connectToNetworkUseCase.getConnectingNetworkName()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val pinnedNetworks = managePinnedNetworksUseCase.getAllPinnedNetworks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val exportStatus = exportNetworksUseCase.getExportStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val exportError = exportNetworksUseCase.getExportError()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    // Computed properties
    val isConnecting = connectingNetworks.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Scanning functions
    fun startScan() {
        viewModelScope.launch {
            try {
                val result = scanWifiNetworksUseCase.startScan()
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    if (exception is SecurityException) {
                        _permissionsRationaleMessage.value = exception.message ?: "Location and WiFi permissions are required to scan networks."
                        _showPermissionRationaleDialog.value = true
                    } else {
                        _permissionsRationaleMessage.value = "Failed to start scan: ${exception?.message}"
                        _showPermissionRationaleDialog.value = true
                    }
                }
            } catch (e: Exception) {
                _permissionsRationaleMessage.value = "Error starting scan: ${e.message}"
                _showPermissionRationaleDialog.value = true
            }
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            scanWifiNetworksUseCase.stopScan()
        }
    }

    fun clearNetworks() {
        viewModelScope.launch {
            scanWifiNetworksUseCase.clearNetworks()
        }
    }

    // Connection functions
    fun connectToNetwork(network: WifiNetwork) {
        viewModelScope.launch {
            val result = connectToNetworkUseCase.connectToNetwork(network)
            if (result.isFailure) {
                _networkForPasswordInput.value = network
                _showPasswordDialog.value = true
            }
        }
    }

    fun onPasswordEntered(password: String) {
        _networkForPasswordInput.value?.let { network ->
            viewModelScope.launch {
                connectToNetworkUseCase.connectToNetwork(network, password)
            }
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

        viewModelScope.launch {
            val password = network.savedPassword
            if (!password.isNullOrEmpty()) {
                connectToNetworkUseCase.connectToNetwork(wifiNetwork, password)
            } else {
                connectToNetwork(wifiNetwork)
            }
        }
    }

    fun clearConnectionProgress() {
        // This would be handled by the use case or repository
    }

    // Pinned network functions
    fun pinNetwork(network: WifiNetwork, comment: String?, password: String?, photoUri: String?) {
        viewModelScope.launch {
            managePinnedNetworksUseCase.pinNetwork(network, comment, password, photoUri)
        }
    }

    fun unpinNetwork(bssid: String) {
        viewModelScope.launch {
            managePinnedNetworksUseCase.unpinNetwork(bssid)
        }
    }

    fun deletePinnedNetwork(network: PinnedNetwork) {
        viewModelScope.launch {
            managePinnedNetworksUseCase.deletePinnedNetwork(network)
        }
    }

    fun updateNetworkData(network: WifiNetwork, comment: String?, password: String?, photoUri: String?) {
        viewModelScope.launch {
            managePinnedNetworksUseCase.updateNetworkData(network, comment, password, photoUri)
        }
    }

    // Export functions
    fun exportWifiNetworks(context: Context, format: ExportFormat, action: ExportAction) {
        viewModelScope.launch {
            exportNetworksUseCase.exportWifiNetworks(context, wifiNetworks.value, format, action)
        }
    }

    fun exportPinnedNetwork(context: Context, network: PinnedNetwork, format: ExportFormat, action: ExportAction) {
        viewModelScope.launch {
            exportNetworksUseCase.exportPinnedNetwork(context, network, format, action)
        }
    }

    fun clearExportStatus() = exportNetworksUseCase.clearStatus()

    fun clearExportError() = exportNetworksUseCase.clearError()

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
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE
        )
        
        // Add NEARBY_WIFI_DEVICES permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        _requestPermissionsAction.value = permissions
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

    // Firebase functions (to be moved to a separate use case)
    fun uploadScanResultsToFirebase(showNotifications: Boolean = true) {
        // This should be moved to a separate use case
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
            // Implementation would go here
        }
    }

    fun clearUploadStatus() {
        _uploadStatus.value = null
    }

    // SharedPreferences for password storage
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("wimap_settings", Context.MODE_PRIVATE)
    
    // Additional properties needed for compatibility
    private val _passwords = MutableStateFlow<List<String>>(emptyList())
    val passwords: StateFlow<List<String>> = _passwords.asStateFlow()

    init {
        // Load passwords from SharedPreferences on initialization
        loadPasswordsFromPreferences()
    }

    private val _maxRetries = MutableStateFlow(3)
    val maxRetries: StateFlow<Int> = _maxRetries.asStateFlow()

    private val _connectionTimeoutSeconds = MutableStateFlow(10)
    val connectionTimeoutSeconds: StateFlow<Int> = _connectionTimeoutSeconds.asStateFlow()

    private val _rssiThresholdForConnection = MutableStateFlow(-70)
    val rssiThresholdForConnection: StateFlow<Int> = _rssiThresholdForConnection.asStateFlow()

    // SharedPreferences methods
    private fun loadPasswordsFromPreferences() {
        val passwordsString = sharedPreferences.getString("stored_passwords", "")
        val passwordsList = if (passwordsString.isNullOrEmpty()) {
            emptyList()
        } else {
            passwordsString.split(",").filter { it.isNotBlank() }
        }
        _passwords.value = passwordsList
        
        // Update ConnectionManager with loaded passwords
        viewModelScope.launch {
            connectToNetworkUseCase.updatePasswordsFromSettings(passwordsList)
        }
    }

    private fun savePasswordsToPreferences() {
        val passwordsString = _passwords.value.joinToString(",")
        sharedPreferences.edit()
            .putString("stored_passwords", passwordsString)
            .apply()
        
        // Update ConnectionManager with new passwords
        viewModelScope.launch {
            connectToNetworkUseCase.updatePasswordsFromSettings(_passwords.value)
        }
    }

    // Password management functions
    fun onAddPassword(password: String) {
        val currentPasswords = _passwords.value.toMutableList()
        if (!currentPasswords.contains(password)) {
            currentPasswords.add(password)
            _passwords.value = currentPasswords
            savePasswordsToPreferences()
        }
    }

    fun onRemovePassword(password: String) {
        val currentPasswords = _passwords.value.toMutableList()
        currentPasswords.remove(password)
        _passwords.value = currentPasswords
        savePasswordsToPreferences()
    }

    fun setMaxRetries(retries: Int) {
        _maxRetries.value = retries
    }

    fun setConnectionTimeoutSeconds(timeout: Int) {
        _connectionTimeoutSeconds.value = timeout
    }

    fun setRssiThresholdForConnection(threshold: Int) {
        _rssiThresholdForConnection.value = threshold
    }

    // Legacy functions for backward compatibility
    fun shareCsv(context: Context) {
        exportWifiNetworks(context, ExportFormat.CSV, ExportAction.SHARE_ONLY)
    }

    fun sharePinnedNetwork(context: Context, network: PinnedNetwork) {
        exportPinnedNetwork(context, network, ExportFormat.CSV, ExportAction.SHARE_ONLY)
    }
}