package com.ner.wimap.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.ner.wimap.data.database.PinnedNetwork
import com.ner.wimap.model.WifiNetwork
import com.ner.wimap.ui.viewmodel.ExportAction
import com.ner.wimap.ui.viewmodel.ExportFormat
import com.ner.wimap.ui.viewmodel.ExportManager
import kotlinx.coroutines.CoroutineScope

/**
 * Wrapper around ExportManager that shows interstitial ads before export/share operations
 */
class AdExportManager(
    private val exportManager: ExportManager,
    private val viewModelScope: CoroutineScope
) {
    companion object {
        private const val TAG = "AdExportManager"
    }
    
    /**
     * Export WiFi networks with interstitial ad shown before the operation
     */
    fun exportWifiNetworksWithAd(
        activity: Activity,
        networks: List<WifiNetwork>,
        format: ExportFormat,
        action: ExportAction = ExportAction.SAVE_AND_SHARE,
        onComplete: (String) -> Unit = {}
    ) {
        Log.d(TAG, "Attempting to show interstitial ad before export")
        
        // Preload interstitial ad for next time
        AdMobManager.loadInterstitialAd(activity)
        
        val proceedWithExport = {
            Log.d(TAG, "Proceeding with export after ad interaction")
            exportManager.exportWifiNetworks(
                context = activity,
                networks = networks,
                format = format,
                action = action,
                onComplete = onComplete
            )
        }
        
        // Try to show interstitial ad, fallback to direct export if not available
        AdMobManager.showInterstitialAd(
            activity = activity,
            onAdDismissed = {
                Log.d(TAG, "Interstitial ad dismissed, proceeding with export")
                proceedWithExport()
            },
            onAdNotAvailable = {
                Log.d(TAG, "Interstitial ad not available, proceeding with export directly")
                proceedWithExport()
            }
        )
    }
    
    /**
     * Export single pinned network with interstitial ad shown before the operation
     */
    fun exportPinnedNetworkWithAd(
        activity: Activity,
        network: PinnedNetwork,
        format: ExportFormat,
        action: ExportAction = ExportAction.SAVE_AND_SHARE,
        onComplete: (String) -> Unit = {}
    ) {
        Log.d(TAG, "Attempting to show interstitial ad before pinned network export")
        
        // Preload interstitial ad for next time
        AdMobManager.loadInterstitialAd(activity)
        
        val proceedWithExport = {
            Log.d(TAG, "Proceeding with pinned network export after ad interaction")
            exportManager.exportPinnedNetwork(
                context = activity,
                network = network,
                format = format,
                action = action,
                onComplete = onComplete
            )
        }
        
        // Try to show interstitial ad, fallback to direct export if not available
        AdMobManager.showInterstitialAd(
            activity = activity,
            onAdDismissed = {
                Log.d(TAG, "Interstitial ad dismissed, proceeding with pinned network export")
                proceedWithExport()
            },
            onAdNotAvailable = {
                Log.d(TAG, "Interstitial ad not available, proceeding with pinned network export directly")
                proceedWithExport()
            }
        )
    }
    
    // Delegate other properties to the original ExportManager
    val exportStatus = exportManager.exportStatus
    val errorMessage = exportManager.errorMessage
}