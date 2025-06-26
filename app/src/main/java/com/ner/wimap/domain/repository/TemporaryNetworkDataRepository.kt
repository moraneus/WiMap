package com.ner.wimap.domain.repository

import com.ner.wimap.data.database.TemporaryNetworkData
import kotlinx.coroutines.flow.Flow

interface TemporaryNetworkDataRepository {
    fun getAllTemporaryData(): Flow<List<TemporaryNetworkData>>
    suspend fun getTemporaryDataByBssid(bssid: String): TemporaryNetworkData?
    fun getTemporaryDataByBssidFlow(bssid: String): Flow<TemporaryNetworkData?>
    suspend fun saveTemporaryData(
        bssid: String,
        ssid: String,
        comment: String = "",
        password: String? = null,
        photoPath: String? = null,
        isPinned: Boolean = false
    )

    suspend fun pinNetwork(bssid: String, isPinned: Boolean)
    suspend fun updateTemporaryData(data: TemporaryNetworkData)
    suspend fun deleteTemporaryData(bssid: String)
    suspend fun clearAllTemporaryData()
    suspend fun deleteOldTemporaryData(cutoffTime: Long)
}