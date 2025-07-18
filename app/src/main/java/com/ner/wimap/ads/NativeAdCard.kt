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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Native Ad Card that matches the Wi-Fi network card styling
 */
@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    isPersistent: Boolean = false
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
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
        android.util.Log.d("NativeAdCard", "Loading native ad (isPersistent: $isPersistent)")
        adManager.loadNativeAd(
            context = context,
            onAdLoaded = { ad ->
                android.util.Log.d("NativeAdCard", "Native ad loaded successfully (isPersistent: $isPersistent)")
                nativeAd = ad
                isLoading = false
                hasError = false
            },
            onAdFailedToLoad = { error ->
                android.util.Log.e("NativeAdCard", "Native ad failed to load (isPersistent: $isPersistent): ${error.message}")
                isLoading = false
                hasError = true
                
                // Retry after a delay for both persistent and regular ads
                coroutineScope.launch {
                    val retryDelay = if (isPersistent) 3000L else 2000L // Faster retry for regular ads
                    delay(retryDelay)
                    android.util.Log.d("NativeAdCard", "Retrying native ad (isPersistent: $isPersistent)...")
                    
                    // Try loading again
                    adManager.loadNativeAd(
                        context = context,
                        onAdLoaded = { ad ->
                            android.util.Log.d("NativeAdCard", "Native ad retry successful (isPersistent: $isPersistent)")
                            nativeAd = ad
                            hasError = false
                        },
                        onAdFailedToLoad = { retryError ->
                            android.util.Log.e("NativeAdCard", "Native ad retry failed (isPersistent: $isPersistent): ${retryError.message}")
                            // Final failure, show placeholder
                        }
                    )
                }
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
            if (isPersistent) {
                // For persistent ads, show a placeholder card instead of disappearing
                NativeAdPlaceholderCard(modifier = modifier)
            } else {
                // For regular ads, show a minimal placeholder to avoid gaps
                Card(
                    modifier = modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (BuildConfig.DEBUG) Color(0xFFFFEBEE) else Color(0xFFF8F9FA) // Light red in debug, light gray in release
                    ),
                    border = BorderStroke(1.dp, if (BuildConfig.DEBUG) Color(0xFFE57373) else Color(0xFFE0E0E0)) // Red in debug, gray in release
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (BuildConfig.DEBUG) {
                            Text(
                                text = "Ad Failed to Load",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD32F2F)
                            )
                        } else {
                            // Show a subtle "Sponsored" placeholder in release
                            Text(
                                text = "Sponsored Content",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
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
            
            // Native ad content using XML layout
            AndroidView(
                factory = { context ->
                    // Inflate the native ad layout
                    val inflater = android.view.LayoutInflater.from(context)
                    inflater.inflate(com.ner.wimap.R.layout.native_ad_layout, null) as NativeAdView
                },
                modifier = Modifier.fillMaxWidth(),
                update = { adView ->
                    // Populate the native ad view
                    populateNativeAdView(nativeAd, adView)
                }
            )
        }
    }
}

/**
 * Populate the native ad view with ad content
 */
private fun populateNativeAdView(nativeAd: NativeAd, nativeAdView: NativeAdView) {
    // Find views from the inflated layout
    val headlineView = nativeAdView.findViewById<android.widget.TextView>(com.ner.wimap.R.id.ad_headline)
    val bodyView = nativeAdView.findViewById<android.widget.TextView>(com.ner.wimap.R.id.ad_body)
    val callToActionView = nativeAdView.findViewById<android.widget.Button>(com.ner.wimap.R.id.ad_call_to_action)
    val mediaView = nativeAdView.findViewById<MediaView>(com.ner.wimap.R.id.ad_media)
    
    // Set the text for each view
    headlineView.text = nativeAd.headline
    bodyView.text = nativeAd.body
    callToActionView.text = nativeAd.callToAction
    
    // Handle media content
    nativeAd.mediaContent?.let { content ->
        if (content.hasVideoContent() || content.aspectRatio > 0) {
            mediaView.visibility = android.view.View.VISIBLE
            mediaView.mediaContent = content
            nativeAdView.mediaView = mediaView
        } else {
            mediaView.visibility = android.view.View.GONE
        }
    } ?: run {
        mediaView.visibility = android.view.View.GONE
    }
    
    // Register the views with the native ad view
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = callToActionView
    
    // CRITICAL: Set the native ad AFTER registering views
    nativeAdView.setNativeAd(nativeAd)
    
    // Log for debugging
    android.util.Log.d("NativeAdCard", "Native ad populated with XML layout: headline=${nativeAd.headline}, cta=${nativeAd.callToAction}")
}

/**
 * Hilt entry point for accessing AdManager and NativeAdCache in Compose
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AdManagerEntryPoint {
    fun adManager(): AdManager
    fun nativeAdCache(): NativeAdCache
}