package com.ner.wimap.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import com.ner.wimap.LocationProvider
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.wifi.WifiScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class ScanManager(
    private val application: Application,
    private val wifiScanner: WifiScanner,
    private val locationProvider: LocationProvider,
    private val viewModelScope: CoroutineScope
) {
    private val isTestMode = false

    // StateFlows
    private val _wifiNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiNetworks: StateFlow<List<WifiNetwork>> = _wifiNetworks

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Network storage for deduplication and RSSI updates
    private val networkMap = mutableMapOf<String, WifiNetwork>()

    // Observer for WifiScanner's LiveData
    private val wifiNetworksObserver = Observer<List<WifiNetwork>> { networks ->
        updateNetworksWithDeduplication(networks)
    }

    // Observer for WifiScanner's scanning state
    private val scanningStateObserver = Observer<Boolean> { isScanning ->
        _isScanning.value = isScanning
    }

    fun initialize() {
        wifiScanner.isScanning.observeForever { isScanning ->
            _isScanning.value = isScanning
        }
    }

    fun startScan(onPermissionError: (String) -> Unit) {
        if (_isScanning.value) return

        viewModelScope.launch {
            try {
                if (isTestMode || isEmulator()) {
                    _isScanning.value = true
                    delay(2000)
                    val mockNetworks = generateMockNetworks()
                    updateNetworksWithDeduplication(mockNetworks)
                    _isScanning.value = false
                    println("DEBUG: Emulator detected - using mock WiFi networks with passwords")
                } else {
                    // Check permissions before starting
                    if (!hasLocationPermission()) {
                        onPermissionError("Location permission is required to scan WiFi networks. Please grant it in app settings.")
                        return@launch
                    }
                    
                    _isScanning.value = true
                    locationProvider.startLocationUpdates()
                    
                    // Set up observers
                    wifiScanner.wifiNetworks.observeForever(wifiNetworksObserver)
                    wifiScanner.isScanning.observeForever(scanningStateObserver)
                    
                    // Start scanning
                    wifiScanner.startScanning()
                }
            } catch (e: SecurityException) {
                onPermissionError("Location and WiFi permissions are required to scan networks. Please grant them in app settings.")
                _errorMessage.value = "Permission denied: ${e.message}"
                _isScanning.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Scan error: ${e.message}"
                _isScanning.value = false
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return try {
            val context = application.applicationContext
            val permission = android.Manifest.permission.ACCESS_FINE_LOCATION
            androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun stopScan() {
        _isScanning.value = false
        locationProvider.stopLocationUpdates()
        wifiScanner.wifiNetworks.removeObserver(wifiNetworksObserver)
        wifiScanner.isScanning.removeObserver(scanningStateObserver)
        wifiScanner.stopScanning()
    }

    fun toggleScan(onPermissionError: (String) -> Unit) {
        if (_isScanning.value) {
            stopScan()
        } else {
            startScan(onPermissionError)
        }
    }

    fun clearNetworks() {
        networkMap.clear()
        _wifiNetworks.value = emptyList()
    }

    /**
     * Updates networks with deduplication logic:
     * - For new networks: add them to the map
     * - For existing networks: update RSSI but keep coordinates where RSSI was strongest
     */
    private fun updateNetworksWithDeduplication(newNetworks: List<WifiNetwork>) {
        newNetworks.forEach { newNetwork ->
            val key = "${newNetwork.bssid}_${newNetwork.ssid}"
            val existingNetwork = networkMap[key]
            
            if (existingNetwork == null) {
                // New network - add it (always online when first detected)
                android.util.Log.d("ScanManager", "New network detected: ${newNetwork.ssid} at ${System.currentTimeMillis()}")
                networkMap[key] = newNetwork.copy(isOffline = false)
            } else {
                // Existing network - update with logic
                val updatedNetwork = if (newNetwork.rssi > existingNetwork.rssi) {
                    // New RSSI is stronger - update everything including coordinates
                    // If this was an offline network, restore it to online
                    if (existingNetwork.isOffline) {
                        android.util.Log.d("ScanManager", "Network ${newNetwork.ssid} is back online - restoring to main list")
                    }
                    newNetwork.copy(
                        comment = existingNetwork.comment, // Preserve user data
                        photoPath = existingNetwork.photoPath,
                        isPinned = existingNetwork.isPinned,
                        isOffline = false // Always online when detected in scan
                    )
                } else {
                    // Keep existing coordinates (where RSSI was strongest) but update other data
                    // If this was an offline network, restore it to online
                    if (existingNetwork.isOffline) {
                        android.util.Log.d("ScanManager", "Network ${newNetwork.ssid} is back online - restoring to main list")
                    }
                    android.util.Log.d("ScanManager", "Updating existing network: ${newNetwork.ssid}, lastSeen: ${newNetwork.lastSeenTimestamp}")
                    newNetwork.copy(
                        latitude = existingNetwork.latitude,
                        longitude = existingNetwork.longitude,
                        comment = existingNetwork.comment, // Preserve user data
                        photoPath = existingNetwork.photoPath,
                        isPinned = existingNetwork.isPinned,
                        isOffline = false // Always online when detected in scan
                    )
                }
                networkMap[key] = updatedNetwork
            }
        }
        
        // Update the StateFlow with deduplicated networks sorted by RSSI
        val deduplicatedNetworks = networkMap.values.toList().sortedByDescending { it.rssi }
        _wifiNetworks.value = deduplicatedNetworks
    }

    /**
     * Mark networks as offline that haven't been seen for the specified duration
     * instead of removing them completely
     */
    fun removeStaleNetworks(hideNetworksUnseenForSeconds: Int) {
        val currentTime = System.currentTimeMillis()
        val thresholdTime = currentTime - (hideNetworksUnseenForSeconds * 1000L) // Convert seconds to milliseconds
        
        android.util.Log.d("ScanManager", "Checking for stale networks. Threshold: ${hideNetworksUnseenForSeconds}s, Current time: $currentTime, Threshold time: $thresholdTime")
        
        var hasChanges = false
        
        // Mark stale networks as offline instead of removing them
        networkMap.forEach { (key, network) ->
            val timeSinceLastSeen = (currentTime - network.lastSeenTimestamp) / 1000
            android.util.Log.d("ScanManager", "Network ${network.ssid}: last seen ${timeSinceLastSeen}s ago, isOffline: ${network.isOffline}")
            
            if (network.lastSeenTimestamp < thresholdTime && !network.isOffline) {
                // Mark as offline
                val offlineNetwork = network.copy(isOffline = true)
                networkMap[key] = offlineNetwork
                hasChanges = true
                android.util.Log.d("ScanManager", "✅ Marked network ${network.ssid} as offline (last seen ${timeSinceLastSeen} seconds ago)")
            }
        }
        
        android.util.Log.d("ScanManager", "Stale network check complete. Changes made: $hasChanges")
        
        // Update the StateFlow if any networks were marked as offline
        if (hasChanges) {
            val updatedNetworks = networkMap.values.toList().sortedByDescending { it.rssi }
            _wifiNetworks.value = updatedNetworks
            android.util.Log.d("ScanManager", "Updated network list with ${updatedNetworks.count { it.isOffline }} offline networks")
        }
    }

    fun exportToCsv(context: Context): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val filename = "wifi_scan_$timestamp.csv"
        val file = File(context.getExternalFilesDir(null), filename)

        try {
            val writer = FileWriter(file)
            val csvWriter = PrintWriter(writer)

            csvWriter.println("SSID,BSSID,RSSI,Channel,Security,Latitude,Longitude,Password")

            _wifiNetworks.value.forEach { network ->
                csvWriter.println(
                    "${network.ssid}," +
                            "${network.bssid}," +
                            "${network.rssi}," +
                            "${network.channel}," +
                            "${network.security}," +
                            "${network.latitude}," +
                            "${network.longitude}," +
                            "" // Empty password field for security
                )
            }

            csvWriter.close()
            return file.absolutePath
        } catch (e: Exception) {
            _errorMessage.value = "Export error: ${e.message}"
            return ""
        }
    }

    fun shareCsv(context: Context) {
        val filePath = exportToCsv(context)
        if (filePath.isEmpty()) {
            _errorMessage.value = "Nothing to share - no networks scanned"
            return
        }

        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "text/csv"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share WiFi Networks CSV"))
    }

    fun cleanup() {
        wifiScanner.wifiNetworks.removeObserver(wifiNetworksObserver)
        wifiScanner.isScanning.removeObserver { }
        wifiScanner.stopScanning()
        locationProvider.stopLocationUpdates()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic")
    }

    private fun generateMockNetworks(): List<WifiNetwork> {
        return listOf(
            WifiNetwork("Home WiFi Pro", "aa:bb:cc:dd:ee:ff", -45, 6, "WPA3", 31.7767, 35.2345, System.currentTimeMillis(), "homepass123"),
            WifiNetwork("Office_5G", "11:22:33:44:55:66", -60, 11, "WPA2", null, null, System.currentTimeMillis(), "office2024"),
            WifiNetwork("Guest Network", "22:33:44:55:66:77", -75, 1, "Open", null, null, System.currentTimeMillis(), null),
            WifiNetwork("Starbucks WiFi", "33:44:55:66:77:88", -80, 144, "Open", 31.7767, 35.2345, System.currentTimeMillis(), null),
            WifiNetwork("iPhone Hotspot", "44:55:66:77:88:99", -35, 6, "WPA2", null, null, System.currentTimeMillis(), "myhotspot"),
            WifiNetwork("TestNetwork", "55:66:77:88:99:aa", -55, 36, "WPA2", 31.7767, 35.2345, System.currentTimeMillis(), "testpass123"),
            WifiNetwork("SecureNet_5G", "66:77:88:99:aa:bb", -65, 149, "WPA3", 31.7767, 35.2345, System.currentTimeMillis(), "supersecure2024"),
            WifiNetwork("DemoWiFi", "77:88:99:aa:bb:cc", -50, 40, "WPA2", null, null, System.currentTimeMillis(), "demo12345")
        )
    }
    
    // WiFi Locator specific methods
    private val _isLocatorScanning = MutableStateFlow(false)
    val isLocatorScanning: StateFlow<Boolean> = _isLocatorScanning
    
    private val _locatorRSSI = MutableStateFlow(-100)
    val locatorRSSI: StateFlow<Int> = _locatorRSSI
    
    // Observer for locator scanning state
    private val locatorScanningStateObserver = Observer<Boolean> { isScanning ->
        _isLocatorScanning.value = isScanning
    }
    
    // Observer for locator RSSI updates
    private val locatorRSSIObserver = Observer<Int> { rssi ->
        _locatorRSSI.value = rssi
    }
    
    fun startLocatorScanning(targetNetwork: WifiNetwork) {
        // Set up observers for locator scanning
        wifiScanner.isLocatorScanning.observeForever(locatorScanningStateObserver)
        wifiScanner.locatorRSSI.observeForever(locatorRSSIObserver)
        
        // Start the locator scanning
        wifiScanner.startLocatorScanning(targetNetwork)
    }
    
    fun stopLocatorScanning() {
        // Stop scanning and remove observers
        wifiScanner.stopLocatorScanning()
        wifiScanner.isLocatorScanning.removeObserver(locatorScanningStateObserver)
        wifiScanner.locatorRSSI.removeObserver(locatorRSSIObserver)
        
        // Reset state
        _isLocatorScanning.value = false
        _locatorRSSI.value = -100
    }
}