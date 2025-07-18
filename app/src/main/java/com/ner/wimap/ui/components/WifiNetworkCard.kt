package com.ner.wimap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.ui.getSignalIcon
import com.ner.wimap.ui.getSignalColor
import com.ner.wimap.camera.rememberEnhancedCameraLauncher
import com.ner.wimap.permissions.PermissionUIHandler
import com.ner.wimap.permissions.rememberPermissionManager
import com.ner.wimap.ui.dialogs.ImageViewerDialog
import com.ner.wimap.R

@Composable
fun WifiNetworkCard(
    network: WifiNetwork,
    onConnectClicked: () -> Unit,
    pinnedNetworks: List<PinnedNetwork>,
    onPinNetwork: (WifiNetwork, String?, String?, String?) -> Unit,
    onUnpinNetwork: (String) -> Unit,
    isConnecting: Boolean = false,
    successfulPasswords: Map<String, String> = emptyMap(),
    onUpdateNetworkData: (WifiNetwork, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onUpdateNetworkDataWithPhotoDeletion: (WifiNetwork, String?, String?, String?, Boolean) -> Unit = { network, comment, password, photoUri, _ -> onUpdateNetworkData(network, comment, password, photoUri) }
) {
    val isPinnedInitially = network.isPinned || pinnedNetworks.any { it.bssid == network.bssid }
    val pinnedNetwork = pinnedNetworks.find { it.bssid == network.bssid }
    var isPinned by remember(network.bssid, network.isPinned) { mutableStateOf(isPinnedInitially) }
    
    // Update pin status when network data changes
    LaunchedEffect(network.isPinned) {
        isPinned = network.isPinned
    }
    var showDetails by remember { mutableStateOf(false) }
    
    // Initialize from network data (which includes temporary data merged from database)
    var comment by remember(network.bssid, network.comment) { mutableStateOf(network.comment) }
    var savedPassword by remember(network.bssid, network.password) { mutableStateOf(network.password ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var photoUri by remember(network.bssid, network.photoPath) { 
        mutableStateOf<Uri?>(network.photoPath?.let { Uri.parse(it) }) 
    }

    // Update local state when network data changes (includes temporary data)
    LaunchedEffect(network.comment, network.password, network.photoPath) {
        comment = network.comment
        savedPassword = network.password ?: ""
        photoUri = network.photoPath?.let { Uri.parse(it) }
    }
    
    // Also update from pinned network data for backward compatibility
    LaunchedEffect(pinnedNetwork) {
        if (pinnedNetwork != null) {
            // Only update if the network data doesn't already have this info
            if (comment.isEmpty() && !pinnedNetwork.comment.isNullOrEmpty()) {
                comment = pinnedNetwork.comment!!
            }
            if (savedPassword.isEmpty() && !pinnedNetwork.savedPassword.isNullOrEmpty()) {
                savedPassword = pinnedNetwork.savedPassword!!
            }
            if (photoUri == null && !pinnedNetwork.photoUri.isNullOrEmpty()) {
                photoUri = Uri.parse(pinnedNetwork.photoUri!!)
            }
        }
    }

    val context = LocalContext.current
    val isOpenNetwork = network.security.contains("Open", ignoreCase = true) ||
            network.security.contains("OPEN", ignoreCase = true)
    
    // Check if network has any attached data (comments, passwords, or photos)
    val hasAttachedData = comment.isNotEmpty() || 
                         savedPassword.isNotEmpty() || 
                         photoUri != null

    // Check if this network has a valid password (either saved locally or from successful connection)
    val hasValidPassword = savedPassword.isNotEmpty() || successfulPasswords.containsKey(network.bssid)

    // Enhanced camera launcher with permission handling
    val permissionManager = rememberPermissionManager()
    val cameraLauncher = rememberEnhancedCameraLauncher(
        onPhotoTaken = { uri ->
            photoUri = uri
            onUpdateNetworkData(network, comment, savedPassword, uri.toString())
            Toast.makeText(context, context.getString(R.string.photo_captured_successfully), Toast.LENGTH_SHORT).show()
        },
        onError = { error ->
            Toast.makeText(context, "Camera error: $error", Toast.LENGTH_SHORT).show()
        },
        onPermissionDenied = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = when {
                    network.isOffline -> 2.dp // Reduced shadow for offline
                    isPinned -> 12.dp
                    hasAttachedData -> 6.dp
                    else -> 4.dp
                }, 
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                network.isOffline -> Color(0xFFE8E8E8) // Uniform gray for offline networks
                isPinned -> Color(0xFFFFF3E0) // Orange tint for pinned
                hasAttachedData -> Color(0xFFF0F8FF) // Light blue tint for attached data
                else -> Color.White
            }
        ),
        border = when {
            network.isOffline -> BorderStroke(1.5.dp, Color(0xFF95A5A6)) // Gray border for offline
            isPinned -> BorderStroke(2.dp, Color(0xFF667eea)) // Blue border for pinned
            hasAttachedData -> BorderStroke(1.dp, Color(0xFF87CEEB)) // Light blue border for attached data
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Section
            NetworkHeader(
                network = network,
                isPinned = isPinned,
                isOpenNetwork = isOpenNetwork,
                isConnecting = isConnecting,
                hasValidPassword = hasValidPassword,
                onConnectClicked = onConnectClicked
            )

            // BSSID
            Text(
                text = network.bssid,
                style = MaterialTheme.typography.bodySmall,
                color = if (network.isOffline) Color(0xFF95A5A6) else Color(0xFF7F8C8D), // Gray when offline
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Photo thumbnail
            NetworkPhoto(
                photoUri = photoUri,
                onPhotoClick = { showImageViewer = true }
            )

            // Info chips
            NetworkInfoChips(
                network = network,
                hasPhoto = photoUri != null
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            NetworkActionButtons(
                isOpenNetwork = isOpenNetwork,
                showDetails = showDetails,
                onToggleDetails = { showDetails = !showDetails },
                onShowCommentDialog = { showCommentDialog = true },
                onShowPasswordDialog = { showPasswordDialog = true },
                onCameraClick = {
                    if (photoUri == null) {
                        // Check permissions and launch camera
                        if (permissionManager.hasCameraPermissions()) {
                            cameraLauncher.launch()
                        } else {
                            // Request permissions first
                            permissionManager.requestCameraPermissions { granted ->
                                if (granted) {
                                    cameraLauncher.launch()
                                }
                            }
                        }
                    } else {
                        // Remove existing photo - use clearPhoto flag to ensure permanent deletion
                        photoUri = null
                        onUpdateNetworkDataWithPhotoDeletion(network, comment, savedPassword, null, true)
                        Toast.makeText(context, "Photo removed!", Toast.LENGTH_SHORT).show()
                    }
                },
                onPinClick = {
                    if (isPinned) {
                        onUnpinNetwork(network.bssid)
                        isPinned = false
                    } else {
                        onPinNetwork(network, comment, savedPassword, photoUri?.toString())
                        isPinned = true
                    }
                },
                isPinned = isPinned
            )

            // Expandable details
            if (showDetails) {
                NetworkDetails(
                    network = network,
                    comment = comment,
                    savedPassword = savedPassword,
                    isOpenNetwork = isOpenNetwork,
                    hasPhoto = photoUri != null,
                    successfulPasswords = successfulPasswords // Add this line
                )
            }
        }
    }

    // Comment Dialog
    if (showCommentDialog) {
        NetworkCommentDialog(
            network = network,
            initialComment = comment,
            onCommentSaved = { newComment ->
                comment = newComment
                onUpdateNetworkData(network, newComment, savedPassword, photoUri?.toString())
                showCommentDialog = false
            },
            onDismiss = { showCommentDialog = false }
        )
    }

    // Password Dialog
    if (showPasswordDialog && !isOpenNetwork) {
        NetworkPasswordDialog(
            network = network,
            initialPassword = savedPassword,
            onPasswordSaved = { newPassword ->
                savedPassword = newPassword
                onUpdateNetworkData(network, comment, newPassword, photoUri?.toString())
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false }
        )
    }

    // Image Viewer Dialog
    photoUri?.let { uri ->
        if (showImageViewer) {
            ImageViewerDialog(
                imageUri = uri,
                networkName = network.ssid,
                onDismiss = { showImageViewer = false }
            )
        }
    }
    
    // Handle permission UI dialogs and snackbars
    PermissionUIHandler(permissionManager = permissionManager)
}

@Composable
internal fun NetworkHeader(
    network: WifiNetwork,
    isPinned: Boolean,
    isOpenNetwork: Boolean,
    isConnecting: Boolean,
    hasValidPassword: Boolean,
    onConnectClicked: () -> Unit
) {
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
            Text(
                text = network.ssid,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (network.isOffline) Color(0xFF6B6B6B) else Color(0xFF2C3E50), // Muted text for offline
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = getSignalIcon(network.rssi),
                contentDescription = "Signal Strength",
                tint = if (network.isOffline) Color(0xFF9E9E9E) else getSignalColor(network.rssi), // Muted signal for offline
                modifier = Modifier.size(16.dp)
            )

            // Security icon with special indicator for valid passwords
            if (isOpenNetwork) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Open Network",
                    tint = Color(0xFFE74C3C),
                    modifier = Modifier.size(14.dp)
                )
            } else if (hasValidPassword) {
                // Show key icon for networks with valid passwords
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Valid Password Available",
                    tint = Color(0xFF27AE60), // Green for valid password
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secured Network",
                    tint = Color(0xFF95A5A6), // Gray for unknown password
                    modifier = Modifier.size(14.dp)
                )
            }
            if (isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = if (network.isOffline) Color(0xFF8B7355) else Color(0xFF667eea), // Muted color for offline pinned
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Connect button with dynamic icon based on password status
        if (!isOpenNetwork) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when {
                            network.isOffline -> Color(0xFFBDC3C7) // Grey for offline networks
                            isConnecting -> Color(0xFFBDC3C7)
                            hasValidPassword -> Color(0xFF27AE60) // Green when password is known
                            else -> Color(0xFFE74C3C) // Red when password is unknown
                        },
                        CircleShape
                    )
                    .clickable(enabled = !isConnecting && !hasValidPassword && !network.isOffline) { onConnectClicked() },
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (network.isOffline) {
                    // Show offline icon for offline networks
                    Icon(
                        imageVector = Icons.Default.SignalWifiOff,
                        contentDescription = "Network Offline",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    // Dynamic icon based on password status
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
    }
}

@Composable
internal fun NetworkPhoto(
    photoUri: Uri?,
    onPhotoClick: () -> Unit
) {
    photoUri?.let { uri ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable { onPhotoClick() },
            shape = RoundedCornerShape(8.dp)
        ) {
            Box {
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = "Network Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Overlay to indicate it's clickable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "View full image",
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .padding(8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}