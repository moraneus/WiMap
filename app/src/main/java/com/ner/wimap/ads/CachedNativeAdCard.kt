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
import dagger.hilt.android.EntryPointAccessors
import com.ner.wimap.ads.AdManagerEntryPoint

/**
 * Cached Native Ad Card that uses pre-loaded ads for instant display
 * This provides much better performance than loading ads on-demand
 */
@Composable
fun CachedNativeAdCard(
    modifier: Modifier = Modifier,
    isPersistent: Boolean = false
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Get NativeAdCache instance
    val adCache = remember {
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdManagerEntryPoint::class.java
        )
        hiltEntryPoint.nativeAdCache()
    }
    
    val cacheSize by adCache.cacheSize.collectAsState()
    val isCacheLoading by adCache.isLoading.collectAsState()
    
    // Initialize cache on first composition
    LaunchedEffect(Unit) {
        android.util.Log.d("CachedNativeAdCard", "Initializing ad cache for ${if (isPersistent) "persistent" else "regular"} ad")
        adCache.initialize(context)
        android.util.Log.d("CachedNativeAdCard", "Cache stats: ${adCache.getCacheStats()}")
    }
    
    // Try to get a cached ad immediately
    LaunchedEffect(cacheSize) {
        if (nativeAd == null && cacheSize > 0) {
            nativeAd = adCache.getAd()
            isLoading = false
            android.util.Log.d("CachedNativeAdCard", "Got cached ad immediately. Remaining: $cacheSize")
        } else if (nativeAd == null && !isCacheLoading) {
            // No ads available and not loading - refresh cache
            isLoading = !isPersistent // Only show loading for persistent ads
            adCache.refreshCache(context)
        } else if (nativeAd == null && isCacheLoading) {
            // Cache is loading - only show loading state for persistent ads
            isLoading = isPersistent
        }
    }
    
    // Dispose of ad when composable is removed
    DisposableEffect(nativeAd) {
        onDispose {
            nativeAd?.destroy()
        }
    }
    
    when {
        nativeAd != null -> {
            // We have an ad - show it immediately
            NativeAdContent(
                nativeAd = nativeAd!!,
                modifier = modifier
            )
        }
        isPersistent -> {
            // For persistent ads, always show something
            if (isLoading || isCacheLoading) {
                NativeAdLoadingCard(modifier = modifier)
            } else {
                NativeAdPlaceholderCard(modifier = modifier)
            }
        }
        else -> {
            // For regular ads, don't show anything if no ad is available
            // This prevents gaps in the list
            Box(modifier = modifier)
        }
    }
}

/**
 * Placeholder card for persistent ads when no ads are available
 */
@Composable
private fun NativeAdPlaceholderCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F8FF) // Light blue background like regular native ads
        ),
        border = BorderStroke(1.dp, Color(0xFF4CAF50)) // Green border to match native ads
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sponsored label
            Row(
                modifier = Modifier
                    .background(
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Featured",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Featured Content",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF4CAF50)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Discover Premium Features",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Enhanced Wi-Fi scanning and network management tools",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Fake CTA button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF4CAF50)
            ) {
                Text(
                    text = "Learn More",
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
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