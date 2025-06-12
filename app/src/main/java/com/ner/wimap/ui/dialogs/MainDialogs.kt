package com.ner.wimap.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ner.wimap.model.WifiNetwork

@Composable
fun ModernPasswordDialog(
    network: WifiNetwork,
    onPasswordEntered: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Connect to Network",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50)
            )
        },
        text = {
            Column {
                Text(
                    "Network: ${network.ssid}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF34495E)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPasswordEntered(password) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))
            ) {
                Text("Connect", fontWeight = FontWeight.SemiBold)
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
fun ModernPermissionDialog(
    message: String?,
    onGrantPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Permissions Required",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2C3E50)
            )
        },
        text = {
            Text(
                message ?: "This app needs permissions to function correctly",
                color = Color(0xFF34495E)
            )
        },
        confirmButton = {
            Button(
                onClick = onGrantPermissions,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3498DB))
            ) {
                Text("Grant Permissions", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings", color = Color(0xFF95A5A6))
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}