package com.ner.wimap.utils

import android.content.Context
import com.ner.wimap.wifi.WifiScanner

/**
 * Utility class for validating Wi-Fi connection prerequisites
 */
object ConnectionValidator {
    
    // Data class for permission validation results
    data class PermissionValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )

    // Data class for connection attempt results
    data class ConnectionResult(
        val success: Boolean,
        val errorMessage: String
    )

    /**
     * Comprehensive validation of permissions and location services
     */
    fun validatePermissionsAndLocation(context: Context, wifiScanner: WifiScanner): PermissionValidationResult {
        // Check for missing permissions first
        val missingPermissions = wifiScanner.getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            val permissionNames = missingPermissions.map { permission ->
                PermissionUtils.getPermissionDisplayName(permission)
            }
            return PermissionValidationResult(
                false,
                "Missing required permissions: ${permissionNames.joinToString(", ")}. Please grant these permissions in app settings."
            )
        }

        // Check if location is enabled (required for Android 10+)
        if (!PermissionUtils.isLocationEnabled(context)) {
            return PermissionValidationResult(
                false,
                "Location services must be enabled for Wi-Fi connections on Android 10+. Please enable location in device settings."
            )
        }

        return PermissionValidationResult(true, "All permissions and location services are available")
    }

    /**
     * Check if password is a common default or fallback password that should be avoided
     */
    fun isDefaultOrFallbackPassword(password: String): Boolean {
        val commonDefaults = listOf(
            "password", "123456", "admin", "guest", "default", "wifi", 
            "12345678", "qwerty", "letmein", "welcome", "changeme",
            "router", "netgear", "linksys", "dlink", "tplink",
            "00000000", "11111111", "password123", "wifipassword"
        )
        return commonDefaults.contains(password.lowercase())
    }

    /**
     * Validate password strength and format
     */
    fun validatePasswordFormat(password: String, securityType: String): Boolean {
        return when {
            securityType.contains("WPA", ignoreCase = true) -> {
                // WPA/WPA2/WPA3 passwords must be 8-63 characters
                password.length in 8..63
            }
            securityType.contains("WEP", ignoreCase = true) -> {
                // WEP passwords are typically 5, 10, 13, or 26 characters
                password.length in listOf(5, 10, 13, 26)
            }
            securityType.contains("Open", ignoreCase = true) -> {
                // Open networks don't require passwords
                true
            }
            else -> {
                // Default validation for unknown security types
                password.length >= 8
            }
        }
    }

    /**
     * Check if a password looks like a MAC address or other network identifier
     */
    fun isNetworkIdentifierPassword(password: String): Boolean {
        // Check for MAC address pattern (xx:xx:xx:xx:xx:xx or xxxxxxxxxxxxxx)
        val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$|^[0-9A-Fa-f]{12}$")
        return macPattern.matches(password)
    }

    /**
     * Enhanced validation for connection attempts to prevent cached credential interference
     */
    fun validateFreshConnectionAttempt(
        network: com.ner.wimap.model.WifiNetwork,
        password: String,
        context: Context
    ): ValidationResult {
        // Check if this might be using cached credentials
        if (isLikelyUsingCachedCredentials(network, context)) {
            return ValidationResult(
                false,
                "Network may be using cached credentials. Clear WiFi settings and try again."
            )
        }

        // Validate password is not a common default
        if (isDefaultOrFallbackPassword(password)) {
            return ValidationResult(
                false,
                "Avoiding common default password. Please use the actual network password."
            )
        }

        // Validate password format for security type
        if (!validatePasswordFormat(password, network.security)) {
            return ValidationResult(
                false,
                "Password format invalid for ${network.security} security type."
            )
        }

        return ValidationResult(true, "Connection attempt validated")
    }

    /**
     * Check if a network is likely using cached credentials
     */
    private fun isLikelyUsingCachedCredentials(
        network: com.ner.wimap.model.WifiNetwork,
        context: Context
    ): Boolean {
        try {
            // Check if network is in Android's configured networks
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                val configuredNetworks = wifiManager.configuredNetworks
                return configuredNetworks?.any { config ->
                    config.SSID == "\"${network.ssid}\""
                } ?: false
            }
            
            // For Android 10+, we rely on other indicators
            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Validate that a connection result represents a fresh authentication
     */
    fun validateFreshAuthentication(
        network: com.ner.wimap.model.WifiNetwork,
        connectionStartTime: Long,
        connectionEndTime: Long
    ): ValidationResult {
        val connectionDuration = connectionEndTime - connectionStartTime
        
        // If connection was too fast (< 2 seconds), it might be using cached credentials
        if (connectionDuration < 2000) {
            return ValidationResult(
                false,
                "Connection succeeded too quickly - may be using cached credentials"
            )
        }
        
        // If connection took reasonable time, it's likely a fresh authentication
        return ValidationResult(true, "Fresh authentication validated")
    }

    /**
     * Enhanced connection result validation
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )

    /**
     * Comprehensive pre-connection validation
     */
    fun validateConnectionPrerequisites(
        context: Context,
        network: com.ner.wimap.model.WifiNetwork,
        password: String?,
        wifiScanner: com.ner.wimap.wifi.WifiScanner
    ): ValidationResult {
        // Check permissions and location
        val permissionResult = validatePermissionsAndLocation(context, wifiScanner)
        if (!permissionResult.isValid) {
            return ValidationResult(false, permissionResult.errorMessage)
        }

        // Check signal strength
        if (network.rssi < -85) {
            return ValidationResult(
                false,
                "Signal too weak (${network.rssi}dBm). Move closer to the network."
            )
        }

        // Validate password if provided
        if (!password.isNullOrEmpty()) {
            if (isDefaultOrFallbackPassword(password)) {
                return ValidationResult(
                    false,
                    "Please use the actual network password, not a default one."
                )
            }

            if (!validatePasswordFormat(password, network.security)) {
                return ValidationResult(
                    false,
                    "Password format invalid for ${network.security} security."
                )
            }
        }

        return ValidationResult(true, "All prerequisites validated")
    }
}