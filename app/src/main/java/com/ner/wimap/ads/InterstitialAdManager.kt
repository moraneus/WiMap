package com.ner.wimap.ads

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.*
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ner.wimap.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InterstitialAdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var currentActivity: androidx.activity.ComponentActivity? = null
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("wimap_ad_prefs", Context.MODE_PRIVATE)
    
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    
    companion object {
        private const val SCAN_COUNTER_KEY = "scan_counter"
        private const val SCANS_BETWEEN_ADS = 3
        private const val TAG = "InterstitialAdManager"
    }
    
    init {
        Log.d(TAG, "InterstitialAdManager initialized")
        Log.d(TAG, "Current scan count: ${getScanCount()}")
        preloadInterstitialAd()
    }
    
    /**
     * Set the current activity for showing interstitial ads
     */
    fun setCurrentActivity(activity: androidx.activity.ComponentActivity?) {
        currentActivity = activity
    }
    
    /**
     * Call this every time a scan starts
     */
    fun onScanStarted(onAdShown: () -> Unit = {}) {
        val currentCount = sharedPreferences.getInt(SCAN_COUNTER_KEY, 0)
        val newCount = currentCount + 1
        
        sharedPreferences.edit()
            .putInt(SCAN_COUNTER_KEY, newCount)
            .apply()
        
        Log.d(TAG, "Scan count: $newCount")
        
        // Show ad every 3 scans using modulo
        if (newCount % SCANS_BETWEEN_ADS == 0) {
            Log.d(TAG, "Showing interstitial ad after $newCount scans")
            showInterstitialAd(onAdShown)
        }
    }
    
    /**
     * Show interstitial ad for export/share actions
     */
    fun showAdForExport(onAdShown: () -> Unit = {}) {
        Log.d(TAG, "showAdForExport called - FORCING AD DISPLAY")
        // Force show ad for every export/share action
        showInterstitialAd(onAdShown)
    }
    
    /**
     * Force show interstitial ad for testing
     */
    fun forceShowAd(onAdShown: () -> Unit = {}) {
        Log.d(TAG, "forceShowAd called - FORCING AD DISPLAY FOR TEST")
        showInterstitialAd(onAdShown)
    }
    
    private fun showInterstitialAd(onAdShown: () -> Unit) {
        val ad = interstitialAd
        val activity = currentActivity
        
        if (ad != null && activity != null) {
            Log.d(TAG, "Showing interstitial ad")
            
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    interstitialAd = null
                    preloadInterstitialAd() // Load next ad
                    onAdShown()
                }
                
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Failed to show interstitial ad: ${adError.message}")
                    interstitialAd = null
                    preloadInterstitialAd()
                    onAdShown() // Continue with action even if ad fails
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad shown successfully")
                }
            }
            
            // Show the ad
            try {
                ad.show(activity)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing interstitial ad", e)
                interstitialAd = null
                preloadInterstitialAd()
                onAdShown() // Continue with action
            }
        } else {
            if (ad == null) {
                Log.d(TAG, "No interstitial ad available, preloading...")
                preloadInterstitialAd() // Try to load for next time
            }
            if (activity == null) {
                Log.e(TAG, "Cannot show interstitial ad: no current activity set")
            }
            onAdShown() // Continue with action
        }
    }
    
    private fun preloadInterstitialAd() {
        if (isLoading || interstitialAd != null) {
            Log.d(TAG, "Skip preload: isLoading=$isLoading, hasAd=${interstitialAd != null}")
            return
        }
        
        isLoading = true
        
        val adUnitId = if (BuildConfig.USE_TEST_ADS) {
            "ca-app-pub-3940256099942544/1033173712" // Test interstitial ad unit
        } else {
            "ca-app-pub-9891349918663384/9744756346" // Production interstitial ad unit  
        }
        
        Log.d(TAG, "Loading interstitial ad with unit ID: $adUnitId (test ads: ${BuildConfig.USE_TEST_ADS})")
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    isLoading = false
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Failed to load interstitial ad: ${loadAdError.message}, code: ${loadAdError.code}")
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }
    
    /**
     * Get current scan count for debugging
     */
    fun getScanCount(): Int {
        return sharedPreferences.getInt(SCAN_COUNTER_KEY, 0)
    }
    
    /**
     * Reset scan counter (for testing)
     */
    fun resetScanCounter() {
        sharedPreferences.edit()
            .putInt(SCAN_COUNTER_KEY, 0)
            .apply()
    }
}