package com.ner.wimap.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import com.ner.wimap.utils.EncryptionUtils

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
    val encryptedPassword: String? = null, // GDPR: Encrypted password storage
    val photoUri: String? = null,
    val pinnedAt: Long = System.currentTimeMillis(),
    val isOffline: Boolean = false, // Track if network is currently offline/unavailable
    val lastSeenTimestamp: Long = timestamp, // Track when network was last seen in scans
    val dataRetentionDate: Long = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000), // GDPR: 1 year retention
    val consentVersion: String = "1.0" // GDPR: Track consent version
) {
    /**
     * GDPR-compliant password getter
     * Decrypts password only when needed
     */
    @get:Ignore
    val savedPassword: String?
        get() = EncryptionUtils.decrypt(encryptedPassword)
    
    /**
     * GDPR-compliant password setter
     * Automatically encrypts password
     */
    fun withPassword(password: String?): PinnedNetwork {
        return copy(encryptedPassword = EncryptionUtils.encrypt(password))
    }
    
    /**
     * Check if data should be automatically deleted under GDPR retention policy
     */
    @get:Ignore
    val shouldBeDeleted: Boolean
        get() = System.currentTimeMillis() > dataRetentionDate
    
    /**
     * Get anonymized version for analytics (GDPR compliant)
     */
    fun toAnonymized(): PinnedNetwork {
        val (anonLat, anonLon) = if (latitude != null && longitude != null) {
            EncryptionUtils.anonymizeLocation(latitude, longitude, EncryptionUtils.LocationPrecision.APPROXIMATE)
        } else {
            Pair(null, null)
        }
        
        return copy(
            bssid = EncryptionUtils.hashForAnalytics(bssid).take(8),
            ssid = if (ssid.isNotEmpty()) "anonymized_network" else ssid,
            latitude = anonLat,
            longitude = anonLon,
            encryptedPassword = null, // Never include passwords in analytics
            comment = null, // Remove personal comments
            photoUri = null // Remove personal photos
        )
    }
}