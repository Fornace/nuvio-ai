package com.nuvio.tv.ui.screens.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * MutableTimedCueStore semantics: provisional coalescing, finalization immutability, epoch
 * rejection + clearing, hard bounds, per-id revision monotonicity, and epoch/window filtering.
 */
class MutableTimedCueStoreTest {

    private fun store(
        maxCues: Int = MutableTimedCueStore.DEFAULT_MAX_CUES,
        maxTextLength: Int = MutableTimedCueStore.DEFAULT_MAX_TEXT_LENGTH,
        initialEpoch: Long = 7L
    ) = MutableTimedCueStore(
        trackKey = "generated-1",
        initialEpoch = initialEpoch,
        maxCues = maxCues,
        maxTextLength = maxTextLength
    )

    private fun update(
        id: String,
        revision: Long,
        startUs: Long = 0L,
        endUs: Long = 1_000_000L,
        text: String = "cue-$id",
        epoch: Long = 7L
    ) = TimedCue(id = id, startUs = startUs, endUs = endUs, text = text, revision = revision, epoch = epoch)

    private fun accepted(result: CueMutationResult): Boolean = result == CueMutationResult.Accepted

    private fun reason(result: CueMutationResult): CueMutationResult.Reason =
        (result as CueMutationResult.Rejected).reason

    @Test
    fun `provisional updates for the same id coalesce into one entry`() {
        val store = store()
        assertTrue(accepted(store.append(update("c1", revision = 1L, text = "first pass"))))
        assertTrue(accepted(store.append(update("c1", revision = 2L, startUs = 5_000L, endUs = 9_000L, text = "better"))))
        assertTrue(accepted(store.append(update("c1", revision = 3L, startUs = 5_000L, endUs = 9_000L, text = "final draft"))))

        assertEquals(1, store.cueCount())
        val snapshot = store.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals("final draft", snapshot[0].text)
        assertEquals(3L, snapshot[0].revision)
        // Window from the latest update survived the coalescing.
        assertEquals(listOf("final draft"), store.activeCues(6_000L, epoch = 7L).map { it.text.toString() })
        assertTrue(store.activeCues(10_000L, epoch = 7L).isEmpty())
    }

    @Test
    fun `accepted mutations bump version, rejected ones do not`() {
        val store = store()
        val before = store.version.value
        assertTrue(accepted(store.append(update("c1", revision = 1L))))
        assertEquals(before + 1L, store.version.value)

        // Regression rejected: no version change.
        assertEquals(
            CueMutationResult.Reason.REVISION_REGRESSION,
            reason(store.append(update("c1", revision = 1L)))
        )
        assertEquals(before + 1L, store.version.value)
    }

    @Test
    fun `finalization freezes a cue and rejects every later update`() {
        val store = store()
        assertTrue(accepted(store.append(update("c1", revision = 5L, text = "settled"))))
        assertTrue(accepted(store.finalizeCue("c1")))
        assertEquals(1, store.finalCueCount())

        // Higher revision, lower revision: both rejected once final.
        assertEquals(
            CueMutationResult.Reason.FINAL,
            reason(store.append(update("c1", revision = 6L, text = "sneaky edit")))
        )
        assertEquals(
            CueMutationResult.Reason.FINAL,
            reason(store.append(update("c1", revision = 4L)))
        )
        // Re-finalizing is also rejected (already final).
        assertEquals(CueMutationResult.Reason.FINAL, reason(store.finalizeCue("c1")))
        // Unknown id cannot be finalized.
        assertEquals(CueMutationResult.Reason.INVALID, reason(store.finalizeCue("nope")))

        assertEquals(1, store.cueCount())
        assertEquals("settled", store.snapshot()[0].text)
        assertTrue(store.snapshot()[0].isFinal)
    }

    @Test
    fun `revision must be strictly monotonic per id`() {
        val store = store()
        assertTrue(accepted(store.append(update("c1", revision = 10L))))
        assertEquals(CueMutationResult.Reason.REVISION_REGRESSION, reason(store.append(update("c1", revision = 9L))))
        assertEquals(CueMutationResult.Reason.REVISION_REGRESSION, reason(store.append(update("c1", revision = 10L))))
        assertTrue(accepted(store.append(update("c1", revision = 11L))))
        assertEquals(11L, store.snapshot()[0].revision)
    }

    @Test
    fun `newEpoch rejects old-epoch appends and clears their provisional cues`() {
        val store = store(initialEpoch = 1L)
        assertTrue(accepted(store.append(update("old-prov", revision = 1L, epoch = 1L))))
        assertTrue(accepted(store.append(update("old-final", revision = 1L, epoch = 1L))))
        assertTrue(accepted(store.finalizeCue("old-final")))
        assertEquals(2, store.cueCount())

        assertTrue(store.newEpoch(2L))
        assertEquals(2L, store.seekEpoch())

        // Provisional old-epoch cue is gone; the final one survives (immutable).
        assertEquals(1, store.cueCount())
        assertEquals(listOf("old-final"), store.snapshot().map { it.id })
        // The surviving final is still queryable under its own production epoch,
        // and invisible to the new epoch the render loop will query with.
        assertEquals(
            listOf("cue-old-final"),
            store.activeCues(500L, epoch = 1L).map { it.text.toString() }
        )
        assertTrue(store.activeCues(500L, epoch = 2L).isEmpty())

        // Old-epoch appends are rejected outright.
        assertEquals(
            CueMutationResult.Reason.STALE_EPOCH,
            reason(store.append(update("late", revision = 1L, epoch = 1L)))
        )
        // Future epochs are rejected too: the host must advance the epoch first.
        assertEquals(
            CueMutationResult.Reason.STALE_EPOCH,
            reason(store.append(update("early", revision = 1L, epoch = 3L)))
        )
        // Current-epoch appends work again after the seek.
        assertTrue(accepted(store.append(update("fresh", revision = 1L, epoch = 2L))))
        assertEquals(2, store.cueCount())
    }

    @Test
    fun `activeCues filters by epoch and by window`() {
        val store = store(initialEpoch = 1L)
        assertTrue(accepted(store.append(update("e1-a", revision = 1L, startUs = 0L, endUs = 10_000L, epoch = 1L))))
        assertTrue(accepted(store.append(update("e1-b", revision = 1L, startUs = 5_000L, endUs = 15_000L, epoch = 1L))))
        // Cross-epoch coexistence is finals + current-epoch cues: newEpoch clears the old
        // epoch's provisional cues, so finalize them before the committed seek to epoch 2.
        assertTrue(accepted(store.finalizeCue("e1-a")))
        assertTrue(accepted(store.finalizeCue("e1-b")))
        assertTrue(store.newEpoch(2L))
        assertTrue(accepted(store.append(update("e2-a", revision = 1L, startUs = 5_000L, endUs = 15_000L, epoch = 2L))))

        // Epoch 1 view sees only its finalized cues; window boundaries are [start, end).
        assertEquals(
            listOf("cue-e1-a"),
            store.activeCues(4_999L, epoch = 1L).map { it.text.toString() }
        )
        assertEquals(
            listOf("cue-e1-a", "cue-e1-b"),
            store.activeCues(7_000L, epoch = 1L).map { it.text.toString() }
        )
        assertEquals(
            listOf("cue-e1-b"),
            store.activeCues(10_000L, epoch = 1L).map { it.text.toString() }
        )
        assertTrue(store.activeCues(15_000L, epoch = 1L).isEmpty())

        // Epoch 2 view at the same positions sees only the epoch-2 cue.
        assertEquals(
            listOf("cue-e2-a"),
            store.activeCues(7_000L, epoch = 2L).map { it.text.toString() }
        )
        assertTrue(store.activeCues(15_000L, epoch = 2L).isEmpty())
        // Epoch nobody produced for is empty.
        assertTrue(store.activeCues(7_000L, epoch = 99L).isEmpty())
    }

    @Test
    fun `max total cues is enforced`() {
        val store = store(maxCues = 2)
        assertTrue(accepted(store.append(update("a", revision = 1L))))
        assertTrue(accepted(store.append(update("b", revision = 1L))))
        assertEquals(
            CueMutationResult.Reason.CAPACITY,
            reason(store.append(update("c", revision = 1L)))
        )
        assertEquals(2, store.cueCount())

        // Updating an existing id at capacity is still allowed (coalescing adds no entry).
        assertTrue(accepted(store.append(update("a", revision = 2L))))
        assertEquals(2, store.cueCount())
    }

    @Test
    fun `max text length per cue is enforced`() {
        val store = store(maxTextLength = 5)
        assertTrue(accepted(store.append(update("a", revision = 1L, text = "12345"))))
        assertEquals(
            CueMutationResult.Reason.TEXT_TOO_LONG,
            reason(store.append(update("b", revision = 1L, text = "123456")))
        )
        assertEquals(1, store.cueCount())
    }

    @Test
    fun `malformed appends are rejected without state change`() {
        val store = store()
        assertEquals(CueMutationResult.Reason.INVALID, reason(store.append(update("", revision = 1L))))
        assertEquals(CueMutationResult.Reason.INVALID, reason(store.append(update("a", revision = 1L, text = " "))))
        assertEquals(
            CueMutationResult.Reason.INVALID,
            reason(store.append(update("a", revision = 1L, startUs = -1L)))
        )
        assertEquals(
            CueMutationResult.Reason.INVALID,
            reason(store.append(update("a", revision = 1L, startUs = 5_000L, endUs = 5_000L)))
        )
        assertEquals(0, store.cueCount())
        assertEquals(0L, store.version.value)
    }

    @Test
    fun `randomized operations agree with a brute-force oracle`() {
        val random = Random(20260903L)
        val maxCues = 30
        val maxText = 20
        val store = MutableTimedCueStore(
            trackKey = "diff",
            initialEpoch = 0L,
            maxCues = maxCues,
            maxTextLength = maxText
        )
        // Oracle mirroring the spec literally; every store result must match it exactly.
        val oracle = LinkedHashMap<String, TimedCue>()
        var oracleEpoch = 0L

        repeat(6) { round ->
            repeat(400) {
                when (random.nextInt(100)) {
                    in 0..54 -> {
                        val existingId = oracle.keys.toList().getOrNull(random.nextInt(40))
                        val id = existingId ?: "c${random.nextInt(maxCues * 3)}"
                        val existing = oracle[id]
                        val revision = if (existing != null && random.nextBoolean()) {
                            existing.revision + random.nextInt(3) // sometimes a regression
                        } else {
                            random.nextInt(5).toLong()
                        }
                        val startUs = random.nextInt(50) * 10_000L
                        val update = TimedCue(
                            id = id,
                            startUs = startUs,
                            endUs = startUs + 1L + random.nextInt(200_000),
                            text = if (random.nextInt(10) == 0) "x".repeat(maxText + 1) else "t$revision",
                            revision = revision,
                            epoch = if (random.nextInt(6) == 0) oracleEpoch - 1 - random.nextInt(2) else oracleEpoch
                        )
                        val expected = when {
                            update.epoch != oracleEpoch -> CueMutationResult.Reason.STALE_EPOCH
                            update.id.isBlank() || update.text.isBlank() -> CueMutationResult.Reason.INVALID
                            update.startUs < 0L || update.endUs <= update.startUs -> CueMutationResult.Reason.INVALID
                            update.text.length > maxText -> CueMutationResult.Reason.TEXT_TOO_LONG
                            existing != null && existing.isFinal -> CueMutationResult.Reason.FINAL
                            existing != null && update.revision <= existing.revision ->
                                CueMutationResult.Reason.REVISION_REGRESSION
                            existing == null && oracle.size >= maxCues -> CueMutationResult.Reason.CAPACITY
                            else -> null
                        }
                        val actual = store.append(update)
                        if (expected == null) {
                            assertEquals(CueMutationResult.Accepted, actual)
                            oracle[id] = update.copy(isFinal = false)
                        } else {
                            assertEquals(expected, (actual as CueMutationResult.Rejected).reason)
                        }
                    }

                    in 55..69 -> {
                        val ids = oracle.keys.toList()
                        val id = ids.getOrNull(random.nextInt(ids.size.coerceAtLeast(1))) ?: "missing"
                        val existing = oracle[id]
                        val expected = when {
                            existing == null -> CueMutationResult.Reason.INVALID
                            existing.isFinal -> CueMutationResult.Reason.FINAL
                            else -> null
                        }
                        val actual = store.finalizeCue(id)
                        if (expected == null) {
                            assertEquals(CueMutationResult.Accepted, actual)
                            oracle[id] = existing!!.copy(isFinal = true)
                        } else {
                            assertEquals(expected, (actual as CueMutationResult.Rejected).reason)
                        }
                    }

                    in 70..79 -> {
                        val nextEpoch = oracleEpoch + random.nextInt(3) - 1
                        val expectedAdvance = nextEpoch > oracleEpoch
                        assertEquals(expectedAdvance, store.newEpoch(nextEpoch))
                        if (expectedAdvance) {
                            oracleEpoch = nextEpoch
                            oracle.entries.retainAll { it.value.isFinal || it.value.epoch >= nextEpoch }
                        }
                    }

                    else -> {
                        val positionUs = random.nextInt(600) * 10_000L
                        val queryEpoch = oracleEpoch - random.nextInt(3)
                        val expectedTexts = oracle.values
                            .filter { it.epoch == queryEpoch && it.startUs <= positionUs && positionUs < it.endUs }
                            .sortedWith(compareBy({ it.startUs }, { it.id }))
                            .map { it.text }
                        assertEquals(
                            "round $round positionUs=$positionUs epoch=$queryEpoch",
                            expectedTexts,
                            store.activeCues(positionUs, queryEpoch).map { it.text.toString() }
                        )
                    }
                }
            }
            assertEquals(oracle.size, store.cueCount())
            assertEquals(
                oracle.values.sortedWith(compareBy({ it.epoch }, { it.startUs }, { it.id })),
                store.snapshot()
            )
        }
    }

    @Test
    fun `appends from a provider thread are visible and consistent to ui thread reads`() {
        val store = store()
        val appendCount = 2_000
        val readyToRead = CountDownLatch(1)
        val executor: ExecutorService = Executors.newFixedThreadPool(2)

        val appender: Future<*> = executor.submit {
            for (i in 0 until appendCount) {
                store.append(
                    update(
                        id = "c$i",
                        revision = 1L,
                        startUs = i * 10_000L,
                        endUs = i * 10_000L + 5_000L,
                        epoch = 7L
                    )
                )
                if (i == appendCount / 2) readyToRead.countDown()
            }
        }
        val reader: Future<*> = executor.submit {
            readyToRead.await()
            var queries = 0
            while (!appender.isDone) {
                // Concurrent reads must never throw or tear.
                store.activeCues(5_000L, epoch = 7L)
                store.snapshot()
                queries++
            }
            queries
        }

        appender.get(30L, TimeUnit.SECONDS)
        // Reader completing without exception proves concurrent reads never tear or throw.
        reader.get(30L, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue(executor.awaitTermination(30L, TimeUnit.SECONDS))

        assertEquals(appendCount, store.cueCount())
        // Every appended cue is queryable afterwards.
        assertEquals(
            listOf("cue-c1234"),
            store.activeCues(12_340_000L + 1L, epoch = 7L).map { it.text.toString() }
        )
    }
}
