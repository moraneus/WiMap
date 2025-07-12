// Minimal syntax check
@file:Suppress("UNUSED_PARAMETER")

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Test the SelectableEnhancedWifiNetworkCard structure
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TestSelectableCard() {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Content
        
        // Show checkmark in top-left corner when in multi-select mode
        if (true) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(20.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (true) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}