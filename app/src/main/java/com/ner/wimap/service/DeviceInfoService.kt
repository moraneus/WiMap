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
                
                // Check consent
                if (!deviceInfoManager.hasConsentGranted()) {
                    Log.d(TAG, "User consent not granted, skipping device info collection")
                    return@launch
                }
                
                // Collect device info
                val deviceInfo = deviceInfoManager.collectDeviceInfo()
                if (deviceInfo == null) {
                    Log.e(TAG, "Failed to collect device info")
                    return@launch
                }
                
                // Upload to Firebase
                val uploadResult = firebaseDeviceInfoRepository.uploadDeviceInfo(deviceInfo)
                uploadResult.fold(
                    onSuccess = {
                        Log.d(TAG, "Device info uploaded successfully on first launch")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to upload device info on first launch", error)
                        // Don't mark as collected if upload fails - will retry next time
                    }
                )
                
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
                if (!deviceInfoManager.hasConsentGranted()) {
                    return@launch
                }
                
                val cachedAdid = deviceInfoManager.getCachedAdid()
                if (cachedAdid == null) {
                    Log.d(TAG, "No cached ADID available")
                    return@launch
                }
                
                // Collect current device info
                val currentDeviceInfo = deviceInfoManager.collectDeviceInfo(forceRefresh = true)
                if (currentDeviceInfo == null) {
                    Log.e(TAG, "Failed to collect current device info")
                    return@launch
                }
                
                // Check if device info exists in Firebase
                val existsResult = firebaseDeviceInfoRepository.deviceInfoExists(cachedAdid)
                existsResult.fold(
                    onSuccess = { exists ->
                        if (exists) {
                            // Update existing record
                            val updates = mapOf(
                                "app_version" to currentDeviceInfo.appVersion,
                                "device_model" to currentDeviceInfo.deviceModel,
                                "os_version" to currentDeviceInfo.osVersion,
                                "manufacturer" to currentDeviceInfo.manufacturer,
                                "limit_ad_tracking" to currentDeviceInfo.limitAdTracking
                            )
                            
                            firebaseDeviceInfoRepository.updateDeviceInfo(cachedAdid, updates)
                                .fold(
                                    onSuccess = {
                                        Log.d(TAG, "Device info updated successfully")
                                    },
                                    onFailure = { error ->
                                        Log.e(TAG, "Failed to update device info", error)
                                    }
                                )
                        } else {
                            // Create new record
                            firebaseDeviceInfoRepository.uploadDeviceInfo(currentDeviceInfo)
                                .fold(
                                    onSuccess = {
                                        Log.d(TAG, "Device info created successfully")
                                    },
                                    onFailure = { error ->
                                        Log.e(TAG, "Failed to create device info", error)
                                    }
                                )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to check device info existence", error)
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking and updating device info", e)
            }
        }
    }
    
    /**
     * Handle user consent granted - collect and upload device info
     */
    fun handleConsentGranted() {
        deviceInfoManager.setConsentGranted(true)
        handleFirstLaunch()
    }
    
    /**
     * Handle user consent denied - clear any cached data
     */
    fun handleConsentDenied() {
        deviceInfoManager.setConsentGranted(false)
        deviceInfoManager.clearDeviceInfo()
        Log.d(TAG, "User consent denied, device info cleared")
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
                    firebaseDeviceInfoRepository.deleteDeviceInfo(cachedAdid)
                        .fold(
                            onSuccess = {
                                Log.d(TAG, "Device info deleted from Firebase")
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to delete device info from Firebase", error)
                            }
                        )
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