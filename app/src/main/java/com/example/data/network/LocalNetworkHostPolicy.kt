package com.example.data.network

import java.net.Inet6Address
import java.net.InetAddress

/**
 * HTTP is used only for private LAN/VPN POS endpoints. RFC1918 LAN hosts, loopback,
 * link-local, ULA IPv6, and the CGNAT range used by Tailscale-style overlay VPNs are
 * permitted. HMAC still authenticates each request; public Internet hosts are rejected.
 */
object LocalNetworkHostPolicy {
    fun isAllowedCleartextHost(host: String): Boolean {
        val normalized = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost")) return true

        if (normalized.contains(":")) {
            // Parse the complete IPv6 literal before allowing its local/private range.
            if (normalized.any { !(it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' || it == '%') }) return false
            val address = runCatching { InetAddress.getByName(normalized) }.getOrNull() as? Inet6Address
                ?: return false
            val firstByte = address.address[0].toInt() and 0xff
            return address.isLoopbackAddress || address.isLinkLocalAddress || (firstByte and 0xfe) == 0xfc
        }

        val octets = normalized.split(".")
        if (octets.size != 4 || octets.any { it.isEmpty() || it.any { char -> char !in '0'..'9' } }) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        val a = numbers[0]
        val b = numbers[1]
        return a == 10 ||
            // Overlay VPNs such as Tailscale assign peer addresses from RFC 6598.
            (a == 100 && b in 64..127) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }
}
