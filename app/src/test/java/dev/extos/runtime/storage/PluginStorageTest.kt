package dev.extos.runtime.storage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun persistsJsonValuesAcrossInstances() {
        val directory = temporaryFolder.newFolder("storage").toPath()
        PluginStorage(directory).set("profile", JSONObject().put("name", "ExtOS"))

        val value = PluginStorage(directory).get("profile") as JSONObject
        assertEquals("ExtOS", value.getString("name"))
    }

    @Test
    fun removesAndClearsValues() {
        val storage = PluginStorage(temporaryFolder.newFolder("storage").toPath())
        storage.set("one", 1)
        storage.set("two", 2)
        storage.remove("one")
        assertNull(storage.get("one"))
        storage.clear()
        assertNull(storage.get("two"))
    }

    @Test
    fun enforcesQuotaBeforeReplacingExistingData() {
        val storage = PluginStorage(temporaryFolder.newFolder("storage").toPath(), maxBytes = 32)
        storage.set("safe", "value")
        assertThrows(IllegalArgumentException::class.java) {
            storage.set("large", "x".repeat(100))
        }
        assertEquals("value", storage.get("safe"))
        assertFalse(storage.remove("missing"))
    }
}
