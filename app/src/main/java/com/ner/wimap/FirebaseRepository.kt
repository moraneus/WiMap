package com.ner.wimap

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.data.DeviceInfoManager
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(
    private val deviceInfoManager: DeviceInfoManager
) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("wifi_scans")
    private val TAG = "FirebaseRepository"

    suspend fun uploadWifiNetworks(networks: List<WifiNetwork>): Result<String> {
        Log.d(TAG, "uploadWifiNetworks called with ${networks.size} networks")
        
        return try {
            var updatedCount = 0
            var addedCount = 0
            var passwordUpdatedCount = 0
            
            // Get device ADID (if consent granted)
            val adid = deviceInfoManager.getCachedAdid() ?: "anonymous"
            Log.d(TAG, "Using ADID: $adid")

            for (network in networks) {
                // Create a unique identifier based on BSSID and SSID
                val uniqueId = "${network.bssid}_${network.ssid}".replace(":", "_").replace(" ", "_")
                val documentRef = collection.document(uniqueId)
                Log.d(TAG, "Processing network: ${network.ssid} (${network.bssid}) -> document: $uniqueId")

                // Check if document already exists
                val snapshot = documentRef.get().await()
                if (snapshot.exists()) {
                    // Document exists, check RSSI and password
                    val existingRssi = snapshot.getLong("rssi")?.toInt() ?: Int.MIN_VALUE
                    val existingPassword = snapshot.getString("password")
                    val hasNewPassword = !network.password.isNullOrEmpty()
                    val shouldUpdateRssi = network.rssi > existingRssi
                    val shouldUpdatePassword = hasNewPassword && existingPassword.isNullOrEmpty()

                    if (shouldUpdateRssi || shouldUpdatePassword) {
                        // Create updated network data
                        val networkData = hashMapOf(
                            "ssid" to network.ssid,
                            "bssid" to network.bssid,
                            "rssi" to if (shouldUpdateRssi) network.rssi else existingRssi,
                            "channel" to network.channel,
                            "security" to network.security,
                            "gps_lat" to network.latitude,
                            "gps_lng" to network.longitude,
                            "vendor" to (network.vendor ?: "Unknown"),
                            "timestamp" to System.currentTimeMillis(),
                            "last_updated" to System.currentTimeMillis(),
                            "adid" to adid
                        )

                        // Add password only if we have a new one or updating with stronger RSSI
                        if (hasNewPassword) {
                            networkData["password"] = network.password!!
                            passwordUpdatedCount++
                            Log.d(TAG, "Updated password for network: ${network.ssid} - BSSID: ${network.bssid}")
                        } else if (shouldUpdateRssi && !existingPassword.isNullOrEmpty()) {
                            // Keep existing password when updating RSSI
                            networkData["password"] = existingPassword
                        }

                        documentRef.set(networkData).await()
                        updatedCount++

                        if (shouldUpdateRssi) {
                            Log.d(TAG, "Updated WifiNetwork with stronger RSSI: ${network.ssid} - BSSID: ${network.bssid}")
                        }
                    } else {
                        Log.d(TAG, "Skipped WifiNetwork update (no improvements): ${network.ssid} - BSSID: ${network.bssid}")
                    }
                } else {
                    // Document does not exist, create a new record
                    val networkData = hashMapOf(
                        "ssid" to network.ssid,
                        "bssid" to network.bssid,
                        "rssi" to network.rssi,
                        "channel" to network.channel,
                        "security" to network.security,
                        "gps_lat" to network.latitude,
                        "gps_lng" to network.longitude,
                        "vendor" to (network.vendor ?: "Unknown"),
                        "timestamp" to System.currentTimeMillis(),
                        "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "last_updated" to System.currentTimeMillis(),
                        "adid" to adid
                    )

                    // Add password if available
                    if (!network.password.isNullOrEmpty()) {
                        networkData["password"] = network.password
                        passwordUpdatedCount++
                        Log.d(TAG, "Added new network with password: ${network.ssid} - BSSID: ${network.bssid}")
                    }

                    documentRef.set(networkData).await()
                    addedCount++
                    Log.d(TAG, "Uploaded new WifiNetwork: ${network.ssid} - BSSID: ${network.bssid}")
                }
            }

            val resultMessage = buildString {
                append("Smart upload complete: ")
                append("$addedCount new networks added")
                if (updatedCount > 0) append(", $updatedCount updated with stronger RSSI")
                if (passwordUpdatedCount > 0) append(", $passwordUpdatedCount passwords saved")
            }

            Log.d(TAG, "Upload completed successfully: $resultMessage")
            Result.Success(resultMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading WifiNetworks", e)
            Result.Failure(e)
        }
    }

    /**
     * Update password for a specific network in Firebase
     */
    suspend fun updateNetworkPassword(network: WifiNetwork, password: String): Result<String> {
        return try {
            val uniqueId = "${network.bssid}_${network.ssid}".replace(":", "_").replace(" ", "_")
            val documentRef = collection.document(uniqueId)

            // Check if document exists
            val snapshot = documentRef.get().await()
            if (snapshot.exists()) {
                // Update existing document with password
                documentRef.update("password", password, "timestamp", System.currentTimeMillis()).await()
                Log.d(TAG, "Updated password for existing network: ${network.ssid}")
                Result.Success("Password updated for ${network.ssid}")
            } else {
                // Create new document with password
                val networkWithPassword = network.copy(password = password)
                uploadWifiNetworks(listOf(networkWithPassword))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating network password", e)
            Result.Failure(e)
        }
    }

    /**
     * Get network by BSSID and SSID
     */
    suspend fun getNetwork(bssid: String, ssid: String): Result<WifiNetwork?> {
        return try {
            val uniqueId = "${bssid}_${ssid}".replace(":", "_").replace(" ", "_")
            val documentRef = collection.document(uniqueId)
            val snapshot = documentRef.get().await()

            if (snapshot.exists()) {
                val data = snapshot.data
                val network = WifiNetwork(
                    ssid = data?.get("ssid") as? String ?: ssid,
                    bssid = data?.get("bssid") as? String ?: bssid,
                    rssi = (data?.get("rssi") as? Long)?.toInt() ?: 0,
                    channel = (data?.get("channel") as? Long)?.toInt() ?: 0,
                    security = data?.get("security") as? String ?: "Unknown",
                    latitude = data?.get("latitude") as? Double,
                    longitude = data?.get("longitude") as? Double,
                    timestamp = (data?.get("timestamp") as? Long) ?: System.currentTimeMillis(),
                    password = data?.get("password") as? String
                )
                Result.Success(network)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network", e)
            Result.Failure(e)
        }
    }

    /**
     * Delete network from Firebase
     */
    suspend fun deleteNetwork(bssid: String, ssid: String): Result<String> {
        return try {
            val uniqueId = "${bssid}_${ssid}".replace(":", "_").replace(" ", "_")
            val documentRef = collection.document(uniqueId)
            documentRef.delete().await()
            Log.d(TAG, "Deleted network: $ssid - $bssid")
            Result.Success("Network deleted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting network", e)
            Result.Failure(e)
        }
    }

    /**
     * Get all networks from Firebase (for debugging/admin purposes)
     */
    suspend fun getAllNetworks(): Result<List<WifiNetwork>> {
        return try {
            val snapshot = collection.get().await()
            val networks = snapshot.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    WifiNetwork(
                        ssid = data["ssid"] as? String ?: "",
                        bssid = data["bssid"] as? String ?: "",
                        rssi = (data["rssi"] as? Long)?.toInt() ?: 0,
                        channel = (data["channel"] as? Long)?.toInt() ?: 0,
                        security = data["security"] as? String ?: "Unknown",
                        latitude = data["latitude"] as? Double,
                        longitude = data["longitude"] as? Double,
                        timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                        password = data["password"] as? String
                    )
                } else null
            }
            Result.Success(networks)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all networks", e)
            Result.Failure(e)
        }
    }
}

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val exception: Throwable) : Result<Nothing>()
}