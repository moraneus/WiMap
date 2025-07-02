package com.ner.wimap.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ner.wimap.R
import com.ner.wimap.MainActivity
import com.ner.wimap.domain.usecase.ScanWifiNetworksUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import android.util.Log
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class WiFiScanService : Service() {
    
    @Inject
    lateinit var scanWifiNetworksUseCase: ScanWifiNetworksUseCase
    
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null
    private var networkCountJob: Job? = null
    private var notificationUpdateJob: Job? = null
    
    private var networksFound: Int = 0
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "wifi_scan_channel"
        const val ACTION_STOP_SCAN = "com.ner.wimap.STOP_SCAN"
        private const val TAG = "WiFiScanService"
        
        fun startService(context: Context) {
            Log.d(TAG, "Starting WiFiScanService - API level: ${Build.VERSION.SDK_INT}")
            val intent = Intent(context, WiFiScanService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d(TAG, "Starting foreground service for API ${Build.VERSION.SDK_INT}")
                    context.startForegroundService(intent)
                } else {
                    Log.d(TAG, "Starting regular service for API ${Build.VERSION.SDK_INT}")
                    context.startService(intent)
                }
                Log.d(TAG, "Service start command sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WiFiScanService", e)
                throw e
            }
        }
        
        
        fun stopService(context: Context) {
            val intent = Intent(context, WiFiScanService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WiFiScanService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WiFiScanService onStartCommand - intent: ${intent?.action}, flags: $flags, startId: $startId")
        
        when (intent?.action) {
            ACTION_STOP_SCAN -> {
                Log.d(TAG, "Stop scan action received from user")
                stopScanningWithReason("Stopped by user")
                return START_NOT_STICKY
            }
            else -> {
                Log.d(TAG, "Starting foreground scan")
                startForegroundScanning()
                return START_STICKY // Restart if killed by system
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "WiFiScanService destroyed")
        stopScanningWithReason("Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel for API ${Build.VERSION.SDK_INT}")
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Scanning",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background WiFi network scanning"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        } else {
            Log.d(TAG, "Skipping notification channel creation for API ${Build.VERSION.SDK_INT}")
        }
    }
    
    private fun startForegroundScanning() {
        Log.d(TAG, "Creating notification and starting foreground service")
        
        // Stop the background notification service if it's running
        try {
            val intent = Intent(this, BackgroundNotificationService::class.java)
            stopService(intent)
            Log.d(TAG, "Stopped BackgroundNotificationService")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop BackgroundNotificationService", e)
        }
        
        // Initialize scanning state
        networksFound = 0
        
        try {
            val notification = createRunningNotification(0)
            Log.d(TAG, "Notification created successfully")
            Log.d(TAG, "Starting foreground with notification ID: $NOTIFICATION_ID")
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            throw e
        }
        
        // Start the actual scanning
        scanJob = serviceScope.launch {
            try {
                Log.d(TAG, "Starting WiFi scan in background")
                val result = scanWifiNetworksUseCase.startScan()
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    Log.e(TAG, "Error starting background scan: ${exception?.message}")
                    stopScanningWithReason("Failed to start scan: ${exception?.message}")
                    return@launch
                }
                
                Log.d(TAG, "WiFi scan started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during background scanning", e)
                stopScanningWithReason("Error during scanning: ${e.message}")
            }
        }
        
        // Start network count monitoring
        startNetworkCountMonitoring()
        
        // Start periodic notification updates
        startPeriodicNotificationUpdates()
    }
    
    
    private fun startNetworkCountMonitoring() {
        networkCountJob = serviceScope.launch {
            try {
                scanWifiNetworksUseCase.getWifiNetworks().collectLatest { networks ->
                    val newCount = networks.size
                    if (newCount != networksFound) {
                        networksFound = newCount
                        Log.d(TAG, "Networks found updated: $networksFound")
                        
                        // Update notification immediately when count changes
                        updateRunningNotification(networksFound)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring network count", e)
            }
        }
    }
    
    private fun startPeriodicNotificationUpdates() {
        notificationUpdateJob = serviceScope.launch {
            while (isActive) {
                delay(20000) // Update every 20 seconds
                try {
                    updateRunningNotification(networksFound)
                    Log.d(TAG, "Periodic notification update: $networksFound networks")
                } catch (e: Exception) {
                    Log.w(TAG, "Error during periodic notification update", e)
                }
            }
        }
    }
    
    
    private fun createRunningNotification(networksFound: Int): Notification {
        // Intent to open main activity when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent to stop scanning
        val stopIntent = Intent(this, WiFiScanService::class.java).apply {
            action = ACTION_STOP_SCAN
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Background scan running – $networksFound networks found")
            .setContentText("Scanning continuously - tap Stop to end")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    private fun updateRunningNotification(networksFound: Int) {
        try {
            val notification = createRunningNotification(networksFound)
            val notificationManager = NotificationManagerCompat.from(this)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot update notification - permission denied", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating notification", e)
        }
    }
    
    private fun stopScanningWithReason(reason: String) {
        Log.d(TAG, "Stopping WiFi scan service: $reason")
        
        // Cancel all jobs
        scanJob?.cancel()
        networkCountJob?.cancel()
        notificationUpdateJob?.cancel()
        scanJob = null
        networkCountJob = null
        notificationUpdateJob = null
        
        // Stop the scanning use case in a coroutine
        serviceScope.launch {
            try {
                scanWifiNetworksUseCase.stopScan()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan use case", e)
            }
        }
        
        // Check if background scanning is still enabled in preferences
        val isBackgroundScanningEnabled = sharedPreferences.getBoolean("background_scanning_enabled", false)
        
        if (isBackgroundScanningEnabled) {
            // Start the background notification service to show "enabled not running" state
            Log.d(TAG, "Starting BackgroundNotificationService for enabled not running state")
            try {
                val intent = Intent(this, BackgroundNotificationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start BackgroundNotificationService", e)
            }
        }
        
        // Show appropriate notification/feedback based on stop reason
        when {
            reason.contains("user", ignoreCase = true) -> {
                showUserStopToast()
            }
            else -> {
                // For any other reason (errors, etc.), just show simple toast
                showUserStopToast()
            }
        }
        
        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    
    private fun showUserStopToast() {
        try {
            // Use coroutine with Main dispatcher to show toast on main thread
            serviceScope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@WiFiScanService,
                    "Background scanning stopped.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error showing user stop toast", e)
        }
    }
}