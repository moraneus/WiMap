package com.ner.wimap.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Ad Cache Manager
 * Pre-loads and caches multiple native ads for instant display
 */
@Singleton
class NativeAdCache @Inject constructor(
    private val adManager: AdManager
) {
    companion object {
        private const val TAG = "NativeAdCache"
        private const val CACHE_SIZE = 5 // Number of ads to pre-load
        private const val MIN_CACHE_SIZE = 2 // Minimum before reloading
    }
    
    // Thread-safe queue for cached ads
    private val adQueue = ConcurrentLinkedQueue<NativeAd>()
    
    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Cache status
    private val _cacheSize = MutableStateFlow(0)
    val cacheSize: StateFlow<Int> = _cacheSize.asStateFlow()
    
    private var isInitialized = false
    
    /**
     * Initialize the cache and pre-load ads
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        lastContext = context.applicationContext
        
        Log.d(TAG, "Initializing native ad cache")
        loadAdBatch(context)
    }
    
    private var lastContext: Context? = null
    
    /**
     * Get a cached native ad if available
     * Returns null if no ads are available
     */
    fun getAd(): NativeAd? {
        val ad = adQueue.poll()
        _cacheSize.value = adQueue.size
        
        if (ad != null) {
            Log.d(TAG, "Provided cached ad. Remaining: ${adQueue.size}")
            
            // Reload if cache is getting low
            if (adQueue.size < MIN_CACHE_SIZE && !_isLoading.value && lastContext != null) {
                Log.d(TAG, "Cache running low, loading more ads")
                loadAdBatch(lastContext!!)
            }
        } else {
            Log.w(TAG, "No cached ads available")
        }
        
        return ad
    }
    
    /**
     * Pre-load a batch of native ads
     */
    private fun loadAdBatch(context: Context) {
        if (_isLoading.value) return
        
        _isLoading.value = true
        val adsToLoad = CACHE_SIZE - adQueue.size
        
        Log.d(TAG, "Loading batch of $adsToLoad native ads")
        
        CoroutineScope(Dispatchers.IO).launch {
            repeat(adsToLoad) {
                loadSingleAd(context)
            }
        }
    }
    
    /**
     * Load a single native ad
     */
    private fun loadSingleAd(context: Context) {
        val adLoader = AdLoader.Builder(context, adManager.getNativeAdUnitId())
            .forNativeAd { nativeAd ->
                adQueue.offer(nativeAd)
                _cacheSize.value = adQueue.size
                Log.d(TAG, "Native ad loaded and cached. Total cached: ${adQueue.size}")
                
                // Set lifecycle callback to properly destroy ad when removed
                nativeAd.setOnPaidEventListener { adValue ->
                    Log.d(TAG, "Native ad generated revenue: ${adValue.valueMicros}")
                }
                
                // Check if we've loaded enough
                if (adQueue.size >= CACHE_SIZE) {
                    _isLoading.value = false
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Failed to load native ad: ${adError.message}")
                    
                    // Check if we should stop trying
                    if (adQueue.isEmpty()) {
                        _isLoading.value = false
                    }
                }
                
                override fun onAdClicked() {
                    Log.d(TAG, "Native ad clicked")
                }
                
                override fun onAdImpression() {
                    Log.d(TAG, "Native ad impression")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setRequestMultipleImages(true)
                    .build()
            )
            .build()
        
        adLoader.loadAd(AdRequest.Builder().build())
    }
    
    /**
     * Refresh the cache - useful when returning to a screen
     */
    fun refreshCache(context: Context) {
        if (adQueue.size < MIN_CACHE_SIZE && !_isLoading.value) {
            loadAdBatch(context)
        }
    }
    
    /**
     * Clear all cached ads (call this when the app is destroyed)
     */
    fun clearCache() {
        Log.d(TAG, "Clearing native ad cache")
        while (adQueue.isNotEmpty()) {
            adQueue.poll()?.destroy()
        }
        _cacheSize.value = 0
        isInitialized = false
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        return "NativeAdCache[size=${adQueue.size}, loading=$_isLoading, initialized=$isInitialized]"
    }
}