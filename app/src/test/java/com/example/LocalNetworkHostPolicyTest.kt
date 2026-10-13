package com.example

import com.example.data.network.LocalNetworkHostPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkHostPolicyTest {
    @Test
    fun allowsPrivateLanAndLoopbackAddresses() {
        listOf(
            "192.168.1.10",
            "10.0.0.14",
            "172.16.0.1",
            "172.31.255.254",
            "169.254.1.2",
            "127.0.0.1",
            "localhost",
            "::1",
            "fd12::10",
            "fe80::1"
        ).forEach { host ->
            assertTrue("Expected private/local host to be allowed: $host",
                LocalNetworkHostPolicy.isAllowedCleartextHost(host))
        }
    }

    @Test
    fun rejectsPublicAndMalformedAddresses() {
        listOf(
            "8.8.8.8",
            "1.1.1.1",
            "172.32.0.1",
            "192.167.1.10",
            "10.0.0.256",
            "pos.example.com",
            "127.attacker.example",
            "127.0.0.1.attacker.example",
            "127.evil",
            "fd:not-an-ip",
            "fdnotipv6:aa:bb",
            "１２７.０.０.１",
            "http://192.168.1.10",
            ""
        ).forEach { host ->
            assertFalse("Expected non-local host to be rejected: $host",
                LocalNetworkHostPolicy.isAllowedCleartextHost(host))
        }
    }
}
