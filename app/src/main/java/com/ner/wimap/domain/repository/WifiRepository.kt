package com.ner.wimap.domain.repository

import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow

interface WifiRepository {
    fun getWifiNetworks(): Flow<List<WifiNetwork>>
    suspend fun startScan(): kotlin.Result<Unit>
    suspend fun stopScan()
    suspend fun connectToNetwork(network: WifiNetwork, password: String? = null): kotlin.Result<Boolean>
    suspend fun clearNetworks()
    fun isScanning(): Flow<Boolean>
    fun getConnectionStatus(): Flow<String?>
    fun getConnectionProgress(): Flow<String?>
    fun getConnectingNetworks(): Flow<Set<String>>
    fun getSuccessfulPasswords(): Flow<Map<String, String>>
    
    // Real-time connection progress data
    fun getCurrentPassword(): Flow<String?>
    fun getCurrentAttempt(): Flow<Int>
    fun getTotalAttempts(): Flow<Int>
    fun getConnectingNetworkName(): Flow<String?>
    suspend fun updatePasswordsFromSettings(passwords: List<String>)
    suspend fun updateConnectionSettings(maxRetries: Int, timeoutSeconds: Int, rssiThreshold: Int)
    suspend fun removeStaleNetworks(hideNetworksUnseenForSeconds: Int)
    fun clearConnectionStatus()
    fun clearConnectionProgress()
}