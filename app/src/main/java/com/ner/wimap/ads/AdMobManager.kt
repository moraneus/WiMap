package com.ner.wimap.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdMobManager {
    private const val TAG = "AdMobManager"
    
    // Ad Unit IDs
    private const val NATIVE_AD_UNIT_ID = "ca-app-pub-9891349918663384/3021988773"
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9891349918663384/5592311790"
    
    // For testing - use these IDs during development
    private const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    
    val nativeAdUnitId: String
        get() = if (com.ner.wimap.BuildConfig.USE_TEST_ADS) TEST_NATIVE_AD_UNIT_ID else NATIVE_AD_UNIT_ID
    
    private val interstitialAdUnitId: String
        get() = if (com.ner.wimap.BuildConfig.USE_TEST_ADS) TEST_INTERSTITIAL_AD_UNIT_ID else INTERSTITIAL_AD_UNIT_ID
    
    // Interstitial ad state
    private var interstitialAd: InterstitialAd? = null
    private val _isInterstitialLoading = MutableStateFlow(false)
    val isInterstitialLoading = _isInterstitialLoading.asStateFlow()
    
    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
        }
    }
    
    fun loadInterstitialAd(context: Context) {
        if (_isInterstitialLoading.value || interstitialAd != null) return
        
        _isInterstitialLoading.value = true
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    interstitialAd = ad
                    _isInterstitialLoading.value = false
                }
                
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${adError.message}")
                    interstitialAd = null
                    _isInterstitialLoading.value = false
                }
            }
        )
    }
    
    fun showInterstitialAd(
        activity: Activity,
        onAdDismissed: () -> Unit,
        onAdNotAvailable: () -> Unit
    ) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    interstitialAd = null
                    onAdDismissed()
                    // Preload the next ad
                    loadInterstitialAd(activity)
                }
                
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                    interstitialAd = null
                    onAdNotAvailable()
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed")
                }
            }
            ad.show(activity)
        } else {
            onAdNotAvailable()
        }
    }
}

