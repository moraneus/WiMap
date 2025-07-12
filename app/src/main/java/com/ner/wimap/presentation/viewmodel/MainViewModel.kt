package com.ner.wimap.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.data.database.ScanSession
import com.ner.wimap.data.database.SessionNetwork
import com.ner.wimap.data.database.ScanSessionDao
import com.ner.wimap.domain.usecase.ConnectToNetworkUseCase
import com.ner.wimap.domain.usecase.ExportNetworksUseCase
import com.ner.wimap.domain.usecase.ManagePinnedNetworksUseCase
import com.ner.wimap.domain.usecase.ManageTemporaryNetworkDataUseCase
import com.ner.wimap.domain.usecase.ScanWifiNetworksUseCase
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.service.WiFiScanService
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ui.viewmodel.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
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
    private val exportNetworksUseCase: ExportNetworksUseCase,
    private val adManager: com.ner.wimap.ads.AdManager,
    private val deviceInfoManager: com.ner.wimap.data.DeviceInfoManager,
    private val deviceInfoService: com.ner.wimap.service.DeviceInfoService,
    private val firebaseRepository: com.ner.wimap.FirebaseRepository,
    private val scanSessionDao: ScanSessionDao,
    private val gdprConsentManager: com.ner.wimap.data.GDPRConsentManager
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

    private val _showPrivacyConsentDialog = MutableStateFlow(false)
    val showPrivacyConsentDialog: StateFlow<Boolean> = _showPrivacyConsentDialog.asStateFlow()
    
    private val _showGDPRConsentDialog = MutableStateFlow(false)
    val showGDPRConsentDialog: StateFlow<Boolean> = _showGDPRConsentDialog.asStateFlow()
    
    private val _showConsentRequiredDialog = MutableStateFlow(false)
    val showConsentRequiredDialog: StateFlow<Boolean> = _showConsentRequiredDialog.asStateFlow()
    
    private val _showScanSummaryDialog = MutableStateFlow(false)
    val showScanSummaryDialog: StateFlow<Boolean> = _showScanSummaryDialog.asStateFlow()
    
    private val _currentScanSummary = MutableStateFlow<Pair<Int, String>?>(null)
    val currentScanSummary: StateFlow<Pair<Int, String>?> = _currentScanSummary.asStateFlow()

    // Permission actions
    private val _requestPermissionsAction = MutableStateFlow<List<String>?>(null)
    val requestPermissionsAction: StateFlow<List<String>?> = _requestPermissionsAction.asStateFlow()

    private val _navigateToAppSettingsAction = MutableStateFlow(false)
    val navigateToAppSettingsAction: StateFlow<Boolean> = _navigateToAppSettingsAction.asStateFlow()

    // Settings
    private val _isBackgroundScanningEnabled = MutableStateFlow(false)
    val isBackgroundScanningEnabled: StateFlow<Boolean> = _isBackgroundScanningEnabled.asStateFlow()

    // Removed backgroundScanIntervalMinutes as it's not needed for continuous scanning
    
    private val _backgroundScanDurationMinutes = MutableStateFlow(10)
    val backgroundScanDurationMinutes: StateFlow<Int> = _backgroundScanDurationMinutes.asStateFlow()
    
    // Background service state
    private val _isBackgroundServiceActive = MutableStateFlow(false)
    val isBackgroundServiceActive: StateFlow<Boolean> = _isBackgroundServiceActive.asStateFlow()
    
    // Scan session tracking
    private var currentScanSession: ScanSession? = null
    private val _currentScanNetworks = mutableListOf<SessionNetwork>()

    private val _isAutoUploadEnabled = MutableStateFlow(true)
    val isAutoUploadEnabled: StateFlow<Boolean> = _isAutoUploadEnabled.asStateFlow()

    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus.asStateFlow()

    private val _connectionSuccessMessage = MutableStateFlow<String?>(null)
    val connectionSuccessMessage: StateFlow<String?> = _connectionSuccessMessage.asStateFlow()

    // Filter settings
    private val _ssidFilter = MutableStateFlow("")
    val ssidFilter: StateFlow<String> = _ssidFilter.asStateFlow()
    
    // Navigation state - persistent across app lifecycle
    private val _currentScreen = MutableStateFlow("main")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()
    
    // Scan history navigation trigger
    private val _navigateToScanHistoryTrigger = MutableStateFlow(false)
    val navigateToScanHistoryTrigger: StateFlow<Boolean> = _navigateToScanHistoryTrigger.asStateFlow()
    
    // Networks to show on map (for filtered map view)
    private val _networksForMap = MutableStateFlow<List<WifiNetwork>?>(null)
    val networksForMap: StateFlow<List<WifiNetwork>?> = _networksForMap.asStateFlow()

    private val _securityFilter = MutableStateFlow(emptySet<String>())
    val securityFilter: StateFlow<Set<String>> = _securityFilter.asStateFlow()

    private val _rssiThreshold = MutableStateFlow("-95")
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
    }.flowOn(Dispatchers.Default) // Process data enhancement on background thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Filtered networks based on current filter settings
    private val filteredNetworks = combine(
        enhancedNetworks,
        _ssidFilter,
        _securityFilter,
        _rssiThreshold,
        _bssidFilter
    ) { networks, ssidFilter, securityFilter, rssiThreshold, bssidFilter ->
        applyFilters(networks, ssidFilter, securityFilter, rssiThreshold, bssidFilter)
    }.flowOn(Dispatchers.Default) // Process filtering on background thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // Sorted networks based on current sorting mode
    val wifiNetworks = combine(
        filteredNetworks,
        _sortingMode
    ) { networks, sortingMode ->
        applySorting(networks, sortingMode)
    }.flowOn(Dispatchers.Default) // Process sorting on background thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
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

    // Enhanced pinned networks that combine pinned data with temporary modifications and availability status
    // FIXED: Removed database operations from combine block to prevent memory leaks
    val pinnedNetworks = combine(
        managePinnedNetworksUseCase.getAllPinnedNetworks(),
        manageTemporaryNetworkDataUseCase.getAllTemporaryData(),
        wifiNetworks
    ) { pinnedNetworksList, temporaryDataList, currentNetworks ->
        val temporaryDataMap = temporaryDataList.associateBy { it.bssid }
        val currentNetworksMap = currentNetworks.associateBy { it.bssid }
        val currentTimestamp = System.currentTimeMillis()
        val hideTimeout = _hideNetworksUnseenForSeconds.value * 1000L
        
        pinnedNetworksList.map { pinnedNetwork ->
            val temporaryData = temporaryDataMap[pinnedNetwork.bssid]
            val currentNetwork = currentNetworksMap[pinnedNetwork.bssid]
            
            // Determine if network is offline (just calculate, don't update database here)
            val isOffline = when {
                currentNetwork != null -> currentNetwork.isOffline
                else -> {
                    val lastSeen = pinnedNetwork.lastSeenTimestamp
                    (currentTimestamp - lastSeen) > hideTimeout
                }
            }
            
            // Calculate updated timestamp (just calculate, don't update database here)
            val updatedLastSeen = if (currentNetwork != null && !currentNetwork.isOffline) {
                currentTimestamp
            } else {
                pinnedNetwork.lastSeenTimestamp
            }
            
            // Schedule database updates separately to avoid memory leaks
            if (isOffline != pinnedNetwork.isOffline || updatedLastSeen != pinnedNetwork.lastSeenTimestamp) {
                scheduleOfflineStatusUpdate(pinnedNetwork.bssid, isOffline, updatedLastSeen)
            }
            
            if (temporaryData != null) {
                // Merge temporary data into pinned network for live updates
                val mergedPhotoUri = when {
                    temporaryData.photoPath == null && pinnedNetwork.photoUri == null -> null
                    temporaryData.photoPath == null -> null // Temporary data explicitly deleted photo
                    pinnedNetwork.photoUri == null -> null // Pinned data explicitly deleted photo
                    else -> temporaryData.photoPath ?: pinnedNetwork.photoUri
                }
                
                // Use temporary password directly without encryption operations in combine flow
                pinnedNetwork.copy(
                    comment = temporaryData.comment.takeIf { it.isNotEmpty() } ?: pinnedNetwork.comment,
                    photoUri = mergedPhotoUri,
                    isOffline = isOffline,
                    lastSeenTimestamp = updatedLastSeen
                )
            } else {
                pinnedNetwork.copy(
                    isOffline = isOffline,
                    lastSeenTimestamp = updatedLastSeen
                )
            }
        }
    }.flowOn(Dispatchers.Default) // Process on background thread
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
        // Check GDPR consent before allowing scan
        val consentState = gdprConsentManager.consentState.value
        if (!consentState.hasValidConsent) {
            _showConsentRequiredDialog.value = true
            return
        }
        
        viewModelScope.launch {
            try {
                // Handle scan with potential ad display every 3rd scan
                adManager.onScanStarted {
                    // This runs after ad is shown (or immediately if no ad)
                    viewModelScope.launch {
                        try {
                            // Only clear previous results if not continuing from background scan
                            if (!_isBackgroundServiceActive.value) {
                                scanWifiNetworksUseCase.clearNetworks()
                            }
                            
                            // Mark that a scan has been started
                            _hasEverScanned.value = true
                            
                            // Start new scan session
                            startScanSession()
                            
                            // Always start with background service if background scanning is enabled
                            // This allows the scan to continue when app is backgrounded
                            if (_isBackgroundScanningEnabled.value) {
                                // Start background scanning service (with foreground notification)
                                startBackgroundScan(getApplication<android.app.Application>())
                            } else {
                                // Start regular foreground-only scan
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
                            }
                        } catch (e: Exception) {
                            _permissionsRationaleMessage.value = "Error starting scan: ${e.message}"
                            _showPermissionRationaleDialog.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                _permissionsRationaleMessage.value = "Error with scan initialization: ${e.message}"
                _showPermissionRationaleDialog.value = true
            }
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            // Always stop the scanning process first
            if (_isBackgroundServiceActive.value) {
                // Stop background scanning service but don't end session yet
                stopBackgroundScan(getApplication<android.app.Application>())
            } else {
                // Stop regular foreground scan
                scanWifiNetworksUseCase.stopScan()
            }
            
            // Check for stale networks after scan completes
            try {
                scanWifiNetworksUseCase.removeStaleNetworks(_hideNetworksUnseenForSeconds.value)
            } catch (e: Exception) {
                // Silently handle cleanup errors
            }
            
            // Always show scan summary dialog if networks were found (regardless of background/foreground mode)
            val networkCount = wifiNetworks.value.size
            if (networkCount > 0) {
                val defaultTitle = generateSessionTitle(System.currentTimeMillis())
                _currentScanSummary.value = Pair(networkCount, defaultTitle)
                _showScanSummaryDialog.value = true
                Log.d("MainViewModel", "stopScan: Showing session dialog for ${networkCount} networks")
            } else {
                // No networks found, just end session without dialog
                endScanSession()
                Log.d("MainViewModel", "stopScan: No networks found, ending session")
            }
            
            // Auto-upload if enabled and networks are available
            Log.d("MainViewModel", "stopScan: Auto-upload enabled=${_isAutoUploadEnabled.value}, networks count=${networkCount}")
            if (_isAutoUploadEnabled.value && networkCount > 0) {
                Log.d("MainViewModel", "stopScan: Triggering auto-upload")
                uploadScanResultsToFirebase(showNotifications = false)
            } else {
                Log.d("MainViewModel", "stopScan: Auto-upload not triggered")
            }
        }
    }

    fun clearNetworks() {
        viewModelScope.launch {
            scanWifiNetworksUseCase.clearNetworks()
        }
    }
    
    // Background scanning with foreground service
    fun startBackgroundScan(context: Context) {
        if (_isBackgroundServiceActive.value) {
            return // Already running
        }
        
        viewModelScope.launch {
            try {
                // Check for notification permission on Android 13+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, 
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (!hasNotificationPermission) {
                        _permissionsRationaleMessage.value = "Notification permission is required for background scanning. Please grant it in the next dialog."
                        _showPermissionRationaleDialog.value = true
                        return@launch
                    }
                }
                
                // Don't clear previous results to maintain scan continuity
                // scanWifiNetworksUseCase.clearNetworks()
                
                // Mark that a scan has been started
                _hasEverScanned.value = true
                _isBackgroundServiceActive.value = true
                
                // Start the foreground service
                android.util.Log.d("MainViewModel", "About to start WiFiScanService for continuous scanning")
                WiFiScanService.startService(context)
                android.util.Log.d("MainViewModel", "WiFiScanService.startService() called")
                
            } catch (e: Exception) {
                _permissionsRationaleMessage.value = "Error starting background scan: ${e.message}"
                _showPermissionRationaleDialog.value = true
                _isBackgroundServiceActive.value = false
            }
        }
    }
    
    fun stopBackgroundScan(context: Context) {
        viewModelScope.launch {
            try {
                // Stop the foreground service
                WiFiScanService.stopService(context)
                _isBackgroundServiceActive.value = false
                
                // Also stop the regular scanning
                scanWifiNetworksUseCase.stopScan()
                
                // Check for stale networks after scan completes
                try {
                    scanWifiNetworksUseCase.removeStaleNetworks(_hideNetworksUnseenForSeconds.value)
                } catch (e: Exception) {
                    // Silently handle cleanup errors
                }
            } catch (e: Exception) {
                // Handle cleanup errors
                _isBackgroundServiceActive.value = false
            }
        }
    }
    
    fun toggleBackgroundScan(context: Context) {
        if (_isBackgroundServiceActive.value) {
            stopBackgroundScan(context)
        } else {
            startBackgroundScan(context)
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
        // Use IO dispatcher for database operations to prevent UI blocking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete pinned network first
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
                
                Log.d("MainViewModel", "Successfully deleted pinned network: ${network.ssid}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete pinned network: ${network.ssid}", e)
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
        // Cancel any existing pin operation for this BSSID to prevent rapid operations
        pinOperationJobs[bssid]?.cancel()
        
        // Schedule new pin operation with debouncing for better UI responsiveness
        pinOperationJobs[bssid] = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Small delay to debounce rapid pin/unpin operations
                kotlinx.coroutines.delay(100)
                
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
                        managePinnedNetworksUseCase.pinNetwork(network, existingData?.comment, existingData?.savedPassword, existingData?.photoPath)
                    }
                } else {
                    managePinnedNetworksUseCase.unpinNetwork(bssid)
                }
                
                pinOperationJobs.remove(bssid)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to pin/unpin network $bssid", e)
                pinOperationJobs.remove(bssid)
            }
        }
    }

    // Export functions
    fun exportWifiNetworks(context: Context, format: ExportFormat, action: ExportAction) {
        adManager.showAdForExport {
            // Execute export after ad is shown (or immediately if no ad)
            viewModelScope.launch {
                exportNetworksUseCase.exportWifiNetworks(context, wifiNetworks.value, format, action)
            }
        }
    }

    fun exportPinnedNetwork(context: Context, network: PinnedNetwork, format: ExportFormat, action: ExportAction) {
        adManager.showAdForExport {
            // Execute export after ad is shown (or immediately if no ad)
            viewModelScope.launch {
                exportNetworksUseCase.exportPinnedNetwork(context, network, format, action)
            }
        }
    }

    fun exportScanSession(context: Context, session: ScanSession, format: ExportFormat, action: ExportAction) {
        adManager.showAdForExport {
            // Execute export after ad is shown (or immediately if no ad)
            viewModelScope.launch {
                // Convert SessionNetwork to WifiNetwork for export
                val wifiNetworks = session.networks.map { sessionNetwork ->
                    WifiNetwork(
                        ssid = sessionNetwork.ssid,
                        bssid = sessionNetwork.bssid,
                        rssi = sessionNetwork.rssi,
                        channel = sessionNetwork.channel,
                        security = sessionNetwork.security,
                        latitude = sessionNetwork.latitude,
                        longitude = sessionNetwork.longitude,
                        timestamp = sessionNetwork.lastSeenTimestamp,
                        lastSeenTimestamp = sessionNetwork.lastSeenTimestamp,
                        isOffline = sessionNetwork.isOffline,
                        vendor = sessionNetwork.vendor.ifEmpty { null }
                    )
                }
                exportNetworksUseCase.exportWifiNetworks(context, wifiNetworks, format, action)
            }
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
        
        // Add NEARBY_WIFI_DEVICES and POST_NOTIFICATIONS permissions for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
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
    fun toggleBackgroundScanning(context: Context, isEnabled: Boolean) {
        android.util.Log.d("MainViewModel", "toggleBackgroundScanning called: enabled=$isEnabled")
        
        _isBackgroundScanningEnabled.value = isEnabled
        saveBackgroundScanSettingsToPreferences()
        
        if (isEnabled) {
            // Only start the background notification service to show "enabled not running" state
            // Don't automatically start scanning or promote current scans
            android.util.Log.d("MainViewModel", "Background scanning enabled - starting notification service")
            try {
                val intent = android.content.Intent(context, com.ner.wimap.service.BackgroundNotificationService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Failed to start BackgroundNotificationService", e)
            }
        } else {
            // Only stop background services if the user has no active scan
            // If there's an active scan, just disable background permission but keep scanning
            android.util.Log.d("MainViewModel", "Background scanning disabled")
            
            // Stop the background notification service
            try {
                val intent = android.content.Intent(context, com.ner.wimap.service.BackgroundNotificationService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.w("MainViewModel", "Failed to stop BackgroundNotificationService", e)
            }
            
            // Note: We don't stop active scans here - just remove background permission
            // Active scans will continue as foreground scans
        }
        
        android.widget.Toast.makeText(context, "Background scanning: ${if (isEnabled) "ALLOWED" else "DISABLED"}", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun promoteToBackgroundScan(context: Context) {
        android.util.Log.d("MainViewModel", "Promoting foreground scan to background mode")
        
        viewModelScope.launch {
            try {
                // Check for notification permission on Android 13+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, 
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (!hasNotificationPermission) {
                        _permissionsRationaleMessage.value = "Notification permission is required for background scanning. Please grant it in the next dialog."
                        _showPermissionRationaleDialog.value = true
                        return@launch
                    }
                }
                
                // Don't clear networks - continue with existing scan data
                _isBackgroundServiceActive.value = true
                
                // Start the foreground service (this will replace foreground scan with background service)
                android.util.Log.d("MainViewModel", "Starting WiFiScanService to promote scan")
                WiFiScanService.startService(context)
                android.util.Log.d("MainViewModel", "Scan promoted to background mode successfully")
                
            } catch (e: Exception) {
                _permissionsRationaleMessage.value = "Error promoting scan to background: ${e.message}"
                _showPermissionRationaleDialog.value = true
                _isBackgroundServiceActive.value = false
            }
        }
    }
    
    // Function to manually start background scanning when enabled
    fun startBackgroundScanningIfEnabled(context: Context) {
        if (_isBackgroundScanningEnabled.value && !_isBackgroundServiceActive.value) {
            android.util.Log.d("MainViewModel", "Starting background scan (user initiated)")
            startBackgroundScan(context)
        }
    }

    // Removed setBackgroundScanDuration as continuous scanning doesn't need duration limits
    
    // Navigation functions
    fun navigateToScreen(screen: String) {
        _currentScreen.value = screen
        // Don't save navigation state - always return to main on app resume
        // sharedPreferences.edit().putString("current_screen", screen).apply()
    }
    
    fun navigateToMain() {
        navigateToScreen("main")
    }
    
    fun navigateToSettings() {
        navigateToScreen("settings")
    }
    
    fun navigateToScanHistory() {
        _navigateToScanHistoryTrigger.value = true
    }
    
    fun onNavigateToScanHistoryHandled() {
        _navigateToScanHistoryTrigger.value = false
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
        val networks = wifiNetworks.value
        Log.d("MainViewModel", "uploadScanResultsToFirebase called with ${networks.size} networks, showNotifications=$showNotifications")
        
        if (networks.isEmpty()) {
            Log.d("MainViewModel", "No networks to upload - checking scanning state")
            Log.d("MainViewModel", "Is scanning: ${isScanning.value}")
            Log.d("MainViewModel", "Has ever scanned: ${hasEverScanned.value}")
            if (showNotifications) {
                _uploadStatus.value = "No networks to upload - try scanning first"
            }
            return
        }

        // Debug: Log all network details for debugging
        Log.d("MainViewModel", "=== NETWORKS TO UPLOAD ===")
        networks.take(5).forEachIndexed { index, network ->
            Log.d("MainViewModel", "Network $index:")
            Log.d("MainViewModel", "  SSID: '${network.ssid}'")
            Log.d("MainViewModel", "  BSSID: '${network.bssid}'")
            Log.d("MainViewModel", "  RSSI: ${network.rssi}")
            Log.d("MainViewModel", "  Security: '${network.security}'")
            Log.d("MainViewModel", "  Channel: ${network.channel}")
            Log.d("MainViewModel", "  Lat/Lng: ${network.latitude}, ${network.longitude}")
            Log.d("MainViewModel", "  Vendor: '${network.vendor}'")
            Log.d("MainViewModel", "  Timestamp: ${network.timestamp}")
        }
        Log.d("MainViewModel", "=== END NETWORK LIST ===")

        // Check filtering state
        Log.d("MainViewModel", "Current filters:")
        Log.d("MainViewModel", "  SSID filter: '${ssidFilter.value}'")
        Log.d("MainViewModel", "  RSSI threshold: '${rssiThreshold.value}'")
        Log.d("MainViewModel", "  Security filter: ${securityFilter.value}")
        Log.d("MainViewModel", "  BSSID filter: '${bssidFilter.value}'")

        viewModelScope.launch {
            try {
                if (showNotifications) {
                    _uploadStatus.value = "Uploading ${networks.size} networks to Firebase..."
                }
                
                Log.d("MainViewModel", "Calling firebaseRepository.uploadWifiNetworks with ${networks.size} networks")
                val result = firebaseRepository.uploadWifiNetworks(networks)
                
                when (result) {
                    is com.ner.wimap.Result.Success -> {
                        if (showNotifications) {
                            _uploadStatus.value = result.data
                        }
                        Log.d("MainViewModel", "Upload successful: ${result.data}")
                    }
                    is com.ner.wimap.Result.Failure -> {
                        if (showNotifications) {
                            _uploadStatus.value = "Upload failed: ${result.exception.message}"
                        }
                        Log.e("MainViewModel", "Upload failed", result.exception)
                    }
                }
            } catch (e: Exception) {
                if (showNotifications) {
                    _uploadStatus.value = "Upload error: ${e.message}"
                }
                Log.e("MainViewModel", "Upload error", e)
            }
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

    private val _rssiThresholdForConnection = MutableStateFlow(-80)
    val rssiThresholdForConnection: StateFlow<Int> = _rssiThresholdForConnection.asStateFlow()

    private val _hideNetworksUnseenForSeconds = MutableStateFlow(30)
    val hideNetworksUnseenForSeconds: StateFlow<Int> = _hideNetworksUnseenForSeconds.asStateFlow()

    init {
        // Load all settings from SharedPreferences on initialization
        loadPasswordsFromPreferences()
        loadFiltersFromPreferences()
        // Start monitoring scan networks for session recording
        startMonitoringScanNetworks()
        loadConnectionSettingsFromPreferences()
        loadSortingFromPreferences()
        loadBackgroundScanSettingsFromPreferences()
        loadNavigationStateFromPreferences()
        
        // Start periodic cleanup of stale networks
        startPeriodicNetworkCleanup()
        
        // Monitor connection status for success notifications
        monitorConnectionSuccess()
        
        // Start background notification service if background scanning is enabled
        initializeBackgroundScanningState()
        
        // Initialize Firebase on first app launch
        initializeFirebaseOnFirstLaunch()
    }
    
    private fun initializeBackgroundScanningState() {
        // Check if background scanning is enabled but not running
        if (_isBackgroundScanningEnabled.value && !_isBackgroundServiceActive.value) {
            // Start the background notification service to show "enabled not running" state
            android.util.Log.d("MainViewModel", "Initializing background notification service on app start")
            viewModelScope.launch {
                try {
                    val context = getApplication<android.app.Application>()
                    val intent = android.content.Intent(context, com.ner.wimap.service.BackgroundNotificationService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Failed to start BackgroundNotificationService on init", e)
                }
            }
        }
        android.util.Log.d("MainViewModel", "Background scanning permission: ${_isBackgroundScanningEnabled.value}")
    }
    
    // Handle app lifecycle changes
    fun onAppBackgrounded() {
        android.util.Log.d("MainViewModel", "App backgrounded - checking scan state")
        
        // If background scanning is disabled and a foreground scan is running, stop it
        if (!_isBackgroundScanningEnabled.value && isScanning.value && !_isBackgroundServiceActive.value) {
            android.util.Log.d("MainViewModel", "Stopping foreground scan - background scanning disabled")
            viewModelScope.launch {
                scanWifiNetworksUseCase.stopScan()
            }
        }
    }
    
    fun onAppForegrounded() {
        android.util.Log.d("MainViewModel", "App foregrounded - scan state maintained")
        // Scan state is maintained - no action needed
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
                
                // Clear all scan sessions from database
                scanSessionDao.clearAllScanSessions()
                
                // Clear current networks list
                scanWifiNetworksUseCase.clearNetworks()
                
                // Reset all state flows to default values
                _passwords.value = emptyList()
                _ssidFilter.value = ""
                _securityFilter.value = emptySet()
                _rssiThreshold.value = "-95"
                _bssidFilter.value = ""
                _maxRetries.value = 3
                _connectionTimeoutSeconds.value = 10
                _rssiThresholdForConnection.value = -80
                _hideNetworksUnseenForSeconds.value = 30
                _isBackgroundScanningEnabled.value = false
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
    
    // Map functions
    fun setNetworksForMap(networks: List<WifiNetwork>) {
        _networksForMap.value = networks
    }
    
    fun clearNetworksForMap() {
        _networksForMap.value = null
    }

    // Periodic network cleanup
    private fun startPeriodicNetworkCleanup() {
        viewModelScope.launch {
            while (true) {
                delay(30 * 1000L) // Run every 30 seconds for more responsive offline detection
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
        _rssiThreshold.value = sharedPreferences.getString("rssi_threshold", "-95") ?: "-95"
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
                val threshold = rssiThreshold.toIntOrNull() ?: -95
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
        _rssiThresholdForConnection.value = sharedPreferences.getInt("rssi_threshold_for_connection", -80).coerceIn(-100, -30)
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

    // Background scanning persistence
    private fun loadBackgroundScanSettingsFromPreferences() {
        _isBackgroundScanningEnabled.value = sharedPreferences.getBoolean("background_scanning_enabled", false)
        _backgroundScanDurationMinutes.value = sharedPreferences.getInt("background_scan_duration_minutes", 10).coerceIn(5, 60)
        
        // Note: Do NOT auto-start background scanning when app opens
        // Background scanning should only start when user explicitly presses the scan button
        // This prevents unwanted automatic scanning when app is reopened
        if (_isBackgroundScanningEnabled.value) {
            Log.d("MainViewModel", "Background scanning is enabled but not auto-starting (user must press scan button)")
        }
    }

    private fun saveBackgroundScanSettingsToPreferences() {
        sharedPreferences.edit()
            .putBoolean("background_scanning_enabled", _isBackgroundScanningEnabled.value)
            .putInt("background_scan_duration_minutes", _backgroundScanDurationMinutes.value)
            .apply()
    }
    
    private fun loadNavigationStateFromPreferences() {
        // Always start on main screen when app resumes/relaunches
        // Don't load saved screen to ensure consistent behavior
        _currentScreen.value = "main"
        // Clear any saved screen state to prevent confusion
        sharedPreferences.edit().putString("current_screen", "main").apply()
    }
    
    // Privacy Consent Methods
    fun checkAndShowPrivacyConsent() {
        // Check GDPR consent first
        if (gdprConsentManager.needsConsent()) {
            _showGDPRConsentDialog.value = true
        } else if (deviceInfoManager.shouldShowMandatoryCollectionNotice()) {
            // Fall back to old consent dialog for legacy compatibility
            _showPrivacyConsentDialog.value = true
        } else if (deviceInfoManager.hasAcknowledgedMandatoryCollection()) {
            // User has already consented, check if we need to update device info
            deviceInfoService.checkAndUpdateDeviceInfo()
        }
    }
    
    // GDPR Consent Methods
    fun onGDPRConsentGranted(
        essentialConsent: Boolean,
        analyticsConsent: Boolean,
        advertisingConsent: Boolean,
        locationConsent: Boolean,
        dataUploadConsent: Boolean,
        userAge: Int
    ) {
        _showGDPRConsentDialog.value = false
        
        // Record consent in GDPR manager
        gdprConsentManager.recordConsent(
            essentialConsent = essentialConsent,
            analyticsConsent = analyticsConsent,
            advertisingConsent = advertisingConsent,
            locationConsent = locationConsent,
            dataUploadConsent = dataUploadConsent,
            userAge = userAge
        )
        
        // Mark mandatory collection acknowledged
        deviceInfoManager.setMandatoryCollectionAcknowledged()
        
        // Ensure Firebase user is created first, then collect device info
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Ensuring Firebase Auth user after consent")
                firebaseRepository.ensureUserAuthenticated()
                
                // Now trigger device info collection and upload (User UID is available)
                Log.d("MainViewModel", "Triggering device info collection and upload after consent")
                deviceInfoService.handleMandatoryCollectionAcknowledged()
                
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to initialize Firebase and collect device info after consent", e)
            }
        }
        
        // Initialize AdMob and other services based on consent
        initializeServicesAfterConsent()
    }
    
    fun onGDPRConsentDenied() {
        _showGDPRConsentDialog.value = false
        // User declined - app can only use essential functionality
        gdprConsentManager.withdrawConsent()
    }
    
    fun dismissGDPRConsentDialog() {
        _showGDPRConsentDialog.value = false
    }
    
    fun dismissConsentRequiredDialog() {
        _showConsentRequiredDialog.value = false
    }
    
    fun onShowConsentFromRequiredDialog() {
        _showConsentRequiredDialog.value = false
        _showGDPRConsentDialog.value = true
    }
    
    fun onSaveScanSession(title: String) {
        _showScanSummaryDialog.value = false
        
        // Update the current session title and save it
        currentScanSession?.let { session ->
            val finalSession = session.copy(
                title = title,
                networkCount = _currentScanNetworks.size,
                networks = _currentScanNetworks.toList()
            )
            
            viewModelScope.launch {
                try {
                    scanSessionDao.insertScanSession(finalSession)
                    Log.d("MainViewModel", "Saved scan session with custom title: '$title'")
                    
                    // Clean up old sessions if needed (keep last 50 sessions)
                    val sessionCount = scanSessionDao.getScanSessionCount()
                    if (sessionCount > 50) {
                        val excessCount = sessionCount - 50
                        scanSessionDao.deleteOldestScanSessions(excessCount)
                        Log.d("MainViewModel", "Cleaned up $excessCount old scan sessions")
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to save scan session", e)
                }
            }
        }
        
        // End the session and reset state
        endScanSession(saveSession = false) // Don't save again, we already saved above
        _currentScanSummary.value = null
    }
    
    fun onCancelScanSession() {
        _showScanSummaryDialog.value = false
        // End session without saving
        endScanSession()
        _currentScanSummary.value = null
    }
    
    // Auto-save session when stopped from notification with default name
    fun autoSaveCurrentScanSession() {
        viewModelScope.launch {
            try {
                // First stop the scan properly (both foreground and background)
                scanWifiNetworksUseCase.stopScan()
                
                // Also ensure background service state is reset if it was active
                if (_isBackgroundServiceActive.value) {
                    stopBackgroundScan(getApplication<android.app.Application>())
                }
                
                val networks = wifiNetworks.value
                if (networks.isNotEmpty()) {
                    val timestamp = System.currentTimeMillis()
                    val defaultTitle = generateSessionTitle(timestamp)
                    android.util.Log.d("MainViewModel", "Auto-saving scan session with title: $defaultTitle")
                    
                    val sessionNetworks = networks.map { network ->
                        com.ner.wimap.data.database.SessionNetwork(
                            ssid = network.ssid,
                            bssid = network.bssid,
                            rssi = network.rssi,
                            channel = network.channel,
                            security = network.security,
                            vendor = network.vendor ?: "",
                            latitude = network.latitude,
                            longitude = network.longitude,
                            lastSeenTimestamp = network.lastSeenTimestamp,
                            isOffline = network.isOffline
                        )
                    }
                    
                    val scanSession = com.ner.wimap.data.database.ScanSession(
                        title = defaultTitle,
                        timestamp = timestamp,
                        networkCount = networks.size,
                        networks = sessionNetworks
                    )
                    
                    scanSessionDao.insertScanSession(scanSession)
                    android.util.Log.d("MainViewModel", "Scan session auto-saved successfully: ${networks.size} networks")
                    
                    // Show success message
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Scan session saved as '$defaultTitle'",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to auto-save scan session", e)
            } finally {
                // Always end the session and reset all scan states
                endScanSession()
                // Ensure scan state is properly reset
                Log.d("MainViewModel", "Resetting scan state after auto-save")
            }
        }
    }
    
    /**
     * Handle stop scan from notification when app is in foreground
     * Shows the normal session save dialog instead of auto-saving
     */
    fun onStopScanFromNotification() {
        viewModelScope.launch {
            try {
                // Stop the scan properly (both foreground and background)
                scanWifiNetworksUseCase.stopScan()
                
                // Also ensure background service state is reset if it was active
                if (_isBackgroundServiceActive.value) {
                    stopBackgroundScan(getApplication<android.app.Application>())
                }
                
                // Check if we have networks to save
                val networks = wifiNetworks.value
                if (networks.isNotEmpty()) {
                    // Show the scan summary dialog for manual save
                    val timestamp = System.currentTimeMillis()
                    _currentScanSummary.value = Pair(networks.size, generateSessionTitle(timestamp))
                    _showScanSummaryDialog.value = true
                    
                    Log.d("MainViewModel", "Showing scan summary dialog for ${networks.size} networks after notification stop")
                } else {
                    // No networks to save, just end the session
                    endScanSession()
                    Log.d("MainViewModel", "No networks to save, ending session after notification stop")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error stopping scan from notification", e)
                // Fallback: just end the session and reset states
                endScanSession()
                // Also ensure background service is stopped in case of error
                if (_isBackgroundServiceActive.value) {
                    try {
                        stopBackgroundScan(getApplication<android.app.Application>())
                    } catch (serviceException: Exception) {
                        Log.e("MainViewModel", "Error stopping background service in fallback", serviceException)
                    }
                }
            }
        }
    }
    
    private fun initializeServicesAfterConsent() {
        val consentState = gdprConsentManager.consentState.value
        
        // Initialize AdMob with consent settings
        if (consentState.canShowPersonalizedAds) {
            // Initialize with personalized ads
            adManager.setPersonalizedAdsEnabled(true)
        } else {
            // Initialize with non-personalized ads only
            adManager.setPersonalizedAdsEnabled(false)
        }
        
        // Handle analytics consent
        if (consentState.canCollectAnalytics) {
            // Enable analytics
            // TODO: Initialize analytics service
        }
        
        // Handle data upload consent
        if (consentState.canUploadData) {
            // Enable cloud uploads
            _isAutoUploadEnabled.value = true
        } else {
            _isAutoUploadEnabled.value = false
        }
    }
    
    // Legacy consent methods for backward compatibility
    fun onPrivacyConsentGranted() {
        _showPrivacyConsentDialog.value = false
        deviceInfoService.handleMandatoryCollectionAcknowledged()
    }
    
    fun onPrivacyConsentDenied() {
        _showPrivacyConsentDialog.value = false
        deviceInfoService.handleMandatoryCollectionRefused()
    }
    
    fun dismissPrivacyConsentDialog() {
        _showPrivacyConsentDialog.value = false
    }
    
    fun isPrivacyConsentGranted(): Boolean {
        val gdprConsent = gdprConsentManager.consentState.value.hasValidConsent
        val legacyConsent = deviceInfoManager.hasAcknowledgedMandatoryCollection()
        return gdprConsent || legacyConsent
    }
    
    fun requestDataDeletion() {
        // Clear GDPR consent data
        gdprConsentManager.clearAllData()
        
        // Clear legacy consent data
        deviceInfoService.handleDataDeletionRequest()
        
        // Clear all app data
        clearAllData()
    }
    
    // GDPR Rights Management
    fun getGDPRConsentSummary(): String {
        return gdprConsentManager.getConsentSummary()
    }
    
    fun exportGDPRData(): Map<String, Any> {
        return gdprConsentManager.exportConsentData()
    }
    
    fun withdrawGDPRConsent() {
        gdprConsentManager.withdrawConsent()
        // Show consent dialog again
        _showGDPRConsentDialog.value = true
    }
    
    // ========================================
    // SCAN SESSION MANAGEMENT
    // ========================================
    
    private fun startScanSession() {
        val timestamp = System.currentTimeMillis()
        currentScanSession = ScanSession(
            title = generateSessionTitle(timestamp),
            timestamp = timestamp,
            networkCount = 0,
            networks = emptyList()
        )
        _currentScanNetworks.clear()
        Log.d("MainViewModel", "Started new scan session: ${currentScanSession?.title}")
    }
    
    private fun startMonitoringScanNetworks() {
        viewModelScope.launch {
            wifiNetworks.collect { networks ->
                if (currentScanSession != null && networks.isNotEmpty()) {
                    // Convert WiFiNetwork to SessionNetwork and add to current session
                    val sessionNetworks = networks.map { network ->
                        SessionNetwork(
                            ssid = network.ssid,
                            bssid = network.bssid,
                            rssi = network.rssi,
                            channel = network.channel,
                            security = network.security,
                            vendor = network.vendor ?: "",
                            latitude = network.latitude,
                            longitude = network.longitude,
                            lastSeenTimestamp = network.timestamp,
                            isOffline = network.isOffline
                        )
                    }
                    
                    // Update current session networks (avoid duplicates by BSSID)
                    val existingBssids = _currentScanNetworks.map { it.bssid }.toSet()
                    val newNetworks = sessionNetworks.filter { it.bssid !in existingBssids }
                    _currentScanNetworks.addAll(newNetworks)
                    
                    Log.d("MainViewModel", "Updated scan session with ${_currentScanNetworks.size} total networks")
                }
            }
        }
    }
    
    private fun endScanSession(saveSession: Boolean = false) {
        val session = currentScanSession
        if (session != null && _currentScanNetworks.isNotEmpty() && saveSession) {
            val finalSession = session.copy(
                networkCount = _currentScanNetworks.size,
                networks = _currentScanNetworks.toList()
            )
            
            viewModelScope.launch {
                try {
                    scanSessionDao.insertScanSession(finalSession)
                    Log.d("MainViewModel", "Saved scan session '${finalSession.title}' with ${finalSession.networkCount} networks")
                    
                    // Clean up old sessions if needed (keep last 50 sessions)
                    val sessionCount = scanSessionDao.getScanSessionCount()
                    if (sessionCount > 50) {
                        val excessCount = sessionCount - 50
                        scanSessionDao.deleteOldestScanSessions(excessCount)
                        Log.d("MainViewModel", "Cleaned up $excessCount old scan sessions")
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Failed to save scan session", e)
                }
            }
        }
        
        // Reset session state
        currentScanSession = null
        _currentScanNetworks.clear()
    }
    
    private fun generateSessionTitle(timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        
        return String.format("Scan %02d/%02d %02d:%02d", month, day, hour, minute)
    }
    
    // Public access to scan sessions for UI
    fun getAllScanSessions() = scanSessionDao.getAllScanSessions()
    
    // Scan session management functions
    fun renameScanSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                scanSessionDao.updateSessionTitle(sessionId, newTitle)
                Log.d("MainViewModel", "Renamed scan session $sessionId to '$newTitle'")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to rename scan session", e)
            }
        }
    }
    
    fun deleteScanSession(session: ScanSession) {
        viewModelScope.launch {
            try {
                scanSessionDao.deleteScanSession(session)
                Log.d("MainViewModel", "Deleted scan session '${session.title}'")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete scan session", e)
            }
        }
    }
    
    /**
     * Initialize Firebase on first app launch
     * Creates anonymous user and stores User UID
     */
    private fun initializeFirebaseOnFirstLaunch() {
        viewModelScope.launch {
            try {
                firebaseRepository.initializeOnFirstLaunch()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to initialize Firebase on first launch", e)
            }
        }
    }
    
    // Debouncing for database updates to prevent excessive operations
    private val offlineStatusUpdateJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val pinOperationJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    /**
     * Schedule offline status update with debouncing to prevent excessive database operations
     */
    private fun scheduleOfflineStatusUpdate(bssid: String, isOffline: Boolean, lastSeenTimestamp: Long) {
        // Cancel any existing update for this BSSID
        offlineStatusUpdateJobs[bssid]?.cancel()
        
        // Schedule new update with debouncing
        offlineStatusUpdateJobs[bssid] = viewModelScope.launch {
            kotlinx.coroutines.delay(500) // 500ms debounce
            try {
                managePinnedNetworksUseCase.updateOfflineStatus(bssid, isOffline, lastSeenTimestamp)
                offlineStatusUpdateJobs.remove(bssid)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to update offline status for $bssid", e)
                offlineStatusUpdateJobs.remove(bssid)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cancel all pending operations to prevent memory leaks
        offlineStatusUpdateJobs.values.forEach { it.cancel() }
        offlineStatusUpdateJobs.clear()
        pinOperationJobs.values.forEach { it.cancel() }
        pinOperationJobs.clear()
        Log.d("MainViewModel", "ViewModel cleared, cancelled ${offlineStatusUpdateJobs.size + pinOperationJobs.size} pending operations")
    }
    
    companion object {
        const val ACTION_AUTO_SAVE_SESSION = "com.ner.wimap.AUTO_SAVE_SESSION"
    }
}
