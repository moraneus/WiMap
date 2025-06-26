package com.ner.wimap.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TemporaryNetworkDataDao {
    @Query("SELECT * FROM temporary_network_data")
    fun getAllTemporaryData(): Flow<List<TemporaryNetworkData>>

    @Query("SELECT * FROM temporary_network_data WHERE bssid = :bssid")
    suspend fun getTemporaryDataByBssid(bssid: String): TemporaryNetworkData?

    @Query("SELECT * FROM temporary_network_data WHERE bssid = :bssid")
    fun getTemporaryDataByBssidFlow(bssid: String): Flow<TemporaryNetworkData?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTemporaryData(data: TemporaryNetworkData)

    @Delete
    suspend fun deleteTemporaryData(data: TemporaryNetworkData)

    @Query("DELETE FROM temporary_network_data WHERE bssid = :bssid")
    suspend fun deleteTemporaryDataByBssid(bssid: String)

    @Query("DELETE FROM temporary_network_data")
    suspend fun clearAllTemporaryData()

    @Query("DELETE FROM temporary_network_data WHERE lastUpdated < :cutoffTime")
    suspend fun deleteOldTemporaryData(cutoffTime: Long)
}