package com.ner.wimap.camera

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun rememberCameraLauncher(
    onPhotoTaken: (Uri) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            // Verify the file exists and has content
            try {
                val file = File(photoUri!!.path ?: "")
                if (file.exists() && file.length() > 0) {
                    onPhotoTaken(photoUri!!)
                } else {
                    // Try to find the file using the URI
                    context.contentResolver.openInputStream(photoUri!!)?.use { inputStream ->
                        if (inputStream.available() > 0) {
                            onPhotoTaken(photoUri!!)
                        } else {
                            onError("Photo file is empty")
                        }
                    } ?: onError("Could not access photo file")
                }
            } catch (e: Exception) {
                // Still try to return the URI as it might be valid
                onPhotoTaken(photoUri!!)
            }
        } else {
            onError("Failed to capture photo - camera operation cancelled or failed")
        }
    }
    
    return {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "WIFI_PHOTO_$timeStamp.jpg"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            
            // Ensure the directory exists
            if (storageDir != null && !storageDir.exists()) {
                storageDir.mkdirs()
            }
            
            val photoFile = File(storageDir, imageFileName)
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            
            photoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            onError("Error setting up camera: ${e.message}")
        }
    }
}

object CameraUtils {
    fun createImageFile(context: Context, networkSSID: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "WIFI_${networkSSID.replace(" ", "_")}_$timeStamp.jpg"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, imageFileName)
    }
    
    fun getImageUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}