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
import dev.extos.runtime.model.PluginManifest
import dev.extos.runtime.packageformat.PluginPackageReader
import dev.extos.runtime.packageformat.ValidatedPluginPackage
import dev.extos.runtime.registry.InstalledPlugin
import dev.extos.runtime.registry.PluginInstaller
import dev.extos.runtime.registry.PluginRegistry
import dev.extos.runtime.security.CapabilityGrantStore
import dev.extos.runtime.security.CapabilityPolicy
import dev.extos.runtime.storage.PluginStorage
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
        registry.repair()
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
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
            addView(Button(context).apply {
                text = manifest?.let { "${it.name}\n${it.version}" }
                    ?: "${plugin.id}\n${plugin.version} (invalid manifest)"
                isEnabled = manifest != null
                setAllCaps(false)
                setOnClickListener { if (manifest != null) launchPlugin(plugin, manifest.entry) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "Manage"
                isEnabled = manifest != null
                setOnClickListener { if (manifest != null) managePlugin(plugin, manifest) }
            })
        }
    }

    private fun managePlugin(plugin: InstalledPlugin, manifest: PluginManifest) {
        AlertDialog.Builder(this)
            .setTitle(manifest.name)
            .setItems(arrayOf("Versions", "Permissions", "Clear data", "Uninstall")) { _, which ->
                when (which) {
                    0 -> showVersions(plugin.id)
                    1 -> showPermissions(manifest)
                    2 -> confirmClearData(manifest)
                    3 -> confirmUninstall(manifest)
                }
            }
            .show()
    }

    private fun showVersions(pluginId: String) {
        val versions = registry.list().filter { it.id == pluginId }
        val labels = versions.map { if (it.active) "${it.version} (active)" else it.version }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Installed versions")
            .setItems(labels) { _, selected ->
                val version = versions[selected]
                if (!version.active) {
                    registry.activate(pluginId, version.version)
                    showHome()
                }
            }
            .setNeutralButton("Delete inactive") { _, _ -> showVersionDeletion(pluginId) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showVersionDeletion(pluginId: String) {
        val versions = registry.list().filter { it.id == pluginId && !it.active }
        if (versions.isEmpty()) {
            Toast.makeText(this, "There are no inactive versions", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete an inactive version")
            .setItems(versions.map { it.version }.toTypedArray()) { _, selected ->
                registry.deleteVersion(pluginId, versions[selected].version)
                showHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissions(manifest: PluginManifest) {
        val capabilities = manifest.capabilities.sorted()
        if (capabilities.isEmpty()) {
            Toast.makeText(this, "This plugin requests no capabilities", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = grantStore.grantsFor(manifest.id).toMutableSet()
        AlertDialog.Builder(this)
            .setTitle("${manifest.name} permissions")
            .setMultiChoiceItems(
                capabilities.toTypedArray(),
                capabilities.map { it in selected }.toBooleanArray(),
            ) { _, index, enabled ->
                if (enabled) selected += capabilities[index] else selected -= capabilities[index]
            }
            .setPositiveButton("Save") { _, _ -> grantStore.replace(manifest.id, selected) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearData(manifest: PluginManifest) {
        AlertDialog.Builder(this)
            .setTitle("Clear ${manifest.name} data?")
            .setMessage("Private plugin storage will be permanently removed.")
            .setPositiveButton("Clear") { _, _ ->
                PluginStorage(pluginDataDirectory(manifest.id)).clear()
                Toast.makeText(this, "Plugin data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmUninstall(manifest: PluginManifest) {
        AlertDialog.Builder(this)
            .setTitle("Uninstall ${manifest.name}?")
            .setMessage("All installed versions, grants, and private data will be removed.")
            .setPositiveButton("Uninstall") { _, _ ->
                registry.uninstall(manifest.id)
                grantStore.remove(manifest.id)
                PluginStorage(pluginDataDirectory(manifest.id)).clear()
                showHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        Toast.makeText(this, "Inspecting package...", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use { PluginPackageReader().read(it) }
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
                manifest.capabilities.sorted().joinToString("\n") { "- $it" }
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
        val contentLoader = PluginContentLoader(plugin.directory, manifest.id)
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
                ): WebResourceResponse? = if (contentLoader.owns(request.url)) {
                    contentLoader.load(request.url)
                } else {
                    PluginContentLoader.blockedExternalRequest()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = !contentLoader.owns(request.url)

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('extosready'))",
                        null,
                    )
                }
            }
            val policy = CapabilityPolicy(manifest, grantStore.grantsFor(manifest.id))
            val dispatcher = BridgeDispatcher(
                policy = policy,
                showToast = { message ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                },
                storage = PluginStorage(pluginDataDirectory(manifest.id)),
            )
            addJavascriptInterface(PluginBridge(this, dispatcher), "ExtOSNative")
            loadUrl(contentLoader.origin + entry)
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

    override fun onResume() {
        super.onResume()
        dispatchLifecycle("extosresume")
    }

    override fun onPause() {
        dispatchLifecycle("extossuspend")
        super.onPause()
    }

    private fun destroyPluginView() {
        pluginView?.apply {
            evaluateJavascript("window.dispatchEvent(new CustomEvent('extosshutdown'))", null)
            removeJavascriptInterface("ExtOSNative")
            stopLoading()
            destroy()
        }
        pluginView = null
    }

    private fun dispatchLifecycle(event: String) {
        pluginView?.evaluateJavascript(
            "window.dispatchEvent(new CustomEvent('$event'))",
            null,
        )
    }

    private fun pluginDataDirectory(pluginId: String) = filesDir.toPath()
        .resolve("plugin-data")
        .resolve(pluginId)

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
