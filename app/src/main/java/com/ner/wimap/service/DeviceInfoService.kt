package com.ner.wimap.service

import android.util.Log
import com.ner.wimap.data.DeviceInfoManager
import com.ner.wimap.data.firebase.FirebaseDeviceInfoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceInfoService @Inject constructor(
    private val deviceInfoManager: DeviceInfoManager,
    private val firebaseDeviceInfoRepository: FirebaseDeviceInfoRepository
) {
    companion object {
        private const val TAG = "DeviceInfoService"
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * Handle first launch device info collection and upload
     * Should be called after user grants consent
     */
    fun handleFirstLaunch() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Handling first launch device info collection")
                
                // Check if already collected
                if (deviceInfoManager.isDeviceInfoCollected()) {
                    Log.d(TAG, "Device info already collected, skipping")
                    return@launch
                }
                
                // Check acknowledgment
                if (!deviceInfoManager.hasAcknowledgedMandatoryCollection()) {
                    Log.d(TAG, "User has not acknowledged mandatory collection, skipping device info collection")
                    return@launch
                }
                
                // Collect mandatory device info
                val deviceInfo = try {
                    deviceInfoManager.collectDeviceInfo()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to collect mandatory device info", e)
                    return@launch
                }
                
                // Upload to Firebase
                val uploadResult = firebaseDeviceInfoRepository.uploadDeviceInfo(deviceInfo)
                when (uploadResult) {
                    is com.ner.wimap.Result.Success -> {
                        Log.d(TAG, "Device info uploaded successfully on first launch")
                    }
                    is com.ner.wimap.Result.Failure -> {
                        Log.e(TAG, "Failed to upload device info on first launch", uploadResult.exception)
                        // Don't mark as collected if upload fails - will retry next time
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in first launch device info handling", e)
            }
        }
    }
    
    /**
     * Check and update device info if needed (e.g., app version changed)
     */
    fun checkAndUpdateDeviceInfo() {
        serviceScope.launch {
            try {
                if (!deviceInfoManager.hasAcknowledgedMandatoryCollection()) {
                    return@launch
                }
                
                val cachedAdid = deviceInfoManager.getCachedAdid()
                if (cachedAdid == null) {
                    Log.d(TAG, "No cached ADID available")
                    return@launch
                }
                
                // Collect current device info
                val currentDeviceInfo = try {
                    deviceInfoManager.collectDeviceInfo(forceRefresh = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to collect current device info", e)
                    return@launch
                }
                
                // Check if device info exists in Firebase
                val existsResult = firebaseDeviceInfoRepository.deviceInfoExists(cachedAdid)
                when (existsResult) {
                    is com.ner.wimap.Result.Success -> {
                        val exists = existsResult.data
                        if (exists) {
                            // Update existing record
                            val updates = mapOf(
                                "app_version" to currentDeviceInfo.appVersion,
                                "device_model" to currentDeviceInfo.deviceModel,
                                "os_version" to currentDeviceInfo.osVersion,
                                "manufacturer" to currentDeviceInfo.manufacturer,
                                "limit_ad_tracking" to currentDeviceInfo.limitAdTracking
                            )
                            
                            when (val updateResult = firebaseDeviceInfoRepository.updateDeviceInfo(cachedAdid, updates)) {
                                is com.ner.wimap.Result.Success -> {
                                    Log.d(TAG, "Device info updated successfully")
                                }
                                is com.ner.wimap.Result.Failure -> {
                                    Log.e(TAG, "Failed to update device info", updateResult.exception)
                                }
                            }
                        } else {
                            // Create new record
                            when (val uploadResult = firebaseDeviceInfoRepository.uploadDeviceInfo(currentDeviceInfo)) {
                                is com.ner.wimap.Result.Success -> {
                                    Log.d(TAG, "Device info created successfully")
                                }
                                is com.ner.wimap.Result.Failure -> {
                                    Log.e(TAG, "Failed to create device info", uploadResult.exception)
                                }
                            }
                        }
                    }
                    is com.ner.wimap.Result.Failure -> {
                        Log.e(TAG, "Failed to check device info existence", existsResult.exception)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking and updating device info", e)
            }
        }
    }
    
    /**
     * Handle user acknowledgment of mandatory collection - collect and upload device info
     */
    fun handleMandatoryCollectionAcknowledged() {
        deviceInfoManager.setMandatoryCollectionAcknowledged()
        // Force upload after consent, even if device info was previously collected
        forceUploadDeviceInfo()
    }
    
    /**
     * Force upload device info after consent - bypasses the "already collected" check
     */
    private fun forceUploadDeviceInfo() {
        serviceScope.launch {
            try {
                Log.d(TAG, "Force uploading device info after consent acknowledgment")
                
                // Check acknowledgment
                if (!deviceInfoManager.hasAcknowledgedMandatoryCollection()) {
                    Log.d(TAG, "User has not acknowledged mandatory collection, cannot upload device info")
                    return@launch
                }
                
                // Collect mandatory device info (refresh ADID if needed)
                val deviceInfo = try {
                    deviceInfoManager.collectDeviceInfo(forceRefresh = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to collect mandatory device info for force upload", e)
                    return@launch
                }
                
                // Upload to Firebase
                val uploadResult = firebaseDeviceInfoRepository.uploadDeviceInfo(deviceInfo)
                when (uploadResult) {
                    is com.ner.wimap.Result.Success -> {
                        Log.d(TAG, "Device info force uploaded successfully after consent")
                    }
                    is com.ner.wimap.Result.Failure -> {
                        Log.e(TAG, "Failed to force upload device info after consent", uploadResult.exception)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in force upload device info handling", e)
            }
        }
    }
    
    /**
     * Handle user refusal of mandatory collection - app cannot function
     */
    fun handleMandatoryCollectionRefused() {
        deviceInfoManager.clearDeviceInfo()
        Log.d(TAG, "User refused mandatory collection, device info cleared")
    }
    
    /**
     * Handle data deletion request (for privacy compliance)
     */
    fun handleDataDeletionRequest() {
        serviceScope.launch {
            try {
                val cachedAdid = deviceInfoManager.getCachedAdid()
                if (cachedAdid != null) {
                    // Delete from Firebase
                    when (val deleteResult = firebaseDeviceInfoRepository.deleteDeviceInfo(cachedAdid)) {
                        is com.ner.wimap.Result.Success -> {
                            Log.d(TAG, "Device info deleted from Firebase")
                        }
                        is com.ner.wimap.Result.Failure -> {
                            Log.e(TAG, "Failed to delete device info from Firebase", deleteResult.exception)
                        }
                    }
                }
                
                // Clear local data
                deviceInfoManager.clearAllData()
                Log.d(TAG, "All device info data cleared locally")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling data deletion request", e)
            }
        }
    }
}