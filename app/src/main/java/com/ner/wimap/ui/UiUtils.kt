package com.ner.wimap.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared UI utility functions for WiFi network display
 */

fun getSignalIcon(rssi: Int): ImageVector {
    return when {
        rssi >= -50 -> Icons.Default.SignalWifi4Bar  // Excellent
        rssi >= -70 -> Icons.Default.NetworkWifi     // Good/Fair
        else -> Icons.Default.SignalWifiOff          // Poor
    }
}

fun getSignalColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> Color(0xFF27AE60)  // Green - Excellent
        rssi >= -60 -> Color(0xFF2ECC71)  // Light Green - Good
        rssi >= -70 -> Color(0xFFF39C12)  // Orange - Fair
        else -> Color(0xFFE74C3C)         // Red - Poor
    }
}