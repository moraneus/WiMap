package com.ner.wimap.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hybrid Native Ad Card that combines caching with fallback loading
 * This ensures ads are always displayed, either from cache or loaded on-demand
 */
@Composable
fun HybridNativeAdCard(
    modifier: Modifier = Modifier,
    isPersistent: Boolean = false
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Get AdManager and NativeAdCache instances
    val (adManager, adCache) = remember {
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdManagerEntryPoint::class.java
        )
        Pair(hiltEntryPoint.adManager(), hiltEntryPoint.nativeAdCache())
    }
    
    val cacheSize by adCache.cacheSize.collectAsState()
    
    // Initialize cache on first composition
    LaunchedEffect(Unit) {
        adCache.initialize(context)
    }
    
    // Try to get ad from cache first, then fall back to loading
    LaunchedEffect(cacheSize) {
        if (nativeAd == null) {
            // Try to get from cache
            val cachedAd = adCache.getAd()
            if (cachedAd != null) {
                android.util.Log.d("HybridNativeAdCard", "Got ad from cache")
                nativeAd = cachedAd
                isLoading = false
                hasError = false
            } else {
                // No cached ad available, load one directly
                android.util.Log.d("HybridNativeAdCard", "No cached ad, loading directly")
                isLoading = true
                
                adManager.loadNativeAd(
                    context = context,
                    onAdLoaded = { ad ->
                        android.util.Log.d("HybridNativeAdCard", "Direct ad loaded successfully")
                        nativeAd = ad
                        isLoading = false
                        hasError = false
                    },
                    onAdFailedToLoad = { error ->
                        android.util.Log.e("HybridNativeAdCard", "Direct ad failed to load: ${error.message}")
                        isLoading = false
                        hasError = true
                        
                        // Retry for persistent ads
                        if (isPersistent) {
                            coroutineScope.launch {
                                delay(3000)
                                if (nativeAd == null) {
                                    // Try cache again
                                    val retryAd = adCache.getAd()
                                    if (retryAd != null) {
                                        nativeAd = retryAd
                                        hasError = false
                                    }
                                }
                            }
                        }
                    }
                )
            }
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
        isLoading && isPersistent -> {
            // Only show loading for persistent ads
            NativeAdLoadingCard(modifier = modifier)
        }
        hasError && isPersistent -> {
            // Only show placeholder for persistent ads
            NativeAdPlaceholderCard(modifier = modifier)
        }
        else -> {
            // For regular ads, don't show anything if no ad is available
            Box(modifier = modifier)
        }
    }
}

/**
 * Placeholder card for persistent ads when loading fails
 */
@Composable
private fun NativeAdPlaceholderCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0F8FF)
        ),
        border = BorderStroke(1.dp, Color(0xFF4CAF50))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            containerColor = Color(0xFFF0F8FF)
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
            containerColor = Color(0xFFF0F8FF)
        ),
        border = BorderStroke(1.dp, Color(0xFF4CAF50))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
            
            // Test button outside native ad to verify touch events work in Compose
            Button(
                onClick = {
                    android.util.Log.d("ComposeTest", "COMPOSE BUTTON CLICKED! Touch events work in Compose.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("TEST COMPOSE BUTTON - Should Work")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            AndroidView(
                factory = { context ->
                    NativeAdView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        
                        // Critical: Ensure this view intercepts touch events
                        isClickable = true
                        isFocusable = true
                        
                        // Override touch handling to ensure events reach this view
                        setOnTouchListener { view, event ->
                            android.util.Log.d("NativeAdView", "Touch event received: ${event.action}")
                            false // Let the native ad handle the touch
                        }
                        
                        // Set up click listener
                        setOnClickListener {
                            android.util.Log.d("NativeAdView", "NativeAdView clicked!")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(),
                update = { nativeAdView ->
                    // Re-populate the ad view when it updates
                    populateNativeAdView(nativeAd, nativeAdView)
                }
            )
        }
    }
}

/**
 * Populate the native ad view with ad content
 */
private fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
    android.util.Log.d("NativeAd", "Starting to populate native ad view")
    
    // Clear any existing content
    nativeAdView.removeAllViews()
    
    val context = nativeAdView.context
    
    // Create a simple, definitely clickable button for testing
    val testClickView = android.widget.Button(context).apply {
        text = "TEST CLICK - ${nativeAd.callToAction ?: "Learn More"}"
        textSize = 14f
        setBackgroundColor(android.graphics.Color.parseColor("#FF0000")) // Red for visibility
        setTextColor(android.graphics.Color.WHITE)
        setPadding(32, 16, 32, 16)
        
        // Ensure it's clickable
        isClickable = true
        isFocusable = true
        
        // Add explicit click listener
        setOnClickListener {
            android.util.Log.d("NativeAd", "TEST BUTTON CLICKED!")
        }
        
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    // Create headline view
    val headlineView = android.widget.TextView(context).apply {
        text = nativeAd.headline ?: "Sponsored Content"
        textSize = 16f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(android.graphics.Color.parseColor("#2C3E50"))
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(16, 16, 16, 8)
    }
    
    // Create body view
    val bodyView = android.widget.TextView(context).apply {
        text = nativeAd.body ?: "Learn more about this product or service"
        textSize = 14f
        setTextColor(android.graphics.Color.parseColor("#7F8C8D"))
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(16, 0, 16, 16)
    }
    
    // Create container
    val containerLayout = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
        
        addView(headlineView)
        addView(bodyView)
        addView(testClickView)
    }
    
    // Add container to the native ad view
    nativeAdView.addView(containerLayout)
    
    // Register views with native ad
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = testClickView
    
    // Add explicit click listener to the entire ad view
    nativeAdView.setOnClickListener {
        android.util.Log.d("NativeAd", "ENTIRE NATIVE AD VIEW CLICKED!")
    }
    
    // Set the native ad - this should enable click tracking
    nativeAdView.setNativeAd(nativeAd)
    
    android.util.Log.d("NativeAd", "Native ad view setup complete with test button")
    
    // Verify the setup
    nativeAdView.post {
        android.util.Log.d("NativeAd", "Ad view isClickable: ${nativeAdView.isClickable}")
        android.util.Log.d("NativeAd", "Test button isClickable: ${testClickView.isClickable}")
        android.util.Log.d("NativeAd", "Ad has headline: ${nativeAd.headline}")
        android.util.Log.d("NativeAd", "Ad has CTA: ${nativeAd.callToAction}")
    }
}