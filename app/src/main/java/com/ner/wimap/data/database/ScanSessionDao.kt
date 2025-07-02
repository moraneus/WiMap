package com.ner.wimap.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    
    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC")
    fun getAllScanSessions(): Flow<List<ScanSession>>
    
    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId")
    suspend fun getScanSessionById(sessionId: String): ScanSession?
    
    @Insert
    suspend fun insertScanSession(scanSession: ScanSession)
    
    @Update
    suspend fun updateScanSession(scanSession: ScanSession)
    
    @Delete
    suspend fun deleteScanSession(scanSession: ScanSession)
    
    @Query("DELETE FROM scan_sessions WHERE id = :sessionId")
    suspend fun deleteScanSessionById(sessionId: String)
    
    @Query("SELECT COUNT(*) FROM scan_sessions")
    suspend fun getScanSessionCount(): Int
    
    @Query("DELETE FROM scan_sessions WHERE id IN (SELECT id FROM scan_sessions ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestScanSessions(count: Int)
    
    @Query("SELECT * FROM scan_sessions ORDER BY timestamp ASC LIMIT :count")
    suspend fun getOldestScanSessions(count: Int): List<ScanSession>
    
    @Query("UPDATE scan_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, newTitle: String)
}