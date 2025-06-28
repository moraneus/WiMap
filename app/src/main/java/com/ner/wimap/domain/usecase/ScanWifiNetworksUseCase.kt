package com.ner.wimap.domain.usecase

import com.ner.wimap.domain.repository.WifiRepository
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanWifiNetworksUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    fun getWifiNetworks(): Flow<List<WifiNetwork>> = wifiRepository.getWifiNetworks()
    
    suspend fun startScan(): kotlin.Result<Unit> = wifiRepository.startScan()
    
    suspend fun stopScan() = wifiRepository.stopScan()
    
    fun isScanning(): Flow<Boolean> = wifiRepository.isScanning()
    
    suspend fun clearNetworks() = wifiRepository.clearNetworks()
    
    suspend fun removeStaleNetworks(hideNetworksUnseenForSeconds: Int) = wifiRepository.removeStaleNetworks(hideNetworksUnseenForSeconds)
}