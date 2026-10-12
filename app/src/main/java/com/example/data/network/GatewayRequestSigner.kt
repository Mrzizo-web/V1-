package com.example.data.network

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Must remain byte-for-byte compatible with POWER FEUL POS GatewayRequestSigner.
 */
object GatewayRequestSigner {
    const val TIMESTAMP_HEADER = "X-Gateway-Timestamp"
    const val SIGNATURE_HEADER = "X-Gateway-Signature"
    const val EVENT_ID_HEADER = "X-Gateway-Event-Id"
    const val DEVICE_ID_HEADER = "X-Gateway-Device-Id"

    fun sign(token: String, timestamp: String, eventId: String, deviceId: String, body: String): String {
        require(token.length >= 32) { "Gateway HMAC key must contain at least 32 characters" }
        val canonical = "$timestamp\n$eventId\n$deviceId\n$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(token.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
