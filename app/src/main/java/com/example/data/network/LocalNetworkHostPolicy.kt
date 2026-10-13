package com.example.data.network

/**
 * HTTP is used only for the local POS gateway. HMAC protects request authenticity
 * and integrity; this allowlist prevents a misconfigured IP field from sending
 * wallet data over cleartext HTTP to a public internet host.
 */
object LocalNetworkHostPolicy {
    fun isAllowedCleartextHost(host: String): Boolean {
        val normalized = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized == "::1") {
            return true
        }

        if (normalized.contains(":")) {
            // Accept only syntactically plausible IPv6 literals before applying local ranges.
            if (normalized.count { it == ':' } < 2 ||
                normalized.any { !(it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' || it == '%') }
            ) return false
            return normalized == "::1" ||
                normalized.startsWith("fc") ||
                normalized.startsWith("fd") ||
                normalized.startsWith("fe80:")
        }

        val octets = normalized.split(".")
        if (octets.size != 4 || octets.any { it.isEmpty() || it.any { char -> !char.isDigit() } }) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        val a = numbers[0]
        val b = numbers[1]
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }
}
