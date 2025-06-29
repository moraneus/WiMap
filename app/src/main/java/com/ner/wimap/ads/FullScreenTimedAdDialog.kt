package com.ner.wimap.ads

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ner.wimap.BuildConfig
import kotlinx.coroutines.delay

/**
 * Full-screen timed ad dialog that shows for 30 seconds with skip functionality
 * Similar to game ads with countdown and skip button
 */
@Composable
fun FullScreenTimedAdDialog(
    isVisible: Boolean,
    onAdCompleted: () -> Unit,
    onSkipAd: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return
    
    var timeRemaining by remember { mutableStateOf(30) }
    var canSkip by remember { mutableStateOf(false) }
    var adLoaded by remember { mutableStateOf(false) }
    var adError by remember { mutableStateOf<String?>(null) }
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    
    val context = LocalContext.current
    
    // Countdown timer
    LaunchedEffect(isVisible) {
        if (isVisible) {
            for (i in 30 downTo 0) {
                timeRemaining = i
                if (i <= 25) { // Allow skip after 5 seconds
                    canSkip = true
                }
                delay(1000)
            }
            // Auto-complete after 30 seconds
            onAdCompleted()
        }
    }
    
    // Load interstitial ad
    LaunchedEffect(isVisible) {
        if (isVisible) {
            val adUnitId = if (BuildConfig.DEBUG) {
                "ca-app-pub-3940256099942544/1033173712" // Test interstitial ad unit
            } else {
                "ca-app-pub-3940256099942544/1033173712" // TODO: Replace with real ad unit
            }
            
            val adRequest = AdRequest.Builder().build()
            
            InterstitialAd.load(
                context,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        adError = loadAdError.message
                        // Show fallback content instead
                    }
                    
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        adLoaded = true
                        
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdClicked() {
                                // Ad was clicked
                            }
                            
                            override fun onAdDismissedFullScreenContent() {
                                // Ad was dismissed, continue with action
                                onAdCompleted()
                            }
                            
                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                // Show fallback content
                                // Continue with custom ad experience
                            }
                            
                            override fun onAdShowedFullScreenContent() {
                                // Ad showed successfully
                            }
                        }
                    }
                }
            )
        }
    }
    
    Dialog(
        onDismissRequest = { /* Prevent dismissal by clicking outside */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        FullScreenAdContent(
            timeRemaining = timeRemaining,
            canSkip = canSkip,
            adLoaded = adLoaded,
            adError = adError,
            interstitialAd = interstitialAd,
            onSkipAd = onSkipAd,
            onShowInterstitial = { ad ->
                if (context is androidx.activity.ComponentActivity) {
                    ad.show(context)
                }
            }
        )
    }
}

@Composable
private fun FullScreenAdContent(
    timeRemaining: Int,
    canSkip: Boolean,
    adLoaded: Boolean,
    adError: String?,
    interstitialAd: InterstitialAd?,
    onSkipAd: () -> Unit,
    onShowInterstitial: (InterstitialAd) -> Unit
) {
    // Animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "ad_gradient")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_offset"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF667eea).copy(alpha = 0.9f + animatedOffset * 0.1f),
                        Color(0xFF764ba2).copy(alpha = 0.9f + animatedOffset * 0.1f),
                        Color(0xFF667eea).copy(alpha = 0.9f + animatedOffset * 0.1f)
                    )
                )
            )
    ) {
        // Skip button (top-right)
        if (canSkip) {
            SkipButton(
                onSkip = onSkipAd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .zIndex(2f)
            )
        }
        
        // Countdown timer (top-left)
        CountdownTimer(
            timeRemaining = timeRemaining,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .zIndex(2f)
        )
        
        // Main ad content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                interstitialAd != null && adLoaded -> {
                    // Show real ad content
                    RealAdContent(
                        interstitialAd = interstitialAd,
                        onShowInterstitial = onShowInterstitial
                    )
                }
                adError != null -> {
                    // Show fallback content
                    FallbackAdContent()
                }
                else -> {
                    // Loading state
                    LoadingAdContent()
                }
            }
        }
        
        // Bottom branding
        AdBranding(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun SkipButton(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseAnimation = rememberInfiniteTransition(label = "skip_pulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Button(
        onClick = onSkip,
        modifier = modifier
            .size(80.dp, 40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.9f),
            contentColor = Color(0xFF667eea)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Skip",
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Skip",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CountdownTimer(
    timeRemaining: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Circular progress indicator
            CircularProgressIndicator(
                progress = { (30 - timeRemaining) / 30f },
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                strokeWidth = 3.dp,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
            
            // Countdown number
            Text(
                text = timeRemaining.toString(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RealAdContent(
    interstitialAd: InterstitialAd,
    onShowInterstitial: (InterstitialAd) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Advertisement",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { onShowInterstitial(interstitialAd) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF667eea)
                    )
                ) {
                    Text(
                        text = "View Full Ad",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackAdContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = if (BuildConfig.DEBUG) "Test Advertisement" else "Advertisement",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🚀 WiMap Pro",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF667eea)
                    )
                    
                    Text(
                        text = "Unlock premium features:\n• Remove all ads\n• Advanced export options\n• Priority support",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF2C3E50)
                    )
                    
                    Button(
                        onClick = { /* TODO: Open upgrade screen */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF27AE60)
                        )
                    ) {
                        Text(
                            text = "Upgrade Now",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingAdContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Loading Advertisement...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 4.dp,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun AdBranding(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Text(
            text = if (BuildConfig.DEBUG) "Test Ad" else "Ad",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}