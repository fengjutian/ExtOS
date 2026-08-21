package dev.extos.runtime.network

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection

class NetworkClient(
    private val policy: NetworkPolicy,
    private val maxResponseBytes: Int = 1024 * 1024,
) {
    fun fetch(url: String): JSONObject {
        val uri = policy.validate(url)
        policy.requirePublicAddresses(uri)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json, text/plain, */*;q=0.1")
            connection.setRequestProperty("User-Agent", "ExtOS/0.1")
            val status = connection.responseCode
            require(status !in 300..399) { "Network redirects are not allowed" }
            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0 || declaredLength <= maxResponseBytes) { "Network response is too large" }
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use(::readBounded)?.toString(Charsets.UTF_8).orEmpty()
            return JSONObject()
                .put("status", status)
                .put("contentType", connection.contentType ?: JSONObject.NULL)
                .put("body", body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(output.size() + count <= maxResponseBytes) { "Network response is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
