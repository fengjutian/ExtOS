package dev.extos.runtime.model

data class PluginManifest(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val version: String,
    val runtime: String,
    val entry: String,
    val minRuntimeVersion: String,
    val capabilities: Set<String>,
    val networkAllowlist: Set<String> = emptySet(),
)
