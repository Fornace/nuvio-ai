@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player.subtitle

import androidx.media3.common.text.Cue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mutable [TimedCueSource] for generated dialogue captions (Milestone 2 core).
 *
 * Mutations ([append], [finalizeCue], [newEpoch]) are expected on the provider/generation
 * thread; queries ([activeCues], [snapshot], …) are expected on the UI render thread. One
 * monitor lock guards every operation, and the only shared mutable structure is a balanced
 * interval index per seek epoch — appends and queries are `O(log n)`, never full-list scans
 * and never whole-index rebuilds, so a UI query cannot stall behind a large provider append.
 *
 * Semantics:
 *  - **Appends** are provisional cue updates keyed by stable cue id. An update for an id that
 *    already exists coalesces into that single entry (text/timing replaced, revision advanced);
 *    it never adds a second entry. Revisions must be strictly monotonic per id.
 *  - **Finalization** freezes a cue id: final cues are immutable and every later update for a
 *    finalized id is rejected, which also forbids any older-revision regression past a final.
 *  - **Seek epochs** invalidate history: [newEpoch] records the new epoch, rejects later appends
 *    tagged with older (or any non-current) epochs and clears the provisional cues produced
 *    under them. Finalized cues survive clearing — they are immutable — but [activeCues] only
 *    ever returns cues matching the caller's epoch.
 *  - **Bounds** are hard: at most [maxCues] total cues and at most [maxTextLength] characters
 *    of text per cue; overflowing appends are rejected via [CueMutationResult].
 */
class MutableTimedCueStore(
    override val trackKey: String,
    initialEpoch: Long? = DEFAULT_INITIAL_EPOCH,
    private val maxCues: Int = DEFAULT_MAX_CUES,
    private val maxTextLength: Int = DEFAULT_MAX_TEXT_LENGTH
) : TimedCueSource {

    private val lock = Any()

    private val versionState = MutableStateFlow(0L)
    override val version: StateFlow<Long> = versionState

    /** All live entries (provisional + final), keyed by stable cue id. */
    private val entries = LinkedHashMap<String, TimedCue>()

    /**
     * Interval index per epoch. The index for the currently produced epoch is mutated in place;
     * epoch clearing drops a map entry instead of rescanning cues. Rendering reads the map
     * directly under [lock], so indexes are never leaked to readers.
     */
    private val indexByEpoch = HashMap<Long, CueIntervalIndex>()

    /** Current seek epoch; appends must match it exactly. Null until the first [newEpoch]. */
    private var currentEpoch: Long? = initialEpoch

    /** Total finalized cue ids (finals are never removed). */
    private var finalCount = 0

    /**
     * Appends a provisional cue update. The update's epoch must equal the store's current seek
     * epoch; its revision must be strictly greater than the stored revision when the id exists.
     * Overflowing text or capacity, blank ids/text and malformed windows are rejected without
     * any state change.
     */
    fun append(update: TimedCue): CueMutationResult = synchronized(lock) {
        if (update.epoch != currentEpoch) {
            return CueMutationResult.Rejected(CueMutationResult.Reason.STALE_EPOCH)
        }
        if (update.id.isBlank() || update.text.isBlank()) {
            return CueMutationResult.Rejected(CueMutationResult.Reason.INVALID)
        }
        if (update.startUs < 0L || update.endUs <= update.startUs) {
            return CueMutationResult.Rejected(CueMutationResult.Reason.INVALID)
        }
        if (update.text.length > maxTextLength) {
            return CueMutationResult.Rejected(CueMutationResult.Reason.TEXT_TOO_LONG)
        }
        val existing = entries[update.id]
        if (existing != null) {
            if (existing.isFinal) {
                return CueMutationResult.Rejected(CueMutationResult.Reason.FINAL)
            }
            if (update.revision <= existing.revision) {
                return CueMutationResult.Rejected(CueMutationResult.Reason.REVISION_REGRESSION)
            }
        } else if (entries.size >= maxCues) {
            return CueMutationResult.Rejected(CueMutationResult.Reason.CAPACITY)
        }

        val fresh = update.copy(isFinal = false)
        entries[update.id] = fresh
        val index = indexFor(update.epoch)
        if (existing != null) {
            index.remove(existing)
        }
        index.add(fresh)
        bumpVersion()
        return CueMutationResult.Accepted
    }

    /**
     * Freezes [cueId] at its current revision. Final cues are immutable: any later [append] for
     * the id is rejected, including older-revision updates. Unknown ids are rejected without
     * state change (the provider must append a cue before finalizing it).
     */
    fun finalizeCue(cueId: String): CueMutationResult = synchronized(lock) {
        val existing = entries[cueId]
            ?: return CueMutationResult.Rejected(CueMutationResult.Reason.INVALID)
        if (existing.isFinal) {
            return CueMutationResult.Rejected(CueMutationResult.Reason.FINAL)
        }
        val finalCue = existing.copy(isFinal = true)
        entries[cueId] = finalCue
        indexFor(existing.epoch).apply {
            remove(existing)
            add(finalCue)
        }
        finalCount++
        bumpVersion()
        return CueMutationResult.Accepted
    }

    /**
     * Records a committed seek/source/track change. Appends tagged with an epoch other than
     * [epoch] are rejected from now on, and the provisional cues produced under older epochs are
     * cleared (finalized cues stay: they are immutable, and [activeCues] already filters by the
     * caller's epoch). Monotonically decreasing epochs are ignored (`false`).
     */
    fun newEpoch(epoch: Long): Boolean = synchronized(lock) {
        val current = currentEpoch
        if (current != null && epoch <= current) return false
        currentEpoch = epoch
        var clearedAny = false
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (!entry.isFinal && entry.epoch < epoch) {
                indexByEpoch[entry.epoch]?.remove(entry)
                iterator.remove()
                clearedAny = true
            }
        }
        indexByEpoch.values.removeAll { it.cueCount == 0 }
        if (clearedAny) bumpVersion()
        return true
    }

    /**
     * Cues whose `[startUs, endUs)` window contains `positionUs` under seek epoch `epoch`,
     * ordered by start time then id.
     */
    override fun activeCues(positionUs: Long, epoch: Long): List<Cue> = synchronized(lock) {
        indexByEpoch[epoch]?.activeCues(positionUs, epoch) ?: emptyList()
    }

    /** Number of live cue entries (provisional + final). */
    fun cueCount(): Int = synchronized(lock) { entries.size }

    /** Number of finalized cue ids; finals are never removed by epoch invalidation. */
    fun finalCueCount(): Int = synchronized(lock) { finalCount }

    /** Current seek epoch, or `null` before the first [newEpoch] when constructed without one. */
    fun seekEpoch(): Long? = synchronized(lock) { currentEpoch }

    /**
     * Diagnostics/testing snapshot of all live entries ordered by `(epoch, startUs, id)`. The
     * render loop must use [activeCues]; this exists for assertions and debug surfaces, not for
     * polling.
     */
    fun snapshot(): List<TimedCue> = synchronized(lock) {
        val out = ArrayList<TimedCue>(entries.size)
        for ((_, index) in indexByEpoch.entries.sortedBy { it.key }) {
            out.addAll(index.snapshot())
        }
        out
    }

    // ---------------------------------------------------------------- internals

    private fun indexFor(epoch: Long): CueIntervalIndex =
        indexByEpoch.getOrPut(epoch) { CueIntervalIndex() }

    private fun bumpVersion() {
        versionState.value = versionState.value + 1L
    }

    companion object {
        const val DEFAULT_MAX_CUES = 5_000
        const val DEFAULT_MAX_TEXT_LENGTH = 1_000
        const val DEFAULT_INITIAL_EPOCH = 0L
    }
}
