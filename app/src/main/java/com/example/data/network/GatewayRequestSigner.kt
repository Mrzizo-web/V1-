package com.example.data.network

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs local gateway requests without transmitting the shared secret.
 * Canonical format must remain identical to POWER FEUL POS GatewayRequestSigner.
 */
object GatewayRequestSigner {
    const val TIMESTAMP_HEADER = "X-Gateway-Timestamp"
    const val SIGNATURE_HEADER = "X-Gateway-Signature"
    const val EVENT_ID_HEADER = "X-Gateway-Event-Id"
    const val DEVICE_ID_HEADER = "X-Gateway-Device-Id"

    fun sign(token: String, timestamp: String, eventId: String, deviceId: String, body: String): String {
        val canonical = "$timestamp\n$eventId\n$deviceId\n$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun verify(
        token: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String,
        signature: String
    ): Boolean {
        if (token.length < 32 || timestamp.isBlank() || eventId.isBlank() || deviceId.isBlank()) return false
        if (!signature.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val expected = sign(token, timestamp, eventId, deviceId, body)
        return MessageDigest.isEqual(
            expected.lowercase().toByteArray(Charsets.US_ASCII),
            signature.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }
}
