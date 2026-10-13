package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GatewayConfigEntity
import com.example.data.repository.GatewayConfigRepository
import com.example.security.AdminPinHasher
import com.example.security.GatewaySecretStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GatewayConfigRepositoryAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GatewayConfigRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GatewayConfigRepository(db.gatewayConfigDao(), context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun blankPinDoesNotUnlockAndFirstPinIsSaltedImmediately() = runBlocking {
        assertFalse(repository.verifyPin(""))
        assertTrue(repository.verifyPin("4826"))

        val stored = db.gatewayConfigDao().getConfig()!!.adminPin
        assertNotEquals("4826", stored)
        assertTrue(AdminPinHasher.isHash(stored))
        assertTrue(repository.verifyPin("4826"))
        assertFalse(repository.verifyPin("0000"))
        assertFalse(repository.verifyPin(""))
    }

    @Test
    fun legacyPlaintextPinIsMigratedOnSuccessfulVerification() = runBlocking {
        db.gatewayConfigDao().insertOrUpdate(GatewayConfigEntity(adminPin = "2468"))

        assertTrue(repository.verifyPin("2468"))
        val stored = db.gatewayConfigDao().getConfig()!!.adminPin
        assertTrue(AdminPinHasher.isHash(stored))
        assertNotEquals("2468", stored)
        assertTrue(repository.verifyPin("2468"))
        assertFalse(repository.verifyPin("8642"))
    }
    @Test
    fun savingOtherSettingsWithBlankNewPinPreservesExistingPinHash() = runBlocking {
        assertTrue(repository.verifyPin("4826"))
        val current = repository.getCurrentConfig()
        repository.updateConfig(current.copy(posIpAddress = "192.168.1.20", adminPin = ""))

        val stored = db.gatewayConfigDao().getConfig()!!.adminPin
        assertTrue(AdminPinHasher.isHash(stored))
        assertTrue(repository.verifyPin("4826"))
        assertFalse(repository.verifyPin("1111"))
        assertFalse(repository.verifyPin(""))
    }

    @Test
    fun legacyPlaintextGatewayTokenMigratesToKeystoreEncryptedValue() = runBlocking {
        val legacyToken = "legacy-shared-token-0123456789-ABCDEFGH"
        db.gatewayConfigDao().insertOrUpdate(GatewayConfigEntity(gatewayToken = legacyToken))

        val migrated = repository.getCurrentConfig()
        assertNotEquals(legacyToken, migrated.gatewayToken)
        assertEquals(legacyToken, GatewaySecretStore.decrypt(
            InstrumentationRegistry.getInstrumentation().targetContext,
            migrated.gatewayToken
        ))
        assertEquals(migrated.gatewayToken, db.gatewayConfigDao().getConfig()!!.gatewayToken)
    }

    @Test
    fun legacyPlaintextGatewayTokenLongerThan64CharactersIsMigrated() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val legacyToken = "legacy-token-" + "!".repeat(80)
        assertTrue(legacyToken.length > 64)
        db.gatewayConfigDao().insertOrUpdate(GatewayConfigEntity(gatewayToken = legacyToken))

        val migrated = repository.getCurrentConfig()
        assertNotEquals(legacyToken, migrated.gatewayToken)
        assertEquals(legacyToken, GatewaySecretStore.decrypt(context, migrated.gatewayToken))
        assertEquals(migrated.gatewayToken, db.gatewayConfigDao().getConfig()!!.gatewayToken)
    }

}
