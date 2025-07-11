package com.ner.wimap.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import android.content.SharedPreferences
import com.ner.wimap.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    private val interstitialAdManager: InterstitialAdManager,
    private val sharedPreferences: SharedPreferences
) {
    
    companion object {
        private const val TAG = "AdManager"
        
        // Test ad unit IDs (for development)
        private const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        
        // Production ad unit IDs
        private const val PROD_NATIVE_AD_UNIT_ID = "ca-app-pub-9891349918663384/3021988773"
        private const val PROD_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9891349918663384/5592311790"
        private const val PROD_BANNER_AD_UNIT_ID = "ca-app-pub-9891349918663384/8828865130"
        private const val PROD_PINNED_BANNER_AD_UNIT_ID = "ca-app-pub-9891349918663384/1975149340"
        
        // GDPR consent preferences
        private const val KEY_PERSONALIZED_ADS_ENABLED = "personalized_ads_enabled"
    }
    
    // Removed duplicate interstitial ad handling - now handled by InterstitialAdManager
    
    /**
     * Initialize AdMob SDK
     */
    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Using test ads for debug build")
            // Configure test device
            val testDeviceIds = listOf("DD3E8A6B792D084D9CF7831C70581CCB")
            val configuration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(configuration)
            Log.d(TAG, "Test device configured: $testDeviceIds")
        }
    }
    
    /**
     * Get the appropriate native ad unit ID based on build type
     */
    fun getNativeAdUnitId(): String {
        return if (BuildConfig.USE_TEST_ADS) {
            TEST_NATIVE_AD_UNIT_ID
        } else {
            PROD_NATIVE_AD_UNIT_ID
        }
    }
    
    /**
     * Get the appropriate banner ad unit ID based on build type
     */
    fun getBannerAdUnitId(): String {
        return if (BuildConfig.USE_TEST_ADS) {
            TEST_BANNER_AD_UNIT_ID
        } else {
            PROD_BANNER_AD_UNIT_ID
        }
    }
    
    /**
     * Get the appropriate pinned screen banner ad unit ID based on build type
     */
    fun getPinnedBannerAdUnitId(): String {
        return if (BuildConfig.USE_TEST_ADS) {
            TEST_BANNER_AD_UNIT_ID
        } else {
            PROD_PINNED_BANNER_AD_UNIT_ID
        }
    }
    
    
    /**
     * Load a native ad
     */
    fun loadNativeAd(
        context: Context,
        onAdLoaded: (NativeAd) -> Unit,
        onAdFailedToLoad: (LoadAdError) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val adLoader = AdLoader.Builder(context, getNativeAdUnitId())
                .forNativeAd { nativeAd ->
                    Log.d(TAG, "Native ad loaded successfully")
                    onAdLoaded(nativeAd)
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        Log.e(TAG, "Native ad failed to load: ${adError.message}")
                        onAdFailedToLoad(adError)
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )
                .build()
            
            adLoader.loadAd(buildAdRequest())
        }
    }
    
    
    
    /**
     * Check if we should show native ads
     */
    fun shouldShowNativeAd(index: Int): Boolean {
        // Show native ad after every 5-7 cards (starting from index 4)
        return index > 0 && (index + 1) % 6 == 0
    }
    
    
    /**
     * Call this when scan starts - shows ad every 3 scans
     */
    fun onScanStarted(onContinue: () -> Unit) {
        Log.d(TAG, "AdManager.onScanStarted called")
        interstitialAdManager.onScanStarted(onContinue)
    }
    
    /**
     * Show interstitial ad for export/share actions
     */
    fun showAdForExport(onContinue: () -> Unit) {
        Log.d(TAG, "AdManager.showAdForExport called")
        interstitialAdManager.showAdForExport(onContinue)
    }
    
    /**
     * Get current scan count for debugging
     */
    fun getScanCount(): Int {
        return interstitialAdManager.getScanCount()
    }
    
    /**
     * Reset scan counter for testing
     */
    fun resetScanCounter() {
        interstitialAdManager.resetScanCounter()
    }
    
    /**
     * Set the current activity for showing interstitial ads
     */
    fun setCurrentActivity(activity: androidx.activity.ComponentActivity?) {
        interstitialAdManager.setCurrentActivity(activity as? Activity)
    }
    
    /**
     * Preload interstitial ad on app startup
     */
    fun preloadInterstitialAd(context: Context) {
        interstitialAdManager.preloadAd()
    }
    
    /**
     * Trigger banner ad reload (for lifecycle management)
     */
    fun reloadBannerAds() {
        Log.d(TAG, "Triggering banner ad reload")
        // This will trigger re-composition of banner ads which will reload them
    }
    
    /**
     * Set personalized ads enabled/disabled based on GDPR consent
     */
    fun setPersonalizedAdsEnabled(enabled: Boolean) {
        Log.d(TAG, "Setting personalized ads enabled: $enabled")
        sharedPreferences.edit()
            .putBoolean(KEY_PERSONALIZED_ADS_ENABLED, enabled)
            .apply()
        
        // Update all future ad requests
        interstitialAdManager.setPersonalizedAdsEnabled(enabled)
    }
    
    /**
     * Check if personalized ads are enabled
     */
    fun isPersonalizedAdsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PERSONALIZED_ADS_ENABLED, false)
    }
    
    /**
     * Build AdRequest with consent settings
     */
    private fun buildAdRequest(): AdRequest {
        val builder = AdRequest.Builder()
        
        // Add non-personalized ads parameter if personalized ads are disabled
        if (!isPersonalizedAdsEnabled()) {
            // Use the simplified approach for non-personalized ads
            builder.addKeyword("npa")
        }
        
        return builder.build()
    }
}