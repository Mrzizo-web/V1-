package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.GatewayApplication
import com.example.worker.GatewaySyncScheduler
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

                    // WorkManager is reboot-persistent and allowed from BOOT_COMPLETED;
                    // Android 15+ restricts starting dataSync foreground services here.
                    GatewaySyncScheduler.schedulePeriodic(context)
                    val config = app.configRepository.getCurrentConfig()
                    if (config.isGatewayActive && config.isAutoSyncEnabled) {
                        GatewaySyncScheduler.enqueueImmediate(context)
                    }
                } catch (e: Exception) {
                    app.repository.logEvent("BOOT_RECOVERY_ERROR", "Background work could not be scheduled after reboot", level = "ERROR")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
