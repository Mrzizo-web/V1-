package com.example.data.repository

import android.util.Base64
import com.example.data.local.dao.GatewayConfigDao
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.security.SecureTokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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
        val requestedPin = updated.adminPin
        val storedPin = when {
            requestedPin.isBlank() -> current.adminPin
            requestedPin == current.adminPin && isHashedPin(requestedPin) -> requestedPin
            requestedPin.matches(Regex("\\d{4,6}")) -> hashPin(requestedPin)
            else -> throw IllegalArgumentException("Administrator PIN must be 4-6 numeric digits")
        }
        require(storedPin.isNotBlank()) {
            "Set an administrator PIN before saving settings"
        }
        configDao.insertOrUpdate(updated.copy(adminPin = storedPin))
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
        if (!enteredPin.matches(Regex("\\d{4,6}"))) return false
        val current = getCurrentConfig()
        val stored = current.adminPin
        if (stored.isBlank()) return false
        if (isHashedPin(stored)) return verifyHashedPin(enteredPin, stored)

        // One-time migration for installations that still contain a legacy plaintext PIN.
        if (MessageDigest.isEqual(enteredPin.toByteArray(Charsets.UTF_8), stored.toByteArray(Charsets.UTF_8))) {
            configDao.insertOrUpdate(current.copy(adminPin = hashPin(enteredPin)))
            return true
        }
        return false
    }

    private fun isHashedPin(value: String): Boolean = value.startsWith(PIN_PREFIX + SEPARATOR)

    private fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derivePinHash(pin, salt, ITERATIONS)
        return "$PIN_PREFIX$ITERATIONS$SEPARATOR" +
            Base64.encodeToString(salt, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun verifyHashedPin(pin: String, encoded: String): Boolean {
        return try {
            val parts = encoded.split(SEPARATOR)
            if (parts.size != 4 || parts[0] != PIN_PREFIX) return false
            val iterations = parts[1].toInt()
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return false
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            val actual = derivePinHash(pin, salt, iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun derivePinHash(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val PIN_PREFIX = "pbkdf2-sha256"
        private const val SEPARATOR = "\\$"
        private const val ITERATIONS = 120_000
        private const val MIN_ITERATIONS = 100_000
        private const val MAX_ITERATIONS = 500_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
    }
}
