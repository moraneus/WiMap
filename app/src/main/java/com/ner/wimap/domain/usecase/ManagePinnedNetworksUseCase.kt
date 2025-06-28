package com.ner.wimap.domain.usecase

import android.net.Uri
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.domain.repository.PinnedNetworkRepository
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject

class ManagePinnedNetworksUseCase @Inject constructor(
    private val pinnedNetworkRepository: PinnedNetworkRepository
) {
    fun getAllPinnedNetworks(): Flow<List<PinnedNetwork>> = 
        pinnedNetworkRepository.getAllPinnedNetworks()
    
    suspend fun pinNetwork(
        network: WifiNetwork, 
        comment: String?, 
        password: String?, 
        photoUri: String?
    ): kotlin.Result<Unit> = pinnedNetworkRepository.pinNetwork(network, comment, password, photoUri)
    
    suspend fun unpinNetwork(bssid: String): kotlin.Result<Unit> = 
        pinnedNetworkRepository.unpinNetwork(bssid)
    
    suspend fun deletePinnedNetwork(network: PinnedNetwork): kotlin.Result<Unit> = 
        pinnedNetworkRepository.deletePinnedNetwork(network)
    
    suspend fun updateNetworkData(
        network: WifiNetwork, 
        comment: String?, 
        password: String?, 
        photoUri: String?
    ): kotlin.Result<Unit> = pinnedNetworkRepository.updateNetworkData(network, comment, password, photoUri)
    
    suspend fun updateNetworkDataWithPhotoDeletion(
        network: WifiNetwork, 
        comment: String?, 
        password: String?, 
        photoUri: String?,
        clearPhoto: Boolean = false
    ): kotlin.Result<Unit> {
        return try {
            if (clearPhoto) {
                // Get existing network to delete its photo
                var existingPhotoUri: String? = null
                pinnedNetworkRepository.getAllPinnedNetworks().collect { networks ->
                    existingPhotoUri = networks.find { it.bssid == network.bssid }?.photoUri
                }
                existingPhotoUri?.let { deletePhotoFile(it) }
                pinnedNetworkRepository.updateNetworkData(network, comment, password, null)
            } else {
                pinnedNetworkRepository.updateNetworkData(network, comment, password, photoUri)
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    suspend fun clearAllPinnedNetworks(): kotlin.Result<Unit> = 
        pinnedNetworkRepository.clearAllPinnedNetworks()

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
}