package com.ner.wimap.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.components.*
import com.ner.wimap.ui.components.MainTopAppBar
import com.ner.wimap.ads.BannerAdView
import kotlinx.coroutines.delay
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiLocatorScreen(
    wifiNetworks: List<WifiNetwork>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onStartLocatorScanning: (WifiNetwork) -> Unit = {},
    onStopLocatorScanning: () -> Unit = {},
    locatorRSSI: Int? = null,
    isLocatorActive: Boolean = false
) {
    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }
    var showNetworkSelector by remember { mutableStateOf(false) }
    var showAuthenticationDialog by remember { mutableStateOf(false) }
    
    // Use the locator RSSI from the active scanning, fallback to network's initial RSSI
    val currentRSSI = locatorRSSI ?: selectedNetwork?.rssi ?: -100
    
    // Use the locator scanning state from the scanner
    val isScanning = isLocatorActive
    
    // Stop scanning when network is deselected
    LaunchedEffect(selectedNetwork) {
        if (selectedNetwork == null) {
            onStopLocatorScanning()
        }
    }
    
    // Clean up when leaving the screen or network changes
    DisposableEffect(selectedNetwork) {
        onDispose {
            onStopLocatorScanning()
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Top App Bar with consistent design
        MainTopAppBar(
            onOpenPinnedNetworks = {}, // Not used in WiFi Locator
            onOpenSettings = {}, // Not used in WiFi Locator  
            isBackgroundServiceActive = false,
            showNavigationActions = true,
            onShowAbout = {},
            onShowTerms = {},
            currentPage = 0, // WiFi Locator is page 0
            onNavigateToPage = {}, // Navigation handled by swipe
            title = "WiFi Locator",
            subtitle = "Signal Strength Tracking",
            showSettingsButton = false
        )
        
        // Content with Box for bottom bar positioning
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Network Selection Card
                NetworkSelectionCard(
                    selectedNetwork = selectedNetwork,
                    onSelectNetwork = { showNetworkSelector = true },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                if (selectedNetwork != null) {
                    // Compass/RSSI Display
                    CompassRSSIDisplay(
                        rssi = currentRSSI,
                        isScanning = isScanning,
                        modifier = Modifier.size(280.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { 
                                if (isScanning) {
                                    onStopLocatorScanning()
                                } else {
                                    selectedNetwork?.let { network ->
                                        showAuthenticationDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isScanning) "Stop" else "Start")
                        }
                        
                        OutlinedButton(
                            onClick = { 
                                onStopLocatorScanning()
                                selectedNetwork = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // RSSI Info Card
                    RSSIInfoCard(
                        rssi = currentRSSI,
                        network = selectedNetwork,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Info Card about scanning
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Tip: Move closer to the router for stronger signal. RSSI updates with each WiFi scan cycle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else {
                    // Empty State
                    EmptyLocatorState(
                        onSelectNetwork = { showNetworkSelector = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(80.dp)) // Space for bottom ad
            }
            
            // Banner Ad at the bottom
            BannerAdView(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                isPinnedScreen = false
            )
        }
    }
    
    // Network Selector Dialog
    if (showNetworkSelector) {
        NetworkSelectorDialog(
            networks = wifiNetworks,
            onNetworkSelected = { network ->
                selectedNetwork = network
                showNetworkSelector = false
            },
            onDismiss = { showNetworkSelector = false }
        )
    }
    
    // Authentication Explanation Dialog
    if (showAuthenticationDialog && selectedNetwork != null) {
        AuthenticationExplanationDialog(
            network = selectedNetwork!!,
            onConfirm = {
                showAuthenticationDialog = false
                onStartLocatorScanning(selectedNetwork!!)
            },
            onDismiss = { showAuthenticationDialog = false }
        )
    }
}

@Composable
private fun AuthenticationExplanationDialog(
    network: WifiNetwork,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isOpenNetwork = network.security.contains("Open", ignoreCase = true)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Active RSSI Tracking",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Network info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Target Network: ${network.ssid}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "BSSID: ${network.bssid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Security: ${network.security} • Channel: ${network.channel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Explanation text
                Text(
                    text = "How Real-time RSSI Tracking Works:",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (isOpenNetwork) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔗",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Connection-based tracking: Creates brief connection attempts every 2 seconds to force the access point to respond with fresh RSSI values.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚡",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Quick disconnect: Connects for ~200ms then disconnects to avoid disrupting the network.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔍",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Authentication probes: Sends fake authentication attempts with invalid passwords to trigger immediate access point responses.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🛡️",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Safe probing: Uses intentionally wrong credentials - no actual connection attempts are made to secured networks.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                // Benefits
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "✅ Real-time RSSI updates",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "✅ No passive waiting for beacons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "✅ Accurate WiFi location tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Tracking")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun NetworkSelectionCard(
    selectedNetwork: WifiNetwork?,
    onSelectNetwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onSelectNetwork() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (selectedNetwork != null) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = selectedNetwork?.ssid ?: "Select a Network",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            
            if (selectedNetwork != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Initial RSSI: ${selectedNetwork.rssi} dBm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "BSSID: ${selectedNetwork.bssid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Security: ${selectedNetwork.security} • Channel: ${selectedNetwork.channel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to choose a WiFi network to locate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CompassRSSIDisplay(
    rssi: Int,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "compass_animations")
    
    // Main compass rotation
    val rotation by if (isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "compass_rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    
    // Pulsing scale animation
    val pulseScale by if (isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    
    // Signal wave animation
    val waveOffset by if (isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_animation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    
    // Glow intensity animation
    val glowIntensity by if (isScanning) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow_intensity"
        )
    } else {
        remember { mutableStateOf(0.2f) }
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer glow effect when scanning
        if (isScanning) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.1f + (glowIntensity * 0.1f)
                        scaleY = 1.1f + (glowIntensity * 0.1f)
                        alpha = glowIntensity * 0.3f
                    }
            ) {
                drawCircle(
                    color = getRSSIColor(rssi),
                    radius = size.minDimension / 2 - 10.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(if (isScanning) rotation else 0f)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
        ) {
            drawEnhancedCompass(rssi, isScanning, waveOffset)
        }
        
        // Center RSSI Value with enhanced animations
        Card(
            modifier = Modifier
                .size(130.dp)
                .graphicsLayer {
                    scaleX = pulseScale * 0.95f + 0.05f
                    scaleY = pulseScale * 0.95f + 0.05f
                },
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = getRSSIColor(rssi).copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$rssi",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "dBm",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = getRSSIQuality(rssi),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                
                // Scanning indicator
                if (isScanning) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f + glowIntensity * 0.4f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawEnhancedCompass(rssi: Int, isScanning: Boolean, waveOffset: Float) {
    val center = Offset(size.width / 2, size.height / 2)
    val radius = size.minDimension / 2 - 25.dp.toPx()
    
    // Background circle with gradient effect
    drawCircle(
        color = Color.Black.copy(alpha = 0.08f),
        radius = radius,
        center = center,
        style = Stroke(width = 3.dp.toPx())
    )
    
    // Animated wave circles when scanning
    if (isScanning) {
        val waveCount = 3
        repeat(waveCount) { index ->
            val waveRadius = radius * (0.2f + (waveOffset + index * 0.33f) % 1f * 0.8f)
            val waveAlpha = (1f - ((waveOffset + index * 0.33f) % 1f)) * 0.4f
            
            drawCircle(
                color = getRSSIColor(rssi).copy(alpha = waveAlpha),
                radius = waveRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
    
    // RSSI range circles with enhanced styling
    val ranges = listOf(0.25f, 0.5f, 0.75f, 1.0f)
    ranges.forEachIndexed { index, range ->
        val circleRadius = radius * range
        val alpha = if (isScanning) 0.25f + index * 0.05f else 0.1f + index * 0.02f
        val strokeWidth = if (index == ranges.size - 1) 3.dp.toPx() else 2.dp.toPx()
        
        drawCircle(
            color = getRSSIColor(rssi).copy(alpha = alpha),
            radius = circleRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
    }
    
    // Compass direction markers
    val directions = listOf("N", "E", "S", "W")
    directions.forEachIndexed { index, _ ->
        val angle = index * 90f * PI / 180f
        val markerStart = center + Offset(
            cos(angle - PI / 2).toFloat() * (radius - 15.dp.toPx()),
            sin(angle - PI / 2).toFloat() * (radius - 15.dp.toPx())
        )
        val markerEnd = center + Offset(
            cos(angle - PI / 2).toFloat() * (radius - 5.dp.toPx()),
            sin(angle - PI / 2).toFloat() * (radius - 5.dp.toPx())
        )
        
        drawLine(
            color = Color.Gray.copy(alpha = 0.6f),
            start = markerStart,
            end = markerEnd,
            strokeWidth = 3.dp.toPx()
        )
    }
    
    // Enhanced signal strength indicator with gradient effect
    val signalStrengthRatio = getSignalStrengthRatio(rssi)
    val signalRadius = radius * 0.8f * signalStrengthRatio
    
    if (signalRadius > 0) {
        // Outer signal ring
        drawCircle(
            color = getRSSIColor(rssi).copy(alpha = 0.3f),
            radius = signalRadius + 8.dp.toPx(),
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Main signal ring
        drawCircle(
            color = getRSSIColor(rssi),
            radius = signalRadius,
            center = center,
            style = Stroke(width = 6.dp.toPx())
        )
        
        // Inner highlight when scanning
        if (isScanning) {
            drawCircle(
                color = getRSSIColor(rssi).copy(alpha = 0.6f),
                radius = signalRadius - 4.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun RSSIInfoCard(
    rssi: Int,
    network: WifiNetwork?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Signal Information",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current RSSI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$rssi dBm",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = getRSSIColor(rssi)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Signal Quality",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = getRSSIQuality(rssi),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = getRSSIColor(rssi)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = getSignalStrengthRatio(rssi),
                modifier = Modifier.fillMaxWidth(),
                color = getRSSIColor(rssi),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            if (network != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Network: ${network.ssid}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "BSSID: ${network.bssid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyLocatorState(
    onSelectNetwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "WiFi Signal Locator",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Select a WiFi network to start tracking its signal strength and locate its source using RSSI values.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onSelectNetwork,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Network")
            }
        }
    }
}

@Composable
private fun NetworkSelectorDialog(
    networks: List<WifiNetwork>,
    onNetworkSelected: (WifiNetwork) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Network to Locate",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(networks) { network ->
                    NetworkSelectorItem(
                        network = network,
                        onClick = { onNetworkSelected(network) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NetworkSelectorItem(
    network: WifiNetwork,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = getRSSIColor(network.rssi),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = "${network.rssi} dBm • ${network.security}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Helper functions for RSSI calculations and colors
private fun getRSSIColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> Color(0xFF4CAF50) // Excellent - Green
        rssi >= -60 -> Color(0xFF8BC34A) // Good - Light Green
        rssi >= -70 -> Color(0xFFFFEB3B) // Fair - Yellow
        rssi >= -80 -> Color(0xFFFF9800) // Poor - Orange
        else -> Color(0xFFF44336) // Very Poor - Red
    }
}

private fun getRSSIQuality(rssi: Int): String {
    return when {
        rssi >= -50 -> "Excellent"
        rssi >= -60 -> "Good"
        rssi >= -70 -> "Fair"
        rssi >= -80 -> "Poor"
        else -> "Very Poor"
    }
}

private fun getSignalStrengthRatio(rssi: Int): Float {
    // Convert RSSI to a 0-1 ratio for progress indicators
    return ((rssi + 100).coerceAtLeast(0) / 50f).coerceAtMost(1f)
}