package com.ner.wimap.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.ViewGroup
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.ner.wimap.BuildConfig
import com.ner.wimap.ads.AdManager
import dagger.hilt.android.EntryPointAccessors

/**
 * Native Ad Card that matches the Wi-Fi network card styling
 */
@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    
    // Get AdManager instance
    val adManager = remember {
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdManagerEntryPoint::class.java
        )
        hiltEntryPoint.adManager()
    }
    
    // Load native ad
    LaunchedEffect(Unit) {
        adManager.loadNativeAd(
            context = context,
            onAdLoaded = { ad ->
                nativeAd = ad
                isLoading = false
                hasError = false
            },
            onAdFailedToLoad = { error ->
                isLoading = false
                hasError = true
            }
        )
    }
    
    // Dispose of ad when composable is removed
    DisposableEffect(nativeAd) {
        onDispose {
            nativeAd?.destroy()
        }
    }
    
    when {
        isLoading -> {
            NativeAdLoadingCard(modifier = modifier)
        }
        hasError -> {
            // Don't show anything if ad fails to load
            // This maintains clean UI without broken ad spaces
        }
        nativeAd != null -> {
            NativeAdContent(
                nativeAd = nativeAd!!,
                modifier = modifier
            )
        }
    }
}

/**
 * Loading state for native ad
 */
@Composable
private fun NativeAdLoadingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F8FF) // Light blue background
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Native ad content that matches Wi-Fi card styling
 */
@Composable
private fun NativeAdContent(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F8FF) // Light blue background to distinguish from Wi-Fi cards
        ),
        border = BorderStroke(1.dp, Color(0xFF4CAF50)) // Green border like selected cards
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Sponsored label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.2f),
                    modifier = Modifier.border(
                        1.dp, 
                        Color(0xFFFF9800), 
                        RoundedCornerShape(8.dp)
                    )
                ) {
                    Text(
                        text = if (BuildConfig.DEBUG) "Test Ad" else "Sponsored",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Ad",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Native ad content
            AndroidView(
                factory = { context ->
                    NativeAdView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { nativeAdView ->
                // Set up the native ad view
                populateNativeAdView(nativeAd, nativeAdView)
            }
        }
    }
}

/**
 * Populate the native ad view with ad content
 */
private fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
    // Create a simple layout for the native ad
    val context = nativeAdView.context
    
    // Create headline text view
    val headlineView = android.widget.TextView(context).apply {
        text = nativeAd.headline ?: "Sponsored Content"
        textSize = 16f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(android.graphics.Color.parseColor("#2C3E50"))
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    
    // Create body text view
    val bodyView = android.widget.TextView(context).apply {
        text = nativeAd.body ?: "Learn more about this product or service"
        textSize = 14f
        setTextColor(android.graphics.Color.parseColor("#7F8C8D"))
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    
    // Create call to action button
    val callToActionView = android.widget.Button(context).apply {
        text = nativeAd.callToAction ?: "Learn More"
        textSize = 12f
        setBackgroundColor(android.graphics.Color.parseColor("#667eea"))
        setTextColor(android.graphics.Color.WHITE)
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12
        }
    }
    
    // Create container layout
    val containerLayout = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        addView(headlineView)
        addView(bodyView)
        addView(callToActionView)
    }
    
    // Add media view if available
    nativeAd.mediaContent?.let { mediaContent ->
        val mediaView = MediaView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                200 // Fixed height for media
            ).apply {
                topMargin = 12
            }
        }
        mediaView.mediaContent = mediaContent
        containerLayout.addView(mediaView, 2) // Add before CTA button
        nativeAdView.mediaView = mediaView
    }
    
    // Set up the native ad view
    nativeAdView.removeAllViews()
    nativeAdView.addView(containerLayout)
    
    // Register views with the native ad
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = callToActionView
    
    // Register the native ad with the view
    nativeAdView.setNativeAd(nativeAd)
}

/**
 * Hilt entry point for accessing AdManager in Compose
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdManagerEntryPoint {
    fun adManager(): AdManager
}