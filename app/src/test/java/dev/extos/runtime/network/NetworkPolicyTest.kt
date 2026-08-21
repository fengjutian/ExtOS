package dev.extos.runtime.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkPolicyTest {
    private val policy = NetworkPolicy(setOf("api.example.com"))

    @Test
    fun acceptsExactAllowlistedHttpsHost() {
        assertTrue(policy.validate("https://api.example.com/data?q=1").host == "api.example.com")
    }

    @Test
    fun rejectsHttpCredentialsPortsAndSiblingDomains() {
        listOf(
            "http://api.example.com",
            "https://user@api.example.com",
            "https://api.example.com:8443",
            "https://evil.example.com",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { policy.validate(url) }
        }
    }

    @Test
    fun rejectsPrivateAndLocalAddresses() {
        listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "169.254.1.1", "::1", "fc00::1")
            .forEach { address -> assertFalse(policy.isPublic(InetAddress.getByName(address))) }
    }

    @Test
    fun acceptsPublicAddress() {
        assertTrue(policy.isPublic(InetAddress.getByName("1.1.1.1")))
    }
}
