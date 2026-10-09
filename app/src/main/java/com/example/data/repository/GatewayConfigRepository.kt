package com.example.data.repository

import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.security.SecureTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GatewayConfigRepository(
    private val configDao: GatewayConfigDao,
    private val secureTokenStore: SecureTokenStore
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

    fun saveGatewayToken(token: String) = secureTokenStore.saveToken(token)

    fun getGatewayToken(): String? = secureTokenStore.getToken()

    fun hasGatewayToken(): Boolean = secureTokenStore.getToken() != null

    fun generateGatewayToken(): String = secureTokenStore.generateToken()

    fun clearGatewayToken() = secureTokenStore.clearToken()

    suspend fun setGatewayActive(active: Boolean) {
        configDao.setGatewayActive(active)
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        val current = getCurrentConfig()
        if (current.adminPin.isBlank()) return true
        return enteredPin.length in 4..6 && enteredPin.all(Char::isDigit) && current.adminPin == enteredPin
    }
}
