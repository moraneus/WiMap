package com.ner.wimap.ads

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.ner.wimap.BuildConfig
import com.ner.wimap.R

@Composable
fun ClickableNativeAdCard(
    modifier: Modifier = Modifier,
    isPersistent: Boolean = false
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        loadClickableNativeAd(
            context = context,
            onAdLoaded = { ad ->
                Log.d("ClickableNativeAd", "Ad loaded successfully")
                nativeAd = ad
                isLoading = false
                hasError = false
            },
            onAdFailed = { error ->
                Log.e("ClickableNativeAd", "Ad failed to load: ${error.message}")
                isLoading = false
                hasError = true
            }
        )
    }
    
    DisposableEffect(nativeAd) {
        onDispose {
            nativeAd?.destroy()
        }
    }
    
    when {
        nativeAd != null -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F8FF)
                ),
                border = BorderStroke(1.dp, Color(0xFF4CAF50))
            ) {
                AndroidView(
                    factory = { ctx ->
                        createClickableNativeAdView(ctx, nativeAd!!)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                )
            }
        }
        isLoading && isPersistent -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F8FF)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        else -> {
            Box(modifier = modifier)
        }
    }
}

private fun loadClickableNativeAd(
    context: Context,
    onAdLoaded: (NativeAd) -> Unit,
    onAdFailed: (LoadAdError) -> Unit
) {
    val adUnitId = if (BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/2247696110" // Test native ad unit
    } else {
        "ca-app-pub-9891349918663384/3021988773" // Production native ad unit
    }
    
    val adLoader = AdLoader.Builder(context, adUnitId)
        .forNativeAd { ad ->
            Log.d("ClickableNativeAd", "Native ad received: ${ad.headline}")
            onAdLoaded(ad)
        }
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("ClickableNativeAd", "Failed to load native ad: ${adError.message}")
                onAdFailed(adError)
            }
            
            override fun onAdClicked() {
                Log.d("ClickableNativeAd", "=== NATIVE AD WAS CLICKED! ===")
            }
            
            override fun onAdOpened() {
                Log.d("ClickableNativeAd", "=== NATIVE AD WAS OPENED! ===")
            }
            
            override fun onAdClosed() {
                Log.d("ClickableNativeAd", "=== NATIVE AD WAS CLOSED! ===")
            }
        })
        .withNativeAdOptions(
            NativeAdOptions.Builder()
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .setRequestMultipleImages(true)
                .build()
        )
        .build()
    
    val adRequest = AdRequest.Builder().build()
    adLoader.loadAd(adRequest)
}

private fun createClickableNativeAdView(context: Context, nativeAd: NativeAd): View {
    Log.d("ClickableNativeAd", "Creating clickable native ad view")
    
    // Create a FrameLayout to hold our native ad
    val container = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(16, 16, 16, 16)
    }
    
    // Inflate the native ad layout
    val inflater = LayoutInflater.from(context)
    val adView = inflater.inflate(R.layout.native_ad_layout, container, false) as NativeAdView
    
    // Populate the ad view
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    
    // Set the ad content
    headlineView?.text = nativeAd.headline
    bodyView?.text = nativeAd.body
    callToActionView?.text = nativeAd.callToAction
    
    // Handle media content
    nativeAd.mediaContent?.let { content ->
        if (content.hasVideoContent() || content.aspectRatio > 0) {
            mediaView?.visibility = View.VISIBLE
            mediaView?.mediaContent = content
            adView.mediaView = mediaView
        } else {
            mediaView?.visibility = View.GONE
        }
    } ?: run {
        mediaView?.visibility = View.GONE
    }
    
    // Register views - CRITICAL for click handling
    adView.headlineView = headlineView
    adView.bodyView = bodyView
    adView.callToActionView = callToActionView
    
    // MOST IMPORTANT: Set the native ad
    adView.setNativeAd(nativeAd)
    
    // Make sure the entire view is clickable
    adView.isClickable = true
    adView.isFocusable = true
    
    // Add the ad view to container
    container.addView(adView)
    
    Log.d("ClickableNativeAd", "Clickable native ad view created successfully")
    return container
}