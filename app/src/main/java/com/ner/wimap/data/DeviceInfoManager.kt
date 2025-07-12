package com.ner.wimap.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.ner.wimap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceInfo(
    val adid: String,
    val userUid: String,
    val deviceModel: String,
    val osVersion: String,
    val manufacturer: String,
    val appVersion: String,
    val limitAdTracking: Boolean = false
)

@Singleton
class DeviceInfoManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "DeviceInfoManager"
        private const val PREFS_NAME = "device_info_prefs"
        private const val KEY_ADID_COLLECTED = "adid_collected"
        private const val KEY_CONSENT_GRANTED = "consent_granted"
        private const val KEY_CACHED_ADID = "cached_adid"
        private const val KEY_LIMIT_AD_TRACKING = "limit_ad_tracking"
        private const val KEY_USER_UID = "firebase_user_uid"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Check if device info has already been collected
     */
    fun isDeviceInfoCollected(): Boolean {
        return prefs.getBoolean(KEY_ADID_COLLECTED, false)
    }
    
    /**
     * Check if user has acknowledged mandatory ADID collection
     */
    fun hasAcknowledgedMandatoryCollection(): Boolean {
        return prefs.getBoolean(KEY_CONSENT_GRANTED, false)
    }
    
    /**
     * Set user acknowledgment for mandatory ADID collection
     */
    fun setMandatoryCollectionAcknowledged() {
        prefs.edit().putBoolean(KEY_CONSENT_GRANTED, true).apply()
    }
    
    /**
     * Check if we should show the mandatory collection notice
     */
    fun shouldShowMandatoryCollectionNotice(): Boolean {
        return !hasAcknowledgedMandatoryCollection()
    }
    
    /**
     * Get cached ADID (mandatory for app functionality)
     */
    fun getCachedAdid(): String? {
        return prefs.getString(KEY_CACHED_ADID, null)
    }
    
    /**
     * Collect device information including ADID
     * ADID collection is mandatory for app functionality
     * @param forceRefresh - Force collection even if already collected
     * @return DeviceInfo or throws exception if collection failed
     */
    suspend fun collectDeviceInfo(forceRefresh: Boolean = false): DeviceInfo {
        return withContext(Dispatchers.IO) {
            try {
                // Check if we need to collect
                if (!forceRefresh && isDeviceInfoCollected()) {
                    // Return cached info if available
                    val cachedAdid = getCachedAdid()
                    if (cachedAdid != null) {
                        // Get Firebase User UID (required)
                        val userUid = getUserUid() ?: throw Exception("Firebase User UID not available - user must be authenticated first")
                        
                        return@withContext DeviceInfo(
                            adid = cachedAdid,
                            userUid = userUid,
                            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                            manufacturer = Build.MANUFACTURER,
                            appVersion = BuildConfig.VERSION_NAME,
                            limitAdTracking = prefs.getBoolean(KEY_LIMIT_AD_TRACKING, false)
                        )
                    } else {
                        // Cached info exists but no ADID - force refresh
                        Log.d(TAG, "Device info collected but ADID missing - forcing refresh")
                    }
                }
                
                Log.d(TAG, "Collecting mandatory device information including ADID...")
                
                // Collect ADID
                val adidInfo = try {
                    AdvertisingIdClient.getAdvertisingIdInfo(context)
                } catch (e: GooglePlayServicesNotAvailableException) {
                    Log.e(TAG, "Google Play Services not available", e)
                    throw Exception("Google Play Services required for device identification", e)
                } catch (e: GooglePlayServicesRepairableException) {
                    Log.e(TAG, "Google Play Services repairable error", e)
                    throw Exception("Google Play Services needs to be updated for device identification", e)
                } catch (e: IOException) {
                    Log.e(TAG, "IO exception getting advertising ID", e)
                    throw Exception("Network error during device identification", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error getting advertising ID", e)
                    throw Exception("Failed to obtain device identification", e)
                }
                
                // Check if user has limited ad tracking
                if (adidInfo.isLimitAdTrackingEnabled) {
                    Log.d(TAG, "User has limited ad tracking enabled")
                    // Still collect other device info, but mark limit ad tracking
                }
                
                // Always collect ADID as it's essential for app functionality
                val adid = adidInfo.id ?: throw Exception("Failed to obtain ADID - required for app functionality")
                
                // Cache the ADID
                prefs.edit()
                    .putString(KEY_CACHED_ADID, adid)
                    .putBoolean(KEY_LIMIT_AD_TRACKING, adidInfo.isLimitAdTrackingEnabled)
                    .apply()
                
                // Get Firebase User UID (required)
                val userUid = getUserUid() ?: throw Exception("Firebase User UID not available - user must be authenticated first")
                
                // Collect other device info
                val deviceInfo = DeviceInfo(
                    adid = adid,
                    userUid = userUid,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    manufacturer = Build.MANUFACTURER,
                    appVersion = BuildConfig.VERSION_NAME,
                    limitAdTracking = adidInfo.isLimitAdTrackingEnabled
                )
                
                // Mark as collected
                prefs.edit().putBoolean(KEY_ADID_COLLECTED, true).apply()
                
                Log.d(TAG, "Mandatory device info collected successfully: model=${deviceInfo.deviceModel}, " +
                        "os=${deviceInfo.osVersion}, adid=$adid, limitTracking=${deviceInfo.limitAdTracking}")
                
                deviceInfo
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to collect mandatory device info", e)
                throw Exception("Device identification is required for app functionality: ${e.message}", e)
            }
        }
    }
    
    /**
     * Get device info with mandatory ADID and User UID
     * @throws Exception if ADID or User UID is not available
     */
    fun getMandatoryDeviceInfo(): DeviceInfo {
        val adid = getCachedAdid() ?: throw Exception("ADID not available - required for app functionality")
        val userUid = getUserUid() ?: throw Exception("Firebase User UID not available - user must be authenticated first")
        return DeviceInfo(
            adid = adid,
            userUid = userUid,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            manufacturer = Build.MANUFACTURER,
            appVersion = BuildConfig.VERSION_NAME,
            limitAdTracking = prefs.getBoolean(KEY_LIMIT_AD_TRACKING, false)
        )
    }
    
    /**
     * Clear collected device info (for privacy/reset purposes)
     */
    fun clearDeviceInfo() {
        prefs.edit()
            .remove(KEY_ADID_COLLECTED)
            .remove(KEY_CACHED_ADID)
            .remove(KEY_LIMIT_AD_TRACKING)
            .remove(KEY_USER_UID)
            .apply()
        Log.d(TAG, "Device info cleared")
    }
    
    /**
     * Store Firebase User UID for device identification
     */
    fun setUserUid(userUid: String) {
        prefs.edit().putString(KEY_USER_UID, userUid).apply()
        Log.d(TAG, "Stored Firebase User UID: $userUid")
    }
    
    /**
     * Get stored Firebase User UID
     */
    fun getUserUid(): String? {
        return prefs.getString(KEY_USER_UID, null)
    }
    
    /**
     * Check if this is the first time the app is launched (no User UID exists)
     */
    fun isFirstLaunch(): Boolean {
        return prefs.getString(KEY_USER_UID, null) == null
    }
    
    /**
     * Clear all preferences including consent
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All device info data cleared")
    }
}