package com.ner.wimap

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.ner.wimap.presentation.viewmodel.MainViewModel
import com.ner.wimap.ui.MainScreen
import com.ner.wimap.ui.MapsScreenWrapper
import com.ner.wimap.ui.PinnedNetworksScreen
import com.ner.wimap.ui.SettingsScreen
import com.ner.wimap.ui.ScanHistoryScreen
import com.ner.wimap.ui.WiFiLocatorScreen
import com.ner.wimap.ui.components.SwipeNavigationContainer
import com.ner.wimap.ui.components.SwipeDestination
import com.ner.wimap.ui.components.NavigationWrapper
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import com.ner.wimap.ui.theme.WiMapTheme
import com.ner.wimap.ui.viewmodel.ExportFormat
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ads.AdMobManager
import com.ner.wimap.ui.dialogs.PrivacyConsentDialog
import com.ner.wimap.ui.dialogs.GDPRConsentDialog
import com.ner.wimap.ui.dialogs.ScanSummaryDialog
import com.ner.wimap.ui.dialogs.ExportSuccessDialog
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var adManager: com.ner.wimap.ads.AdManager

    private var sessionSaveReceiver: BroadcastReceiver? = null
    private var mainViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob SDK
        adManager.initialize(this)
        
        // Set this activity for interstitial ads
        adManager.setCurrentActivity(this)
        
        // Preload interstitial ad
        adManager.preloadInterstitialAd(this)

        setContent {
            WiMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WiMapApp()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update the activity reference when resuming
        adManager.setCurrentActivity(this)
        
        // Reload banner ads to ensure they appear consistently
        adManager.reloadBannerAds()
        
    }
    
    override fun onPause() {
        super.onPause()
        // Clear the activity reference when pausing
        adManager.setCurrentActivity(null)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clear the activity reference
        adManager.setCurrentActivity(null)
        
        // Unregister broadcast receiver
        sessionSaveReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
                sessionSaveReceiver = null
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
    }
    
    private fun setupSessionSaveBroadcastReceiver() {
        // Unregister existing receiver if any
        sessionSaveReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver might not be registered
            }
        }
        
        // Create new receiver
        sessionSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    com.ner.wimap.service.BackgroundNotificationService.ACTION_SAVE_SESSION_FROM_NOTIFICATION -> {
                        android.util.Log.d("MainActivity", "Received save session broadcast from notification")
                        
                        // Check if app is in foreground
                        val isAppInForeground = isAppInForeground()
                        android.util.Log.d("MainActivity", "App in foreground: $isAppInForeground")
                        
                        if (isAppInForeground) {
                            // App is in foreground - show normal session dialog
                            android.util.Log.d("MainActivity", "App in foreground - stopping scan and showing session dialog")
                            mainViewModel?.onStopScanFromNotification()
                        } else {
                            // App is in background - auto-save with default name
                            android.util.Log.d("MainActivity", "App in background - auto-saving session")
                            mainViewModel?.autoSaveCurrentScanSession()
                        }
                    }
                }
            }
        }
        
        // Register receiver
        val filter = IntentFilter().apply {
            addAction(com.ner.wimap.service.BackgroundNotificationService.ACTION_SAVE_SESSION_FROM_NOTIFICATION)
        }
        
        try {
            registerReceiver(sessionSaveReceiver, filter)
            android.util.Log.d("MainActivity", "Session save broadcast receiver registered")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to register session save broadcast receiver", e)
        }
    }
    
    private fun isAppInForeground(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses
            
            for (appProcess in runningAppProcesses) {
                if (appProcess.processName == packageName) {
                    return appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking if app is in foreground", e)
            false
        }
    }

    @Composable
    fun WiMapApp(viewModel: MainViewModel = hiltViewModel()) {
        
        // Store viewModel reference for broadcast receiver
        LaunchedEffect(viewModel) {
            mainViewModel = viewModel
            setupSessionSaveBroadcastReceiver()
        }
        
        val requestPermissionsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            viewModel.handlePermissionsResult(permissions)
        }
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        // Main app state
        val wifiNetworks by viewModel.wifiNetworks.collectAsState()
        val isScanning by viewModel.isScanning.collectAsState()
        val hasEverScanned by viewModel.hasEverScanned.collectAsState()
        
        // WiFi Locator state
        val isLocatorScanning by viewModel.isLocatorScanning.collectAsState()
        val locatorRSSI by viewModel.locatorRSSI.collectAsState()
        val connectionStatus by viewModel.connectionStatus.collectAsState()
        val uploadStatus by viewModel.uploadStatus.collectAsState()
        
        val connectionSuccessMessage by viewModel.connectionSuccessMessage.collectAsState()
        val showPasswordDialog by viewModel.showPasswordDialog.collectAsState()
        val networkForPasswordInput by viewModel.networkForPasswordInput.collectAsState()
        val showPermissionRationaleDialog by viewModel.showPermissionRationaleDialog.collectAsState()
        val permissionsRationaleMessage by viewModel.permissionsRationaleMessage.collectAsState()
        val showEmptyPasswordListDialog by viewModel.showEmptyPasswordListDialog.collectAsState()
        val networkForEmptyPasswordDialog by viewModel.networkForEmptyPasswordDialog.collectAsState()
        val showPrivacyConsentDialog by viewModel.showPrivacyConsentDialog.collectAsState()
        val showGDPRConsentDialog by viewModel.showGDPRConsentDialog.collectAsState()
        val showConsentRequiredDialog by viewModel.showConsentRequiredDialog.collectAsState()
        val showScanSummaryDialog by viewModel.showScanSummaryDialog.collectAsState()
        val currentScanSummary by viewModel.currentScanSummary.collectAsState()
        val isBackgroundScanningEnabled by viewModel.isBackgroundScanningEnabled.collectAsState()
        val isBackgroundServiceActive by viewModel.isBackgroundServiceActive.collectAsState()
        val isAutoUploadEnabled by viewModel.isAutoUploadEnabled.collectAsState()
        val pinnedNetworks by viewModel.pinnedNetworks.collectAsState()
        val isConnecting by viewModel.isConnecting.collectAsState()
        val connectingNetworks by viewModel.connectingNetworks.collectAsState()
        val connectionProgress by viewModel.connectionProgress.collectAsState()
        val successfulPasswords by viewModel.successfulPasswords.collectAsState()
        
        // Real-time connection progress data
        val currentPassword by viewModel.currentPassword.collectAsState()
        val currentAttempt by viewModel.currentAttempt.collectAsState()
        val totalAttempts by viewModel.totalAttempts.collectAsState()
        val connectingNetworkName by viewModel.connectingNetworkName.collectAsState()

        // Export states
        val exportStatus by viewModel.exportStatus.collectAsState()
        val exportError by viewModel.exportError.collectAsState()

        // Settings state
        val ssidFilter by viewModel.ssidFilter.collectAsState()
        val securityFilter by viewModel.securityFilter.collectAsState()
        val rssiThreshold by viewModel.rssiThreshold.collectAsState()
        
        // Map state
        val networksForMap by viewModel.networksForMap.collectAsState()
        val bssidFilter by viewModel.bssidFilter.collectAsState()
        val sortingMode by viewModel.sortingMode.collectAsState()
        val passwords by viewModel.passwords.collectAsState()
        val maxRetries by viewModel.maxRetries.collectAsState()
        val connectionTimeoutSeconds by viewModel.connectionTimeoutSeconds.collectAsState()
        val rssiThresholdForConnection by viewModel.rssiThresholdForConnection.collectAsState()
        val hideNetworksUnseenForSeconds by viewModel.hideNetworksUnseenForSeconds.collectAsState()
        val availableSecurityTypes = viewModel.availableSecurityTypes

        // Navigation actions
        val requestPermissionsAction by viewModel.requestPermissionsAction.collectAsState()
        val navigateToAppSettingsAction by viewModel.navigateToAppSettingsAction.collectAsState()

        // Export success/error dialog state
        var showExportSuccessDialog by remember { mutableStateOf(false) }
        var exportSuccessMessage by remember { mutableStateOf("") }
        var showExportErrorDialog by remember { mutableStateOf(false) }
        var exportErrorMessage by remember { mutableStateOf("") }

        // Handle export status/error feedback with dialogs
        LaunchedEffect(exportStatus) {
            exportStatus?.let {
                exportSuccessMessage = it
                showExportSuccessDialog = true
                viewModel.clearExportStatus()
            }
        }

        LaunchedEffect(exportError) {
            exportError?.let {
                exportErrorMessage = it
                showExportErrorDialog = true
                viewModel.clearExportError()
            }
        }

        // Handle connection success notifications
        LaunchedEffect(connectionSuccessMessage) {
            connectionSuccessMessage?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.clearConnectionSuccessMessage()
            }
        }

        // Handle permission requests
        LaunchedEffect(requestPermissionsAction) {
            requestPermissionsAction?.let { permissions ->
                requestPermissionsLauncher.launch(permissions.toTypedArray())
                viewModel.onPermissionsRequestLaunched()
            }
        }

        // Handle navigation to app settings for WiFi/Location permissions only
        LaunchedEffect(navigateToAppSettingsAction) {
            if (navigateToAppSettingsAction) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
                viewModel.onAppSettingsOpened()
            }
        }

        // Check and show privacy consent dialog on app start and resume
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        // Always navigate to main screen when app becomes visible
                        viewModel.navigateToMain()
                        // Check for consent dialog when app becomes visible
                        viewModel.checkAndShowPrivacyConsent()
                        // Handle app foregrounded
                        viewModel.onAppForegrounded()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        // Also ensure main screen on resume (covers different resume scenarios)
                        viewModel.navigateToMain()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        // Handle app backgrounded
                        viewModel.onAppBackgrounded()
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        
        // Initial check on first composition
        LaunchedEffect(Unit) {
            viewModel.checkAndShowPrivacyConsent()
            // Always navigate to main screen when app opens
            viewModel.navigateToMain()
        }

        // Current navigation state - now managed by ViewModel for persistence
        val currentScreen by viewModel.currentScreen.collectAsState()
        var currentPage by remember { mutableStateOf(SwipeDestination.MAIN.index) }
        val coroutineScope = rememberCoroutineScope()
        
        // Create a remembered pager state for the swipe navigation
        val pagerState = rememberPagerState(
            initialPage = SwipeDestination.MAIN.index,
            pageCount = { 5 }
        )
        
        // Handle scan history navigation trigger
        val navigateToScanHistoryTrigger by viewModel.navigateToScanHistoryTrigger.collectAsState()
        LaunchedEffect(navigateToScanHistoryTrigger) {
            if (navigateToScanHistoryTrigger) {
                // Navigate to scan history from any screen
                if (currentScreen == "settings") {
                    // From settings, first navigate to main screen
                    viewModel.navigateToMain()
                }
                // Navigate to scan history page
                coroutineScope.launch {
                    pagerState.animateScrollToPage(SwipeDestination.SCAN_HISTORY.index)
                }
                currentPage = SwipeDestination.SCAN_HISTORY.index
                viewModel.onNavigateToScanHistoryHandled()
            }
        }
        
        // Auto-save session broadcast receiver
        DisposableEffect(Unit) {
            val autoSaveBroadcastReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    if (intent?.action == "com.ner.wimap.AUTO_SAVE_SESSION") {
                        android.util.Log.d("MainActivity", "Received auto-save broadcast")
                        viewModel.autoSaveCurrentScanSession()
                    }
                }
            }
            
            val filter = android.content.IntentFilter("com.ner.wimap.AUTO_SAVE_SESSION")
            registerReceiver(autoSaveBroadcastReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            
            onDispose {
                unregisterReceiver(autoSaveBroadcastReceiver)
            }
        }
        
        // Back button handling
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        DisposableEffect(currentScreen, currentPage) {
            val callback = backDispatcher?.addCallback {
                when {
                    currentScreen == "settings" -> {
                        // From settings, go back to main
                        viewModel.navigateToMain()
                    }
                    currentScreen == "main" -> {
                        when (currentPage) {
                            SwipeDestination.WIFI_LOCATOR.index -> {
                                // From WiFi locator, go back to main
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                                currentPage = SwipeDestination.MAIN.index
                            }
                            SwipeDestination.PINNED.index -> {
                                // From pinned, go back to main
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                                currentPage = SwipeDestination.MAIN.index
                            }
                            SwipeDestination.MAPS.index -> {
                                // From maps, go back to main
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                                currentPage = SwipeDestination.MAIN.index
                            }
                            SwipeDestination.SCAN_HISTORY.index -> {
                                // From scan history, go back to main
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                                currentPage = SwipeDestination.MAIN.index
                            }
                            SwipeDestination.MAIN.index -> {
                                // On main screen, minimize app instead of closing
                                moveTaskToBack(true)
                            }
                        }
                    }
                }
            }
            onDispose {
                callback?.remove()
            }
        }
        
        // Reset to main page when currentScreen changes to main
        LaunchedEffect(currentScreen) {
            if (currentScreen == "main" && pagerState.currentPage != SwipeDestination.MAIN.index) {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                }
                currentPage = SwipeDestination.MAIN.index
            }
        }
        
        if (currentScreen == "settings") {
            SettingsScreen(
                ssidFilter = ssidFilter,
                onSsidFilterChange = { viewModel.onSsidFilterChange(it) },
                securityFilter = securityFilter,
                onSecurityFilterChange = { viewModel.onSecurityFilterChange(it) },
                rssiThreshold = rssiThreshold,
                onRssiThresholdChange = { viewModel.onRssiThresholdChange(it) },
                bssidFilter = bssidFilter,
                onBssidFilterChange = { viewModel.onBssidFilterChange(it) },
                availableSecurityTypes = availableSecurityTypes,
                passwords = passwords,
                onAddPassword = { viewModel.onAddPassword(it) },
                onRemovePassword = { viewModel.onRemovePassword(it) },
                maxRetries = maxRetries,
                onMaxRetriesChange = { viewModel.setMaxRetries(it) },
                connectionTimeoutSeconds = connectionTimeoutSeconds,
                onConnectionTimeoutChange = { viewModel.setConnectionTimeoutSeconds(it) },
                rssiThresholdForConnection = rssiThresholdForConnection,
                onRssiThresholdForConnectionChange = { viewModel.setRssiThresholdForConnection(it) },
                hideNetworksUnseenForSeconds = hideNetworksUnseenForSeconds,
                onHideNetworksUnseenForSecondsChange = { viewModel.setHideNetworksUnseenForSeconds(it) },
                isBackgroundScanningEnabled = isBackgroundScanningEnabled,
                onToggleBackgroundScanning = { enabled -> viewModel.toggleBackgroundScanning(this@MainActivity, enabled) },
                isAutoUploadEnabled = isAutoUploadEnabled,
                onToggleAutoUpload = { enabled -> viewModel.toggleAutoUpload(enabled) },
                onClearAllData = { viewModel.clearAllData() },
                onBack = { viewModel.navigateToMain() },
                onNavigateToScanHistory = { viewModel.navigateToScanHistory() }
            )
        } else {
            SwipeNavigationContainer(
                initialPage = SwipeDestination.MAIN.index,
                pagerState = pagerState,
                onPageChanged = { pageIndex ->
                    currentPage = pageIndex
                }
            ) { pageIndex, innerPagerState ->
                when (pageIndex) {
                    SwipeDestination.WIFI_LOCATOR.index -> {
                        WiFiLocatorScreen(
                            wifiNetworks = wifiNetworks,
                            onBack = {
                                // Navigate back to main screen (center page)
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                            },
                            onStartLocatorScanning = { network ->
                                viewModel.startLocatorScanning(network)
                            },
                            onStopLocatorScanning = {
                                viewModel.stopLocatorScanning()
                            },
                            locatorRSSI = locatorRSSI,
                            isLocatorActive = isLocatorScanning
                        )
                    }
                    SwipeDestination.PINNED.index -> {
                        PinnedNetworksScreen(
                            pinnedNetworks = pinnedNetworks,
                            successfulPasswords = successfulPasswords,
                            connectingNetworks = connectingNetworks,
                            onBack = { 
                                // Navigate back to main screen (center page)
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                            },
                            onDeletePinnedNetwork = { network ->
                                viewModel.deletePinnedNetwork(network)
                            },
                            onDeletePinnedNetworks = { networks ->
                                networks.forEach { network ->
                                    viewModel.deletePinnedNetwork(network)
                                }
                            },
                            onConnectToPinnedNetwork = { network ->
                                viewModel.connectToPinnedNetwork(network)
                            },
                            onSharePinnedNetwork = { network ->
                                viewModel.sharePinnedNetwork(this@MainActivity, network)
                            },
                            onSharePinnedNetworks = { networks ->
                                // For now, share functionality is included in the Export dialog
                                // Users can select "Share Only" option in the export dialog
                            },
                            onExportPinnedNetwork = { network, format, action ->
                                viewModel.exportPinnedNetwork(this@MainActivity, network, format, action)
                            },
                            onExportPinnedNetworks = { networks, format, action ->
                                // Convert PinnedNetworks to WifiNetworks for bulk export
                                val wifiNetworks = networks.map { network ->
                                    // Validate timestamp
                                    val validTimestamp = if (network.timestamp < 1000000000000L) {
                                        System.currentTimeMillis()
                                    } else {
                                        network.timestamp
                                    }
                                    
                                    WifiNetwork(
                                        ssid = network.ssid,
                                        bssid = network.bssid,
                                        rssi = network.rssi,
                                        channel = network.channel,
                                        security = network.security,
                                        latitude = network.latitude,
                                        longitude = network.longitude,
                                        timestamp = validTimestamp,
                                        password = network.savedPassword,
                                        comment = network.comment ?: "",
                                        photoPath = network.photoUri,
                                        isPinned = true,
                                        peakRssi = network.rssi,
                                        peakRssiLatitude = network.latitude,
                                        peakRssiLongitude = network.longitude,
                                        lastSeenTimestamp = validTimestamp
                                    )
                                }
                                // Use ExportManager directly for bulk export
                                val exportManager = com.ner.wimap.ui.viewmodel.ExportManager(lifecycleScope)
                                exportManager.exportWifiNetworks(this@MainActivity, wifiNetworks, format, action)
                            },
                            onShowNetworksOnMap = { networks ->
                                // Convert PinnedNetworks to WifiNetworks and set them for map display
                                val wifiNetworks = networks.map { network ->
                                    // Validate timestamp
                                    val validTimestamp = if (network.timestamp < 1000000000000L) {
                                        System.currentTimeMillis()
                                    } else {
                                        network.timestamp
                                    }
                                    
                                    WifiNetwork(
                                        ssid = network.ssid,
                                        bssid = network.bssid,
                                        rssi = network.rssi,
                                        channel = network.channel,
                                        security = network.security,
                                        latitude = network.latitude,
                                        longitude = network.longitude,
                                        timestamp = validTimestamp,
                                        password = network.savedPassword,
                                        comment = network.comment ?: "",
                                        photoPath = network.photoUri,
                                        isPinned = true,
                                        peakRssi = network.rssi,
                                        peakRssiLatitude = network.latitude,
                                        peakRssiLongitude = network.longitude,
                                        lastSeenTimestamp = validTimestamp
                                    )
                                }
                                
                                // Set the selected networks for map display
                                viewModel.setNetworksForMap(wifiNetworks)
                                
                                // Navigate to the Maps page in the swipeable navigation
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(SwipeDestination.MAPS.index)
                                    // Force page update
                                    currentPage = SwipeDestination.MAPS.index
                                }
                            },
                            onUpdatePinnedNetworkData = { bssid, ssid, comment, password, photoPath, clearPhoto ->
                                viewModel.updateTemporaryNetworkDataWithPhotoDeletion(bssid, ssid, comment, password, photoPath, clearPhoto)
                            },
                            onNavigateToPage = { pageIndex ->
                                currentPage = pageIndex
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(pageIndex)
                                }
                            },
                            currentPage = currentPage
                        )
                    }
                    SwipeDestination.MAIN.index -> {
                        MainScreen(
                            wifiNetworks = wifiNetworks,
                            isScanning = isScanning,
                            hasEverScanned = hasEverScanned,
                            connectionStatus = connectionStatus,
                            uploadStatus = uploadStatus,
                            showPasswordDialog = showPasswordDialog,
                            networkForPasswordInput = networkForPasswordInput,
                            showPermissionRationaleDialog = showPermissionRationaleDialog,
                            permissionsRationaleMessage = permissionsRationaleMessage,
                            showEmptyPasswordListDialog = showEmptyPasswordListDialog,
                            networkForEmptyPasswordDialog = networkForEmptyPasswordDialog,
                            isBackgroundScanningEnabled = isBackgroundScanningEnabled,
                            isBackgroundServiceActive = isBackgroundServiceActive,
                            isAutoUploadEnabled = isAutoUploadEnabled,
                            pinnedNetworks = pinnedNetworks,
                            isConnecting = isConnecting,
                            connectingNetworks = connectingNetworks,
                            connectionProgress = connectionProgress,
                            successfulPasswords = successfulPasswords,
                            currentPassword = currentPassword,
                            currentAttempt = currentAttempt,
                            totalAttempts = totalAttempts,
                            connectingNetworkName = connectingNetworkName,
                            currentSortingMode = sortingMode,
                            onStartScan = { viewModel.startScan() },
                            onStopScan = { viewModel.stopScan() },
                            onConnect = { network -> viewModel.connectToNetwork(network) },
                            onPasswordEntered = { password -> viewModel.onPasswordEntered(password) },
                            onDismissPasswordDialog = { viewModel.dismissPasswordDialog() },
                            onDismissPermissionRationaleDialog = { viewModel.dismissPermissionRationaleDialog() },
                            onRationalePermissionsRequest = { viewModel.onUserApprovesRationaleRequest() },
                            onRationaleOpenSettings = { viewModel.onUserRequestsOpenSettings() },
                            onDismissEmptyPasswordListDialog = { viewModel.dismissEmptyPasswordListDialog() },
                            onOpenPasswordManagement = { 
                                viewModel.dismissEmptyPasswordListDialog()
                                viewModel.navigateToSettings()
                            },
                            onToggleBackgroundScanning = { enabled -> viewModel.toggleBackgroundScanning(this@MainActivity, enabled) },
                            onToggleAutoUpload = { enabled -> viewModel.toggleAutoUpload(enabled) },
                            onUploadScanResults = { viewModel.uploadScanResultsToFirebase() },
                            onClearUploadStatus = { viewModel.clearUploadStatus() },
                            onOpenSettings = { viewModel.navigateToSettings() },
                            onExportWithFormatAndAction = { format, action ->
                                viewModel.exportWifiNetworks(this@MainActivity, format, action)
                            },
                            onShareCsv = { viewModel.shareCsv(this@MainActivity) },
                            onClearNetworks = { viewModel.clearNetworks() },
                            onOpenPinnedNetworks = { 
                                // Navigate to pinned networks page via swipe
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(SwipeDestination.PINNED.index)
                                }
                            },
                            onPinNetwork = { network, comment, password, photoUri ->
                                // Use the new temporary data system for pinning
                                viewModel.pinNetworkWithTemporaryData(network.bssid, true)
                            },
                            onUnpinNetwork = { bssid -> 
                                // Use the new temporary data system for unpinning
                                viewModel.pinNetworkWithTemporaryData(bssid, false)
                            },
                            onClearConnectionProgress = { viewModel.clearConnectionProgress() },
                            onUpdateNetworkData = { network, comment, password, photoUri ->
                                // Use temporary network data for all networks (pinned and unpinned)
                                viewModel.updateTemporaryNetworkData(network.bssid, network.ssid, comment, password, photoUri)
                            },
                            onUpdateNetworkDataWithPhotoDeletion = { network, comment, password, photoUri, clearPhoto ->
                                // Use temporary network data with photo deletion support
                                viewModel.updateTemporaryNetworkDataWithPhotoDeletion(network.bssid, network.ssid, comment, password, photoUri, clearPhoto)
                            },
                            onOpenMaps = {
                                // Navigate to maps page via swipe
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(SwipeDestination.MAPS.index)
                                }
                            },
                            onSortingModeChanged = { mode -> viewModel.setSortingMode(mode) },
                            onClearConnectionStatus = { viewModel.clearConnectionStatus() }
                        )
                    }
                    SwipeDestination.MAPS.index -> {
                        // Create a Maps screen composition that integrates with the swipe navigation
                        MapsScreenWrapper(
                            wifiNetworks = networksForMap ?: wifiNetworks, // Use selected networks if available, otherwise all networks
                            isFilteredView = networksForMap != null, // Show indicator when displaying selected networks
                            onBack = {
                                // Clear the selected networks when leaving map
                                viewModel.clearNetworksForMap()
                                // Navigate back to main screen (center page)
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                            },
                            onNavigateToPage = { pageIndex ->
                                currentPage = pageIndex
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(pageIndex)
                                }
                            },
                            currentPage = currentPage,
                            onClearSelection = {
                                // Clear the selected networks but stay on map screen
                                viewModel.clearNetworksForMap()
                            }
                        )
                    }
                    SwipeDestination.SCAN_HISTORY.index -> {
                        ScanHistoryScreen(
                            onNavigateBack = {
                                // Navigate back to main screen (center page)
                                coroutineScope.launch {
                                    innerPagerState.animateScrollToPage(SwipeDestination.MAIN.index)
                                }
                                currentPage = SwipeDestination.MAIN.index
                            }
                        )
                    }
                }
            }
        }
        
        // GDPR Consent Dialog (takes priority over legacy dialog)
        if (showGDPRConsentDialog) {
            GDPRConsentDialog(
                onConsentGiven = { essential, analytics, advertising, location, dataUpload, userAge ->
                    viewModel.onGDPRConsentGranted(essential, analytics, advertising, location, dataUpload, userAge)
                },
                onDismiss = { viewModel.onGDPRConsentDenied() }
            )
        }
        
        // Consent Required Dialog (blocks app usage)
        if (showConsentRequiredDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { }, // Cannot dismiss without action
                title = {
                    androidx.compose.material3.Text(
                        text = "Consent Required",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    androidx.compose.material3.Text(
                        text = "WiMap requires consent to essential features (GPS location and data sharing) to function as a WiFi mapping application. Please review and accept the terms to continue."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.onShowConsentFromRequiredDialog() }
                    ) {
                        androidx.compose.material3.Text("Review Terms")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { viewModel.dismissConsentRequiredDialog() }
                    ) {
                        androidx.compose.material3.Text("Cancel")
                    }
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
        }
        
        // Scan Summary Dialog
        if (showScanSummaryDialog && currentScanSummary != null) {
            val (networkCount, defaultTitle) = currentScanSummary!!
            ScanSummaryDialog(
                networkCount = networkCount,
                defaultTitle = defaultTitle,
                onSave = { title -> viewModel.onSaveScanSession(title) },
                onCancel = { viewModel.onCancelScanSession() }
            )
        }
        
        // Legacy Privacy Consent Dialog (for backward compatibility)
        PrivacyConsentDialog(
            isVisible = showPrivacyConsentDialog,
            onConsentGranted = { viewModel.onPrivacyConsentGranted() },
            onConsentDenied = { viewModel.onPrivacyConsentDenied() },
            onDismiss = { viewModel.dismissPrivacyConsentDialog() }
        )
        
        // Export Success Dialog
        if (showExportSuccessDialog) {
            ExportSuccessDialog(
                message = exportSuccessMessage,
                onDismiss = { showExportSuccessDialog = false }
            )
        }
        
        // Export Error Dialog
        if (showExportErrorDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExportErrorDialog = false },
                title = {
                    androidx.compose.material3.Text(
                        text = "Export Error",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    androidx.compose.material3.Text(text = exportErrorMessage)
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showExportErrorDialog = false }
                    ) {
                        androidx.compose.material3.Text("OK")
                    }
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            )
        }
    }
}