package com.ner.wimap.camera

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.ner.wimap.permissions.PermissionManager
import com.ner.wimap.permissions.rememberPermissionManager
import com.ner.wimap.utils.PermissionUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced camera launcher with comprehensive permission handling
 */
@Composable
fun rememberEnhancedCameraLauncher(
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit,
    onPermissionDenied: (String) -> Unit = { /* Default: do nothing */ }
): CameraLauncherState {
    val context = LocalContext.current
    val permissionManager = rememberPermissionManager()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Permission states
    val permissionStates by permissionManager.permissionStates.collectAsState()
    val showRationaleDialog by permissionManager.showRationaleDialog.collectAsState()
    val currentPermissionRequest by permissionManager.currentPermissionRequest.collectAsState()
    val showPermissionDeniedMessage by permissionManager.showPermissionDeniedMessage.collectAsState()
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            handleCameraResult(photoUri!!, context, onPhotoTaken, onError)
        } else {
            onError("Failed to capture photo - camera operation cancelled or failed")
        }
    }
    
    val launchCamera = {
        if (permissionManager.hasCameraPermissions()) {
            launchCameraInternal(context, cameraLauncher, onError) { uri ->
                photoUri = uri
            }
        } else {
            // Request permissions first
            permissionManager.requestCameraPermissions { granted ->
                if (granted) {
                    launchCameraInternal(context, cameraLauncher, onError) { uri ->
                        photoUri = uri
                    }
                } else {
                    val missingPermissions = permissionManager.getMissingCameraPermissions()
                    val permissionNames = missingPermissions.map { it.second }
                    onPermissionDenied("Camera permissions (${permissionNames.joinToString(", ")}) are required to attach photos to Wi-Fi cards.")
                }
            }
        }
    }
    
    return CameraLauncherState(
        launch = launchCamera,
        hasPermissions = permissionManager.hasCameraPermissions(),
        missingPermissions = permissionManager.getMissingCameraPermissions(),
        showRationaleDialog = showRationaleDialog,
        currentPermissionRequest = currentPermissionRequest,
        showPermissionDeniedMessage = showPermissionDeniedMessage,
        permissionManager = permissionManager
    )
}

/**
 * State class for camera launcher with permission handling
 */
data class CameraLauncherState(
    val launch: () -> Unit,
    val hasPermissions: Boolean,
    val missingPermissions: List<Pair<String, String>>,
    val showRationaleDialog: Boolean,
    val currentPermissionRequest: com.ner.wimap.permissions.PermissionRequest?,
    val showPermissionDeniedMessage: String?,
    val permissionManager: com.ner.wimap.permissions.SimplePermissionManager
)


private fun handleCameraResult(
    photoUri: Uri,
    context: Context,
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    // Optimized: Trust the camera intent result without heavy file validation
    // The camera app will only return a URI if the photo was successfully captured
    try {
        // Basic URI validation without blocking file I/O
        if (photoUri.path?.isNotEmpty() == true) {
            onPhotoTaken(photoUri)
        } else {
            onError("Invalid photo URI")
        }
    } catch (e: Exception) {
        // Still try to return the URI as it might be valid
        onPhotoTaken(photoUri)
    }
}

private fun launchCameraInternal(
    context: Context,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    onError: (String) -> Unit,
    onUriCreated: (Uri) -> Unit
) {
    try {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "WIFI_PHOTO_$timeStamp.jpg"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        // Ensure the directory exists
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
        onError("Error setting up camera: ${e.message}")
    }
}

/**
 * Backwards compatibility function
 */
@Composable
fun rememberCameraLauncherCompat(
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val enhancedLauncher = rememberEnhancedCameraLauncher(
        onPhotoTaken = onPhotoTaken,
        onError = onError,
        onPermissionDenied = onError
    )
    
    return enhancedLauncher.launch
}