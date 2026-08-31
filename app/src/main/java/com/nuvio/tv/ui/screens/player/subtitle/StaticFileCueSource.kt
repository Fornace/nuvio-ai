@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player.subtitle

import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [TimedCueSource] over an already parsed, immutable sidecar file — the exact
 * `List<CuesWithTiming>` the sidecar path produces today from `parseSidecarTimedCuesRobust`,
 * plus the sidecar track key.
 *
 * Behavior parity with the existing `collectActiveSidecarCues` loop is intentional:
 *  - window semantics are `[startUs, endUs)` with `startUs` inclusive and `endUs` exclusive
 *    (`endUs = startTimeUs + durationUs`, and cues with both duration and end unset extend to
 *    `Long.MAX_VALUE`);
 *  - original [Cue] instances are reused verbatim, so existing style/position/bitmap handling
 *    downstream (sanitizer, SDH filter, RTL fix, overlap merge) sees identical objects;
 *  - the whole immutable list is indexed once at construction (sorted interval treap), so
 *    per-tick queries are `O(log n + k)` instead of the old full-list scan;
 *  - `version` never changes and [activeCues] ignores `epoch`: a static file has no per-seek
 *    production, so there is nothing epoch-stale to filter.
 *
 * Construction cost is one `O(n log n)` index build; queries afterwards never mutate state.
 */
class StaticFileCueSource(
    cuesWithTiming: List<CuesWithTiming>,
    override val trackKey: String
) : TimedCueSource {

    private val index = CueIntervalIndex()

    init {
        cuesWithTiming.forEachIndexed { entryIndex, entry ->
            val startUs = entry.startTimeUs
            if (startUs == C.TIME_UNSET) return@forEachIndexed
            val endUs = when {
                entry.durationUs != C.TIME_UNSET -> startUs + entry.durationUs
                entry.endTimeUs != C.TIME_UNSET -> entry.endTimeUs
                else -> Long.MAX_VALUE
            }
            if (endUs <= startUs) return@forEachIndexed
            entry.cues.forEachIndexed { cueIndex, cue ->
                val text = cue.text?.toString()?.takeIf { it.isNotBlank() }
                if (text != null) {
                    index.add(
                        TimedCue(
                            id = "static-$entryIndex-$cueIndex",
                            startUs = startUs,
                            endUs = endUs,
                            text = text,
                            revision = 0L,
                            epoch = STATIC_EPOCH
                        ),
                        renderedCue = cue
                    )
                }
            }
        }
    }

    /** Fixed at 0: a parsed file never mutates. */
    override val version: StateFlow<Long> = MutableStateFlow(0L)

    override fun activeCues(positionUs: Long, epoch: Long): List<Cue> {
        return index.activeCues(positionUs, STATIC_EPOCH)
    }

    private companion object {
        /** Static file cues are epoch-agnostic; they are never filtered by the caller's epoch. */
        const val STATIC_EPOCH = 0L
    }
}
