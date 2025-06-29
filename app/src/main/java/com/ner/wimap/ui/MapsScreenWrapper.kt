package com.ner.wimap.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.components.UnifiedTopAppBar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import android.os.Bundle
import android.util.Log

/**
 * Enhanced map screen with full network visualization and interactive features
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreenWrapper(
    wifiNetworks: List<WifiNetwork>,
    onBack: () -> Unit,
    onNavigateToPage: (Int) -> Unit = {},
    currentPage: Int = 2
) {
    val context = LocalContext.current
    var showNetworkList by remember { mutableStateOf(false) }
    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()
    
    // Filter networks with valid GPS coordinates
    val networksWithLocation = remember(wifiNetworks) {
        val filtered = wifiNetworks.filter { network ->
            val hasValidPeakCoords = network.peakRssiLatitude != null && 
                                   network.peakRssiLongitude != null &&
                                   network.peakRssiLatitude != 0.0 && 
                                   network.peakRssiLongitude != 0.0
            
            val hasValidCoords = network.latitude != null && 
                               network.longitude != null &&
                               network.latitude != 0.0 && 
                               network.longitude != 0.0
            
            if (!hasValidPeakCoords && !hasValidCoords) {
                Log.d("MapsScreenWrapper", "Network ${network.ssid} has no valid coords: peak(${network.peakRssiLatitude}, ${network.peakRssiLongitude}) regular(${network.latitude}, ${network.longitude})")
            }
            
            hasValidPeakCoords || hasValidCoords
        }
        Log.d("MapsScreenWrapper", "Total networks: ${wifiNetworks.size}, with location: ${filtered.size}")
        if (wifiNetworks.isNotEmpty() && filtered.isEmpty()) {
            Log.d("MapsScreenWrapper", "First network sample: SSID=${wifiNetworks[0].ssid}, lat=${wifiNetworks[0].latitude}, lng=${wifiNetworks[0].longitude}, peakLat=${wifiNetworks[0].peakRssiLatitude}, peakLng=${wifiNetworks[0].peakRssiLongitude}")
        }
        filtered
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Unified top app bar
        UnifiedTopAppBar(
            title = "Network Map",
            icon = Icons.Default.Map,
            onBack = onBack,
            currentPage = currentPage,
            onNavigateToPage = onNavigateToPage,
            showNavigationActions = true
        )
        
        // Map content with overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Google Map using Maps Compose
            val mapUiSettings = remember {
                MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = true
                )
            }
            
            val mapProperties = remember {
                MapProperties(
                    mapType = MapType.NORMAL
                )
            }
            
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(LatLng(40.7831, -73.9712), 10f)
            }
            
            Log.d("MapsScreenWrapper", "Rendering GoogleMap with ${networksWithLocation.size} networks")
            
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                onMapLoaded = {
                    Log.d("MapsScreenWrapper", "GoogleMap loaded successfully!")
                }
            ) {
                // Always add a test marker
                Marker(
                    state = MarkerState(position = LatLng(40.7831, -73.9712)),
                    title = "Test Marker",
                    snippet = "Map is working!",
                    onClick = { marker ->
                        Log.d("MapsScreenWrapper", "Test marker clicked: ${marker.title}")
                        false
                    }
                )
                
                // Add network markers
                networksWithLocation.forEach { network ->
                    val position = LatLng(
                        network.peakRssiLatitude ?: network.latitude ?: 0.0,
                        network.peakRssiLongitude ?: network.longitude ?: 0.0
                    )
                    
                    Marker(
                        state = MarkerState(position = position),
                        title = network.ssid,
                        snippet = buildMarkerSnippet(network),
                        icon = createCustomWiFiMarker(network),
                        onClick = { marker ->
                            Log.d("MapsScreenWrapper", "Network marker clicked: ${network.ssid}")
                            selectedNetwork = network
                            false
                        }
                    )
                }
            }
            
            // Auto-focus on networks if available
            LaunchedEffect(networksWithLocation) {
                if (networksWithLocation.isNotEmpty()) {
                    Log.d("MapsScreenWrapper", "Adjusting camera for ${networksWithLocation.size} networks")
                    val bounds = networksWithLocation.map { network ->
                        LatLng(
                            network.peakRssiLatitude ?: network.latitude ?: 0.0,
                            network.peakRssiLongitude ?: network.longitude ?: 0.0
                        )
                    }
                    
                    if (bounds.size == 1) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(bounds.first(), 16f)
                    } else {
                        val latitudes = bounds.map { it.latitude }
                        val longitudes = bounds.map { it.longitude }
                        val centerLat = (latitudes.minOrNull()!! + latitudes.maxOrNull()!!) / 2
                        val centerLng = (longitudes.minOrNull()!! + longitudes.maxOrNull()!!) / 2
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(centerLat, centerLng), 12f)
                    }
                }
            }
            
            // Network count indicator (always show for debugging)
            NetworkCountCard(
                totalNetworks = wifiNetworks.size,
                mappedNetworks = networksWithLocation.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
            
            // Floating List Button
            if (networksWithLocation.isNotEmpty()) {
                FloatingNetworkListButton(
                    onClick = { showNetworkList = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .zIndex(1f)
                )
            }
            
            // Selected network auto-clear effect
            selectedNetwork?.let { network ->
                LaunchedEffect(network) {
                    delay(3000) // Auto-clear selection after 3 seconds
                    selectedNetwork = null
                }
            }
        }
    }
    
    // Network List Bottom Sheet
    if (showNetworkList) {
        ModalBottomSheet(
            onDismissRequest = { showNetworkList = false },
            sheetState = bottomSheetState,
            modifier = Modifier.fillMaxHeight(0.8f)
        ) {
            NetworkListBottomSheet(
                networks = networksWithLocation,
                onNetworkSelected = { network ->
                    selectedNetwork = network
                    showNetworkList = false
                }
            )
        }
    }
}

// Old map setup functions removed - now using Maps Compose

/**
 * Create custom WiFi-themed marker icon based on signal strength
 */
private fun createCustomWiFiMarker(network: WifiNetwork): BitmapDescriptor {
    return when {
        network.rssi >= -50 -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
        network.rssi >= -70 -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
        network.rssi >= -80 -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
        else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
    }
}

/**
 * Build detailed snippet for marker info window
 */
private fun buildMarkerSnippet(network: WifiNetwork): String {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val lastSeen = timeFormat.format(Date(network.lastSeenTimestamp))
    
    return buildString {
        append("BSSID: ${network.bssid}\n")
        append("Signal: ${network.rssi} dBm\n")
        append("Security: ${network.security}\n")
        append("Channel: ${network.channel}\n")
        append("Last seen: $lastSeen")
    }
}

// Camera and highlighting functions removed - now handled by Maps Compose

/**
 * Network count card component
 */
@Composable
private fun NetworkCountCard(
    totalNetworks: Int,
    mappedNetworks: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$mappedNetworks",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Mapped",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            if (totalNetworks > mappedNetworks) {
                Text(
                    text = "of $totalNetworks total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Floating Action Button for network list
 */
@Composable
private fun FloatingNetworkListButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fab_scale"
    )
    
    FloatingActionButton(
        onClick = {
            isPressed = true
            onClick()
            isPressed = false
        },
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            ),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.List,
            contentDescription = "View Network List",
            modifier = Modifier.size(28.dp)
        )
    }
}

/**
 * Bottom sheet with network list
 */
@Composable
private fun NetworkListBottomSheet(
    networks: List<WifiNetwork>,
    onNetworkSelected: (WifiNetwork) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Networks on Map",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${networks.size}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Network list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(networks) { network ->
                NetworkListItem(
                    network = network,
                    onClick = { onNetworkSelected(network) }
                )
            }
        }
    }
}

/**
 * Individual network item in the list
 */
@Composable
private fun NetworkListItem(
    network: WifiNetwork,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "item_scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable {
                isPressed = true
                onClick()
                isPressed = false
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal strength indicator
            SignalStrengthIndicator(
                rssi = network.rssi,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Network info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = network.bssid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${network.rssi} dBm • ${network.security}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            // Location icon
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = "Show on map",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Signal strength visual indicator
 */
@Composable
private fun SignalStrengthIndicator(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val (color, bars) = when {
        rssi >= -50 -> MaterialTheme.colorScheme.primary to 4
        rssi >= -70 -> Color(0xFFFFB300) to 3
        rssi >= -80 -> Color(0xFFFF8F00) to 2
        else -> MaterialTheme.colorScheme.error to 1
    }
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (bars) {
                4 -> Icons.Default.SignalWifi4Bar
                3 -> Icons.Default.SignalWifi4Bar
                2 -> Icons.Default.Wifi
                else -> Icons.Default.SignalWifiOff
            },
            contentDescription = "Signal: $rssi dBm",
            tint = color,
            modifier = Modifier.fillMaxSize()
        )
    }
}