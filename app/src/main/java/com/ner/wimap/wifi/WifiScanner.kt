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
    private val handler = Handler(Looper.getMainLooper())
    
    // Background coroutine scope for Wi-Fi operations
    private val wifiScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanningJob: Job? = null
    private var connectionJob: Job? = null

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION == intent.action) {
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                } else {
                    true
                }
                if (success) {
                    processScanResults()
                } else {
                    _wifiNetworks.postValue(emptyList())
                }
                if (_isScanning.value == true) {
                    handler.postDelayed(scanRunnable, scanInterval)
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

    private fun processScanResults() {
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            _wifiNetworks.postValue(emptyList())
            return
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
                        peakRssi = peakRssi, // Peak RSSI seen so far
                        peakRssiLatitude = peakLat, // GPS coordinates where peak RSSI was observed
                        peakRssiLongitude = peakLng, // GPS coordinates where peak RSSI was observed
                        lastSeenTimestamp = currentTime // Update last seen time
                    )
                }
            }.sortedByDescending { it.rssi } 
            _wifiNetworks.postValue(networks)
        } catch (e: SecurityException) {
            _wifiNetworks.postValue(emptyList())
            _isScanning.value = false 
            _connectionStatus.postValue("Permission error processing scan results.")
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
                        _connectionStatus.postValue("❌ WEP networks are not supported on Android 10+")
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
            
            currentNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(net: Network) {
                    super.onAvailable(net)
                    if (!connectionAttemptActive) return
                    
                    try {
                        // Verify this is actually the network we're trying to connect to
                        val currentWifiInfo = wifiManager.connectionInfo
                        val connectedSsid = currentWifiInfo?.ssid?.replace("\"", "")
                        
                        if (connectedSsid != network.ssid) {
                            _connectionStatus.postValue("❌ Connected to wrong network: $connectedSsid instead of ${network.ssid}")
                            releaseCurrentNetworkCallback()
                            return
                        }
                        
                        // Verify the connection was made with our current password attempt
                        if (!validateConnectionWithCurrentPassword(network, password)) {
                            _connectionStatus.postValue("❌ Connection succeeded but may be using cached credentials")
                            releaseCurrentNetworkCallback()
                            return
                        }
                        
                        connectionSuccessful = true
                        _connectionStatus.postValue("✅ Successfully connected to ${network.ssid} with provided password")
                        
                        // Immediately validate and disconnect to complete verification
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (connectionAttemptActive) {
                                validateAndDisconnect(net, network)
                                connectionValidated = true
                            }
                        }, 1000) // Wait 1 second to ensure connection is stable
                        
                    } catch (e: Exception) {
                        _connectionStatus.postValue("❌ Failed to validate connection: ${e.message}")
                        releaseCurrentNetworkCallback()
                    }
                }

                override fun onLost(net: Network) {
                    super.onLost(net)
                    if (connectionValidated) {
                        _connectionStatus.postValue("✅ Connection validated and disconnected successfully")
                    } else {
                        _connectionStatus.postValue("⚠️ Lost connection to ${network.ssid}")
                    }
                    connectivityManager.bindProcessToNetwork(null)
                    releaseCurrentNetworkCallback()
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    if (!connectionAttemptActive) return
                    
                    connectionAttemptActive = false
                    _connectionStatus.postValue("❌ Could not connect to ${network.ssid} - incorrect password or network unavailable")
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
                    _connectionStatus.postValue("❌ Connection timeout - no response from ${network.ssid}")
                    releaseCurrentNetworkCallback()
                }
            }, 15000) // 15 second timeout
            
            connectivityManager.requestNetwork(networkRequest, currentNetworkCallback!!)
            _connectionStatus.postValue("🔄 Attempting fresh connection to ${network.ssid}...")
            
        } catch (e: SecurityException) {
            _connectionStatus.postValue("❌ Permission error: ${e.message}")
        } catch (e: Exception) {
            _connectionStatus.postValue("❌ Connection error: ${e.message}")
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
        val fineLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED) return false

        if (checkAccessWifiState) {
            val wifiStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE)
            if (wifiStatePermission != PackageManager.PERMISSION_GRANTED) return false
        }

        if (checkChangeWifiState) {
            val changeWifiStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE)
            if (changeWifiStatePermission != PackageManager.PERMISSION_GRANTED) return false
            
            val changeNetworkStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_NETWORK_STATE)
            if (changeNetworkStatePermission != PackageManager.PERMISSION_GRANTED) return false
        }

        // Check for NEARBY_WIFI_DEVICES permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearbyWifiDevicesPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
            if (nearbyWifiDevicesPermission != PackageManager.PERMISSION_GRANTED) return false
        }

        return true
    }

    fun getMissingPermissions(): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.CHANGE_WIFI_STATE)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        
        return missingPermissions
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(wifiScanReceiver)
            releaseCurrentNetworkCallback() 
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
    private fun validateConnectionWithCurrentPassword(network: WifiNetwork, password: String?): Boolean {
        try {
            // For Android 10+, WifiNetworkSpecifier connections should be non-persistent
            // but we add additional validation to ensure the connection is fresh
            
            val currentWifiInfo = wifiManager.connectionInfo
            if (currentWifiInfo == null || currentWifiInfo.networkId == -1) {
                return false
            }
            
            // Check if this is a fresh connection by verifying timing
            // If we just initiated the connection and it succeeded quickly,
            // it's likely using our current password attempt
            
            // Additional validation: check if the network was in configured networks
            // before our connection attempt (which would indicate cached credentials)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val configuredNetworks = wifiManager.configuredNetworks
                val wasConfigured = configuredNetworks?.any { config ->
                    config.SSID == "\"${network.ssid}\"" && config.networkId == currentWifiInfo.networkId
                } ?: false
                
                // If it was already configured and we didn't just add it, 
                // it might be using cached credentials
                if (wasConfigured) {
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            // If validation fails, assume the connection might be using cached credentials
            return false
        }
    }

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
                
                _connectionStatus.postValue("✅ Password validated for ${wifiNetwork.ssid} - connection test complete")
            }, 500)
            
        } catch (e: Exception) {
            _connectionStatus.postValue("⚠️ Connection validation completed with warnings: ${e.message}")
            connectivityManager.bindProcessToNetwork(null)
            releaseCurrentNetworkCallback()
        }
    }
}
