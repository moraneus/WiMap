package com.ner.wimap

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
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
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "FirebaseRepository"
    
    /**
     * Get the device-specific collection for WiFi scans
     * Structure: wifi_scans -> [user_uid] -> networks -> [network_id]
     */
    private fun getDeviceCollection(): com.google.firebase.firestore.CollectionReference {
        val userUid = getCurrentUserId() ?: throw Exception("User not authenticated - cannot access device collection")
        return db.collection("wifi_scans")
            .document(userUid)
            .collection("networks")
    }

    suspend fun uploadWifiNetworks(networks: List<WifiNetwork>): Result<String> {
        Log.d(TAG, "uploadWifiNetworks called with ${networks.size} networks")
        
        return try {
            var updatedCount = 0
            var addedCount = 0
            
            // Get Firebase User UID and mandatory device info
            val userUid = getCurrentUserId() ?: throw Exception("User not authenticated")
            val deviceInfo = deviceInfoManager.getMandatoryDeviceInfo()
            val adid = deviceInfo.adid
            val deviceCollection = getDeviceCollection()
            
            Log.d(TAG, "Using User UID: $userUid, ADID: $adid")
            
            // Ensure Firebase Auth user exists
            ensureFirebaseUser()

            for (network in networks) {
                // Create a unique identifier based on BSSID and SSID
                val uniqueId = "${network.bssid}_${network.ssid}".replace(":", "_").replace(" ", "_")
                val documentRef = deviceCollection.document(uniqueId)
                Log.d(TAG, "Processing network: ${network.ssid} (${network.bssid}) -> user: $userUid, document: $uniqueId")

                // Check if document already exists
                val snapshot = documentRef.get().await()
                if (snapshot.exists()) {
                    // Document exists, check RSSI only (passwords are kept local)
                    val existingRssi = snapshot.getLong("rssi")?.toInt() ?: Int.MIN_VALUE
                    val shouldUpdateRssi = network.rssi > existingRssi

                    if (shouldUpdateRssi) {
                        // Create updated network data (NO PASSWORDS - kept local only)
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
                            "last_updated" to System.currentTimeMillis(),
                            "user_uid" to userUid,
                            "adid" to adid
                        )

                        documentRef.set(networkData).await()
                        updatedCount++
                        Log.d(TAG, "Updated WifiNetwork with stronger RSSI: ${network.ssid} - BSSID: ${network.bssid}")
                    } else {
                        Log.d(TAG, "Skipped WifiNetwork update (no RSSI improvement): ${network.ssid} - BSSID: ${network.bssid}")
                    }
                } else {
                    // Document does not exist, create a new record (NO PASSWORDS - kept local only)
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
                        "user_uid" to userUid,
                        "adid" to adid
                    )

                    documentRef.set(networkData).await()
                    addedCount++
                    Log.d(TAG, "Uploaded new WifiNetwork (no password): ${network.ssid} - BSSID: ${network.bssid}")
                }
            }

            val resultMessage = buildString {
                append("Smart upload complete: ")
                append("$addedCount new networks added")
                if (updatedCount > 0) append(", $updatedCount updated with stronger RSSI")
                append(" (passwords kept local only)")
            }

            Log.d(TAG, "Upload completed successfully: $resultMessage")
            Result.Success(resultMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading WifiNetworks", e)
            Result.Failure(e)
        }
    }

    /**
     * Note: Password management is handled locally only for privacy and security.
     * Passwords are never uploaded to cloud storage.
     */

    /**
     * Get network by BSSID and SSID
     */
    suspend fun getNetwork(bssid: String, ssid: String): Result<WifiNetwork?> {
        return try {
            val uniqueId = "${bssid}_${ssid}".replace(":", "_").replace(" ", "_")
            val deviceCollection = getDeviceCollection()
            val documentRef = deviceCollection.document(uniqueId)
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
                    password = null // Passwords are never stored in cloud
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
            val deviceCollection = getDeviceCollection()
            val documentRef = deviceCollection.document(uniqueId)
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
            val deviceCollection = getDeviceCollection()
            val snapshot = deviceCollection.get().await()
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
    
    
    /**
     * Get all networks for this device (for debugging/admin purposes)
     */
    suspend fun getDeviceNetworks(): Result<List<WifiNetwork>> {
        return getAllNetworks()
    }
    
    /**
     * Ensure Firebase Auth anonymous user exists
     * Creates an anonymous user if none exists
     */
    private suspend fun ensureFirebaseUser() {
        try {
            Log.d(TAG, "Checking Firebase Auth status...")
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d(TAG, "No Firebase user found, attempting to create anonymous user")
                Log.d(TAG, "Firebase project ID: ${FirebaseFirestore.getInstance().app.options.projectId}")
                
                val result = auth.signInAnonymously().await()
                val user = result.user
                if (user != null) {
                    Log.d(TAG, "✅ Successfully created anonymous Firebase user: ${user.uid}")
                    Log.d(TAG, "User is anonymous: ${user.isAnonymous}")
                    Log.d(TAG, "User creation time: ${user.metadata?.creationTimestamp}")
                } else {
                    Log.e(TAG, "❌ Failed to create anonymous user - result.user is null")
                }
            } else {
                Log.d(TAG, "✅ Firebase user already exists: ${currentUser.uid} (anonymous: ${currentUser.isAnonymous})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error ensuring Firebase user", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Get current Firebase user ID (if available)
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    /**
     * Initialize Firebase Auth on first app launch
     * Creates Firebase user first, then collects device info with User UID
     */
    suspend fun initializeOnFirstLaunch() {
        if (deviceInfoManager.isFirstLaunch()) {
            Log.d(TAG, "First app launch detected, initializing Firebase and collecting device info")
            
            // Create Firebase user first
            ensureFirebaseUser()
            
            // Store User UID in DeviceInfoManager
            val userUid = getCurrentUserId()
            if (userUid != null) {
                deviceInfoManager.setUserUid(userUid)
                Log.d(TAG, "Stored User UID in DeviceInfoManager: $userUid")
            } else {
                Log.e(TAG, "Failed to get User UID after authentication")
                throw Exception("Failed to authenticate user")
            }
        }
    }
    
    /**
     * Ensure Firebase user exists and User UID is stored in DeviceInfoManager
     * Called after consent is granted to ensure everything is set up
     */
    suspend fun ensureUserAuthenticated() {
        Log.d(TAG, "Ensuring user is authenticated and User UID is stored")
        
        // Create Firebase user if needed
        ensureFirebaseUser()
        
        // Store/update User UID in DeviceInfoManager
        val userUid = getCurrentUserId()
        if (userUid != null) {
            deviceInfoManager.setUserUid(userUid)
            Log.d(TAG, "User UID stored/updated in DeviceInfoManager: $userUid")
        } else {
            Log.e(TAG, "Failed to get User UID after authentication")
            throw Exception("Failed to authenticate user")
        }
    }
}

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val exception: Throwable) : Result<Nothing>()
}