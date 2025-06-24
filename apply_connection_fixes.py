#!/usr/bin/env python3
"""
Script to apply key Wi-Fi connection fixes to the ConnectionManager
"""

import re

def apply_connection_fixes():
    """Apply the essential connection fixes to ConnectionManager.kt"""
    
    connection_manager_path = "/Users/moraneus/AndroidStudioProjects/WiMap/app/src/main/java/com/ner/wimap/ui/viewmodel/ConnectionManager.kt"
    
    # Read the current file
    with open(connection_manager_path, 'r') as f:
        content = f.read()
    
    # Fix 1: Correct the password comparison logic
    old_password_check = '''                // Check if we already have a working password for this network
                val existingPassword = getWorkingPassword(network)
                if (existingPassword != null && existingPassword != password) {
                    _connectionProgress.value = "ℹ️ Using previously validated password for ${network.ssid}"
                    // Don't actually connect, just confirm we have the password
                    _connectionStatus.value = "✅ Password already validated for ${network.ssid}"
                    return true
                }'''
    
    new_password_check = '''                // Check if we already have a working password for this network
                val existingPassword = getWorkingPassword(network)
                if (existingPassword != null && existingPassword == password) {
                    _connectionProgress.value = "ℹ️ Using previously validated password for ${network.ssid}"
                    _connectionStatus.value = "✅ Password already validated for ${network.ssid}"
                    return true
                } else if (existingPassword != null && password != existingPassword) {
                    // Clear old password if we're trying a different one
                    clearWorkingPassword(network)
                }

                // Ensure no fallback or default passwords interfere
                if (isDefaultOrFallbackPassword(password)) {
                    _connectionProgress.value = "⚠️ Avoiding common/default password for security"
                    _connectionStatus.value = "❌ Please use the actual network password, not a default one"
                    return false
                }'''
    
    content = content.replace(old_password_check, new_password_check)
    
    # Fix 2: Add helper functions at the end of the class
    helper_functions = '''
    // Enhanced helper functions for improved connection handling

    fun clearWorkingPassword(network: WifiNetwork) {
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
    fun getAllWorkingPasswords(): Map<String, String> {
        return try {
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
    fun clearAllWorkingPasswords() {
        try {
            val workingPasswordsPrefs = context.getSharedPreferences("working_passwords", Context.MODE_PRIVATE)
            workingPasswordsPrefs.edit().clear().apply()
        } catch (e: Exception) {
            println("DEBUG: Failed to clear all working passwords: ${e.message}")
        }
    }'''
    
    # Add helper functions before the last closing brace
    content = content.rstrip()
    if content.endswith('}'):
        content = content[:-1] + helper_functions + '\n}'
    
    # Write the updated content back
    with open(connection_manager_path, 'w') as f:
        f.write(content)
    
    print("✅ Applied connection fixes to ConnectionManager.kt")
    print("Key improvements:")
    print("  - Fixed password comparison logic")
    print("  - Added default password detection")
    print("  - Added working password cleanup")
    print("  - Added helper functions for password management")

if __name__ == "__main__":
    apply_connection_fixes()