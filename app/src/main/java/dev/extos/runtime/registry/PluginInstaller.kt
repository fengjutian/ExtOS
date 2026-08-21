package dev.extos.runtime.registry

import dev.extos.runtime.packageformat.PluginPackageReader
import dev.extos.runtime.packageformat.ValidatedPluginPackage
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.UUID

class PluginInstaller(
    private val root: Path,
    private val registry: PluginRegistry = PluginRegistry(root),
    private val packageReader: PluginPackageReader = PluginPackageReader(),
) {
    fun install(source: InputStream, activate: Boolean = true): InstalledPlugin {
        return install(packageReader.read(source), activate)
    }

    fun install(plugin: ValidatedPluginPackage, activate: Boolean = true): InstalledPlugin {
        val manifest = plugin.manifest
        val target = registry.versionDirectory(manifest.id, manifest.version)
        require(!Files.exists(target)) { "Plugin version is already installed" }

        Files.createDirectories(root)
        val stagingRoot = root.resolve(".staging")
        Files.createDirectories(stagingRoot)
        val staging = stagingRoot.resolve(UUID.randomUUID().toString())

        try {
            Files.createDirectory(staging)
            writePackage(staging, plugin)
            Files.createDirectories(target.parent)
            moveDirectory(staging, target)
            if (activate) registry.activate(manifest.id, manifest.version)
        } finally {
            deleteTree(staging)
        }

        return InstalledPlugin(
            manifest.id,
            manifest.version,
            target,
            active = activate,
        )
    }

    private fun writePackage(directory: Path, plugin: ValidatedPluginPackage) {
        plugin.files.forEach { (relativePath, content) ->
            val target = directory.resolve(relativePath).normalize()
            check(target.startsWith(directory)) { "Validated path escaped staging directory" }
            Files.createDirectories(target.parent)
            Files.write(target, content)
        }
    }

    private fun moveDirectory(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun deleteTree(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder<Path>()).forEach { Files.deleteIfExists(it) }
        }
    }
}
