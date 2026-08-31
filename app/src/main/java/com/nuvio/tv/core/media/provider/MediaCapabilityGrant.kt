package com.nuvio.tv.core.media.provider

import java.time.Instant

enum class MediaTransformOperation(val capability: MediaTransformCapability) {
    GENERATE_SUBTITLE_CUES(MediaTransformCapability.SUBTITLE_CUES_V1),
    CREATE_DUB_ARTIFACT(MediaTransformCapability.DUB_ARTIFACT_V1)
}

/** A named output boundary; it must be identical at issue and consumption time. */
@JvmInline
value class MediaCapabilityDestination(val value: String) {
    init {
        require(value.isNotBlank()) { "Grant destination must not be blank" }
    }
}

data class MediaCapabilityGrantScope(
    val sessionId: String,
    val profileGeneration: Long,
    val episodeContentId: String,
    val audioTrackFingerprint: String?,
    val providerId: MediaTransformProviderId,
    val operation: MediaTransformOperation,
    val destination: MediaCapabilityDestination
) {
    init {
        require(sessionId.isNotBlank()) { "Session id must not be blank" }
        require(profileGeneration >= 0) { "Profile generation must not be negative" }
        require(episodeContentId.isNotBlank()) { "Episode/content id must not be blank" }
        require(audioTrackFingerprint == null || audioTrackFingerprint.isNotBlank()) {
            "Audio track fingerprint must be absent or non-blank"
        }
    }
}

/** Opaque one-shot value. Its text is deliberately omitted from logs and grant toString output. */
class MediaCapabilityNonce(val value: String) {
    init {
        require(value.isNotBlank()) { "Grant nonce must not be blank" }
    }

    override fun equals(other: Any?): Boolean = other is MediaCapabilityNonce && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "[redacted-media-grant-nonce]"
}

data class MediaCapabilityGrant(
    val scope: MediaCapabilityGrantScope,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val nonce: MediaCapabilityNonce
) {
    init {
        require(expiresAt.isAfter(issuedAt)) { "Grant expiry must be after issue time" }
    }
}
