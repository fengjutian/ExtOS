package dev.extos.runtime.security

import android.content.Context

class PublisherTrustStore(context: Context) {
    private val preferences = context.getSharedPreferences("plugin-publisher-keys", Context.MODE_PRIVATE)

    fun requireCompatible(pluginId: String, publisherKeyId: String?) {
        val trustedKey = preferences.getString(pluginId, null) ?: return
        require(publisherKeyId == trustedKey) {
            "Plugin update is not signed by the previously accepted publisher key"
        }
    }

    fun remember(pluginId: String, publisherKeyId: String?) {
        if (publisherKeyId != null) {
            check(preferences.edit().putString(pluginId, publisherKeyId).commit()) {
                "Could not persist publisher key continuity"
            }
        }
    }

    fun remove(pluginId: String) {
        preferences.edit().remove(pluginId).commit()
    }
}
