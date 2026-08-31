package com.nuvio.tv.core.media.provider.host

import kotlinx.serialization.Serializable
import java.net.URI

/** Wire model for version 1 of the provider registry. All fields have safe defaults. */
@Serializable
data class ProviderRegistryDocumentDto(
    val schemaVersion: Int = 0,
    val generatedAt: String? = null,
    val registry: String? = null,
    val providers: List<ProviderRegistryProviderDto> = emptyList()
)

@Serializable
data class ProviderRegistryProviderDto(
    val id: String = "",
    val name: String = "",
    val capability: String = "",
    val status: String = "",
    val installable: Boolean = false,
    val description: String? = null,
    val release: ProviderReleaseDto? = null,
    val documentation: String? = null
)

@Serializable
data class ProviderReleaseDto(
    val version: String? = null,
    val apkUrl: String? = null,
    val apkSha256: String? = null,
    val minHostAppVersion: Int? = null,
    val packageNames: List<String> = emptyList(),
    val signingCertSha256: List<String> = emptyList(),
    val engineStatus: String? = null
)

data class ParsedProviderRegistry(
    val schemaVersion: Int,
    val generatedAt: String?,
    val registryUrl: String?,
    val providers: List<ProviderRegistryEntry>,
    val warnings: List<ProviderRegistryWarning>
)

data class ProviderRegistryEntry(
    val id: String,
    val name: String,
    val capability: String,
    val status: String,
    val installable: Boolean,
    val description: String?,
    val version: String?,
    val apkUrl: String?,
    val apkSha256: String?,
    val minHostAppVersion: Int?,
    val packageNames: List<String>,
    val signingCertSha256: Set<String>,
    val engineStatus: String?,
    val documentation: String?,
    val parseWarnings: List<String>
)

data class ProviderRegistryWarning(
    val providerId: String,
    val message: String
)

/** Maps the wire document without I/O. Unsupported schemas are rejected before use. */
fun ProviderRegistryDocumentDto.toParsedRegistry(): ParsedProviderRegistry {
    require(schemaVersion == SUPPORTED_PROVIDER_REGISTRY_SCHEMA) {
        "Unsupported provider registry schemaVersion: $schemaVersion"
    }

    val entries = providers.map(ProviderRegistryProviderDto::toParsedEntry)
    return ParsedProviderRegistry(
        schemaVersion = schemaVersion,
        generatedAt = generatedAt,
        registryUrl = registry,
        providers = entries,
        warnings = entries.flatMap { entry ->
            entry.parseWarnings.map { ProviderRegistryWarning(entry.id, it) }
        }
    )
}

fun ProviderRegistryProviderDto.toParsedEntry(): ProviderRegistryEntry {
    val warnings = if (installable) validateInstallableRelease(release) else emptyList()
    val parsedRelease = release
    return ProviderRegistryEntry(
        id = id,
        name = name,
        capability = capability,
        status = status,
        installable = installable && warnings.isEmpty(),
        description = description,
        version = parsedRelease?.version,
        apkUrl = parsedRelease?.apkUrl,
        apkSha256 = parsedRelease?.apkSha256,
        minHostAppVersion = parsedRelease?.minHostAppVersion,
        packageNames = parsedRelease?.packageNames.orEmpty(),
        signingCertSha256 = parsedRelease?.signingCertSha256.orEmpty().toSet(),
        engineStatus = parsedRelease?.engineStatus,
        documentation = documentation,
        parseWarnings = warnings
    )
}

private fun validateInstallableRelease(release: ProviderReleaseDto?): List<String> {
    if (release == null) return listOf("installable provider has no release")
    return buildList {
        if (!release.apkUrl.isValidArtifactUrl()) {
            add("installable release has no valid HTTPS apkUrl")
        }
        if (release.apkSha256?.matches(LOWERCASE_SHA256) != true) {
            add("installable release apkSha256 must be 64 lowercase hexadecimal characters")
        }
        if (release.packageNames.isEmpty() || release.packageNames.any(String::isBlank)) {
            add("installable release packageNames must be non-empty")
        }
        if (release.signingCertSha256.isEmpty() ||
            release.signingCertSha256.any { !it.matches(UPPERCASE_SHA256) }
        ) {
            add("installable release signingCertSha256 values must be 64 uppercase hexadecimal characters")
        }
    }
}

private fun String?.isValidArtifactUrl(): Boolean {
    if (isNullOrBlank()) return false
    return runCatching {
        val uri = URI(this)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

const val SUPPORTED_PROVIDER_REGISTRY_SCHEMA = 1
private val LOWERCASE_SHA256 = Regex("[0-9a-f]{64}")
private val UPPERCASE_SHA256 = Regex("[0-9A-F]{64}")
