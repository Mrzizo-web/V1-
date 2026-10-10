package com.example

import com.example.data.network.GatewayRequestSigner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRequestSignerTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun signsRequestWithoutSendingSharedToken() {
        val timestamp = "1791644400000"
        val eventId = "payment:abc-123"
        val deviceId = "GATEWAY_DEV_01"
        val body = """{"sender":"JEEB","rawMessage":"اضيف 5000 ر.ي"}"""
        val signature = GatewayRequestSigner.sign(token, timestamp, eventId, deviceId, body)

        assertTrue(
            GatewayRequestSigner.verify(
                token, timestamp, eventId, deviceId, body, signature
            )
        )
        assertFalse(
            GatewayRequestSigner.verify(
                token, timestamp, eventId, deviceId, body + " ", signature
            )
        )
    }
}
