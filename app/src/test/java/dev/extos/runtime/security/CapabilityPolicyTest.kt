package dev.extos.runtime.security

import dev.extos.runtime.model.PluginManifest
import org.junit.Assert.assertThrows
import org.junit.Test

class CapabilityPolicyTest {
    private val manifest = PluginManifest(
        1,
        "dev.extos.test",
        "Test",
        "0.1.0",
        "web",
        "dist/index.html",
        "0.1.0",
        setOf("runtime.version", "ui.toast"),
    )

    @Test
    fun deniesUndeclaredCapability() {
        assertThrows(CapabilityDeniedException::class.java) {
            CapabilityPolicy(manifest, manifest.capabilities).require("storage.read")
        }
    }

    @Test
    fun deniesDeclaredButNotGrantedCapability() {
        assertThrows(CapabilityDeniedException::class.java) {
            CapabilityPolicy(manifest, setOf("runtime.version")).require("ui.toast")
        }
    }
}
