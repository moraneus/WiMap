# Background Scanning and Auto-Save Fixes

## Issues Fixed

### 1. Background Scanning Toggle Behavior
**Problem**: Toggle automatically started/stopped scans instead of just controlling background permission.

**Solution**: Modified `toggleBackgroundScanning()` in MainViewModel.kt:
- **When enabled**: Only starts notification service, doesn't auto-start scanning or promote existing scans
- **When disabled**: Only stops background services, doesn't interfere with active foreground scans
- Active scans continue as foreground scans when background permission is disabled

### 2. Auto-Save When Stopped from Notification
**Problem**: Stopping scan from notification didn't save session automatically.

**Solution**: Implemented broadcast-based auto-save system:
1. **Added `autoSaveCurrentScanSession()` method** in MainViewModel.kt (lines 1549-1596)
   - Automatically generates session title with timestamp format: "Scan MM/dd HH:mm"
   - Saves session with all discovered networks
   - Shows toast confirmation message
2. **Modified WiFiScanService.kt** to send broadcast when stopped by user:
   - Added `sendAutoSaveBroadcast()` method 
   - Broadcasts `ACTION_AUTO_SAVE_SESSION` before stopping
3. **Added broadcast receiver** in MainActivity.kt:
   - Listens for auto-save broadcasts
   - Calls `viewModel.autoSaveCurrentScanSession()` when received

## Changes Made

### MainViewModel.kt
- **Line 808-846**: Modified `toggleBackgroundScanning()` to only control permissions
- **Line 1549-1596**: Added `autoSaveCurrentScanSession()` method
- **Line 1788-1790**: Added companion object with broadcast action constant

### WiFiScanService.kt  
- **Line 78-84**: Modified ACTION_STOP_SCAN handling to send broadcast first
- **Line 316-321**: Added `sendAutoSaveBroadcast()` method

### MainActivity.kt
- **Line 292-309**: Added broadcast receiver for auto-save functionality

## How It Works Now

### Background Scanning Toggle
1. **Enable**: Shows "Background scanning: ALLOWED" toast, starts notification service
2. **Disable**: Shows "Background scanning: DISABLED" toast, stops notification service
3. **No automatic scan starting/stopping**

### Auto-Save from Notification
1. User taps "Stop" in notification
2. Service sends `AUTO_SAVE_SESSION` broadcast
3. MainActivity receives broadcast
4. Calls `autoSaveCurrentScanSession()`
5. Session saved with default timestamp name (e.g., "Scan 03/15 14:30")
6. Toast shows "Scan session saved as 'Scan 03/15 14:30'"

## Benefits

✅ **Clean permission toggle**: Background scanning toggle only controls background permission
✅ **No interruption**: Active scans continue uninterrupted when toggling background permission  
✅ **Automatic session saving**: Sessions always saved when stopped via notification
✅ **User-friendly**: Clear feedback with timestamp-based session names
✅ **Consistent behavior**: Same auto-save logic as manual scan stopping

The fixes ensure the background scanning toggle behaves as a permission control rather than a scan controller, and guarantee that scan sessions are never lost when stopped from notifications.