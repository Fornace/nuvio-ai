@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player.subtitle

import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static sidecar adapter: window boundaries are `[startUs, endUs)`, empty input yields no cues,
 * and the version flow never moves (a parsed file is immutable).
 */
class StaticFileCueSourceTest {

    private fun cue(text: String): Cue = Cue.Builder().setText(text).build()

    private fun timed(cues: List<Cue>, startUs: Long, durationUs: Long) =
        CuesWithTiming(cues, startUs, durationUs)

    @Test
    fun `start boundary is inclusive and end boundary is exclusive`() {
        val source = StaticFileCueSource(
            listOf(timed(listOf(cue("hello")), startUs = 1_000L, durationUs = 2_000L)),
            trackKey = "sidecar-srt-1"
        )

        assertEquals(listOf("hello"), source.activeCues(1_000L, epoch = 0L).map { it.text.toString() })
        assertEquals(listOf("hello"), source.activeCues(2_999L, epoch = 0L).map { it.text.toString() })
        assertTrue(source.activeCues(999L, epoch = 0L).isEmpty())
        assertTrue(source.activeCues(3_000L, epoch = 0L).isEmpty())
    }

    @Test
    fun `adjacent cues hand over exactly at the shared boundary`() {
        val source = StaticFileCueSource(
            listOf(
                timed(listOf(cue("first")), startUs = 0L, durationUs = 5_000L),
                timed(listOf(cue("second")), startUs = 5_000L, durationUs = 5_000L)
            ),
            trackKey = "t"
        )

        assertEquals(listOf("first"), source.activeCues(4_999L, epoch = 3L).map { it.text.toString() })
        assertEquals(listOf("second"), source.activeCues(5_000L, epoch = 3L).map { it.text.toString() })
        assertTrue(source.activeCues(10_000L, epoch = 3L).isEmpty())
    }

    @Test
    fun `overlapping cues are returned together ordered by start time`() {
        val source = StaticFileCueSource(
            listOf(
                timed(listOf(cue("wide")), startUs = 0L, durationUs = 10_000L),
                timed(listOf(cue("narrow")), startUs = 4_000L, durationUs = 2_000L)
            ),
            trackKey = "t"
        )

        assertEquals(listOf("wide"), source.activeCues(3_000L, epoch = 0L).map { it.text.toString() })
        assertEquals(
            listOf("wide", "narrow"),
            source.activeCues(5_000L, epoch = 0L).map { it.text.toString() }
        )
        assertEquals(listOf("wide"), source.activeCues(7_000L, epoch = 0L).map { it.text.toString() })
    }

    @Test
    fun `empty input yields no cues`() {
        val source = StaticFileCueSource(emptyList(), trackKey = "t")

        assertTrue(source.activeCues(0L, epoch = 0L).isEmpty())
        assertTrue(source.activeCues(Long.MAX_VALUE / 2, epoch = 9L).isEmpty())
        assertEquals(0, source.version.value)
    }

    @Test
    fun `unset duration keeps the cue active until the end of time`() {
        val source = StaticFileCueSource(
            listOf(CuesWithTiming(listOf(cue("tail")), 100L, C.TIME_UNSET)),
            trackKey = "t"
        )
        // Matches the legacy collectActiveSidecarCues fallback for unset end times.
        assertTrue(source.activeCues(Long.MAX_VALUE - 1, epoch = 0L).isNotEmpty())
        assertTrue(source.activeCues(99L, epoch = 0L).isEmpty())
    }

    @Test
    fun `blank text cues are skipped like the lenient sidecar parser does`() {
        val source = StaticFileCueSource(
            listOf(
                timed(listOf(cue("  ")), startUs = 0L, durationUs = 1_000L),
                timed(listOf(cue("real")), startUs = 2_000L, durationUs = 1_000L)
            ),
            trackKey = "t"
        )

        assertTrue(source.activeCues(500L, epoch = 0L).isEmpty())
        assertEquals(listOf("real"), source.activeCues(2_500L, epoch = 0L).map { it.text.toString() })
    }

    @Test
    fun `version never changes and duplicate start times keep both entries`() {
        val source = StaticFileCueSource(
            listOf(
                timed(listOf(cue("one")), startUs = 1_000L, durationUs = 5_000L),
                timed(listOf(cue("two")), startUs = 1_000L, durationUs = 3_000L)
            ),
            trackKey = "t"
        )

        assertEquals(0L, source.version.value)
        // Both same-start entries must survive; ids must not collide in the index.
        assertEquals(
            listOf("one", "two"),
            source.activeCues(1_500L, epoch = 0L).map { it.text.toString() }
        )
        // Querying does not mutate the source.
        source.activeCues(1_500L, epoch = 0L)
        source.activeCues(4_500L, epoch = 0L)
        assertEquals(0L, source.version.value)
    }
}
