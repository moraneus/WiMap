package com.ner.wimap.data.repository

import android.content.Context
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.domain.repository.ExportRepository
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.viewmodel.ExportManager
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ui.viewmodel.ExportFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val coroutineScope: CoroutineScope
) : ExportRepository {
    
    private val exportManager = ExportManager(coroutineScope)
    
    override suspend fun exportWifiNetworks(
        context: Context,
        networks: List<WifiNetwork>,
        format: ExportFormat,
        action: ExportAction
    ): kotlin.Result<String> {
        return try {
            exportManager.exportWifiNetworks(context, networks, format, action)
            kotlin.Result.success("Export completed successfully")
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override suspend fun exportPinnedNetwork(
        context: Context,
        network: PinnedNetwork,
        format: ExportFormat,
        action: ExportAction
    ): kotlin.Result<String> {
        return try {
            exportManager.exportPinnedNetwork(context, network, format, action)
            kotlin.Result.success("Export completed successfully")
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
    
    override fun getExportStatus(): Flow<String?> = exportManager.exportStatus
    
    override fun getExportError(): Flow<String?> = exportManager.errorMessage
    
    override fun clearStatus() = exportManager.clearStatus()
    
    override fun clearError() = exportManager.clearError()
}