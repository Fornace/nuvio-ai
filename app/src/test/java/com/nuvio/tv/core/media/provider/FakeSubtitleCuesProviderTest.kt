package com.nuvio.tv.core.media.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeSubtitleCuesProviderTest {

    // 1000 Hz, mono, 16-bit little endian => 2000 bytes per second of media time.
    private val format = NormalizedPcmFormat(
        sampleRateHz = 1000,
        channelCount = 1,
        encoding = NormalizedPcmEncoding.PCM_SIGNED_16_BIT_LITTLE_ENDIAN
    )

    private val sessionIdentity = MediaTransformSessionIdentity(
        sessionId = "session-1",
        profileGeneration = 7,
        episodeContentId = "episode-42",
        audioTrackFingerprint = "track-fingerprint-1",
        generation = 5,
        initialEpoch = 0
    )

    private class RecordingCallback : SubtitleCuesCallback {
        val cues = mutableListOf<SubtitleCue>()
        var completed: Pair<Long, Long>? = null
        var cancelled: Pair<Long, Long>? = null

        override fun onCue(cue: SubtitleCue) {
            cues += cue
        }

        override fun onCompleted(generation: Long, epoch: Long) {
            completed = generation to epoch
        }

        override fun onCancelled(generation: Long, epoch: Long) {
            cancelled = generation to epoch
        }
    }

    private fun provider(
        bytesPerCue: Long = 4000L,
        inputLengthBytes: Long = 12000L,
        cueTexts: List<String> = listOf("first cue", "second cue", "third cue")
    ): FakeSubtitleCuesProvider = FakeSubtitleCuesProvider(
        providerId = MediaTransformProviderId(
            packageName = "com.example.mediatransform",
            signingCertificateSha256 = "3a1076bf45ab962d4cb21c3e4e4de64d6b2e21b3d7cd1c2b8e2f6efc8e4e2a11"
        ),
        configuration = FakeSubtitleCuesProvider.Configuration(
            bytesPerCue = bytesPerCue,
            inputLengthBytes = inputLengthBytes,
            cueTexts = cueTexts
        ),
        dispatchers = MediaProviderDispatchers(Dispatchers.Unconfined)
    )

    @Test
    fun `emits one deterministic cue per configured byte count and completes at input end`() =
        runTest {
            val fake = provider(bytesPerCue = 4000, inputLengthBytes = 12000)
            val callback = RecordingCallback()
            fake.start(format, sessionIdentity, callback)

            fake.submitPcm(ByteArray(4000))
            assertEquals(1, callback.cues.size)

            fake.submitPcm(ByteArray(8000))
            assertEquals(3, callback.cues.size)

            val cues = callback.cues
            assertEquals(listOf(1L, 2L, 3L), cues.map { it.revision })
            assertEquals(listOf(0L, 0L, 0L), cues.map { it.epoch })
            assertEquals(listOf(5L, 5L, 5L), cues.map { it.generation })
            assertEquals(listOf("first cue", "second cue", "third cue"), cues.map { it.text })
            assertTrue(cues.all { it.final })
            assertTrue(cues.all { it.endMediaTimeUs > it.startMediaTimeUs })
            assertEquals(0L, cues[0].startMediaTimeUs)
            assertEquals(2_000_000L, cues[0].endMediaTimeUs)
            assertEquals(2_000_000L, cues[1].startMediaTimeUs)
            assertEquals(4_000_000L, cues[1].endMediaTimeUs)
            assertEquals(4_000_000L, cues[2].startMediaTimeUs)
            assertEquals(6_000_000L, cues[2].endMediaTimeUs)

            assertEquals(SubtitleCuesProviderState.COMPLETED, fake.state)
            assertEquals(5L to 0L, callback.completed)

            fake.submitPcm(ByteArray(4000))
            assertEquals(3, callback.cues.size)
        }

    @Test
    fun `paused sessions emit no cues until resumed and pause does not complete the session`() =
        runTest {
            val fake = provider(bytesPerCue = 4000, inputLengthBytes = 12000)
            val callback = RecordingCallback()
            fake.start(format, sessionIdentity, callback)

            fake.pause()
            assertEquals(SubtitleCuesProviderState.PAUSED, fake.state)

            fake.submitPcm(ByteArray(8000))
            assertEquals(0, callback.cues.size)
            assertEquals(null, callback.completed)

            fake.resume()
            assertEquals(SubtitleCuesProviderState.ACTIVE, fake.state)

            // Paused input is never counted; only post-resume bytes produce cues.
            fake.submitPcm(ByteArray(8000))
            assertEquals(2, callback.cues.size)
            assertEquals(listOf(1L, 2L), callback.cues.map { it.revision })
            assertEquals(0L, callback.cues[0].startMediaTimeUs)
            assertEquals(null, callback.completed)
        }

    @Test
    fun `discontinuity increments the epoch and every cue keeps the epoch it was emitted under`() =
        runTest {
            val fake = provider(bytesPerCue = 4000, inputLengthBytes = 12000)
            val callback = RecordingCallback()
            fake.start(format, sessionIdentity, callback)

            fake.submitPcm(ByteArray(4000))
            assertEquals(1, callback.cues.size)

            fake.discontinuity(newEpoch = 3, mediaTimeUs = 10_000_000)
            fake.submitPcm(ByteArray(4000))
            fake.submitPcm(ByteArray(4000))

            val cues = callback.cues
            assertEquals(3, cues.size)
            assertEquals(0L, cues[0].epoch)
            assertEquals(listOf(3L, 3L), cues.drop(1).map { it.epoch })
            // Revisions keep increasing monotonically across the discontinuity.
            assertEquals(listOf(1L, 2L, 3L), cues.map { it.revision })
            assertEquals(listOf(5L, 5L, 5L), cues.map { it.generation })
            // New-epoch cues are timed from the discontinuity anchor, not the session start.
            assertEquals(10_000_000L, cues[1].startMediaTimeUs)
            assertEquals(12_000_000L, cues[1].endMediaTimeUs)
            assertEquals(12_000_000L, cues[2].startMediaTimeUs)
            assertEquals(14_000_000L, cues[2].endMediaTimeUs)
            assertEquals(5L to 3L, callback.completed)
        }

    @Test
    fun `a discontinuity must advance the epoch`() = runTest {
        val fake = provider()
        val callback = RecordingCallback()
        fake.start(format, sessionIdentity, callback)
        fake.submitPcm(ByteArray(4000))

        val error = runCatching { fake.discontinuity(newEpoch = 0) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(SubtitleCuesProviderState.ACTIVE, fake.state)
    }

    @Test
    fun `cancel ends the session and further input is ignored`() =
        runTest {
            val fake = provider(bytesPerCue = 4000, inputLengthBytes = 12000)
            val callback = RecordingCallback()
            fake.start(format, sessionIdentity, callback)
            fake.submitPcm(ByteArray(4000))

            fake.cancel()

            assertEquals(SubtitleCuesProviderState.CANCELLED, fake.state)
            assertEquals(5L to 0L, callback.cancelled)
            assertEquals(null, callback.completed)

            fake.submitPcm(ByteArray(8000))
            assertEquals(1, callback.cues.size)

            fake.cancel()
            assertEquals(5L to 0L, callback.cancelled)
        }

    @Test
    fun `starting a new session after cancellation resets revisions and counters`() =
        runTest {
            val fake = provider(bytesPerCue = 4000, inputLengthBytes = 8000)
            val first = RecordingCallback()
            val second = RecordingCallback()
            fake.start(format, sessionIdentity, first)
            fake.submitPcm(ByteArray(4000))
            fake.cancel()

            fake.start(
                format,
                sessionIdentity.copy(generation = 6, initialEpoch = 9),
                second
            )
            fake.submitPcm(ByteArray(8000))

            assertEquals(listOf(1L, 2L), second.cues.map { it.revision })
            assertEquals(listOf(9L, 9L), second.cues.map { it.epoch })
            assertEquals(listOf(6L, 6L), second.cues.map { it.generation })
            assertEquals(6L to 9L, second.completed)
            assertEquals(0L, second.cues[0].startMediaTimeUs)
        }

    @Test
    fun `input smaller than one cue boundary emits no premature cues`() =
        runTest {
            val fake = provider(bytesPerCue = 8000, inputLengthBytes = 16000)
            val callback = RecordingCallback()
            fake.start(format, sessionIdentity, callback)

            fake.submitPcm(ByteArray(7999))

            assertEquals(0, callback.cues.size)
            assertEquals(SubtitleCuesProviderState.ACTIVE, fake.state)

            fake.submitPcm(ByteArray(1))

            assertEquals(1, callback.cues.size)
            assertEquals(4_000_000L, callback.cues[0].endMediaTimeUs - callback.cues[0].startMediaTimeUs)
        }
}
