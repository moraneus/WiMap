package com.ner.wimap.ui.dialogs

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ner.wimap.R
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EnhancedImageViewerDialog(
    imageUri: Uri,
    networkName: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isVisible by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    
    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // Animation for entry/exit
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f)
        rotation += rotationChange
        offset += offsetChange
        
        // Show controls when user interacts
        showControls = true
    }

    // Swipe to dismiss gesture state
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = screenHeight.value * 0.3f

    Dialog(
        onDismissRequest = {
            isVisible = false
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(
                animationSpec = tween(300, easing = EaseInOut)
            ) + scaleIn(
                initialScale = 0.8f,
                animationSpec = tween(300, easing = EaseInOut)
            ),
            exit = fadeOut(
                animationSpec = tween(200, easing = EaseInOut)
            ) + scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(200, easing = EaseInOut)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDoubleTap = {
                                // Reset zoom on double tap
                                scale = if (scale > 1f) 1f else 2f
                                offset = Offset.Zero
                                rotation = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                if (abs(dragOffset) > dismissThreshold) {
                                    isVisible = false
                                    onDismiss()
                                } else {
                                    dragOffset = 0f
                                }
                            }
                        ) { _, dragAmount ->
                            dragOffset += dragAmount.y
                            showControls = true
                        }
                    }
            ) {
                // Main image with enhanced loading and error states
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.network_photo),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            rotationZ = rotation,
                            translationX = offset.x,
                            translationY = offset.y + dragOffset
                        )
                        .transformable(state = transformableState),
                    contentScale = ContentScale.Fit
                )

                // Enhanced top bar with smooth animations
                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(300, easing = EaseInOut)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(200, easing = EaseInOut)
                    ) + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = networkName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Network Photo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            FilledIconButton(
                                onClick = {
                                    isVisible = false
                                    onDismiss()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }
                    }
                }

                // Enhanced bottom controls with Material 3 design
                AnimatedVisibility(
                    visible = showControls,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(300, easing = EaseInOut)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(200, easing = EaseInOut)
                    ) + fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reset view button
                            FilledTonalButton(
                                onClick = {
                                    scale = 1f
                                    rotation = 0f
                                    offset = Offset.Zero
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset View",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset")
                            }
                            
                            // Zoom info with better styling
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            // Rotate button
                            FilledTonalButton(
                                onClick = {
                                    rotation += 90f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RotateRight,
                                    contentDescription = "Rotate",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rotate")
                            }
                        }
                    }
                }

                // Swipe indicator
                if (abs(dragOffset) > 50f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwipeDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Release to close",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}