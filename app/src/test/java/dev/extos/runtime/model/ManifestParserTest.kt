package dev.extos.runtime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ManifestParserTest {
    @Test
    fun parsesSupportedManifest() {
        val manifest = ManifestParser.parse(validManifest())
        assertEquals("dev.extos.hello", manifest.id)
        assertEquals(setOf("runtime.version"), manifest.capabilities)
    }

    @Test
    fun rejectsTraversalEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            ManifestParser.parse(validManifest().replace("dist/index.html", "../index.html"))
        }
    }

    @Test
    fun rejectsUnknownCapability() {
        assertThrows(IllegalArgumentException::class.java) {
            ManifestParser.parse(validManifest().replace("runtime.version", "android.raw"))
        }
    }

    @Test
    fun parsesNetworkAllowlist() {
        val source = validManifest()
            .replace("\"runtime.version\"", "\"runtime.version\", \"network.fetch\"")
            .replace("\n}", ",\n  \"networkAllowlist\": [\"api.example.com\"]\n}")
        val manifest = ManifestParser.parse(source)
        assertEquals(setOf("api.example.com"), manifest.networkAllowlist)
    }

    @Test
    fun rejectsNetworkCapabilityWithoutAllowlist() {
        assertThrows(IllegalArgumentException::class.java) {
            ManifestParser.parse(validManifest().replace("runtime.version", "network.fetch"))
        }
    }

    private fun validManifest() = """
        {
          "schemaVersion": 1,
          "id": "dev.extos.hello",
          "name": "Hello",
          "version": "0.1.0",
          "runtime": "web",
          "entry": "dist/index.html",
          "minRuntimeVersion": "0.1.0",
          "capabilities": ["runtime.version"]
        }
    """.trimIndent()
}
