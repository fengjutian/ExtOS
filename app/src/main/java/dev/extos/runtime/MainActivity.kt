package dev.extos.runtime

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import dev.extos.runtime.bridge.PluginBridge
import dev.extos.runtime.model.ManifestParser

class MainActivity : AppCompatActivity() {
    private lateinit var pluginView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manifestSource = assets.open("plugins/hello/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val manifest = ManifestParser.parse(manifestSource)
        val baseUrl = "https://plugin.extos.local/"
        val entrySource = assets.open("plugins/hello/${manifest.entry}")
            .bufferedReader()
            .use { it.readText() }

        pluginView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = !request.url.toString().startsWith(baseUrl)
            }
            addJavascriptInterface(PluginBridge(this@MainActivity, manifest), "ExtOS")
            loadDataWithBaseURL(baseUrl, entrySource, "text/html", "UTF-8", null)
        }

        setContentView(pluginView)
    }

    override fun onDestroy() {
        pluginView.removeJavascriptInterface("ExtOS")
        pluginView.destroy()
        super.onDestroy()
    }
}
