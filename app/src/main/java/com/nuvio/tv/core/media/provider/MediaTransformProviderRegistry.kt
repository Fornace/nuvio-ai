package com.nuvio.tv.core.media.provider

/** Supplies the current installed signer set. An empty set means the package is unknown. */
fun interface PackageIdentitySource {
    fun signingCertificateSha256Digests(packageName: String): Set<String>
}

enum class MediaProviderRejectionReason {
    UNKNOWN_IDENTITY,
    WRONG_SIGNER,
    UNSUPPORTED_CAPABILITY,
    VERSION_MISMATCH,
    CONTRACT_MISMATCH
}

class MediaProviderRejectedException(
    val reason: MediaProviderRejectionReason,
    message: String
) : IllegalStateException(message)

/**
 * Process-local registry for typed native providers.
 *
 * Identity is rechecked both when registering and resolving so an install/signature change cannot
 * keep using a stale registration. A capability is usable only when its declared version equals
 * the version compiled into [MediaTransformCapability].
 */
class MediaTransformProviderRegistry(
    private val packageIdentitySource: PackageIdentitySource
) {
    private val providers = linkedMapOf<MediaTransformProviderId, MediaTransformProvider>()

    @Synchronized
    fun register(provider: MediaTransformProvider): MediaTransformProviderRegistry {
        verifyIdentity(provider.providerId)
        providers[provider.providerId] = provider
        return this
    }

    @Synchronized
    fun unregister(providerId: MediaTransformProviderId) {
        providers.remove(providerId)
    }

    @Synchronized
    fun resolve(
        providerId: MediaTransformProviderId,
        capability: MediaTransformCapability
    ): MediaTransformProvider {
        verifyIdentity(providerId)
        val provider = providers[providerId] ?: reject(
            MediaProviderRejectionReason.UNKNOWN_IDENTITY,
            "No registered provider for ${providerId.displayName}"
        )
        val declaredVersion = provider.capabilities[capability] ?: reject(
            MediaProviderRejectionReason.UNSUPPORTED_CAPABILITY,
            "${providerId.displayName} does not declare $capability"
        )
        if (declaredVersion != capability.version) {
            reject(
                MediaProviderRejectionReason.VERSION_MISMATCH,
                "$capability requires exact version ${capability.version}, provider declared $declaredVersion"
            )
        }
        return provider
    }

    fun resolveSubtitleCues(providerId: MediaTransformProviderId): SubtitleCuesProvider {
        val provider = resolve(providerId, MediaTransformCapability.SUBTITLE_CUES_V1)
        return provider as? SubtitleCuesProvider ?: reject(
            MediaProviderRejectionReason.CONTRACT_MISMATCH,
            "${providerId.displayName} declares subtitle cues but does not implement its typed contract"
        )
    }

    private fun verifyIdentity(providerId: MediaTransformProviderId) {
        val installedDigests = packageIdentitySource
            .signingCertificateSha256Digests(providerId.packageName)
            .map(::normalizeDigest)
            .toSet()
        if (installedDigests.isEmpty()) {
            reject(
                MediaProviderRejectionReason.UNKNOWN_IDENTITY,
                "Provider package ${providerId.packageName} is not installed or has no signer"
            )
        }
        if (providerId.signingCertificateSha256 !in installedDigests) {
            reject(
                MediaProviderRejectionReason.WRONG_SIGNER,
                "Signing certificate does not match ${providerId.packageName}"
            )
        }
    }

    private fun normalizeDigest(digest: String): String = digest.replace(":", "").lowercase()

    private fun reject(reason: MediaProviderRejectionReason, message: String): Nothing {
        throw MediaProviderRejectedException(reason, message)
    }
}
