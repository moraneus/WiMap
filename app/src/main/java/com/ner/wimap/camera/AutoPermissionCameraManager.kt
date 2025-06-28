package com.ner.wimap.camera

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ner.wimap.permissions.EnhancedPermissionManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Auto-permission camera launcher that handles permissions automatically
 * without requiring manual settings navigation
 */
@Composable
fun rememberAutoPermissionCameraLauncher(
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit,
    onPermissionDenied: (String) -> Unit = { /* Default: do nothing */ }
): AutoPermissionCameraLauncherState {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            handleCameraResult(photoUri!!, context, onPhotoTaken, onError)
        } else {
            onError("Failed to capture photo - camera operation cancelled or failed")
        }
    }
    
    // Direct camera permission launcher - no enhanced permission manager to avoid settings dialogs
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            launchCameraInternal(context, cameraLauncher, onError) { uri ->
                photoUri = uri
            }
        } else {
            // Simple denial message - no settings dialog
            onPermissionDenied("Camera access is needed to take photos")
        }
    }
    
    val launchCamera = {
        val hasPermissions = com.ner.wimap.utils.PermissionUtils.hasAllCameraPermissions(context)
        
        if (hasPermissions) {
            // Permissions already granted, launch camera directly
            launchCameraInternal(context, cameraLauncher, onError) { uri ->
                photoUri = uri
            }
        } else {
            // Request only camera permission through system dialog
            val requiredPermissions = com.ner.wimap.utils.PermissionUtils.getRequiredCameraPermissions()
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }
    
    return AutoPermissionCameraLauncherState(
        launch = launchCamera,
        hasPermissions = com.ner.wimap.utils.PermissionUtils.hasAllCameraPermissions(context),
        missingPermissions = com.ner.wimap.utils.PermissionUtils.getMissingCameraPermissions(context).map { it to it },
        permissionManager = null
    )
}

/**
 * State class for auto-permission camera launcher
 */
data class AutoPermissionCameraLauncherState(
    val launch: () -> Unit,
    val hasPermissions: Boolean,
    val missingPermissions: List<Pair<String, String>>,
    val permissionManager: EnhancedPermissionManager?
)

private fun handleCameraResult(
    photoUri: Uri,
    context: Context,
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val file = File(photoUri.path ?: "")
        if (file.exists() && file.length() > 0) {
            onPhotoTaken(photoUri)
        } else {
            // Try to find the file using the URI
            context.contentResolver.openInputStream(photoUri)?.use { inputStream ->
                if (inputStream.available() > 0) {
                    onPhotoTaken(photoUri)
                } else {
                    onError("Photo file is empty")
                }
            } ?: onError("Could not access photo file")
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
 * Enhanced camera button that automatically handles permissions
 */
@Composable
fun AutoPermissionCameraButton(
    onClick: () -> Unit,
    hasExistingPhoto: Boolean = false,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    
    // Direct permission launcher - no enhanced permission manager
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onClick()
        }
        // If denied, silently ignore - no error dialogs
    }
    
    val hasPermissions = com.ner.wimap.utils.PermissionUtils.hasAllCameraPermissions(context)
    
    IconButton(
        onClick = {
            if (hasPermissions) {
                onClick()
            } else {
                // Request permissions directly through system dialog
                val requiredPermissions = com.ner.wimap.utils.PermissionUtils.getRequiredCameraPermissions()
                permissionLauncher.launch(requiredPermissions.toTypedArray())
            }
        },
        modifier = modifier,
        enabled = enabled
    ) {
        val icon = Icons.Default.CameraAlt
        
        val tint = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            !hasPermissions -> MaterialTheme.colorScheme.error
            hasExistingPhoto -> Color(0xFF667eea)
            else -> Color(0xFF27AE60)
        }
        
        Icon(
            imageVector = icon,
            contentDescription = if (hasExistingPhoto) "Edit Photo" else "Take Photo",
            tint = tint
        )
    }
}

/**
 * Wrapper composable that handles camera functionality with automatic permissions
 */
@Composable
fun WithAutoCameraPermissions(
    onPermissionsGranted: @Composable () -> Unit,
    onPermissionsRequired: @Composable (requestPermissions: () -> Unit) -> Unit = { requestPermissions ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera permissions required to attach photos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = requestPermissions) {
                Text("Allow")
            }
        }
    }
) {
    val context = LocalContext.current
    
    // Direct permission launcher - no enhanced permission manager
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled automatically - no additional UI needed
    }
    
    val hasPermissions = com.ner.wimap.utils.PermissionUtils.hasAllCameraPermissions(context)
    
    if (hasPermissions) {
        onPermissionsGranted()
    } else {
        onPermissionsRequired {
            // Request permissions directly through system dialog
            val requiredPermissions = com.ner.wimap.utils.PermissionUtils.getRequiredCameraPermissions()
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }
}