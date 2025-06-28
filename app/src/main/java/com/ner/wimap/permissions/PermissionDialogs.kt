package com.ner.wimap.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ner.wimap.utils.PermissionUtils

@Composable
fun PermissionRationaleDialog(
    permissionRequest: PermissionRequest,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title with icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getPermissionTypeIcon(permissionRequest.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = getPermissionTypeTitle(permissionRequest.type),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Description
                Text(
                    text = getPermissionTypeDescription(permissionRequest.type),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Permission list
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Required Permissions:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        permissionRequest.permissions.forEach { permission ->
                            PermissionItem(
                                permission = permission,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text("Not Now")
                    }
                    
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedSnackbar(
    message: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Snackbar(
        action = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    Text("Settings")
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.inversePrimary
                    )
                ) {
                    Text("Dismiss")
                }
            }
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        actionContentColor = MaterialTheme.colorScheme.inversePrimary
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PermissionItem(
    permission: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = getPermissionIcon(permission),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = PermissionUtils.getPermissionDisplayName(permission),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = PermissionUtils.getPermissionExplanation(permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PermissionStatusIndicator(
    hasPermissions: Boolean,
    permissionType: String,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasPermissions) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "$permissionType permissions required",
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
                    Text("Grant", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun getPermissionTypeIcon(type: PermissionType): ImageVector {
    return when (type) {
        PermissionType.CAMERA -> Icons.Default.PhotoCamera
        PermissionType.WIFI -> Icons.Default.Wifi
        PermissionType.ALL -> Icons.Default.Security
    }
}

private fun getPermissionTypeTitle(type: PermissionType): String {
    return when (type) {
        PermissionType.CAMERA -> "Camera Access"
        PermissionType.WIFI -> "Wi-Fi Access"
        PermissionType.ALL -> "Permissions Required"
    }
}

private fun getPermissionTypeDescription(type: PermissionType): String {
    return when (type) {
        PermissionType.CAMERA -> 
            "To attach photos to Wi-Fi cards, we need access to your camera and photo library. This helps you visually identify network locations."
        PermissionType.WIFI -> 
            "To scan for Wi-Fi networks and help with connections, we need access to your device's Wi-Fi capabilities and location."
        PermissionType.ALL -> 
            "This app requires several permissions to provide full functionality for Wi-Fi network scanning and photo attachment features."
    }
}

private fun getPermissionIcon(permission: String): ImageVector {
    return when (permission) {
        android.Manifest.permission.CAMERA -> Icons.Default.PhotoCamera
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_MEDIA_IMAGES -> Icons.Default.Photo
        android.Manifest.permission.ACCESS_FINE_LOCATION -> Icons.Default.LocationOn
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.CHANGE_WIFI_STATE -> Icons.Default.Wifi
        android.Manifest.permission.NEARBY_WIFI_DEVICES -> Icons.Default.DeviceHub
        else -> Icons.Default.Security
    }
}