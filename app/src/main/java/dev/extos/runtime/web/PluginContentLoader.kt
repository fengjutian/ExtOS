package dev.extos.runtime.web

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class PluginContentLoader(pluginDirectory: Path, pluginId: String) {
    private val root = pluginDirectory.toRealPath()
    private val host = "$pluginId.$HOST_SUFFIX"
    val origin = "$SCHEME://$host/"

    fun owns(uri: Uri): Boolean = uri.scheme == SCHEME && uri.host == host

    fun load(uri: Uri): WebResourceResponse {
        if (!owns(uri)) return denied("Origin is not allowed")
        val relativePath = uri.pathSegments.joinToString("/")
        if (!isSafePath(relativePath)) return denied("Path is not allowed")

        val candidate = root.resolve(relativePath).normalize()
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) return notFound()
        val realPath = runCatching { candidate.toRealPath() }.getOrNull() ?: return notFound()
        if (!realPath.startsWith(root)) return denied("Path escaped plugin directory")

        val extension = candidate.fileName.toString().substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: FALLBACK_MIME_TYPES[extension]
            ?: "application/octet-stream"
        val encoding = if (mime.startsWith("text/") || mime in UTF8_MIME_TYPES) "UTF-8" else null
        return WebResourceResponse(
            mime,
            encoding,
            200,
            "OK",
            SECURITY_HEADERS,
            Files.newInputStream(realPath),
        )
    }

    private fun isSafePath(path: String): Boolean =
        path.isNotBlank() && '\\' !in path && path.split('/').none {
            it.isBlank() || it == "." || it == ".."
        }

    private fun denied(message: String) = response(403, "Forbidden", message)

    private fun notFound() = response(404, "Not Found", "Plugin resource was not found")

    private fun response(status: Int, reason: String, message: String) = WebResourceResponse(
        "text/plain", "UTF-8", status, reason,
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(message.toByteArray()),
    )

    companion object {
        const val SCHEME = "https"
        private const val HOST_SUFFIX = "plugin.extos.local"

        fun blockedExternalRequest(): WebResourceResponse = WebResourceResponse(
            "text/plain", "UTF-8", 403, "Forbidden",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream("External resources are blocked".toByteArray()),
        )

        private val UTF8_MIME_TYPES = setOf("application/javascript", "application/json", "image/svg+xml")
        private val SECURITY_HEADERS = mapOf(
            "Cache-Control" to "no-store",
            "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'unsafe-inline'; " +
                "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'none'; " +
                "object-src 'none'; frame-src 'none'; base-uri 'none'",
            "X-Content-Type-Options" to "nosniff",
        )
        private val FALLBACK_MIME_TYPES = mapOf(
            "js" to "application/javascript",
            "json" to "application/json",
            "svg" to "image/svg+xml",
            "webmanifest" to "application/manifest+json",
        )
    }
}
