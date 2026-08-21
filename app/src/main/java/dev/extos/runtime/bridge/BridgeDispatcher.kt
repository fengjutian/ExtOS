package dev.extos.runtime.bridge

import dev.extos.runtime.security.CapabilityDeniedException
import dev.extos.runtime.security.CapabilityPolicy
import org.json.JSONException
import org.json.JSONObject

private const val MAX_REQUEST_BYTES = 16 * 1024

class BridgeDispatcher(
    private val policy: CapabilityPolicy,
    private val showToast: (String) -> Unit,
) {
    fun dispatch(source: String): String {
        var requestId: String? = null
        return try {
            require(source.toByteArray(Charsets.UTF_8).size <= MAX_REQUEST_BYTES) {
                "Request is too large"
            }
            val request = JSONObject(source)
            val id = request.getString("id").also {
                require(it.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) { "Invalid request id" }
            }
            requestId = id
            val result = when (val method = request.getString("method")) {
                "runtime.version" -> {
                    policy.require(method)
                    JSONObject().put("version", "0.1.0")
                }
                "ui.toast" -> {
                    policy.require(method)
                    val message = request.getJSONObject("params").getString("message")
                    require(message.isNotBlank() && message.length <= 200) {
                        "Toast message must contain 1 to 200 characters"
                    }
                    showToast(message)
                    JSONObject.NULL
                }
                else -> throw NoSuchMethodException("Unknown bridge method: $method")
            }
            success(id, result)
        } catch (error: Exception) {
            failure(requestId, error)
        }
    }

    private fun success(id: String, result: Any): String = JSONObject()
        .put("id", id)
        .put("ok", true)
        .put("result", result)
        .toString()

    private fun failure(id: String?, error: Exception): String {
        val code = when (error) {
            is CapabilityDeniedException -> "CAPABILITY_DENIED"
            is NoSuchMethodException -> "METHOD_NOT_FOUND"
            is JSONException, is IllegalArgumentException -> "INVALID_REQUEST"
            else -> "INTERNAL_ERROR"
        }
        val safeMessage = when (code) {
            "INTERNAL_ERROR" -> "The host could not process the request"
            else -> error.message ?: code
        }
        return JSONObject()
            .put("id", id ?: JSONObject.NULL)
            .put("ok", false)
            .put("error", JSONObject().put("code", code).put("message", safeMessage))
            .toString()
    }
}
