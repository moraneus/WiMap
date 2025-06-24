package com.ner.wimap.domain.usecase

import com.ner.wimap.domain.repository.WifiRepository
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ConnectToNetworkUseCase @Inject constructor(
    private val wifiRepository: WifiRepository
) {
    suspend fun connectToNetwork(network: WifiNetwork, password: String? = null): kotlin.Result<Boolean> =
        wifiRepository.connectToNetwork(network, password)
    
    fun getConnectionStatus(): Flow<String?> = wifiRepository.getConnectionStatus()
    
    fun getConnectionProgress(): Flow<String?> = wifiRepository.getConnectionProgress()
    
    fun getConnectingNetworks(): Flow<Set<String>> = wifiRepository.getConnectingNetworks()
    
    fun getSuccessfulPasswords(): Flow<Map<String, String>> = wifiRepository.getSuccessfulPasswords()

    // Real-time connection progress data
    fun getCurrentPassword(): Flow<String?> = wifiRepository.getCurrentPassword()

    fun getCurrentAttempt(): Flow<Int> = wifiRepository.getCurrentAttempt()

    fun getTotalAttempts(): Flow<Int> = wifiRepository.getTotalAttempts()

    fun getConnectingNetworkName(): Flow<String?> = wifiRepository.getConnectingNetworkName()

    suspend fun updatePasswordsFromSettings(passwords: List<String>) = wifiRepository.updatePasswordsFromSettings(passwords)
}