package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 PIN storage. Records include their iteration count and a
 * random salt so the work factor can be raised in a later version.
 */
object AdminPinHasher {
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun isHash(record: String): Boolean = record.startsWith(PREFIX + ":")

    fun isValidPin(pin: String): Boolean = pin.matches(Regex("[0-9]{4,6}"))

    fun hash(pin: String): String {
        require(isValidPin(pin)) { "PIN must contain 4 to 6 digits" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(pin, salt, ITERATIONS)
        return PREFIX + ":" + ITERATIONS + ":" +
            Base64.encodeToString(salt, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(derived, Base64.NO_WRAP)
    }

    fun verify(pin: String, record: String): Boolean {
        if (!isValidPin(pin) || !isHash(record)) return false
        val parts = record.split(":")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in 50_000..1_000_000) return false
        return try {
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            if (salt.size != SALT_BYTES || expected.size != KEY_BITS / 8) return false
            val actual = derive(pin, salt, iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (_: Exception) {
            false
        }
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }
}
