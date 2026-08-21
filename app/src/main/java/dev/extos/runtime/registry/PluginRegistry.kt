package dev.extos.runtime.registry

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator

class PluginRegistry(private val root: Path) {
    private val pluginIdPattern = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$")
    private val versionPattern = Regex(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)([-+][0-9A-Za-z.-]+)?$",
    )

    fun activeVersion(pluginId: String): String? {
        val marker = activeMarker(pluginId)
        if (!Files.isRegularFile(marker)) return null
        return JSONObject(Files.readString(marker, StandardCharsets.UTF_8)).getString("version")
    }

    fun activate(pluginId: String, version: String) {
        val versionDirectory = versionDirectory(pluginId, version)
        require(Files.isDirectory(versionDirectory)) { "Plugin version is not installed" }

        val pluginDirectory = pluginDirectory(pluginId)
        Files.createDirectories(pluginDirectory)
        val marker = activeMarker(pluginId)
        val temporary = pluginDirectory.resolve(".active-${java.util.UUID.randomUUID()}.tmp")
        try {
            Files.writeString(
                temporary,
                JSONObject().put("version", version).toString(),
                StandardCharsets.UTF_8,
            )
            atomicReplace(temporary, marker)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun list(): List<InstalledPlugin> {
        if (!Files.isDirectory(root)) return emptyList()
        val installed = mutableListOf<InstalledPlugin>()
        Files.list(root).use { plugins ->
            plugins.filter { Files.isDirectory(it) }.forEach { pluginDirectory ->
                val id = pluginDirectory.fileName.toString()
                val active = runCatching { activeVersion(id) }.getOrNull()
                val versionsDirectory = pluginDirectory.resolve("versions")
                if (Files.isDirectory(versionsDirectory)) {
                    Files.list(versionsDirectory).use { versions ->
                        versions.filter { Files.isDirectory(it) }.forEach { directory ->
                            val version = directory.fileName.toString()
                            if (Files.isRegularFile(directory.resolve("manifest.json"))) {
                                installed += InstalledPlugin(
                                    id,
                                    version,
                                    directory,
                                    version == active,
                                )
                            }
                        }
                    }
                }
            }
        }
        return installed.sortedWith(compareBy(InstalledPlugin::id, InstalledPlugin::version))
    }

    fun repair() {
        if (!Files.isDirectory(root)) return
        val staging = root.resolve(".staging")
        if (Files.isDirectory(staging)) {
            Files.list(staging).use { children -> children.forEach(::deleteTree) }
        }

        val byPlugin = list().groupBy(InstalledPlugin::id)
        byPlugin.forEach { (pluginId, versions) ->
            val active = runCatching { activeVersion(pluginId) }.getOrNull()
            if (versions.none { it.version == active }) {
                val newest = versions.maxWithOrNull { left, right ->
                    compareVersions(left.version, right.version)
                }
                if (newest != null) activate(pluginId, newest.version)
            }
        }
    }

    fun deleteVersion(pluginId: String, version: String) {
        require(activeVersion(pluginId) != version) { "Cannot delete the active plugin version" }
        deleteTree(versionDirectory(pluginId, version))
    }

    fun uninstall(pluginId: String) {
        deleteTree(pluginDirectory(pluginId))
    }

    internal fun versionDirectory(pluginId: String, version: String): Path =
        pluginDirectory(pluginId).resolve("versions").resolve(validateVersion(version))

    private fun pluginDirectory(pluginId: String): Path {
        require(pluginIdPattern.matches(pluginId)) { "Invalid plugin id" }
        return root.resolve(pluginId)
    }

    private fun activeMarker(pluginId: String): Path = pluginDirectory(pluginId).resolve("active.json")

    private fun validateVersion(version: String): String {
        require(versionPattern.matches(version)) { "Invalid plugin version" }
        return version
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.substringBefore('-').substringBefore('+').split('.').map(String::toLong)
        val rightParts = right.substringBefore('-').substringBefore('+').split('.').map(String::toLong)
        for (index in 0..2) {
            val compared = leftParts[index].compareTo(rightParts[index])
            if (compared != 0) return compared
        }
        val leftStable = '-' !in left
        val rightStable = '-' !in right
        return leftStable.compareTo(rightStable)
    }

    private fun atomicReplace(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteTree(directory: Path) {
        if (!Files.exists(directory)) return
        val normalizedRoot = root.toAbsolutePath().normalize()
        val target = directory.toAbsolutePath().normalize()
        check(target.startsWith(normalizedRoot) && target != normalizedRoot) {
            "Refusing to delete outside the plugin registry"
        }
        Files.walk(target).use { paths ->
            paths.sorted(Comparator.reverseOrder<Path>()).forEach { Files.deleteIfExists(it) }
        }
    }
}
