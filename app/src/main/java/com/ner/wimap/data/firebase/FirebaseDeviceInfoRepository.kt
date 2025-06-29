package com.ner.wimap.data.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ner.wimap.data.DeviceInfo
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseDeviceInfoRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "FirebaseDeviceInfo"
        private const val COLLECTION_DEVICE_INFO = "device_info"
    }
    
    /**
     * Upload device information to Firebase
     * Uses ADID as document ID
     */
    suspend fun uploadDeviceInfo(deviceInfo: DeviceInfo): Result<Unit> {
        return try {
            Log.d(TAG, "Uploading device info for ADID: ${deviceInfo.adid}")
            
            val deviceData = mapOf(
                "device_model" to deviceInfo.deviceModel,
                "os_version" to deviceInfo.osVersion,
                "manufacturer" to deviceInfo.manufacturer,
                "app_version" to deviceInfo.appVersion,
                "limit_ad_tracking" to deviceInfo.limitAdTracking,
                "last_updated" to System.currentTimeMillis(),
                "created_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            // Use ADID as document ID, merge to avoid overwriting existing data
            firestore.collection(COLLECTION_DEVICE_INFO)
                .document(deviceInfo.adid)
                .set(deviceData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Device info uploaded successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload device info", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if device info exists for given ADID
     */
    suspend fun deviceInfoExists(adid: String): Result<Boolean> {
        return try {
            val document = firestore.collection(COLLECTION_DEVICE_INFO)
                .document(adid)
                .get()
                .await()
            
            Result.success(document.exists())
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check device info existence", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update device info (e.g., when app version changes)
     */
    suspend fun updateDeviceInfo(adid: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            val updateData = updates.toMutableMap()
            updateData["last_updated"] = System.currentTimeMillis()
            
            firestore.collection(COLLECTION_DEVICE_INFO)
                .document(adid)
                .update(updateData)
                .await()
            
            Log.d(TAG, "Device info updated successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update device info", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete device info (for privacy/data deletion requests)
     */
    suspend fun deleteDeviceInfo(adid: String): Result<Unit> {
        return try {
            firestore.collection(COLLECTION_DEVICE_INFO)
                .document(adid)
                .delete()
                .await()
            
            Log.d(TAG, "Device info deleted successfully")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete device info", e)
            Result.failure(e)
        }
    }
}