package com.nuvio.tv.core.media.provider.host

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Non-secret per-provider vendor choice (vendor id plus any auxiliary auth fields). */
data class VendorSelection(
    val vendorId: String,
    val auxFields: Map<String, String> = emptyMap(),
)

/** Persists vendor selections outside the encrypted vault; they contain no secrets. */
interface VendorSelectionStore {
    fun load(providerId: String): VendorSelection?
    fun save(providerId: String, selection: VendorSelection)
    fun clear(providerId: String)
}

/**
 * Credential envelope stored in the vault: one JSON document carrying the
 * vendor id, the API key and any auxiliary auth fields, so the provider engine
 * receives everything needed to call its vendor in one sealed payload.
 */
object CredentialEnvelope {
    const val KEY_VENDOR_ID = "vendorId"
    const val KEY_API_KEY = "apiKey"

    fun build(vendorId: String, apiKey: CharArray, auxFields: Map<String, String>): ByteArray {
        require(vendorId.isNotBlank()) { "vendorId must not be blank" }
        require(apiKey.isNotEmpty()) { "apiKey must not be empty" }
        val document = JSONObject()
        document.put(KEY_VENDOR_ID, vendorId)
        document.put(KEY_API_KEY, String(apiKey))
        auxFields.forEach { (key, value) ->
            require(key.isNotBlank() && key != KEY_VENDOR_ID && key != KEY_API_KEY) {
                "reserved aux field: $key"
            }
            document.put(key, value)
        }
        return document.toString().toByteArray(Charsets.UTF_8)
    }
}

/**
 * Device-local JSON file under no restrictive permissions, holding only
 * non-secret vendor choices. One JSON object keyed by provider id.
 */
class AndroidVendorSelectionStore(context: Context) : VendorSelectionStore {

    private val file: File = File(context.filesDir, FILE_NAME)

    @Synchronized
    override fun load(providerId: String): VendorSelection? {
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val entry = root.optJSONObject(providerId) ?: return null
            val vendorId = entry.optString(KEY_VENDOR_ID)
            if (vendorId.isBlank()) return null
            val aux = mutableMapOf<String, String>()
            val auxRoot = entry.optJSONObject(KEY_AUX)
            if (auxRoot != null) {
                val keys = auxRoot.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    aux[key] = auxRoot.optString(key)
                }
            }
            VendorSelection(vendorId = vendorId, auxFields = aux)
        }.getOrNull()
    }

    @Synchronized
    override fun save(providerId: String, selection: VendorSelection) {
        val entry = JSONObject()
            .put(KEY_VENDOR_ID, selection.vendorId)
        if (selection.auxFields.isNotEmpty()) {
            val aux = JSONObject()
            selection.auxFields.forEach { (key, value) -> aux.put(key, value) }
            entry.put(KEY_AUX, aux)
        }
        mutateRoot { root -> root.put(providerId, entry) }
    }

    @Synchronized
    override fun clear(providerId: String) {
        mutateRoot { root -> root.remove(providerId) }
    }

    private fun mutateRoot(block: (JSONObject) -> Unit) {
        val root = runCatching {
            if (file.exists()) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
        }.getOrDefault(JSONObject())
        block(root)
        file.parentFile?.mkdirs()
        file.writeText(root.toString(), Charsets.UTF_8)
    }

    companion object {
        private const val FILE_NAME = "provider_vendor_selections.json"
        private const val KEY_VENDOR_ID = "vendorId"
        private const val KEY_AUX = "aux"
    }
}
