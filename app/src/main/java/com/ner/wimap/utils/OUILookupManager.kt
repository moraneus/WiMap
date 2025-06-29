package com.ner.wimap.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * OUI (Organizationally Unique Identifier) Lookup Manager
 * Provides fast offline MAC vendor resolution using IEEE OUI database
 */
class OUILookupManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: OUILookupManager? = null
        
        fun getInstance(): OUILookupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OUILookupManager().also { INSTANCE = it }
            }
        }
    }
    
    // Fast lookup map: OUI (first 6 hex chars) -> Vendor Name
    private val ouiMap = ConcurrentHashMap<String, String>()
    private var isInitialized = false
    
    /**
     * Initialize the OUI database from assets
     * Should be called once on app startup
     */
    suspend fun initialize(context: Context) {
        if (isInitialized) return
        
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.assets.open("oui_database.csv")
                val reader = BufferedReader(InputStreamReader(inputStream))
                
                var lineCount = 0
                reader.useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split(",", limit = 2)
                        if (parts.size == 2) {
                            val oui = parts[0].trim().uppercase()
                            val vendor = parts[1].trim()
                            ouiMap[oui] = vendor
                            lineCount++
                            if (lineCount <= 5) {
                                android.util.Log.d("OUILookupManager", "Loading: $oui -> $vendor")
                            }
                        }
                    }
                }
                
                isInitialized = true
                android.util.Log.d("OUILookupManager", "Initialized with $lineCount OUI entries")
                
                // Test a few known entries
                android.util.Log.d("OUILookupManager", "Test lookup - 00:00:0C: ${ouiMap["00:00:0C"]}")
                android.util.Log.d("OUILookupManager", "Test lookup - 3C:5A:B4: ${ouiMap["3C:5A:B4"]}")
                android.util.Log.d("OUILookupManager", "Sample entries: ${ouiMap.keys.take(10)}")
                
            } catch (e: Exception) {
                android.util.Log.e("OUILookupManager", "Failed to initialize OUI database", e)
            }
        }
    }
    
    /**
     * Extract OUI (first 6 hex characters) from MAC address
     * @param macAddress MAC address in format like "AA:BB:CC:DD:EE:FF"
     * @return OUI in format "AA:BB:CC" or null if invalid
     */
    private fun extractOUI(macAddress: String): String? {
        val cleaned = macAddress.replace(":", "").replace("-", "").replace(".", "")
        if (cleaned.length < 6) return null
        
        val oui = cleaned.substring(0, 6).uppercase()
        // Convert to colon-separated format for lookup
        return "${oui.substring(0, 2)}:${oui.substring(2, 4)}:${oui.substring(4, 6)}"
    }
    
    /**
     * Look up vendor name for a MAC address
     * @param macAddress MAC address (BSSID) in any common format
     * @return Vendor name or null if not found
     */
    fun lookupVendor(macAddress: String?): String? {
        if (!isInitialized || macAddress.isNullOrBlank()) {
            android.util.Log.d("OUILookupManager", "Lookup failed - initialized: $isInitialized, macAddress: $macAddress")
            return null
        }
        
        val oui = extractOUI(macAddress)
        if (oui == null) {
            android.util.Log.d("OUILookupManager", "Failed to extract OUI from: $macAddress")
            return null
        }
        
        val vendor = ouiMap[oui]
        android.util.Log.d("OUILookupManager", "Lookup: $macAddress -> OUI: $oui -> Vendor: $vendor")
        return vendor
    }
    
    /**
     * Look up vendor name with fallback to "Unknown"
     * @param macAddress MAC address (BSSID) in any common format
     * @return Vendor name or "Unknown Vendor" if not found
     */
    fun lookupVendorWithFallback(macAddress: String?): String {
        return lookupVendor(macAddress) ?: "Unknown Vendor"
    }
    
    /**
     * Get vendor name in short format (removes common suffixes)
     * @param macAddress MAC address (BSSID) in any common format
     * @return Short vendor name (e.g., "Cisco" instead of "Cisco Systems, Inc")
     */
    fun lookupVendorShort(macAddress: String?): String? {
        val fullVendor = lookupVendor(macAddress) ?: return null
        
        // Remove common corporate suffixes for cleaner display
        return fullVendor
            .replace(", Inc.", "")
            .replace(", Inc", "")
            .replace(" Inc.", "")
            .replace(" Inc", "")
            .replace(", Ltd.", "")
            .replace(", Ltd", "")
            .replace(" Ltd.", "")
            .replace(" Ltd", "")
            .replace(", LLC", "")
            .replace(" LLC", "")
            .replace(", Corp.", "")
            .replace(", Corp", "")
            .replace(" Corp.", "")
            .replace(" Corp", "")
            .replace(", Co.", "")
            .replace(", Co", "")
            .replace(" Co.", "")
            .replace(" Co", "")
            .replace(" Corporation", "")
            .replace(" Systems", "")
            .replace(" Technologies", "")
            .replace(" Technology", "")
            .trim()
    }
    
    /**
     * Get vendor statistics
     * @return Map of vendor name to count of OUIs assigned
     */
    fun getVendorStats(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        ouiMap.values.forEach { vendor ->
            val shortVendor = vendor
                .replace(", Inc.", "")
                .replace(" Corporation", "")
                .replace(" Systems", "")
                .trim()
            stats[shortVendor] = stats.getOrDefault(shortVendor, 0) + 1
        }
        return stats.toMap()
    }
    
    /**
     * Check if the database is ready for lookups
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Get the number of OUI entries loaded
     */
    fun getEntryCount(): Int = ouiMap.size
}