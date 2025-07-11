# View History Navigation Fix

## Issue
The "View History" button in Settings → Data Management was not navigating to the scan history screen.

## Root Cause
The navigation logic in MainActivity was only working when `currentScreen == "main"`. When users were in the settings screen (`currentScreen == "settings"`), the navigation was being blocked by this condition:

```kotlin
if (currentScreen == "main") {
    // Only navigated when on main screen
    coroutineScope.launch {
        pagerState.animateScrollToPage(SwipeDestination.SCAN_HISTORY.index)
    }
    currentPage = SwipeDestination.SCAN_HISTORY.index
}
```

## Fix Applied
Updated the navigation logic in `MainActivity.kt` lines 276-290 to handle navigation from any screen:

### Before:
```kotlin
LaunchedEffect(navigateToScanHistoryTrigger) {
    if (navigateToScanHistoryTrigger) {
        // Ensure we're on main screen first, then navigate to scan history
        if (currentScreen == "main") {
            coroutineScope.launch {
                pagerState.animateScrollToPage(SwipeDestination.SCAN_HISTORY.index)
            }
            currentPage = SwipeDestination.SCAN_HISTORY.index
        }
        viewModel.onNavigateToScanHistoryHandled()
    }
}
```

### After:
```kotlin
LaunchedEffect(navigateToScanHistoryTrigger) {
    if (navigateToScanHistoryTrigger) {
        // Navigate to scan history from any screen
        if (currentScreen == "settings") {
            // From settings, first navigate to main screen
            viewModel.navigateToMain()
        }
        // Navigate to scan history page
        coroutineScope.launch {
            pagerState.animateScrollToPage(SwipeDestination.SCAN_HISTORY.index)
        }
        currentPage = SwipeDestination.SCAN_HISTORY.index
        viewModel.onNavigateToScanHistoryHandled()
    }
}
```

## How It Works Now

1. **From Settings**: When user clicks "View History" in settings:
   - Calls `viewModel.navigateToScanHistory()`
   - Sets `navigateToScanHistoryTrigger` to `true`
   - Navigation logic detects `currentScreen == "settings"`
   - Calls `viewModel.navigateToMain()` to exit settings
   - Navigates to `SwipeDestination.SCAN_HISTORY.index`
   - Resets the trigger flag

2. **From Main Screen**: Still works as before, directly navigating to scan history

## Validation
✅ **Compilation**: Release build successful  
✅ **Navigation Flow**: Now handles navigation from both settings and main screens  
✅ **State Management**: Properly resets trigger flag after navigation  
✅ **User Experience**: Seamless transition from settings to scan history

## Test Steps
1. Go to Settings → Data Management  
2. Click "View History" button
3. Should navigate to Scan History screen
4. Can navigate back normally using back button

The "View History" button now works correctly from the settings screen.