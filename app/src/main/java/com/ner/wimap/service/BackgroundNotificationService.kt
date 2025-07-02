package com.ner.wimap.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ner.wimap.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.util.Log

@AndroidEntryPoint
class BackgroundNotificationService : Service() {
    
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    
    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "background_scan_status_channel"
        const val ACTION_STOP_BACKGROUND_SCANNING = "com.ner.wimap.STOP_BACKGROUND_SCANNING"
        private const val TAG = "BackgroundNotificationService"
        
        fun startService(context: Context) {
            Log.d(TAG, "Starting BackgroundNotificationService")
            val intent = Intent(context, BackgroundNotificationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "BackgroundNotificationService start command sent")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BackgroundNotificationService", e)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundNotificationService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackgroundNotificationService created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BackgroundNotificationService onStartCommand - action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_STOP_BACKGROUND_SCANNING -> {
                Log.d(TAG, "Stop background scanning action received")
                handleStopBackgroundScanning()
                return START_NOT_STICKY
            }
            else -> {
                showEnabledNotRunningNotification()
                return START_STICKY
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "BackgroundNotificationService destroyed")
        super.onDestroy()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Scan Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows background scanning status"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }
    
    private fun showEnabledNotRunningNotification() {
        // Intent to open main activity when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent to stop background scanning
        val stopIntent = Intent(this, BackgroundNotificationService::class.java).apply {
            action = ACTION_STOP_BACKGROUND_SCANNING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Background scan enabled – not running")
            .setContentText("Tap to open app or Stop to disable")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
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
        
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Enabled not running notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show enabled not running notification", e)
        }
    }
    
    private fun handleStopBackgroundScanning() {
        Log.d(TAG, "Handling stop background scanning request")
        
        // Disable background scanning in preferences
        sharedPreferences.edit()
            .putBoolean("background_scanning_enabled", false)
            .apply()
        
        // Stop any active WiFi scanning service
        try {
            val wifiScanIntent = Intent(this, WiFiScanService::class.java)
            stopService(wifiScanIntent)
            Log.d(TAG, "Stopped WiFiScanService")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop WiFiScanService", e)
        }
        
        // Show toast feedback on main thread
        try {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                android.widget.Toast.makeText(
                    applicationContext,
                    "Background scanning disabled",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show toast", e)
        }
        
        // Stop this service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}