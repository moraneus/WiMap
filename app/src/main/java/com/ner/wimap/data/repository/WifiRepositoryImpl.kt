package com.ner.wimap.data.repository

import com.ner.wimap.domain.repository.WifiRepository
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.viewmodel.ConnectionManager
import com.ner.wimap.ui.viewmodel.ScanManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiRepositoryImpl @Inject constructor(
    private val scanManager: ScanManager,
    private val connectionManager: ConnectionManager
) : WifiRepository {
    
    override fun getWifiNetworks(): Flow<List<WifiNetwork>> = scanManager.wifiNetworks
    
    override suspend fun startScan(): kotlin.Result<Unit> {
        return try {
            var permissionError: String? = null
            
            scanManager.startScan { error ->
                permissionError = error
            }
            
            // Give a small delay to allow the permission check to complete
            kotlinx.coroutines.delay(100)
            
            // If there was a permission error, return it as a failure
            permissionError?.let {
                return kotlin.Result.failure(SecurityException(it))
            }
            
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun stopScan() = scanManager.stopScan()
    
    override suspend fun connectToNetwork(network: WifiNetwork, password: String?): kotlin.Result<Boolean> {
        return try {
            if (password != null) {
                connectionManager.connectWithManualPassword(network, password)
            } else {
                connectionManager.connectToNetwork(network) { /* Handle password request */ }
            }
            kotlin.Result.success(true)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun clearNetworks() = scanManager.clearNetworks()
    
    override fun isScanning(): Flow<Boolean> = scanManager.isScanning
    
    override fun getConnectionStatus(): Flow<String?> = connectionManager.connectionStatus
    
    override fun getConnectionProgress(): Flow<String?> = connectionManager.connectionProgress
    
    override fun getConnectingNetworks(): Flow<Set<String>> = connectionManager.connectingNetworks
    
    override fun getSuccessfulPasswords(): Flow<Map<String, String>> = connectionManager.successfulPasswords

    // Real-time connection progress data
    override fun getCurrentPassword(): Flow<String?> = connectionManager.currentPassword

    override fun getCurrentAttempt(): Flow<Int> = connectionManager.currentAttempt

    override fun getTotalAttempts(): Flow<Int> = connectionManager.totalAttempts

    override suspend fun updatePasswordsFromSettings(passwords: List<String>) {
        connectionManager.updatePasswordsFromSettings(passwords)
    }

    override suspend fun updateConnectionSettings(maxRetries: Int, timeoutSeconds: Int, rssiThreshold: Int) {
        connectionManager.setMaxRetries(maxRetries)
        connectionManager.setConnectionTimeoutSeconds(timeoutSeconds)
        connectionManager.setRssiThresholdForConnection(rssiThreshold)
    }

    override fun getConnectingNetworkName(): Flow<String?> = connectionManager.connectingNetworkName

    override suspend fun removeStaleNetworks(hideNetworksUnseenForSeconds: Int) {
        scanManager.removeStaleNetworks(hideNetworksUnseenForSeconds)
    }
}