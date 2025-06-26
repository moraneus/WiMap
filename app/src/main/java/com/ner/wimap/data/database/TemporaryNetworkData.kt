package com.ner.wimap.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temporary_network_data")
data class TemporaryNetworkData(
    @PrimaryKey
    val bssid: String, // Use BSSID as primary key since it's unique
    val ssid: String,
    val comment: String = "",
    val savedPassword: String? = null,
    val photoPath: String? = null,
    val isPinned: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)