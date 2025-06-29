package com.ner.wimap.ui.viewmodel

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.wifi.WifiScanner
import com.ner.wimap.FirebaseRepository
import com.ner.wimap.Result
import com.ner.wimap.data.database.PinnedNetworkDao
import com.ner.wimap.utils.PermissionUtils

class ConnectionManager(
    private val wifiScanner: WifiScanner,
    private val firebaseRepository: FirebaseRepository,
    private val pinnedNetworkDao: PinnedNetworkDao,
    private val viewModelScope: CoroutineScope,
    private val context: Context
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

    // Real-time connection progress data
    private val _currentPassword = MutableStateFlow<String?>(null)
    val currentPassword: StateFlow<String?> = _currentPassword

    private val _currentAttempt = MutableStateFlow(0)
    val currentAttempt: StateFlow<Int> = _currentAttempt

    private val _totalAttempts = MutableStateFlow(0)
    val totalAttempts: StateFlow<Int> = _totalAttempts

    private val _connectingNetworkName = MutableStateFlow<String?>(null)
    val connectingNetworkName: StateFlow<String?> = _connectingNetworkName

    // Settings
    private val _maxRetries = MutableStateFlow(3)
    val maxRetries: StateFlow<Int> = _maxRetries

    private val _connectionTimeoutSeconds = MutableStateFlow(10)
    val connectionTimeoutSeconds: StateFlow<Int> = _connectionTimeoutSeconds

    private val _rssiThresholdForConnection = MutableStateFlow(-80)
    val rssiThresholdForConnection: StateFlow<Int> = _rssiThresholdForConnection

    private val _passwords = MutableStateFlow<List<String>>(emptyList())
    val passwords: StateFlow<List<String>> = _passwords

    // Connection job tracking for cancellation
    private var currentConnectionJob: Job? = null

    private val isTestMode = false

    fun initializePasswordsFromSettings() {
        // Passwords are now loaded from SharedPreferences via Settings
        // No default passwords are provided
        _passwords.value = emptyList()
    }

    fun connectToNetwork(
        network: WifiNetwork,
        onShowPasswordDialog: (WifiNetwork) -> Unit
    ) {
        if (_connectingNetworks.value.contains(network.bssid)) return // Already connecting to this network

        // Cancel any existing connection job
        currentConnectionJob?.cancel()
        
        currentConnectionJob = viewModelScope.launch(Dispatchers.IO) {
            // Update UI state on main thread
            withContext(Dispatchers.Main) {
                _connectingNetworks.value = _connectingNetworks.value + network.bssid
                _isConnecting.value = true // Keep for backward compatibility
                _connectingNetworkName.value = network.ssid
                _currentPassword.value = null
                _currentAttempt.value = 0
                _totalAttempts.value = 0
                _connectionProgress.value = "Checking network signal strength..."
            }

            try {
                val rssiThreshold = _rssiThresholdForConnection.value
                if (network.rssi < rssiThreshold) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "❌ Signal too weak (${network.rssi}dBm < ${rssiThreshold}dBm)"
                        _connectionProgress.value = "Connection aborted - weak signal"
                    }
                    delay(2000)
                    return@launch
                }

                if (network.security.contains("Open", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "ℹ️ Open network detected: ${network.ssid}"
                        _connectionProgress.value = "Open networks don't require connection"
                    }
                    delay(2000)
                    return@launch
                }

                val storedPasswords = _passwords.value
                if (storedPasswords.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _connectionProgress.value = "No stored passwords found"
                    }
                    delay(1000)
                    withContext(Dispatchers.Main) {
                        onShowPasswordDialog(network)
                    }
                    return@launch
                }

                var connected = false
                val maxRetries = _maxRetries.value
                val timeoutSeconds = _connectionTimeoutSeconds.value
                
                // Calculate total attempts: each password gets exactly maxRetries attempts
                _totalAttempts.value = storedPasswords.size * maxRetries

                var attemptCount = 0
                for ((index, password) in storedPasswords.withIndex()) {
                    if (connected || !isActive) break // Check for cancellation
                    
                    // Additional check: if connection was cancelled, stop immediately
                    if (!_connectingNetworks.value.contains(network.bssid)) {
                        android.util.Log.d("ConnectionManager", "Connection cancelled, stopping password attempts")
                        break
                    }

                    withContext(Dispatchers.Main) {
                        _currentPassword.value = password
                        _connectionProgress.value = "Trying password ${index + 1}/${storedPasswords.size} for ${network.ssid}..."
                    }

                    // Each password gets exactly maxRetries attempts, no more, no less
                    for (retry in 1..maxRetries) {
                        if (!isActive) break // Check for cancellation
                        
                        // Check if connection was cancelled
                        if (!_connectingNetworks.value.contains(network.bssid)) {
                            android.util.Log.d("ConnectionManager", "Connection cancelled during retry attempts")
                            break
                        }
                        
                        attemptCount++
                        withContext(Dispatchers.Main) {
                            _currentAttempt.value = attemptCount
                            _connectionProgress.value = "Password ${index + 1}/${storedPasswords.size}, attempt $retry/$maxRetries (timeout: ${timeoutSeconds}s)"
                        }

                        val success = attemptConnectionWithRetry(network, password, timeoutSeconds)

                        // Check again if cancelled after connection attempt
                        if (!_connectingNetworks.value.contains(network.bssid)) {
                            android.util.Log.d("ConnectionManager", "Connection cancelled after attempt")
                            break
                        }

                        if (success) {
                            withContext(Dispatchers.Main) {
                                _connectionStatus.value = "✅ Connected to ${network.ssid} with password: '$password'"
                                _connectionProgress.value = "Connection successful! Saving password..."
                            }

                            // Store the successful password on main thread
                            withContext(Dispatchers.Main) {
                                _successfulPasswords.value = _successfulPasswords.value.toMutableMap().apply {
                                    put(network.bssid, password)
                                }

                                // Add to stored passwords if not already there
                                if (!_passwords.value.contains(password)) {
                                    _passwords.value = _passwords.value + password
                                }
                            }

                            // Database operations already on background thread
                            updatePinnedNetworkPassword(network, password)
                            updateFirebaseWithPassword(network, password)

                            connected = true
                            delay(1000)
                            break
                        } else {
                            // Only show retry message if we have more attempts left for this password
                            if (retry < maxRetries) {
                                withContext(Dispatchers.Main) {
                                    _connectionProgress.value = "❌ Wrong password '$password', retrying in 2s..."
                                }
                                delay(2000)
                                // Check if cancelled during delay
                                if (!isActive || !_connectingNetworks.value.contains(network.bssid)) {
                                    android.util.Log.d("ConnectionManager", "Connection cancelled during retry delay")
                                    return@launch
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    _connectionProgress.value = "❌ Password '$password' failed after $maxRetries attempts"
                                }
                                delay(1000)
                                // Check if cancelled during delay
                                if (!isActive || !_connectingNetworks.value.contains(network.bssid)) {
                                    android.util.Log.d("ConnectionManager", "Connection cancelled during failure delay")
                                    return@launch
                                }
                            }
                        }
                    }
                    
                    // If connected, break out of password loop
                    if (connected) break
                }

                if (!connected) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "❌ All stored passwords failed for ${network.ssid}"
                        _connectionProgress.value = "All passwords failed - manual entry required"
                    }
                    delay(2000)
                    // Check if cancelled during delay
                    if (!isActive || !_connectingNetworks.value.contains(network.bssid)) {
                        android.util.Log.d("ConnectionManager", "Connection cancelled during failure message delay")
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        onShowPasswordDialog(network)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _connectionStatus.value = "Connection error: ${e.message}"
                    _connectionProgress.value = "Connection failed: ${e.message}"
                }
            } finally {
                // Clear progress data and remove this network from connecting set on main thread
                withContext(Dispatchers.Main) {
                    _currentPassword.value = null
                    _currentAttempt.value = 0
                    _totalAttempts.value = 0
                    _connectingNetworkName.value = null
                    _connectingNetworks.value = _connectingNetworks.value - network.bssid
                    _isConnecting.value = _connectingNetworks.value.isNotEmpty() // Update global state
                }
                // Only delay if not cancelled
                try {
                    delay(3000)
                } catch (e: CancellationException) {
                    // If cancelled, don't delay - clear immediately
                }
                withContext(Dispatchers.Main) {
                    _connectionProgress.value = null
                }
            }
        }
    }

    fun connectWithManualPassword(network: WifiNetwork, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Update UI state on main thread
            withContext(Dispatchers.Main) {
                _connectingNetworks.value = _connectingNetworks.value + network.bssid
                _isConnecting.value = true
                _connectingNetworkName.value = network.ssid
                _currentPassword.value = password
                _currentAttempt.value = 0
                _connectionProgress.value = "Checking signal strength..."
            }

            val rssiThreshold = _rssiThresholdForConnection.value
            if (network.rssi < rssiThreshold) {
                withContext(Dispatchers.Main) {
                    _connectionStatus.value = "❌ Signal too weak (${network.rssi}dBm < ${rssiThreshold}dBm)"
                    _connectionProgress.value = "Connection aborted - weak signal"
                    // Clear progress data and remove from connecting set
                    _currentPassword.value = null
                    _currentAttempt.value = 0
                    _totalAttempts.value = 0
                    _connectingNetworkName.value = null
                    _connectingNetworks.value = _connectingNetworks.value - network.bssid
                    _isConnecting.value = _connectingNetworks.value.isNotEmpty()
                }
                delay(2000)
                withContext(Dispatchers.Main) {
                    _connectionProgress.value = null
                }
                return@launch
            }

            val maxRetries = _maxRetries.value
            val timeoutSeconds = _connectionTimeoutSeconds.value
            _totalAttempts.value = maxRetries
            var connected = false

            for (retry in 1..maxRetries) {
                withContext(Dispatchers.Main) {
                    _currentAttempt.value = retry
                    _connectionProgress.value = "Trying manual password, attempt $retry/$maxRetries (timeout: ${timeoutSeconds}s)"
                }

                val success = attemptConnectionWithRetry(network, password, timeoutSeconds)

                if (success) {
                    withContext(Dispatchers.Main) {
                        _connectionStatus.value = "✅ Connected to ${network.ssid} with manual password!"
                        _connectionProgress.value = "Manual connection successful! Saving password..."

                        _successfulPasswords.value = _successfulPasswords.value.toMutableMap().apply {
                            put(network.bssid, password)
                        }

                        if (!_passwords.value.contains(password)) {
                            _passwords.value = _passwords.value + password
                        }
                    }

                    // Database operations already on background thread
                    updatePinnedNetworkPassword(network, password)
                    updateFirebaseWithPassword(network, password)

                    connected = true
                    delay(1000)
                    break
                } else {
                    if (retry < maxRetries) {
                        withContext(Dispatchers.Main) {
                            _connectionProgress.value = "❌ Attempt $retry failed, retrying in 2s..."
                        }
                        delay(2000)
                        // Check if cancelled during delay
                        if (!_connectingNetworks.value.contains(network.bssid)) {
                            android.util.Log.d("ConnectionManager", "Manual connection cancelled during retry delay")
                            return@launch
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _connectionStatus.value = "❌ Manual password failed for ${network.ssid} after $maxRetries attempts"
                            _connectionProgress.value = "Manual password incorrect after all retries"
                        }
                    }
                }
            }

            // Clear progress data and remove from connecting set on main thread
            withContext(Dispatchers.Main) {
                _currentPassword.value = null
                _currentAttempt.value = 0
                _totalAttempts.value = 0
                _connectingNetworkName.value = null
                _connectingNetworks.value = _connectingNetworks.value - network.bssid
                _isConnecting.value = _connectingNetworks.value.isNotEmpty()
            }
            // Only delay if not cancelled
            try {
                delay(2000)
            } catch (e: CancellationException) {
                // If cancelled, don't delay - clear immediately
            }
            withContext(Dispatchers.Main) {
                _connectionProgress.value = null
            }
        }
    }

    private suspend fun attemptConnectionWithRetry(network: WifiNetwork, password: String, timeoutSeconds: Int): Boolean {
        return try {
            if (isEmulator()) {
                // Emulator fallback - use mock data for testing
                val connectionDelay = (timeoutSeconds * 1000 * 0.7).toLong()
                delay(connectionDelay)

                val mockNetwork = generateMockNetworks().find { it.bssid == network.bssid }
                if (mockNetwork != null && mockNetwork.password != null) {
                    val isCorrectPassword = password == mockNetwork.password || 
                           listOf("test", "demo", "password", "admin").contains(password.lowercase())
                    
                    if (isCorrectPassword) {
                        // Simulate successful connection and immediate disconnection
                        _connectionProgress.value = "✅ Successfully connected to ${network.ssid}"
                        delay(1000)
                        _connectionProgress.value = "Disconnecting and saving password..."
                        delay(500)
                        saveWorkingPassword(network, password)
                        _connectionProgress.value = "✅ Password validated and saved for ${network.ssid}"
                        return true
                    }
                }
                return network.security.contains("Open", ignoreCase = true)
            } else {
                // Real device implementation with enhanced validation
                _connectionProgress.value = "Checking permissions and location services..."
                
                // Check for missing permissions first
                val missingPermissions = wifiScanner.getMissingPermissions()
                if (missingPermissions.isNotEmpty()) {
                    _connectionProgress.value = "❌ Missing permissions: ${missingPermissions.joinToString(", ")}"
                    _connectionStatus.value = "❌ Required permissions not granted. Please enable: ${missingPermissions.joinToString(", ")}"
                    return false
                }
                
                // Check if location is enabled (required for Android 10+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                    val isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                                          locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                    
                    if (!isLocationEnabled) {
                        _connectionProgress.value = "❌ Location services must be enabled for Wi-Fi connections on Android 10+"
                        _connectionStatus.value = "❌ Please enable location services in device settings"
                        return false
                    }
                }
                
                // Enhanced validation: Clear any existing working password to force fresh validation
                clearWorkingPassword(network)
                
                // Ensure no fallback or default passwords interfere
                if (isDefaultOrFallbackPassword(password)) {
                    _connectionProgress.value = "⚠️ Avoiding common/default password for security"
                    _connectionStatus.value = "❌ Please use the actual network password, not a default one"
                    return false
                }
                
                _connectionProgress.value = "Initiating fresh connection attempt to ${network.ssid}..."
                
                // Create a connection result tracker
                var connectionResult: Boolean? = null
                var connectionError: String? = null
                
                // Set up a listener for connection status updates from WifiScanner
                val originalStatus = wifiScanner.connectionStatus.value
                
                // Use WifiScanner to attempt connection with enhanced validation
                wifiScanner.connectToNetwork(network, password)
                
                // Wait for connection attempt with enhanced monitoring
                var elapsedTime = 0
                val checkInterval = 500L // Check every 500ms
                val maxWaitTime = timeoutSeconds * 1000L
                
                while (elapsedTime < maxWaitTime && connectionResult == null) {
                    delay(checkInterval)
                    elapsedTime += checkInterval.toInt()
                    
                    // Update progress
                    val remainingTime = (maxWaitTime - elapsedTime) / 1000
                    _connectionProgress.value = "Validating password with ${network.ssid}... (${remainingTime}s remaining)"
                    
                    // Check the WifiScanner's connection status for results
                    val currentStatus = wifiScanner.connectionStatus.value
                    if (currentStatus != originalStatus && currentStatus != null) {
                        android.util.Log.d("ConnectionManager", "WifiScanner status: '$currentStatus'")
                        when {
                            currentStatus.contains("✅ Password validated") -> {
                                connectionResult = true
                                _connectionProgress.value = "✅ Password validation successful!"
                                android.util.Log.d("ConnectionManager", "Password validation successful")
                            }
                            currentStatus.contains("❌") -> {
                                connectionResult = false
                                connectionError = currentStatus
                                _connectionProgress.value = "❌ Password validation failed"
                                android.util.Log.d("ConnectionManager", "Password validation failed: $currentStatus")
                            }
                            currentStatus.contains("Connection validated and disconnected") -> {
                                connectionResult = true
                                _connectionProgress.value = "✅ Connection validated successfully!"
                                android.util.Log.d("ConnectionManager", "Connection validated and disconnected")
                            }
                        }
                    }
                }
                
                // Handle the connection result
                when (connectionResult) {
                    true -> {
                        // Save the working password to SharedPreferences
                        saveWorkingPassword(network, password)
                        
                        // Store the successful password in the map for UI updates
                        _successfulPasswords.value = _successfulPasswords.value.toMutableMap().apply {
                            put(network.bssid, password)
                        }
                        android.util.Log.d("ConnectionManager", "Stored successful password for ${network.bssid}: ${password.take(3)}***")
                        
                        _connectionProgress.value = "✅ Password validated and saved for ${network.ssid}"
                        _connectionStatus.value = "✅ Connection successful! Password saved for future reference."
                        return true
                    }
                    false -> {
                        _connectionProgress.value = connectionError ?: "❌ Connection failed"
                        _connectionStatus.value = connectionError ?: "❌ Failed to connect to ${network.ssid} - incorrect password"
                        return false
                    }
                    null -> {
                        // Connection timeout
                        _connectionProgress.value = "❌ Connection timeout after ${timeoutSeconds}s"
                        _connectionStatus.value = "❌ Connection timeout - no response from ${network.ssid}"
                        return false
                    }
                }
            }
        } catch (e: SecurityException) {
            _connectionProgress.value = "❌ Permission error: ${e.message}"
            _connectionStatus.value = "❌ Security error: Missing required permissions"
            false
        } catch (e: Exception) {
            _connectionProgress.value = "❌ Connection error: ${e.message}"
            _connectionStatus.value = "❌ Connection failed: ${e.message}"
            false
        }
    }

    private suspend fun saveWorkingPassword(network: WifiNetwork, password: String) = withContext(Dispatchers.IO) {
        try {
            // Save to a separate SharedPreferences for working passwords
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            
            // Use network BSSID as key for more precise identification
            workingPasswordsPrefs.edit()
                .putString(network.bssid, password)
                .putString("${network.bssid}_ssid", network.ssid) // Store SSID for reference
                .putLong("${network.bssid}_timestamp", System.currentTimeMillis())
                .apply()
                
            withContext(Dispatchers.Main) {
                _connectionProgress.value = "✅ Working password saved for future use"
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _connectionProgress.value = "Warning: Could not save working password: ${e.message}"
            }
        }
    }

    suspend fun getWorkingPassword(network: WifiNetwork): String? = withContext(Dispatchers.IO) {
        try {
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            workingPasswordsPrefs.getString(network.bssid, null)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun hasWorkingPassword(network: WifiNetwork): Boolean {
        return getWorkingPassword(network) != null
    }

    private suspend fun updatePinnedNetworkPassword(network: WifiNetwork, password: String) = withContext(Dispatchers.IO) {
        try {
            val existingPinned = pinnedNetworkDao.getPinnedNetworkByBssid(network.bssid)
            existingPinned?.let { pinned ->
                val updatedNetwork = pinned.copy(savedPassword = password)
                pinnedNetworkDao.updatePinnedNetwork(updatedNetwork)
                withContext(Dispatchers.Main) {
                    _connectionProgress.value = "✅ Password saved to pinned network"
                }
                delay(500)
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to update pinned network password: ${e.message}")
        }
    }

    private suspend fun updateFirebaseWithPassword(network: WifiNetwork, password: String) = withContext(Dispatchers.IO) {
        try {
            val networkWithPassword = network.copy(password = password)
            when (val result = firebaseRepository.uploadWifiNetworks(listOf(networkWithPassword))) {
                is Result.Success -> {
                    withContext(Dispatchers.Main) {
                        _connectionProgress.value = "✅ Password updated in cloud database"
                    }
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
        android.util.Log.d("ConnectionManager", "Clearing connection progress - cancelling all connections")
        
        // Cancel the current connection job IMMEDIATELY
        currentConnectionJob?.cancel(CancellationException("User cancelled connection"))
        currentConnectionJob = null
        
        // Cancel any ongoing Wi-Fi connection attempts in WifiScanner
        wifiScanner.cancelConnectionAttempt()
        
        // Clear all connection state IMMEDIATELY - no delays
        _connectionProgress.value = null
        _currentPassword.value = null
        _currentAttempt.value = 0
        _totalAttempts.value = 0
        _connectingNetworkName.value = null
        _connectingNetworks.value = emptySet()
        _isConnecting.value = false
        _connectionStatus.value = null // Clear status immediately, no message
        
        android.util.Log.d("ConnectionManager", "All connection attempts cancelled and UI cleared immediately")
    }

    fun cancelConnection() {
        clearConnectionProgress()
    }
    
    fun clearConnectionStatus() {
        // Clear connection status to prevent re-display when returning from other screens
        _connectionStatus.value = null
        android.util.Log.d("ConnectionManager", "Connection status cleared")
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

    fun updatePasswordsFromSettings(passwords: List<String>) {
        _passwords.value = passwords
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

    // Enhanced helper functions for improved connection handling

    suspend fun clearWorkingPassword(network: WifiNetwork) = withContext(Dispatchers.IO) {
        try {
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            workingPasswordsPrefs.edit()
                .remove(network.bssid)
                .remove("${network.bssid}_ssid")
                .remove("${network.bssid}_timestamp")
                .apply()
        } catch (e: Exception) {
            println("DEBUG: Failed to clear working password: ${e.message}")
        }
    }

    /**
     * Check if password is a common default or fallback password that should be avoided
     */
    private fun isDefaultOrFallbackPassword(password: String): Boolean {
        val commonDefaults = listOf(
            "password", "123456", "admin", "guest", "default", "wifi", 
            "12345678", "qwerty", "letmein", "welcome", "changeme",
            "router", "netgear", "linksys", "dlink", "tplink"
        )
        return commonDefaults.contains(password.lowercase())
    }

    /**
     * Get all working passwords for debugging/management purposes
     */
    suspend fun getAllWorkingPasswords(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            val allEntries = workingPasswordsPrefs.all
            val passwords = mutableMapOf<String, String>()
            
            allEntries.forEach { (key, value) ->
                if (!key.contains("_ssid") && !key.contains("_timestamp") && value is String) {
                    passwords[key] = value
                }
            }
            
            passwords
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Clear all working passwords (for reset/cleanup purposes)
     */
    suspend fun clearAllWorkingPasswords() = withContext(Dispatchers.IO) {
        try {
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            workingPasswordsPrefs.edit().clear().apply()
        } catch (e: Exception) {
            println("DEBUG: Failed to clear all working passwords: ${e.message}")
        }
    }
}