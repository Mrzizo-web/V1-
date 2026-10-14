package com.example

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.network.LocalNetworkHostPolicy
import com.example.security.GatewaySecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Debug-only CI setup helper. It configures this Gateway app with a test token and
 * asks the separately installed POS debug harness to start its actual HTTP server.
 * This class is not packaged in release variants.
 */
class DebugGatewayIntegrationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID).orEmpty()
        val posIp = intent.getStringExtra(EXTRA_POS_IP).orEmpty()
        val posPort = intent.getIntExtra(EXTRA_POS_PORT, DEFAULT_PORT)

        Thread {
            val setupSucceeded = runCatching {
                require(token.length >= GatewaySecretStore.MIN_TOKEN_LENGTH) {
                    "Integration token must contain at least 32 characters"
                }
                require(deviceId.isNotBlank()) { "Integration device ID is required" }
                require(LocalNetworkHostPolicy.isAllowedCleartextHost(posIp)) {
                    "Integration POS host must be local/private"
                }
                require(posPort in 1024..65535) { "Integration POS port is invalid" }

                val app = application as GatewayApplication
                val encryptedToken = GatewaySecretStore.encrypt(applicationContext, token)
                runBlocking(Dispatchers.IO) {
                    app.database.gatewayConfigDao().insertOrUpdate(
                        GatewayConfigEntity(
                            deviceId = deviceId,
                            posIpAddress = posIp,
                            posPort = posPort,
                            connectionMode = "WIFI_LAN",
                            isAutoSyncEnabled = false,
                            isGatewayActive = true,
                            gatewayToken = encryptedToken
                        )
                    )
                }
            }.isSuccess

            Handler(Looper.getMainLooper()).post {
                if (setupSucceeded) {
                    val posIntent = Intent().apply {
                        component = ComponentName(
                            "com.aistudio.powerfeulpos.wzkmqy",
                            "com.example.gateway.DebugGatewayIntegrationActivity"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("debug_gateway_integration_token", token)
                        putExtra("debug_gateway_integration_device_id", deviceId)
                        putExtra("debug_gateway_integration_port", posPort)
                        putExtra("debug_gateway_integration_stop", false)
                    }
                    runCatching { startActivity(posIntent) }
                        .onFailure { setResult(RESULT_CANCELED) }
                        .onSuccess { setResult(RESULT_OK) }
                } else {
                    setResult(RESULT_CANCELED)
                }
                finish()
            }
        }.start()
    }

    companion object {
        const val EXTRA_TOKEN = "debug_gateway_integration_token"
        const val EXTRA_DEVICE_ID = "debug_gateway_integration_device_id"
        const val EXTRA_POS_IP = "debug_gateway_integration_pos_ip"
        const val EXTRA_POS_PORT = "debug_gateway_integration_pos_port"
        const val DEFAULT_PORT = 18080
    }
}
