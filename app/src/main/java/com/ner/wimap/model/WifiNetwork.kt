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
    val password: String? = null
)
