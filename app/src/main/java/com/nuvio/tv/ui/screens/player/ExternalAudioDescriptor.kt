package com.nuvio.tv.ui.screens.player

import java.security.MessageDigest

/**
 * Origin of an external (non-embedded) audio track offered by the translated voice overlay.
 *
 * [GENERATED_DUB] is the only origin in the finalized-artifact pilot: audio produced by a
 * media-transform provider and attached to the player as a completed artifact.
 */
enum class ExternalAudioOrigin {
    GENERATED_DUB,
}

/**
 * Range of source media time (microseconds) for which generated audio exists.
 *
 * `startUs`/`endUs` are on the source media-time grid, not on the artifact's own timeline.
 * `endUs == 0` means coverage is not known yet, which is legal only while the artifact is
 * still being produced.
 */
data class ExternalAudioCoverageRange(
    val startUs: Long,
    val endUs: Long,
) {
    init {
        require(startUs >= 0L) { "coverage startUs must be >= 0 (was $startUs)" }
        require(endUs >= startUs) { "coverage endUs must be >= startUs ($endUs < $startUs)" }
    }

    val durationUs: Long
        get() = endUs - startUs

    /** Coverage is only usable once at least one microsecond past the origin is known. */
    val hasKnownCoverage: Boolean
        get() = endUs > 0L

    companion object {
        /** Coverage is unknown: use while a provider is still generating the artifact. */
        val UNKNOWN = ExternalAudioCoverageRange(startUs = 0L, endUs = 0L)
    }
}

/**
 * Immutable description of one external generated-audio artifact.
 *
 * The descriptor is the shared host currency between the provider session, the loopback
 * artifact server and the player adapter. It carries everything the player needs to attach,
 * label and align the track, and nothing about *where* the bytes live: [artifactToken] is an
 * opaque server token, never a filesystem path, URL or credential.
 *
 * [stableKey] is derived, not free-form: it is the SHA-256 of origin, generation, target
 * language, MIME type and artifact digest, so the same artifact always maps to the same
 * generated track identity across engine switches and rebuilds.
 */
data class ExternalAudioDescriptor(
    val origin: ExternalAudioOrigin = ExternalAudioOrigin.GENERATED_DUB,
    val generation: Long,
    val targetLanguage: String,
    val label: String,
    val mimeType: String,
    val mediaTimeOriginUs: Long,
    val durationUs: Long,
    val coveredRangeUs: ExternalAudioCoverageRange,
    val completed: Boolean,
    val seekable: Boolean,
    val sha256: String,
    val artifactToken: String,
) {
    init {
        require(generation >= 0L) { "generation must be >= 0 (was $generation)" }
        require(REGEX_LANGUAGE.matches(targetLanguage)) {
            "targetLanguage must be a simple BCP-47 tag (was '$targetLanguage')"
        }
        require(label.isNotBlank()) { "label must not be blank" }
        require(REGEX_MIME.matches(mimeType)) {
            "mimeType must look like type/subtype (was '$mimeType')"
        }
        require(mediaTimeOriginUs >= 0L) { "mediaTimeOriginUs must be >= 0 (was $mediaTimeOriginUs)" }
        require(durationUs >= 0L) { "durationUs must be >= 0 (was $durationUs)" }
        require(REGEX_SHA256.matches(sha256)) {
            "sha256 must be 64 hex characters (was '$sha256')"
        }
        require(REGEX_TOKEN.matches(artifactToken)) {
            "artifactToken must be an opaque non-path token (was '$artifactToken')"
        }
        if (durationUs > 0L) {
            require(coveredRangeUs.endUs <= mediaTimeOriginUs + durationUs) {
                "coveredRangeUs.endUs ${coveredRangeUs.endUs} exceeds the artifact end " +
                    "${mediaTimeOriginUs + durationUs}"
            }
        }
        if (completed) {
            require(coveredRangeUs.hasKnownCoverage) {
                "a completed descriptor must declare known coverage (coveredRangeUs.endUs > 0)"
            }
        }
    }

    /** Stable generated-track identity; derived by [stableKeyFor]. */
    val stableKey: String
        get() = stableKeyFor(
            origin = origin,
            generation = generation,
            targetLanguage = targetLanguage,
            mimeType = mimeType,
            sha256 = sha256,
        )

    /** Source media time (microseconds) at which the artifact ends; 0 while duration is unknown. */
    val artifactEndUs: Long
        get() = if (durationUs > 0L) mediaTimeOriginUs + durationUs else 0L

    /** True when the artifact claims full coverage of its declared media-time span. */
    val coversDeclaredRange: Boolean
        get() = coveredRangeUs.hasKnownCoverage &&
            coveredRangeUs.startUs <= mediaTimeOriginUs &&
            (artifactEndUs == 0L || coveredRangeUs.endUs >= artifactEndUs)

    private companion object {
        private val REGEX_LANGUAGE = Regex("""[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*""")
        private val REGEX_MIME = Regex(
            """[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]{0,126}/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]{0,126}""",
        )
        private val REGEX_SHA256 = Regex("""[0-9a-fA-F]{64}""")
        private val REGEX_TOKEN = Regex("""[A-Za-z0-9][A-Za-z0-9._~-]{7,255}""")
    }
}

/**
 * Derive the stable generated-track key for an artifact.
 *
 * Only identity-bearing fields participate: origin, generation, target language, MIME type
 * and the artifact digest. Presentation-only fields (label), timing fields and the artifact
 * token deliberately do not, so the key can never leak a token or a path and never changes
 * because coverage grew.
 *
 * Language, MIME type and digest are case-folded before hashing, so semantically equivalent
 * casing still produces one identity per artifact. The returned key is the lowercase SHA-256
 * digest only; none of the input strings are exposed in it.
 */
fun stableKeyFor(
    origin: ExternalAudioOrigin,
    generation: Long,
    targetLanguage: String,
    mimeType: String,
    sha256: String,
): String {
    val canonical = buildString {
        append(origin.name)
        append('|').append(generation)
        append('|').append(targetLanguage.trim().lowercase())
        append('|').append(mimeType.trim().lowercase())
        append('|').append(sha256.trim().lowercase())
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        for (byte in digest) {
            append(HEX[(byte.toInt() shr 4) and 0xF])
            append(HEX[byte.toInt() and 0xF])
        }
    }
}

private val HEX = "0123456789abcdef".toCharArray()
