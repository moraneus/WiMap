package com.ner.wimap.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.ner.wimap.BuildConfig
import dagger.hilt.android.EntryPointAccessors

/**
 * Banner Ad View for map screen bottom and pinned screen
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    isPinnedScreen: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var adView by remember { mutableStateOf<AdView?>(null) }
    var isAdLoaded by remember { mutableStateOf(false) }
    var adLoadError by remember { mutableStateOf<String?>(null) }
    
    // Get AdManager instance
    val adManager = remember {
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.ner.wimap.ads.AdManagerEntryPoint::class.java
        )
        hiltEntryPoint.adManager()
    }
    
    val adUnitId = if (isPinnedScreen) {
        adManager.getPinnedBannerAdUnitId()
    } else {
        adManager.getBannerAdUnitId()
    }
    
    // Track if we need to reload the ad
    var shouldReloadAd by remember { mutableStateOf(false) }
    
    // Force reload if ad doesn't load within reasonable time and periodically check
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000) // Check every 5 seconds
            if (!isAdLoaded) {
                Log.d("BannerAdView", "Banner ad not loaded, triggering reload - error: $adLoadError")
                shouldReloadAd = true
            }
        }
    }
    
    // Handle lifecycle events
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d("BannerAdView", "ON_RESUME - resuming AdView and checking if reload needed")
                    adView?.resume()
                    // If the ad failed to load or disappeared, trigger reload
                    if (!isAdLoaded || adLoadError != null) {
                        Log.d("BannerAdView", "Ad not loaded on resume, triggering reload")
                        shouldReloadAd = true
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("BannerAdView", "ON_PAUSE - pausing AdView")
                    adView?.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView?.destroy()
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp) // Always reserve space for banner ad
            .shadow(if (isAdLoaded) 4.dp else 2.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    Log.d("BannerAdView", "Creating AdView with unit ID: $adUnitId")
                    AdView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setAdSize(AdSize.BANNER)
                        setAdUnitId(adUnitId)
                        
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdLoaded() {
                                super.onAdLoaded()
                                Log.d("BannerAdView", "Banner ad loaded successfully")
                                isAdLoaded = true
                                adLoadError = null
                            }
                            
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                super.onAdFailedToLoad(error)
                                Log.e("BannerAdView", "Banner ad failed to load: ${error.message} (Code: ${error.code})")
                                isAdLoaded = false
                                adLoadError = error.message
                            }
                            
                            override fun onAdClicked() {
                                super.onAdClicked()
                                Log.d("BannerAdView", "Banner ad clicked")
                            }
                            
                            override fun onAdImpression() {
                                super.onAdImpression()
                                Log.d("BannerAdView", "Banner ad impression")
                            }
                        }
                        
                        // Load the ad
                        val adRequest = AdRequest.Builder().build()
                        loadAd(adRequest)
                        adView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // Re-load ad if needed or triggered by lifecycle
                    if ((!isAdLoaded && adLoadError != null) || shouldReloadAd) {
                        Log.d("BannerAdView", "Reloading banner ad - reason: ${if (shouldReloadAd) "lifecycle trigger" else "retry after error"}")
                        view.loadAd(AdRequest.Builder().build())
                        shouldReloadAd = false // Reset the trigger
                    }
                }
            )
            
            // Debug overlay
            if (BuildConfig.DEBUG && !isAdLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Red.copy(alpha = 0.3f))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Banner: ${adLoadError ?: "Loading..."}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}