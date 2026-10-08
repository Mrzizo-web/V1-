package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.network.HttpPosGateway
import com.example.data.repository.GatewayConfigRepository
import com.example.data.repository.GatewayRepository
import com.example.domain.parser.ParserRegistry
import com.example.domain.parser.WalletDetector
import com.example.service.GatewayForegroundService
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

        val posGateway = HttpPosGateway(database.gatewayConfigDao())

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

        // Boot logging and auto service start
        CoroutineScope(Dispatchers.IO).launch {
            val config = configRepository.getCurrentConfig()
            walletDetector.updateKeywords(
                config.jeebSenderKeyword,
                config.floosakSenderKeyword,
                config.jawaliSenderKeyword
            )

            repository.logEvent(
                tag = "APP_START",
                message = "POWER FEUL SMS GATEWAY initialized (Device ID: ${config.deviceId})",
                level = "INFO"
            )

            if (config.isGatewayActive) {
                try {
                    GatewayForegroundService.startService(this@GatewayApplication)
                } catch (_: Exception) {
                    // Handled if background start restrictions apply
                }
            }
        }
    }
}
