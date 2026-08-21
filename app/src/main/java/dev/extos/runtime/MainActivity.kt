package dev.extos.runtime

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.extos.runtime.bridge.BridgeDispatcher
import dev.extos.runtime.bridge.PluginBridge
import dev.extos.runtime.model.ManifestParser
import dev.extos.runtime.packageformat.PluginPackageReader
import dev.extos.runtime.packageformat.ValidatedPluginPackage
import dev.extos.runtime.registry.InstalledPlugin
import dev.extos.runtime.registry.PluginInstaller
import dev.extos.runtime.registry.PluginRegistry
import dev.extos.runtime.security.CapabilityGrantStore
import dev.extos.runtime.security.CapabilityPolicy
import dev.extos.runtime.web.PluginContentLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class MainActivity : AppCompatActivity() {
    private val pluginRoot by lazy { filesDir.toPath().resolve("plugins") }
    private val registry by lazy { PluginRegistry(pluginRoot) }
    private val installer by lazy { PluginInstaller(pluginRoot, registry) }
    private val grantStore by lazy { CapabilityGrantStore(this) }
    private var pluginView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        destroyPluginView()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
            addView(TextView(context).apply {
                text = "ExtOS"
                textSize = 32f
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "Installed plugin applications"
                textSize = 16f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(4), 0, dp(24))
            })
            addView(Button(context).apply {
                text = "Install .ext package"
                setOnClickListener { choosePackage() }
            })
        }

        val activePlugins = registry.list().filter { it.active }
        if (activePlugins.isEmpty()) {
            content.addView(TextView(this).apply {
                text = "No plugins installed yet. Choose a local .ext package to begin."
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(32), 0, 0)
            })
        } else {
            activePlugins.forEach { plugin -> content.addView(pluginCard(plugin)) }
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 17, 22))
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun pluginCard(plugin: InstalledPlugin): View {
        val manifest = runCatching {
            ManifestParser.parse(
                Files.readString(plugin.directory.resolve("manifest.json"), StandardCharsets.UTF_8),
            )
        }.getOrNull()
        return Button(this).apply {
            text = manifest?.let { "${it.name}\n${it.version}" }
                ?: "${plugin.id}\n${plugin.version} (invalid manifest)"
            isEnabled = manifest != null
            setAllCaps(false)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { if (manifest != null) launchPlugin(plugin, manifest.entry) }
        }
    }

    private fun choosePackage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_PACKAGE)
    }

    @Deprecated("Uses the stable activity result API until host UI dependencies are finalized")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PACKAGE && resultCode == Activity.RESULT_OK) {
            data?.data?.let(::inspectPackage)
        }
    }

    private fun inspectPackage(uri: Uri) {
        Toast.makeText(this, "Inspecting package…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use(PluginPackageReader()::read)
                    ?: error("The selected document could not be opened")
            }
            runOnUiThread { result.onSuccess(::confirmInstallation).onFailure(::showError) }
        }.start()
    }

    private fun confirmInstallation(plugin: ValidatedPluginPackage) {
        val manifest = plugin.manifest
        val capabilityText = if (manifest.capabilities.isEmpty()) {
            "This plugin requests no host capabilities."
        } else {
            "Requested capabilities:\n\n" +
                manifest.capabilities.sorted().joinToString("\n") { "• $it" }
        }
        AlertDialog.Builder(this)
            .setTitle("Install ${manifest.name} ${manifest.version}?")
            .setMessage(
                "Publisher signatures are not implemented yet. " +
                    "Install only packages you trust.\n\n$capabilityText",
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Install") { _, _ -> installPackage(plugin) }
            .show()
    }

    private fun installPackage(plugin: ValidatedPluginPackage) {
        Thread {
            val result = runCatching { installer.install(plugin) }
            runOnUiThread {
                result.onSuccess {
                    grantStore.replace(plugin.manifest.id, plugin.manifest.capabilities)
                    Toast.makeText(this, "${plugin.manifest.name} installed", Toast.LENGTH_SHORT).show()
                    showHome()
                }.onFailure(::showError)
            }
        }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun launchPlugin(plugin: InstalledPlugin, entry: String) {
        val manifest = ManifestParser.parse(
            Files.readString(plugin.directory.resolve("manifest.json"), StandardCharsets.UTF_8),
        )
        val contentLoader = PluginContentLoader(plugin.directory)
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = if (
                    request.url.scheme == PluginContentLoader.SCHEME &&
                    request.url.host == PluginContentLoader.HOST
                ) contentLoader.load(request.url) else null

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = request.url.scheme != PluginContentLoader.SCHEME ||
                    request.url.host != PluginContentLoader.HOST

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('extosready'))",
                        null,
                    )
                }
            }
            val policy = CapabilityPolicy(manifest, grantStore.grantsFor(manifest.id))
            val dispatcher = BridgeDispatcher(policy) { message ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
            addJavascriptInterface(PluginBridge(this, dispatcher), "ExtOSNative")
            loadUrl(PluginContentLoader.ORIGIN + entry)
        }
        pluginView = webView
        setContentView(webView)
    }

    override fun onBackPressed() {
        val webView = pluginView
        when {
            webView == null -> super.onBackPressed()
            webView.canGoBack() -> webView.goBack()
            else -> showHome()
        }
    }

    override fun onDestroy() {
        destroyPluginView()
        super.onDestroy()
    }

    private fun destroyPluginView() {
        pluginView?.apply {
            removeJavascriptInterface("ExtOSNative")
            stopLoading()
            destroy()
        }
        pluginView = null
    }

    private fun showError(error: Throwable) {
        AlertDialog.Builder(this)
            .setTitle("ExtOS could not complete the operation")
            .setMessage(error.message ?: "Unknown error")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PACKAGE = 1001
    }
}
