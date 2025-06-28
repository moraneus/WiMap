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
import com.ner.wimap.data.database.TemporaryNetworkData
import com.ner.wimap.ui.getSignalIcon
import com.ner.wimap.ui.getSignalColor
import com.ner.wimap.camera.rememberCameraLauncher
import com.ner.wimap.ui.dialogs.ImageViewerDialog
import com.ner.wimap.R

@Composable
fun EnhancedWifiNetworkCard(
    network: WifiNetwork,
    isConnecting: Boolean,
    connectionStatus: String?,
    onPinClick: (String, Boolean) -> Unit,
    onConnectClick: (WifiNetwork) -> Unit,
    onCancelConnectionClick: () -> Unit,
    onMoreInfoClick: (WifiNetwork) -> Unit,
    onUpdateData: (bssid: String, ssid: String, comment: String, password: String?, photoPath: String?) -> Unit,
    onUpdateDataWithPhotoDeletion: (bssid: String, ssid: String, comment: String, password: String?, photoPath: String?, clearPhoto: Boolean) -> Unit = { bssid, ssid, comment, password, photoPath, _ -> onUpdateData(bssid, ssid, comment, password, photoPath) },
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }
    var comment by remember(network.bssid, network.comment) { mutableStateOf(network.comment) }
    var savedPassword by remember(network.bssid, network.password) { mutableStateOf(network.password ?: "") }
    var photoPath by remember(network.bssid, network.photoPath) { mutableStateOf(network.photoPath) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isOpenNetwork = network.security.contains("Open", ignoreCase = true) ||
            network.security.contains("OPEN", ignoreCase = true)

    // Check if this network has a valid password (either saved locally or from successful connection)
    // Camera launcher
    val cameraLauncher = rememberCameraLauncher(
        onPhotoTaken = { uri ->
            photoPath = uri.toString()
            onUpdateData(network.bssid, network.ssid, comment, savedPassword, photoPath)
            Toast.makeText(context, context.getString(R.string.photo_captured_successfully), Toast.LENGTH_SHORT).show()
        },
        onError = { error ->
            Toast.makeText(context, "Camera error: $error", Toast.LENGTH_SHORT).show()
        }
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(if (network.isPinned) 12.dp else 4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (network.isPinned) Color(0xFFFFF3E0) else Color.White
        ),
        border = if (network.isPinned) BorderStroke(2.dp, Color(0xFF667eea)) else null // Use main app color
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Section
            NetworkHeader(
                network = network,
                isPinned = network.isPinned,
                isOpenNetwork = isOpenNetwork,
                isConnecting = isConnecting,
                hasValidPassword = savedPassword.isNotEmpty(),
                onConnectClicked = {
                    onConnectClick(network)
                }
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
                photoUri = photoPath?.let { Uri.parse(it) },
                onPhotoClick = { showImageViewer = true }
            )

            // Info chips
            NetworkInfoChips(
                network = network,
                hasPhoto = photoPath != null
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
                    if (photoPath == null) {
                        // Launch real camera
                        cameraLauncher()
                    } else {
                        // Remove existing photo - use clearPhoto flag to ensure permanent deletion
                        photoPath = null
                        onUpdateDataWithPhotoDeletion(network.bssid, network.ssid, comment, savedPassword, null, true)
                        Toast.makeText(context, "Photo removed!", Toast.LENGTH_SHORT).show()
                    }
                },
                onPinClick = {
                    onPinClick(network.bssid, !network.isPinned)
                },
                isPinned = network.isPinned
            )

            // Expandable details
            if (showDetails) {
                NetworkDetails(
                    network = network,
                    comment = comment,
                    savedPassword = savedPassword,
                    isOpenNetwork = isOpenNetwork,
                    hasPhoto = photoPath != null
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
                onUpdateData(network.bssid, network.ssid, newComment, savedPassword, photoPath)
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
                onUpdateData(network.bssid, network.ssid, comment, newPassword, photoPath)
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false }
        )
    }

    // Image Viewer Dialog
    photoPath?.let { path ->
        if (showImageViewer) {
            ImageViewerDialog(
                imageUri = Uri.parse(path),
                networkName = network.ssid,
                onDismiss = { showImageViewer = false }
            )
        }
    }
}