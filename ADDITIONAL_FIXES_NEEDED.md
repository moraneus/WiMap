# Additional Fixes Needed for Image Attachment and Pinned Network Behavior

## Issues Identified

### 1. Images Not Attaching
**Root Cause**: The `remember` state in `EnhancedWifiNetworkCard.kt` is keyed on `network.photoPath`, causing the state to reset when network data changes during scans.

**Fix Required**:
```kotlin
// In EnhancedWifiNetworkCard.kt, change:
var photoPath by remember(network.bssid, network.photoPath) { mutableStateOf(network.photoPath) }

// To:
var photoPath by remember(network.bssid) { mutableStateOf(network.photoPath) }

// And add:
LaunchedEffect(network.photoPath) {
    photoPath = network.photoPath
}
```

### 2. Password/Comment Clearing Affects Pinned Networks
**Root Cause**: The current `updateTemporaryNetworkData` function doesn't distinguish between pinned and non-pinned networks when clearing data.

**Fix Required**: Add a new function to clear data only for non-pinned networks:
```kotlin
fun clearTemporaryNetworkDataForNonPinned(bssid: String, ssid: String, clearComment: Boolean = false, clearPassword: Boolean = false, clearPhoto: Boolean = false) {
    viewModelScope.launch {
        val existingData = manageTemporaryNetworkDataUseCase.getTemporaryDataByBssid(bssid)
        val isPinned = existingData?.isPinned ?: false
        
        // Only clear data if network is NOT pinned
        if (!isPinned) {
            // Clear logic here
        }
    }
}
```

### 3. Pinned Networks Auto-Syncing
**Root Cause**: The `SyncNetworkDataUseCase` automatically syncs temporary data to pinned networks.

**Fix Applied**: ✅ Modified `SyncNetworkDataUseCase.getPinnedNetworksWithLiveUpdates()` to return pinned networks without automatic syncing.

### 4. Image Deletion Logic
**Root Cause**: The `updateTemporaryNetworkData` function doesn't properly handle image file deletion from storage.

**Fix Required**: Add proper file deletion logic:
```kotlin
val finalPhotoPath = if (photoPath == null) {
    // Delete the image file if it exists
    existingData?.photoPath?.let { oldPhotoPath ->
        try {
            val file = java.io.File(oldPhotoPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    null
} else {
    photoPath
}
```

### 5. GPS Location for Strongest RSSI
**Status**: ✅ Already implemented correctly in `WifiScanner.kt`. The scanner tracks peak RSSI and corresponding GPS coordinates.

## Implementation Status

### ✅ Completed:
1. **SyncNetworkDataUseCase**: Modified to not automatically sync temporary data to pinned networks
2. **WiFi Scanner**: Already correctly handles GPS location for strongest RSSI
3. **Temporary Data Use Case**: Added `explicitPhotoPathUpdate` parameter for proper photo deletion handling

### ⚠️ Needs Implementation:
1. **EnhancedWifiNetworkCard.kt**: Fix remember state keys to prevent image attachment issues
2. **MainViewModel.kt**: Add image deletion logic and separate clear functions for pinned vs non-pinned networks
3. **UI Components**: Update clear buttons to use the new selective clearing functions

## Detailed Implementation Plan

### Step 1: Fix Image Attachment (EnhancedWifiNetworkCard.kt)
```kotlin
// Change remember keys to only use network.bssid
var comment by remember(network.bssid) { mutableStateOf(network.comment) }
var savedPassword by remember(network.bssid) { mutableStateOf(network.password ?: "") }
var photoPath by remember(network.bssid) { mutableStateOf(network.photoPath) }

// Add LaunchedEffect to update when network data changes
LaunchedEffect(network.comment) { comment = network.comment }
LaunchedEffect(network.password) { savedPassword = network.password ?: "" }
LaunchedEffect(network.photoPath) { photoPath = network.photoPath }
```

### Step 2: Add Selective Clearing (MainViewModel.kt)
```kotlin
fun clearTemporaryNetworkDataForNonPinned(
    bssid: String, 
    ssid: String, 
    clearComment: Boolean = false, 
    clearPassword: Boolean = false, 
    clearPhoto: Boolean = false
) {
    viewModelScope.launch {
        val existingData = manageTemporaryNetworkDataUseCase.getTemporaryDataByBssid(bssid)
        val isPinned = existingData?.isPinned ?: false
        
        if (!isPinned) {
            val newComment = if (clearComment) "" else existingData?.comment
            val newPassword = if (clearPassword) null else existingData?.savedPassword
            val newPhotoPath = if (clearPhoto) {
                // Delete image file
                existingData?.photoPath?.let { oldPhotoPath ->
                    try {
                        val file = java.io.File(oldPhotoPath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) { /* handle error */ }
                }
                null
            } else existingData?.photoPath
            
            manageTemporaryNetworkDataUseCase.saveOrUpdateTemporaryNetworkData(
                bssid = bssid,
                ssid = ssid,
                comment = newComment ?: "",
                password = newPassword,
                photoPath = newPhotoPath,
                isPinned = isPinned,
                explicitPhotoPathUpdate = clearPhoto
            )
        }
    }
}
```

### Step 3: Update Image Deletion Logic (MainViewModel.kt)
```kotlin
fun updateTemporaryNetworkData(bssid: String, ssid: String, comment: String?, password: String?, photoPath: String?) {
    viewModelScope.launch {
        val existingData = manageTemporaryNetworkDataUseCase.getTemporaryDataByBssid(bssid)
        
        val finalPhotoPath = if (photoPath == null) {
            // Delete existing image file
            existingData?.photoPath?.let { oldPhotoPath ->
                try {
                    val file = java.io.File(oldPhotoPath)
                    if (file.exists()) {
                        file.delete()
                        android.util.Log.d("MainViewModel", "Deleted image file: $oldPhotoPath")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Failed to delete image file: $oldPhotoPath", e)
                }
            }
            null
        } else {
            photoPath
        }
        
        manageTemporaryNetworkDataUseCase.saveOrUpdateTemporaryNetworkData(
            bssid = bssid,
            ssid = ssid,
            comment = comment ?: "",
            password = password,
            photoPath = finalPhotoPath,
            isPinned = null,
            explicitPhotoPathUpdate = true
        )
    }
}
```

## Testing Checklist

### Image Attachment:
- [ ] Attach image to network
- [ ] Perform scan
- [ ] Verify image remains attached
- [ ] Remove image
- [ ] Perform scan
- [ ] Verify image stays removed

### Pinned Network Protection:
- [ ] Pin a network with comment/password/image
- [ ] Clear comment/password/image in main screen
- [ ] Verify pinned network data unchanged
- [ ] Verify only temporary data cleared

### GPS Location:
- [ ] Move to different locations while scanning same network
- [ ] Verify GPS coordinates update for strongest RSSI location
- [ ] Check both current and peak RSSI locations are tracked

## Current Build Status
✅ **Project compiles successfully**
✅ **Core architecture in place**
⚠️ **UI fixes needed for complete functionality**

The foundation is solid, but the UI components need the specific fixes outlined above to resolve the image attachment and selective clearing issues.