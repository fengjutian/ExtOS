package dev.extos.runtime.model

import org.json.JSONObject

object ManifestParser {
    private val pluginId = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$")
    private val semanticVersion = Regex(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)([-+][0-9A-Za-z.-]+)?$",
    )

    fun parse(source: String): PluginManifest {
        val json = JSONObject(source)
        val capabilitiesJson = json.getJSONArray("capabilities")
        val capabilities = buildSet {
            for (index in 0 until capabilitiesJson.length()) {
                add(capabilitiesJson.getString(index))
            }
        }

        return PluginManifest(
            schemaVersion = json.getInt("schemaVersion"),
            id = json.getString("id"),
            name = json.getString("name"),
            version = json.getString("version"),
            runtime = json.getString("runtime"),
            entry = json.getString("entry"),
            minRuntimeVersion = json.getString("minRuntimeVersion"),
            capabilities = capabilities,
        ).also(::validate)
    }

    private fun validate(manifest: PluginManifest) {
        require(manifest.schemaVersion == 1) { "Unsupported manifest schema" }
        require(pluginId.matches(manifest.id)) { "Invalid plugin id" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 80) { "Invalid plugin name" }
        require(semanticVersion.matches(manifest.version)) { "Invalid plugin version" }
        require(semanticVersion.matches(manifest.minRuntimeVersion)) { "Invalid minimum runtime version" }
        require(manifest.runtime == "web") { "Unsupported plugin runtime" }
        require(isSafeRelativePath(manifest.entry)) { "Unsafe plugin entry path" }
        require(manifest.capabilities.all { it in CapabilityCatalog.supported }) {
            "Manifest requests an unknown capability"
        }
    }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) return false
        return path.split('/', '\\').none { it.isBlank() || it == "." || it == ".." }
    }
}

object CapabilityCatalog {
    val supported = setOf("runtime.version", "ui.toast")
}
