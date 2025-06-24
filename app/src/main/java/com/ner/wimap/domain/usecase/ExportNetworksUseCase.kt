package com.ner.wimap.domain.usecase

import android.content.Context
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.domain.repository.ExportRepository
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ui.viewmodel.ExportFormat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExportNetworksUseCase @Inject constructor(
    private val exportRepository: ExportRepository
) {
    suspend fun exportWifiNetworks(
        context: Context,
        networks: List<WifiNetwork>,
        format: ExportFormat,
        action: ExportAction
    ): kotlin.Result<String> = exportRepository.exportWifiNetworks(context, networks, format, action)
    
    suspend fun exportPinnedNetwork(
        context: Context,
        network: PinnedNetwork,
        format: ExportFormat,
        action: ExportAction
    ): kotlin.Result<String> = exportRepository.exportPinnedNetwork(context, network, format, action)
    
    fun getExportStatus(): Flow<String?> = exportRepository.getExportStatus()
    
    fun getExportError(): Flow<String?> = exportRepository.getExportError()
    
    fun clearStatus() = exportRepository.clearStatus()
    
    fun clearError() = exportRepository.clearError()
}