package com.ner.wimap.data.repository

import com.ner.wimap.data.database.AppDatabase
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.data.database.PinnedNetworkDao
import com.ner.wimap.domain.repository.PinnedNetworkRepository
import com.ner.wimap.model.WifiNetwork
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinnedNetworkRepositoryImpl @Inject constructor(
    private val pinnedNetworkDao: PinnedNetworkDao
) : PinnedNetworkRepository {
    
    override fun getAllPinnedNetworks(): Flow<List<PinnedNetwork>> = 
        pinnedNetworkDao.getAllPinnedNetworks()
    
    override suspend fun pinNetwork(
        network: WifiNetwork, 
        comment: String?, 
        password: String?, 
        photoUri: String?
    ): kotlin.Result<Unit> {
        return try {
            val pinnedNetwork = PinnedNetwork(
                ssid = network.ssid,
                bssid = network.bssid,
                rssi = network.rssi,
                channel = network.channel,
                security = network.security,
                latitude = network.latitude,
                longitude = network.longitude,
                timestamp = network.timestamp,
                comment = comment,
                encryptedPassword = com.ner.wimap.utils.EncryptionUtils.encrypt(password),
                photoUri = photoUri,
                pinnedAt = System.currentTimeMillis()
            )
            pinnedNetworkDao.insertPinnedNetwork(pinnedNetwork)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun unpinNetwork(bssid: String): kotlin.Result<Unit> {
        return try {
            pinnedNetworkDao.deletePinnedNetworkByBssid(bssid)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun deletePinnedNetwork(network: PinnedNetwork): kotlin.Result<Unit> {
        return try {
            pinnedNetworkDao.deletePinnedNetwork(network)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun updateNetworkData(
        network: WifiNetwork, 
        comment: String?, 
        password: String?, 
        photoUri: String?
    ): kotlin.Result<Unit> {
        return try {
            val existingNetwork = pinnedNetworkDao.getPinnedNetworkByBssid(network.bssid)
            if (existingNetwork != null) {
                val updatedNetwork = existingNetwork.copy(
                    comment = comment,
                    encryptedPassword = com.ner.wimap.utils.EncryptionUtils.encrypt(password),
                    photoUri = photoUri
                )
                pinnedNetworkDao.updatePinnedNetwork(updatedNetwork)
            }
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun updatePinnedNetwork(network: PinnedNetwork): kotlin.Result<Unit> {
        return try {
            pinnedNetworkDao.updatePinnedNetwork(network)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun clearAllPinnedNetworks(): kotlin.Result<Unit> {
        return try {
            pinnedNetworkDao.deleteAllPinnedNetworks()
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun updateOfflineStatus(bssid: String, isOffline: Boolean, lastSeenTimestamp: Long): kotlin.Result<Unit> {
        return try {
            pinnedNetworkDao.updateOfflineStatus(bssid, isOffline, lastSeenTimestamp)
            kotlin.Result.success(Unit)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
}