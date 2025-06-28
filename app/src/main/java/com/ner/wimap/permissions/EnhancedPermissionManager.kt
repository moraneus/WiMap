package com.ner.wimap.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ner.wimap.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enhanced permission manager for automatic camera permission handling
 * Camera permissions use automatic dialogs, WiFi permissions use original explanation popup
 */
class EnhancedPermissionManager(
    private val activity: ComponentActivity
) {
    
    // Permission states
    private val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()
    
    // UI states
    private val _showRationaleDialog = MutableStateFlow(false)
    val showRationaleDialog: StateFlow<Boolean> = _showRationaleDialog.asStateFlow()
    
    private val _currentPermissionRequest = MutableStateFlow<PermissionRequest?>(null)
    val currentPermissionRequest: StateFlow<PermissionRequest?> = _currentPermissionRequest.asStateFlow()
    
    private val _showPermissionDeniedMessage = MutableStateFlow<String?>(null)
    val showPermissionDeniedMessage: StateFlow<String?> = _showPermissionDeniedMessage.asStateFlow()
    
    private val _isRequestingPermissions = MutableStateFlow(false)
    val isRequestingPermissions: StateFlow<Boolean> = _isRequestingPermissions.asStateFlow()
    
    // Permission launcher - will be set by the Composable
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    
    init {
        checkInitialPermissionStates()
    }
    
    /**
     * Set the permission launcher - called from Composable
     */
    fun setPermissionLauncher(launcher: ActivityResultLauncher<Array<String>>) {
        permissionLauncher = launcher
    }
    
    /**
     * Handle permission result from the launcher
     */
    fun onPermissionResult(permissions: Map<String, Boolean>) {
        handlePermissionResult(permissions)
    }
    
    private fun checkInitialPermissionStates() {
        val allPermissions = mutableMapOf<String, PermissionState>()
        
        // Check camera permissions
        PermissionUtils.getRequiredCameraPermissions().forEach { permission ->
            allPermissions[permission] = getPermissionState(permission)
        }
        
        // Check WiFi permissions  
        PermissionUtils.getRequiredWifiPermissions().forEach { permission ->
            allPermissions[permission] = getPermissionState(permission)
        }
        
        _permissionStates.value = allPermissions
    }
    
    private fun getPermissionState(permission: String): PermissionState {
        val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            activity, permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        return if (isGranted) {
            PermissionState.GRANTED
        } else {
            val shouldShow = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            val wasRequestedBefore = hasRequestedPermissionBefore(permission)
            
            when {
                !wasRequestedBefore -> PermissionState.NOT_REQUESTED
                shouldShow -> PermissionState.DENIED
                else -> PermissionState.PERMANENTLY_DENIED
            }
        }
    }
    
    private fun hasRequestedPermissionBefore(permission: String): Boolean {
        val prefs = activity.getSharedPreferences("permission_tracker", Context.MODE_PRIVATE)
        return prefs.getBoolean("requested_$permission", false)
    }
    
    private fun markPermissionAsRequested(permission: String) {
        val prefs = activity.getSharedPreferences("permission_tracker", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("requested_$permission", true).apply()
    }
    
    /**
     * Request camera permissions automatically
     */
    fun requestCameraPermissions(onResult: (granted: Boolean) -> Unit) {
        val permissions = PermissionUtils.getRequiredCameraPermissions()
        requestPermissions(
            type = PermissionType.CAMERA,
            permissions = permissions,
            onResult = { result ->
                val allGranted = result.values.all { it }
                onResult(allGranted)
            }
        )
    }
    
    /**
     * Request WiFi permissions automatically
     */
    fun requestWifiPermissions(onResult: (granted: Boolean) -> Unit) {
        val permissions = PermissionUtils.getRequiredWifiPermissions()
        requestPermissions(
            type = PermissionType.WIFI,
            permissions = permissions,
            onResult = { result ->
                val allGranted = result.values.all { it }
                onResult(allGranted)
            }
        )
    }
    
    private fun requestPermissions(
        type: PermissionType,
        permissions: List<String>,
        onResult: (Map<String, Boolean>) -> Unit
    ) {
        // Mark permissions as requested
        permissions.forEach { markPermissionAsRequested(it) }
        
        // Check if already granted
        val alreadyGranted = permissions.all { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(
                activity, permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        if (alreadyGranted) {
            onResult(permissions.associateWith { true })
            return
        }
        
        // Check if we need to show rationale for any permission
        val needsRationale = permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        
        val request = PermissionRequest(type, permissions, onResult)
        _currentPermissionRequest.value = request
        
        if (needsRationale) {
            // Show rationale dialog first
            _showRationaleDialog.value = true
        } else {
            // Direct permission request
            launchPermissionRequest(permissions)
        }
    }
    
    private fun launchPermissionRequest(permissions: List<String>) {
        if (permissionLauncher == null) {
            handlePermissionLauncherFailure()
            return
        }
        
        try {
            _isRequestingPermissions.value = true
            permissionLauncher?.launch(permissions.toTypedArray()) ?: run {
                // Fallback to settings if launcher not available
                handlePermissionLauncherFailure()
            }
        } catch (e: Exception) {
            handlePermissionLauncherFailure()
        }
    }
    
    private fun handlePermissionLauncherFailure() {
        _isRequestingPermissions.value = false
        val currentRequest = _currentPermissionRequest.value
        if (currentRequest != null) {
            val deniedResult = currentRequest.permissions.associateWith { false }
            currentRequest.onResult(deniedResult)
            _showPermissionDeniedMessage.value = "Permission launcher not available. This can happen if the app is in an invalid state. Please try again or restart the app."
        }
        _currentPermissionRequest.value = null
    }
    
    private fun handlePermissionResult(result: Map<String, Boolean>) {
        _isRequestingPermissions.value = false
        val currentRequest = _currentPermissionRequest.value ?: return
        
        // Update permission states
        val updatedStates = _permissionStates.value.toMutableMap()
        result.forEach { (permission, granted) ->
            updatedStates[permission] = if (granted) {
                PermissionState.GRANTED
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                    PermissionState.DENIED
                } else {
                    PermissionState.PERMANENTLY_DENIED
                }
            }
        }
        _permissionStates.value = updatedStates
        
        // Check if any permissions were permanently denied
        val permanentlyDenied = result.filter { !it.value }.keys.filter { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        
        val anyDenied = result.values.any { !it }
        
        if (anyDenied) {
            if (permanentlyDenied.isNotEmpty()) {
                // Show permanent denial message with settings option
                val deniedNames = permanentlyDenied.map { PermissionUtils.getPermissionDisplayName(it) }
                _showPermissionDeniedMessage.value = createPermanentDenialMessage(deniedNames, currentRequest.type)
            } else {
                // Temporary denial - can request again
                val deniedNames = result.filter { !it.value }.keys.map { PermissionUtils.getPermissionDisplayName(it) }
                _showPermissionDeniedMessage.value = createTemporaryDenialMessage(deniedNames, currentRequest.type)
            }
        }
        
        // Call the result callback
        currentRequest.onResult(result)
        _currentPermissionRequest.value = null
    }
    
    private fun createPermanentDenialMessage(deniedPermissions: List<String>, type: PermissionType): String {
        val permissionList = deniedPermissions.joinToString(", ")
        return when (type) {
            PermissionType.CAMERA -> 
                "Camera and media permissions ($permissionList) are permanently denied. The camera feature won't work without these permissions. You can enable them manually in app settings if needed."
            PermissionType.WIFI -> 
                "Wi-Fi permissions ($permissionList) are permanently denied. Wi-Fi scanning won't work without these permissions. You can enable them manually in app settings if needed."
            PermissionType.ALL -> 
                "Required permissions ($permissionList) are permanently denied. Some features won't work without these permissions."
        }
    }
    
    private fun createTemporaryDenialMessage(deniedPermissions: List<String>, type: PermissionType): String {
        val permissionList = deniedPermissions.joinToString(", ")
        return when (type) {
            PermissionType.CAMERA -> 
                "Camera and media permissions ($permissionList) are required to attach photos. Please grant them when prompted."
            PermissionType.WIFI -> 
                "Wi-Fi permissions ($permissionList) are required for network scanning. Please grant them when prompted."
            PermissionType.ALL -> 
                "Required permissions ($permissionList) were denied. Some features may not work properly."
        }
    }
    
    /**
     * User approved rationale, proceed with permission request
     */
    fun onRationaleApproved() {
        _showRationaleDialog.value = false
        val currentRequest = _currentPermissionRequest.value
        if (currentRequest != null) {
            launchPermissionRequest(currentRequest.permissions)
        }
    }
    
    /**
     * User declined rationale, cancel request
     */
    fun onRationaleDeclined() {
        _showRationaleDialog.value = false
        val currentRequest = _currentPermissionRequest.value
        if (currentRequest != null) {
            val deniedResult = currentRequest.permissions.associateWith { false }
            currentRequest.onResult(deniedResult)
        }
        _currentPermissionRequest.value = null
    }
    
    /**
     * Dismiss permission denied message
     */
    fun dismissPermissionDeniedMessage() {
        _showPermissionDeniedMessage.value = null
    }
    
    /**
     * Open app settings for manual permission enabling
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        activity.startActivity(intent)
        dismissPermissionDeniedMessage()
    }
    
    /**
     * Check if camera permissions are granted
     */
    fun hasCameraPermissions(): Boolean {
        return PermissionUtils.hasAllCameraPermissions(activity)
    }
    
    /**
     * Check if WiFi permissions are granted
     */
    fun hasWifiPermissions(): Boolean {
        return PermissionUtils.hasAllWifiPermissions(activity)
    }
    
    /**
     * Get missing camera permissions with user-friendly names
     */
    fun getMissingCameraPermissions(): List<Pair<String, String>> {
        return PermissionUtils.getMissingCameraPermissions(activity).map { permission ->
            permission to PermissionUtils.getPermissionDisplayName(permission)
        }
    }
    
    /**
     * Get missing WiFi permissions with user-friendly names
     */
    fun getMissingWifiPermissions(): List<Pair<String, String>> {
        return PermissionUtils.getMissingWifiPermissions(activity).map { permission ->
            permission to PermissionUtils.getPermissionDisplayName(permission)
        }
    }
    
    /**
     * Refresh permission states (call when returning from settings)
     */
    fun refreshPermissionStates() {
        checkInitialPermissionStates()
    }
}

/**
 * Composable hook for accessing EnhancedPermissionManager
 */
@Composable
fun rememberEnhancedPermissionManager(): EnhancedPermissionManager {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val permissionManager = remember(activity) {
        EnhancedPermissionManager(activity)
    }
    
    // Register the permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.onPermissionResult(permissions)
    }
    
    // Set the launcher in the permission manager
    LaunchedEffect(permissionLauncher) {
        permissionManager.setPermissionLauncher(permissionLauncher)
    }
    
    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Refresh permissions when app resumes (e.g., from settings)
                    permissionManager.refreshPermissionStates()
                }
                else -> { /* No action needed */ }
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    return permissionManager
}