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
import kotlinx.coroutines.flow.StateFlow

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

    // Persistent network map to maintain networks across scans
    private val persistentNetworkMap = mutableMapOf<String, WifiNetwork>()

    private val scanInterval = 10000L // 10 seconds, adjust as needed
    private val handler = Handler(Looper.getMainLooper())

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
                }
                // Don't post empty list on failure - keep existing results
                
                if (_isScanning.value == true) {
                    handler.postDelayed(scanRunnable, scanInterval)
                }
            }
        }
    }

    private val scanRunnable = Runnable {
        if (_isScanning.value == true) {
            startSingleScan()
        }
    }

    init {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)
    }

    fun startScanning() {
        if (_isScanning.value == false) {
            _isScanning.value = true
            handler.removeCallbacks(scanRunnable) 
            startSingleScan() 
        }
    }

    fun stopScanning() {
        if (_isScanning.value == true) {
            _isScanning.value = false
            handler.removeCallbacks(scanRunnable)
        }
    }

    private fun startSingleScan() {
        val hasPermissions = hasRequiredPermissions(checkChangeWifiState = false)
        
        if (!hasPermissions) {
            _isScanning.value = false 
            _connectionStatus.postValue("Scanning requires location permission.")
            return
        }
        _connectionStatus.postValue(null) // Clear previous status

        val wifiEnabled = wifiManager.isWifiEnabled
        
        if (wifiEnabled) {
            val scanInitiated = wifiManager.startScan()
            if (!scanInitiated) {
                if (_isScanning.value == true) { 
                    handler.postDelayed(scanRunnable, scanInterval)
                }
            }
        } else {
            _connectionStatus.postValue("WiFi is disabled.")
            if (_isScanning.value == true) { 
                handler.postDelayed(scanRunnable, scanInterval)
            }
        }
    }

    private fun processScanResults() {
        val hasPermissions = hasRequiredPermissions(checkChangeWifiState = false)
        
        if (!hasPermissions) {
            return
        }
        try {
            val results: List<ScanResult> = wifiManager.scanResults
            println("WIMAP_DEBUG: ===== STARTING SCAN PROCESSING =====")
            println("WIMAP_DEBUG: Total raw scan results: ${results.size}")
            
            // Log all raw scan results first
            results.forEachIndexed { index, scanResult ->
                if (scanResult.BSSID != null) {
                    println("WIMAP_DEBUG: RAW[$index] BSSID: ${scanResult.BSSID}, SSID: '${scanResult.SSID}', RSSI: ${scanResult.level}")
                }
            }
            
            // Process current scan results and deduplicate within this scan
            val currentScanMap = mutableMapOf<String, WifiNetwork>()
            
            println("WIMAP_DEBUG: ----- PROCESSING SCAN RESULTS -----")
            for ((index, scanResult) in results.withIndex()) {
                if (scanResult.BSSID == null) {
                    println("WIMAP_DEBUG: SKIP[$index] - NULL BSSID")
                    continue
                }
                
                val bssid = scanResult.BSSID
                val rawSsid = scanResult.SSID
                val ssid = rawSsid?.takeIf { it.isNotEmpty() && it.trim().isNotEmpty() } ?: ""
                
                println("WIMAP_DEBUG: PROCESS[$index] BSSID: $bssid")
                println("WIMAP_DEBUG: PROCESS[$index] Raw SSID: '$rawSsid'")
                println("WIMAP_DEBUG: PROCESS[$index] Cleaned SSID: '$ssid'")
                println("WIMAP_DEBUG: PROCESS[$index] RSSI: ${scanResult.level}")
                println("WIMAP_DEBUG: PROCESS[$index] Will be displayed as: '${if (ssid.isEmpty()) "<Hidden Network>" else ssid}'")
                
                // Create network object
                val network = WifiNetwork(
                    ssid = if (ssid.isEmpty()) "<Hidden Network>" else ssid,
                    bssid = bssid,
                    rssi = scanResult.level,
                    channel = convertFrequencyToChannel(scanResult.frequency),
                    security = getSecurityType(scanResult.capabilities ?: ""),
                    latitude = currentLocationFlow.value?.latitude,
                    longitude = currentLocationFlow.value?.longitude,
                    timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) scanResult.timestamp else System.currentTimeMillis(),
                    password = null
                )
                
                val existingInCurrentScan = currentScanMap[bssid]
                
                if (existingInCurrentScan == null) {
                    // First time seeing this BSSID in current scan
                    println("WIMAP_DEBUG: PROCESS[$index] NEW BSSID - Adding to currentScanMap")
                    currentScanMap[bssid] = network
                } else {
                    // BSSID already exists in current scan, decide which one to keep
                    println("WIMAP_DEBUG: PROCESS[$index] DUPLICATE BSSID DETECTED!")
                    println("WIMAP_DEBUG: PROCESS[$index] Existing in map: SSID='${existingInCurrentScan.ssid}', RSSI=${existingInCurrentScan.rssi}")
                    println("WIMAP_DEBUG: PROCESS[$index] New candidate: SSID='${network.ssid}', RSSI=${network.rssi}")
                    
                    val shouldReplace = when {
                        // Prefer non-hidden networks over hidden ones
                        existingInCurrentScan.ssid == "<Hidden Network>" && network.ssid != "<Hidden Network>" -> {
                            println("WIMAP_DEBUG: PROCESS[$index] DECISION: Replace hidden with named '${network.ssid}'")
                            true
                        }
                        // If new one is hidden but existing has name, keep existing
                        network.ssid == "<Hidden Network>" && existingInCurrentScan.ssid != "<Hidden Network>" -> {
                            println("WIMAP_DEBUG: PROCESS[$index] DECISION: Keep existing named '${existingInCurrentScan.ssid}', reject hidden")
                            false
                        }
                        // Both have names or both are hidden - prefer stronger signal
                        network.rssi > existingInCurrentScan.rssi -> {
                            println("WIMAP_DEBUG: PROCESS[$index] DECISION: Replace with stronger signal ${network.rssi} > ${existingInCurrentScan.rssi}")
                            true
                        }
                        // Keep existing if it's better or equal
                        else -> {
                            println("WIMAP_DEBUG: PROCESS[$index] DECISION: Keep existing (stronger or equal signal)")
                            false
                        }
                    }
                    
                    if (shouldReplace) {
                        println("WIMAP_DEBUG: PROCESS[$index] REPLACING in currentScanMap")
                        currentScanMap[bssid] = network
                    } else {
                        println("WIMAP_DEBUG: PROCESS[$index] KEEPING existing in currentScanMap")
                    }
                }
            }
            
            println("WIMAP_DEBUG: ----- CURRENT SCAN MAP AFTER DEDUPLICATION -----")
            currentScanMap.forEach { (bssid, network) ->
                println("WIMAP_DEBUG: MAP_ENTRY: BSSID=$bssid, SSID='${network.ssid}', RSSI=${network.rssi}")
            }
            
            // Now create final networks list - ONLY networks from current scan, but enhanced with persistent data
            println("WIMAP_DEBUG: ----- MERGING WITH PERSISTENT DATA -----")
            println("WIMAP_DEBUG: Persistent networks count: ${persistentNetworkMap.size}")
            
            val finalNetworks = mutableListOf<WifiNetwork>()
            
            for ((bssid, currentNetwork) in currentScanMap) {
                val existingPersistent = persistentNetworkMap[bssid]
                
                println("WIMAP_DEBUG: MERGE: Processing BSSID=$bssid, Current='${currentNetwork.ssid}'")
                
                val finalNetwork = if (existingPersistent == null) {
                    // New network not seen before
                    println("WIMAP_DEBUG: MERGE: New network - adding to persistent map")
                    persistentNetworkMap[bssid] = currentNetwork
                    currentNetwork
                } else {
                    // Network exists in persistent map, merge data intelligently
                    println("WIMAP_DEBUG: MERGE: Existing persistent='${existingPersistent.ssid}', Current='${currentNetwork.ssid}'")
                    
                    val mergedNetwork = when {
                        // Always prefer non-hidden networks over hidden ones
                        existingPersistent.ssid == "<Hidden Network>" && currentNetwork.ssid != "<Hidden Network>" -> {
                            println("WIMAP_DEBUG: MERGE: Using current named over historical hidden")
                            currentNetwork.copy(password = existingPersistent.password)
                        }
                        // If current is hidden but existing has name, keep existing name but update RSSI
                        currentNetwork.ssid == "<Hidden Network>" && existingPersistent.ssid != "<Hidden Network>" -> {
                            println("WIMAP_DEBUG: MERGE: Keeping historical name with current RSSI")
                            existingPersistent.copy(
                                rssi = currentNetwork.rssi,
                                latitude = currentNetwork.latitude ?: existingPersistent.latitude,
                                longitude = currentNetwork.longitude ?: existingPersistent.longitude,
                                timestamp = currentNetwork.timestamp
                            )
                        }
                        // For networks with same visibility, use current data but preserve GPS from stronger signal and password
                        else -> {
                            println("WIMAP_DEBUG: MERGE: Standard merge with GPS/password preservation")
                            if (existingPersistent.rssi > currentNetwork.rssi && 
                                existingPersistent.latitude != null && existingPersistent.longitude != null &&
                                (currentNetwork.latitude == null || currentNetwork.longitude == null)) {
                                currentNetwork.copy(
                                    latitude = existingPersistent.latitude,
                                    longitude = existingPersistent.longitude,
                                    password = existingPersistent.password
                                )
                            } else {
                                currentNetwork.copy(password = existingPersistent.password)
                            }
                        }
                    }
                    
                    println("WIMAP_DEBUG: MERGE: Final merged network='${mergedNetwork.ssid}', RSSI=${mergedNetwork.rssi}")
                    
                    // Update persistent map
                    persistentNetworkMap[bssid] = mergedNetwork
                    mergedNetwork
                }
                
                finalNetworks.add(finalNetwork)
                println("WIMAP_DEBUG: MERGE: Added to final list: BSSID=$bssid, SSID='${finalNetwork.ssid}'")
            }
            
            // Sort by signal strength
            val networks = finalNetworks.sortedByDescending { it.rssi }
            
            println("WIMAP_DEBUG: ----- FINAL RESULTS -----")
            println("WIMAP_DEBUG: Final network count (current scan only): ${networks.size}")
            println("WIMAP_DEBUG: Persistent map size (historical): ${persistentNetworkMap.size}")
            
            // Debug: Log final networks
            networks.forEachIndexed { index, network ->
                println("WIMAP_DEBUG: FINAL[$index] BSSID=${network.bssid}, SSID='${network.ssid}', RSSI=${network.rssi}")
            }
            
            println("WIMAP_DEBUG: ===== SCAN PROCESSING COMPLETE =====")
            
            _wifiNetworks.postValue(networks)
        } catch (e: SecurityException) {
            _isScanning.value = false 
            _connectionStatus.postValue("Permission error processing scan results.")
        } catch (e: Exception) {
            println("WIMAP_DEBUG: Exception in processScanResults: ${e.message}")
            _isScanning.value = false 
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
        val specifierBuilder = WifiNetworkSpecifier.Builder()
            .setSsid(network.ssid)

        if (!password.isNullOrEmpty()) {
            // Use the 'security' field from WifiNetwork model which is derived from capabilities
            when {
                network.security.contains("WPA3") -> specifierBuilder.setWpa3Passphrase(password)
                network.security.contains("WPA2") || network.security.contains("WPA") -> specifierBuilder.setWpa2Passphrase(password)
                network.security.contains("WEP") -> {
                    _connectionStatus.postValue("WEP connection not supported via NetworkSpecifier.")
                    return
                }
                // Add other security types if necessary, or assume open if none match
            }
        } // For open networks, no passphrase is set

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        releaseCurrentNetworkCallback() 
        currentNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(net: Network) {
                super.onAvailable(net)
                connectivityManager.bindProcessToNetwork(net)
                _connectionStatus.postValue("Connected to ${network.ssid}")
            }

            override fun onLost(net: Network) {
                super.onLost(net)
                _connectionStatus.postValue("Lost connection to ${network.ssid}")
                connectivityManager.bindProcessToNetwork(null) 
                releaseCurrentNetworkCallback() 
            }

            override fun onUnavailable() {
                super.onUnavailable()
                _connectionStatus.postValue("Could not connect to ${network.ssid}")
                releaseCurrentNetworkCallback() 
            }
        }
        connectivityManager.requestNetwork(networkRequest, currentNetworkCallback!!)
        _connectionStatus.postValue("Attempting to connect to ${network.ssid}...")
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
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED) {
            println("WIMAP_PERMISSION_DEBUG: Missing ACCESS_FINE_LOCATION permission")
            return false
        }

        if (checkAccessWifiState) {
            val wifiStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE)
            if (wifiStatePermission != PackageManager.PERMISSION_GRANTED) {
                println("WIMAP_PERMISSION_DEBUG: Missing ACCESS_WIFI_STATE permission")
                return false
            }
        }

        if (checkChangeWifiState) {
            val changeWifiStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_WIFI_STATE)
            if (changeWifiStatePermission != PackageManager.PERMISSION_GRANTED) {
                println("WIMAP_PERMISSION_DEBUG: Missing CHANGE_WIFI_STATE permission")
                return false
            }
            
            // Check additional network permissions for Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val changeNetworkStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CHANGE_NETWORK_STATE)
                if (changeNetworkStatePermission != PackageManager.PERMISSION_GRANTED) {
                    println("WIMAP_PERMISSION_DEBUG: Missing CHANGE_NETWORK_STATE permission (required for Android 10+)")
                    return false
                }
                
                val accessNetworkStatePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)
                if (accessNetworkStatePermission != PackageManager.PERMISSION_GRANTED) {
                    println("WIMAP_PERMISSION_DEBUG: Missing ACCESS_NETWORK_STATE permission (required for Android 10+)")
                    return false
                }
            }
        }
        
        println("WIMAP_PERMISSION_DEBUG: All required permissions granted")
        return true
    }

    fun clearNetworks() {
        persistentNetworkMap.clear()
        _wifiNetworks.postValue(emptyList())
        println("WIMAP_DEBUG: Cleared persistent network map")
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(wifiScanReceiver)
            releaseCurrentNetworkCallback() 
        } catch (e: IllegalArgumentException) {
        }
    }
}