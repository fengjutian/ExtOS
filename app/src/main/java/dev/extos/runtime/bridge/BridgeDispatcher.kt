package dev.extos.runtime.bridge

import dev.extos.runtime.security.CapabilityDeniedException
import dev.extos.runtime.security.CapabilityPolicy
import dev.extos.runtime.storage.PluginStorage
import dev.extos.runtime.network.NetworkClient
import org.json.JSONException
import org.json.JSONObject

private const val MAX_REQUEST_BYTES = 16 * 1024

class BridgeDispatcher(
    private val policy: CapabilityPolicy,
    private val storage: PluginStorage? = null,
    private val network: NetworkClient? = null,
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
                "storage.get" -> {
                    policy.require("storage.private")
                    val value = requireStorage().get(params(request).getString("key"))
                    value ?: JSONObject.NULL
                }
                "storage.set" -> {
                    policy.require("storage.private")
                    val params = params(request)
                    require(params.has("value")) { "Storage value is required" }
                    requireStorage().set(params.getString("key"), params.get("value"))
                    JSONObject.NULL
                }
                "storage.remove" -> {
                    policy.require("storage.private")
                    requireStorage().remove(params(request).getString("key"))
                }
                "storage.clear" -> {
                    policy.require("storage.private")
                    requireStorage().clear()
                    JSONObject.NULL
                }
                "network.fetch" -> {
                    policy.require("network.fetch")
                    requireNetwork().fetch(params(request).getString("url"))
                }
                else -> throw NoSuchMethodException("Unknown bridge method: $method")
            }
            success(id, result)
        } catch (error: Exception) {
            failure(requestId, error)
        }
    }

    private fun params(request: JSONObject): JSONObject = request.optJSONObject("params")
        ?: throw IllegalArgumentException("Request params must be an object")

    private fun requireStorage(): PluginStorage = storage
        ?: error("Plugin storage is unavailable")

    private fun requireNetwork(): NetworkClient = network
        ?: error("Plugin network client is unavailable")

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
