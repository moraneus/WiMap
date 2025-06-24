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
import com.ner.wimap.camera.rememberCameraLauncher
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
    onUpdateNetworkData: (WifiNetwork, String?, String?, String?) -> Unit = { _, _, _, _ -> }
) {
    val isPinnedInitially = pinnedNetworks.any { it.bssid == network.bssid }
    val pinnedNetwork = pinnedNetworks.find { it.bssid == network.bssid }
    var isPinned by remember(network.bssid) { mutableStateOf(isPinnedInitially) }
    var showDetails by remember { mutableStateOf(false) }
    var comment by remember(pinnedNetwork?.bssid) { mutableStateOf(pinnedNetwork?.comment ?: "") }
    var savedPassword by remember(pinnedNetwork?.bssid) { mutableStateOf(pinnedNetwork?.savedPassword ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var photoUri by remember(pinnedNetwork?.bssid) { mutableStateOf<Uri?>(pinnedNetwork?.photoUri?.let { Uri.parse(it) }) }

    // Update local state when pinnedNetwork changes
    LaunchedEffect(pinnedNetwork) {
        comment = pinnedNetwork?.comment ?: ""
        savedPassword = pinnedNetwork?.savedPassword ?: ""
        photoUri = pinnedNetwork?.photoUri?.let { Uri.parse(it) }
    }

    val context = LocalContext.current
    val isOpenNetwork = network.security.contains("Open", ignoreCase = true) ||
            network.security.contains("OPEN", ignoreCase = true)

    // Check if this network has a valid password (either saved locally or from successful connection)
    val hasValidPassword = savedPassword.isNotEmpty() || successfulPasswords.containsKey(network.bssid)

    // Camera launcher
    val cameraLauncher = rememberCameraLauncher(
        onPhotoTaken = { uri ->
            photoUri = uri
            onUpdateNetworkData(network, comment, savedPassword, uri.toString())
            Toast.makeText(context, context.getString(R.string.photo_captured_successfully), Toast.LENGTH_SHORT).show()
        },
        onError = { error ->
            Toast.makeText(context, "Camera error: $error", Toast.LENGTH_SHORT).show()
        }
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(if (isPinned) 12.dp else 4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPinned) Color(0xFFFFF3E0) else Color.White
        ),
        border = if (isPinned) BorderStroke(2.dp, Color(0xFF667eea)) else null // Use main app color
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
                color = Color(0xFF7F8C8D),
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
                        // Launch real camera
                        cameraLauncher()
                    } else {
                        // Remove existing photo
                        photoUri = null
                        onUpdateNetworkData(network, comment, savedPassword, null)
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
    }    }

@Composable
private fun NetworkHeader(
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
                    tint = Color(0xFF667eea), // Use main app color
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
                            isConnecting -> Color(0xFFBDC3C7)
                            hasValidPassword -> Color(0xFF27AE60) // Green when password is known
                            else -> Color(0xFFE74C3C) // Red when password is unknown
                        },
                        CircleShape
                    )
                    .clickable(enabled = !isConnecting) { onConnectClicked() },
                contentAlignment = Alignment.Center
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
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
private fun NetworkPhoto(
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