package com.nuvio.tv.ui.screens.player.subtitle

/**
 * Outcome of a store mutation ([MutableTimedCueStore.append] / [finalizeCue]).
 *
 * Overflowing or stale appends are rejected through this type instead of exceptions, so a
 * provider bridge can surface bounded/rejected status codes without a try/catch seam.
 */
sealed interface CueMutationResult {

    /** Mutation applied. */
    data object Accepted : CueMutationResult

    /** Mutation rejected; [reason] explains why. No state changed. */
    data class Rejected(val reason: Reason) : CueMutationResult

    enum class Reason {
        /** The cue id is already finalized; final cues are immutable. */
        FINAL,

        /** The update's revision is not strictly greater than the stored revision. */
        REVISION_REGRESSION,

        /** The update's epoch does not match the store's current seek epoch. */
        STALE_EPOCH,

        /** Total cue count is at [MutableTimedCueStore.maxCues]. */
        CAPACITY,

        /** Text exceeds [MutableTimedCueStore.maxTextLength]. */
        TEXT_TOO_LONG,

        /** Malformed window (`endUs <= startUs`, negative `startUs`) or blank text/id. */
        INVALID
    }
}
