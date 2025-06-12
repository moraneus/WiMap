package com.ner.wimap.data.database


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_networks")
data class PinnedNetwork(
    @PrimaryKey
    val bssid: String, // Use BSSID as primary key since it's unique
    val ssid: String,
    val rssi: Int,
    val channel: Int,
    val security: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val comment: String? = null,
    val savedPassword: String? = null,
    val photoUri: String? = null,
    val pinnedAt: Long = System.currentTimeMillis()
)