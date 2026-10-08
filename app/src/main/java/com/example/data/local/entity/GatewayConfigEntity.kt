package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gateway_config")
data class GatewayConfigEntity(
    @PrimaryKey val id: Int = 1,
    val deviceId: String = "GATEWAY_DEV_01",
    val posIpAddress: String = "",
    val posPort: Int = 8080,
    val connectionMode: String = "WIFI_LAN",
    val isAutoSyncEnabled: Boolean = true,
    val maxRetryCount: Int = 5,
    val retryDelaySeconds: Int = 15,
    val isLoggingEnabled: Boolean = true,
    val isGatewayActive: Boolean = true,
    val adminPin: String = "",
    val jeebSenderKeyword: String = "JEEB",
    val floosakSenderKeyword: String = "FLOOSAK",
    val jawaliSenderKeyword: String = "JAWALI",
    val gatewayToken: String = "",
    // Kept only for database backward compatibility; it is never detected or parsed.
    val hawalySenderKeyword: String = "DISABLED"
)
