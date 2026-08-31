package com.nuvio.tv.ui.screens.player.subtitle

import java.util.Random

/**
 * Deterministic randomized overlap pattern for cue-index tests: cues are laid out in sequential
 * non-overlapping pairs with occasional bridges spanning the previous pair, so expected active
 * cues at any position are computable in closed form instead of being asserted by re-scanning.
 *
 * Layout for index i (0-based):
 *  - even i: `startUs = (i + 1) * SPACING`, `endUs = startUs + PAIR_DURATION`; index 0 leaves a
 *    gap before it, later even indices may be stretched backwards as a bridge.
 *  - odd i: `startUs = even(i - 1).startUs + PAIR_TAIL`, `endUs = startUs + PAIR_DURATION`
 *    (overlaps the tail of the even cue's window).
 *  - bridge (rare, ~1/17 of even indices >= 2): the cue is stretched backwards to cover
 *    everything up to the previous pair's odd start, creating nested intervals that force the
 *    index's max-end pruning to prove itself.
 *
 * At any position p the expected cues are exactly the layout cues with `startUs <= p < endUs`.
 */
object OverlappingCuePattern {

    const val SPACING = 2_000_000L
    const val PAIR_DURATION = 1_000_000L
    const val PAIR_TAIL = 500_000L
    const val BRIDGE_NUMERATOR = 1
    const val BRIDGE_DENOMINATOR = 17

    fun evenStartUs(evenIndex: Int): Long = (evenIndex + 1L) * SPACING

    fun isBridge(index: Int, random: Random): Boolean =
        index >= 2 && index % 2 == 0 && random.nextInt(BRIDGE_DENOMINATOR) < BRIDGE_NUMERATOR

    /** Expected `[startUs, endUs)` window of layout cue `index` under `random`'s bridge choices. */
    fun window(index: Int, random: Random): LongRange {
        return if (index % 2 == 0) {
            val start = evenStartUs(index)
            val stretchedStart = if (isBridge(index, random)) {
                evenStartUs(index - 2) + PAIR_TAIL
            } else {
                start
            }
            stretchedStart until start + PAIR_DURATION
        } else {
            val start = evenStartUs(index - 1) + PAIR_TAIL
            start until start + PAIR_DURATION
        }
    }

    /** All layout cues active at `positionUs` (a linear scan is fine — it is the oracle). */
    fun activeIdsAt(positionUs: Long, cueCount: Int, random: Random): List<String> {
        val active = ArrayList<String>(2)
        for (index in 0 until cueCount) {
            if (window(index, random).contains(positionUs)) {
                active += "perf-$index"
            }
        }
        return active
    }
}
