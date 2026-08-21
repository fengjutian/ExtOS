package dev.extos.runtime.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun installsAndActivatesPackage() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        val registry = PluginRegistry(root)
        val installed = PluginInstaller(root, registry).install(pluginPackage("0.1.0"))

        assertTrue(Files.isRegularFile(installed.directory.resolve("dist/index.html")))
        assertEquals("0.1.0", registry.activeVersion("dev.extos.example"))
        assertTrue(registry.list().single().active)
    }

    @Test
    fun installsInactiveVersionWithoutChangingCurrentVersion() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        val registry = PluginRegistry(root)
        val installer = PluginInstaller(root, registry)
        installer.install(pluginPackage("0.1.0"))
        val candidate = installer.install(pluginPackage("0.2.0"), activate = false)

        assertFalse(candidate.active)
        assertEquals("0.1.0", registry.activeVersion("dev.extos.example"))
        assertEquals(2, registry.list().size)
    }

    @Test
    fun switchesBackToPreviouslyInstalledVersion() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        val registry = PluginRegistry(root)
        val installer = PluginInstaller(root, registry)
        installer.install(pluginPackage("0.1.0"))
        installer.install(pluginPackage("0.2.0"))

        registry.activate("dev.extos.example", "0.1.0")

        assertEquals("0.1.0", registry.activeVersion("dev.extos.example"))
        assertTrue(registry.list().single { it.version == "0.1.0" }.active)
    }

    @Test
    fun refusesToOverwriteInstalledVersion() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        val installer = PluginInstaller(root)
        installer.install(pluginPackage("0.1.0"))

        assertThrows(IllegalArgumentException::class.java) {
            installer.install(pluginPackage("0.1.0"))
        }
    }

    @Test
    fun registryRejectsPathLikePluginIdentifiers() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        assertThrows(IllegalArgumentException::class.java) {
            PluginRegistry(root).activeVersion("../outside")
        }
    }

    @Test
    fun deletesInactiveVersionAndUninstallsPlugin() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        val registry = PluginRegistry(root)
        val installer = PluginInstaller(root, registry)
        installer.install(pluginPackage("0.1.0"))
        installer.install(pluginPackage("0.2.0"), activate = false)

        registry.deleteVersion("dev.extos.example", "0.2.0")
        assertEquals(listOf("0.1.0"), registry.list().map { it.version })

        registry.uninstall("dev.extos.example")
        assertTrue(registry.list().isEmpty())
    }

    @Test
    fun refusesToDeleteActiveVersion() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        val registry = PluginRegistry(root)
        PluginInstaller(root, registry).install(pluginPackage("0.1.0"))
        assertThrows(IllegalArgumentException::class.java) {
            registry.deleteVersion("dev.extos.example", "0.1.0")
        }
    }

    @Test
    fun repairsMissingActiveVersionAndCleansStaging() {
        val root = temporaryFolder.newFolder("plugins").toPath()
        val registry = PluginRegistry(root)
        val installer = PluginInstaller(root, registry)
        installer.install(pluginPackage("0.9.0"), activate = false)
        installer.install(pluginPackage("1.0.0"), activate = false)
        val abandoned = root.resolve(".staging").resolve("abandoned")
        Files.createDirectories(abandoned)
        Files.writeString(abandoned.resolve("partial"), "data")

        registry.repair()

        assertEquals("1.0.0", registry.activeVersion("dev.extos.example"))
        assertFalse(Files.exists(abandoned))
    }

    private fun pluginPackage(version: String): ByteArrayInputStream {
        val manifest = """
            {
              "schemaVersion": 1,
              "id": "dev.extos.example",
              "name": "Example",
              "version": "$version",
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
            zip.putNextEntry(ZipEntry("dist/index.html"))
            zip.write("<h1>$version</h1>".toByteArray())
            zip.closeEntry()
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}
