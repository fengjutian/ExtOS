package dev.extos.runtime.registry

import java.nio.file.Path

data class InstalledPlugin(
    val id: String,
    val version: String,
    val directory: Path,
    val active: Boolean,
)
