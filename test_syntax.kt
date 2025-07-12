// Test file to verify SelectableEnhancedWifiNetworkCard syntax
@Composable
fun SelectableEnhancedWifiNetworkCard(
    network: WifiNetwork,
    isConnecting: Boolean,
    connectionStatus: String?,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit,
    onPinClick: (String, Boolean) -> Unit,
    onConnectClick: (WifiNetwork) -> Unit,
    onCancelConnectionClick: (String) -> Unit,
    onMoreInfoClick: (WifiNetwork) -> Unit,
    onShowOnMapClick: () -> Unit,
    onUpdateData: (String, String, String?, String?, String?) -> Unit,
    onUpdateDataWithPhotoDeletion: (String, String, String?, String?, String?, Boolean) -> Unit
) {
    // Create a wrapper around EnhancedWifiNetworkCard that handles selection
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // Add long press detection for initiating multi-select, and selection clicks when already in multi-select
                Modifier.combinedClickable(
                    onClick = {
                        if (isMultiSelectMode) {
                            onCardClick()
                        }
                        // When not in multi-select mode, let the EnhancedWifiNetworkCard handle its own clicks
                    },
                    onLongClick = {
                        if (!isMultiSelectMode) {
                            onCardLongClick()
                        }
                        // When already in multi-select mode, ignore long press
                    }
                )
            )
            .background(
                Color.Transparent, // Let the card handle its own background
                RoundedCornerShape(16.dp)
            )
    ) {
        // Use EnhancedWifiNetworkCard directly - it will handle its own selection styling
        EnhancedWifiNetworkCard(
            network = network,
            isConnecting = isConnecting,
            connectionStatus = connectionStatus,
            isSelected = isSelected && isMultiSelectMode,
            onPinClick = onPinClick,
            onConnectClick = onConnectClick,
            onCancelConnectionClick = { onCancelConnectionClick(network.bssid) },
            onMoreInfoClick = onMoreInfoClick,
            onUpdateData = onUpdateData,
            onUpdateDataWithPhotoDeletion = onUpdateDataWithPhotoDeletion
        )
        
        // Show checkmark in top-left corner when in multi-select mode
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(20.dp)
                    .background(
                        if (isSelected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.95f),
                        CircleShape
                    )
                    .shadow(3.dp, CircleShape)
                    .then(
                        if (!isSelected) {
                            Modifier.border(1.5.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
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