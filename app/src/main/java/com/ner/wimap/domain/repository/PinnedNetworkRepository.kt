package com.ner.wimap.domain.repository

import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow

interface PinnedNetworkRepository {
    fun getAllPinnedNetworks(): Flow<List<PinnedNetwork>>
    suspend fun pinNetwork(network: WifiNetwork, comment: String?, password: String?, photoUri: String?): kotlin.Result<Unit>
    suspend fun unpinNetwork(bssid: String): kotlin.Result<Unit>
    suspend fun deletePinnedNetwork(network: PinnedNetwork): kotlin.Result<Unit>
    suspend fun updateNetworkData(network: WifiNetwork, comment: String?, password: String?, photoUri: String?): kotlin.Result<Unit>
    suspend fun updatePinnedNetwork(network: PinnedNetwork): kotlin.Result<Unit>
    suspend fun clearAllPinnedNetworks(): kotlin.Result<Unit>
}