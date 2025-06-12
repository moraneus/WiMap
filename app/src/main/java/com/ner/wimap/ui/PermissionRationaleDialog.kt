package com.ner.wimap.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleDialog(
    message: String,
    onDismissRequest: () -> Unit,
    onRequestPermissions: () -> Unit, // For re-requesting after rationale
    onOpenSettings: () -> Unit       // For going to app settings
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Permissions Required") },
        text = {
            Column {
                Text(text = message)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please grant these permissions to enable full app functionality. If you've previously denied them, you might need to enable them in the app settings.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRequestPermissions
            ) {
                Text("Grant Permissions")
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onOpenSettings
                ) {
                    Text("Open Settings")
                }
                TextButton(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel")
                }
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}
