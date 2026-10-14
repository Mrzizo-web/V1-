package com.example

import android.app.Application
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.network.HttpPosGateway
import com.example.data.repository.GatewayConfigRepository
import com.example.data.repository.GatewayRepository
import com.example.domain.parser.ParserRegistry
import com.example.domain.parser.WalletDetector
import com.example.worker.GatewaySyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GatewayApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var configRepository: GatewayConfigRepository
        private set

    lateinit var repository: GatewayRepository
        private set

    lateinit var walletDetector: WalletDetector
        private set

    lateinit var parserRegistry: ParserRegistry
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        configRepository = GatewayConfigRepository(database.gatewayConfigDao(), this)
        walletDetector = WalletDetector()
        parserRegistry = ParserRegistry()

        val posGateway = HttpPosGateway(database.gatewayConfigDao(), database.smsMessageDao(), this)

        repository = GatewayRepository(
            smsMessageDao = database.smsMessageDao(),
            paymentDao = database.paymentDao(),
            paymentAttemptDao = database.paymentAttemptDao(),
            gatewayConfigDao = database.gatewayConfigDao(),
            eventLogDao = database.eventLogDao(),
            posGateway = posGateway,
            walletDetector = walletDetector,
            parserRegistry = parserRegistry
        )

        // Android initializes WorkManager through its startup provider on-device.
        // Unit-test host processes may not run that provider, so scheduling failure
        // must not prevent parsers, Room, or the login screen from starting.
        // The periodic job is registered again on boot and on next app launch.
        runCatching { GatewaySyncScheduler.schedulePeriodic(this) }.onFailure { error ->
            Log.w("GatewayApplication", "Could not register periodic background sync", error)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.recoverPendingSms()
            } catch (_: Exception) {
                repository.logEvent(
                    tag = "SMS_RECOVERY_STARTUP_ERROR",
                    message = "Could not recover pending SMS after app startup",
                    level = "ERROR"
                )
            }
            val config = configRepository.getCurrentConfig()
            walletDetector.updateKeywords(
                config.jeebSenderKeyword,
                config.floosakSenderKeyword,
                config.jawaliSenderKeyword
            )

            repository.logEvent(
                tag = "APP_START",
                message = "POWER FEUL SMS GATEWAY initialized (Device ID configured)",
                level = "INFO"
            )

            if (config.isGatewayActive && config.isAutoSyncEnabled) {
                runCatching { GatewaySyncScheduler.enqueueImmediate(this@GatewayApplication) }
            }
        }
    }
}
