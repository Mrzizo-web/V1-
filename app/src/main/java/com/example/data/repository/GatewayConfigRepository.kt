package com.example.data.repository

import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.GatewayConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GatewayConfigRepository(
    private val configDao: GatewayConfigDao
) {
    val configFlow: Flow<GatewayConfigEntity> = configDao.observeConfig().map { it ?: GatewayConfigEntity() }

    suspend fun getCurrentConfig(): GatewayConfigEntity {
        return configDao.getConfig() ?: GatewayConfigEntity().also {
            configDao.insertOrUpdate(it)
        }
    }

    suspend fun updateConfig(updated: GatewayConfigEntity) {
        val current = getCurrentConfig()
        val isFirstRun = current.adminPin.isBlank()
        val newPin = updated.adminPin
        require(!isFirstRun || newPin.matches(Regex("\\d{4,6}"))) {
            "Administrator PIN must be 4-6 numeric digits during first-run setup"
        }
        configDao.insertOrUpdate(updated)
    }

    suspend fun setGatewayActive(active: Boolean) {
        configDao.setGatewayActive(active)
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        val current = getCurrentConfig()
        // First run has no credential yet; the settings UI validates the new PIN.
        if (current.adminPin.isBlank()) return true
        return enteredPin.length in 4..6 && enteredPin.all(Char::isDigit) && current.adminPin == enteredPin
    }
}
