package com.ner.wimap.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ner.wimap.model.WifiNetwork
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NetworkInfoChips(
    network: WifiNetwork,
    hasPhoto: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InfoChip("${network.rssi}dBm", Color(0xFF9B59B6))
        InfoChip("Ch${network.channel}", Color(0xFFE67E22))
        InfoChip(network.security, Color(0xFF16A085))

        if (network.peakRssiLatitude != null && network.peakRssiLongitude != null &&
            network.peakRssiLatitude != 0.0 && network.peakRssiLongitude != 0.0) {
            InfoChip("GPS", Color(0xFFE74C3C))
        }

        // Show indicators for attached data
        if (hasPhoto) {
            InfoChip("📷", Color(0xFF8E44AD))
        }
        
        if (network.comment.isNotEmpty()) {
            InfoChip("💬", Color(0xFF27AE60))
        }
        
        if (!network.password.isNullOrEmpty()) {
            InfoChip("🔑", Color(0xFF2C3E50))
        }
    }
}

@Composable
fun InfoChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.padding(1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun NetworkActionButtons(
    isOpenNetwork: Boolean,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onShowCommentDialog: () -> Unit,
    onShowPasswordDialog: () -> Unit,
    onCameraClick: () -> Unit,
    onPinClick: () -> Unit,
    isPinned: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            text = if (showDetails) "Hide" else "Details",
            color = Color(0xFF3498DB),
            onClick = onToggleDetails
        )
        ActionButton(
            text = "Comment",
            color = Color(0xFF27AE60),
            onClick = onShowCommentDialog
        )
        if (!isOpenNetwork) {
            ActionButton(
                text = "Password",
                color = Color(0xFF9B59B6),
                onClick = onShowPasswordDialog
            )
        }
        IconButton(
            onClick = onCameraClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Camera",
                tint = Color(0xFF8E44AD),
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(
            onClick = onPinClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = if (isPinned) "Unpin" else "Pin",
                tint = if (isPinned) Color(0xFF667eea) else Color(0xFFBDC3C7), // Use main app color for pinned
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun NetworkDetails(
    network: WifiNetwork,
    comment: String,
    savedPassword: String,
    isOpenNetwork: Boolean,
    hasPhoto: Boolean,
    successfulPasswords: Map<String, String> = emptyMap() // Add this parameter
) {
    // Get the actual password to display (prefer successful password over saved)
    val displayPassword = successfulPasswords[network.bssid] ?: savedPassword

    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (comment.isNotEmpty()) {
                DetailRow("Comment", comment)
            }
            if (displayPassword.isNotEmpty() && !isOpenNetwork) {
                DetailRow("Password", displayPassword) // Show the actual password (successful or saved)
            }
            // Show GPS location where peak signal was observed
            if (network.peakRssiLatitude != null && network.peakRssiLongitude != null &&
                network.peakRssiLatitude != 0.0 && network.peakRssiLongitude != 0.0) {
                DetailRow("Peak GPS", "${String.format("%.4f", network.peakRssiLatitude)}, ${String.format("%.4f", network.peakRssiLongitude)}")
                DetailRow("Peak RSSI", "${network.peakRssi}dBm")
            }
            DetailRow("Frequency", "${if (network.channel <= 14) "2.4" else "5"} GHz")
            DetailRow("Timestamp", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(network.timestamp)))
            if (hasPhoto) {
                DetailRow("Photo", "Attached")
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF7F8C8D)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF2C3E50),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun NetworkCommentDialog(
    network: WifiNetwork,
    initialComment: String,
    onCommentSaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempComment by remember { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Comment", fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)) },
        text = {
            Column {
                Text("Network: ${network.ssid}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF34495E))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tempComment,
                    onValueChange = { tempComment = it },
                    label = { Text("Comment") },
                    placeholder = { Text("Add your notes about this network...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCommentSaved(tempComment) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF95A5A6))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun NetworkPasswordDialog(
    network: WifiNetwork,
    initialPassword: String,
    onPasswordSaved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tempPassword by remember { mutableStateOf(initialPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Password", fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50)) },
        text = {
            Column {
                Text("Network: ${network.ssid}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF34495E))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter network password...") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                    // Removed visualTransformation to show clear text
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasswordSaved(tempPassword) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B59B6))
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF95A5A6))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}