package com.example.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRequestSignerTest {
    private val key = "0123456789abcdef0123456789abcdef"

    @Test
    fun signingIsDeterministicForTheSameCanonicalRequest() {
        val body = """{"sender":"JEEB"}"""
        val signature = GatewayRequestSigner.sign(
            key, "POST", "/api/sms/ingest", "1791644400000", "evt-1", "PHONE-1", body
        )
        assertEquals(
            signature,
            GatewayRequestSigner.sign(
                key, "POST", "/api/sms/ingest", "1791644400000", "evt-1", "PHONE-1", body
            )
        )
        assertTrue(signature.matches(Regex("[0-9a-f]{64}")))
        assertTrue(
            GatewayRequestSigner.verify(
                key, "POST", "/api/sms/ingest", "1791644400000", "evt-1", "PHONE-1", body, signature
            )
        )
    }

    @Test
    fun signatureChangesIfAnyCanonicalFieldChanges() {
        val body = """{"sender":"JEEB"}"""
        val signature = GatewayRequestSigner.sign(
            key, "POST", "/api/sms/ingest", "1791644400000", "evt-1", "PHONE-1", body
        )
        assertNotEquals(signature, GatewayRequestSigner.sign(key, "POST", "/api/sms/ingest", "1791644400001", "evt-1", "PHONE-1", body))
        assertNotEquals(signature, GatewayRequestSigner.sign(key, "POST", "/api/sms/ingest", "1791644400000", "evt-2", "PHONE-1", body))
        assertNotEquals(signature, GatewayRequestSigner.sign(key, "POST", "/api/sms/ingest", "1791644400000", "evt-1", "PHONE-2", body))
        assertNotEquals(signature, GatewayRequestSigner.sign(key, "POST", "/api/sms/ingest", "1791644400000", "evt-1", "PHONE-1", """{"sender":"FLOOSAK"}"""))
        assertNotEquals(signature, GatewayRequestSigner.sign(key, "POST", "/api/status", "1791644400000", "evt-1", "PHONE-1", body))
        assertNotEquals(signature, GatewayRequestSigner.sign(key, "GET", "/api/sms/ingest", "1791644400000", "evt-1", "PHONE-1", body))
    }

    @Test(expected = IllegalArgumentException::class)
    fun weakTokenIsRejected() {
        GatewayRequestSigner.sign("short", "POST", "/api/sms/ingest", "1", "evt", "PHONE-1", "{}")
    }
}
