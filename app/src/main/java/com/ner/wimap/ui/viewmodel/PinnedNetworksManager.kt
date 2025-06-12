package com.ner.wimap.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.data.database.PinnedNetworkDao
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class PinnedNetworksManager(
    private val pinnedNetworkDao: PinnedNetworkDao,
    private val viewModelScope: CoroutineScope
) {
    private val _pinnedNetworks = MutableStateFlow<List<PinnedNetwork>>(emptyList())
    val pinnedNetworks: StateFlow<List<PinnedNetwork>> = _pinnedNetworks

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun initialize() {
        viewModelScope.launch {
            pinnedNetworkDao.getAllPinnedNetworks().collect { networks ->
                _pinnedNetworks.value = networks
            }
        }
    }

    fun pinNetwork(network: WifiNetwork, comment: String? = null, password: String? = null, photoUri: String? = null) {
        viewModelScope.launch {
            try {
                val pinnedNetwork = PinnedNetwork(
                    bssid = network.bssid,
                    ssid = network.ssid,
                    rssi = network.rssi,
                    channel = network.channel,
                    security = network.security,
                    latitude = network.latitude,
                    longitude = network.longitude,
                    timestamp = network.timestamp,
                    comment = comment,
                    savedPassword = password,
                    photoUri = photoUri,
                    pinnedAt = System.currentTimeMillis()
                )
                pinnedNetworkDao.insertPinnedNetwork(pinnedNetwork)
                _statusMessage.value = "📌 Pinned ${network.ssid}"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to pin network: ${e.message}"
            }
        }
    }

    fun unpinNetwork(bssid: String) {
        viewModelScope.launch {
            try {
                pinnedNetworkDao.deletePinnedNetworkByBssid(bssid)
                _statusMessage.value = "📌 Network unpinned"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to unpin network: ${e.message}"
            }
        }
    }

    fun deletePinnedNetwork(network: PinnedNetwork) {
        viewModelScope.launch {
            try {
                pinnedNetworkDao.deletePinnedNetwork(network)
                _statusMessage.value = "🗑️ Removed ${network.ssid} from pinned networks"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete pinned network: ${e.message}"
            }
        }
    }

    fun updateNetworkData(network: WifiNetwork, comment: String?, password: String?, photoUri: String?) {
        viewModelScope.launch {
            try {
                val existingPinned = pinnedNetworkDao.getPinnedNetworkByBssid(network.bssid)
                if (existingPinned != null) {
                    val updatedNetwork = existingPinned.copy(
                        comment = comment,
                        savedPassword = password,
                        photoUri = photoUri
                    )
                    pinnedNetworkDao.updatePinnedNetwork(updatedNetwork)
                } else {
                    pinNetwork(network, comment, password, photoUri)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update network data: ${e.message}"
            }
        }
    }

    suspend fun isNetworkPinned(bssid: String): Boolean {
        return try {
            pinnedNetworkDao.isPinned(bssid) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun updatePinnedNetwork(
        bssid: String,
        comment: String? = null,
        password: String? = null,
        photoUri: String? = null
    ) {
        viewModelScope.launch {
            try {
                val existingNetwork = pinnedNetworkDao.getPinnedNetworkByBssid(bssid)
                existingNetwork?.let { network ->
                    val updatedNetwork = network.copy(
                        comment = comment ?: network.comment,
                        savedPassword = password ?: network.savedPassword,
                        photoUri = photoUri ?: network.photoUri
                    )
                    pinnedNetworkDao.updatePinnedNetwork(updatedNetwork)
                    _statusMessage.value = "📝 Updated pinned network info"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update pinned network: ${e.message}"
            }
        }
    }

    fun exportPinnedNetworkToCsv(context: Context, network: PinnedNetwork): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val filename = "pinned_network_${network.ssid.replace(" ", "_")}_$timestamp.csv"
        val file = File(context.getExternalFilesDir(null), filename)

        try {
            val writer = FileWriter(file)
            val csvWriter = PrintWriter(writer)

            // Write header
            csvWriter.println("SSID,BSSID,RSSI,Channel,Security,Latitude,Longitude,Comment,HasPassword,PinnedAt")

            // Write data
            csvWriter.println(
                "${network.ssid}," +
                        "${network.bssid}," +
                        "${network.rssi}," +
                        "${network.channel}," +
                        "${network.security}," +
                        "${network.latitude ?: ""}," +
                        "${network.longitude ?: ""}," +
                        "\"${network.comment ?: ""}\"," +
                        "${if (!network.savedPassword.isNullOrEmpty()) "Yes" else "No"}," +
                        "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(network.pinnedAt))}"
            )

            csvWriter.close()
            _statusMessage.value = "Exported ${network.ssid} to $filename"
            return file.absolutePath
        } catch (e: Exception) {
            _errorMessage.value = "Export error: ${e.message}"
            return ""
        }
    }

    fun sharePinnedNetwork(context: Context, network: PinnedNetwork) {
        val filePath = exportPinnedNetworkToCsv(context, network)
        if (filePath.isEmpty()) {
            _errorMessage.value = "Nothing to share - export failed"
            return
        }

        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Shared WiFi Network: ${network.ssid}")
            type = "text/csv"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share ${network.ssid}"))
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}