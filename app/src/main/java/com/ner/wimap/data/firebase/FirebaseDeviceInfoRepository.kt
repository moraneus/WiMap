package com.ner.wimap.data.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ner.wimap.data.DeviceInfo
import com.ner.wimap.data.DeviceInfoManager
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseDeviceInfoRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val deviceInfoManager: DeviceInfoManager
) {
    companion object {
        private const val TAG = "FirebaseDeviceInfo"
        private const val COLLECTION_DEVICE_INFO = "device_info"
        private const val COLLECTION_USER_MAPPING = "user_adid_mapping"
    }
    
    /**
     * Upload device information to Firebase with User UID
     * Creates device_info collection (ADID-based) with User UID reference
     */
    suspend fun uploadDeviceInfo(deviceInfo: DeviceInfo): com.ner.wimap.Result<Unit> {
        return try {
            Log.d(TAG, "Uploading device info for ADID: ${deviceInfo.adid}, User UID: ${deviceInfo.userUid}")
            
            val deviceData = mapOf(
                "adid" to deviceInfo.adid,
                "user_uid" to deviceInfo.userUid,
                "device_model" to deviceInfo.deviceModel,
                "os_version" to deviceInfo.osVersion,
                "manufacturer" to deviceInfo.manufacturer,
                "app_version" to deviceInfo.appVersion,
                "limit_ad_tracking" to deviceInfo.limitAdTracking,
                "last_updated" to System.currentTimeMillis(),
                "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            // Upload to device_info collection (ADID-based) with User UID
            firestore.collection(COLLECTION_DEVICE_INFO)
                .document(deviceInfo.adid)
                .set(deviceData, SetOptions.merge())
                .await()
            
            // Also create/update the User UID to ADID mapping
            createUserMapping(deviceInfo.userUid, deviceInfo.adid)
            
            Log.d(TAG, "Device info uploaded successfully")
            com.ner.wimap.Result.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload device info", e)
            com.ner.wimap.Result.Failure(e)
        }
    }
    
    /**
     * Check if device info exists for current device (using ADID)
     */
    suspend fun deviceInfoExists(adid: String): com.ner.wimap.Result<Boolean> {
        return try {
            // Check in device_info collection (ADID-based)
            val adidDocument = firestore.collection(COLLECTION_DEVICE_INFO)
                .document(adid)
                .get()
                .await()
            
            com.ner.wimap.Result.Success(adidDocument.exists())
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check device info existence", e)
            com.ner.wimap.Result.Failure(e)
        }
    }
    
    /**
     * Update device info (e.g., when app version changes)
     */
    suspend fun updateDeviceInfo(adid: String, updates: Map<String, Any>): com.ner.wimap.Result<Unit> {
        return try {
            val userUid = deviceInfoManager.getUserUid() ?: throw Exception("Firebase User UID not available - user must be authenticated first")
            val updateData = updates.toMutableMap()
            updateData["last_updated"] = System.currentTimeMillis()
            updateData["user_uid"] = userUid
            updateData["adid"] = adid
            
            // Update device_info collection
            firestore.collection(COLLECTION_DEVICE_INFO)
                .document(adid)
                .update(updateData)
                .await()
            
            Log.d(TAG, "Device info updated successfully")
            com.ner.wimap.Result.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update device info", e)
            com.ner.wimap.Result.Failure(e)
        }
    }
    
    /**
     * Delete device info (for privacy/data deletion requests)
     */
    suspend fun deleteDeviceInfo(adid: String): com.ner.wimap.Result<Unit> {
        return try {
            // Delete from device_info collection
            firestore.collection(COLLECTION_DEVICE_INFO)
                .document(adid)
                .delete()
                .await()
            
            Log.d(TAG, "Device info deleted successfully")
            com.ner.wimap.Result.Success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete device info", e)
            com.ner.wimap.Result.Failure(e)
        }
    }
    
    /**
     * Create or update User UID to ADID mapping
     * This mapping table helps track which Firebase users correspond to which ADIDs
     */
    private suspend fun createUserMapping(userUid: String, adid: String) {
        try {
            val mappingData = mapOf(
                "user_uid" to userUid,
                "adid" to adid,
                "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "last_updated" to System.currentTimeMillis()
            )
            
            // Store mapping with User UID as document ID
            firestore.collection(COLLECTION_USER_MAPPING)
                .document(userUid)
                .set(mappingData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "User mapping created/updated: $userUid -> $adid")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create user mapping", e)
            // Don't throw - this is not critical for app functionality
        }
    }
    
    /**
     * Get ADID for a given User UID from mapping table
     */
    suspend fun getAdidForUser(userUid: String): com.ner.wimap.Result<String?> {
        return try {
            val document = firestore.collection(COLLECTION_USER_MAPPING)
                .document(userUid)
                .get()
                .await()
            
            val adid = document.getString("adid")
            com.ner.wimap.Result.Success(adid)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ADID for user: $userUid", e)
            com.ner.wimap.Result.Failure(e)
        }
    }
}