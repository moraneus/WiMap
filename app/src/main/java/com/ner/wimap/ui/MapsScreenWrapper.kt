package com.ner.wimap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.components.UnifiedTopAppBar

/**
 * A wrapper composable that displays the Google Maps functionality
 * integrated into the swipe navigation system
 */
@Composable
fun MapsScreenWrapper(
    wifiNetworks: List<WifiNetwork>,
    onBack: () -> Unit,
    onNavigateToPage: (Int) -> Unit = {},
    currentPage: Int = 2
) {
    val context = LocalContext.current
    var mapView: MapView? by remember { mutableStateOf(null) }
    var googleMap: GoogleMap? by remember { mutableStateOf(null) }
    
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
        
        // Map content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context).apply {
                        onCreate(null)
                        getMapAsync { map ->
                            googleMap = map
                            setupMap(map, wifiNetworks)
                        }
                        mapView = this
                    }
                },
                update = { view ->
                    // Update map when networks change
                    googleMap?.let { map ->
                        setupMap(map, wifiNetworks)
                    }
                }
            )
            
            // Network count indicator
            if (wifiNetworks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C3E50).copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${wifiNetworks.size}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "Networks",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
    
    // Handle lifecycle
    DisposableEffect(mapView) {
        onDispose {
            mapView?.onDestroy()
        }
    }
}

private fun setupMap(googleMap: GoogleMap, wifiNetworks: List<WifiNetwork>) {
    googleMap.clear()
    
    val networksWithLocation = wifiNetworks.filter { network ->
        network.peakRssiLatitude != null && 
        network.peakRssiLongitude != null &&
        network.peakRssiLatitude != 0.0 && 
        network.peakRssiLongitude != 0.0
    }
    
    if (networksWithLocation.isNotEmpty()) {
        // Add markers for networks with location data
        networksWithLocation.forEach { network ->
            val position = LatLng(
                network.peakRssiLatitude!!,
                network.peakRssiLongitude!!
            )
            
            googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(network.ssid)
                    .snippet("RSSI: ${network.rssi} dBm • ${network.security}")
            )
        }
        
        // Center camera on the first network or calculate bounds
        val firstNetwork = networksWithLocation.first()
        val cameraPosition = LatLng(
            firstNetwork.peakRssiLatitude!!,
            firstNetwork.peakRssiLongitude!!
        )
        
        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(cameraPosition, 15f)
        )
    }
    
    // Enable map UI controls
    googleMap.uiSettings.apply {
        isZoomControlsEnabled = true
        isMapToolbarEnabled = true
        isMyLocationButtonEnabled = true
    }
}