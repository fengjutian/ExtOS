package dev.extos.runtime.packageformat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginPackageReaderTest {
    @Test
    fun validatesManifestAndEntryTogether() {
        val plugin = PluginPackageReader().read(packageZip(includeEntry = true))
        assertEquals("dev.extos.example", plugin.manifest.id)
    }

    @Test
    fun rejectsMissingEntry() {
        assertThrows(InvalidPackageException::class.java) {
            PluginPackageReader().read(packageZip(includeEntry = false))
        }
    }

    private fun packageZip(includeEntry: Boolean): ByteArrayInputStream {
        val manifest = """
            {
              "schemaVersion": 1,
              "id": "dev.extos.example",
              "name": "Example",
              "version": "0.1.0",
              "runtime": "web",
              "entry": "dist/index.html",
              "minRuntimeVersion": "0.1.0",
              "capabilities": []
            }
        """.trimIndent().toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest)
            zip.closeEntry()
            if (includeEntry) {
                zip.putNextEntry(ZipEntry("dist/index.html"))
                zip.write("<h1>Example</h1>".toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}
