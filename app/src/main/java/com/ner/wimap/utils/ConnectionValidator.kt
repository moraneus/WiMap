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
}