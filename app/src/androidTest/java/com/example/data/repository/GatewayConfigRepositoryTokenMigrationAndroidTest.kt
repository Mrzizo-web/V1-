package com.example.data.repository

import android.util.Base64
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayConfigEntity
import com.example.security.GatewaySecretStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConfigRepositoryTokenMigrationAndroidTest {
    @Test
    fun migratesLongBase64CompatibleLegacyPlaintextToken() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            // Reproduces the old length heuristic bug: a genuine legacy token can
            // be long, Base64-only text without being an encrypted ciphertext.
            val legacyToken = Base64.encodeToString(
                ByteArray(96) { index -> ((index * 37) % 256).toByte() },
                Base64.NO_WRAP
            )
            assertTrue(legacyToken.length >= GatewaySecretStore.MIN_TOKEN_LENGTH)
            db.gatewayConfigDao().insertOrUpdate(GatewayConfigEntity(gatewayToken = legacyToken))

            val migrated = GatewayConfigRepository(db.gatewayConfigDao(), context).getCurrentConfig()

            assertTrue(migrated.gatewayToken.startsWith(GatewaySecretStore.ENCRYPTED_PREFIX))
            assertEquals(legacyToken, GatewaySecretStore.decrypt(context, migrated.gatewayToken))
            assertEquals(migrated.gatewayToken, db.gatewayConfigDao().getConfig()!!.gatewayToken)
        } finally {
            db.close()
        }
    }

    @Test
    fun rewrapsPreviouslyEncryptedUnversionedToken() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val token = "test-gateway-token-0123456789abcdef012345"
            val oldEnvelope = GatewaySecretStore.encrypt(context, token)
                .removePrefix(GatewaySecretStore.ENCRYPTED_PREFIX)
            db.gatewayConfigDao().insertOrUpdate(GatewayConfigEntity(gatewayToken = oldEnvelope))

            val migrated = GatewayConfigRepository(db.gatewayConfigDao(), context).getCurrentConfig()

            assertTrue(migrated.gatewayToken.startsWith(GatewaySecretStore.ENCRYPTED_PREFIX))
            assertEquals(token, GatewaySecretStore.decrypt(context, migrated.gatewayToken))
        } finally {
            db.close()
        }
    }
}
