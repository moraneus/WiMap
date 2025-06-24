package com.ner.wimap.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    /**
     * Get all required permissions for Wi-Fi operations
     */
    fun getRequiredWifiPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )
        
        // Add NEARBY_WIFI_DEVICES permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        return permissions
    }
    
    /**
     * Check if all required Wi-Fi permissions are granted
     */
    fun hasAllWifiPermissions(context: Context): Boolean {
        return getRequiredWifiPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of missing Wi-Fi permissions
     */
    fun getMissingWifiPermissions(context: Context): List<String> {
        return getRequiredWifiPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if location services are enabled (required for Android 10+)
     */
    fun isLocationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true // Location not required for Wi-Fi on older versions
        }
        
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * Get user-friendly permission names for display
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> "Fine Location"
            Manifest.permission.ACCESS_WIFI_STATE -> "Wi-Fi State Access"
            Manifest.permission.CHANGE_WIFI_STATE -> "Wi-Fi State Control"
            Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Wi-Fi Devices"
            else -> permission.substringAfterLast(".")
        }
    }
    
    /**
     * Get user-friendly explanation for why each permission is needed
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> 
                "Required to scan for Wi-Fi networks on Android 10+"
            Manifest.permission.ACCESS_WIFI_STATE -> 
                "Required to read Wi-Fi network information"
            Manifest.permission.CHANGE_WIFI_STATE -> 
                "Required to connect to Wi-Fi networks"
            Manifest.permission.NEARBY_WIFI_DEVICES -> 
                "Required to discover nearby Wi-Fi devices on Android 13+"
            else -> "Required for Wi-Fi operations"
        }
    }
    
    /**
     * Check if we should show rationale for permission request
     */
    fun shouldShowRationale(context: Context, permission: String): Boolean {
        return if (context is androidx.fragment.app.FragmentActivity) {
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
        } else {
            false
        }
    }
    
    /**
     * Generate a comprehensive error message for missing permissions and location
     */
    fun generatePermissionErrorMessage(context: Context): String {
        val missingPermissions = getMissingWifiPermissions(context)
        val locationEnabled = isLocationEnabled(context)
        
        val messages = mutableListOf<String>()
        
        if (missingPermissions.isNotEmpty()) {
            messages.add("Missing permissions:")
            missingPermissions.forEach { permission ->
                messages.add("• ${getPermissionDisplayName(permission)}: ${getPermissionExplanation(permission)}")
            }
        }
        
        if (!locationEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            messages.add("Location services must be enabled for Wi-Fi scanning on Android 10+")
        }
        
        return if (messages.isNotEmpty()) {
            messages.joinToString("\n")
        } else {
            "All permissions granted and location services enabled"
        }
    }
}