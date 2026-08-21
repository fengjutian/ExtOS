package dev.extos.runtime.security

import android.content.Context

class CapabilityGrantStore(context: Context) {
    private val preferences = context.getSharedPreferences("plugin-capability-grants", Context.MODE_PRIVATE)

    fun grantsFor(pluginId: String): Set<String> =
        preferences.getStringSet(pluginId, emptySet())?.toSet() ?: emptySet()

    fun replace(pluginId: String, capabilities: Set<String>) {
        preferences.edit().putStringSet(pluginId, capabilities.toSet()).apply()
    }

    fun remove(pluginId: String) {
        preferences.edit().remove(pluginId).apply()
    }
}
