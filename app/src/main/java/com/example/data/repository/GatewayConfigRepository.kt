package com.example.data.repository

import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.GatewayConfigEntity
import android.content.Context
import com.example.security.GatewaySecretStore
import com.example.security.AdminPinHasher
import java.security.MessageDigest
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
        val pinToStore = when {
            updated.adminPin.isBlank() -> current.adminPin
            AdminPinHasher.isHash(updated.adminPin) -> updated.adminPin
            else -> AdminPinHasher.hash(updated.adminPin)
        }
        configDao.insertOrUpdate(updated.copy(gatewayToken = tokenToStore, adminPin = pinToStore))
    }

    suspend fun setGatewayActive(active: Boolean) {
        configDao.setGatewayActive(active)
    }

    suspend fun verifyPin(enteredPin: String): Boolean {
        if (!AdminPinHasher.isValidPin(enteredPin)) return false

        var current = getCurrentConfig()
        if (current.adminPin.isBlank()) {
            // First-time setup: the first valid PIN is immediately stored as a hash.
            val encoded = AdminPinHasher.hash(enteredPin)
            if (configDao.setInitialAdminPin(encoded) == 1) return true
            current = getCurrentConfig()
        }

        val stored = current.adminPin
        if (AdminPinHasher.isHash(stored)) {
            return AdminPinHasher.verify(enteredPin, stored)
        }

        // One-time migration for existing plaintext PINs. Never accept a blank PIN.
        val matchesLegacy = MessageDigest.isEqual(
            enteredPin.toByteArray(Charsets.UTF_8),
            stored.toByteArray(Charsets.UTF_8)
        )
        if (!matchesLegacy) return false
        configDao.updateAdminPin(AdminPinHasher.hash(enteredPin))
        return true
    }
}
