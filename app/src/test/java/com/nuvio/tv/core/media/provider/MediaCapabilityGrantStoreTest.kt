package com.nuvio.tv.core.media.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MediaCapabilityGrantStoreTest {

    private val start = Instant.parse("2025-01-01T00:00:00Z")

    private class MutableClock(initialInstant: Instant) : Clock() {
        private var currentInstant = initialInstant

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = currentInstant

        fun advance(duration: Duration) {
            currentInstant = currentInstant.plus(duration)
        }
    }

    private class QueueNonceSource(vararg values: String) : MediaCapabilityNonceSource {
        private val pending = ArrayDeque(values.toList())

        override fun newNonce(): MediaCapabilityNonce = MediaCapabilityNonce(pending.removeFirst())
    }

    private val clock = MutableClock(start)
    private val nonceSource = QueueNonceSource(
        "nonce-1", "nonce-2", "nonce-3", "nonce-4", "nonce-5",
        "nonce-6", "nonce-7", "nonce-8", "nonce-9", "nonce-10"
    )

    private val providerId = MediaTransformProviderId(
        packageName = "com.example.mediatransform",
        signingCertificateSha256 = "3a1076bf45ab962d4cb21c3e4e4de64d6b2e21b3d7cd1c2b8e2f6efc8e4e2a11"
    )

    private fun scope(
        sessionId: String = "session-1",
        profileGeneration: Long = 7,
        episodeContentId: String = "episode-42",
        audioTrackFingerprint: String? = "track-fingerprint-1",
        operation: MediaTransformOperation = MediaTransformOperation.GENERATE_SUBTITLE_CUES,
        destination: String = "subtitle-cue-sink"
    ) = MediaCapabilityGrantScope(
        sessionId = sessionId,
        profileGeneration = profileGeneration,
        episodeContentId = episodeContentId,
        audioTrackFingerprint = audioTrackFingerprint,
        providerId = providerId,
        operation = operation,
        destination = MediaCapabilityDestination(destination)
    )

    private fun store() = InMemoryMediaCapabilityGrantStore(clock, nonceSource)

    @Test
    fun `a valid grant validates once and records its lifecycle instants`() {
        val grantStore = store()
        val issued = grantStore.issue(scope(), Duration.ofMinutes(5))

        assertEquals(start, issued.issuedAt)
        assertEquals(start.plus(Duration.ofMinutes(5)), issued.expiresAt)
        assertEquals(
            MediaCapabilityGrantValidation.VALID,
            grantStore.validate(issued, scope())
        )
        assertEquals(
            MediaCapabilityGrantValidation.NONCE_REPLAYED,
            grantStore.validate(issued, scope())
        )
        assertFalse(issued.toString().contains("nonce-1"))
        assertEquals("[redacted-media-grant-nonce]", issued.nonce.toString())
    }

    @Test
    fun `a grant at or beyond its expiry instant fails closed`() {
        val grantStore = store()
        val boundary = grantStore.issue(scope(), Duration.ofMinutes(5))
        clock.advance(Duration.ofMinutes(4).plusSeconds(59))

        assertEquals(
            MediaCapabilityGrantValidation.VALID,
            grantStore.validate(boundary, scope())
        )

        val grant = grantStore.issue(scope(), Duration.ofMinutes(1))
        clock.advance(Duration.ofMinutes(1).plusSeconds(1))

        assertEquals(
            MediaCapabilityGrantValidation.EXPIRED,
            grantStore.validate(grant, scope())
        )
    }

    @Test
    fun `a revoked grant fails closed and revocation reports whether it was live`() {
        val grantStore = store()
        val grant = grantStore.issue(scope(), Duration.ofMinutes(5))

        assertTrue(grantStore.revoke(grant.nonce))
        assertFalse(grantStore.revoke(grant.nonce))
        assertEquals(
            MediaCapabilityGrantValidation.REVOKED,
            grantStore.validate(grant, scope())
        )
        assertFalse(grantStore.revoke(MediaCapabilityNonce("never-issued")))
    }

    @Test
    fun `revoking a session revokes every live grant it issued`() {
        val grantStore = store()
        val first = grantStore.issue(scope(), Duration.ofMinutes(5))
        val second = grantStore.issue(scope(), Duration.ofMinutes(5))
        val otherSession = grantStore.issue(scope(sessionId = "session-2"), Duration.ofMinutes(5))

        assertEquals(2, grantStore.revokeSession("session-1"))

        assertEquals(
            MediaCapabilityGrantValidation.REVOKED,
            grantStore.validate(first, scope())
        )
        assertEquals(
            MediaCapabilityGrantValidation.REVOKED,
            grantStore.validate(second, scope())
        )
        assertEquals(
            MediaCapabilityGrantValidation.VALID,
            grantStore.validate(otherSession, scope(sessionId = "session-2"))
        )
    }

    @Test
    fun `a replayed nonce cannot be spent a second time even against another scope`() {
        val grantStore = store()
        val grant = grantStore.issue(scope(), Duration.ofMinutes(5))
        grantStore.validate(grant, scope())

        assertEquals(
            MediaCapabilityGrantValidation.NONCE_REPLAYED,
            grantStore.validate(grant, scope(sessionId = "session-2"))
        )
    }

    @Test
    fun `a forged grant reusing a live nonce is rejected as invalid`() {
        val grantStore = store()
        val grant = grantStore.issue(scope(), Duration.ofMinutes(5))
        val forged = grant.copy(scope = scope(sessionId = "attacker-session"))

        assertEquals(
            MediaCapabilityGrantValidation.INVALID_GRANT,
            grantStore.validate(forged, scope(sessionId = "attacker-session"))
        )
        // The genuine grant is still spendable; the forgery consumed nothing.
        assertEquals(
            MediaCapabilityGrantValidation.VALID,
            grantStore.validate(grant, scope())
        )
    }

    @Test
    fun `a grant never issued by this store is unknown`() {
        val grantStore = store()
        val unknown = MediaCapabilityGrant(
            scope = scope(),
            issuedAt = start,
            expiresAt = start.plus(Duration.ofMinutes(5)),
            nonce = MediaCapabilityNonce("never-issued")
        )

        assertEquals(
            MediaCapabilityGrantValidation.UNKNOWN_GRANT,
            grantStore.validate(unknown, scope())
        )
    }

    @Test
    fun `scope mismatch fails closed for every scope field without consuming the grant`() {
        val grantStore = store()
        val grant = grantStore.issue(scope(), Duration.ofMinutes(5))

        assertEquals(
            MediaCapabilityGrantValidation.SCOPE_MISMATCH,
            grantStore.validate(grant, scope(sessionId = "session-2"))
        )
        assertEquals(
            MediaCapabilityGrantValidation.SCOPE_MISMATCH,
            grantStore.validate(grant, scope(profileGeneration = 8))
        )
        assertEquals(
            MediaCapabilityGrantValidation.SCOPE_MISMATCH,
            grantStore.validate(grant, scope(episodeContentId = "episode-43"))
        )
        assertEquals(
            MediaCapabilityGrantValidation.SCOPE_MISMATCH,
            grantStore.validate(grant, scope(audioTrackFingerprint = "track-fingerprint-2"))
        )
        assertEquals(
            MediaCapabilityGrantValidation.SCOPE_MISMATCH,
            grantStore.validate(grant, scope(destination = "dub-artifact-sink"))
        )
        assertEquals(
            MediaCapabilityGrantValidation.SCOPE_MISMATCH,
            grantStore.validate(
                grant,
                scope(operation = MediaTransformOperation.CREATE_DUB_ARTIFACT)
            )
        )
        assertEquals(
            MediaCapabilityGrantValidation.VALID,
            grantStore.validate(grant, scope())
        )
    }

    @Test
    fun `grants issued before time advances keep deterministic issue instants`() {
        val grantStore = store()
        val first = grantStore.issue(scope(), Duration.ofMinutes(1))
        clock.advance(Duration.ofSeconds(30))
        val second = grantStore.issue(scope(), Duration.ofMinutes(1))

        assertEquals(start, first.issuedAt)
        assertEquals(start.plusSeconds(30), second.issuedAt)
        assertTrue(second.expiresAt.isAfter(first.expiresAt))
    }
}
