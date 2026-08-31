package com.nuvio.tv.ui.screens.player

import java.time.Clock

/**
 * Dub attachment states as shown in the translated voice overlay.
 *
 * Playback itself never stops in these states: original audio keeps playing in every state
 * except [Ready], where the generated track is the selected audio.
 */
enum class DubPlaybackState {
    /** No descriptor attached; the coordinator holds no session state. */
    Idle,

    /** A descriptor is attached but the generated track is not confirmed usable yet. */
    Preparing,

    /** The generated track is attached, selected and has known playable coverage. */
    Ready,

    /** The provider reported a recoverable fault; original audio plays while it heals. */
    Recovering,

    /** Original audio is selected on purpose: user request, or a non-terminal dub fault. */
    OriginalFallback,

    /** The dub attempt is terminally dead; original audio is the only selectable audio. */
    Failed,
}

/** Which audio the user currently hears, derived from [DubPlaybackState]. */
enum class DubAudioSelection {
    GeneratedDub,
    OriginalAudio,
}

/** Report for one coordinator event, accepted or ignored. */
data class DubEventReport(
    val event: String,
    val accepted: Boolean,
    val fromState: DubPlaybackState,
    val toState: DubPlaybackState,
    val reason: String? = null,
    val atEpochMs: Long,
)

/** A provider or adapter callback that was rejected instead of applied. */
data class DubRejectedCallback(
    val descriptorKey: String?,
    val generation: Long,
    val reason: String,
    val atEpochMs: Long,
)

/** Immutable view of the coordinator for UI state and diagnostics. */
data class DubPlaybackSnapshot(
    val state: DubPlaybackState,
    val descriptor: ExternalAudioDescriptor?,
    val generation: Long?,
    val coveredEndUs: Long?,
    val selectedAudio: DubAudioSelection,
    val fallbackReason: String?,
    val lastErrorReason: String?,
    val userRequestedOriginal: Boolean,
)

/**
 * Pure state machine for translated-voice (dub) attachment, selection and fallback.
 *
 * It owns no player, thread, UI or storage: the host feeds it events and reads the resulting
 * state. Every event method is total — an event that is not legal in the current state is
 * ignored and reported in [lastEvent] (and counted in [ignoredEventCount]) instead of
 * throwing. This is what keeps an auxiliary dub fault from ever taking down primary playback:
 * the worst outcome this class can produce is [DubPlaybackState.OriginalFallback] or
 * [DubPlaybackState.Failed], never an exception.
 *
 * Rules encoded here, from the translated-voice-overlay brief:
 * - [DubPlaybackState.Ready] is unreachable without completed coverage information
 *   (`coveredRangeUs.endUs > 0`), so the UI can never claim Ready while target coverage is
 *   absent. Coverage lives on the immutable descriptor; a provider that learns more coverage
 *   must describe it in a new descriptor generation.
 * - A seek commits a strictly newer generation and invalidates the old one: the descriptor is
 *   re-keyed with the new generation (so its [ExternalAudioDescriptor.stableKey] changes) and
 *   work tagged with the previous generation must be dropped.
 * - Late callbacks are validated by [callbackReceived] and rejected, never applied.
 * - [useOriginalAudio] is an explicit user action, recorded as such, so it stays
 *   distinguishable from a failure-driven fallback.
 * - [stop] drops the descriptor and returns to [DubPlaybackState.Idle].
 *
 * Generations move forward on [seekCommitted]; [start] adopts the descriptor supplied for a
 * new/retried attempt.
 *
 * Diagnostics counters ([ignoredEventCount], [rejectedCallbackCount]) cover the whole
 * coordinator lifetime and are not reset by [stop]; only session-scoped state is cleared.
 */
class DubPlaybackCoordinator(
    private val clock: Clock = Clock.systemUTC(),
) {
    var state: DubPlaybackState = DubPlaybackState.Idle
        private set

    /** The descriptor for the current session, or `null` after [stop]. */
    var currentDescriptor: ExternalAudioDescriptor? = null
        private set

    /** Current generation (seek epoch); `null` while no session is active. */
    var currentGeneration: Long? = null
        private set

    /** Highest coverage end (source microseconds) the provider has reported, if any. */
    var coveredEndUs: Long? = null
        private set

    /** Set when a fault moved playback to original audio; `null` for a user request. */
    var fallbackReason: String? = null
        private set

    /** Last provider/adapter error, including recoverable ones; `null` when none. */
    var lastErrorReason: String? = null
        private set

    /** True when the user explicitly asked for original audio. */
    var userRequestedOriginal: Boolean = false
        private set

    /** Report for the most recent event, including ignored ones. */
    var lastEvent: DubEventReport? = null
        private set

    /** How many events were ignored because they were illegal in the current state. */
    var ignoredEventCount: Int = 0
        private set

    /** How many provider/adapter callbacks were rejected (stale or foreign). */
    var rejectedCallbackCount: Int = 0
        private set

    /** The most recently rejected callback, if any. */
    var lastRejectedCallback: DubRejectedCallback? = null
        private set

    /** True while a session exists (any state other than [DubPlaybackState.Idle]). */
    val isActive: Boolean
        get() = state != DubPlaybackState.Idle

    /** Which audio the user currently hears. */
    val selectedAudio: DubAudioSelection
        get() = if (state == DubPlaybackState.Ready) {
            DubAudioSelection.GeneratedDub
        } else {
            DubAudioSelection.OriginalAudio
        }

    /** Stable key of the attached descriptor, if a session is active. */
    val currentDescriptorKey: String?
        get() = currentDescriptor?.stableKey

    fun snapshot(): DubPlaybackSnapshot = DubPlaybackSnapshot(
        state = state,
        descriptor = currentDescriptor,
        generation = currentGeneration,
        coveredEndUs = coveredEndUs,
        selectedAudio = selectedAudio,
        fallbackReason = fallbackReason,
        lastErrorReason = lastErrorReason,
        userRequestedOriginal = userRequestedOriginal,
    )

    /** Attach a descriptor and begin preparing it. Valid from Idle and Failed. */
    fun start(descriptor: ExternalAudioDescriptor): DubPlaybackState {
        val from = state
        if (from != DubPlaybackState.Idle && from != DubPlaybackState.Failed) {
            return ignored("start", from, "start requires Idle or Failed (was $from)")
        }
        currentDescriptor = descriptor
        currentGeneration = descriptor.generation
        clearSessionReasons()
        return applied("start", from, DubPlaybackState.Preparing)
    }

    /**
     * The player confirmed the external track described by [descriptorKey] is attached.
     * Valid only while [DubPlaybackState.Preparing], only for the current descriptor's stable
     * key, and never reports Ready without completed coverage information.
     */
    fun attachSucceeded(descriptorKey: String): DubPlaybackState {
        val from = state
        val descriptor = currentDescriptor
            ?: return ignored("attachSucceeded", from, "no active dub session")
        if (from != DubPlaybackState.Preparing) {
            return ignored("attachSucceeded", from, "attachSucceeded requires Preparing (was $from)")
        }
        if (descriptorKey != descriptor.stableKey) {
            return ignored("attachSucceeded", from, "descriptor key does not match the attached descriptor")
        }
        if (!hasCompletedCoverageInformation()) {
            return ignored("attachSucceeded", from, "cannot report Ready while coveredRangeUs.endUs <= 0")
        }
        return applied("attachSucceeded", from, DubPlaybackState.Ready)
    }

    /**
     * Attaching the external track failed. Non-terminal failures keep original audio
     * selectable ([DubPlaybackState.OriginalFallback]); a [terminal] failure ends the attempt
     * ([DubPlaybackState.Failed]) while still leaving original audio selectable.
     */
    fun attachFailed(reason: String, terminal: Boolean = false): DubPlaybackState {
        val from = state
        if (from != DubPlaybackState.Preparing) {
            return ignored("attachFailed", from, "attachFailed requires Preparing (was $from)")
        }
        val to = if (terminal) DubPlaybackState.Failed else DubPlaybackState.OriginalFallback
        lastErrorReason = reason.ifBlank { "attach failed" }
        fallbackReason = lastErrorReason
        userRequestedOriginal = false
        return applied("attachFailed", from, to)
    }

    /**
     * A seek was committed: adopt [newGeneration], invalidate the previous generation and
     * return to [DubPlaybackState.Preparing] until coverage at the new position is confirmed.
     * Valid from any active state; the generation must strictly advance. The descriptor is
     * re-keyed with the new generation, so callbacks carrying the pre-seek stable key are
     * rejected just like ones carrying the pre-seek generation.
     */
    fun seekCommitted(newGeneration: Long): DubPlaybackState {
        val from = state
        val descriptor = currentDescriptor
            ?: return ignored("seekCommitted", from, "no active dub session")
        val current = currentGeneration
        if (newGeneration <= (current ?: Long.MIN_VALUE)) {
            return ignored(
                "seekCommitted",
                from,
                "seek generation $newGeneration does not advance past $current",
            )
        }
        currentDescriptor = descriptor.copy(generation = newGeneration)
        currentGeneration = newGeneration
        clearSessionReasons()
        return applied("seekCommitted", from, DubPlaybackState.Preparing)
    }

    /**
     * Provider coverage advanced to [coveredEndUs] (source microseconds). Valid from
     * [DubPlaybackState.Ready] and [DubPlaybackState.Recovering]; the watermark only moves
     * forward, and a regressing value is reported and ignored.
     */
    fun providerProgress(coveredEndUs: Long): DubPlaybackState {
        val from = state
        if (from != DubPlaybackState.Ready && from != DubPlaybackState.Recovering) {
            return ignored("providerProgress", from, "providerProgress requires Ready or Recovering (was $from)")
        }
        if (coveredEndUs < 0L) {
            return ignored("providerProgress", from, "coveredEndUs must be >= 0 (was $coveredEndUs)")
        }
        val watermark = this.coveredEndUs
        if (watermark != null && coveredEndUs < watermark) {
            return ignored(
                "providerProgress",
                from,
                "coverage regressed from $watermark us to $coveredEndUs us; watermark kept",
            )
        }
        this.coveredEndUs = coveredEndUs
        return applied("providerProgress", from, from)
    }

    /**
     * The provider reported a fault. A [recoverable] fault keeps the session alive in
     * [DubPlaybackState.Recovering] with original audio playing meanwhile; otherwise playback
     * falls back to original audio with the reason recorded.
     */
    fun providerError(recoverable: Boolean, reason: String = "provider error"): DubPlaybackState {
        val from = state
        if (from != DubPlaybackState.Ready) {
            return ignored("providerError", from, "providerError requires Ready (was $from)")
        }
        lastErrorReason = reason.ifBlank { "provider error" }
        fallbackReason = if (recoverable) null else lastErrorReason
        userRequestedOriginal = false
        val to = if (recoverable) DubPlaybackState.Recovering else DubPlaybackState.OriginalFallback
        return applied("providerError", from, to)
    }

    /**
     * A recoverable provider fault healed. Valid only from [DubPlaybackState.Recovering],
     * and only reportable while coverage is known.
     */
    fun providerRecovered(): DubPlaybackState {
        val from = state
        if (from != DubPlaybackState.Recovering) {
            return ignored("providerRecovered", from, "providerRecovered requires Recovering (was $from)")
        }
        if (!hasCompletedCoverageInformation()) {
            return ignored("providerRecovered", from, "cannot report Ready while coveredRangeUs.endUs <= 0")
        }
        return applied("providerRecovered", from, DubPlaybackState.Ready)
    }

    /**
     * The user explicitly chose original audio. This is a user action, not a failure: it is
     * recorded in [userRequestedOriginal] and leaves [fallbackReason] unset so the two can
     * never be confused.
     */
    fun useOriginalAudio(): DubPlaybackState {
        val from = state
        if (currentDescriptor == null) {
            return ignored("useOriginalAudio", from, "no active dub session")
        }
        if (from == DubPlaybackState.Failed) {
            return ignored("useOriginalAudio", from, "session already failed; stop() or start() first")
        }
        userRequestedOriginal = true
        fallbackReason = null
        return applied("useOriginalAudio", from, DubPlaybackState.OriginalFallback)
    }

    /** Drop the descriptor and all session state; always legal. */
    fun stop(): DubPlaybackState {
        val from = state
        currentDescriptor = null
        currentGeneration = null
        clearSessionReasons()
        return applied("stop", from, DubPlaybackState.Idle)
    }

    /**
     * Validate a provider/adapter callback before applying it: the descriptor key must match
     * the attached descriptor and the callback generation must be exactly the current one.
     * Anything else is rejected (and counted in [rejectedCallbackCount]) rather than applied,
     * so a late artifact from an old generation or another episode can never attach.
     *
     * @return `true` when the callback belongs to the current session and generation.
     */
    fun callbackReceived(descriptorKey: String?, generation: Long): Boolean {
        val from = state
        val descriptor = currentDescriptor
        val reason = when {
            descriptor == null -> "no active dub session"
            descriptorKey != descriptor.stableKey ->
                "descriptor key does not match the attached descriptor"
            generation != currentGeneration ->
                "generation $generation is not the current generation $currentGeneration"
            else -> null
        }
        if (reason != null) {
            rejectedCallbackCount++
            val now = clock.millis()
            lastRejectedCallback = DubRejectedCallback(
                descriptorKey = descriptorKey,
                generation = generation,
                reason = reason,
                atEpochMs = now,
            )
            lastEvent = DubEventReport("callbackReceived", false, from, from, reason, now)
            return false
        }
        lastEvent = DubEventReport("callbackReceived", true, from, from, null, clock.millis())
        return true
    }

    private fun hasCompletedCoverageInformation(): Boolean =
        (currentDescriptor?.coveredRangeUs?.endUs ?: 0L) > 0L

    private fun clearSessionReasons() {
        coveredEndUs = null
        fallbackReason = null
        lastErrorReason = null
        userRequestedOriginal = false
    }

    private fun applied(event: String, from: DubPlaybackState, to: DubPlaybackState) =
        to.also {
            state = to
            lastEvent = DubEventReport(event, true, from, to, null, clock.millis())
        }

    private fun ignored(event: String, from: DubPlaybackState, reason: String) =
        from.also {
            ignoredEventCount++
            lastEvent = DubEventReport(event, false, from, from, reason, clock.millis())
        }
}
