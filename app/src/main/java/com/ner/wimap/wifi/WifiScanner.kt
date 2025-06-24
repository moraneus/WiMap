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
        if (!hasRequiredPermissions(checkChangeWifiState = false)) {
            _wifiNetworks.postValue(emptyList()) 
            _isScanning.value = false 
            _connectionStatus.postValue("Scanning requires location permission.")
            return
        }
        _connectionStatus.postValue(null) // Clear previous status

        if (wifiManager.isWifiEnabled) {
            val scanInitiated = wifiManager.startScan()
            if (!scanInitiated) {
                if (_isScanning.value == true) { 
                    handler.postDelayed(scanRunnable, scanInterval)
                }
            }
        } else {
            _wifiNetworks.postValue(emptyList()) 
            _connectionStatus.postValue("WiFi is disabled.")
            if (_isScanning.value == true) { 
                handler.postDelayed(scanRunnable, scanInterval)
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
            val networks = results.mapNotNull { scanResult ->
                if (scanResult.BSSID == null || scanResult.SSID == null) null
                else WifiNetwork(
                    ssid = scanResult.SSID.ifEmpty { "<Hidden Network>" },
                    bssid = scanResult.BSSID,
                    rssi = scanResult.level,
                    channel = convertFrequencyToChannel(scanResult.frequency),
                    security = getSecurityType(scanResult.capabilities ?: ""),
                    latitude = currentLocationFlow.value?.latitude,
                    longitude = currentLocationFlow.value?.longitude,
                    timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) scanResult.timestamp else System.currentTimeMillis(),
                    password = null
                )
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
            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(network.ssid)

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

            releaseCurrentNetworkCallback() 
            currentNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(net: Network) {
                    super.onAvailable(net)
                    try {
                        connectivityManager.bindProcessToNetwork(net)
                        _connectionStatus.postValue("✅ Connected to ${network.ssid}")
                    } catch (e: Exception) {
                        _connectionStatus.postValue("❌ Failed to bind to network: ${e.message}")
                        releaseCurrentNetworkCallback()
                    }
                }

                override fun onLost(net: Network) {
                    super.onLost(net)
                    _connectionStatus.postValue("⚠️ Lost connection to ${network.ssid}")
                    connectivityManager.bindProcessToNetwork(null) 
                    releaseCurrentNetworkCallback() 
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    _connectionStatus.postValue("❌ Could not connect to ${network.ssid} - check password and signal strength")
                    releaseCurrentNetworkCallback() 
                }
                
                override fun onCapabilitiesChanged(net: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(net, networkCapabilities)
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        _connectionStatus.postValue("✅ WiFi connection established to ${network.ssid}")
                    }
                }
            }
            
            connectivityManager.requestNetwork(networkRequest, currentNetworkCallback!!)
            _connectionStatus.postValue("🔄 Attempting to connect to ${network.ssid}...")
            
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
}
