package com.nuvio.tv.core.media.provider

import java.time.Clock
import java.time.Duration

fun interface MediaCapabilityNonceSource {
    fun newNonce(): MediaCapabilityNonce
}

enum class MediaCapabilityGrantValidation {
    VALID,
    EXPIRED,
    REVOKED,
    NONCE_REPLAYED,
    SCOPE_MISMATCH,
    UNKNOWN_GRANT,
    INVALID_GRANT
}

interface MediaCapabilityGrantStore {
    fun issue(scope: MediaCapabilityGrantScope, validity: Duration): MediaCapabilityGrant
    fun validate(
        grant: MediaCapabilityGrant,
        expectedScope: MediaCapabilityGrantScope
    ): MediaCapabilityGrantValidation
    fun revoke(nonce: MediaCapabilityNonce): Boolean
    fun revokeSession(sessionId: String): Int
}

/** Thread-safe, process-lifetime grant store. A successful validation atomically spends the nonce. */
class InMemoryMediaCapabilityGrantStore(
    private val clock: Clock,
    private val nonceSource: MediaCapabilityNonceSource
) : MediaCapabilityGrantStore {
    private data class Record(
        val grant: MediaCapabilityGrant,
        var revoked: Boolean = false,
        var consumed: Boolean = false
    )

    private val records = linkedMapOf<MediaCapabilityNonce, Record>()

    @Synchronized
    override fun issue(
        scope: MediaCapabilityGrantScope,
        validity: Duration
    ): MediaCapabilityGrant {
        require(!validity.isZero && !validity.isNegative) { "Grant validity must be positive" }
        val nonce = nonceSource.newNonce()
        check(nonce !in records) { "Grant nonce collision" }
        val issuedAt = clock.instant()
        val grant = MediaCapabilityGrant(
            scope = scope,
            issuedAt = issuedAt,
            expiresAt = issuedAt.plus(validity),
            nonce = nonce
        )
        records[nonce] = Record(grant)
        return grant
    }

    @Synchronized
    override fun validate(
        grant: MediaCapabilityGrant,
        expectedScope: MediaCapabilityGrantScope
    ): MediaCapabilityGrantValidation {
        val record = records[grant.nonce] ?: return MediaCapabilityGrantValidation.UNKNOWN_GRANT
        if (record.grant != grant) return MediaCapabilityGrantValidation.INVALID_GRANT
        if (record.revoked) return MediaCapabilityGrantValidation.REVOKED
        if (!clock.instant().isBefore(grant.expiresAt)) {
            return MediaCapabilityGrantValidation.EXPIRED
        }
        // Replay is checked before scope so a spent nonce can never be probed against other scopes.
        if (record.consumed) return MediaCapabilityGrantValidation.NONCE_REPLAYED
        if (grant.scope != expectedScope) return MediaCapabilityGrantValidation.SCOPE_MISMATCH
        record.consumed = true
        return MediaCapabilityGrantValidation.VALID
    }

    @Synchronized
    override fun revoke(nonce: MediaCapabilityNonce): Boolean {
        val record = records[nonce] ?: return false
        val newlyRevoked = !record.revoked
        record.revoked = true
        return newlyRevoked
    }

    @Synchronized
    override fun revokeSession(sessionId: String): Int {
        var count = 0
        records.values.forEach { record ->
            if (record.grant.scope.sessionId == sessionId && !record.revoked) {
                record.revoked = true
                count++
            }
        }
        return count
    }
}
