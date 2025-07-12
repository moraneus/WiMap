package com.ner.wimap.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.Coordinates
import com.ner.wimap.utils.PermissionUtils
import com.ner.wimap.utils.OUILookupManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import com.ner.wimap.R

// Helper function to convert frequency to channel
fun convertFrequencyToChannel(freq: Int): Int {
    return when (freq) {
        in 2412..2484 -> (freq - 2412) / 5 + 1 // For 2.4 GHz
        in 5170..5825 -> (freq - 5170) / 5 + 34 // For 5 GHz
        else -> -1 // Unknown or unsupported frequency
    }
}

// Helper function to convert channel to frequency for targeted scanning
fun convertChannelToFrequency(channel: Int): Int {
    return when (channel) {
        in 1..14 -> 2412 + (channel - 1) * 5 // 2.4 GHz channels
        in 34..165 -> 5170 + (channel - 34) * 5 // 5 GHz channels (simplified)
        36 -> 5180
        40 -> 5200
        44 -> 5220
        48 -> 5240
        52 -> 5260
        56 -> 5280
        60 -> 5300
        64 -> 5320
        100 -> 5500
        104 -> 5520
        108 -> 5540
        112 -> 5560
        116 -> 5580
        120 -> 5600
        124 -> 5620
        128 -> 5640
        132 -> 5660
        136 -> 5680
        140 -> 5700
        144 -> 5720
        149 -> 5745
        153 -> 5765
        157 -> 5785
        161 -> 5805
        165 -> 5825
        else -> -1 // Unknown channel
    }
}

// Helper function to determine security type from capabilities string
fun getSecurityType(capabilities: String): String {
    return when {
        capabilities.contains("WPA3") -> "WPA3"
        capabilities.contains("WPA2") -> "WPA2"
        capabilities.contains("WPA") -> "WPA"
        capabilities.contains("WEP") -> "WEP"
        capabilities.isBlank() || capabilities.contains("[ESS]") || capabilities.contains("[IBSS]") -> "Open"
        else -> "Unknown"
    }
}

class WifiScanner(private val context: Context, private val currentLocationFlow: StateFlow<Coordinates?>) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _wifiNetworks = MutableLiveData<List<WifiNetwork>>()
    val wifiNetworks: LiveData<List<WifiNetwork>> = _wifiNetworks

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _connectionStatus = MutableLiveData<String?>()
    val connectionStatus: LiveData<String?> = _connectionStatus

    private var currentNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private val scanInterval = 10000L // 10 seconds, adjust as needed
    private val locatorScanInterval = 800L // 0.8 seconds for aggressive locator mode
    private val connectionRssiInterval = 2000L // 2 seconds for connection-based RSSI tracking
    private val handler = Handler(Looper.getMainLooper())
    private val locatorHandler = Handler(Looper.getMainLooper())
    private val connectionRssiHandler = Handler(Looper.getMainLooper())
    
    // Background coroutine scope for Wi-Fi operations
    private val wifiScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanningJob: Job? = null
    private var connectionJob: Job? = null
    private var locatorScanningJob: Job? = null
    
    // Locator mode state
    private val _isLocatorScanning = MutableLiveData(false)
    val isLocatorScanning: LiveData<Boolean> = _isLocatorScanning
    private var targetNetworkForLocator: WifiNetwork? = null
    
    // LiveData for locator-specific RSSI updates
    private val _locatorRSSI = MutableLiveData<Int>()
    val locatorRSSI: LiveData<Int> = _locatorRSSI
    
    // Connection-based RSSI tracking state
    private var isConnectionBasedTracking = false
    private var connectionTrackingCallback: ConnectivityManager.NetworkCallback? = null
    private var lastConnectionAttemptTime = 0L

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent.action) {
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                } else {
                    true
                }
                
                // Process scan results on background thread
                wifiScope.launch {
                    if (success) {
                        processScanResults()
                    } else {
                        withContext(Dispatchers.Main) {
                            _wifiNetworks.postValue(emptyList())
                        }
                    }
                    
                    // Schedule next scan on main thread if still scanning
                    withContext(Dispatchers.Main) {
                        if (_isScanning.value == true) {
                            handler.postDelayed(scanRunnable, scanInterval)
                        }
                    }
                }
            }
        }
    }

    private val scanRunnable = Runnable {
        if (_isScanning.value == true) {
            wifiScope.launch {
                performSingleScan()
            }
        }
    }

    init {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)
    }

    fun startScanning() {
        if (_isScanning.value == false) {
            scanningJob?.cancel()
            scanningJob = wifiScope.launch {
                withContext(Dispatchers.Main) {
                    _isScanning.value = true
                    _connectionStatus.postValue(context.getString(R.string.wifi_scan_starting))
                }
                
                try {
                    startScanningLoop()
                } catch (e: CancellationException) {
                    // Scanning was cancelled
                    withContext(Dispatchers.Main) {
                        _connectionStatus.postValue(context.getString(R.string.wifi_scan_cancelled))
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.postValue(context.getString(R.string.wifi_scan_error, e.message ?: "Unknown error"))
                    }
                }
            }
        }
    }

    fun stopScanning() {
        scanningJob?.cancel()
        scanningJob = null
        _isScanning.value = false
        handler.removeCallbacks(scanRunnable)
        _connectionStatus.postValue(context.getString(R.string.wifi_scan_stopped))
    }

    /**
     * Start aggressive RSSI tracking for WiFi Locator mode
     * Uses connection-based approach to force AP responses with fresh RSSI
     */
    fun startLocatorScanning(targetNetwork: WifiNetwork) {
        if (_isLocatorScanning.value == true) {
            stopLocatorScanning()
        }
        
        targetNetworkForLocator = targetNetwork
        _locatorRSSI.postValue(targetNetwork.rssi) // Set initial RSSI
        _isLocatorScanning.value = true
        
        val frequency = convertChannelToFrequency(targetNetwork.channel)
        val freqInfo = if (frequency > 0) " (${frequency}MHz)" else ""
        
        // Determine the best tracking approach based on network security
        val isOpenNetwork = targetNetwork.security.contains("Open", ignoreCase = true)
        
        if (isOpenNetwork && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use connection-based tracking for open networks (forces immediate AP response)
            _connectionStatus.postValue("🔗 Connection-based tracking: ${targetNetwork.ssid} Ch${targetNetwork.channel}$freqInfo")
            startConnectionBasedRssiTracking(targetNetwork)
        } else {
            // Use probe-request based tracking for secured networks
            _connectionStatus.postValue("📡 Probe-based tracking: ${targetNetwork.ssid} Ch${targetNetwork.channel}$freqInfo")
            startProbeBasedRssiTracking(targetNetwork)
        }
    }

    /**
     * Stop WiFi Locator scanning
     */
    fun stopLocatorScanning() {
        locatorScanningJob?.cancel()
        locatorScanningJob = null
        _isLocatorScanning.value = false
        targetNetworkForLocator = null
        locatorHandler.removeCallbacks(locatorScanRunnable)
        
        // Stop connection-based tracking
        stopConnectionBasedRssiTracking()
        
        _connectionStatus.postValue("🛑 Locator scanning stopped")
    }

    // Independent locator scanning runnable
    private val locatorScanRunnable = Runnable {
        if (_isLocatorScanning.value == true && targetNetworkForLocator != null) {
            performIndependentLocatorScan()
        }
    }

    /**
     * Start independent high-frequency scanning that doesn't depend on regular WiFi scanning
     */
    private fun startIndependentLocatorScanning() {
        locatorHandler.post(locatorScanRunnable)
    }

    /**
     * Perform a single independent scan for locator mode with channel-specific targeting
     * This uses channel-specific scanning to catch packets immediately when AP transmits
     */
    private fun performIndependentLocatorScan() {
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            _isLocatorScanning.value = false
            _connectionStatus.postValue("⚠️ Locator scanning requires WiFi permissions")
            return
        }

        if (!wifiManager.isWifiEnabled) {
            _connectionStatus.postValue("⚠️ WiFi disabled - locator scanning paused")
            scheduleNextLocatorScan()
            return
        }

        val target = targetNetworkForLocator
        if (target == null) {
            scheduleNextLocatorScan()
            return
        }

        try {
            // Perform channel-specific scanning for the target network
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                performChannelSpecificScan(target)
            } else {
                // Fallback to regular scanning for older Android versions
                performRegularScanWithChannelAwareness(target)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("WifiScanner", "Locator scan permission error", e)
            _connectionStatus.postValue("⚠️ Locator scan permission error")
        }
        
        // Schedule next scan regardless
        scheduleNextLocatorScan()
    }

    /**
     * Perform channel-specific scan for Android R+ (API 30+)
     * This focuses scanning on the target network's specific channel
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun performChannelSpecificScan(target: WifiNetwork) {
        try {
            // Convert channel to frequency for targeted scanning
            val frequency = convertChannelToFrequency(target.channel)
            
            if (frequency > 0) {
                android.util.Log.d("WifiScanner", "🎯 Channel-specific scan: ${target.ssid} on channel ${target.channel} (${frequency}MHz)")
                _connectionStatus.postValue("📡 Tuning to channel ${target.channel} for ${target.ssid}")
                
                // Use rapid scanning technique for channel awareness
                // Android doesn't allow direct channel selection via public APIs,
                // so we use rapid scanning with frequency filtering
                val scanStarted = wifiManager.startScan()
                if (scanStarted) {
                    wifiScope.launch {
                        delay(200) // Shorter delay for aggressive scanning
                        processChannelSpecificResults(target, frequency)
                    }
                } else {
                    // Fallback to regular scan
                    performRegularScanWithChannelAwareness(target)
                }
            } else {
                android.util.Log.w("WifiScanner", "Invalid frequency for channel ${target.channel}, falling back to regular scan")
                performRegularScanWithChannelAwareness(target)
            }
        } catch (e: Exception) {
            android.util.Log.e("WifiScanner", "Channel-specific scan failed, falling back to regular scan", e)
            performRegularScanWithChannelAwareness(target)
        }
    }

    /**
     * Perform rapid scanning with channel awareness for all Android versions
     * Uses aggressive scanning to maximize chance of catching target AP packets
     */
    private fun performRegularScanWithChannelAwareness(target: WifiNetwork) {
        try {
            android.util.Log.d("WifiScanner", "🔄 Rapid channel scan: ${target.ssid} on channel ${target.channel}")
            _connectionStatus.postValue("📡 Rapid scanning ${target.ssid} (Ch${target.channel})")
            
            val scanStarted = wifiManager.startScan()
            if (scanStarted) {
                wifiScope.launch {
                    // Aggressive scanning approach: multiple quick scans to catch more packets
                    repeat(5) { attempt ->
                        delay(150) // Very short delay between attempts
                        processIndependentLocatorResults()
                        
                        // Start another scan immediately unless it's the last attempt
                        if (attempt < 4) {
                            try {
                                wifiManager.startScan()
                            } catch (e: Exception) {
                                // Ignore scan throttling errors - continue with existing results
                                android.util.Log.w("WifiScanner", "Scan throttled on attempt ${attempt + 1}")
                            }
                        }
                    }
                }
            } else {
                android.util.Log.w("WifiScanner", "Failed to start rapid scan")
                _connectionStatus.postValue("⚠️ Failed to start rapid scan")
            }
        } catch (e: Exception) {
            android.util.Log.e("WifiScanner", "Rapid scan error", e)
            _connectionStatus.postValue("⚠️ Scan error: ${e.message}")
        }
    }

    /**
     * Process results from channel-specific scanning
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun processChannelSpecificResults(target: WifiNetwork, targetFrequency: Int) = withContext(Dispatchers.IO) {
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            return@withContext
        }
        
        try {
            val results: List<ScanResult> = wifiManager.scanResults
            android.util.Log.d("WifiScanner", "🔍 Processing ${results.size} channel-specific results for ${target.ssid}")
            
            // Filter results to prioritize those on the target frequency
            val targetResults = results.filter { scanResult ->
                scanResult.frequency == targetFrequency && 
                (scanResult.BSSID == target.bssid || scanResult.SSID == target.ssid)
            }
            
            val targetScanResult = targetResults.firstOrNull() ?: results.find { scanResult ->
                scanResult.BSSID == target.bssid || 
                (scanResult.SSID == target.ssid && scanResult.BSSID != null)
            }
            
            if (targetScanResult != null) {
                val newRssi = targetScanResult.level
                val isChannelMatch = targetScanResult.frequency == targetFrequency
                
                withContext(Dispatchers.Main) {
                    _locatorRSSI.postValue(newRssi)
                    val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                    val channelInfo = if (isChannelMatch) "📡 Ch${target.channel}" else "📶 Off-Ch"
                    _connectionStatus.postValue("$channelInfo Fresh RSSI: ${newRssi} dBm @ $timestamp")
                }
                
                android.util.Log.d("WifiScanner", "🎯 Channel-specific RSSI: ${target.ssid} = ${newRssi} dBm (freq: ${targetScanResult.frequency}MHz, expected: ${targetFrequency}MHz)")
            } else {
                withContext(Dispatchers.Main) {
                    _connectionStatus.postValue("⚠️ No packets on channel ${target.channel}")
                }
                android.util.Log.w("WifiScanner", "No packets found for ${target.ssid} on channel ${target.channel}")
            }
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                _connectionStatus.postValue("⚠️ Permission error in channel scan")
            }
            android.util.Log.e("WifiScanner", "Permission error in channel-specific scan", e)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _connectionStatus.postValue("⚠️ Channel scan error")
            }
            android.util.Log.e("WifiScanner", "Error in channel-specific scan", e)
        }
    }

    /**
     * Schedule the next independent locator scan
     */
    private fun scheduleNextLocatorScan() {
        if (_isLocatorScanning.value == true) {
            locatorHandler.postDelayed(locatorScanRunnable, locatorScanInterval)
        }
    }

    /**
     * Process scan results independently for locator mode
     * This doesn't wait for broadcasts - it directly reads scan results
     */
    private suspend fun processIndependentLocatorResults() = withContext(Dispatchers.IO) {
        val target = targetNetworkForLocator ?: return@withContext
        
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            return@withContext
        }
        
        try {
            val results: List<ScanResult> = wifiManager.scanResults
            android.util.Log.d("WifiScanner", "🔍 Processing ${results.size} scan results for locator")
            
            // Find the target network in scan results
            val targetScanResult = results.find { scanResult ->
                scanResult.BSSID == target.bssid || 
                (scanResult.SSID == target.ssid && scanResult.BSSID != null)
            }
            
            if (targetScanResult != null) {
                // Found target network - update RSSI
                val newRssi = targetScanResult.level
                withContext(Dispatchers.Main) {
                    _locatorRSSI.postValue(newRssi)
                    // Update status with fresh measurement and timestamp
                    val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    _connectionStatus.postValue("📡 Fresh RSSI: ${newRssi} dBm @ $timestamp")
                }
                android.util.Log.d("WifiScanner", "🎯 Independent locator RSSI update: ${target.ssid} = ${newRssi} dBm")
            } else {
                // Target network not found in latest scan
                withContext(Dispatchers.Main) {
                    _connectionStatus.postValue("⚠️ Target network not visible in scan")
                }
                android.util.Log.w("WifiScanner", "Target network ${target.ssid} not found in independent scan results")
            }
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                _connectionStatus.postValue("⚠️ Permission error in independent locator scan")
            }
            android.util.Log.e("WifiScanner", "Permission error in independent locator scan", e)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _connectionStatus.postValue("⚠️ Error processing independent locator scan")
            }
            android.util.Log.e("WifiScanner", "Error in independent locator scan", e)
        }
    }

    /**
     * Start connection-based RSSI tracking - forces AP to respond with fresh RSSI
     * This approach initiates connection attempts to trigger immediate AP responses
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startConnectionBasedRssiTracking(target: WifiNetwork) {
        isConnectionBasedTracking = true
        
        // Start with immediate connection attempt
        performConnectionBasedRssiCheck(target)
        
        // Schedule periodic connection-based RSSI checks
        scheduleNextConnectionRssiCheck(target)
    }
    
    /**
     * Stop connection-based RSSI tracking
     */
    private fun stopConnectionBasedRssiTracking() {
        isConnectionBasedTracking = false
        connectionRssiHandler.removeCallbacks(connectionRssiRunnable)
        
        // Clean up any active connection callback
        connectionTrackingCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                android.util.Log.w("WifiScanner", "Error unregistering connection tracking callback", e)
            }
            connectionTrackingCallback = null
        }
        
        // Unbind from any process network
        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (e: Exception) {
            android.util.Log.w("WifiScanner", "Error unbinding process network", e)
        }
    }
    
    /**
     * Perform connection-based RSSI check - initiates connection to force AP response
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun performConnectionBasedRssiCheck(target: WifiNetwork) {
        if (!isConnectionBasedTracking || targetNetworkForLocator?.bssid != target.bssid) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // Throttle connection attempts to avoid overwhelming the AP
        if (currentTime - lastConnectionAttemptTime < 1500) {
            scheduleNextConnectionRssiCheck(target)
            return
        }
        
        lastConnectionAttemptTime = currentTime
        
        try {
            android.util.Log.d("WifiScanner", "🔗 Connection-based RSSI check for ${target.ssid}")
            
            // Clean up any existing callback
            connectionTrackingCallback?.let { callback ->
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (e: Exception) { /* Ignore */ }
            }
            
            // Create network specifier for connection attempt
            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(target.ssid)
                .setIsEnhancedOpen(false)
            
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()
            
            // Track connection attempt state
            var hasReceivedRssi = false
            var connectionAttemptActive = true
            
            connectionTrackingCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    if (!connectionAttemptActive || !isConnectionBasedTracking) return
                    
                    try {
                        // Get fresh RSSI from the connection attempt
                        val currentWifiInfo = wifiManager.connectionInfo
                        if (currentWifiInfo != null && currentWifiInfo.bssid != null) {
                            val freshRssi = currentWifiInfo.rssi
                            
                            // Verify this is our target network
                            val connectedSsid = currentWifiInfo.ssid?.replace("\"", "")
                            if (connectedSsid == target.ssid || currentWifiInfo.bssid == target.bssid) {
                                hasReceivedRssi = true
                                
                                // Update RSSI with fresh value
                                _locatorRSSI.postValue(freshRssi)
                                val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
                                _connectionStatus.postValue("🔗 Live RSSI: ${freshRssi} dBm @ $timestamp")
                                
                                android.util.Log.d("WifiScanner", "🎯 Connection-based RSSI: ${target.ssid} = ${freshRssi} dBm")
                            }
                        }
                        
                        // Immediately disconnect to avoid actually connecting
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                connectivityManager.bindProcessToNetwork(null)
                                connectivityManager.unregisterNetworkCallback(this)
                            } catch (e: Exception) { /* Ignore */ }
                        }, 200) // Very short connection time
                        
                    } catch (e: Exception) {
                        android.util.Log.e("WifiScanner", "Error getting connection-based RSSI", e)
                    }
                    
                    connectionAttemptActive = false
                }
                
                override fun onUnavailable() {
                    super.onUnavailable()
                    connectionAttemptActive = false
                    
                    if (!hasReceivedRssi) {
                        // Connection failed - fall back to regular scan
                        wifiScope.launch {
                            processIndependentLocatorResults()
                        }
                    }
                }
                
                override fun onLost(network: Network) {
                    super.onLost(network)
                    // Connection lost - this is expected behavior
                }
            }
            
            // Set timeout for connection attempt
            Handler(Looper.getMainLooper()).postDelayed({
                if (connectionAttemptActive && !hasReceivedRssi) {
                    connectionAttemptActive = false
                    // Timeout - fall back to scan
                    wifiScope.launch {
                        processIndependentLocatorResults()
                    }
                }
            }, 3000) // 3 second timeout
            
            // Request connection
            connectivityManager.requestNetwork(networkRequest, connectionTrackingCallback!!)
            
        } catch (e: SecurityException) {
            android.util.Log.e("WifiScanner", "Permission error in connection-based tracking", e)
            _connectionStatus.postValue("⚠️ Connection tracking permission error")
        } catch (e: Exception) {
            android.util.Log.e("WifiScanner", "Error in connection-based tracking", e)
            _connectionStatus.postValue("⚠️ Connection tracking error")
        }
        
        // Schedule next check
        scheduleNextConnectionRssiCheck(target)
    }
    
    /**
     * Schedule next connection-based RSSI check
     */
    private fun scheduleNextConnectionRssiCheck(target: WifiNetwork) {
        if (isConnectionBasedTracking) {
            connectionRssiHandler.postDelayed({
                performConnectionBasedRssiCheck(target)
            }, connectionRssiInterval)
        }
    }
    
    // Connection-based RSSI runnable
    private val connectionRssiRunnable = Runnable {
        targetNetworkForLocator?.let { target ->
            if (isConnectionBasedTracking && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                performConnectionBasedRssiCheck(target)
            }
        }
    }
    
    /**
     * Start probe-based RSSI tracking for secured networks
     * This uses rapid scanning with connection attempts to trigger AP responses
     */
    private fun startProbeBasedRssiTracking(target: WifiNetwork) {
        // Use the existing aggressive scanning as base
        startIndependentLocatorScanning()
        
        // Additionally, try authentication attempts for secured networks to force responses
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startAuthenticationProbes(target)
        }
    }
    
    /**
     * Start authentication probes to force AP responses
     * This creates fake authentication attempts that force the AP to respond immediately
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startAuthenticationProbes(target: WifiNetwork) {
        wifiScope.launch {
            while (_isLocatorScanning.value == true && targetNetworkForLocator?.bssid == target.bssid) {
                try {
                    // Create a fake connection attempt with wrong credentials to trigger AP response
                    val specifierBuilder = WifiNetworkSpecifier.Builder()
                        .setSsid(target.ssid)
                        .setWpa2Passphrase("fake_password_to_trigger_response_${System.currentTimeMillis()}")
                    
                    val networkRequest = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setNetworkSpecifier(specifierBuilder.build())
                        .build()
                    
                    var probeAttemptActive = true
                    
                    val probeCallback = object : ConnectivityManager.NetworkCallback() {
                        override fun onUnavailable() {
                            super.onUnavailable()
                            probeAttemptActive = false
                            
                            // Authentication failed (expected) - but this should trigger fresh scan results
                            wifiScope.launch {
                                delay(100) // Short delay for AP to update
                                processIndependentLocatorResults()
                            }
                        }
                        
                        override fun onAvailable(network: Network) {
                            super.onAvailable(network)
                            probeAttemptActive = false
                            // Unexpected success - disconnect immediately
                            try {
                                connectivityManager.bindProcessToNetwork(null)
                                connectivityManager.unregisterNetworkCallback(this)
                            } catch (e: Exception) { /* Ignore */ }
                        }
                    }
                    
                    // Set timeout for probe attempt
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (probeAttemptActive) {
                            probeAttemptActive = false
                            try {
                                connectivityManager.unregisterNetworkCallback(probeCallback)
                            } catch (e: Exception) { /* Ignore */ }
                        }
                    }, 2000) // 2 second timeout
                    
                    connectivityManager.requestNetwork(networkRequest, probeCallback)
                    
                    android.util.Log.d("WifiScanner", "🔍 Authentication probe sent to ${target.ssid}")
                    _connectionStatus.postValue("🔍 Probing ${target.ssid} for fresh RSSI...")
                    
                } catch (e: Exception) {
                    android.util.Log.w("WifiScanner", "Authentication probe failed", e)
                }
                
                // Wait before next probe attempt
                delay(3000) // 3 seconds between probes
            }
        }
    }

    private suspend fun startScanningLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                performSingleScan()
                delay(scanInterval)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _connectionStatus.postValue(context.getString(R.string.wifi_scan_error, e.message ?: "Unknown error"))
                }
                delay(scanInterval) // Wait before retrying
            }
        }
    }

    private suspend fun performSingleScan() = withContext(Dispatchers.IO) {
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            withContext(Dispatchers.Main) {
                _wifiNetworks.postValue(emptyList())
                _isScanning.value = false
                _connectionStatus.postValue(context.getString(R.string.wifi_scan_permission_required))
            }
            return@withContext
        }

        if (!wifiManager.isWifiEnabled) {
            withContext(Dispatchers.Main) {
                _wifiNetworks.postValue(emptyList())
                _connectionStatus.postValue(context.getString(R.string.wifi_disabled))
            }
            return@withContext
        }

        try {
            val scanInitiated = wifiManager.startScan()
            if (!scanInitiated) {
                withContext(Dispatchers.Main) {
                    _connectionStatus.postValue(context.getString(R.string.wifi_scan_failed_to_start))
                }
            }
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                _connectionStatus.postValue(context.getString(R.string.wifi_scan_permission_error, e.message ?: "Permission denied"))
            }
        }
    }

    private suspend fun processScanResults() = withContext(Dispatchers.IO) {
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            withContext(Dispatchers.Main) {
                _wifiNetworks.postValue(emptyList())
            }
            return@withContext
        }
        
        try {
            val results: List<ScanResult> = wifiManager.scanResults
            val currentLocation = currentLocationFlow.value
            val currentTime = System.currentTimeMillis()
            
            // Get existing networks to maintain peak RSSI data
            val existingNetworks = _wifiNetworks.value ?: emptyList()
            val existingNetworkMap = existingNetworks.associateBy { it.bssid }
            
            val networks = results.mapNotNull { scanResult ->
                if (scanResult.BSSID == null || scanResult.SSID == null) null
                else {
                    val currentRssi = scanResult.level
                    val existingNetwork = existingNetworkMap[scanResult.BSSID]
                    
                    // Determine peak RSSI and corresponding GPS coordinates
                    val (peakRssi, peakLat, peakLng) = if (existingNetwork != null) {
                        // If current RSSI is stronger than existing peak, update peak location
                        if (currentRssi > existingNetwork.peakRssi) {
                            Triple(
                                currentRssi,
                                currentLocation?.latitude ?: existingNetwork.peakRssiLatitude,
                                currentLocation?.longitude ?: existingNetwork.peakRssiLongitude
                            )
                        } else {
                            // Keep existing peak data
                            Triple(
                                existingNetwork.peakRssi,
                                existingNetwork.peakRssiLatitude,
                                existingNetwork.peakRssiLongitude
                            )
                        }
                    } else {
                        // New network - current location becomes peak location
                        Triple(
                            currentRssi,
                            currentLocation?.latitude,
                            currentLocation?.longitude
                        )
                    }
                    
                    WifiNetwork(
                        ssid = scanResult.SSID.ifEmpty { "<Hidden Network>" },
                        bssid = scanResult.BSSID,
                        rssi = currentRssi, // Current RSSI
                        channel = convertFrequencyToChannel(scanResult.frequency),
                        security = getSecurityType(scanResult.capabilities ?: ""),
                        latitude = currentLocation?.latitude, // Current location
                        longitude = currentLocation?.longitude, // Current location
                        timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) scanResult.timestamp else currentTime,
                        password = existingNetwork?.password, // Preserve existing password
                        vendor = OUILookupManager.getInstance().lookupVendorShort(scanResult.BSSID), // Lookup vendor from BSSID
                        peakRssi = peakRssi, // Peak RSSI seen so far
                        peakRssiLatitude = peakLat, // GPS coordinates where peak RSSI was observed
                        peakRssiLongitude = peakLng, // GPS coordinates where peak RSSI was observed
                        lastSeenTimestamp = currentTime // Update last seen time
                    )
                }
            }.sortedByDescending { it.rssi }
            
            // Update UI on main thread
            withContext(Dispatchers.Main) {
                _wifiNetworks.postValue(networks)
            }
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                _wifiNetworks.postValue(emptyList())
                _isScanning.value = false 
                _connectionStatus.postValue("Permission error processing scan results.")
            }
        }
    }

    /**
     * Process scan results specifically for locator mode
     * Extracts RSSI for the target network and updates locator RSSI
     */
    private suspend fun processLocatorScanResults() = withContext(Dispatchers.IO) {
        val target = targetNetworkForLocator ?: return@withContext
        
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            return@withContext
        }
        
        try {
            val results: List<ScanResult> = wifiManager.scanResults
            
            // Find the target network in scan results
            val targetScanResult = results.find { scanResult ->
                scanResult.BSSID == target.bssid || 
                (scanResult.SSID == target.ssid && scanResult.BSSID != null)
            }
            
            if (targetScanResult != null) {
                // Found target network - update RSSI
                val newRssi = targetScanResult.level
                withContext(Dispatchers.Main) {
                    _locatorRSSI.postValue(newRssi)
                    // Optional: Update connection status with fresh measurement
                    _connectionStatus.postValue("📡 Fresh RSSI: ${newRssi} dBm")
                }
                android.util.Log.d("WifiScanner", "Locator RSSI update: ${target.ssid} = ${newRssi} dBm")
            } else {
                // Target network not found in latest scan
                withContext(Dispatchers.Main) {
                    _connectionStatus.postValue("⚠️ Target network not visible in scan")
                }
                android.util.Log.w("WifiScanner", "Target network ${target.ssid} not found in scan results")
            }
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                _connectionStatus.postValue("Permission error in locator scan")
            }
        }
    }

    fun getCurrentWifiNetwork(): WifiNetwork? {
        if (!hasRequiredPermissions(checkAccessWifiState = true, checkChangeWifiState = false)) return null
        try {
            val connectionInfo = wifiManager.connectionInfo
            return if (connectionInfo != null && connectionInfo.networkId != -1 && connectionInfo.bssid != null) {
                // For connected network, capabilities are not directly available from WifiInfo in the same way as ScanResult.
                // We might need to find the corresponding ScanResult or make an educated guess.
                // For simplicity, we'll use a generic "Connected" or try to get from configured networks if available.
                // Here, we'll use a placeholder for security and derive channel.
                val currentSsid = connectionInfo.ssid?.replace("^\"|\"$".toRegex(), "") ?: "<Unknown SSID>"
                val configuredNetwork = wifiManager.configuredNetworks?.find { it.SSID == "\"$currentSsid\"" }
                val security = configuredNetwork?.let {
                    when {
                        it.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK) -> "WPA/WPA2 PSK"
                        it.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_EAP) -> "WPA/WPA2 EAP"
                        it.allowedKeyManagement.get(android.net.wifi.WifiConfiguration.KeyMgmt.IEEE8021X) -> "802.1x EAP"
                        it.wepKeys[0] != null -> "WEP"
                        else -> "Open"
                    }
                } ?: "Connected" // Fallback if not found in configured networks

                WifiNetwork(
                    ssid = currentSsid,
                    bssid = connectionInfo.bssid,
                    rssi = connectionInfo.rssi,
                    channel = convertFrequencyToChannel(connectionInfo.frequency),
                    security = security,
                    latitude = currentLocationFlow.value?.latitude,
                    longitude = currentLocationFlow.value?.longitude,
                    timestamp = System.currentTimeMillis(),
                    password = null
                )
            } else {
                null
            }
        } catch (e: SecurityException) {
            _connectionStatus.postValue("Permission error getting current network.")
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectWithNetworkSpecifier(network: WifiNetwork, password: String?) {
        try {
            // Force clean connection by clearing any existing network callbacks first
            releaseCurrentNetworkCallback()
            
            // Clear any cached network configurations for this SSID to prevent auto-reconnection
            clearCachedNetworkConfigurations(network.ssid)
            
            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(network.ssid)
                // Force non-persistent behavior to avoid caching
                .setIsEnhancedOpen(false)

            if (!password.isNullOrEmpty()) {
                // Use the 'security' field from WifiNetwork model which is derived from capabilities
                when {
                    network.security.contains("WPA3", ignoreCase = true) -> {
                        specifierBuilder.setWpa3Passphrase(password)
                    }
                    network.security.contains("WPA2", ignoreCase = true) || 
                    network.security.contains("WPA", ignoreCase = true) -> {
                        specifierBuilder.setWpa2Passphrase(password)
                    }
                    network.security.contains("WEP", ignoreCase = true) -> {
                        _connectionStatus.postValue("⚠️ WEP networks are not supported on Android 10+")
                        return
                    }
                    network.security.contains("Open", ignoreCase = true) -> {
                        // No password needed for open networks
                    }
                    else -> {
                        // Default to WPA2 for unknown security types
                        specifierBuilder.setWpa2Passphrase(password)
                    }
                }
            }

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            // Track connection attempt state
            var connectionAttemptActive = true
            var connectionSuccessful = false
            var connectionValidated = false
            var connectionCancelled = false
            
            currentNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(net: Network) {
                    super.onAvailable(net)
                    if (!connectionAttemptActive) return
                    
                    android.util.Log.d("WifiScanner", "onAvailable called for network connection")
                    
                    try {
                        // Verify this is actually the network we're trying to connect to
                        val currentWifiInfo = wifiManager.connectionInfo
                        val connectedSsid = currentWifiInfo?.ssid?.replace("\"", "")
                        
                        android.util.Log.d("WifiScanner", "Connected to SSID: '$connectedSsid', expected: '${network.ssid}'")
                        
                        if (connectedSsid != network.ssid) {
                            android.util.Log.w("WifiScanner", "Wrong network connected: $connectedSsid instead of ${network.ssid}")
                            _connectionStatus.postValue("⚠️ Connected to wrong network: $connectedSsid instead of ${network.ssid}")
                            releaseCurrentNetworkCallback()
                            return
                        }
                        
                        // Connection successful - password is correct
                        connectionSuccessful = true
                        connectionValidated = true
                        connectionAttemptActive = false // Stop the attempt, we succeeded
                        android.util.Log.d("WifiScanner", "Connection successful! Password is correct for ${network.ssid}")
                        
                        // Save the working password to SharedPreferences immediately
                        password?.let { validPassword ->
                            saveWorkingPassword(network, validPassword)
                        }
                        
                        _connectionStatus.postValue("✅ Password validated for ${network.ssid}")
                        
                        // Immediately disconnect and complete verification
                        Handler(Looper.getMainLooper()).postDelayed({
                            android.util.Log.d("WifiScanner", "Disconnecting after successful validation")
                            connectivityManager.bindProcessToNetwork(null)
                            releaseCurrentNetworkCallback()
                        }, 500) // Quick disconnect
                        
                    } catch (e: Exception) {
                        android.util.Log.e("WifiScanner", "Exception in onAvailable: ${e.message}")
                        _connectionStatus.postValue("⚠️ Failed to validate connection: ${e.message}")
                        releaseCurrentNetworkCallback()
                    }
                }

                override fun onLost(net: Network) {
                    super.onLost(net)
                    android.util.Log.d("WifiScanner", "onLost called, connectionValidated: $connectionValidated")
                    if (connectionValidated) {
                        android.util.Log.d("WifiScanner", "Sending final success message")
                        _connectionStatus.postValue("✅ Password validated and saved for ${network.ssid}")
                    } else {
                        android.util.Log.w("WifiScanner", "Connection lost without validation")
                        _connectionStatus.postValue("⚠️ Lost connection to ${network.ssid}")
                    }
                    connectivityManager.bindProcessToNetwork(null)
                    releaseCurrentNetworkCallback()
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    if (!connectionAttemptActive) return
                    
                    android.util.Log.d("WifiScanner", "onUnavailable called - connection failed")
                    connectionAttemptActive = false
                    _connectionStatus.postValue("⚠️ Could not connect to ${network.ssid} - incorrect password or network unavailable")
                    releaseCurrentNetworkCallback()
                }
                
                override fun onCapabilitiesChanged(net: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(net, networkCapabilities)
                    if (!connectionAttemptActive) return
                    
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && 
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        // Additional validation that we have internet connectivity
                        _connectionStatus.postValue("✅ WiFi connection validated with internet access")
                    }
                }
            }
            
            // Set timeout for connection attempt
            Handler(Looper.getMainLooper()).postDelayed({
                if (connectionAttemptActive && !connectionSuccessful) {
                    connectionAttemptActive = false
                    _connectionStatus.postValue("⚠️ Connection timeout - no response from ${network.ssid}")
                    releaseCurrentNetworkCallback()
                }
            }, 10000) // 10 second timeout
            
            connectivityManager.requestNetwork(networkRequest, currentNetworkCallback!!)
            _connectionStatus.postValue("🔄 Attempting fresh connection to ${network.ssid}...")
            android.util.Log.d("WifiScanner", "Connection request sent for ${network.ssid} with password: ${password?.take(3)}***")
            
        } catch (e: SecurityException) {
            _connectionStatus.postValue("⚠️ Permission error: ${e.message}")
        } catch (e: Exception) {
            _connectionStatus.postValue("⚠️ Connection error: ${e.message}")
        }
    }

    private fun connectWithWifiConfiguration(network: WifiNetwork, password: String?) {
        _connectionStatus.postValue("Connection method for older Android versions is not yet implemented.")
    }

    fun connectToNetwork(network: WifiNetwork, password: String? = null) {
        if (!hasRequiredPermissions(checkAccessWifiState = true, checkChangeWifiState = true)) {
            _connectionStatus.postValue("Connection requires WiFi control permissions.")
            return
        }
        _connectionStatus.postValue(null) 

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWithNetworkSpecifier(network, password)
        } else {
            connectWithWifiConfiguration(network, password)
        }
    }

    fun disconnectFromNetwork() {
        if (!hasRequiredPermissions(checkAccessWifiState = true, checkChangeWifiState = true)) {
            _connectionStatus.postValue("Disconnection requires WiFi control permissions.")
            return
        }
        _connectionStatus.postValue(null) 

        releaseCurrentNetworkCallback()
        connectivityManager.bindProcessToNetwork(null) 

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (wifiManager.isWifiEnabled) {
            }
        }
        _connectionStatus.postValue("Disconnected.") 
    }

    fun cancelConnectionAttempt() {
        android.util.Log.d("WifiScanner", "Cancelling connection attempt")
        
        // Cancel any ongoing connection job
        connectionJob?.cancel()
        connectionJob = null
        
        // Release network callback to stop connection attempt
        releaseCurrentNetworkCallback()
        
        // Clear any process network binding
        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (e: Exception) {
            android.util.Log.w("WifiScanner", "Error unbinding network: ${e.message}")
        }
        
        // Update status
        _connectionStatus.postValue("Connection cancelled by user")
        android.util.Log.d("WifiScanner", "Connection attempt cancelled")
    }

    private fun releaseCurrentNetworkCallback(){
        currentNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: IllegalArgumentException) {
            }
            currentNetworkCallback = null
        }
    }

    private fun hasRequiredPermissions(checkAccessWifiState: Boolean = true, checkChangeWifiState: Boolean = true): Boolean {
        // Use the centralized permission utilities
        return PermissionUtils.hasAllWifiPermissions(context) && 
               (!checkAccessWifiState || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) &&
               (!checkChangeWifiState || ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED)
    }
    
    /**
     * Get detailed list of missing permissions for better error reporting
     */
    fun getMissingPermissions(): List<String> {
        return PermissionUtils.getMissingWifiPermissions(context)
    }
    
    /**
     * Check if location services are enabled (required for Android 10+)
     */
    fun isLocationEnabled(): Boolean {
        return PermissionUtils.isLocationEnabled(context)
    }
    
    /**
     * Save working password to SharedPreferences for future reference
     */
    private fun saveWorkingPassword(network: WifiNetwork, password: String) {
        try {
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            
            workingPasswordsPrefs.edit()
                .putString(network.bssid, password)
                .putString("${network.bssid}_ssid", network.ssid) 
                .putLong("${network.bssid}_timestamp", System.currentTimeMillis())
                .apply()
                
            android.util.Log.d("WifiScanner", "Saved working password for ${network.ssid} (${network.bssid})")
        } catch (e: Exception) {
            android.util.Log.e("WifiScanner", "Failed to save working password: ${e.message}")
        }
    }
    
    /**
     * Get saved working password for a network
     */
    fun getWorkingPassword(network: WifiNetwork): String? {
        return try {
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            workingPasswordsPrefs.getString(network.bssid, null)
        } catch (e: Exception) {
            null
        }
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(wifiScanReceiver)
            releaseCurrentNetworkCallback()
            stopLocatorScanning() // Also stop locator scanning
            locatorHandler.removeCallbacks(locatorScanRunnable) // Clean up locator handler
            stopConnectionBasedRssiTracking() // Clean up connection-based tracking
            connectionRssiHandler.removeCallbacks(connectionRssiRunnable) // Clean up connection RSSI handler
        } catch (e: IllegalArgumentException) {
        }
    }

    /**
     * Clear cached network configurations for the specified SSID to prevent auto-reconnection
     * with previously saved credentials
     */
    private fun clearCachedNetworkConfigurations(ssid: String) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // For older Android versions, try to remove configured networks
                val configuredNetworks = wifiManager.configuredNetworks
                configuredNetworks?.forEach { config ->
                    if (config.SSID == "\"$ssid\"") {
                        wifiManager.removeNetwork(config.networkId)
                    }
                }
                wifiManager.saveConfiguration()
            }
            // For Android 10+, WifiNetworkSpecifier should handle this automatically
            // but we ensure no persistent behavior by using appropriate flags
        } catch (e: SecurityException) {
            // Ignore security exceptions - we may not have permission to modify configurations
        } catch (e: Exception) {
            // Ignore other exceptions - clearing cache is best effort
        }
    }

    /**
     * Validate that the connection was made with the current password attempt
     * and not from cached/remembered credentials
     */

    /**
     * Validate the connection and immediately disconnect to complete password verification
     */
    private fun validateAndDisconnect(network: Network, wifiNetwork: WifiNetwork) {
        try {
            // Verify we have internet connectivity through this network
            connectivityManager.bindProcessToNetwork(network)
            
            // Brief validation period
            Handler(Looper.getMainLooper()).postDelayed({
                // Disconnect from the network
                connectivityManager.bindProcessToNetwork(null)
                releaseCurrentNetworkCallback()
                
                _connectionStatus.postValue("Connection validated and disconnected")
            }, 500)
            
        } catch (e: Exception) {
            _connectionStatus.postValue("⚠️ Connection validation completed with warnings: ${e.message}")
            connectivityManager.bindProcessToNetwork(null)
            releaseCurrentNetworkCallback()
        }
    }
}
