package com.example.data.repository

import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.GatewayConfigEntity
import android.content.Context
import com.example.security.GatewaySecretStore
import com.example.security.AdminPinHasher
import java.security.MessageDigest
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GatewayConfigRepository(
    private val configDao: GatewayConfigDao,
    private val context: Context
) {
    val configFlow: Flow<GatewayConfigEntity> = configDao.observeConfig().map { it ?: GatewayConfigEntity() }

    suspend fun getCurrentConfig(): GatewayConfigEntity {
        val current = configDao.getConfig() ?: GatewayConfigEntity().also {
            configDao.insertOrUpdate(it)
        }
        val storedToken = current.gatewayToken
        if (storedToken.isBlank() || GatewaySecretStore.decrypt(context, storedToken).isNotBlank()) {
            return current
        }

        // Migrate legacy plaintext tokens of any supported length. Encoded legacy
        // ciphertext candidates (12-byte IV + at least a GCM tag, 60+ decoded bytes)
        // remain untouched if they cannot be decrypted, so an invalidated Keystore
        // record is never silently re-encrypted as though it were a new secret.
        if (storedToken.length < GatewaySecretStore.MIN_TOKEN_LENGTH) return current
        val decodedSize = runCatching {
            Base64.decode(storedToken, Base64.NO_WRAP).size
        }.getOrNull()
        if (decodedSize != null && decodedSize >= 60) return current

        val encrypted = runCatching {
            GatewaySecretStore.encrypt(context, storedToken)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return current
        val migrated = current.copy(gatewayToken = encrypted)
        configDao.insertOrUpdate(migrated)
        return migrated
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
