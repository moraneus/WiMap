package com.ner.wimap.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Swipe indicators that show users they can navigate left and right
 */
@Composable
fun SwipeIndicators(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "swipe_hint")
    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_movement"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_pulse"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Left swipe indicator (to Pinned)
        if (currentPage != 0) { // Don't show on leftmost page
            SwipeHint(
                icon = Icons.Outlined.PushPin,
                label = "Pinned",
                direction = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .alpha(alpha)
                    .graphicsLayer {
                        translationX = -arrowOffset
                    }
            )
        }
        
        // Page indicators (center)
        PageIndicators(
            currentPage = currentPage,
            totalPages = 4,
            modifier = Modifier.align(Alignment.Center)
        )
        
        // Right swipe indicator (to Maps or Scan History)
        if (currentPage != 3) { // Don't show on rightmost page
            val (rightIcon, rightLabel) = when {
                currentPage < 2 -> Icons.Outlined.Map to "Maps"
                else -> Icons.Outlined.History to "Scans"
            }
            SwipeHint(
                icon = rightIcon,
                label = rightLabel,
                direction = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .alpha(alpha)
                    .graphicsLayer {
                        translationX = arrowOffset
                    }
            )
        }
    }
}

@Composable
private fun SwipeHint(
    icon: ImageVector,
    label: String,
    direction: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (direction == Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
                Icon(
                    imageVector = direction,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (direction == Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                Icon(
                    imageVector = direction,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PageIndicators(
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            PageIndicatorDot(
                isSelected = currentPage == index,
                color = when (index) {
                    0 -> MaterialTheme.colorScheme.tertiary // Pinned
                    1 -> MaterialTheme.colorScheme.primary  // Main
                    2 -> MaterialTheme.colorScheme.secondary // Maps
                    3 -> MaterialTheme.colorScheme.error    // Scan History
                    else -> MaterialTheme.colorScheme.outline
                }
            )
        }
    }
}

@Composable
private fun PageIndicatorDot(
    isSelected: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val size by animateDpAsState(
        targetValue = if (isSelected) 10.dp else 6.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dot_size"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.4f,
        animationSpec = tween(300),
        label = "dot_alpha"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

/**
 * Minimal swipe indicators for when space is limited
 */
@Composable
fun MinimalSwipeIndicators(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            PageIndicatorDot(
                isSelected = currentPage == index,
                color = when (index) {
                    0 -> MaterialTheme.colorScheme.tertiary
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.secondary
                    3 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}