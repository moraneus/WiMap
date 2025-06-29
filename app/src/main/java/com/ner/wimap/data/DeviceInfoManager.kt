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
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceInfo(
    val adid: String,
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
     * Check if user has granted consent for ADID collection
     */
    fun hasConsentGranted(): Boolean {
        return prefs.getBoolean(KEY_CONSENT_GRANTED, false)
    }
    
    /**
     * Set user consent for ADID collection
     * Only stores positive consent - denial is not stored permanently
     */
    fun setConsentGranted(granted: Boolean) {
        if (granted) {
            prefs.edit().putBoolean(KEY_CONSENT_GRANTED, true).apply()
        }
        // If denied, we don't store anything so dialog will show again next time
    }
    
    /**
     * Check if we should show the consent dialog
     * Shows dialog if consent was never granted
     */
    fun shouldShowConsentDialog(): Boolean {
        return !hasConsentGranted()
    }
    
    /**
     * Get cached ADID (if available and user consented)
     */
    fun getCachedAdid(): String? {
        if (!hasConsentGranted()) return null
        return prefs.getString(KEY_CACHED_ADID, null)
    }
    
    /**
     * Collect device information including ADID
     * @param forceRefresh - Force collection even if already collected
     * @return DeviceInfo or null if consent not granted or collection failed
     */
    suspend fun collectDeviceInfo(forceRefresh: Boolean = false): DeviceInfo? {
        return withContext(Dispatchers.IO) {
            try {
                // Check if we need to collect
                if (!forceRefresh && isDeviceInfoCollected() && !hasConsentGranted()) {
                    Log.d(TAG, "Device info already collected or consent not granted")
                    return@withContext null
                }
                
                // Check consent
                if (!hasConsentGranted()) {
                    Log.d(TAG, "User consent not granted for ADID collection")
                    return@withContext null
                }
                
                Log.d(TAG, "Collecting device information...")
                
                // Collect ADID
                val adidInfo = try {
                    AdvertisingIdClient.getAdvertisingIdInfo(context)
                } catch (e: GooglePlayServicesNotAvailableException) {
                    Log.e(TAG, "Google Play Services not available", e)
                    return@withContext null
                } catch (e: GooglePlayServicesRepairableException) {
                    Log.e(TAG, "Google Play Services repairable error", e)
                    return@withContext null
                } catch (e: IOException) {
                    Log.e(TAG, "IO exception getting advertising ID", e)
                    return@withContext null
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error getting advertising ID", e)
                    return@withContext null
                }
                
                // Check if user has limited ad tracking
                if (adidInfo.isLimitAdTrackingEnabled) {
                    Log.d(TAG, "User has limited ad tracking enabled")
                    // Still collect other device info, but mark limit ad tracking
                }
                
                val adid = if (adidInfo.isLimitAdTrackingEnabled) {
                    "00000000-0000-0000-0000-000000000000" // Use null ADID if tracking limited
                } else {
                    adidInfo.id ?: "unknown"
                }
                
                // Cache the ADID
                prefs.edit()
                    .putString(KEY_CACHED_ADID, adid)
                    .putBoolean(KEY_LIMIT_AD_TRACKING, adidInfo.isLimitAdTrackingEnabled)
                    .apply()
                
                // Collect other device info
                val deviceInfo = DeviceInfo(
                    adid = adid,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    manufacturer = Build.MANUFACTURER,
                    appVersion = BuildConfig.VERSION_NAME,
                    limitAdTracking = adidInfo.isLimitAdTrackingEnabled
                )
                
                // Mark as collected
                prefs.edit().putBoolean(KEY_ADID_COLLECTED, true).apply()
                
                Log.d(TAG, "Device info collected successfully: model=${deviceInfo.deviceModel}, " +
                        "os=${deviceInfo.osVersion}, limitTracking=${deviceInfo.limitAdTracking}")
                
                deviceInfo
                
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting device info", e)
                null
            }
        }
    }
    
    /**
     * Get device info without ADID (for cases where consent is not granted)
     */
    fun getBasicDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            adid = "consent_not_granted",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            manufacturer = Build.MANUFACTURER,
            appVersion = BuildConfig.VERSION_NAME,
            limitAdTracking = true
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
            .apply()
        Log.d(TAG, "Device info cleared")
    }
    
    /**
     * Clear all preferences including consent
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All device info data cleared")
    }
}