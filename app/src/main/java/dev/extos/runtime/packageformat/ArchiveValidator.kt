package dev.extos.runtime.packageformat

import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class ArchiveLimits(
    val maxFiles: Int = 256,
    val maxFileBytes: Long = 4L * 1024 * 1024,
    val maxTotalBytes: Long = 20L * 1024 * 1024,
)

data class ValidatedArchive(val files: Map<String, ByteArray>) {
    fun requireFile(path: String): ByteArray =
        files[path] ?: throw InvalidPackageException("Package is missing $path")
}

class InvalidPackageException(message: String) : IllegalArgumentException(message)

class ArchiveValidator(private val limits: ArchiveLimits = ArchiveLimits()) {
    fun read(source: InputStream): ValidatedArchive {
        require(limits.maxFiles > 0 && limits.maxFileBytes > 0 && limits.maxTotalBytes > 0)

        val files = linkedMapOf<String, ByteArray>()
        val seenPaths = mutableSetOf<String>()
        val filePaths = mutableSetOf<String>()
        var totalBytes = 0L

        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = normalize(if (entry.isDirectory) entry.name.removeSuffix("/") else entry.name)
                val pathKey = path.lowercase(Locale.ROOT)
                if (!seenPaths.add(pathKey)) {
                    invalid("Package contains a duplicate or case-colliding path: $path")
                }
                val ancestors = pathKey.split('/').dropLast(1).runningFold("") { parent, segment ->
                    if (parent.isEmpty()) segment else "$parent/$segment"
                }
                if (ancestors.any { it in filePaths }) {
                    invalid("A file is used as a parent directory: $path")
                }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                if (files.size >= limits.maxFiles) invalid("Package has too many files")
                if (seenPaths.any { it.startsWith("$pathKey/") }) {
                    invalid("A directory is replaced by a file: $path")
                }
                filePaths.add(pathKey)
                if (entry.size > limits.maxFileBytes) invalid("File exceeds size limit: $path")

                val content = readEntry(zip, path) { count ->
                    totalBytes += count
                    if (totalBytes > limits.maxTotalBytes) invalid("Package exceeds total size limit")
                }
                files[path] = content
                zip.closeEntry()
            }
        }

        if (files.isEmpty()) invalid("Package is empty")
        return ValidatedArchive(files)
    }

    private fun readEntry(
        zip: ZipInputStream,
        path: String,
        onBytes: (Long) -> Unit,
    ): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var fileBytes = 0L
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            fileBytes += count
            if (fileBytes > limits.maxFileBytes) invalid("File exceeds size limit: $path")
            onBytes(count.toLong())
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun normalize(rawPath: String): String {
        if (rawPath.isBlank()) invalid("Package contains an empty path")
        if ('\\' in rawPath) invalid("Backslashes are not allowed in package paths")
        if (rawPath.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(rawPath)) {
            invalid("Absolute package path is not allowed: $rawPath")
        }
        val segments = rawPath.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) {
            invalid("Unsafe package path: $rawPath")
        }
        return segments.joinToString("/")
    }

    private fun invalid(message: String): Nothing = throw InvalidPackageException(message)
}
