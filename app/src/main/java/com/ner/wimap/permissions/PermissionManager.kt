package com.ner.wimap.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.ner.wimap.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PermissionType {
    CAMERA,
    WIFI,
    ALL
}

enum class PermissionState {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    NOT_REQUESTED
}

data class PermissionRequest(
    val type: PermissionType,
    val permissions: List<String>,
    val onResult: (Map<String, Boolean>) -> Unit
)

open class PermissionManager(private val activity: ComponentActivity) {
    
    protected val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()
    
    protected val _showRationaleDialog = MutableStateFlow(false)
    val showRationaleDialog: StateFlow<Boolean> = _showRationaleDialog.asStateFlow()
    
    protected val _currentPermissionRequest = MutableStateFlow<PermissionRequest?>(null)
    val currentPermissionRequest: StateFlow<PermissionRequest?> = _currentPermissionRequest.asStateFlow()
    
    protected val _showPermissionDeniedMessage = MutableStateFlow<String?>(null)
    val showPermissionDeniedMessage: StateFlow<String?> = _showPermissionDeniedMessage.asStateFlow()
    
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    
    init {
        checkInitialPermissionStates()
    }
    
    private fun getPermissionLauncher(): ActivityResultLauncher<Array<String>> {
        if (permissionLauncher == null) {
            try {
                permissionLauncher = activity.registerForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    handlePermissionResult(result)
                }
            } catch (e: IllegalStateException) {
                // Activity is already started, cannot register launcher
                // This is expected in some cases, we'll handle it gracefully
                throw IllegalStateException("Cannot register permission launcher after Activity is started. Please ensure PermissionManager is created during Activity onCreate.")
            }
        }
        return permissionLauncher!!
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
        return when {
            PermissionUtils.hasAllCameraPermissions(activity) && permission in PermissionUtils.getRequiredCameraPermissions() -> PermissionState.GRANTED
            PermissionUtils.hasAllWifiPermissions(activity) && permission in PermissionUtils.getRequiredWifiPermissions() -> PermissionState.GRANTED
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
                // Either not requested yet or permanently denied
                if (hasRequestedPermissionBefore(permission)) {
                    PermissionState.PERMANENTLY_DENIED
                } else {
                    PermissionState.NOT_REQUESTED
                }
            }
            else -> PermissionState.DENIED
        }
    }
    
    private fun hasRequestedPermissionBefore(permission: String): Boolean {
        // Use SharedPreferences to track if we've requested this permission before
        val prefs = activity.getSharedPreferences("permission_tracker", Context.MODE_PRIVATE)
        return prefs.getBoolean("requested_$permission", false)
    }
    
    private fun markPermissionAsRequested(permission: String) {
        val prefs = activity.getSharedPreferences("permission_tracker", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("requested_$permission", true).apply()
    }
    
    /**
     * Request camera permissions with proper rationale handling
     */
    open fun requestCameraPermissions(onResult: (granted: Boolean) -> Unit) {
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
     * Request WiFi permissions with proper rationale handling
     */
    open fun requestWifiPermissions(onResult: (granted: Boolean) -> Unit) {
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
    
    private fun requestPermissions(
        type: PermissionType,
        permissions: List<String>,
        onResult: (Map<String, Boolean>) -> Unit
    ) {
        // Mark permissions as requested
        permissions.forEach { markPermissionAsRequested(it) }
        
        // Check if we need to show rationale for any permission
        val needsRationale = permissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
        
        val request = PermissionRequest(type, permissions, onResult)
        _currentPermissionRequest.value = request
        
        if (needsRationale) {
            _showRationaleDialog.value = true
        } else {
            launchPermissionRequest(permissions)
        }
    }
    
    private fun launchPermissionRequest(permissions: List<String>) {
        try {
            getPermissionLauncher().launch(permissions.toTypedArray())
        } catch (e: IllegalStateException) {
            // Cannot register launcher, handle gracefully
            val currentRequest = _currentPermissionRequest.value
            if (currentRequest != null) {
                val deniedResult = currentRequest.permissions.associateWith { false }
                currentRequest.onResult(deniedResult)
                _showPermissionDeniedMessage.value = "Cannot request permissions at this time. Please enable permissions manually in device settings."
            }
            _currentPermissionRequest.value = null
        }
    }
    
    private fun handlePermissionResult(result: Map<String, Boolean>) {
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
        
        if (permanentlyDenied.isNotEmpty()) {
            val deniedNames = permanentlyDenied.map { PermissionUtils.getPermissionDisplayName(it) }
            _showPermissionDeniedMessage.value = createDeniedMessage(deniedNames, currentRequest.type)
        }
        
        // Call the result callback
        currentRequest.onResult(result)
        _currentPermissionRequest.value = null
    }
    
    private fun createDeniedMessage(deniedPermissions: List<String>, type: PermissionType): String {
        val permissionList = deniedPermissions.joinToString(", ")
        return when (type) {
            PermissionType.CAMERA -> 
                "Camera and media permissions ($permissionList) are required to attach photos to Wi-Fi cards."
            PermissionType.WIFI -> 
                "Wi-Fi permissions ($permissionList) are required for network scanning and connection."
            PermissionType.ALL -> 
                "The following permissions ($permissionList) are required for full app functionality."
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
     * Refresh permission states (call when returning from settings)
     */
    fun refreshPermissionStates() {
        checkInitialPermissionStates()
    }
}

/**
 * Composable hook for accessing PermissionManager
 * Uses a simpler approach that avoids ActivityResultLauncher registration issues
 */
@Composable
fun rememberPermissionManager(): SimplePermissionManager {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    
    return remember(activity) {
        SimplePermissionManager(activity)
    }
}

/**
 * Simplified PermissionManager that focuses on permission checking and UI feedback
 * without trying to register ActivityResultLaunchers in Compose
 */
class SimplePermissionManager(private val activity: ComponentActivity) {
    
    private val _showPermissionDeniedMessage = MutableStateFlow<String?>(null)
    val showPermissionDeniedMessage: StateFlow<String?> = _showPermissionDeniedMessage.asStateFlow()
    
    private val _showRationaleDialog = MutableStateFlow(false)
    val showRationaleDialog: StateFlow<Boolean> = _showRationaleDialog.asStateFlow()
    
    private val _currentPermissionRequest = MutableStateFlow<PermissionRequest?>(null)
    val currentPermissionRequest: StateFlow<PermissionRequest?> = _currentPermissionRequest.asStateFlow()
    
    private val _permissionStates = MutableStateFlow<Map<String, PermissionState>>(emptyMap())
    val permissionStates: StateFlow<Map<String, PermissionState>> = _permissionStates.asStateFlow()
    
    init {
        checkInitialPermissionStates()
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
        return when {
            PermissionUtils.hasAllCameraPermissions(activity) && permission in PermissionUtils.getRequiredCameraPermissions() -> PermissionState.GRANTED
            PermissionUtils.hasAllWifiPermissions(activity) && permission in PermissionUtils.getRequiredWifiPermissions() -> PermissionState.GRANTED
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
                // Either not requested yet or permanently denied
                if (hasRequestedPermissionBefore(permission)) {
                    PermissionState.PERMANENTLY_DENIED
                } else {
                    PermissionState.NOT_REQUESTED
                }
            }
            else -> PermissionState.DENIED
        }
    }
    
    private fun hasRequestedPermissionBefore(permission: String): Boolean {
        val prefs = activity.getSharedPreferences("permission_tracker", Context.MODE_PRIVATE)
        return prefs.getBoolean("requested_$permission", false)
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
     * Request camera permissions - shows rationale and directs to settings
     */
    fun requestCameraPermissions(onResult: (granted: Boolean) -> Unit) {
        if (hasCameraPermissions()) {
            onResult(true)
            return
        }
        
        val permissions = PermissionUtils.getRequiredCameraPermissions()
        val request = PermissionRequest(PermissionType.CAMERA, permissions) { result ->
            val allGranted = result.values.all { it }
            onResult(allGranted)
        }
        _currentPermissionRequest.value = request
        
        // Show rationale dialog since we can't directly request permissions from here
        _showRationaleDialog.value = true
    }
    
    /**
     * Request WiFi permissions - shows rationale and directs to settings
     */
    fun requestWifiPermissions(onResult: (granted: Boolean) -> Unit) {
        if (hasWifiPermissions()) {
            onResult(true)
            return
        }
        
        val permissions = PermissionUtils.getRequiredWifiPermissions()
        val request = PermissionRequest(PermissionType.WIFI, permissions) { result ->
            val allGranted = result.values.all { it }
            onResult(allGranted)
        }
        _currentPermissionRequest.value = request
        
        // Show rationale dialog since we can't directly request permissions from here
        _showRationaleDialog.value = true
    }
    
    /**
     * User approved rationale, direct to settings
     */
    fun onRationaleApproved() {
        _showRationaleDialog.value = false
        val currentRequest = _currentPermissionRequest.value
        if (currentRequest != null) {
            val deniedNames = currentRequest.permissions.map { PermissionUtils.getPermissionDisplayName(it) }
            _showPermissionDeniedMessage.value = createDeniedMessage(deniedNames, currentRequest.type)
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
    
    private fun createDeniedMessage(deniedPermissions: List<String>, type: PermissionType): String {
        val permissionList = deniedPermissions.joinToString(", ")
        return when (type) {
            PermissionType.CAMERA -> 
                "Camera and media permissions ($permissionList) are required to attach photos to Wi-Fi cards."
            PermissionType.WIFI -> 
                "Wi-Fi permissions ($permissionList) are required for network scanning and connection."
            PermissionType.ALL -> 
                "The following permissions ($permissionList) are required for full app functionality."
        }
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
     * Refresh permission states (call when returning from settings)
     */
    fun refreshPermissionStates() {
        checkInitialPermissionStates()
    }
}