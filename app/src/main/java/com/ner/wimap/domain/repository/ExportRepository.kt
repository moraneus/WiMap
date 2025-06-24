package com.ner.wimap.domain.repository

import android.content.Context
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ui.viewmodel.ExportFormat
import kotlinx.coroutines.flow.Flow

interface ExportRepository {
    suspend fun exportWifiNetworks(
        context: Context,
        networks: List<WifiNetwork>,
        format: ExportFormat,
        action: ExportAction
    ): kotlin.Result<String>
    
    suspend fun exportPinnedNetwork(
        context: Context,
        network: PinnedNetwork,
        format: ExportFormat,
        action: ExportAction
    ): kotlin.Result<String>
    
    fun getExportStatus(): Flow<String?>
    fun getExportError(): Flow<String?>
    fun clearStatus()
    fun clearError()
}