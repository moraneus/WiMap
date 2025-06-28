package com.ner.wimap.service

import android.app.*
import android.content.Context
import android.content.Intent
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

@AndroidEntryPoint
class WiFiScanService : Service() {
    
    @Inject
    lateinit var scanWifiNetworksUseCase: ScanWifiNetworksUseCase
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scanJob: Job? = null
    
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
        android.widget.Toast.makeText(this, "WiFiScanService CREATED", android.widget.Toast.LENGTH_SHORT).show()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WiFiScanService onStartCommand - intent: ${intent?.action}, flags: $flags, startId: $startId")
        
        when (intent?.action) {
            ACTION_STOP_SCAN -> {
                Log.d(TAG, "Stop scan action received")
                stopScanning()
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
        stopScanning()
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
        try {
            val notification = createScanningNotification()
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
                    stopScanning()
                    return@launch
                }
                
                // Monitor scanning state
                scanWifiNetworksUseCase.isScanning().collectLatest { isScanning ->
                    Log.d(TAG, "Scanning state changed: $isScanning")
                    if (isScanning) {
                        updateNotification("Scanning for WiFi networks...")
                    } else {
                        updateNotification("WiFi scan completed")
                        // Auto-stop service after scan completes
                        delay(2000) // Show completion message briefly
                        stopScanning()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during background scanning", e)
                stopScanning()
            }
        }
    }
    
    private fun createScanningNotification(): Notification {
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
            .setContentTitle("WiMap - WiFi Scanning")
            .setContentText("Scanning for WiFi networks in background...")
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Use system sync icon for background scanning
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_delete, // Use system stop icon
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
    
    private fun updateNotification(text: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WiMap - WiFi Scanning")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            val notificationManager = NotificationManagerCompat.from(this)
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot update notification - permission denied", e)
        }
    }
    
    private fun stopScanning() {
        Log.d(TAG, "Stopping WiFi scan service")
        
        scanJob?.cancel()
        scanJob = null
        
        // Stop the scanning use case in a coroutine
        serviceScope.launch {
            try {
                scanWifiNetworksUseCase.stopScan()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan use case", e)
            }
        }
        
        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}