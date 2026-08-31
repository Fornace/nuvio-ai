@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player.subtitle

import androidx.media3.common.text.Cue
import kotlinx.coroutines.flow.StateFlow

/**
 * Time-indexed cue seam for sidecar subtitle rendering (generated-dialogue mission, Milestone 2).
 *
 * Replaces the old immutable `List<CuesWithTiming>` field plus 100 ms full-list polling in
 * `PlayerSidecarSubtitles` with a stable query surface the render loop can call per tick:
 *
 *  - [activeCues] answers "which cues are on screen at `positionUs` right now" from a sorted
 *    interval index — never a per-query linear scan of the whole track.
 *  - [version] increments on every visible mutation, so collectors can cheaply detect that
 *    something changed and recompose only then.
 *  - [trackKey] identifies the rendered subtitle track (same key the sidecar uses for view tags),
 *    so a stale source can never push cues into a view bound to a newer selection.
 *
 * Epoch contract: `epoch` is the seek epoch of the caller's playback position (the host increments
 * it before the first sample after every committed seek / source / track change). Implementations
 * use it to filter cues produced before the current epoch — see [MutableTimedCueStore.newEpoch].
 * [StaticFileCueSource] has no per-epoch production (its content is complete before playback
 * starts) and therefore ignores the parameter.
 *
 * Implementations must be safe for concurrent use: mutations arrive from a provider/generation
 * thread while [activeCues] is queried from the UI render thread.
 */
interface TimedCueSource {

    /** Stable identity of the subtitle track this source renders; see `addonSubtitleKey`. */
    val trackKey: String

    /**
     * Monotonic mutation counter. Starts at 0 for a source with no cues and increments on every
     * visible mutation (accepted append, finalized cue, cleared stale-epoch provisional cues).
     * Pure queries and rejected mutations never change it.
     */
    val version: StateFlow<Long>

    /**
     * Cues whose display window contains `positionUs` under seek epoch `epoch`.
     *
     * Window semantics are `[startUs, endUs)`: a cue is active at its start instant and inactive
     * from its end instant. The result is ordered by start time and safe to retain (it shares no
     * mutable state with the source).
     */
    fun activeCues(positionUs: Long, epoch: Long): List<Cue>
}
