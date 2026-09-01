package com.nuvio.tv.core.media.provider.host

/** Pure validation at the package, registry and Messenger trust boundary. */
class ProviderContractValidator(
    private val hostVersionCode: Int,
    private val supportedCapabilities: Map<String, Int> = SUPPORTED_CAPABILITIES,
) {
    fun validatePreflight(
        entry: ProviderRegistryEntry,
        requestedPackage: String,
        installed: InstalledProviderInfo?,
    ): ProviderContractFailure? {
        if (!entry.hasTrustedReleaseMetadata()) return ProviderContractFailure.REGISTRY_RELEASE_UNTRUSTED
        if (requestedPackage !in entry.packageNames) return ProviderContractFailure.PACKAGE_NOT_ALLOWED
        if (installed == null) return ProviderContractFailure.PACKAGE_NOT_INSTALLED
        if (!installed.signingCertSha256Set.matchesExactly(entry.signingCertSha256)) {
            return ProviderContractFailure.SIGNER_MISMATCH
        }
        if ((entry.minHostAppVersion ?: 0) > hostVersionCode) {
            return ProviderContractFailure.HOST_VERSION_TOO_OLD
        }
        return null
    }

    fun validate(
        entry: ProviderRegistryEntry,
        requestedPackage: String,
        installed: InstalledProviderInfo?,
        service: ProviderServiceDescriptor,
        payload: ProviderNegotiationPayload,
        installedAfterReply: InstalledProviderInfo? = installed,
    ): ProviderContractResult {
        validatePreflight(entry, requestedPackage, installed)?.let { return rejected(it) }
        checkNotNull(installed)
        if (service.packageName != requestedPackage) return rejected(ProviderContractFailure.PACKAGE_NAME_MISMATCH)
        if (!service.exported) return rejected(ProviderContractFailure.SERVICE_NOT_EXPORTED)
        if (service.permission != ExternalProviderContract.BIND_PERMISSION) {
            return rejected(ProviderContractFailure.SERVICE_PERMISSION_MISMATCH)
        }
        if (payload.error != null) return rejected(ProviderContractFailure.PROVIDER_REPORTED_ERROR)
        if (payload.protocol != ExternalProviderContract.PROTOCOL_VERSION) {
            return rejected(ProviderContractFailure.PROTOCOL_MISMATCH)
        }
        if (payload.providerId != entry.id) return rejected(ProviderContractFailure.PROVIDER_ID_MISMATCH)
        if (payload.packageName != requestedPackage) return rejected(ProviderContractFailure.PACKAGE_NAME_MISMATCH)
        if (payload.versionName != installed.versionName || payload.versionName != entry.version) {
            return rejected(ProviderContractFailure.VERSION_MISMATCH)
        }
        val capabilityVersion = supportedCapabilities[entry.capability]
            ?: return rejected(ProviderContractFailure.CAPABILITY_MISMATCH)
        val expectedWireCapability = "${entry.capability}@$capabilityVersion"
        if (payload.capabilities.toSet() != setOf(expectedWireCapability)) {
            return rejected(ProviderContractFailure.CAPABILITY_MISMATCH)
        }
        if (payload.engineStatus != entry.engineStatus) {
            return rejected(ProviderContractFailure.ENGINE_STATUS_MISMATCH)
        }
        if (payload.hostMinVersionCode > hostVersionCode) {
            return rejected(ProviderContractFailure.HOST_VERSION_TOO_OLD)
        }
        if (installedAfterReply != installed) return rejected(ProviderContractFailure.PACKAGE_CHANGED)

        return ProviderContractResult.Trusted(
            TrustedProviderContract(
                providerId = entry.id,
                packageName = requestedPackage,
                versionName = payload.versionName,
                capability = entry.capability,
                capabilityVersion = capabilityVersion,
                engineStatus = payload.engineStatus,
                signingCertSha256 = installed.signingCertSha256Set,
            )
        )
    }

    private fun ProviderRegistryEntry.hasTrustedReleaseMetadata(): Boolean =
        installable && !version.isNullOrBlank() && !apkUrl.isNullOrBlank() &&
            !apkSha256.isNullOrBlank() && packageNames.isNotEmpty() &&
            signingCertSha256.isNotEmpty() && !engineStatus.isNullOrBlank()

    private fun Set<String>.matchesExactly(expected: Set<String>): Boolean =
        isNotEmpty() && map(String::uppercase).toSet() == expected.map(String::uppercase).toSet()

    private fun rejected(reason: ProviderContractFailure) = ProviderContractResult.Rejected(reason)

    companion object {
        val SUPPORTED_CAPABILITIES = mapOf(
            "SUBTITLE_CUES_V1" to 1,
            "DUB_ARTIFACT_V1" to 1,
        )
    }
}

data class ProviderServiceDescriptor(
    val packageName: String,
    val className: String,
    val exported: Boolean,
    val permission: String?,
)
