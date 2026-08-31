package com.nuvio.tv.ui.screens.player

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DubPlaybackCoordinatorTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC)

    private fun coordinator() = DubPlaybackCoordinator(clock)

    private fun descriptor(
        generation: Long = 1L,
        coveredRangeUs: ExternalAudioCoverageRange = ExternalAudioCoverageRange(
            startUs = 0L,
            endUs = 60_000_000L,
        ),
        completed: Boolean = true,
    ) = ExternalAudioDescriptor(
        origin = ExternalAudioOrigin.GENERATED_DUB,
        generation = generation,
        targetLanguage = "it",
        label = "Translated voice (Italian)",
        mimeType = "audio/mp4",
        mediaTimeOriginUs = 0L,
        durationUs = 60_000_000L,
        coveredRangeUs = coveredRangeUs,
        completed = completed,
        seekable = true,
        sha256 = "aa".repeat(32),
        artifactToken = "token-0001",
    )

    /** Start and confirm attachment, i.e. the happy path up to Ready. */
    private fun readyCoordinator(generation: Long = 1L): DubPlaybackCoordinator {
        val coordinator = coordinator()
        val descriptor = descriptor(generation = generation)
        coordinator.start(descriptor)
        coordinator.attachSucceeded(descriptor.stableKey)
        return coordinator
    }

    @Test
    fun `happy path walks idle to preparing to ready`() {
        val coordinator = coordinator()
        val descriptor = descriptor()

        assertEquals(DubPlaybackState.Idle, coordinator.state)
        assertFalse(coordinator.isActive)
        assertEquals(DubPlaybackState.Preparing, coordinator.start(descriptor))
        assertEquals(descriptor, coordinator.currentDescriptor)
        assertEquals(1L, coordinator.currentGeneration)
        assertEquals(DubAudioSelection.OriginalAudio, coordinator.selectedAudio)

        assertEquals(DubPlaybackState.Ready, coordinator.attachSucceeded(descriptor.stableKey))
        assertEquals(DubPlaybackState.Ready, coordinator.state)
        assertEquals(DubAudioSelection.GeneratedDub, coordinator.selectedAudio)
        assertEquals(descriptor.stableKey, coordinator.currentDescriptorKey)
        assertTrue(coordinator.lastEvent?.accepted == true)
    }

    @Test
    fun `attach failure routes to original fallback with a reason`() {
        val coordinator = coordinator()
        val descriptor = descriptor()
        coordinator.start(descriptor)

        assertEquals(
            DubPlaybackState.OriginalFallback,
            coordinator.attachFailed(reason = "artifact 404"),
        )
        assertEquals(DubPlaybackState.OriginalFallback, coordinator.state)
        assertEquals(DubAudioSelection.OriginalAudio, coordinator.selectedAudio)
        assertEquals("artifact 404", coordinator.fallbackReason)
        assertFalse(coordinator.userRequestedOriginal)
        // The descriptor survives a non-terminal failure so Retry can re-prepare it.
        assertEquals(descriptor, coordinator.currentDescriptor)
    }

    @Test
    fun `terminal attach failure routes to failed`() {
        val coordinator = coordinator()
        coordinator.start(descriptor())
        coordinator.attachFailed(reason = "provider refused", terminal = true)

        assertEquals(DubPlaybackState.Failed, coordinator.state)
        assertEquals(DubAudioSelection.OriginalAudio, coordinator.selectedAudio)
        assertEquals("provider refused", coordinator.fallbackReason)
        assertEquals("provider refused", coordinator.lastErrorReason)
    }

    @Test
    fun `recoverable provider error recovers back to ready`() {
        val coordinator = readyCoordinator()

        assertEquals(
            DubPlaybackState.Recovering,
            coordinator.providerError(recoverable = true, reason = "socket reset"),
        )
        assertEquals(DubPlaybackState.Recovering, coordinator.state)
        // Playback continues on original audio while recovering.
        assertEquals(DubAudioSelection.OriginalAudio, coordinator.selectedAudio)
        assertNull(coordinator.fallbackReason)
        assertEquals("socket reset", coordinator.lastErrorReason)

        assertEquals(DubPlaybackState.Ready, coordinator.providerRecovered())
        assertEquals(DubAudioSelection.GeneratedDub, coordinator.selectedAudio)
    }

    @Test
    fun `non recoverable provider error falls back to original with original audio selectable`() {
        val coordinator = readyCoordinator()

        assertEquals(
            DubPlaybackState.OriginalFallback,
            coordinator.providerError(recoverable = false, reason = "quota exceeded"),
        )
        assertEquals(DubPlaybackState.OriginalFallback, coordinator.state)
        assertEquals(DubAudioSelection.OriginalAudio, coordinator.selectedAudio)
        assertEquals("quota exceeded", coordinator.fallbackReason)
        // Original audio stays selectable and the descriptor is retained for diagnostics.
        assertNotNull(coordinator.currentDescriptor)
        // Recovery is not offered after a non-recoverable fault.
        assertEquals(DubPlaybackState.OriginalFallback, coordinator.providerRecovered())
    }

    @Test
    fun `seek during ready returns to preparing and rejects stale generation callbacks`() {
        val coordinator = readyCoordinator(generation = 7L)
        val key = coordinator.currentDescriptorKey!!
        assertEquals(7L, coordinator.currentGeneration)

        assertEquals(DubPlaybackState.Preparing, coordinator.seekCommitted(newGeneration = 8L))
        assertEquals(DubPlaybackState.Preparing, coordinator.state)
        assertEquals(8L, coordinator.currentGeneration)
        assertEquals(DubAudioSelection.OriginalAudio, coordinator.selectedAudio)
        assertNull(coordinator.coveredEndUs)
        // The seek re-keys the descriptor, so even the correct generation with the pre-seek
        // key cannot re-enter the session.
        assertEquals(8L, coordinator.currentDescriptor?.generation)
        val reKeyed = coordinator.currentDescriptorKey!!
        assertNotEquals(key, reKeyed)

        // A callback tagged with the invalidated generation is rejected, not applied.
        assertFalse(coordinator.callbackReceived(key, generation = 7L))
        assertFalse(coordinator.callbackReceived(key, generation = 8L))
        assertEquals(2, coordinator.rejectedCallbackCount)
        val rejected = coordinator.lastRejectedCallback
        assertNotNull(rejected)
        assertEquals(8L, rejected?.generation)
        assertTrue(rejected?.reason?.contains("descriptor key") == true)

        // The current key and generation are accepted, and Ready can be reconfirmed.
        assertTrue(coordinator.callbackReceived(reKeyed, generation = 8L))
        assertEquals(2, coordinator.rejectedCallbackCount)
        assertEquals(DubPlaybackState.Ready, coordinator.attachSucceeded(reKeyed))
        assertEquals(DubPlaybackState.Ready, coordinator.attachSucceeded(key))
        assertEquals(1, coordinator.ignoredEventCount)
    }

    @Test
    fun `seek must advance the generation and needs an active session`() {
        val coordinator = readyCoordinator(generation = 3L)
        assertEquals(DubPlaybackState.Ready, coordinator.seekCommitted(newGeneration = 3L))
        assertEquals(DubPlaybackState.Ready, coordinator.seekCommitted(newGeneration = 2L))
        assertEquals(2, coordinator.ignoredEventCount)
        assertEquals(3L, coordinator.currentGeneration)

        val idle = coordinator()
        assertEquals(DubPlaybackState.Idle, idle.seekCommitted(newGeneration = 4L))
        assertEquals(DubPlaybackState.Idle, idle.state)
        assertEquals(1, idle.ignoredEventCount)
    }

    @Test
    fun `ready is unreachable without completed coverage information`() {
        val coordinator = coordinator()
        val descriptor = descriptor(
            coveredRangeUs = ExternalAudioCoverageRange.UNKNOWN,
            completed = false,
        )
        coordinator.start(descriptor)

        assertEquals(DubPlaybackState.Preparing, coordinator.attachSucceeded(descriptor.stableKey))
        assertEquals(DubPlaybackState.Preparing, coordinator.state)
        assertEquals(DubAudioSelection.OriginalAudio, coordinator.selectedAudio)
        assertTrue(coordinator.lastEvent?.accepted == false)
        assertTrue(coordinator.lastEvent?.reason?.contains("coveredRangeUs") == true)
    }

    @Test
    fun `use original audio is a user action distinct from failure fallback`() {
        val userChoice = readyCoordinator()
        assertEquals(DubPlaybackState.OriginalFallback, userChoice.useOriginalAudio())
        assertEquals(DubPlaybackState.OriginalFallback, userChoice.state)
        assertTrue(userChoice.userRequestedOriginal)
        assertNull(userChoice.fallbackReason)
        assertNull(userChoice.lastErrorReason)
        assertEquals(DubAudioSelection.OriginalAudio, userChoice.selectedAudio)
        assertFalse(userChoice.state == DubPlaybackState.Failed)
        assertFalse(userChoice.state == DubPlaybackState.Recovering)

        val failure = readyCoordinator()
        failure.providerError(recoverable = false, reason = "decoder error")
        assertEquals(DubPlaybackState.OriginalFallback, failure.state)
        assertFalse(failure.userRequestedOriginal)
        assertEquals("decoder error", failure.fallbackReason)
        assertFalse(failure.state == DubPlaybackState.Failed)
        assertFalse(failure.state == DubPlaybackState.Recovering)
    }

    @Test
    fun `stop returns to idle and clears the session`() {
        val coordinator = readyCoordinator(generation = 5L)

        assertEquals(DubPlaybackState.Idle, coordinator.stop())
        assertEquals(DubPlaybackState.Idle, coordinator.state)
        assertNull(coordinator.currentDescriptor)
        assertNull(coordinator.currentGeneration)
        assertNull(coordinator.currentDescriptorKey)
        assertNull(coordinator.coveredEndUs)
        assertNull(coordinator.fallbackReason)
        assertFalse(coordinator.userRequestedOriginal)
        assertFalse(coordinator.isActive)

        // Stopping again is still legal and idempotent.
        assertEquals(DubPlaybackState.Idle, coordinator.stop())
    }

    @Test
    fun `illegal events are ignored and reported instead of throwing`() {
        val coordinator = coordinator()
        val descriptor = descriptor()

        assertEquals(DubPlaybackState.Idle, coordinator.attachSucceeded(descriptor.stableKey))
        assertEquals(DubPlaybackState.Idle, coordinator.attachFailed(reason = "late"))
        assertEquals(DubPlaybackState.Idle, coordinator.attachFailed(reason = "late", terminal = true))
        assertEquals(DubPlaybackState.Idle, coordinator.seekCommitted(newGeneration = 2L))
        assertEquals(DubPlaybackState.Idle, coordinator.providerProgress(coveredEndUs = 1_000L))
        assertEquals(DubPlaybackState.Idle, coordinator.providerError(recoverable = true))
        assertEquals(DubPlaybackState.Idle, coordinator.providerRecovered())
        assertEquals(DubPlaybackState.Idle, coordinator.useOriginalAudio())
        assertEquals(DubPlaybackState.Idle, coordinator.state)
        assertEquals(8, coordinator.ignoredEventCount)
        assertTrue(coordinator.lastEvent?.accepted == false)
        assertEquals("useOriginalAudio", coordinator.lastEvent?.event)
        assertTrue(coordinator.lastEvent?.reason?.contains("no active dub session") == true)

        // Starting twice is also illegal: the session is already preparing.
        val preparing = coordinator()
        preparing.start(descriptor)
        assertEquals(DubPlaybackState.Preparing, preparing.start(descriptor(generation = 2L)))
        assertEquals(DubPlaybackState.Preparing, preparing.state)
        assertEquals(1L, preparing.currentGeneration)
        assertEquals(1, preparing.ignoredEventCount)
    }

    @Test
    fun `restart from failed accepts the retry descriptor`() {
        val coordinator = coordinator()
        val descriptor = descriptor(generation = 4L)
        coordinator.start(descriptor)
        coordinator.attachFailed(reason = "terminal", terminal = true)
        assertEquals(DubPlaybackState.Failed, coordinator.state)

        // A retry of the same artifact is legal: the descriptor (and its generation) is
        // whatever the session coordinator hands over for the new attempt.
        assertEquals(DubPlaybackState.Preparing, coordinator.start(descriptor))
        assertEquals(DubPlaybackState.Preparing, coordinator.state)
        assertEquals(4L, coordinator.currentGeneration)
        assertEquals(descriptor.stableKey, coordinator.currentDescriptorKey)
        assertNull(coordinator.fallbackReason)
        assertEquals(DubPlaybackState.Ready, coordinator.attachSucceeded(descriptor.stableKey))
    }

    @Test
    fun `callbacks with a foreign descriptor key are rejected`() {
        val coordinator = readyCoordinator(generation = 2L)

        assertFalse(coordinator.callbackReceived("not-the-attached-key", generation = 2L))
        assertFalse(coordinator.callbackReceived(null, generation = 2L))
        assertEquals(2, coordinator.rejectedCallbackCount)
        assertTrue(coordinator.callbackReceived(coordinator.currentDescriptorKey, generation = 2L))
        assertEquals(2, coordinator.rejectedCallbackCount)

        coordinator.stop()
        assertFalse(coordinator.callbackReceived(coordinator.currentDescriptorKey, generation = 2L))
        assertEquals(3, coordinator.rejectedCallbackCount)
    }

    @Test
    fun `provider progress tracks coverage forward only`() {
        val coordinator = readyCoordinator()
        assertNull(coordinator.coveredEndUs)

        assertEquals(DubPlaybackState.Ready, coordinator.providerProgress(coveredEndUs = 12_000_000L))
        assertEquals(12_000_000L, coordinator.coveredEndUs)
        assertEquals(DubPlaybackState.Ready, coordinator.providerProgress(coveredEndUs = 15_000_000L))
        assertEquals(15_000_000L, coordinator.coveredEndUs)

        // A regression is reported and ignored; the watermark never moves backwards.
        assertEquals(DubPlaybackState.Ready, coordinator.providerProgress(coveredEndUs = 1_000_000L))
        assertEquals(15_000_000L, coordinator.coveredEndUs)
        assertEquals(1, coordinator.ignoredEventCount)

        assertEquals(
            DubPlaybackState.Recovering,
            coordinator.providerError(recoverable = true, reason = "reconnect"),
        )
        assertEquals(
            DubPlaybackState.Recovering,
            coordinator.providerProgress(coveredEndUs = 20_000_000L),
        )
        assertEquals(20_000_000L, coordinator.coveredEndUs)
        assertEquals(DubPlaybackState.Ready, coordinator.providerRecovered())
    }

    @Test
    fun `snapshot mirrors the coordinator state for the ui`() {
        val coordinator = readyCoordinator(generation = 9L)
        coordinator.providerProgress(coveredEndUs = 42_000_000L)

        val snapshot = coordinator.snapshot()
        assertEquals(DubPlaybackState.Ready, snapshot.state)
        assertEquals(coordinator.currentDescriptor, snapshot.descriptor)
        assertEquals(9L, snapshot.generation)
        assertEquals(42_000_000L, snapshot.coveredEndUs)
        assertEquals(DubAudioSelection.GeneratedDub, snapshot.selectedAudio)
        assertFalse(snapshot.userRequestedOriginal)
        assertNull(snapshot.fallbackReason)
    }
}
