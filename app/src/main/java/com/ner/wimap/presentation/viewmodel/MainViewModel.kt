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
import com.ner.wimap.domain.usecase.ManageTemporaryNetworkDataUseCase
import com.ner.wimap.domain.usecase.ScanWifiNetworksUseCase
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ui.viewmodel.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

// Sorting modes for Wi-Fi networks
enum class SortingMode {
    LAST_SEEN,      // Last seen time → newest first
    SIGNAL_STRENGTH, // Signal strength (RSSI) → strongest first
    SSID_ALPHABETICAL // SSID (A-Z) → alphabetical order
}

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val scanWifiNetworksUseCase: ScanWifiNetworksUseCase,
    private val connectToNetworkUseCase: ConnectToNetworkUseCase,
    private val managePinnedNetworksUseCase: ManagePinnedNetworksUseCase,
    private val manageTemporaryNetworkDataUseCase: ManageTemporaryNetworkDataUseCase,
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

    private val _showEmptyPasswordListDialog = MutableStateFlow(false)
    val showEmptyPasswordListDialog: StateFlow<Boolean> = _showEmptyPasswordListDialog.asStateFlow()

    private val _networkForEmptyPasswordDialog = MutableStateFlow<WifiNetwork?>(null)
    val networkForEmptyPasswordDialog: StateFlow<WifiNetwork?> = _networkForEmptyPasswordDialog.asStateFlow()

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

    private val _connectionSuccessMessage = MutableStateFlow<String?>(null)
    val connectionSuccessMessage: StateFlow<String?> = _connectionSuccessMessage.asStateFlow()

    // Filter settings
    private val _ssidFilter = MutableStateFlow("")
    val ssidFilter: StateFlow<String> = _ssidFilter.asStateFlow()

    private val _securityFilter = MutableStateFlow(emptySet<String>())
    val securityFilter: StateFlow<Set<String>> = _securityFilter.asStateFlow()

    private val _rssiThreshold = MutableStateFlow("-70")
    val rssiThreshold: StateFlow<String> = _rssiThreshold.asStateFlow()

    private val _bssidFilter = MutableStateFlow("")
    val bssidFilter: StateFlow<String> = _bssidFilter.asStateFlow()

    // Sorting settings
    private val _sortingMode = MutableStateFlow(SortingMode.LAST_SEEN)
    val sortingMode: StateFlow<SortingMode> = _sortingMode.asStateFlow()

    // Use case flows
    private val rawWifiNetworks = scanWifiNetworksUseCase.getWifiNetworks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Enhanced networks with temporary data
    private val enhancedNetworks = combine(
        rawWifiNetworks,
        manageTemporaryNetworkDataUseCase.getAllTemporaryData()
    ) { networks, temporaryDataList ->
        val temporaryDataMap = temporaryDataList.associateBy { it.bssid }
        networks.map { network ->
            val temporaryData = temporaryDataMap[network.bssid]
            if (temporaryData != null) {
                network.copy(
                    comment = temporaryData.comment,
                    password = temporaryData.savedPassword,
                    photoPath = temporaryData.photoPath,
                    isPinned = temporaryData.isPinned
                )
            } else {
                network
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Filtered networks based on current filter settings
    private val filteredNetworks = combine(
        enhancedNetworks,
        _ssidFilter,
        _securityFilter,
        _rssiThreshold,
        _bssidFilter
    ) { networks, ssidFilter, securityFilter, rssiThreshold, bssidFilter ->
        applyFilters(networks, ssidFilter, securityFilter, rssiThreshold, bssidFilter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Sorted networks based on current sorting mode
    val wifiNetworks = combine(
        filteredNetworks,
        _sortingMode
    ) { networks, sortingMode ->
        applySorting(networks, sortingMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val isScanning = scanWifiNetworksUseCase.isScanning()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // Track if a scan has ever been started
    private val _hasEverScanned = MutableStateFlow(false)
    val hasEverScanned: StateFlow<Boolean> = _hasEverScanned.asStateFlow()

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

    // Enhanced pinned networks that combine pinned data with temporary modifications
    val pinnedNetworks = combine(
        managePinnedNetworksUseCase.getAllPinnedNetworks(),
        manageTemporaryNetworkDataUseCase.getAllTemporaryData()
    ) { pinnedNetworksList, temporaryDataList ->
        val temporaryDataMap = temporaryDataList.associateBy { it.bssid }
        pinnedNetworksList.map { pinnedNetwork ->
            val temporaryData = temporaryDataMap[pinnedNetwork.bssid]
            if (temporaryData != null) {
                // Merge temporary data into pinned network for live updates
                // CRITICAL: Handle photo deletion properly - if either source has null photo, use null
                val mergedPhotoUri = when {
                    temporaryData.photoPath == null && pinnedNetwork.photoUri == null -> null
                    temporaryData.photoPath == null -> null // Temporary data explicitly deleted photo
                    pinnedNetwork.photoUri == null -> null // Pinned data explicitly deleted photo
                    else -> temporaryData.photoPath ?: pinnedNetwork.photoUri
                }
                
                pinnedNetwork.copy(
                    comment = temporaryData.comment.takeIf { it.isNotEmpty() } ?: pinnedNetwork.comment,
                    savedPassword = temporaryData.savedPassword ?: pinnedNetwork.savedPassword,
                    photoUri = mergedPhotoUri
                )
            } else {
                pinnedNetwork
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

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
                // Clear previous results before starting a fresh scan
                scanWifiNetworksUseCase.clearNetworks()
                
                // Mark that a scan has been started
                _hasEverScanned.value = true
                
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
        // Check if network is secured and password list is empty
        val isOpenNetwork = network.security.contains("Open", ignoreCase = true) ||
                           network.security.contains("OPEN", ignoreCase = true)
        
        if (!isOpenNetwork && _passwords.value.isEmpty()) {
            // Show empty password list dialog instead of attempting connection
            _networkForEmptyPasswordDialog.value = network
            _showEmptyPasswordListDialog.value = true
            return
        }
        
        // Validate RSSI threshold before attempting connection
        val rssiThreshold = _rssiThresholdForConnection.value
        if (network.rssi < rssiThreshold) {
            _permissionsRationaleMessage.value = "Signal too weak (${network.rssi}dBm < ${rssiThreshold}dBm). Connection not attempted."
            _showPermissionRationaleDialog.value = true
            return
        }
        
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
        connectToNetworkUseCase.clearConnectionProgress()
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
            // Also update temporary data to reflect that network is no longer pinned
            val existingTempData = manageTemporaryNetworkDataUseCase.getTemporaryDataByBssid(network.bssid)
            if (existingTempData != null) {
                manageTemporaryNetworkDataUseCase.saveOrUpdateTemporaryNetworkData(
                    bssid = network.bssid,
                    ssid = existingTempData.ssid,
                    comment = existingTempData.comment,
                    password = existingTempData.savedPassword,
                    photoPath = existingTempData.photoPath,
                    isPinned = false
                )
            }
        }
    }

    fun updateNetworkData(network: WifiNetwork, comment: String?, password: String?, photoUri: String?) {
        viewModelScope.launch {
            managePinnedNetworksUseCase.updateNetworkData(network, comment, password, photoUri)
        }
    }

    fun updatePinnedNetworkDataWithPhotoDeletion(
        network: WifiNetwork, 
        comment: String?, 
        password: String?, 
        photoUri: String?,
        clearPhoto: Boolean
    ) {
        viewModelScope.launch {
            managePinnedNetworksUseCase.updateNetworkDataWithPhotoDeletion(network, comment, password, photoUri, clearPhoto)
        }
    }

    // Temporary network data functions
    fun updateTemporaryNetworkData(bssid: String, ssid: String, comment: String?, password: String?, photoPath: String?) {
        android.util.Log.d("MainViewModel", "Updating temporary network data for BSSID $bssid: comment='$comment', hasPassword=${password != null}, hasPhoto=${photoPath != null}")
        viewModelScope.launch {
            manageTemporaryNetworkDataUseCase.saveOrUpdateTemporaryNetworkData(
                bssid = bssid,
                ssid = ssid,
                comment = comment ?: "",
                password = password,
                photoPath = photoPath,
                isPinned = null // Don't change pin status when just updating data
            )
            
            // If this network is currently pinned, also update the pinned network data for consistency
            val currentPinnedNetworks = pinnedNetworks.value
            val isPinnedNetwork = currentPinnedNetworks.any { it.bssid == bssid }
            if (isPinnedNetwork) {
                val network = wifiNetworks.value.find { it.bssid == bssid }
                if (network != null) {
                    android.util.Log.d("MainViewModel", "Also updating pinned network data for consistency")
                    managePinnedNetworksUseCase.updateNetworkData(network, comment, password, photoPath)
                }
            }
        }
    }

    fun updateTemporaryNetworkDataWithPhotoDeletion(
        bssid: String, 
        ssid: String, 
        comment: String?, 
        password: String?, 
        photoPath: String?, 
        clearPhoto: Boolean
    ) {
        android.util.Log.d("MainViewModel", "Updating temporary network data with photo deletion for BSSID $bssid: clearPhoto=$clearPhoto")
        viewModelScope.launch {
            // Check if this is a pinned network BEFORE making any updates
            val currentPinnedNetworks = pinnedNetworks.value
            val isPinnedNetwork = currentPinnedNetworks.any { it.bssid == bssid }
            
            if (isPinnedNetwork && clearPhoto) {
                // For pinned networks with photo deletion, update both sources atomically
                val network = wifiNetworks.value.find { it.bssid == bssid }
                if (network != null) {
                    android.util.Log.d("MainViewModel", "Atomically updating both pinned and temporary data for photo deletion")
                    
                    // Update pinned data first to ensure permanent deletion
                    managePinnedNetworksUseCase.updateNetworkDataWithPhotoDeletion(network, comment, password, null, clearPhoto)
                    
                    // Then update temporary data to match, ensuring photoPath is null
                    manageTemporaryNetworkDataUseCase.saveOrUpdateTemporaryNetworkData(
                        bssid = bssid,
                        ssid = ssid,
                        comment = comment ?: "",
                        password = password,
                        photoPath = null, // Explicitly set to null for photo deletion
                        isPinned = null,
                        clearPhoto = clearPhoto
                    )
                } else {
                    android.util.Log.w("MainViewModel", "Could not find network with BSSID $bssid in current wifi networks")
                }
            } else {
                // For non-pinned networks or non-deletion updates, use normal flow
                manageTemporaryNetworkDataUseCase.saveOrUpdateTemporaryNetworkData(
                    bssid = bssid,
                    ssid = ssid,
                    comment = comment ?: "",
                    password = password,
                    photoPath = photoPath,
                    isPinned = null,
                    clearPhoto = clearPhoto
                )
                
                // Update pinned data if network is pinned (for non-deletion cases)
                if (isPinnedNetwork && !clearPhoto) {
                    val network = wifiNetworks.value.find { it.bssid == bssid }
                    if (network != null) {
                        android.util.Log.d("MainViewModel", "Also updating pinned network data for consistency")
                        managePinnedNetworksUseCase.updateNetworkData(network, comment, password, photoPath)
                    }
                }
            }
        }
    }

    fun pinNetworkWithTemporaryData(bssid: String, isPinned: Boolean) {
        viewModelScope.launch {
            // Update the pin status in temporary data
            val existingData = manageTemporaryNetworkDataUseCase.getTemporaryDataByBssid(bssid)
            if (existingData != null) {
                manageTemporaryNetworkDataUseCase.saveOrUpdateTemporaryNetworkData(
                    bssid = bssid,
                    ssid = existingData.ssid,
                    comment = existingData.comment,
                    password = existingData.savedPassword,
                    photoPath = existingData.photoPath,
                    isPinned = isPinned
                )
            }
            
            // Also update the pinned networks table if pinning
            if (isPinned) {
                val network = wifiNetworks.value.find { it.bssid == bssid }
                if (network != null) {
                    pinNetwork(network, existingData?.comment, existingData?.savedPassword, existingData?.photoPath)
                }
            } else {
                unpinNetwork(bssid)
            }
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

    fun dismissEmptyPasswordListDialog() {
        _showEmptyPasswordListDialog.value = false
        _networkForEmptyPasswordDialog.value = null
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
        saveFiltersToPreferences()
    }

    fun onSecurityFilterChange(newFilter: Set<String>) {
        _securityFilter.value = newFilter
        saveFiltersToPreferences()
    }

    fun onRssiThresholdChange(newThreshold: String) {
        _rssiThreshold.value = newThreshold
        saveFiltersToPreferences()
    }

    fun onBssidFilterChange(newFilter: String) {
        _bssidFilter.value = newFilter
        saveFiltersToPreferences()
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

    fun clearConnectionSuccessMessage() {
        _connectionSuccessMessage.value = null
    }

    fun clearConnectionStatus() {
        // Clear connection status to prevent re-display when returning from other screens
        connectToNetworkUseCase.clearConnectionStatus()
    }

    // Monitor connection status for success notifications
    private fun monitorConnectionSuccess() {
        viewModelScope.launch {
            connectionStatus.collect { status ->
                if (status != null && status.contains("✅ Connected to")) {
                    // Extract network name from success message
                    val networkName = status.substringAfter("✅ Connected to ").substringBefore(" with")
                    _connectionSuccessMessage.value = "Connected successfully to $networkName. Password saved."
                }
            }
        }
    }

    // SharedPreferences for password storage
    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("wimap_settings", Context.MODE_PRIVATE)
    
    // Additional properties needed for compatibility
    private val _passwords = MutableStateFlow<List<String>>(emptyList())
    val passwords: StateFlow<List<String>> = _passwords.asStateFlow()

    private val _maxRetries = MutableStateFlow(3)
    val maxRetries: StateFlow<Int> = _maxRetries.asStateFlow()

    private val _connectionTimeoutSeconds = MutableStateFlow(10)
    val connectionTimeoutSeconds: StateFlow<Int> = _connectionTimeoutSeconds.asStateFlow()

    private val _rssiThresholdForConnection = MutableStateFlow(-70)
    val rssiThresholdForConnection: StateFlow<Int> = _rssiThresholdForConnection.asStateFlow()

    private val _hideNetworksUnseenForSeconds = MutableStateFlow(30)
    val hideNetworksUnseenForSeconds: StateFlow<Int> = _hideNetworksUnseenForSeconds.asStateFlow()

    init {
        // Load all settings from SharedPreferences on initialization
        loadPasswordsFromPreferences()
        loadFiltersFromPreferences()
        loadConnectionSettingsFromPreferences()
        loadSortingFromPreferences()
        
        // Start periodic cleanup of stale networks
        startPeriodicNetworkCleanup()
        
        // Monitor connection status for success notifications
        monitorConnectionSuccess()
    }

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
        _maxRetries.value = retries.coerceIn(1, 10)
        saveConnectionSettingsToPreferences()
        // Update ConnectionManager with new settings
        viewModelScope.launch {
            connectToNetworkUseCase.updateConnectionSettings(
                maxRetries = _maxRetries.value,
                timeoutSeconds = _connectionTimeoutSeconds.value,
                rssiThreshold = _rssiThresholdForConnection.value
            )
        }
    }

    fun setConnectionTimeoutSeconds(timeout: Int) {
        _connectionTimeoutSeconds.value = timeout.coerceIn(5, 60)
        saveConnectionSettingsToPreferences()
        // Update ConnectionManager with new settings
        viewModelScope.launch {
            connectToNetworkUseCase.updateConnectionSettings(
                maxRetries = _maxRetries.value,
                timeoutSeconds = _connectionTimeoutSeconds.value,
                rssiThreshold = _rssiThresholdForConnection.value
            )
        }
    }

    fun setRssiThresholdForConnection(threshold: Int) {
        _rssiThresholdForConnection.value = threshold.coerceIn(-100, -30)
        saveConnectionSettingsToPreferences()
        // Update ConnectionManager with new settings
        viewModelScope.launch {
            connectToNetworkUseCase.updateConnectionSettings(
                maxRetries = _maxRetries.value,
                timeoutSeconds = _connectionTimeoutSeconds.value,
                rssiThreshold = _rssiThresholdForConnection.value
            )
        }
    }

    fun setHideNetworksUnseenForSeconds(seconds: Int) {
        _hideNetworksUnseenForSeconds.value = seconds
        saveConnectionSettingsToPreferences()
    }

    // Clear all data function
    fun clearAllData() {
        viewModelScope.launch {
            try {
                // Clear all SharedPreferences
                sharedPreferences.edit().clear().apply()
                
                // Clear working passwords SharedPreferences
                val workingPasswordsPrefs = getApplication<Application>().getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
                workingPasswordsPrefs.edit().clear().apply()
                
                // Clear all pinned networks from database
                managePinnedNetworksUseCase.clearAllPinnedNetworks()
                
                // Clear all temporary network data from database
                manageTemporaryNetworkDataUseCase.clearAllTemporaryData()
                
                // Clear current networks list
                scanWifiNetworksUseCase.clearNetworks()
                
                // Reset all state flows to default values
                _passwords.value = emptyList()
                _ssidFilter.value = ""
                _securityFilter.value = emptySet()
                _rssiThreshold.value = "-70"
                _bssidFilter.value = ""
                _maxRetries.value = 3
                _connectionTimeoutSeconds.value = 10
                _rssiThresholdForConnection.value = -70
                _hideNetworksUnseenForSeconds.value = 30
                _isBackgroundScanningEnabled.value = false
                _backgroundScanIntervalMinutes.value = 15
                _isAutoUploadEnabled.value = true
                
                // Update ConnectionManager with empty passwords
                connectToNetworkUseCase.updatePasswordsFromSettings(emptyList())
                
            } catch (e: Exception) {
                // Handle error if needed
                _uploadStatus.value = "Error clearing data: ${e.message}"
            }
        }
    }

    // Legacy functions for backward compatibility
    fun shareCsv(context: Context) {
        exportWifiNetworks(context, ExportFormat.CSV, ExportAction.SHARE_ONLY)
    }

    fun sharePinnedNetwork(context: Context, network: PinnedNetwork) {
        exportPinnedNetwork(context, network, ExportFormat.CSV, ExportAction.SHARE_ONLY)
    }

    // Periodic network cleanup
    private fun startPeriodicNetworkCleanup() {
        viewModelScope.launch {
            while (true) {
                delay(30 * 60 * 1000L) // Run every 30 minutes
                try {
                    scanWifiNetworksUseCase.removeStaleNetworks(_hideNetworksUnseenForSeconds.value)
                } catch (e: Exception) {
                    // Silently handle cleanup errors
                }
            }
        }
    }

    // Filter persistence
    private fun saveFiltersToPreferences() {
        sharedPreferences.edit()
            .putString("ssid_filter", _ssidFilter.value)
            .putStringSet("security_filter", _securityFilter.value)
            .putString("rssi_threshold", _rssiThreshold.value)
            .putString("bssid_filter", _bssidFilter.value)
            .apply()
    }

    private fun loadFiltersFromPreferences() {
        _ssidFilter.value = sharedPreferences.getString("ssid_filter", "") ?: ""
        _securityFilter.value = sharedPreferences.getStringSet("security_filter", emptySet()) ?: emptySet()
        _rssiThreshold.value = sharedPreferences.getString("rssi_threshold", "-70") ?: "-70"
        _bssidFilter.value = sharedPreferences.getString("bssid_filter", "") ?: ""
    }

    // Filtering logic
    private fun applyFilters(
        networks: List<WifiNetwork>,
        ssidFilter: String,
        securityFilter: Set<String>,
        rssiThreshold: String,
        bssidFilter: String
    ): List<WifiNetwork> {
        return networks.filter { network ->
            // SSID Filter - partial match (case insensitive)
            val ssidMatch = if (ssidFilter.isBlank()) {
                true
            } else {
                network.ssid.contains(ssidFilter, ignoreCase = true)
            }

            // Security Filter - multi-select match
            val securityMatch = if (securityFilter.isEmpty()) {
                true
            } else {
                securityFilter.any { filterType ->
                    network.security.contains(filterType, ignoreCase = true)
                }
            }

            // RSSI Threshold Filter
            val rssiMatch = try {
                val threshold = rssiThreshold.toIntOrNull() ?: -70
                network.rssi >= threshold
            } catch (e: Exception) {
                true // If parsing fails, don't filter
            }

            // BSSID Filter - supports comma-separated list and partial matches
            val bssidMatch = if (bssidFilter.isBlank()) {
                true
            } else {
                val bssidFilters = bssidFilter.split(",").map { it.trim() }
                bssidFilters.any { filter ->
                    if (filter.isNotBlank()) {
                        network.bssid.contains(filter, ignoreCase = true)
                    } else {
                        true
                    }
                }
            }

            ssidMatch && securityMatch && rssiMatch && bssidMatch
        }
    }

    // Available security types for filtering
    val availableSecurityTypes = listOf(
        "WPA3",
        "WPA2", 
        "WPA",
        "WEP",
        "OPEN",
        "OWE",
        "SAE",
        "PSK",
        "EAP"
    )
    // Sorting functions
    fun setSortingMode(mode: SortingMode) {
        _sortingMode.value = mode
        saveSortingToPreferences()
    }

    // Sorting logic
    private fun applySorting(networks: List<WifiNetwork>, sortingMode: SortingMode): List<WifiNetwork> {
        return when (sortingMode) {
            SortingMode.LAST_SEEN -> networks.sortedByDescending { it.lastSeenTimestamp }
            SortingMode.SIGNAL_STRENGTH -> networks.sortedByDescending { it.rssi }
            SortingMode.SSID_ALPHABETICAL -> networks.sortedBy { it.ssid.lowercase() }
        }
    }

    // Sorting persistence
    private fun saveSortingToPreferences() {
        sharedPreferences.edit()
            .putString("sorting_mode", _sortingMode.value.name)
            .apply()
    }

    private fun loadSortingFromPreferences() {
        val savedSortingMode = sharedPreferences.getString("sorting_mode", SortingMode.LAST_SEEN.name)
        _sortingMode.value = try {
            SortingMode.valueOf(savedSortingMode ?: SortingMode.LAST_SEEN.name)
        } catch (e: IllegalArgumentException) {
            SortingMode.LAST_SEEN
        }
    }

    // Connection settings persistence
    private fun loadConnectionSettingsFromPreferences() {
        _maxRetries.value = sharedPreferences.getInt("max_retries", 3).coerceIn(1, 10)
        _connectionTimeoutSeconds.value = sharedPreferences.getInt("connection_timeout_seconds", 10).coerceIn(5, 60)
        _rssiThresholdForConnection.value = sharedPreferences.getInt("rssi_threshold_for_connection", -70).coerceIn(-100, -30)
        _hideNetworksUnseenForSeconds.value = sharedPreferences.getInt("hide_networks_unseen_for_seconds", 30)
        
        // Update ConnectionManager with loaded settings
        viewModelScope.launch {
            connectToNetworkUseCase.updateConnectionSettings(
                maxRetries = _maxRetries.value,
                timeoutSeconds = _connectionTimeoutSeconds.value,
                rssiThreshold = _rssiThresholdForConnection.value
            )
        }
    }

    private fun saveConnectionSettingsToPreferences() {
        sharedPreferences.edit()
            .putInt("max_retries", _maxRetries.value)
            .putInt("connection_timeout_seconds", _connectionTimeoutSeconds.value)
            .putInt("rssi_threshold_for_connection", _rssiThresholdForConnection.value)
            .putInt("hide_networks_unseen_for_seconds", _hideNetworksUnseenForSeconds.value)
            .apply()
    }
}
