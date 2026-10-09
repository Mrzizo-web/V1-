package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the POS shared token encrypted with an AES-256 key held by Android Keystore.
 * The key is non-exportable; only ciphertext and the GCM IV are stored in app-private prefs.
 */
class SecureTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    @Synchronized
    fun saveToken(token: String) {
        require(token.length >= MIN_TOKEN_LENGTH) {
            "Gateway token must contain at least $MIN_TOKEN_LENGTH characters"
        }
        require(token.none { it.isWhitespace() }) { "Gateway token must not contain whitespace" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val saved = preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not save encrypted gateway token" }
    }

    @Synchronized
    fun getToken(): String? {
        val encryptedValue = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val ivValue = preferences.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivValue, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(encryptedValue, Base64.NO_WRAP)),
                Charsets.UTF_8
            ).takeIf { it.length >= MIN_TOKEN_LENGTH }
        } catch (_: Exception) {
            // Fail closed: do not send an unreadable or corrupted credential.
            null
        }
    }

    @Synchronized
    fun clearToken() {
        preferences.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).commit()
    }

    fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "power_feul_gateway_token_key_v1"
        private const val PREFS_NAME = "secure_gateway_credentials"
        private const val KEY_CIPHERTEXT = "gateway_token_ciphertext"
        private const val KEY_IV = "gateway_token_iv"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val MIN_TOKEN_LENGTH = 32
    }
}
