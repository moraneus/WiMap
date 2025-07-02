package com.ner.wimap.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
    private var interstitialAd: InterstitialAd? = null
    private var currentActivity: Activity? = null
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val SCAN_COUNTER_KEY = "scan_count"
        private const val TAG = "InterstitialAdManager"
    }
    
    init {
        Log.d(TAG, "InterstitialAdManager initialized")
        loadInterstitialAd()
    }
    
    /**
     * Set the current activity for showing interstitial ads
     */
    fun setCurrentActivity(activity: Activity?) {
        currentActivity = activity
        Log.d(TAG, "Current activity set: ${activity?.javaClass?.simpleName}")
    }
    
    /**
     * Load the Interstitial Ad
     */
    private fun loadInterstitialAd() {
        Log.d(TAG, "Loading interstitial ad...")
        
        val adUnitId = if (BuildConfig.USE_TEST_ADS) {
            "ca-app-pub-3940256099942544/1033173712" // Test interstitial ad unit
        } else {
            "ca-app-pub-9891349918663384/5592311790" // Your production interstitial ad unit
        }
        
        Log.d(TAG, "Using ad unit: $adUnitId (test ads: ${BuildConfig.USE_TEST_ADS})")
        
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Failed to load interstitial ad: ${error.message}, code: ${error.code}")
                    interstitialAd = null
                }
            }
        )
    }
    
    /**
     * Show Ad Before Share/Save - Always shows ad for export actions
     */
    fun showAdThenRun(onContinue: () -> Unit) {
        val activity = currentActivity
        
        if (activity == null) {
            Log.e(TAG, "No current activity set for showing ad")
            onContinue()
            return
        }
        
        if (interstitialAd != null) {
            Log.d(TAG, "Showing interstitial ad before action")
            
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    interstitialAd = null
                    loadInterstitialAd() // Preload next ad
                    onContinue()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Failed to show interstitial ad: ${adError.message}")
                    interstitialAd = null
                    loadInterstitialAd() // Preload next ad
                    onContinue()
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed fullscreen content")
                }
            }

            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Ad not ready – proceeding without ad")
            loadInterstitialAd() // Try to load for next time
            onContinue()
        }
    }
    
    /**
     * Handle scan start - show ad every 3rd scan
     */
    fun onScanStarted(onContinue: () -> Unit) {
        val scanCount = sharedPreferences.getInt(SCAN_COUNTER_KEY, 0) + 1
        sharedPreferences.edit().putInt(SCAN_COUNTER_KEY, scanCount).apply()
        
        Log.d(TAG, "Scan count: $scanCount")
        
        if (scanCount % 3 == 0) {
            Log.d(TAG, "Showing ad for 3rd scan (#$scanCount)")
            showAdThenRun {
                onContinue()
            }
        } else {
            Log.d(TAG, "No ad for scan #$scanCount")
            onContinue()
        }
    }
    
    /**
     * Show interstitial ad for export/share actions
     */
    fun showAdForExport(onContinue: () -> Unit) {
        Log.d(TAG, "Export/Share action triggered - showing ad")
        showAdThenRun(onContinue)
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
        sharedPreferences.edit().putInt(SCAN_COUNTER_KEY, 0).apply()
        Log.d(TAG, "Scan counter reset to 0")
    }
    
    /**
     * Check if ad is ready
     */
    fun isAdReady(): Boolean {
        return interstitialAd != null
    }
    
    /**
     * Force preload ad (call from MainActivity startup)
     */
    fun preloadAd() {
        if (interstitialAd == null) {
            loadInterstitialAd()
        }
    }
}