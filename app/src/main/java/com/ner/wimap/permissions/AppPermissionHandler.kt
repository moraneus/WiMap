package com.ner.wimap.permissions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * App-level permission handler that manages permission states across the entire application
 */
@Composable
fun AppPermissionHandler(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current as ComponentActivity
    val permissionManager = remember { SimplePermissionManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Monitor app lifecycle to refresh permissions when returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Refresh permission states when app resumes
                    // This handles the case where user granted permissions in settings
                    permissionManager.refreshPermissionStates()
                }
                else -> { /* No action needed */ }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Provide the permission manager through CompositionLocal
    CompositionLocalProvider(
        LocalPermissionManager provides permissionManager
    ) {
        content()
        
        // Handle global permission UI
        PermissionUIHandler(permissionManager = permissionManager)
    }
}

/**
 * CompositionLocal for providing PermissionManager throughout the app
 */
val LocalPermissionManager = compositionLocalOf<SimplePermissionManager?> { null }

/**
 * Hook to access the app-level PermissionManager
 */
@Composable
fun rememberAppPermissionManager(): SimplePermissionManager {
    return LocalPermissionManager.current 
        ?: throw IllegalStateException("PermissionManager not found. Make sure AppPermissionHandler wraps your app content.")
}

/**
 * Composable that shows permission requirement banner at the top of screens when permissions are missing
 */
@Composable
fun PermissionRequirementBanner(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val permissionManager = LocalPermissionManager.current ?: return
    val missingCameraPermissions = permissionManager.getMissingCameraPermissions()
    val missingWifiPermissions = permissionManager.getMissingWifiPermissions()
    
    // Show banner if any critical permissions are missing
    if (missingCameraPermissions.isNotEmpty() || missingWifiPermissions.isNotEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
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
                    text = "Some permissions are required for full functionality",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                
                TextButton(
                    onClick = { 
                        // Request the most critical missing permissions
                        when {
                            missingCameraPermissions.isNotEmpty() -> {
                                permissionManager.requestCameraPermissions { /* handled by UI */ }
                            }
                            missingWifiPermissions.isNotEmpty() -> {
                                permissionManager.requestWifiPermissions { /* handled by UI */ }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        "Grant", 
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

/**
 * Permission-aware composable that shows different content based on permission status
 */
@Composable
fun ConditionalPermissionContent(
    requiredPermissions: List<String>,
    onPermissionsGranted: @Composable () -> Unit,
    onPermissionsMissing: @Composable (missingPermissions: List<String>, requestPermissions: () -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionManager = LocalPermissionManager.current ?: return
    
    val missingPermissions = requiredPermissions.filter { permission ->
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, 
            permission
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    if (missingPermissions.isEmpty()) {
        onPermissionsGranted()
    } else {
        onPermissionsMissing(missingPermissions) {
            // Determine which type of permissions to request
            val containsCameraPermissions = missingPermissions.any { 
                it in com.ner.wimap.utils.PermissionUtils.getRequiredCameraPermissions() 
            }
            val containsWifiPermissions = missingPermissions.any { 
                it in com.ner.wimap.utils.PermissionUtils.getRequiredWifiPermissions() 
            }
            
            when {
                containsCameraPermissions -> permissionManager.requestCameraPermissions { /* handled by UI */ }
                containsWifiPermissions -> permissionManager.requestWifiPermissions { /* handled by UI */ }
            }
        }
    }
}