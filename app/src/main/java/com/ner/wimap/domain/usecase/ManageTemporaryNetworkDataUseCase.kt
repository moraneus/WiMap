package com.ner.wimap.domain.usecase

import android.net.Uri
import com.ner.wimap.data.database.TemporaryNetworkData
import com.ner.wimap.domain.repository.TemporaryNetworkDataRepository
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManageTemporaryNetworkDataUseCase @Inject constructor(
    private val temporaryNetworkDataRepository: TemporaryNetworkDataRepository
) {

    fun getAllTemporaryData(): Flow<List<TemporaryNetworkData>> {
        return temporaryNetworkDataRepository.getAllTemporaryData()
    }

    suspend fun getTemporaryDataByBssid(bssid: String): TemporaryNetworkData? {
        return temporaryNetworkDataRepository.getTemporaryDataByBssid(bssid)
    }

    fun getTemporaryDataByBssidFlow(bssid: String): Flow<TemporaryNetworkData?> {
        return temporaryNetworkDataRepository.getTemporaryDataByBssidFlow(bssid)
    }

    suspend fun saveTemporaryNetworkData(
        network: WifiNetwork,
        comment: String = "",
        password: String? = null,
        photoPath: String? = null,
        isPinned: Boolean = false
    ) {
        temporaryNetworkDataRepository.saveTemporaryData(
            bssid = network.bssid,
            ssid = network.ssid,
            comment = comment,
            password = password,
            photoPath = photoPath,
            isPinned = isPinned
        )
    }

    suspend fun pinNetwork(bssid: String, isPinned: Boolean) {
        temporaryNetworkDataRepository.pinNetwork(bssid, isPinned)
    }

    suspend fun updateTemporaryNetworkData(
        bssid: String,
        ssid: String,
        comment: String = "",
        password: String? = null,
        photoPath: String? = null,
        clearPhoto: Boolean = false
    ) {
        val existingData = temporaryNetworkDataRepository.getTemporaryDataByBssid(bssid)
        
        // Handle photo deletion vs update
        val finalPhotoPath = when {
            clearPhoto -> {
                // Delete existing photo file if it exists
                existingData?.photoPath?.let { deletePhotoFile(it) }
                null
            }
            photoPath != null -> photoPath
            else -> existingData?.photoPath
        }
        
        val updatedData = (existingData ?: TemporaryNetworkData(bssid = bssid, ssid = ssid)).copy(
            comment = comment,
            savedPassword = password ?: existingData?.savedPassword,
            photoPath = finalPhotoPath,
            lastUpdated = System.currentTimeMillis()
        )
        temporaryNetworkDataRepository.updateTemporaryData(updatedData)
    }

    suspend fun saveOrUpdateTemporaryNetworkData(
        bssid: String,
        ssid: String,
        comment: String = "",
        password: String? = null,
        photoPath: String? = null,
        isPinned: Boolean? = null,
        clearPhoto: Boolean = false
    ) {
        val existingData = getTemporaryDataByBssid(bssid)
        if (existingData != null) {
            // Handle photo deletion vs update
            val finalPhotoPath = when {
                clearPhoto -> {
                    // Delete existing photo file if it exists
                    existingData.photoPath?.let { deletePhotoFile(it) }
                    null
                }
                photoPath != null -> photoPath
                else -> existingData.photoPath
            }
            
            // Update existing data
            val updatedData = existingData.copy(
                comment = comment,
                savedPassword = password ?: existingData.savedPassword,
                photoPath = finalPhotoPath,
                isPinned = isPinned ?: existingData.isPinned,
                lastUpdated = System.currentTimeMillis()
            )
            temporaryNetworkDataRepository.updateTemporaryData(updatedData)
        } else {
            // Save new data
            temporaryNetworkDataRepository.saveTemporaryData(
                bssid = bssid,
                ssid = ssid,
                comment = comment,
                password = password,
                photoPath = photoPath,
                isPinned = isPinned ?: false
            )
        }
    }

    suspend fun deleteTemporaryData(bssid: String) {
        temporaryNetworkDataRepository.deleteTemporaryData(bssid)
    }

    suspend fun clearAllTemporaryData() {
        temporaryNetworkDataRepository.clearAllTemporaryData()
    }

    suspend fun cleanupOldTemporaryData(maxAgeHours: Int = 24) {
        val cutoffTime = System.currentTimeMillis() - (maxAgeHours * 60 * 60 * 1000L)
        temporaryNetworkDataRepository.deleteOldTemporaryData(cutoffTime)
    }

    /**
     * Merges temporary data with a WifiNetwork to create an enriched network object
     */
    suspend fun enrichNetworkWithTemporaryData(network: WifiNetwork): WifiNetwork {
        val temporaryData = getTemporaryDataByBssid(network.bssid)
        return if (temporaryData != null) {
            network.copy(
                password = temporaryData.savedPassword ?: network.password,
                comment = temporaryData.comment,
                photoPath = temporaryData.photoPath,
                isPinned = temporaryData.isPinned
            )
        } else {
            network
        }
    }

    /**
     * Gets temporary data for multiple networks efficiently
     */
    suspend fun getTemporaryDataForNetworks(networks: List<WifiNetwork>): Map<String, TemporaryNetworkData> {
        val temporaryDataMap = mutableMapOf<String, TemporaryNetworkData>()
        networks.forEach { network ->
            val temporaryData = getTemporaryDataByBssid(network.bssid)
            if (temporaryData != null) {
                temporaryDataMap[network.bssid] = temporaryData
            }
        }
        return temporaryDataMap
    }

    /**
     * Deletes a photo file from the filesystem
     */
    private fun deletePhotoFile(photoPath: String): Boolean {
        return try {
            // Handle both URI strings and file paths
            val file = if (photoPath.startsWith("content://") || photoPath.startsWith("file://")) {
                // Extract file path from URI
                val uri = Uri.parse(photoPath)
                uri.path?.let { File(it) }
            } else {
                // Direct file path
                File(photoPath)
            }
            
            file?.let { 
                if (it.exists()) {
                    it.delete()
                } else {
                    false
                }
            } ?: false
        } catch (e: Exception) {
            // Log error but don't crash the app
            false
        }
    }

    /**
     * Cleanup method to delete photo file when removing temporary data
     */
    suspend fun deleteTemporaryDataWithCleanup(bssid: String) {
        val existingData = getTemporaryDataByBssid(bssid)
        existingData?.photoPath?.let { deletePhotoFile(it) }
        temporaryNetworkDataRepository.deleteTemporaryData(bssid)
    }
}