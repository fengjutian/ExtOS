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
