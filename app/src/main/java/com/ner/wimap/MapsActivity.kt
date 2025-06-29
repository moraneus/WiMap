package com.ner.wimap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.theme.WiMapTheme
import com.ner.wimap.ui.components.InfoChip
import com.ner.wimap.ui.components.UnifiedTopAppBar
import com.ner.wimap.ui.components.UnifiedTopBarActionButton
import com.ner.wimap.ui.getSignalColor
import com.ner.wimap.utils.OUILookupManager
import java.text.SimpleDateFormat
import java.util.*
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

data class ParcelableWifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val channel: Int,
    val security: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val password: String? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readValue(Double::class.java.classLoader) as? Double,
        parcel.readValue(Double::class.java.classLoader) as? Double,
        parcel.readLong(),
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(ssid)
        parcel.writeString(bssid)
        parcel.writeInt(rssi)
        parcel.writeInt(channel)
        parcel.writeString(security)
        parcel.writeValue(latitude)
        parcel.writeValue(longitude)
        parcel.writeLong(timestamp)
        parcel.writeString(password)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ParcelableWifiNetwork> {
        override fun createFromParcel(parcel: Parcel): ParcelableWifiNetwork {
            return ParcelableWifiNetwork(parcel)
        }

        override fun newArray(size: Int): Array<ParcelableWifiNetwork?> {
            return arrayOfNulls(size)
        }
    }

    fun toWifiNetwork(): WifiNetwork {
        return WifiNetwork(ssid, bssid, rssi, channel, security, latitude, longitude, timestamp, password)
    }
}

fun WifiNetwork.toParcelable(): ParcelableWifiNetwork {
    return ParcelableWifiNetwork(ssid, bssid, rssi, channel, security, latitude, longitude, timestamp, password)
}

/**
 * ClusterItem wrapper for WifiNetwork to enable clustering
 */
data class WifiNetworkClusterItem(
    val network: WifiNetwork
) : ClusterItem {
    override fun getPosition(): LatLng {
        return LatLng(network.latitude ?: 0.0, network.longitude ?: 0.0)
    }
    
    override fun getTitle(): String {
        return "SSID: ${network.ssid}"
    }
    
    override fun getSnippet(): String {
        return "BSSID: ${network.bssid}"
    }
    
    override fun getZIndex(): Float? {
        return null
    }
}

class MapsActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_NETWORKS = "extra_networks"

        fun createIntent(context: Context, networks: List<WifiNetwork>): Intent {
            return Intent(context, MapsActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_NETWORKS, ArrayList(networks.map { it.toParcelable() }))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parcelableNetworks = intent.getParcelableArrayListExtra<ParcelableWifiNetwork>(EXTRA_NETWORKS) ?: emptyList()
        val networks = parcelableNetworks.map { it.toWifiNetwork() }

        setContent {
            WiMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WiFiMapsScreen(
                        networks = networks,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiMapsScreen(
    networks: List<WifiNetwork>,
    onBack: () -> Unit
) {
    var selectedNetwork by remember { mutableStateOf<WifiNetwork?>(null) }
    var showNetworksList by remember { mutableStateOf(false) }
    val networksWithLocation = networks.filter {
        it.latitude != null && it.longitude != null &&
                it.latitude != 0.0 && it.longitude != 0.0
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Unified top bar
        UnifiedTopAppBar(
            title = "Map View",
            icon = Icons.Default.Map,
            onBack = onBack,
            actions = {
                // Networks count
                Text(
                    "${networksWithLocation.size}/${networks.size}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                // List/Map toggle button
                UnifiedTopBarActionButton(
                    icon = if (showNetworksList) Icons.Default.Map else Icons.Default.List,
                    contentDescription = if (showNetworksList) "Show Map" else "Show List",
                    onClick = { showNetworksList = !showNetworksList }
                )
            }
        )

        if (showNetworksList) {
            // Networks list view
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Networks with GPS Location",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF2C3E50)
                    )
                }

                if (networksWithLocation.isEmpty()) {
                    item {
                        EmptyLocationState()
                    }
                } else {
                    items(networksWithLocation) { network ->
                        NetworkMapCard(
                            network = network,
                            onClick = {
                                selectedNetwork = network
                                showNetworksList = false
                            }
                        )
                    }
                }
            }
        } else {
            // Map view
            if (networksWithLocation.isEmpty()) {
                EmptyLocationState()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    GoogleMapView(
                        networks = networksWithLocation,
                        selectedNetwork = selectedNetwork,
                        onNetworkSelected = { selectedNetwork = it }
                    )

                    // Selected network info overlay
                    selectedNetwork?.let { network ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .align(Alignment.BottomCenter),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            NetworkInfoOverlay(
                                network = network,
                                onClose = { selectedNetwork = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleMapView(
    networks: List<WifiNetwork>,
    selectedNetwork: WifiNetwork?,
    onNetworkSelected: (WifiNetwork?) -> Unit
) {
    AndroidView(
        factory = { context ->
            MapView(context).apply {
                onCreate(Bundle())
                getMapAsync { googleMap ->
                    setupMap(googleMap, networks, selectedNetwork, onNetworkSelected, context)
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { mapView ->
        mapView.getMapAsync { googleMap ->
            setupMap(googleMap, networks, selectedNetwork, onNetworkSelected, mapView.context)
        }
    }
}

private fun setupMap(
    googleMap: GoogleMap,
    networks: List<WifiNetwork>,
    selectedNetwork: WifiNetwork?,
    onNetworkSelected: (WifiNetwork?) -> Unit,
    context: Context
) {
    googleMap.clear()
    
    // Set up cluster manager
    val clusterManager = ClusterManager<WifiNetworkClusterItem>(context, googleMap)
    val clusterRenderer = WifiNetworkClusterRenderer(context, googleMap, clusterManager)
    clusterManager.renderer = clusterRenderer
    
    // Set custom info window adapter for both clusters and individual markers
    googleMap.setInfoWindowAdapter(CustomInfoWindowAdapter(context))
    clusterManager.markerCollection.setInfoWindowAdapter(CustomInfoWindowAdapter(context))

    val bounds = LatLngBounds.Builder()
    var hasValidLocation = false

    // Add networks to cluster manager instead of directly to map
    networks.forEach { network ->
        if (network.latitude != null && network.longitude != null) {
            val position = LatLng(network.latitude, network.longitude)
            bounds.include(position)
            hasValidLocation = true
            
            // Create cluster item and add to cluster manager
            val clusterItem = WifiNetworkClusterItem(network)
            clusterManager.addItem(clusterItem)
        }
    }
    
    // Cluster the markers
    clusterManager.cluster()

    // Set click listeners on cluster manager
    clusterManager.setOnClusterItemClickListener { clusterItem ->
        val network = clusterItem.network
        onNetworkSelected(network)
        true
    }
    
    clusterManager.setOnClusterItemInfoWindowClickListener { clusterItem ->
        val network = clusterItem.network
        onNetworkSelected(network)
    }

    // Set up map click listeners
    googleMap.setOnCameraIdleListener(clusterManager)
    googleMap.setOnMarkerClickListener(clusterManager)
    googleMap.setOnInfoWindowClickListener(clusterManager)

    googleMap.setOnMapClickListener {
        onNetworkSelected(null)
    }

    if (hasValidLocation) {
        try {
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds.build(), 100)
            )
        } catch (e: Exception) {
            // Fallback to a default location if bounds are invalid
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(31.7767, 35.2345), 10f)
            )
        }
    }
}

@Composable
fun NetworkMapCard(
    network: WifiNetwork,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF2C3E50),
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Has Location",
                    tint = Color(0xFF27AE60),
                    modifier = Modifier.size(20.dp)
                )
            }

            // BSSID with Vendor - real lookup with fallback
            var vendor by remember { mutableStateOf<String?>(null) }
            
            LaunchedEffect(network.bssid) {
                // Wait for database to initialize if needed
                var attempts = 0
                while (attempts < 5 && !OUILookupManager.getInstance().isReady()) {
                    kotlinx.coroutines.delay(500)
                    attempts++
                }
                
                if (OUILookupManager.getInstance().isReady()) {
                    vendor = OUILookupManager.getInstance().lookupVendorShort(network.bssid)
                } else {
                    // Fallback to hardcoded for common vendors
                    vendor = when {
                        network.bssid.startsWith("00:00:0C", ignoreCase = true) -> "Cisco"
                        network.bssid.startsWith("3C:5A:B4", ignoreCase = true) -> "Google"
                        network.bssid.startsWith("A4:2B:B0", ignoreCase = true) -> "TP-Link"
                        network.bssid.startsWith("00:03:93", ignoreCase = true) -> "Apple"
                        network.bssid.startsWith("F8:32:E4", ignoreCase = true) -> "Apple"
                        else -> null
                    }
                }
            }
            
            val bssidText = if (vendor != null) {
                "${network.bssid} ($vendor)"
            } else {
                network.bssid
            }
            
            Text(
                text = bssidText,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7F8C8D)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoChip("${network.rssi}dBm", getSignalColor(network.rssi))
                InfoChip("Ch${network.channel}", Color(0xFFE67E22))
                InfoChip(network.security, Color(0xFF16A085))

                if (!network.password.isNullOrEmpty()) {
                    InfoChip("🔑", Color(0xFF27AE60))
                }
            }

            if (network.latitude != null && network.longitude != null) {
                Text(
                    text = "${String.format("%.6f", network.latitude)}, ${String.format("%.6f", network.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF34495E)
                )
            }
        }
    }
}

@Composable
fun NetworkInfoOverlay(
    network: WifiNetwork,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = network.ssid,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF2C3E50),
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFF95A5A6)
                )
            }
        }

        // BSSID with Vendor - use direct lookup since this is not in a composable
        val vendor = OUILookupManager.getInstance().lookupVendorShort(network.bssid)
        val bssidText = if (vendor != null) {
            "${network.bssid} ($vendor)"
        } else {
            network.bssid
        }
        
        Text(
            text = bssidText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF7F8C8D)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoChip("${network.rssi}dBm", getSignalColor(network.rssi))
            InfoChip("Ch${network.channel}", Color(0xFFE67E22))
            InfoChip(network.security, Color(0xFF16A085))
            InfoChip("${if (network.channel <= 14) "2.4" else "5"} GHz", Color(0xFF9B59B6))

            if (!network.password.isNullOrEmpty()) {
                InfoChip("Password Known", Color(0xFF27AE60))
            }
        }

        if (network.latitude != null && network.longitude != null) {
            Text(
                text = "Location: ${String.format("%.6f", network.latitude)}, ${String.format("%.6f", network.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF34495E)
            )
        }

        // Use current time if timestamp is invalid (0 or too old)
        val validTimestamp = if (network.timestamp < 1000000000000L) { // Before year 2001
            System.currentTimeMillis()
        } else {
            network.timestamp
        }
        Text(
            text = "Discovered: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(validTimestamp))}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF7F8C8D)
        )
    }
}

@Composable
fun EmptyLocationState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.LocationOff,
            contentDescription = null,
            tint = Color(0xFFBDC3C7),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Networks with GPS Location",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF7F8C8D)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start scanning with GPS enabled to see networks on the map",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFBDC3C7),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Custom cluster renderer for Wi-Fi networks with enhanced markers and performance optimizations
 */
class WifiNetworkClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<WifiNetworkClusterItem>
) : DefaultClusterRenderer<WifiNetworkClusterItem>(context, map, clusterManager) {
    
    companion object {
        private const val MAX_MARKERS_ZOOM_OUT = 100 // Limit markers when zoomed out
        private const val MIN_ZOOM_FOR_ALL_MARKERS = 12f // Show all markers above this zoom
    }
    
    override fun onBeforeClusterItemRendered(item: WifiNetworkClusterItem, markerOptions: MarkerOptions) {
        val network = item.network
        
        // Set marker color based on network security and password availability
        val markerColor = when {
            !network.password.isNullOrEmpty() -> BitmapDescriptorFactory.HUE_GREEN
            network.security.contains("Open", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ORANGE
            else -> BitmapDescriptorFactory.HUE_RED
        }
        
        markerOptions
            .title("SSID: ${network.ssid}")
            .snippet("BSSID: ${network.bssid}")
            .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
    }
    
    override fun onClusterItemRendered(clusterItem: WifiNetworkClusterItem, marker: Marker) {
        super.onClusterItemRendered(clusterItem, marker)
        // Store the network data in the marker tag for info window access
        marker.tag = clusterItem.network
    }
    
    override fun shouldRenderAsCluster(cluster: com.google.maps.android.clustering.Cluster<WifiNetworkClusterItem>): Boolean {
        // Performance optimization: cluster when there are many close markers
        return cluster.size > 3
    }
    
    /**
     * Create custom marker icon with signal strength indicator
     */
    private fun createSignalMarkerIcon(network: WifiNetwork, context: Context): BitmapDescriptor {
        val markerColor = when {
            !network.password.isNullOrEmpty() -> BitmapDescriptorFactory.HUE_GREEN
            network.security.contains("Open", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ORANGE
            else -> BitmapDescriptorFactory.HUE_RED
        }
        
        // For now, return default marker. Custom drawing can be added later for more detailed icons
        return BitmapDescriptorFactory.defaultMarker(markerColor)
    }
}

/**
 * Custom info window adapter for displaying enhanced Wi-Fi network information
 */
class CustomInfoWindowAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {
    
    override fun getInfoWindow(marker: Marker): View? {
        return null // Use default info window frame
    }
    
    override fun getInfoContents(marker: Marker): View? {
        val network = marker.tag as? WifiNetwork ?: return null
        
        val view = LayoutInflater.from(context).inflate(R.layout.custom_marker_info_window, null)
        
        // Set SSID
        val ssidTextView = view.findViewById<TextView>(R.id.tv_ssid)
        ssidTextView.text = network.ssid
        
        // Set BSSID
        val bssidTextView = view.findViewById<TextView>(R.id.tv_bssid)
        bssidTextView.text = network.bssid
        
        // Set signal strength with color coding
        val signalTextView = view.findViewById<TextView>(R.id.tv_signal)
        signalTextView.text = "${network.rssi}dBm"
        
        // Color code signal strength
        val signalColor = when {
            network.rssi >= -50 -> ContextCompat.getColor(context, R.color.signal_strong)
            network.rssi >= -70 -> ContextCompat.getColor(context, R.color.signal_medium)
            else -> ContextCompat.getColor(context, R.color.signal_weak)
        }
        signalTextView.setBackgroundColor(signalColor)
        
        // Set security type
        val securityTextView = view.findViewById<TextView>(R.id.tv_security)
        securityTextView.text = network.security
        
        // Color code security type
        val securityColor = if (network.security.contains("Open", ignoreCase = true)) {
            ContextCompat.getColor(context, R.color.security_open)
        } else {
            ContextCompat.getColor(context, R.color.security_secure)
        }
        securityTextView.setBackgroundColor(securityColor)
        
        return view
    }
}