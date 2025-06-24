package com.ner.wimap.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedNetworkDao {

    @Query("SELECT * FROM pinned_networks ORDER BY pinnedAt DESC")
    fun getAllPinnedNetworks(): Flow<List<PinnedNetwork>>

    @Query("SELECT * FROM pinned_networks WHERE bssid = :bssid LIMIT 1")
    suspend fun getPinnedNetworkByBssid(bssid: String): PinnedNetwork?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedNetwork(network: PinnedNetwork): Long

    @Delete
    suspend fun deletePinnedNetwork(network: PinnedNetwork): Int

    @Query("DELETE FROM pinned_networks WHERE bssid = :bssid")
    suspend fun deletePinnedNetworkByBssid(bssid: String): Int

    @Update
    suspend fun updatePinnedNetwork(network: PinnedNetwork): Int

    @Query("SELECT COUNT(*) FROM pinned_networks WHERE bssid = :bssid")
    suspend fun isPinned(bssid: String): Int

    @Query("DELETE FROM pinned_networks")
    suspend fun deleteAllPinnedNetworks(): Int
}