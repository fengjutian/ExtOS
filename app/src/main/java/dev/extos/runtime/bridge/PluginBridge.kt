package dev.extos.runtime.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast
import dev.extos.runtime.model.PluginManifest

class PluginBridge(
    private val context: Context,
    private val manifest: PluginManifest,
) {
    @JavascriptInterface
    fun runtimeVersion(): String {
        authorize("runtime.version")
        return "0.1.0"
    }

    @JavascriptInterface
    fun showToast(message: String) {
        authorize("ui.toast")
        require(message.length <= 200) { "Toast message is too long" }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun authorize(capability: String) {
        check(capability in manifest.capabilities) {
            "Plugin ${manifest.id} did not declare $capability"
        }
    }
}
