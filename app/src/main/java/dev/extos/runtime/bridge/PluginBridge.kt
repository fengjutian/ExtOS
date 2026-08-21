package dev.extos.runtime.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.lang.ref.WeakReference

class PluginBridge(
    webView: WebView,
    private val dispatcher: BridgeDispatcher,
) {
    private val webView = WeakReference(webView)

    @JavascriptInterface
    fun postMessage(request: String) {
        val response = dispatcher.dispatch(request)
        webView.get()?.post {
            webView.get()?.evaluateJavascript(
                "window.__extosReceive(${JSONObject.quote(response)})",
                null,
            )
        }
    }
}
