package com.ner.wimap.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Modern bottom navigation bar with central scan button
 */
@Composable
fun ModernBottomBar(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onShareExportClicked: () -> Unit,
    onClearNetworks: () -> Unit,
    networkCount: Int = 0,
    onShowNoDataSnackbar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Share Button (Left)
                val hasNetworks = networkCount > 0
                ModernBottomBarButton(
                    icon = Icons.Outlined.Share,
                    label = "Share",
                    onClick = if (hasNetworks) onShareExportClicked else onShowNoDataSnackbar,
                    tint = if (hasNetworks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    enabled = hasNetworks
                )
                
                // Central Scan Button
                CentralScanButton(
                    isScanning = isScanning,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan
                )
                
                // Clear Button (Right)
                ModernBottomBarButton(
                    icon = Icons.Outlined.Clear,
                    label = "Clear",
                    onClick = onClearNetworks,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ModernBottomBarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "button_scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { 
                if (enabled) {
                    isPressed = true
                    onClick()
                    isPressed = false
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = tint
        )
    }
}

@Composable
private fun CentralScanButton(
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scan_button_scale"
    )
    
    // Pulsing animation when scanning
    val pulseScale by animateFloatAsState(
        targetValue = if (isScanning) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale * pulseScale
                    scaleY = scale * pulseScale
                }
                .shadow(
                    elevation = if (isScanning) 8.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = if (isScanning)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
                .background(
                    brush = if (isScanning) {
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        )
                    },
                    shape = CircleShape
                )
                .clickable { 
                    isPressed = true
                    if (isScanning) onStopScan() else onStartScan()
                    isPressed = false
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isScanning) "Stop Scan" else "Start Scan",
                tint = if (isScanning)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = if (isScanning) "Stop" else "Scan",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = if (isScanning)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        )
    }
}