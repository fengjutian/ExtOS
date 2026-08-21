package dev.extos.runtime.storage

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

class PluginStorage(
    private val directory: Path,
    private val maxBytes: Int = 1024 * 1024,
) {
    private val file = directory.resolve("data.json")

    @Synchronized
    fun get(key: String): Any? {
        validateKey(key)
        val data = read()
        return if (data.has(key)) data.get(key) else null
    }

    @Synchronized
    fun set(key: String, value: Any?) {
        validateKey(key)
        require(value == null || value === JSONObject.NULL || value is String ||
            value is Number || value is Boolean || value is JSONObject || value is org.json.JSONArray) {
            "Storage value is not JSON-compatible"
        }
        val data = read().put(key, value ?: JSONObject.NULL)
        write(data)
    }

    @Synchronized
    fun remove(key: String): Boolean {
        validateKey(key)
        val data = read()
        val existed = data.has(key)
        if (existed) write(data.apply { remove(key) })
        return existed
    }

    @Synchronized
    fun clear() {
        Files.deleteIfExists(file)
    }

    private fun read(): JSONObject = if (Files.isRegularFile(file)) {
        JSONObject(Files.readString(file, StandardCharsets.UTF_8))
    } else {
        JSONObject()
    }

    private fun write(data: JSONObject) {
        val encoded = data.toString().toByteArray(StandardCharsets.UTF_8)
        require(encoded.size <= maxBytes) { "Plugin storage quota exceeded" }
        Files.createDirectories(directory)
        val temporary = directory.resolve(".data-${UUID.randomUUID()}.tmp")
        try {
            Files.write(temporary, encoded)
            try {
                Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validateKey(key: String) {
        require(key.isNotBlank() && key.length <= 128) { "Storage key must contain 1 to 128 characters" }
    }
}
