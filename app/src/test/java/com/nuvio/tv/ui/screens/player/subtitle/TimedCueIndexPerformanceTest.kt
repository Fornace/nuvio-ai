@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player.subtitle

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Index performance gate: 5000 cues, 200 random position queries, total wall time under 2
 * seconds on any developer machine. The bound is deliberately generous — it exists to prove
 * there is no per-query full-list re-sort or full-copy (a plain linear membership scan of 5000
 * cues x 200 queries would still pass; allocation storms and per-query rebuilds would not).
 * Correctness is asserted for a handful of fixed positions against an independently computed
 * oracle.
 */
class TimedCueIndexPerformanceTest {

    @Test
    fun `static source answers 200 random queries over 5000 cues under 2 seconds`() {
        val cueCount = 5_000
        val queries = 200
        val seed = 20260901L
        val random = Random(seed)
        val cues = ArrayList<CuesWithTiming>(cueCount)
        for (i in 0 until cueCount) {
            val window = OverlappingCuePattern.window(i, random)
            cues.add(
                CuesWithTiming(
                    listOf(Cue.Builder().setText("perf-$i").build()),
                    window.first,
                    (window.last + 1L) - window.first
                )
            )
        }

        val source = StaticFileCueSource(cues, trackKey = "perf-static")
        val positions = IntArray(queries) { random.nextInt(12_000_000) }.map { it.toLong() }

        val started = System.nanoTime()
        val results = positions.map { source.activeCues(it, epoch = 0L) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertEquals(queries, results.size)
        assertTrue(
            "200 queries over $cueCount static cues took ${elapsedMs}ms; expected < 2000ms",
            elapsedMs < 2_000L
        )

        // Correctness oracle at fixed positions (a linear rescan is fine for the oracle itself).
        val oracleRandom = Random(seed)
        for (probe in listOf(0L, 1_000_000L, 2_400_000L, 2_500_000L, 3_100_000L, 11_999_999L)) {
            val expected = OverlappingCuePattern.activeIdsAt(probe, cueCount, oracleRandom)
            val actual = source.activeCues(probe, epoch = 0L).map { it.text.toString() }
            assertEquals("mismatch at positionUs=$probe", expected, actual)
        }
    }

    @Test
    fun `mutable store answers 200 random queries over 5000 cues under 2 seconds`() {
        val cueCount = 5_000
        val queries = 200
        val seed = 20260902L
        val random = Random(seed)
        val store = MutableTimedCueStore(trackKey = "perf-generated")
        for (i in 0 until cueCount) {
            val window = OverlappingCuePattern.window(i, random)
            val result = store.append(
                TimedCue(
                    id = "perf-$i",
                    startUs = window.first,
                    endUs = window.last + 1L,
                    text = "perf-$i",
                    revision = 1L,
                    epoch = 0L
                )
            )
            assertEquals(CueMutationResult.Accepted, result)
        }
        assertEquals(cueCount, store.cueCount())

        val positions = IntArray(queries) { random.nextInt(12_000_000) }.map { it.toLong() }
        val started = System.nanoTime()
        val results = positions.map { store.activeCues(it, epoch = 0L) }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L

        assertEquals(queries, results.size)
        assertTrue(
            "200 queries over $cueCount mutable cues took ${elapsedMs}ms; expected < 2000ms",
            elapsedMs < 2_000L
        )

        // Same [start, end) window semantics as the static adapter, checked against the oracle.
        val oracleRandom = Random(seed)
        for (probe in listOf(0L, 1_000_000L, 2_400_000L, 2_500_000L, 3_100_000L, 11_999_999L)) {
            val expected = OverlappingCuePattern.activeIdsAt(probe, cueCount, oracleRandom)
            val actual = store.activeCues(probe, epoch = 0L).map { it.text.toString() }
            assertEquals("mismatch at positionUs=$probe", expected, actual)
        }
    }
}
