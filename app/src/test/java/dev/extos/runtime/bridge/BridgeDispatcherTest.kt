package dev.extos.runtime.bridge

import dev.extos.runtime.model.PluginManifest
import dev.extos.runtime.security.CapabilityPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeDispatcherTest {
    private val manifest = PluginManifest(
        1, "dev.extos.test", "Test", "0.1.0", "web", "dist/index.html", "0.1.0",
        setOf("runtime.version", "ui.toast"),
    )

    @Test
    fun returnsRuntimeVersion() {
        val response = dispatch(setOf("runtime.version"), "runtime.version")
        assertTrue(response.getBoolean("ok"))
        assertEquals("0.1.0", response.getJSONObject("result").getString("version"))
    }

    @Test
    fun rejectsCapabilityWithoutUserGrant() {
        val response = dispatch(emptySet(), "runtime.version")
        assertFalse(response.getBoolean("ok"))
        assertEquals("CAPABILITY_DENIED", response.getJSONObject("error").getString("code"))
    }

    @Test
    fun rejectsUnknownMethod() {
        val response = dispatch(manifest.capabilities, "device.root")
        assertEquals("METHOD_NOT_FOUND", response.getJSONObject("error").getString("code"))
    }

    private fun dispatch(grants: Set<String>, method: String): JSONObject {
        val dispatcher = BridgeDispatcher(CapabilityPolicy(manifest, grants)) {}
        val request = JSONObject().put("id", "request-1").put("method", method).put("params", JSONObject())
        return JSONObject(dispatcher.dispatch(request.toString()))
    }
}
