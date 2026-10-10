package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gateway_config")
data class GatewayConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val deviceId: String = "GATEWAY_DEV_01",
    val posIpAddress: String = "192.168.1.100",
    val posPort: Int = 8080,
    val connectionMode: String = "WIFI_LAN", // "WIFI_LAN" or "HTTP_DIRECT"
    val isAutoSyncEnabled: Boolean = true,
    val maxRetryCount: Int = 5,
    val retryDelaySeconds: Int = 15,
    val isLoggingEnabled: Boolean = true,
    val isGatewayActive: Boolean = true,
    // Empty by default: first-run setup must define an administrator PIN. Never ship a default PIN.
    val adminPin: String = "",
    val jeebSenderKeyword: String = "JEEB",
    val floosakSenderKeyword: String = "FLOOSAK"
)
