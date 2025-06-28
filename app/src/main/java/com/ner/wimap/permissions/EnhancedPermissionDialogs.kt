package com.ner.wimap.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Enhanced permission UI handler for automatic permission management
 */
@Composable
fun EnhancedPermissionUIHandler(
    permissionManager: EnhancedPermissionManager,
    modifier: Modifier = Modifier
) {
    val showRationaleDialog by permissionManager.showRationaleDialog.collectAsState()
    val currentPermissionRequest by permissionManager.currentPermissionRequest.collectAsState()
    val showPermissionDeniedMessage by permissionManager.showPermissionDeniedMessage.collectAsState()
    val isRequestingPermissions by permissionManager.isRequestingPermissions.collectAsState()
    
    // Permission Rationale Dialog
    if (showRationaleDialog && currentPermissionRequest != null) {
        EnhancedPermissionRationaleDialog(
            permissionRequest = currentPermissionRequest!!,
            onAllow = { permissionManager.onRationaleApproved() },
            onCancel = { permissionManager.onRationaleDeclined() },
            onDismiss = { permissionManager.onRationaleDeclined() }
        )
    }
    
    // Permission Denied Message
    showPermissionDeniedMessage?.let { message ->
        EnhancedPermissionDeniedDialog(
            message = message,
            onOpenSettings = { permissionManager.openAppSettings() },
            onDismiss = { permissionManager.dismissPermissionDeniedMessage() }
        )
    }
    
    // Loading indicator during permission request
    if (isRequestingPermissions) {
        Dialog(onDismissRequest = { /* Cannot dismiss during request */ }) {
            Card(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Requesting permissions...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Material 3 permission rationale dialog
 */
@Composable
private fun EnhancedPermissionRationaleDialog(
    permissionRequest: PermissionRequest,
    onAllow: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, message, icon) = when (permissionRequest.type) {
        PermissionType.CAMERA -> Triple(
            "Camera & Photos Access",
            "This app needs access to your camera and photos to let you attach images to Wi-Fi networks.",
            Icons.Default.CameraAlt
        )
        PermissionType.WIFI -> Triple(
            "Wi-Fi Access",
            "This app needs Wi-Fi permissions to scan for and connect to wireless networks.",
            Icons.Default.Wifi
        )
        PermissionType.ALL -> Triple(
            "Permissions Required",
            "This app needs several permissions to function properly.",
            Icons.Default.Security
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                // Show detailed permission explanations
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Required permissions:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        permissionRequest.permissions.forEach { permission ->
                            PermissionExplanationRow(
                                permission = permission
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Allow",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

/**
 * Enhanced permission denied dialog with settings option
 */
@Composable
private fun EnhancedPermissionDeniedDialog(
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPermanentDenial = message.contains("permanently denied")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isPermanentDenial) Icons.Default.Block else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isPermanentDenial) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isPermanentDenial) "Permissions Denied" else "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            if (isPermanentDenial) {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Open Settings",
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "OK",
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = if (isPermanentDenial) "Maybe Later" else "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

/**
 * Row showing permission explanation with icon
 */
@Composable
private fun PermissionExplanationRow(
    permission: String
) {
    val (icon, color) = when {
        permission.contains("CAMERA") -> Icons.Default.CameraAlt to Color(0xFF4CAF50)
        permission.contains("READ_MEDIA") || permission.contains("READ_EXTERNAL") -> Icons.Default.Photo to Color(0xFF2196F3)
        permission.contains("WIFI") -> Icons.Default.Wifi to Color(0xFF9C27B0)
        permission.contains("LOCATION") -> Icons.Default.LocationOn to Color(0xFFFF9800)
        else -> Icons.Default.Security to Color(0xFF607D8B)
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = com.ner.wimap.utils.PermissionUtils.getPermissionDisplayName(permission),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = com.ner.wimap.utils.PermissionUtils.getPermissionExplanation(permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Enhanced permission status indicator for use in UI components
 */
@Composable
fun EnhancedPermissionStatusIndicator(
    hasPermissions: Boolean,
    permissionType: String,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasPermissions) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
                
                Text(
                    text = "$permissionType permission required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                
                TextButton(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = "Grant",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}