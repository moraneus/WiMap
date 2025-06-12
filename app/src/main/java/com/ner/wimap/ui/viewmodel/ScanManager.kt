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
    private val isTestMode = true

    // StateFlows
    private val _wifiNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val wifiNetworks: StateFlow<List<WifiNetwork>> = _wifiNetworks

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Observer for WifiScanner's LiveData
    private val wifiNetworksObserver = Observer<List<WifiNetwork>> { networks ->
        _wifiNetworks.value = networks
    }

    fun initialize() {
        wifiScanner.isScanning.observeForever { isScanning ->
            _isScanning.value = isScanning
        }
    }

    fun startScan(onPermissionError: (String) -> Unit) {
        if (_isScanning.value) return

        _isScanning.value = true
        locationProvider.startLocationUpdates()

        viewModelScope.launch {
            try {
                if (isTestMode || isEmulator()) {
                    delay(2000)
                    val mockNetworks = generateMockNetworks()
                    _wifiNetworks.value = mockNetworks
                    _isScanning.value = false
                    println("DEBUG: Emulator detected - using mock WiFi networks with passwords")
                } else {
                    wifiScanner.wifiNetworks.observeForever(wifiNetworksObserver)
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

    fun stopScan() {
        _isScanning.value = false
        locationProvider.stopLocationUpdates()
        wifiScanner.wifiNetworks.removeObserver(wifiNetworksObserver)
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
        _wifiNetworks.value = emptyList()
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
}