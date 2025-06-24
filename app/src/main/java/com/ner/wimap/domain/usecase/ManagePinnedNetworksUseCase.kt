package com.ner.wimap.domain.usecase

import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.domain.repository.PinnedNetworkRepository
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow
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
    
    suspend fun clearAllPinnedNetworks(): kotlin.Result<Unit> = 
        pinnedNetworkRepository.clearAllPinnedNetworks()
}