package dev.extos.runtime.registry

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

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
}
