package com.nuvio.tv.core.media.provider.host

import kotlinx.serialization.Serializable
import java.net.URI

/** Wire model for the version 1 vendor catalog served beside the provider registry. */
@Serializable
data class VendorCatalogDocumentDto(
    val schemaVersion: Int = 0,
    val updated: String? = null,
    val vendors: List<VendorCatalogEntryDto> = emptyList()
)

@Serializable
data class VendorCatalogEntryDto(
    val id: String = "",
    val name: String = "",
    val capability: String = "",
    val adaptor: String = "",
    val apiBase: String = "",
    val model: String = "",
    val authFields: List<String> = emptyList(),
    val keyUrl: String = "",
    val docsUrl: String = "",
    val pricingHint: String = "",
    val notes: String? = null
)

data class ParsedVendorCatalog(
    val schemaVersion: Int,
    val updated: String?,
    val vendors: List<VendorCatalogEntry>,
    val warnings: List<String>
)

/**
 * One BYOK vendor the user can pick for a capability. The catalog is data, not
 * code: swapping a model id, price hint, or console link is a registry-side
 * JSON edit, and only a new wire protocol shape needs a provider APK update
 * through its adaptor implementation.
 */
data class VendorCatalogEntry(
    val id: String,
    val name: String,
    val capability: String,
    val adaptor: String,
    val apiBase: String,
    val model: String,
    val authFields: List<String>,
    val keyUrl: String,
    val docsUrl: String,
    val pricingHint: String,
    val notes: String?
)

/** Maps the wire document without I/O. Unsupported schemas are rejected before use. */
fun VendorCatalogDocumentDto.toParsedVendorCatalog(): ParsedVendorCatalog {
    require(schemaVersion == SUPPORTED_VENDOR_CATALOG_SCHEMA) {
        "Unsupported vendor catalog schemaVersion: $schemaVersion"
    }
    val warnings = mutableListOf<String>()
    val parsed = mutableListOf<VendorCatalogEntry>()
    val seen = mutableSetOf<String>()
    for (dto in vendors) {
        val entryWarnings = dto.entryWarnings()
        when {
            entryWarnings.isNotEmpty() ->
                warnings += "vendor ${dto.id.ifBlank { "?" }}: ${entryWarnings.joinToString("; ")}"
            !seen.add("${dto.id}|${dto.capability}") ->
                warnings += "duplicate vendor id for capability: ${dto.id}"
            else -> parsed += dto.toEntry()
        }
    }
    return ParsedVendorCatalog(
        schemaVersion = schemaVersion,
        updated = updated,
        vendors = parsed,
        warnings = warnings
    )
}

private fun VendorCatalogEntryDto.toEntry() = VendorCatalogEntry(
    id = id,
    name = name,
    capability = capability,
    adaptor = adaptor,
    apiBase = apiBase,
    model = model,
    authFields = authFields,
    keyUrl = keyUrl,
    docsUrl = docsUrl,
    pricingHint = pricingHint,
    notes = notes
)

private fun VendorCatalogEntryDto.entryWarnings(): List<String> = buildList {
    for (field in listOf("id", "name", "capability", "adaptor", "model", "pricingHint")) {
        if (getField(field).isBlank()) add("$field must be a non-empty string")
    }
    if (!apiBase.isValidEndpointUrl()) add("apiBase must be a valid https or wss URL")
    if (!keyUrl.isValidEndpointUrl(httpsOnly = true)) add("keyUrl must be a valid https URL")
    if (!docsUrl.isValidEndpointUrl(httpsOnly = true)) add("docsUrl must be a valid https URL")
    if (authFields.isEmpty() || "apiKey" !in authFields) {
        add("authFields must be non-empty and include apiKey")
    }
    val allowedPlaceholders = setOf("model") + authFields
    val unknown = PLACEHOLDER.findAll(apiBase).mapNotNull { it.groupValues[1] }.toSet() - allowedPlaceholders
    if (unknown.isNotEmpty()) add("apiBase placeholder has no source: $unknown")
}

private fun VendorCatalogEntryDto.getField(key: String): String =
    when (key) {
        "id" -> id
        "name" -> this.name
        "capability" -> capability
        "adaptor" -> adaptor
        "model" -> model
        "pricingHint" -> pricingHint
        else -> ""
    }

private fun String.isValidEndpointUrl(httpsOnly: Boolean = false): Boolean {
    if (isBlank()) return false
    // Placeholder braces are illegal in java.net.URI; probe with dummies substituted.
    val probe = PLACEHOLDER.replace(this, "placeholder")
    return runCatching {
        val uri = URI(probe)
        val schemeOk = when {
            httpsOnly -> uri.scheme.equals("https", ignoreCase = true)
            else -> uri.scheme.equals("https", ignoreCase = true) ||
                uri.scheme.equals("wss", ignoreCase = true)
        }
        schemeOk && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

const val SUPPORTED_VENDOR_CATALOG_SCHEMA = 1
private val PLACEHOLDER = Regex("\\{(\\w+)\\}")
