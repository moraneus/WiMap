package com.ner.wimap.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import android.net.Uri
import com.ner.wimap.R
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.components.InfoChip
import com.ner.wimap.ui.components.DetailRow
import com.ner.wimap.ui.components.ExportFormatDialog
import com.ner.wimap.ui.components.UnifiedTopAppBar
import com.ner.wimap.ui.components.EnhancedWifiNetworkCard
import com.ner.wimap.ui.getSignalIcon
import com.ner.wimap.ui.getSignalColor
import com.ner.wimap.ui.viewmodel.ExportFormat
import com.ner.wimap.ui.viewmodel.ExportAction
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PinnedNetworksScreen(
    pinnedNetworks: List<PinnedNetwork>,
    successfulPasswords: Map<String, String> = emptyMap(),
    connectingNetworks: Set<String> = emptySet(),
    onBack: () -> Unit,
    onDeletePinnedNetwork: (PinnedNetwork) -> Unit,
    onConnectToPinnedNetwork: (PinnedNetwork) -> Unit,
    onSharePinnedNetwork: (PinnedNetwork) -> Unit = { },
    onExportPinnedNetwork: (PinnedNetwork, ExportFormat, ExportAction) -> Unit = { _, _, _ -> },
    onUpdatePinnedNetworkData: (bssid: String, ssid: String, comment: String?, password: String?, photoPath: String?, clearPhoto: Boolean) -> Unit = { _, _, _, _, _, _ -> }
) {
    var showActionMenu by remember { mutableStateOf(false) }
    var selectedNetwork by remember { mutableStateOf<PinnedNetwork?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // Unified Material 3 top bar
        UnifiedTopAppBar(
            title = stringResource(R.string.title_pinned_networks),
            icon = Icons.Default.PushPin,
            onBack = onBack
        )

        // Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with count
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinned Networks",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "${pinnedNetworks.size}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF667eea) // Same accent color
                    )
                }
            }

            if (pinnedNetworks.isEmpty()) {
                item {
                    EmptyPinnedNetworksState()
                }
            } else {
                item {
                    Text(
                        text = "Long press on any network for more options",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF7F8C8D),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(pinnedNetworks) { network ->
                    // Convert PinnedNetwork to WifiNetwork for EnhancedWifiNetworkCard
                    val wifiNetwork = WifiNetwork(
                        ssid = network.ssid,
                        bssid = network.bssid,
                        rssi = network.rssi,
                        channel = network.channel,
                        security = network.security,
                        latitude = network.latitude,
                        longitude = network.longitude,
                        timestamp = network.timestamp,
                        comment = network.comment ?: "",
                        password = network.savedPassword,
                        photoPath = network.photoUri,
                        isPinned = true // Always true for pinned networks screen
                    )
                    
                    EnhancedWifiNetworkCard(
                        network = wifiNetwork,
                        isConnecting = connectingNetworks.contains(network.bssid),
                        connectionStatus = null,
                        onPinClick = { bssid, isPinned ->
                            // On unpin, delete the network
                            if (!isPinned) {
                                onDeletePinnedNetwork(network)
                            }
                        },
                        onConnectClick = { _ ->
                            onConnectToPinnedNetwork(network)
                        },
                        onCancelConnectionClick = { /* Not needed for pinned networks */ },
                        onMoreInfoClick = { _ ->
                            selectedNetwork = network
                            showActionMenu = true
                        },
                        onUpdateData = { bssid, ssid, comment, password, photoPath ->
                            onUpdatePinnedNetworkData(bssid, ssid, comment, password, photoPath, false)
                        },
                        onUpdateDataWithPhotoDeletion = { bssid, ssid, comment, password, photoPath, clearPhoto ->
                            onUpdatePinnedNetworkData(bssid, ssid, comment, password, photoPath, clearPhoto)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    // Action menu dialog
    if (showActionMenu && selectedNetwork != null) {
        AlertDialog(
            onDismissRequest = { showActionMenu = false },
            title = {
                Text(
                    "Actions for ${selectedNetwork!!.ssid}",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2C3E50)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionMenuItem(
                        icon = Icons.Default.Share,
                        text = "Share Network",
                        color = Color(0xFF3498DB),
                        onClick = {
                            onSharePinnedNetwork(selectedNetwork!!)
                            showActionMenu = false
                        }
                    )
                    ActionMenuItem(
                        icon = Icons.Default.Save,
                        text = "Export as...",
                        color = Color(0xFF27AE60),
                        onClick = {
                            showExportDialog = true
                            showActionMenu = false
                        }
                    )
                    ActionMenuItem(
                        icon = Icons.Default.Delete,
                        text = "Delete Network",
                        color = Color(0xFFE74C3C),
                        onClick = {
                            onDeletePinnedNetwork(selectedNetwork!!)
                            showActionMenu = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showActionMenu = false }
                ) {
                    Text("Cancel", color = Color(0xFF95A5A6))
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Export dialog for pinned networks
    if (showExportDialog && selectedNetwork != null) {
        ExportFormatDialog(
            title = "Export ${selectedNetwork!!.ssid}",
            onFormatAndActionSelected = { format, action ->
                onExportPinnedNetwork(selectedNetwork!!, format, action)
                showExportDialog = false
                selectedNetwork = null
            },
            onDismiss = {
                showExportDialog = false
                selectedNetwork = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernPinnedNetworkCard(
    network: PinnedNetwork,
    successfulPasswords: Map<String, String> = emptyMap(),
    connectingNetworks: Set<String> = emptySet(),
    onLongPress: () -> Unit,
    onConnect: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showDetails by remember { mutableStateOf(false) }

    // Declare these variables once at the top level of the composable
    val isOpenNetwork = network.security.contains("Open", ignoreCase = true)
    // Check both saved password and successful passwords from connection attempts
    val hasValidPassword = !network.savedPassword.isNullOrEmpty() || successfulPasswords.containsKey(network.bssid)
    val isConnecting = connectingNetworks.contains(network.bssid)

    // Get the actual password to display (prefer successful password over saved)
    val displayPassword = successfulPasswords[network.bssid] ?: network.savedPassword ?: ""

    // Use the same modern card design as main screen
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(16.dp)) // Same shadow as pinned cards in main
            .combinedClickable(
                onClick = onConnect,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0) // Same orange tinted background
        ),
        border = BorderStroke(2.dp, Color(0xFF667eea)) // Use main app color instead of orange
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Section - same as main screen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = Color(0xFF667eea),
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = network.ssid,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF2C3E50),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Icon(
                        imageVector = getSignalIcon(network.rssi),
                        contentDescription = "Signal Strength",
                        tint = getSignalColor(network.rssi),
                        modifier = Modifier.size(16.dp)
                    )

                    // Security icon with password status
                    if (isOpenNetwork) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Open Network",
                            tint = Color(0xFFE74C3C),
                            modifier = Modifier.size(14.dp)
                        )
                    } else if (hasValidPassword) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Valid Password Available",
                            tint = Color(0xFF27AE60),
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secured Network",
                            tint = Color(0xFF95A5A6),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Connect button with dynamic icon based on password status
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            when {
                                isConnecting -> Color(0xFFBDC3C7) // Gray when connecting
                                hasValidPassword -> Color(0xFF27AE60) // Green when password known
                                else -> Color(0xFFE74C3C) // Red when password unknown
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (hasValidPassword) {
                                Icons.Default.CheckCircle // Green checkmark when password is known
                            } else {
                                Icons.Default.VpnKey // Red key when trying to break password
                            },
                            contentDescription = if (hasValidPassword) "Connect with Known Password" else "Break Password",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // BSSID
            Text(
                text = network.bssid,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7F8C8D),
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Photo thumbnail - same as main screen
            network.photoUri?.let { uri ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(uri)),
                        contentDescription = "Network Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Info chips - same as main screen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoChip("${network.rssi}dBm", Color(0xFF9B59B6))
                InfoChip("Ch${network.channel}", Color(0xFFE67E22))
                InfoChip(network.security, Color(0xFF16A085))

                if (network.latitude != null && network.longitude != null &&
                    network.latitude != 0.0 && network.longitude != 0.0) {
                    InfoChip("GPS", Color(0xFFE74C3C))
                }

                if (!network.photoUri.isNullOrEmpty()) {
                    InfoChip("📷", Color(0xFF8E44AD))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pinned info
            Text(
                text = "Pinned on ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(network.pinnedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF667eea), // Use main app color
                fontWeight = FontWeight.Medium
            )

            // Expandable details - same as main screen
            if (showDetails) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF8F9FA)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!network.comment.isNullOrEmpty()) {
                            DetailRow("Comment", network.comment)
                        }

                        if (!displayPassword.isEmpty()) {
                            DetailRow("Password", displayPassword)
                        }

                        if (network.latitude != null && network.longitude != null &&
                            network.latitude != 0.0 && network.longitude != 0.0) {
                            DetailRow("GPS", "${String.format("%.4f", network.latitude)}, ${String.format("%.4f", network.longitude)}")
                        }

                        DetailRow("Frequency", "${if (network.channel <= 14) "2.4" else "5"} GHz")
                        DetailRow("Timestamp", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(network.timestamp)))
                        if (!network.photoUri.isNullOrEmpty()) {
                            DetailRow("Photo", "Attached")
                        }
                    }
                }
            }

            // Toggle details button
            TextButton(
                onClick = { showDetails = !showDetails },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF667eea) // Use main app color
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    if (showDetails) "Hide Details" else "Show Details",
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .combinedClickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = color
        )
    }
}

@Composable
fun EmptyPinnedNetworksState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = null,
            tint = Color(0xFFBDC3C7),
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "No Pinned Networks",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = Color(0xFF7F8C8D)
        )
        Text(
            text = "Pin networks from the main screen to access them quickly here",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFBDC3C7),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}