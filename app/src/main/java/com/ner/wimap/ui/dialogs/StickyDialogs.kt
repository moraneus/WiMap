package com.ner.wimap.ui.dialogs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ner.wimap.R

@Composable
fun StickyProgressDialog(
    isVisible: Boolean,
    title: String,
    message: String,
    progress: Float? = null,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { with(density) { -40.dp.roundToPx() } },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { with(density) { -40.dp.roundToPx() } },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
            .zIndex(1000f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Progress indicator
                if (progress != null) {
                    // Determinate progress
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    // Indeterminate progress
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
                
                // Content column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    
                    // Progress percentage if available
                    if (progress != null) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Close button
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.content_desc_dismiss),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StickyScanProgressDialog(
    isVisible: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    StickyProgressDialog(
        isVisible = isVisible,
        title = stringResource(R.string.scanning_for_networks),
        message = "Discovering networks...",
        onCancel = onCancel,
        modifier = modifier.zIndex(1001f) // Higher z-index than connection dialog
    )
}

@Composable
fun StickyConnectionProgressDialog(
    isVisible: Boolean,
    networkName: String,
    currentAttempt: Int,
    totalAttempts: Int,
    currentPassword: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalAttempts > 0) currentAttempt.toFloat() / totalAttempts else null
    
    // Enhanced message with exact password and attempt details
    val message = buildString {
        if (currentPassword != null && currentPassword.isNotBlank()) {
            append("Password: \"$currentPassword\"")
            if (totalAttempts > 0 && currentAttempt > 0) {
                append("\nAttempt $currentAttempt of $totalAttempts")
            }
        } else if (networkName.isNotBlank()) {
            append("Connecting to $networkName...")
        } else {
            append("Establishing connection...")
        }
    }

    StickyProgressDialog(
        isVisible = isVisible,
        title = if (networkName.isNotBlank()) "Connecting to $networkName" else "Connecting to Network",
        message = message,
        progress = progress,
        onCancel = onCancel,
        modifier = modifier.zIndex(999f) // Lower z-index than scanning dialog
    )
}

@Composable
fun StickyPasswordAttemptDialog(
    isVisible: Boolean,
    networkName: String,
    currentPasswordIndex: Int,
    totalPasswords: Int,
    currentAttempt: Int,
    maxAttempts: Int,
    currentPassword: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val overallProgress = if (totalPasswords > 0) {
        (currentPasswordIndex.toFloat() + (currentAttempt.toFloat() / maxAttempts)) / totalPasswords
    } else null

    val message = buildString {
        append("Password ${currentPasswordIndex + 1}/$totalPasswords")
        if (currentPassword != null) {
            append(" - Attempt $currentAttempt/$maxAttempts")
        }
    }

    StickyProgressDialog(
        isVisible = isVisible,
        title = "Breaking Password for $networkName",
        message = message,
        progress = overallProgress,
        onCancel = onCancel,
        modifier = modifier
    )
}

@Composable
fun StickyUploadProgressDialog(
    isVisible: Boolean,
    uploadedCount: Int,
    totalCount: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalCount > 0) uploadedCount.toFloat() / totalCount else null
    
    StickyProgressDialog(
        isVisible = isVisible,
        title = "Uploading Networks",
        message = "Uploading network data to cloud ($uploadedCount/$totalCount)",
        progress = progress,
        onCancel = onCancel,
        modifier = modifier
    )
}