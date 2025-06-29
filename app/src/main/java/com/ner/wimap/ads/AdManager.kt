package com.ner.wimap.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.ner.wimap.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor() {
    
    companion object {
        private const val TAG = "AdManager"
        
        // Test ad unit IDs (for development)
        private const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        
        // Production ad unit IDs (replace with your actual ad unit IDs)
        private const val PROD_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110" // TODO: Replace with real ad unit
        private const val PROD_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712" // TODO: Replace with real ad unit
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownTime = 0L
    private val interstitialCooldownMs = 5 * 60 * 1000L // 5 minutes cooldown
    
    /**
     * Initialize AdMob SDK
     */
    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Using test ads for debug build")
        }
        
        // Preload interstitial ad
        loadInterstitialAd(context)
    }
    
    /**
     * Get the appropriate native ad unit ID based on build type
     */
    fun getNativeAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            TEST_NATIVE_AD_UNIT_ID
        } else {
            PROD_NATIVE_AD_UNIT_ID
        }
    }
    
    /**
     * Get the appropriate interstitial ad unit ID based on build type
     */
    private fun getInterstitialAdUnitId(): String {
        return if (BuildConfig.DEBUG) {
            TEST_INTERSTITIAL_AD_UNIT_ID
        } else {
            PROD_INTERSTITIAL_AD_UNIT_ID
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
            
            adLoader.loadAd(AdRequest.Builder().build())
        }
    }
    
    /**
     * Load interstitial ad in background
     */
    private fun loadInterstitialAd(context: Context) {
        if (isInterstitialLoading || interstitialAd != null) {
            return
        }
        
        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            getInterstitialAdUnitId(),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${adError.message}")
                    interstitialAd = null
                    isInterstitialLoading = false
                }
                
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    isInterstitialLoading = false
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            Log.d(TAG, "Interstitial ad was clicked")
                        }
                        
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Interstitial ad dismissed")
                            interstitialAd = null
                            lastInterstitialShownTime = System.currentTimeMillis()
                            // Preload next ad
                            loadInterstitialAd(context)
                        }
                        
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                            interstitialAd = null
                        }
                        
                        override fun onAdImpression() {
                            Log.d(TAG, "Interstitial ad recorded an impression")
                        }
                        
                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Interstitial ad showed fullscreen content")
                        }
                    }
                }
            }
        )
    }
    
    /**
     * Show interstitial ad before key actions (export, share)
     * @deprecated Use showFullScreenTimedAd instead for better UX
     */
    @Deprecated("Use showFullScreenTimedAd instead")
    fun showInterstitialAdBeforeAction(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onAdNotAvailable: () -> Unit
    ) {
        // Fallback to new timed ad system
        showFullScreenTimedAd(onAdDismissed, onAdNotAvailable)
    }
    
    /**
     * Show full-screen timed ad with skip functionality (new implementation)
     */
    fun showFullScreenTimedAd(
        onAdCompleted: () -> Unit,
        onAdNotAvailable: () -> Unit
    ): Boolean {
        val currentTime = System.currentTimeMillis()
        
        // Check cooldown period
        if (currentTime - lastInterstitialShownTime < interstitialCooldownMs) {
            Log.d(TAG, "Full-screen ad in cooldown period, proceeding without ad")
            onAdNotAvailable()
            return false
        }
        
        Log.d(TAG, "Showing full-screen timed ad")
        lastInterstitialShownTime = currentTime
        return true
    }
    
    /**
     * Check if we should show native ads
     */
    fun shouldShowNativeAd(index: Int): Boolean {
        // Show native ad after every 5-7 cards (starting from index 4)
        return index > 0 && (index + 1) % 6 == 0
    }
    
    /**
     * Preload interstitial ad for better user experience
     */
    fun preloadInterstitialAd(context: Context) {
        if (interstitialAd == null && !isInterstitialLoading) {
            loadInterstitialAd(context)
        }
    }
}