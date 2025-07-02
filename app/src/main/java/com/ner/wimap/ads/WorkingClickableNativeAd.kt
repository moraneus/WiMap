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
import androidx.compose.foundation.clickable
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
fun WorkingClickableNativeAd(
    modifier: Modifier = Modifier,
    isPersistent: Boolean = false
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var adView by remember { mutableStateOf<NativeAdView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        loadWorkingNativeAd(
            context = context,
            onAdLoaded = { ad ->
                Log.d("WorkingClickableNativeAd", "Ad loaded successfully: ${ad.headline}")
                nativeAd = ad
                isLoading = false
                hasError = false
            },
            onAdFailed = { error ->
                Log.e("WorkingClickableNativeAd", "Ad failed to load: ${error.message}")
                isLoading = false
                hasError = true
            }
        )
    }
    
    DisposableEffect(Unit) {
        onDispose {
            Log.d("WorkingClickableNativeAd", "Disposing native ad")
            adView?.destroy()
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
                        val view = createWorkingNativeAdView(ctx, nativeAd!!)
                        adView = view as NativeAdView
                        view
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
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

private fun loadWorkingNativeAd(
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
            Log.d("WorkingClickableNativeAd", "Native ad received: ${ad.headline}")
            
            // Set click confirmation listener
            ad.setOnPaidEventListener { adValue ->
                Log.d("WorkingClickableNativeAd", "Ad revenue generated: ${adValue.valueMicros}")
            }
            
            onAdLoaded(ad)
        }
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("WorkingClickableNativeAd", "Failed to load native ad: ${adError.message}")
                onAdFailed(adError)
            }
            
            override fun onAdClicked() {
                Log.d("WorkingClickableNativeAd", "=== NATIVE AD CLICKED - OPENING ADVERTISER! ===")
            }
            
            override fun onAdOpened() {
                Log.d("WorkingClickableNativeAd", "=== NATIVE AD OPENED! ===")
            }
            
            override fun onAdClosed() {
                Log.d("WorkingClickableNativeAd", "=== NATIVE AD CLOSED! ===")
            }
            
            override fun onAdImpression() {
                Log.d("WorkingClickableNativeAd", "=== NATIVE AD IMPRESSION! ===")
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

private fun createWorkingNativeAdView(context: Context, nativeAd: NativeAd): View {
    Log.d("WorkingClickableNativeAd", "Creating working native ad view")
    
    // Inflate the XML layout directly
    val inflater = LayoutInflater.from(context)
    val adView = inflater.inflate(R.layout.native_ad_layout_working, null) as NativeAdView
    
    // Register all the views
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    
    // Set the text content
    headlineView?.text = nativeAd.headline
    bodyView?.text = nativeAd.body
    callToActionView?.text = nativeAd.callToAction
    
    // Set media content
    nativeAd.mediaContent?.let { content ->
        mediaView?.mediaContent = content
        mediaView?.visibility = View.VISIBLE
    }
    
    // Register the views with NativeAdView
    adView.headlineView = headlineView
    adView.bodyView = bodyView
    adView.callToActionView = callToActionView
    adView.mediaView = mediaView
    
    // MOST CRITICAL: Set the native ad on the view
    adView.setNativeAd(nativeAd)
    
    // Add click listeners to all clickable elements
    callToActionView?.setOnClickListener {
        Log.d("WorkingClickableNativeAd", "CTA button clicked directly")
        adView.performClick()
    }
    
    headlineView?.setOnClickListener {
        Log.d("WorkingClickableNativeAd", "Headline clicked directly")
        adView.performClick()
    }
    
    bodyView?.setOnClickListener {
        Log.d("WorkingClickableNativeAd", "Body clicked directly")  
        adView.performClick()
    }
    
    // Make the entire ad view clickable
    adView.setOnClickListener {
        Log.d("WorkingClickableNativeAd", "Ad view clicked")
    }
    
    Log.d("WorkingClickableNativeAd", "Working native ad view created successfully")
    return adView
}