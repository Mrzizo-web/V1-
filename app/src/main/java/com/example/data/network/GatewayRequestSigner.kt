package com.example.data.network

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Must remain byte-for-byte compatible with POWER FEUL POS GatewayRequestSigner.
 * HMAC includes method and path so a signature cannot be replayed on another endpoint.
 */
object GatewayRequestSigner {
    const val TIMESTAMP_HEADER = "X-Gateway-Timestamp"
    const val SIGNATURE_HEADER = "X-Gateway-Signature"
    const val EVENT_ID_HEADER = "X-Gateway-Event-Id"
    const val DEVICE_ID_HEADER = "X-Gateway-Device-Id"

    fun sign(
        token: String,
        method: String,
        path: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String
    ): String {
        require(token.length >= 32) { "Gateway HMAC key must contain at least 32 characters" }
        require(method.isNotBlank() && path.startsWith("/")) { "HTTP method and absolute path are required" }
        val canonical = listOf(
            method.uppercase(),
            path,
            timestamp,
            eventId,
            deviceId,
            body
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun verify(
        token: String,
        method: String,
        path: String,
        timestamp: String,
        eventId: String,
        deviceId: String,
        body: String,
        signature: String
    ): Boolean {
        if (token.length < 32 || method.isBlank() || !path.startsWith("/")) return false
        if (!signature.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val expected = sign(token, method, path, timestamp, eventId, deviceId, body)
        return MessageDigest.isEqual(
            expected.lowercase().toByteArray(Charsets.US_ASCII),
            signature.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }
}
