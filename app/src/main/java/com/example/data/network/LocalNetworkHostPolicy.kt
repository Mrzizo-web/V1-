package com.example.data.network

/**
 * HTTP is used only for the local POS gateway. HMAC protects request authenticity
 * and integrity; this allowlist prevents a misconfigured IP field from sending
 * wallet data over cleartext HTTP to a public internet host.
 */
object LocalNetworkHostPolicy {
    fun isAllowedCleartextHost(host: String): Boolean {
        val normalized = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") ||
            normalized == "::1" || normalized.startsWith("127.")
        ) return true

        if (normalized.contains(":")) {
            // IPv6 unique-local, loopback, and link-local ranges.
            return normalized == "::1" ||
                normalized.startsWith("fc") ||
                normalized.startsWith("fd") ||
                normalized.startsWith("fe80:")
        }

        val octets = normalized.split(".").map { it.toIntOrNull() ?: return false }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val a = octets[0]
        val b = octets[1]
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }
}
