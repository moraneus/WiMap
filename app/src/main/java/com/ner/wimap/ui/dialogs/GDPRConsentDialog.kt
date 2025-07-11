package com.ner.wimap.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * GDPR-compliant consent dialog with granular consent options
 * Meets GDPR Article 7 requirements for clear and informed consent
 */
@Composable
fun GDPRConsentDialog(
    onConsentGiven: (
        essentialConsent: Boolean,
        analyticsConsent: Boolean,
        advertisingConsent: Boolean,
        locationConsent: Boolean,
        dataUploadConsent: Boolean,
        userAge: Int
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var essentialConsent by remember { mutableStateOf(true) }
    var analyticsConsent by remember { mutableStateOf(true) }
    var advertisingConsent by remember { mutableStateOf(true) }
    var locationConsent by remember { mutableStateOf(true) }
    var dataUploadConsent by remember { mutableStateOf(true) }
    var userAge by remember { mutableStateOf("") }
    var showAgeVerification by remember { mutableStateOf(true) }
    var ageVerified by remember { mutableStateOf(false) }
    var isChild by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showAgeVerification) {
                    AgeVerificationSection(
                        userAge = userAge,
                        onAgeChanged = { userAge = it },
                        onAgeVerified = { age ->
                            val ageInt = age.toIntOrNull() ?: 0
                            ageVerified = true
                            isChild = ageInt < 13
                            showAgeVerification = false
                        }
                    )
                } else {
                    ConsentSection(
                        isChild = isChild,
                        essentialConsent = essentialConsent,
                        analyticsConsent = analyticsConsent,
                        advertisingConsent = advertisingConsent,
                        locationConsent = locationConsent,
                        dataUploadConsent = dataUploadConsent,
                        onEssentialChanged = { essentialConsent = it },
                        onAnalyticsChanged = { analyticsConsent = it },
                        onAdvertisingChanged = { advertisingConsent = it },
                        onLocationChanged = { locationConsent = it },
                        onDataUploadChanged = { dataUploadConsent = it },
                        onAccept = {
                            onConsentGiven(
                                essentialConsent,
                                analyticsConsent,
                                advertisingConsent,
                                locationConsent,
                                dataUploadConsent,
                                userAge.toIntOrNull() ?: 0
                            )
                        },
                        onDecline = onDismiss,
                        canAccept = essentialConsent && dataUploadConsent && locationConsent && ageVerified
                    )
                }
            }
        }
    }
}

@Composable
private fun AgeVerificationSection(
    userAge: String,
    onAgeChanged: (String) -> Unit,
    onAgeVerified: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.VerifiedUser,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Age Verification Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "To comply with privacy laws (COPPA/GDPR), we need to verify your age before proceeding.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        OutlinedTextField(
            value = userAge,
            onValueChange = onAgeChanged,
            label = { Text("Your Age") },
            placeholder = { Text("Enter your age") },
            supportingText = { Text("Required for privacy compliance") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Button(
            onClick = { onAgeVerified(userAge) },
            enabled = userAge.toIntOrNull()?.let { it in 1..120 } == true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ConsentSection(
    isChild: Boolean,
    essentialConsent: Boolean,
    analyticsConsent: Boolean,
    advertisingConsent: Boolean,
    locationConsent: Boolean,
    dataUploadConsent: Boolean,
    onEssentialChanged: (Boolean) -> Unit,
    onAnalyticsChanged: (Boolean) -> Unit,
    onAdvertisingChanged: (Boolean) -> Unit,
    onLocationChanged: (Boolean) -> Unit,
    onDataUploadChanged: (Boolean) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    canAccept: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "Privacy & Data Consent",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        if (isChild) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChildCare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Parental Consent Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Users under 13 require parental or guardian consent to use this app. Please have a parent or guardian review and provide consent for data processing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        
        Text(
            text = "WiMap builds a global WiFi database. Essential features (marked with •) are required for the app to function:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Essential Consent (Required)
        ConsentOption(
            icon = Icons.Default.Security,
            title = "Essential App Functions",
            description = "Required for the app to work properly. Includes basic WiFi scanning and local data storage.",
            isRequired = true,
            isChecked = essentialConsent,
            onCheckedChange = onEssentialChanged,
            isEnabled = true
        )
        
        // Optional Consents
        ConsentOption(
            icon = Icons.Default.Analytics,
            title = "Anonymous Analytics",
            description = "Help improve the app with anonymous usage statistics. No personal data is collected.",
            isRequired = false,
            isChecked = analyticsConsent,
            onCheckedChange = onAnalyticsChanged,
            isEnabled = !isChild
        )
        
        ConsentOption(
            icon = Icons.Default.AttachMoney,
            title = "Personalized Advertising",
            description = "Show relevant ads based on app usage. Helps support free app development.",
            isRequired = false,
            isChecked = advertisingConsent,
            onCheckedChange = onAdvertisingChanged,
            isEnabled = !isChild
        )
        
        ConsentOption(
            icon = Icons.Default.LocationOn,
            title = "GPS Location Data",
            description = "Use GPS coordinates for accurate network mapping. Required for proper functionality.",
            isRequired = true,
            isChecked = locationConsent,
            onCheckedChange = onLocationChanged,
            isEnabled = true
        )
        
        ConsentOption(
            icon = Icons.Default.CloudUpload,
            title = "Network Data Sharing",
            description = "Share anonymous network data to build a global WiFi database. Required for app functionality.",
            isRequired = true,
            isChecked = dataUploadConsent,
            onCheckedChange = onDataUploadChanged,
            isEnabled = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f)
            ) {
                Text("Decline")
            }
            
            Button(
                onClick = onAccept,
                enabled = canAccept,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Start Using App",
                    fontSize = 14.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Text(
            text = "You can change these preferences anytime in Settings. Your consent is valid for 13 months.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConsentOption(
    icon: ImageVector,
    title: String,
    description: String,
    isRequired: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRequired) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (isRequired) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (!isEnabled) {
                    Text(
                        text = "Not available for users under 13",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Checkbox(
                checked = isChecked,
                onCheckedChange = if (isEnabled) onCheckedChange else null,
                enabled = isEnabled
            )
        }
    }
}