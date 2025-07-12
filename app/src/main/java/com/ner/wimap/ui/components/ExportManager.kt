package com.ner.wimap.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.data.database.PinnedNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

enum class ExportFormat {
    CSV,
    GOOGLE_MAPS,
    PDF
}

enum class ExportAction {
    SAVE_ONLY,
    SHARE_ONLY,
    SAVE_AND_SHARE
}

class ExportManager(
    private val viewModelScope: CoroutineScope
) {
    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    companion object {
        private const val TAG = "ExportManager"
    }

    fun exportWifiNetworks(
        context: Context,
        networks: List<WifiNetwork>,
        format: ExportFormat,
        action: ExportAction = ExportAction.SAVE_AND_SHARE,
        onComplete: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting export of ${networks.size} networks as ${format.name} with action ${action.name}")

                val actionText = when (action) {
                    ExportAction.SAVE_ONLY -> "Saving"
                    ExportAction.SHARE_ONLY -> "Preparing to share"
                    ExportAction.SAVE_AND_SHARE -> "Exporting"
                }

                _exportStatus.value = "$actionText ${networks.size} networks as ${format.name}..."

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val fileName = when (format) {
                    ExportFormat.CSV -> "WiMap_Networks_$timestamp.csv"
                    ExportFormat.GOOGLE_MAPS -> "WiMap_GoogleMaps_$timestamp.kml"
                    ExportFormat.PDF -> "WiMap_Report_$timestamp.pdf"
                }

                val file = withContext(Dispatchers.IO) {
                    createExportFile(context, fileName, format, action)
                }

                Log.d(TAG, "Export file path: ${file.absolutePath}")

                // Perform heavy file I/O operations on background thread
                withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.CSV -> exportToCsv(networks, file)
                        ExportFormat.GOOGLE_MAPS -> exportToGoogleMaps(networks, file)
                        ExportFormat.PDF -> exportToPdf(networks, file, context)
                    }
                }

                Log.d(TAG, "Export completed. File exists: ${file.exists()}, Size: ${file.length()}")

                if (file.exists() && file.length() > 0) {
                    // Handle export action on Main dispatcher for UI operations
                    withContext(Dispatchers.Main) {
                        handleExportAction(context, file, format, action)
                        onComplete(file.absolutePath)
                    }
                } else {
                    throw Exception("File was not created or is empty")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _errorMessage.value = "Export failed: ${e.message}"
                _exportStatus.value = null
            }
        }
    }

    fun exportPinnedNetwork(
        context: Context,
        network: PinnedNetwork,
        format: ExportFormat,
        action: ExportAction = ExportAction.SAVE_AND_SHARE,
        onComplete: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting export of pinned network ${network.ssid} as ${format.name}")

                val actionText = when (action) {
                    ExportAction.SAVE_ONLY -> "Saving"
                    ExportAction.SHARE_ONLY -> "Preparing to share"
                    ExportAction.SAVE_AND_SHARE -> "Exporting"
                }

                _exportStatus.value = "$actionText ${network.ssid} as ${format.name}..."

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val safeSsid = network.ssid.replace(Regex("[^a-zA-Z0-9]"), "_")
                val fileName = when (format) {
                    ExportFormat.CSV -> "WiMap_${safeSsid}_$timestamp.csv"
                    ExportFormat.GOOGLE_MAPS -> "WiMap_${safeSsid}_$timestamp.kml"
                    ExportFormat.PDF -> "WiMap_${safeSsid}_$timestamp.pdf"
                }

                val file = withContext(Dispatchers.IO) {
                    createExportFile(context, fileName, format, action)
                }

                // Convert PinnedNetwork to WifiNetwork for export - decrypt password in background
                val wifiNetwork = withContext(Dispatchers.IO) {
                    val decryptedPassword = com.ner.wimap.utils.EncryptionUtils.decrypt(network.encryptedPassword)
                    WifiNetwork(
                        ssid = network.ssid,
                        bssid = network.bssid,
                        rssi = network.rssi,
                        channel = network.channel,
                        security = network.security,
                        latitude = network.latitude,
                        longitude = network.longitude,
                        timestamp = network.timestamp,
                        password = decryptedPassword
                    )
                }

                // Perform heavy file I/O operations on background thread
                withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.CSV -> exportToCsv(listOf(wifiNetwork), file)
                        ExportFormat.GOOGLE_MAPS -> exportToGoogleMaps(listOf(wifiNetwork), file)
                        ExportFormat.PDF -> exportToPdf(listOf(wifiNetwork), file, context)
                    }
                }

                if (file.exists() && file.length() > 0) {
                    // Handle export action on Main dispatcher for UI operations
                    withContext(Dispatchers.Main) {
                        handleExportAction(context, file, format, action, network.ssid)
                        onComplete(file.absolutePath)
                    }
                } else {
                    throw Exception("File was not created or is empty")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Pinned network export failed", e)
                _errorMessage.value = "Export failed: ${e.message}"
                _exportStatus.value = null
            }
        }
    }

    private fun createExportFile(context: Context, fileName: String, format: ExportFormat, action: ExportAction): File {
        // Choose storage location based on action
        val possibleDirectories = when (action) {
            ExportAction.SAVE_ONLY -> listOf(
                // Prefer public Downloads for save-only
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                File(context.filesDir, "exports")
            )
            ExportAction.SHARE_ONLY -> listOf(
                // Use cache for share-only (temporary)
                File(context.cacheDir, "exports"),
                context.getExternalFilesDir(null),
                File(context.filesDir, "exports")
            )
            ExportAction.SAVE_AND_SHARE -> listOf(
                // App's external directory for both save and share
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                context.getExternalFilesDir(null),
                File(context.filesDir, "exports")
            )
        }

        for (directory in possibleDirectories) {
            try {
                if (directory != null) {
                    val wiMapDir = File(directory, "WiMap")
                    if (!wiMapDir.exists()) {
                        val created = wiMapDir.mkdirs()
                        Log.d(TAG, "Created directory ${wiMapDir.absolutePath}: $created")
                    }

                    if (wiMapDir.exists() && wiMapDir.canWrite()) {
                        val file = File(wiMapDir, fileName)
                        Log.d(TAG, "Using directory: ${wiMapDir.absolutePath}")
                        return file
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create directory: ${directory?.absolutePath}", e)
                continue
            }
        }

        // Fallback
        val cacheDir = File(context.cacheDir, "WiMap")
        cacheDir.mkdirs()
        return File(cacheDir, fileName)
    }

    private fun handleExportAction(
        context: Context,
        file: File,
        format: ExportFormat,
        action: ExportAction,
        networkName: String? = null
    ) {
        val itemName = networkName ?: "WiFi networks"
        val fileName = file.name
        val directory = file.parent ?: "unknown location"

        when (action) {
            ExportAction.SAVE_ONLY -> {
                val message = "✅ $itemName saved as $fileName"
                _exportStatus.value = "$message\n📁 Location: $directory"
                showFileLocationNotification(context, file, itemName)
            }
            ExportAction.SHARE_ONLY -> {
                shareFile(context, file, format, itemName)
                _exportStatus.value = "📤 Sharing $itemName..."
            }
            ExportAction.SAVE_AND_SHARE -> {
                val message = "✅ $itemName saved as $fileName"
                _exportStatus.value = "$message\n📁 Location: $directory"
                shareFile(context, file, format, itemName)
            }
        }
    }

    private fun showFileLocationNotification(context: Context, file: File, itemName: String) {
        // Show toast with file save location
        val message = "$itemName saved to ${file.name}"
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        Log.d(TAG, "File saved to: ${file.absolutePath}")
    }

    private fun shareFile(context: Context, file: File, format: ExportFormat, itemName: String = "WiFi networks") {
        try {
            Log.d(TAG, "Attempting to share file: ${file.absolutePath}")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when (format) {
                ExportFormat.CSV -> "text/csv"
                ExportFormat.GOOGLE_MAPS -> "application/vnd.google-earth.kml+xml"
                ExportFormat.PDF -> "application/pdf"
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "$itemName exported from WiMap")
                putExtra(Intent.EXTRA_SUBJECT, "WiMap Export - ${format.name}")
                type = mimeType
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share $itemName"))
            Toast.makeText(context, "Sharing $itemName...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Share intent launched successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share file", e)
            _errorMessage.value = "Failed to share file: ${e.message}"
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Rest of the methods remain the same (exportToCsv, exportToGoogleMaps, exportToPdf)
    // I'll include them for completeness but they're identical to the previous version

    private fun exportToCsv(networks: List<WifiNetwork>, file: File) {
        try {
            Log.d(TAG, "Starting CSV export to ${file.absolutePath}")
            Log.d(TAG, "Exporting ${networks.size} networks (including offline networks)")
            file.parentFile?.mkdirs()

            FileWriter(file).use { writer ->
                PrintWriter(writer).use { csvWriter ->
                    csvWriter.println("SSID,BSSID,RSSI,Channel,Security,Latitude,Longitude,Frequency,Timestamp,Password,IsOffline,Comment")

                    networks.forEach { network ->
                        val passwordValue = network.password ?: "N/A"
                        val frequency = if (network.channel <= 14) "2.4 GHz" else "5 GHz"
                        // Use current time if timestamp is invalid (0 or too old)
                        val validTimestamp = if (network.timestamp < 1000000000000L) { // Before year 2001
                            System.currentTimeMillis()
                        } else {
                            network.timestamp
                        }
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(validTimestamp))
                        val isOffline = if (network.isOffline) "Yes" else "No"
                        val commentValue = if (network.comment.isNotEmpty()) network.comment else "WiFi Network found by WiMap"

                        csvWriter.println(
                            "\"${network.ssid.replace("\"", "\"\"")}\"," +
                                    "${network.bssid}," +
                                    "${network.rssi}," +
                                    "${network.channel}," +
                                    "\"${network.security}\"," +
                                    "${network.latitude ?: ""}," +
                                    "${network.longitude ?: ""}," +
                                    "\"$frequency\"," +
                                    "\"$timestamp\"," +
                                    "\"$passwordValue\"," +
                                    "$isOffline," +
                                    "\"$commentValue\""
                        )
                    }
                    csvWriter.flush()
                }
            }
            Log.d(TAG, "CSV export completed. File size: ${file.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed", e)
            throw e
        }
    }

    private fun exportToGoogleMaps(networks: List<WifiNetwork>, file: File) {
        try {
            Log.d(TAG, "Starting KML export to ${file.absolutePath}")
            Log.d(TAG, "Exporting ${networks.size} networks (including offline networks)")
            file.parentFile?.mkdirs()

            val kmlContent = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
                appendLine("  <Document>")
                appendLine("    <name>WiMap WiFi Networks</name>")
                appendLine("    <description>WiFi networks discovered by WiMap</description>")

                // Define styles
                appendLine("    <Style id=\"openNetwork\">")
                appendLine("      <IconStyle>")
                appendLine("        <color>ff00ff00</color>")
                appendLine("        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/wifi.png</href></Icon>")
                appendLine("      </IconStyle>")
                appendLine("    </Style>")

                appendLine("    <Style id=\"securedNetwork\">")
                appendLine("      <IconStyle>")
                appendLine("        <color>ff0000ff</color>")
                appendLine("        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/wifi.png</href></Icon>")
                appendLine("      </IconStyle>")
                appendLine("    </Style>")

                appendLine("    <Style id=\"knownPassword\">")
                appendLine("      <IconStyle>")
                appendLine("        <color>ffff0000</color>")
                appendLine("        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/wifi.png</href></Icon>")
                appendLine("      </IconStyle>")
                appendLine("    </Style>")

                appendLine("    <Style id=\"offlineNetwork\">")
                appendLine("      <IconStyle>")
                appendLine("        <color>ff888888</color>")
                appendLine("        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/wifi.png</href></Icon>")
                appendLine("      </IconStyle>")
                appendLine("    </Style>")

                networks.forEach { network ->
                    if (network.latitude != null && network.longitude != null &&
                        network.latitude != 0.0 && network.longitude != 0.0) {

                        val style = when {
                            network.isOffline -> "offlineNetwork"
                            !network.password.isNullOrEmpty() -> "knownPassword"
                            network.security.contains("Open", ignoreCase = true) -> "openNetwork"
                            else -> "securedNetwork"
                        }

                        val signalStrength = when {
                            network.rssi >= -50 -> "Excellent"
                            network.rssi >= -60 -> "Good"
                            network.rssi >= -70 -> "Fair"
                            else -> "Poor"
                        }

                        appendLine("    <Placemark>")
                        appendLine("      <name>${network.ssid.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</name>")
                        appendLine("      <description><![CDATA[")
                        appendLine("        <b>BSSID:</b> ${network.bssid}<br/>")
                        appendLine("        <b>Security:</b> ${network.security}<br/>")
                        appendLine("        <b>Signal:</b> ${network.rssi} dBm ($signalStrength)<br/>")
                        appendLine("        <b>Channel:</b> ${network.channel}<br/>")
                        appendLine("        <b>Frequency:</b> ${if (network.channel <= 14) "2.4 GHz" else "5 GHz"}<br/>")
                        // Use current time if timestamp is invalid (0 or too old)
                        val validTimestamp = if (network.timestamp < 1000000000000L) { // Before year 2001
                            System.currentTimeMillis()
                        } else {
                            network.timestamp
                        }
                        appendLine("        <b>Discovered:</b> ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(validTimestamp))}<br/>")
                        if (!network.password.isNullOrEmpty()) {
                            appendLine("        <b>Password:</b> ${network.password}<br/>")
                        }
                        if (network.comment.isNotEmpty()) {
                            appendLine("        <b>Comment:</b> ${network.comment.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}<br/>")
                        }
                        if (network.isOffline) {
                            appendLine("        <b>Status:</b> Offline (not currently visible)<br/>")
                        } else {
                            appendLine("        <b>Status:</b> Online<br/>")
                        }
                        appendLine("        <br/><i>Discovered by WiMap</i>")
                        appendLine("      ]]></description>")
                        appendLine("      <styleUrl>#$style</styleUrl>")
                        appendLine("      <Point>")
                        appendLine("        <coordinates>${network.longitude},${network.latitude},0</coordinates>")
                        appendLine("      </Point>")
                        appendLine("    </Placemark>")
                    }
                }

                appendLine("  </Document>")
                appendLine("</kml>")
            }

            file.writeText(kmlContent)
            Log.d(TAG, "KML export completed. File size: ${file.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "KML export failed", e)
            throw e
        }
    }

    private fun exportToPdf(networks: List<WifiNetwork>, file: File, context: Context) {
        try {
            Log.d(TAG, "Starting PDF export to ${file.absolutePath}")
            Log.d(TAG, "Exporting ${networks.size} networks (including offline networks)")
            file.parentFile?.mkdirs()

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Paint objects
            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
            }

            val headerPaint = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            }

            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.DEFAULT
            }

            val smallPaint = Paint().apply {
                color = Color.GRAY
                textSize = 10f
                typeface = Typeface.DEFAULT
            }

            var yPosition = 50f
            val leftMargin = 50f
            val rightMargin = 545f

            // Create PDF content (same as before)
            canvas.drawText("WiMap WiFi Networks Report", leftMargin, yPosition, titlePaint)
            yPosition += 40f

            val timestamp = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.US).format(Date())
            canvas.drawText("Generated on $timestamp", leftMargin, yPosition, smallPaint)
            yPosition += 30f

            canvas.drawText("Total Networks Found: ${networks.size}", leftMargin, yPosition, headerPaint)
            yPosition += 20f

            val securedNetworks = networks.count { !it.security.contains("Open", ignoreCase = true) }
            val openNetworks = networks.size - securedNetworks
            val networksWithPasswords = networks.count { !it.password.isNullOrEmpty() }
            val networksWithGPS = networks.count { it.latitude != null && it.longitude != null && it.latitude != 0.0 && it.longitude != 0.0 }
            val offlineNetworks = networks.count { it.isOffline }
            val onlineNetworks = networks.size - offlineNetworks

            canvas.drawText("• Online Networks: $onlineNetworks", leftMargin + 20f, yPosition, bodyPaint)
            yPosition += 15f
            canvas.drawText("• Offline Networks: $offlineNetworks", leftMargin + 20f, yPosition, bodyPaint)
            yPosition += 15f
            canvas.drawText("• Secured Networks: $securedNetworks", leftMargin + 20f, yPosition, bodyPaint)
            yPosition += 15f
            canvas.drawText("• Open Networks: $openNetworks", leftMargin + 20f, yPosition, bodyPaint)
            yPosition += 15f
            canvas.drawText("• Networks with Known Passwords: $networksWithPasswords", leftMargin + 20f, yPosition, bodyPaint)
            yPosition += 15f
            canvas.drawText("• Networks with GPS Location: $networksWithGPS", leftMargin + 20f, yPosition, bodyPaint)
            yPosition += 30f

            canvas.drawLine(leftMargin, yPosition, rightMargin, yPosition, bodyPaint)
            yPosition += 20f

            var pageNumber = 1
            var networkCount = 0

            for (network in networks) {
                if (yPosition > 750f) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPosition = 50f
                    pageNumber++
                }

                networkCount++

                val networkTitle = if (network.isOffline) {
                    "${networkCount}. ${network.ssid} [OFFLINE]"
                } else {
                    "${networkCount}. ${network.ssid}"
                }
                canvas.drawText(networkTitle, leftMargin, yPosition, headerPaint)
                yPosition += 20f

                canvas.drawText("BSSID: ${network.bssid}", leftMargin + 20f, yPosition, bodyPaint)
                yPosition += 15f

                canvas.drawText("Security: ${network.security}", leftMargin + 20f, yPosition, bodyPaint)
                yPosition += 15f

                val signalStrength = when {
                    network.rssi >= -50 -> "Excellent"
                    network.rssi >= -60 -> "Good"
                    network.rssi >= -70 -> "Fair"
                    else -> "Poor"
                }
                canvas.drawText("Signal: ${network.rssi} dBm ($signalStrength)", leftMargin + 20f, yPosition, bodyPaint)
                yPosition += 15f

                canvas.drawText("Channel: ${network.channel} (${if (network.channel <= 14) "2.4 GHz" else "5 GHz"})", leftMargin + 20f, yPosition, bodyPaint)
                yPosition += 15f

                if (network.latitude != null && network.longitude != null && network.latitude != 0.0 && network.longitude != 0.0) {
                    canvas.drawText("Location: ${String.format("%.6f", network.latitude)}, ${String.format("%.6f", network.longitude)}", leftMargin + 20f, yPosition, bodyPaint)
                    yPosition += 15f
                }

                if (!network.password.isNullOrEmpty()) {
                    canvas.drawText("Password: ${network.password}", leftMargin + 20f, yPosition, bodyPaint)
                    yPosition += 15f
                }

                val status = if (network.isOffline) "Offline (not currently visible)" else "Online"
                canvas.drawText("Status: $status", leftMargin + 20f, yPosition, bodyPaint)
                yPosition += 15f

                // Use current time if timestamp is invalid (0 or too old)
                val validTimestamp = if (network.timestamp < 1000000000000L) { // Before year 2001
                    System.currentTimeMillis()
                } else {
                    network.timestamp
                }
                val discoveryTime = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(validTimestamp))
                canvas.drawText("Discovered: $discoveryTime", leftMargin + 20f, yPosition, smallPaint)
                yPosition += 15f
                
                if (network.comment.isNotEmpty()) {
                    canvas.drawText("Comment: ${network.comment}", leftMargin + 20f, yPosition, smallPaint)
                    yPosition += 15f
                }
                
                yPosition += 10f // Extra spacing after each network
            }

            if (yPosition > 800f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPosition = 50f
            }

            canvas.drawText("Generated by WiMap - WiFi Network Scanner", leftMargin, 820f, smallPaint)
            canvas.drawText("Page $pageNumber", rightMargin - 50f, 820f, smallPaint)

            pdfDocument.finishPage(page)

            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            }
            pdfDocument.close()

            Log.d(TAG, "PDF export completed. File size: ${file.length()}")
        } catch (e: Exception) {
            Log.e(TAG, "PDF export failed", e)
            throw e
        }
    }

    fun clearStatus() {
        _exportStatus.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }
}