package com.ner.wimap.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.alpha
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
// Removed rememberAutoPermissionCameraLauncher - using direct camera permission handling
// Removed EnhancedPermissionUIHandler imports - camera permissions handled by AutoPermissionCameraLauncher
import com.ner.wimap.ui.dialogs.ImageViewerDialog
import com.ner.wimap.ui.dialogs.CameraPermissionRationaleDialog
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
    isPinned: Boolean? = null, // Optional override for pin status
    successfulPasswords: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }
    var comment by remember(network.bssid, network.comment) { mutableStateOf(network.comment) }
    var savedPassword by remember(network.bssid, network.password) { mutableStateOf(network.password ?: "") }
    var photoPath by remember(network.bssid, network.photoPath) { mutableStateOf(network.photoPath) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showImageViewer by remember { mutableStateOf(false) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val isOpenNetwork = network.security.contains("Open", ignoreCase = true) ||
            network.security.contains("OPEN", ignoreCase = true)
    
    // Use explicit isPinned parameter if provided, otherwise fall back to network's pin status
    val actuallyPinned = isPinned ?: network.isPinned
    
    // Check if this network has a valid password (either saved locally or from successful connection)
    val hasValidPassword = savedPassword.isNotEmpty() || successfulPasswords.containsKey(network.bssid)
    
    // Check if network has any attached data (comments, passwords, or photos)
    val hasAttachedData = comment.isNotEmpty() || 
                         savedPassword.isNotEmpty() || 
                         photoPath != null

    // Check if this network has a valid password (either saved locally or from successful connection)
    // Simple camera launcher with direct permission handling - no enhanced managers
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    val directCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            photoPath = photoUri.toString()
            onUpdateData(network.bssid, network.ssid, comment, savedPassword, photoPath)
            Toast.makeText(context, context.getString(R.string.photo_captured_successfully), Toast.LENGTH_SHORT).show()
        }
    }
    
    val directPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // Launch camera after permissions granted
            launchCameraWithUri(context, directCameraLauncher) { uri ->
                photoUri = uri
            }
        } else {
            // Simple toast - no red error dialogs
            Toast.makeText(context, "Camera access needed for photos", Toast.LENGTH_SHORT).show()
        }
    }
    
    val cameraLauncher = SimpleCameraLauncherState(
        launch = {
            val hasPermissions = com.ner.wimap.utils.PermissionUtils.hasAllCameraPermissions(context)
            
            if (hasPermissions) {
                launchCameraWithUri(context, directCameraLauncher) { uri ->
                    photoUri = uri
                }
            } else {
                // Show rationale dialog first
                showCameraPermissionDialog = true
            }
        }
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = when {
                    network.isOffline && actuallyPinned -> 8.dp // Reduced but still elevated for pinned offline
                    network.isOffline -> 2.dp // Reduced elevation for offline
                    actuallyPinned -> 12.dp
                    hasAttachedData -> 6.dp
                    else -> 4.dp
                }, 
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                when {
                    network.isOffline && actuallyPinned -> Modifier.alpha(0.75f) // Less faded for pinned offline
                    network.isOffline -> Modifier.alpha(0.6f) // More faded for regular offline
                    else -> Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                network.isOffline && actuallyPinned -> Color(0xFFF0E6D2) // Muted orange for pinned offline
                network.isOffline -> Color(0xFFF5F5F5) // Greyed out for offline
                actuallyPinned -> Color(0xFFFFF3E0) // Orange tint for pinned
                hasAttachedData -> Color(0xFFF0F8FF) // Light blue tint for attached data
                else -> Color.White
            }
        ),
        border = when {
            network.isOffline && actuallyPinned -> BorderStroke(2.dp, Color(0xFF8B7355)) // Muted pin border for offline pinned
            network.isOffline -> BorderStroke(1.dp, Color(0xFFE0E0E0)) // Grey border for offline
            actuallyPinned -> BorderStroke(2.dp, Color(0xFF667eea)) // Blue border for pinned
            hasAttachedData -> BorderStroke(1.dp, Color(0xFF87CEEB)) // Light blue border for attached data
            else -> null
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Section
            NetworkHeader(
                network = network,
                isPinned = actuallyPinned,
                isOpenNetwork = isOpenNetwork,
                isConnecting = isConnecting,
                hasValidPassword = hasValidPassword,
                onConnectClicked = {
                    onConnectClick(network)
                }
            )

            // BSSID
            Text(
                text = network.bssid,
                style = MaterialTheme.typography.bodySmall,
                color = if (network.isOffline) Color(0xFFB0B0B0) else Color(0xFF7F8C8D), // More muted for offline
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
                        // Launch camera with automatic permission handling
                        cameraLauncher.launch()
                    } else {
                        // Remove existing photo - use clearPhoto flag to ensure permanent deletion
                        photoPath = null
                        onUpdateDataWithPhotoDeletion(network.bssid, network.ssid, comment, savedPassword, null, true)
                        Toast.makeText(context, "Photo removed!", Toast.LENGTH_SHORT).show()
                    }
                },
                onPinClick = {
                    onPinClick(network.bssid, !actuallyPinned)
                },
                isPinned = actuallyPinned
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
    
    // Camera Permission Rationale Dialog
    if (showCameraPermissionDialog) {
        CameraPermissionRationaleDialog(
            onAllowCamera = {
                showCameraPermissionDialog = false
                // Request permissions after user understands why
                val requiredPermissions = com.ner.wimap.utils.PermissionUtils.getRequiredCameraPermissions()
                directPermissionLauncher.launch(requiredPermissions.toTypedArray())
            },
            onDismiss = { 
                showCameraPermissionDialog = false 
            }
        )
    }
}

/**
 * Simple camera launcher state without enhanced permission management
 */
data class SimpleCameraLauncherState(
    val launch: () -> Unit
)

/**
 * Launch camera with URI creation - no enhanced permission management
 */
private fun launchCameraWithUri(
    context: Context,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    onUriCreated: (Uri) -> Unit
) {
    try {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "WIFI_PHOTO_$timeStamp.jpg"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs()
        }
        
        val photoFile = File(storageDir, imageFileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        
        onUriCreated(uri)
        cameraLauncher.launch(uri)
    } catch (e: Exception) {
        // Silent error handling - no red dialogs
    }
}