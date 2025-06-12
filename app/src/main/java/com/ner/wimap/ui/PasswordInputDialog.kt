package com.ner.wimap.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordInputDialog(
    ssid: String,
    onDismissRequest: () -> Unit,
    onConnectClicked: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Enter Password for $ssid") },
        text = {
            Column {
                Text("This network is secured. Please enter the password to connect.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // It's good practice to ensure password is not blank before connecting
                    // although the ViewModel or WifiScanner might also handle empty passwords.
                    if (password.isNotBlank()) { 
                        onConnectClicked(password)
                    }
                }
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.padding(16.dp) // Apply padding to the AlertDialog itself if needed for screen margins
    )
}
