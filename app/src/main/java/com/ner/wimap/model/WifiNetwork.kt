package com.ner.wimap.model

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val security: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val password: String? = null,
    val peakRssi: Int = rssi, // Track the strongest RSSI seen for this network
    val peakRssiLatitude: Double? = latitude, // GPS coordinates where peak RSSI was observed
    val peakRssiLongitude: Double? = longitude,
    val lastSeenTimestamp: Long = timestamp, // Track when this network was last seen
    val comment: String = "", // User-added comment
    val photoPath: String? = null, // Path to attached photo
    val isPinned: Boolean = false, // Pin status
    val isOffline: Boolean = false, // Network is out of range/timed out
    val vendor: String? = null // Router vendor information
)
