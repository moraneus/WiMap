package com.ner.wimap.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Comprehensive permission UI handler that manages all permission-related dialogs and snackbars
 */
@Composable
fun PermissionUIHandler(
    permissionManager: SimplePermissionManager,
    modifier: Modifier = Modifier
) {
    val showRationaleDialog by permissionManager.showRationaleDialog.collectAsState()
    val currentPermissionRequest by permissionManager.currentPermissionRequest.collectAsState()
    val showPermissionDeniedMessage by permissionManager.showPermissionDeniedMessage.collectAsState()
    
    // Permission Rationale Dialog
    if (showRationaleDialog && currentPermissionRequest != null) {
        PermissionRationaleDialog(
            permissionRequest = currentPermissionRequest!!,
            onApprove = { permissionManager.onRationaleApproved() },
            onDecline = { permissionManager.onRationaleDeclined() },
            onDismiss = { permissionManager.onRationaleDeclined() }
        )
    }
    
    // Permission Denied Snackbar
    showPermissionDeniedMessage?.let { message ->
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            PermissionDeniedSnackbar(
                message = message,
                onOpenSettings = { permissionManager.openAppSettings() },
                onDismiss = { permissionManager.dismissPermissionDeniedMessage() },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Camera permission handler for network cards
 */
@Composable
fun CameraPermissionHandler(
    onCameraLaunch: () -> Unit,
    hasExistingPhoto: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val permissionManager = rememberPermissionManager()
    val hasPermissions = permissionManager.hasCameraPermissions()
    
    // Show permission status indicator if permissions are missing
    if (!hasPermissions) {
        PermissionStatusIndicator(
            hasPermissions = false,
            permissionType = "Camera",
            onRequestPermissions = {
                permissionManager.requestCameraPermissions { granted ->
                    if (granted) {
                        onCameraLaunch()
                    }
                }
            },
            modifier = modifier
        )
    }
    
    // Handle permission UI
    PermissionUIHandler(permissionManager = permissionManager)
}

/**
 * WiFi permission handler for scanning operations
 */
@Composable
fun WifiPermissionHandler(
    onScanStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionManager = rememberPermissionManager()
    val hasPermissions = permissionManager.hasWifiPermissions()
    
    // Show permission status indicator if permissions are missing
    if (!hasPermissions) {
        PermissionStatusIndicator(
            hasPermissions = false,
            permissionType = "Wi-Fi",
            onRequestPermissions = {
                permissionManager.requestWifiPermissions { granted ->
                    if (granted) {
                        onScanStart()
                    }
                }
            },
            modifier = modifier
        )
    }
    
    // Handle permission UI
    PermissionUIHandler(permissionManager = permissionManager)
}

/**
 * Composable that wraps camera functionality with automatic permission handling
 */
@Composable
fun WithCameraPermissions(
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
                Text("Grant Permissions")
            }
        }
    }
) {
    val permissionManager = rememberPermissionManager()
    val hasPermissions = permissionManager.hasCameraPermissions()
    
    if (hasPermissions) {
        onPermissionsGranted()
    } else {
        onPermissionsRequired {
            permissionManager.requestCameraPermissions { /* handled by UI */ }
        }
    }
    
    // Handle permission UI
    PermissionUIHandler(permissionManager = permissionManager)
}

/**
 * Composable that wraps WiFi functionality with automatic permission handling
 */
@Composable
fun WithWifiPermissions(
    onPermissionsGranted: @Composable () -> Unit,
    onPermissionsRequired: @Composable (requestPermissions: () -> Unit) -> Unit = { requestPermissions ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Wi-Fi permissions required for network scanning",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = requestPermissions) {
                Text("Grant Permissions")
            }
        }
    }
) {
    val permissionManager = rememberPermissionManager()
    val hasPermissions = permissionManager.hasWifiPermissions()
    
    if (hasPermissions) {
        onPermissionsGranted()
    } else {
        onPermissionsRequired {
            permissionManager.requestWifiPermissions { /* handled by UI */ }
        }
    }
    
    // Handle permission UI
    PermissionUIHandler(permissionManager = permissionManager)
}

/**
 * Permission-aware camera button that automatically handles permission requests
 */
@Composable
fun PermissionAwareCameraButton(
    onClick: () -> Unit,
    hasExistingPhoto: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val permissionManager = rememberPermissionManager()
    
    IconButton(
        onClick = {
            if (permissionManager.hasCameraPermissions()) {
                onClick()
            } else {
                permissionManager.requestCameraPermissions { granted ->
                    if (granted) {
                        onClick()
                    }
                }
            }
        },
        modifier = modifier,
        enabled = enabled
    ) {
        val icon = if (hasExistingPhoto) {
            Icons.Default.CameraAlt
        } else {
            Icons.Default.CameraAlt
        }
        
        val tint = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            !permissionManager.hasCameraPermissions() -> MaterialTheme.colorScheme.error
            hasExistingPhoto -> Color(0xFF667eea)
            else -> Color(0xFF27AE60)
        }
        
        Icon(
            imageVector = icon,
            contentDescription = if (hasExistingPhoto) "Edit Photo" else "Take Photo",
            tint = tint
        )
    }
    
    // Handle permission UI
    PermissionUIHandler(permissionManager = permissionManager)
}