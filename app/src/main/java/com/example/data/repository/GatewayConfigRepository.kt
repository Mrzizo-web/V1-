package com.example.data.repository

import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.GatewayConfigEntity
import android.content.Context
import com.example.security.GatewaySecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GatewayConfigRepository(
    private val configDao: GatewayConfigDao,
    private val context: Context
) {
    val configFlow: Flow<GatewayConfigEntity> = configDao.observeConfig().map { it ?: GatewayConfigEntity() }

    suspend fun getCurrentConfig(): GatewayConfigEntity {
        return configDao.getConfig() ?: GatewayConfigEntity().also {
            configDao.insertOrUpdate(it)
        }
    }

    suspend fun updateConfig(updated: GatewayConfigEntity) {
        val current = getCurrentConfig()
        val tokenToStore = if (updated.gatewayToken.isBlank()) current.gatewayToken else GatewaySecretStore.encrypt(context, updated.gatewayToken)
        configDao.insertOrUpdate(updated.copy(gatewayToken = tokenToStore))
    }

    suspend fun setGatewayActive(active: Boolean) {
        configDao.setGatewayActive(active)
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        val current = getCurrentConfig()
        if (current.adminPin.isBlank()) return enteredPin.isBlank()
        return current.adminPin == enteredPin
    }
}
