package com.ner.wimap.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

@Entity(tableName = "scan_sessions")
@TypeConverters(NetworkListConverter::class)
data class ScanSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String, // User-customizable name
    val timestamp: Long, // Original timestamp (read-only)
    val networkCount: Int, // Total networks in this session
    val networks: List<SessionNetwork> // Networks found in this scan
)

data class SessionNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val security: String,
    val vendor: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastSeenTimestamp: Long,
    val isOffline: Boolean = false // Was offline during this scan session
)

class NetworkListConverter {
    private val gson = Gson()
    
    companion object {
        private val listType = object : TypeToken<List<SessionNetwork>>() {}.type
    }
    
    @TypeConverter
    fun fromNetworkList(networks: List<SessionNetwork>): String {
        return gson.toJson(networks)
    }
    
    @TypeConverter
    fun toNetworkList(networksJson: String): List<SessionNetwork> {
        return gson.fromJson(networksJson, listType)
    }
}