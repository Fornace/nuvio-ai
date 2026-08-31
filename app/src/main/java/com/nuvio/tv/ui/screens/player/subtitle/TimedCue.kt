@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player.subtitle

import androidx.media3.common.text.Cue

/**
 * Store-level cue model for generated dialogue captions.
 *
 * The store deliberately does not hold [Cue] values in its mutation API: media3 `Cue`s carry
 * Android-side styling state the provider should not need to know about. Providers address cues
 * by stable id; [toMedia3Cue] adapts the stored text to the render-side type as a pure function
 * once per accepted mutation, and the index caches the result for cheap repeated queries.
 *
 * @property id Stable cue id assigned by the provider; all revisions of one caption share it.
 * @property startUs Inclusive start of the display window, in media-time microseconds.
 * @property endUs Exclusive end of the display window, in media-time microseconds.
 * @property text Cue text, already bounded by the store's max text length.
 * @property revision Provider-assigned revision of this entry; strictly increasing per id.
 * @property epoch Seek epoch this cue was produced under; see [MutableTimedCueStore.newEpoch].
 * @property isFinal Final cues are immutable snapshots; any later update for their id is rejected.
 */
data class TimedCue(
    val id: String,
    val startUs: Long,
    val endUs: Long,
    val text: String,
    val revision: Long,
    val epoch: Long,
    val isFinal: Boolean = false
) {
    /**
     * True when `positionUs` falls inside this cue's `[startUs, endUs)` window. This model is
     * intentionally constructible with malformed windows: it may carry untrusted provider
     * data, and [MutableTimedCueStore.append] is the validation boundary that rejects it.
     */
    fun isActiveAt(positionUs: Long): Boolean = positionUs >= startUs && positionUs < endUs
}

/** Adapter from the store-level cue model to the media3 render-side cue. */
internal fun TimedCue.toMedia3Cue(): Cue = Cue.Builder().setText(text).build()
