package dev.extos.runtime.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

class NetworkPolicy(private val allowedHosts: Set<String>) {
    fun validate(url: String): URI {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid network URL")
        }
        require(uri.scheme == "https") { "Only HTTPS network requests are allowed" }
        require(uri.rawUserInfo == null) { "URL credentials are not allowed" }
        require(uri.fragment == null) { "URL fragments are not allowed" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("URL host is required")
        require(host in allowedHosts) { "Host is not present in networkAllowlist" }
        require(uri.port == -1 || uri.port == 443) { "Only the standard HTTPS port is allowed" }
        return uri
    }

    fun requirePublicAddresses(uri: URI) {
        val addresses = InetAddress.getAllByName(uri.host)
        require(addresses.isNotEmpty()) { "Host did not resolve" }
        require(addresses.all(::isPublic)) { "Private or local network destinations are blocked" }
    }

    internal fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 0xff }
        return when (address) {
            is Inet4Address -> bytes[0] != 0 &&
                !(bytes[0] == 100 && bytes[1] in 64..127) &&
                !(bytes[0] == 169 && bytes[1] == 254) &&
                !(bytes[0] == 192 && bytes[1] == 0 && bytes[2] in setOf(0, 2)) &&
                !(bytes[0] == 198 && bytes[1] in 18..19) &&
                !(bytes[0] == 198 && bytes[1] == 51 && bytes[2] == 100) &&
                !(bytes[0] == 203 && bytes[1] == 0 && bytes[2] == 113) &&
                bytes[0] < 224
            is Inet6Address -> !((bytes[0] and 0xfe) == 0xfc) &&
                !(bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8) &&
                !(bytes.take(12).all { it == 0 } && bytes[12] == 0xff && bytes[13] == 0xff)
            else -> false
        }
    }
}
