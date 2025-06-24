package com.ner.wimap.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun EmptyNetworksAnimation(
    statusText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated radar/scanning animation (only animation shown)
        AnimatedRadarScanner()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Dynamic status text
        Text(
            text = statusText,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnimatedRadarScanner() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_scanner")
    
    // Rotation animation for the radar sweep
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_rotation"
    )
    
    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
        ) {
            drawRadarSweep(
                color = Color(0xFF667eea),
                center = center,
                radius = size.minDimension / 2
            )
        }
        
        // Animated radar rings
        repeat(3) { index ->
            val delay = index * 600
            val animatedScale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing, delayMillis = delay),
                    repeatMode = RepeatMode.Restart
                ),
                label = "radar_ring_$index"
            )
            
            val animatedAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing, delayMillis = delay),
                    repeatMode = RepeatMode.Restart
                ),
                label = "radar_ring_alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .size((80 + index * 40).dp)
                    .scale(animatedScale)
                    .alpha(animatedAlpha)
                    .clip(CircleShape)
                    .background(
                        Color(0xFF667eea).copy(alpha = 0.3f),
                        CircleShape
                    )
            )
        }
        
        // Center dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    Color(0xFF667eea),
                    CircleShape
                )
        )
    }
}

@Composable
private fun AnimatedWifiSearchIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "wifi_search")
    
    // Bounce animation
    val bounceScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wifi_bounce"
    )
    
    // Icon switching animation
    val iconSwitch by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "icon_switch"
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(bounceScale)
            .background(
                Color(0xFF667eea).copy(alpha = 0.1f),
                RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animated dots around the icon
        repeat(8) { index ->
            val angle = (index * 45f) + (iconSwitch * 360f)
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing, delayMillis = index * 100),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha_$index"
            )
            
            Box(
                modifier = Modifier
                    .offset(
                        x = (cos(Math.toRadians(angle.toDouble())) * 35).dp,
                        y = (sin(Math.toRadians(angle.toDouble())) * 35).dp
                    )
                    .size(4.dp)
                    .alpha(dotAlpha)
                    .background(
                        Color(0xFF667eea),
                        CircleShape
                    )
            )
        }
        
        // Main WiFi icon
        Icon(
            imageVector = if (iconSwitch < 0.5f) Icons.Default.Wifi else Icons.Default.WifiOff,
            contentDescription = null,
            tint = Color(0xFF667eea),
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun DrawScope.drawRadarSweep(
    color: Color,
    center: Offset,
    radius: Float
) {
    // Draw radar grid lines
    val gridColor = color.copy(alpha = 0.2f)
    
    // Concentric circles
    for (i in 1..4) {
        val circleRadius = radius * (i / 4f)
        drawCircle(
            color = gridColor,
            radius = circleRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
    
    // Cross lines
    drawLine(
        color = gridColor,
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = gridColor,
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = 1.dp.toPx()
    )
    
    // Radar sweep (gradient arc)
    val sweepGradient = Brush.sweepGradient(
        colors = listOf(
            Color.Transparent,
            color.copy(alpha = 0.3f),
            color.copy(alpha = 0.8f),
            Color.Transparent
        ),
        center = center
    )
    
    drawArc(
        brush = sweepGradient,
        startAngle = 0f,
        sweepAngle = 90f,
        useCenter = true,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2)
    )
}