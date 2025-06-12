package com.ner.wimap.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.wifi.WifiScanner
import com.ner.wimap.FirebaseRepository
import com.ner.wimap.Result
import com.ner.wimap.data.database.PinnedNetworkDao

class ConnectionManager(
    private val wifiScanner: WifiScanner,
    private val firebaseRepository: FirebaseRepository,
    private val pinnedNetworkDao: PinnedNetworkDao,
    private val viewModelScope: CoroutineScope
) {
    // Connection states - make connecting network-specific
    private val _connectingNetworks = MutableStateFlow<Set<String>>(emptySet()) // Track BSSIDs of connecting networks
    val connectingNetworks: StateFlow<Set<String>> = _connectingNetworks

    private val _isConnecting = MutableStateFlow(false) // Keep for backward compatibility
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _connectionProgress = MutableStateFlow<String?>(null)
    val connectionProgress: StateFlow<String?> = _connectionProgress

    private val _connectionStatus = MutableStateFlow<String?>(null)
    val connectionStatus: StateFlow<String?> = _connectionStatus

    private val _successfulPasswords = MutableStateFlow<Map<String, String>>(emptyMap())
    val successfulPasswords: StateFlow<Map<String, String>> = _successfulPasswords

    // Settings
    private val _maxRetries = MutableStateFlow(3)
    val maxRetries: StateFlow<Int> = _maxRetries

    private val _connectionTimeoutSeconds = MutableStateFlow(10)
    val connectionTimeoutSeconds: StateFlow<Int> = _connectionTimeoutSeconds

    private val _rssiThresholdForConnection = MutableStateFlow(-80)
    val rssiThresholdForConnection: StateFlow<Int> = _rssiThresholdForConnection

    private val _passwords = MutableStateFlow<List<String>>(emptyList())
    val passwords: StateFlow<List<String>> = _passwords

    private val isTestMode = true

    fun initializeTestPasswords() {
        if (isTestMode || isEmulator()) {
            _passwords.value = listOf(
                "homepass123", "office2024", "myhotspot", "testpass123",
                "supersecure2024", "demo12345", "password123", "12345678",
                "test1234", "demo"
            )
        }
    }

    fun connectToNetwork(
        network: WifiNetwork,
        onShowPasswordDialog: (WifiNetwork) -> Unit
    ) {
        if (_connectingNetworks.value.contains(network.bssid)) return // Already connecting to this network

        viewModelScope.launch {
            // Add this network to connecting set
            _connectingNetworks.value = _connectingNetworks.value + network.bssid
            _isConnecting.value = true // Keep for backward compatibility
            _connectionProgress.value = "Checking network signal strength..."

            try {
                val rssiThreshold = _rssiThresholdForConnection.value
                if (network.rssi < rssiThreshold) {
                    _connectionStatus.value = "❌ Signal too weak (${network.rssi}dBm < ${rssiThreshold}dBm)"
                    _connectionProgress.value = "Connection aborted - weak signal"
                    delay(2000)
                    return@launch
                }

                if (network.security.contains("Open", ignoreCase = true)) {
                    _connectionStatus.value = "ℹ️ Open network detected: ${network.ssid}"
                    _connectionProgress.value = "Open networks don't require connection"
                    delay(2000)
                    return@launch
                }

                val storedPasswords = _passwords.value
                if (storedPasswords.isEmpty()) {
                    _connectionProgress.value = "No stored passwords found"
                    delay(1000)
                    onShowPasswordDialog(network)
                    return@launch
                }

                var connected = false
                val maxRetries = _maxRetries.value
                val timeoutSeconds = _connectionTimeoutSeconds.value

                for ((index, password) in storedPasswords.withIndex()) {
                    if (connected) break

                    _connectionProgress.value = "Trying password ${index + 1}/${storedPasswords.size} for ${network.ssid}..."

                    for (retry in 1..maxRetries) {
                        _connectionProgress.value = "Password ${index + 1}/${storedPasswords.size}, attempt $retry/$maxRetries (timeout: ${timeoutSeconds}s)"

                        val success = attemptConnectionWithRetry(network, password, timeoutSeconds)

                        if (success) {
                            _connectionStatus.value = "✅ Connected to ${network.ssid} with password: '$password'"
                            _connectionProgress.value = "Connection successful! Saving password..."

                            // Store the successful password
                            _successfulPasswords.value = _successfulPasswords.value.toMutableMap().apply {
                                put(network.bssid, password)
                            }

                            // Add to stored passwords if not already there
                            if (!_passwords.value.contains(password)) {
                                _passwords.value = _passwords.value + password
                            }

                            updatePinnedNetworkPassword(network, password)
                            updateFirebaseWithPassword(network, password)

                            connected = true
                            delay(1000)
                            break
                        } else {
                            if (retry < maxRetries) {
                                _connectionProgress.value = "❌ Wrong password '$password', retrying in 2s..."
                                delay(2000)
                            } else {
                                _connectionProgress.value = "❌ Password '$password' failed after $maxRetries attempts"
                                delay(1000)
                            }
                        }
                    }
                }

                if (!connected) {
                    _connectionStatus.value = "❌ All stored passwords failed for ${network.ssid}"
                    _connectionProgress.value = "All passwords failed - manual entry required"
                    delay(2000)
                    onShowPasswordDialog(network)
                }
            } catch (e: Exception) {
                _connectionStatus.value = "Connection error: ${e.message}"
                _connectionProgress.value = "Connection failed: ${e.message}"
            } finally {
                // Remove this network from connecting set
                _connectingNetworks.value = _connectingNetworks.value - network.bssid
                _isConnecting.value = _connectingNetworks.value.isNotEmpty() // Update global state
                delay(3000)
                _connectionProgress.value = null
            }
        }
    }

    fun connectWithManualPassword(network: WifiNetwork, password: String) {
        viewModelScope.launch {
            // Add this network to connecting set
            _connectingNetworks.value = _connectingNetworks.value + network.bssid
            _isConnecting.value = true
            _connectionProgress.value = "Checking signal strength..."

            val rssiThreshold = _rssiThresholdForConnection.value
            if (network.rssi < rssiThreshold) {
                _connectionStatus.value = "❌ Signal too weak (${network.rssi}dBm < ${rssiThreshold}dBm)"
                _connectionProgress.value = "Connection aborted - weak signal"
                // Remove from connecting set
                _connectingNetworks.value = _connectingNetworks.value - network.bssid
                _isConnecting.value = _connectingNetworks.value.isNotEmpty()
                delay(2000)
                _connectionProgress.value = null
                return@launch
            }

            val maxRetries = _maxRetries.value
            val timeoutSeconds = _connectionTimeoutSeconds.value
            var connected = false

            for (retry in 1..maxRetries) {
                _connectionProgress.value = "Trying manual password, attempt $retry/$maxRetries (timeout: ${timeoutSeconds}s)"

                val success = attemptConnectionWithRetry(network, password, timeoutSeconds)

                if (success) {
                    _connectionStatus.value = "✅ Connected to ${network.ssid} with manual password!"
                    _connectionProgress.value = "Manual connection successful! Saving password..."

                    _successfulPasswords.value = _successfulPasswords.value.toMutableMap().apply {
                        put(network.bssid, password)
                    }

                    if (!_passwords.value.contains(password)) {
                        _passwords.value = _passwords.value + password
                    }

                    updatePinnedNetworkPassword(network, password)
                    updateFirebaseWithPassword(network, password)

                    connected = true
                    delay(1000)
                    break
                } else {
                    if (retry < maxRetries) {
                        _connectionProgress.value = "❌ Attempt $retry failed, retrying in 2s..."
                        delay(2000)
                    } else {
                        _connectionStatus.value = "❌ Manual password failed for ${network.ssid} after $maxRetries attempts"
                        _connectionProgress.value = "Manual password incorrect after all retries"
                    }
                }
            }

            // Remove from connecting set
            _connectingNetworks.value = _connectingNetworks.value - network.bssid
            _isConnecting.value = _connectingNetworks.value.isNotEmpty()
            delay(2000)
            _connectionProgress.value = null
        }
    }

    private suspend fun attemptConnectionWithRetry(network: WifiNetwork, password: String, timeoutSeconds: Int): Boolean {
        return try {
            if (isTestMode || isEmulator()) {
                val connectionDelay = (timeoutSeconds * 1000 * 0.7).toLong()
                delay(connectionDelay)

                // Find the actual mock network to get the real password
                val mockNetwork = generateMockNetworks().find { it.bssid == network.bssid }

                if (mockNetwork != null) {
                    // Network exists in our mock data
                    if (mockNetwork.password != null) {
                        // Secured network - check password EXACTLY
                        println("DEBUG: Trying password '$password' for ${network.ssid} (correct: '${mockNetwork.password}')")
                        if (password == mockNetwork.password) {
                            println("DEBUG: ✅ Exact password match!")
                            return true // Exact password match
                        }

                        // Check some universal demo passwords for testing convenience
                        val universalDemoPasswords = listOf("test", "demo", "password", "admin")
                        if (universalDemoPasswords.contains(password.lowercase())) {
                            println("DEBUG: ✅ Universal demo password accepted")
                            return true // Universal demo password
                        }

                        // Wrong password - be strict
                        println("DEBUG: ❌ Wrong password: '$password' != '${mockNetwork.password}'")
                        return false
                    } else {
                        // Open network - should always succeed
                        return true
                    }
                } else {
                    // Network not in mock data - simulate realistic failure rate
                    // Only succeed with very common passwords
                    val commonPasswords = listOf("password", "12345678", "admin", "test")
                    return commonPasswords.contains(password.lowercase()) &&
                            password.length >= 8 // Minimum security requirement
                }
            } else {
                // Real device implementation
                wifiScanner.connectToNetwork(network, password)
                delay(timeoutSeconds * 1000L)
                // TODO: Actually check connection status from WifiScanner
                false // For now, return false to force manual implementation
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun updatePinnedNetworkPassword(network: WifiNetwork, password: String) {
        try {
            val existingPinned = pinnedNetworkDao.getPinnedNetworkByBssid(network.bssid)
            existingPinned?.let { pinned ->
                val updatedNetwork = pinned.copy(savedPassword = password)
                pinnedNetworkDao.updatePinnedNetwork(updatedNetwork)
                _connectionProgress.value = "✅ Password saved to pinned network"
                delay(500)
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to update pinned network password: ${e.message}")
        }
    }

    private suspend fun updateFirebaseWithPassword(network: WifiNetwork, password: String) {
        try {
            val networkWithPassword = network.copy(password = password)
            when (val result = firebaseRepository.uploadWifiNetworks(listOf(networkWithPassword))) {
                is Result.Success -> {
                    _connectionProgress.value = "✅ Password updated in cloud database"
                    delay(500)
                }
                is Result.Failure -> {
                    println("DEBUG: Failed to update Firebase with password: ${result.exception.message}")
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Error updating Firebase with password: ${e.message}")
        }
    }

    fun clearConnectionProgress() {
        _connectionProgress.value = null
    }

    fun setMaxRetries(retries: Int) {
        _maxRetries.value = retries.coerceIn(1, 10)
    }

    fun setConnectionTimeoutSeconds(timeout: Int) {
        _connectionTimeoutSeconds.value = timeout.coerceIn(5, 60)
    }

    fun setRssiThresholdForConnection(threshold: Int) {
        _rssiThresholdForConnection.value = threshold.coerceIn(-100, -30)
    }

    fun addPassword(password: String) {
        if (password.isNotBlank() && !_passwords.value.contains(password)) {
            _passwords.value = _passwords.value + password
        }
    }

    fun removePassword(password: String) {
        _passwords.value = _passwords.value.filter { it != password }
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
            WifiNetwork("iPhone Hotspot", "44:55:66:77:88:99", -35, 6, "WPA2", null, null, System.currentTimeMillis(), "myhotspot"),
            WifiNetwork("TestNetwork", "55:66:77:88:99:aa", -55, 36, "WPA2", 31.7767, 35.2345, System.currentTimeMillis(), "testpass123"),
            WifiNetwork("SecureNet_5G", "66:77:88:99:aa:bb", -65, 149, "WPA3", 31.7767, 35.2345, System.currentTimeMillis(), "supersecure2024"),
            WifiNetwork("DemoWiFi", "77:88:99:aa:bb:cc", -50, 40, "WPA2", null, null, System.currentTimeMillis(), "demo12345")
        )
    }
}