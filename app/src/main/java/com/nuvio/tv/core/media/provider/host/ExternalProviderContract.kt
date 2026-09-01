package com.nuvio.tv.core.media.provider.host

/** Version 1 of the signature-protected Messenger contract shared with provider APKs. */
object ExternalProviderContract {
    const val BIND_ACTION = "com.nuvio.tv.provider.BIND"
    const val BIND_CATEGORY = "android.intent.category.DEFAULT"
    const val BIND_PERMISSION = "com.nuvio.tv.permission.BIND_MEDIA_PROVIDER"

    const val MSG_NEGOTIATE = 1
    const val PROTOCOL_VERSION = 1

    const val KEY_PROTOCOL = "protocol"
    const val KEY_PROVIDER_ID = "providerId"
    const val KEY_PACKAGE_NAME = "packageName"
    const val KEY_VERSION_NAME = "versionName"
    const val KEY_CAPABILITIES = "capabilities"
    const val KEY_ENGINE_STATUS = "engineStatus"
    const val KEY_HOST_MIN_VERSION_CODE = "hostMinVersionCode"
    const val KEY_ERROR = "error"
}

data class ProviderNegotiationPayload(
    val protocol: Int,
    val providerId: String,
    val packageName: String,
    val versionName: String,
    val capabilities: List<String>,
    val engineStatus: String,
    val hostMinVersionCode: Int,
    val error: String? = null,
)

data class TrustedProviderContract(
    val providerId: String,
    val packageName: String,
    val versionName: String,
    val capability: String,
    val capabilityVersion: Int,
    val engineStatus: String,
    val signingCertSha256: Set<String>,
)

sealed interface ProviderContractResult {
    data class Trusted(val contract: TrustedProviderContract) : ProviderContractResult
    data class Rejected(val reason: ProviderContractFailure) : ProviderContractResult
}

enum class ProviderContractFailure {
    REGISTRY_RELEASE_UNTRUSTED,
    PACKAGE_NOT_ALLOWED,
    PACKAGE_NOT_INSTALLED,
    SIGNER_MISMATCH,
    HOST_VERSION_TOO_OLD,
    SERVICE_NOT_FOUND,
    MULTIPLE_SERVICES,
    SERVICE_NOT_EXPORTED,
    SERVICE_PERMISSION_MISMATCH,
    BIND_REJECTED,
    BIND_TIMEOUT,
    BINDER_DIED,
    REMOTE_ERROR,
    PROVIDER_REPORTED_ERROR,
    PROTOCOL_MISMATCH,
    PROVIDER_ID_MISMATCH,
    PACKAGE_NAME_MISMATCH,
    VERSION_MISMATCH,
    CAPABILITY_MISMATCH,
    ENGINE_STATUS_MISMATCH,
    PACKAGE_CHANGED,
}

interface ProviderContractClient {
    suspend fun negotiate(
        registryEntry: ProviderRegistryEntry,
        packageName: String,
    ): ProviderContractResult
}
