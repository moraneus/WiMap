package com.ner.wimap.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraManager(private val context: Context) {
    
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun createImageFile(networkSSID: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "WIFI_${networkSSID.replace(" ", "_")}_$timeStamp.jpg"
        val storageDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "WiMap")
        
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        
        return File(storageDir, imageFileName)
    }
    
    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}

@Composable
fun rememberCameraManager(): CameraManager {
    val context = LocalContext.current
    return remember { CameraManager(context) }
}

@Composable
fun rememberCameraLauncher(
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit
): Pair<ManagedActivityResultLauncher<Uri, Boolean>, (String) -> Unit> {
    val cameraManager = rememberCameraManager()
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            onImageCaptured(currentPhotoUri!!)
        } else {
            onError("Failed to capture image")
        }
    }
    
    val takePicture: (String) -> Unit = takePicture@{ networkSSID ->
        try {
            if (!cameraManager.hasPermission()) {
                onError("Camera permission is required")
                return@takePicture
            }
            
            val imageFile = cameraManager.createImageFile(networkSSID)
            val photoUri = cameraManager.getUriForFile(imageFile)
            currentPhotoUri = photoUri
            cameraLauncher.launch(photoUri)
        } catch (e: Exception) {
            onError("Error launching camera: ${e.message}")
        }
    }
    
    return Pair(cameraLauncher, takePicture)
}

@Composable
fun rememberPermissionLauncher(
    onPermissionResult: (Boolean) -> Unit
): ManagedActivityResultLauncher<String, Boolean> {
    return androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult(isGranted)
    }
}