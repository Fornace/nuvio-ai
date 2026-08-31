package com.nuvio.tv.ui.screens.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAudioDescriptorTest {

    private fun descriptor(
        generation: Long = 1L,
        targetLanguage: String = "it",
        label: String = "Translated voice (Italian)",
        mimeType: String = "audio/mp4",
        coveredRangeUs: ExternalAudioCoverageRange = ExternalAudioCoverageRange(
            startUs = 0L,
            endUs = 60_000_000L,
        ),
        completed: Boolean = true,
        sha256: String = "aa".repeat(32),
        artifactToken: String = "token-0001",
    ) = ExternalAudioDescriptor(
        origin = ExternalAudioOrigin.GENERATED_DUB,
        generation = generation,
        targetLanguage = targetLanguage,
        label = label,
        mimeType = mimeType,
        mediaTimeOriginUs = 0L,
        durationUs = 60_000_000L,
        coveredRangeUs = coveredRangeUs,
        completed = completed,
        seekable = true,
        sha256 = sha256,
        artifactToken = artifactToken,
    )

    @Test
    fun `stable key is deterministic for identical identity fields`() {
        val first = descriptor()
        val second = descriptor(artifactToken = "token-9999", label = "Altro")

        assertEquals(first.stableKey, second.stableKey)
        assertEquals(
            stableKeyFor(
                origin = ExternalAudioOrigin.GENERATED_DUB,
                generation = 1L,
                targetLanguage = "it",
                mimeType = "audio/mp4",
                sha256 = "aa".repeat(32),
            ),
            first.stableKey,
        )
        // Case folding: semantically identical casing is still one artifact identity.
        assertEquals(descriptor(targetLanguage = "IT").stableKey, descriptor().stableKey)
        assertEquals(
            descriptor(sha256 = "AA".repeat(32)).stableKey,
            descriptor(sha256 = "aa".repeat(32)).stableKey,
        )
    }

    @Test
    fun `stable key differs when target language differs`() {
        assertNotEquals(descriptor(targetLanguage = "it").stableKey, descriptor(targetLanguage = "es").stableKey)
        assertNotEquals(descriptor(targetLanguage = "it").stableKey, descriptor(targetLanguage = "it-CH").stableKey)
    }

    @Test
    fun `stable key differs when the artifact digest differs`() {
        assertNotEquals(
            descriptor(sha256 = "aa".repeat(32)).stableKey,
            descriptor(sha256 = "bb".repeat(32)).stableKey,
        )
    }

    @Test
    fun `stable key differs when generation or mime type differ`() {
        assertNotEquals(descriptor(generation = 1L).stableKey, descriptor(generation = 2L).stableKey)
        assertNotEquals(descriptor(mimeType = "audio/mp4").stableKey, descriptor(mimeType = "audio/ogg").stableKey)
    }

    @Test
    fun `stable key is a bare hash without separators or artifact token`() {
        val token = "token-4f2a9b7c"
        val key = descriptor(artifactToken = token).stableKey

        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        assertFalse(key.contains('/'))
        assertFalse(key.contains('\\'))
        assertFalse(key.contains(File.separator))
        assertFalse(key.contains(token))
        assertFalse(key.contains("nuvio"))
    }

    @Test
    fun `coverage helpers report known and unknown coverage`() {
        assertTrue(descriptor().coveredRangeUs.hasKnownCoverage)
        assertFalse(ExternalAudioCoverageRange.UNKNOWN.hasKnownCoverage)
        assertTrue(descriptor().coversDeclaredRange)
        assertFalse(
            descriptor(coveredRangeUs = ExternalAudioCoverageRange(startUs = 0L, endUs = 30_000_000L))
                .coversDeclaredRange,
        )
    }

    @Test
    fun `descriptor rejects malformed target language tags`() {
        assertThrows(IllegalArgumentException::class.java) {
            descriptor(targetLanguage = "it IT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            descriptor(targetLanguage = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            descriptor(targetLanguage = "italian!")
        }
    }

    @Test
    fun `descriptor rejects artifact tokens that look like paths`() {
        assertThrows(IllegalArgumentException::class.java) {
            descriptor(artifactToken = "../cache/dub.m4a")
        }
        assertThrows(IllegalArgumentException::class.java) {
            descriptor(artifactToken = "/data/local/tmp/dub.m4a")
        }
    }

    @Test
    fun `descriptor rejects a completed artifact without known coverage`() {
        assertThrows(IllegalArgumentException::class.java) {
            descriptor(
                coveredRangeUs = ExternalAudioCoverageRange.UNKNOWN,
                completed = true,
            )
        }
        assertEquals(
            ExternalAudioCoverageRange.UNKNOWN,
            descriptor(coveredRangeUs = ExternalAudioCoverageRange.UNKNOWN, completed = false).coveredRangeUs,
        )
    }

    @Test
    fun `descriptor rejects coverage beyond the artifact end`() {
        assertThrows(IllegalArgumentException::class.java) {
            descriptor(coveredRangeUs = ExternalAudioCoverageRange(startUs = 0L, endUs = 61_000_000L))
        }
    }
}
