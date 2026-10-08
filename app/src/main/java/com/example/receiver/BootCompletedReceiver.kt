package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.GatewayApplication
import com.example.service.GatewayForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val app = context.applicationContext as? GatewayApplication ?: return

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.repository.logEvent(
                        tag = "DEVICE_BOOT",
                        message = "Device rebooted. Gateway recovering state and queue...",
                        level = "WARN"
                    )

                    // Start background foreground service if configured
                    val config = app.configRepository.getCurrentConfig()
                    if (config.isGatewayActive) {
                        GatewayForegroundService.startService(context)
                        // Trigger immediate sync of any leftover queued items
                        app.repository.syncPendingPayments()
                    }
                } catch (e: Exception) {
                    app.repository.logEvent("BOOT_RECOVERY_ERROR", "Error during boot recovery: ${e.message}", level = "ERROR")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
