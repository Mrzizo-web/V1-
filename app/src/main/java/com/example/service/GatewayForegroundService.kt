package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.GatewayApplication
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GatewayForegroundService : Service {

    constructor() : super()

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var syncJob: Job? = null

    companion object {
        const val CHANNEL_ID = "power_fuel_gateway_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Gateway يعمل بنشاط"))
        startPeriodicSyncLoop()
    }

    private fun startPeriodicSyncLoop() {
        syncJob?.cancel()
        val app = applicationContext as? GatewayApplication ?: return

        syncJob = serviceScope.launch {
            while (isActive) {
                try {
                    val config = app.configRepository.getCurrentConfig()
                    if (config.isGatewayActive && config.isAutoSyncEnabled) {
                        val syncedCount = app.repository.syncPendingPayments()
                        if (syncedCount > 0) {
                            updateNotification("تمت مزامنة $syncedCount حوالة بنجاح")
                        }
                    }
                } catch (_: Exception) {
                    // Ignored in loop
                }
                delay(15_000L) // Check every 15 seconds
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "POWER FEUL SMS Gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة المراقبة والمزامنة الخلفية لبوابة الرسائل"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("POWER FEUL SMS GATEWAY")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
