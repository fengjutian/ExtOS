package dev.extos.runtime.security

import dev.extos.runtime.model.PluginManifest

class CapabilityDeniedException(message: String) : SecurityException(message)

class CapabilityPolicy(
    private val manifest: PluginManifest,
    private val grantedCapabilities: Set<String>,
) {
    fun require(capability: String) {
        if (capability !in manifest.capabilities) {
            throw CapabilityDeniedException("Plugin did not declare $capability")
        }
        if (capability !in grantedCapabilities) {
            throw CapabilityDeniedException("User did not grant $capability")
        }
    }
}
