package com.ner.wimap.ads

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.ner.wimap.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdInitializer @Inject constructor() {
    
    companion object {
        private const val TAG = "AdInitializer"
    }
    
    fun initialize(application: Application) {
        // Configure test devices for development
        if (BuildConfig.DEBUG) {
            val testDeviceIds = listOf(
                "33BE2250B43518CCDA7DE426D04EE231" // Add your device ID here for testing
            )
            
            val configuration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            
            MobileAds.setRequestConfiguration(configuration)
            Log.d(TAG, "AdMob configured for testing with test device IDs")
        }
        
        // Initialize AdMob
        MobileAds.initialize(application) { initializationStatus ->
            val statusMap = initializationStatus.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                Log.d(TAG, "Adapter name: $adapterClass, " +
                        "Description: ${status?.description}, " +
                        "Latency: ${status?.latency}")
            }
            Log.i(TAG, "AdMob initialization completed")
        }
    }
}