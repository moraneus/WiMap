package com.ner.wimap.ads

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
import kotlinx.coroutines.launch

@Composable
fun StableNativeAdCard(
    modifier: Modifier = Modifier,
    isPersistent: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nativeAdView by remember { mutableStateOf<NativeAdView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    DisposableEffect(Unit) {
        val adUnitId = if (BuildConfig.DEBUG) {
            "ca-app-pub-3940256099942544/2247696110" // Test native ad unit
        } else {
            "ca-app-pub-9891349918663384/3021988773" // Production native ad unit
        }
        
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                Log.d("StableNativeAdCard", "Native ad loaded: ${nativeAd.headline}")
                
                scope.launch {
                    try {
                        // Create the ad view
                        val inflater = LayoutInflater.from(context)
                        val adView = inflater.inflate(R.layout.native_ad_layout, null) as NativeAdView
                        
                        // Set up the ad view
                        adView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        
                        // Populate the native ad view
                        populateNativeAdView(nativeAd, adView)
                        
                        // Update state
                        nativeAdView = adView
                        isLoading = false
                        loadError = null
                    } catch (e: Exception) {
                        Log.e("StableNativeAdCard", "Error creating ad view", e)
                        loadError = e.message
                        isLoading = false
                    }
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("StableNativeAdCard", "Failed to load ad: ${adError.message}")
                    loadError = adError.message
                    isLoading = false
                }
                
                override fun onAdClicked() {
                    Log.d("StableNativeAdCard", "Native ad clicked!")
                }
                
                override fun onAdOpened() {
                    Log.d("StableNativeAdCard", "Native ad opened!")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()
        
        // Load the ad
        adLoader.loadAd(AdRequest.Builder().build())
        
        onDispose {
            nativeAdView?.destroy()
            Log.d("StableNativeAdCard", "Native ad disposed")
        }
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F8FF)
        )
    ) {
        when {
            nativeAdView != null -> {
                AndroidView(
                    factory = { nativeAdView!! },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )
            }
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            loadError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ad failed to load",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            else -> {
                // Empty state
                Box(modifier = Modifier.fillMaxWidth().height(0.dp))
            }
        }
    }
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    // Find views
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    val callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    
    // Set the text
    headlineView?.text = nativeAd.headline
    bodyView?.text = nativeAd.body
    callToActionView?.text = nativeAd.callToAction
    
    // Set the media content
    nativeAd.mediaContent?.let { content ->
        mediaView?.mediaContent = content
        mediaView?.visibility = android.view.View.VISIBLE
        adView.mediaView = mediaView
    }
    
    // Register the views
    adView.headlineView = headlineView
    adView.bodyView = bodyView  
    adView.callToActionView = callToActionView
    
    // IMPORTANT: This registers the NativeAd with the NativeAdView
    adView.setNativeAd(nativeAd)
}