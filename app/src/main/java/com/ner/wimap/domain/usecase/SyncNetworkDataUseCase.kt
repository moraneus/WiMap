package com.ner.wimap.domain.usecase

import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.domain.repository.PinnedNetworkRepository
import com.ner.wimap.domain.repository.TemporaryNetworkDataRepository
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case responsible for managing pinned networks without automatic syncing
 * Pinned networks should only be updated through explicit user actions
 */
@Singleton
class SyncNetworkDataUseCase @Inject constructor(
    private val temporaryNetworkDataRepository: TemporaryNetworkDataRepository,
    private val pinnedNetworkRepository: PinnedNetworkRepository
) {

    /**
     * Get pinned networks without automatic syncing from temporary data
     * Pinned networks should only be updated through explicit user actions
     */
    fun getPinnedNetworksWithLiveUpdates(): Flow<List<PinnedNetwork>> {
        // Return pinned networks as-is, without automatic syncing from temporary data
        return pinnedNetworkRepository.getAllPinnedNetworks()
    }

    /**
     * Explicitly update a pinned network with new data (user action only)
     * This should only be called when user explicitly modifies pinned network data
     */
    suspend fun updatePinnedNetworkExplicitly(
        bssid: String,
        comment: String? = null,
        password: String? = null,
        photoUri: String? = null
    ) {
        val wifiNetwork = WifiNetwork(
            ssid = "", // Will be filled by repository
            bssid = bssid,
            rssi = 0,
            channel = 0,
            security = "",
            latitude = null,
            longitude = null,
            timestamp = System.currentTimeMillis()
        )
        
        pinnedNetworkRepository.updateNetworkData(
            network = wifiNetwork,
            comment = comment,
            password = password,
            photoUri = photoUri
        )
    }

    /**
     * Remove temporary data when a network is unpinned
     */
    suspend fun cleanupTemporaryDataOnUnpin(bssid: String) {
        val temporaryData = temporaryNetworkDataRepository.getTemporaryDataByBssid(bssid)
        if (temporaryData != null) {
            // Update the isPinned flag to false but keep other data
            val updatedData = temporaryData.copy(isPinned = false)
            temporaryNetworkDataRepository.updateTemporaryData(updatedData)
        }
    }

    /**
     * Delete both temporary data and pinned network data
     */
    suspend fun deleteNetworkCompletely(bssid: String) {
        // Delete from temporary data
        temporaryNetworkDataRepository.deleteTemporaryData(bssid)
        
        // Delete from pinned networks
        pinnedNetworkRepository.unpinNetwork(bssid)
    }
}