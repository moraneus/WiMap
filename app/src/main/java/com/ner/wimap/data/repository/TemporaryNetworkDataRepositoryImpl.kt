package com.ner.wimap.data.repository

import android.util.Log
import com.ner.wimap.data.database.TemporaryNetworkData
import com.ner.wimap.data.database.TemporaryNetworkDataDao
import com.ner.wimap.domain.repository.TemporaryNetworkDataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemporaryNetworkDataRepositoryImpl @Inject constructor(
    private val temporaryNetworkDataDao: TemporaryNetworkDataDao
) : TemporaryNetworkDataRepository {

    companion object {
        private const val TAG = "TemporaryNetworkDataRepo"
    }

    override fun getAllTemporaryData(): Flow<List<TemporaryNetworkData>> {
        return temporaryNetworkDataDao.getAllTemporaryData()
    }

    override suspend fun getTemporaryDataByBssid(bssid: String): TemporaryNetworkData? {
        val data = temporaryNetworkDataDao.getTemporaryDataByBssid(bssid)
        Log.d(TAG, "Retrieved temporary data for BSSID $bssid: ${data != null}")
        return data
    }

    override fun getTemporaryDataByBssidFlow(bssid: String): Flow<TemporaryNetworkData?> {
        return temporaryNetworkDataDao.getTemporaryDataByBssidFlow(bssid)
    }

    override suspend fun saveTemporaryData(
        bssid: String,
        ssid: String,
        comment: String,
        password: String?,
        photoPath: String?,
        isPinned: Boolean
    ) {
        Log.d(TAG, "Saving temporary data for BSSID $bssid: comment='$comment', hasPassword=${password != null}, hasPhoto=${photoPath != null}, isPinned=$isPinned")
        
        val existingData = temporaryNetworkDataDao.getTemporaryDataByBssid(bssid)
        val temporaryData = existingData?.copy(
            ssid = ssid, // Keep ssid updated
            comment = comment,
            savedPassword = password,
            photoPath = photoPath,
            isPinned = isPinned,
            lastUpdated = System.currentTimeMillis()
        ) ?: TemporaryNetworkData(
            bssid = bssid,
            ssid = ssid,
            comment = comment,
            savedPassword = password,
            photoPath = photoPath,
            isPinned = isPinned,
            lastUpdated = System.currentTimeMillis()
        )
        
        temporaryNetworkDataDao.insertOrUpdateTemporaryData(temporaryData)
        Log.d(TAG, "Successfully saved temporary data for BSSID $bssid")
    }

    override suspend fun pinNetwork(bssid: String, isPinned: Boolean) {
        val temporaryData = temporaryNetworkDataDao.getTemporaryDataByBssid(bssid)
        if (temporaryData != null) {
            temporaryNetworkDataDao.insertOrUpdateTemporaryData(temporaryData.copy(isPinned = isPinned))
        }
    }

    override suspend fun updateTemporaryData(data: TemporaryNetworkData) {
        temporaryNetworkDataDao.insertOrUpdateTemporaryData(data.copy(lastUpdated = System.currentTimeMillis()))
    }

    override suspend fun deleteTemporaryData(bssid: String) {
        temporaryNetworkDataDao.deleteTemporaryDataByBssid(bssid)
    }

    override suspend fun clearAllTemporaryData() {
        temporaryNetworkDataDao.clearAllTemporaryData()
    }

    override suspend fun deleteOldTemporaryData(cutoffTime: Long) {
        temporaryNetworkDataDao.deleteOldTemporaryData(cutoffTime)
    }
}