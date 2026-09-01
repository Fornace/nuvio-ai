package com.nuvio.tv.core.media.provider.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ProfileGenerationStoreTest {

    @Test
    fun `generation is stable across reads and queryable by profile id`() {
        val store = ProfileGenerationStore(InMemoryProfileGenerationStorage())

        val first = store.generationOf(2)
        val second = store.generationOf(2)

        assertEquals(first, second)
        assertEquals(first, store.peekGeneration(2))
        assertTrue(store.hasGeneration(2))
    }

    @Test
    fun `distinct profiles get distinct generations`() {
        val store = ProfileGenerationStore(InMemoryProfileGenerationStorage())

        val profile2 = store.generationOf(2)
        val profile3 = store.generationOf(3)

        assertNotEquals(profile2, profile3)
    }

    @Test
    fun `peek never assigns a generation`() {
        val store = ProfileGenerationStore(RecordingProfileGenerationStorage())

        assertNull(store.peekGeneration(7))
        assertFalse(store.hasGeneration(7))

        val assigned = store.generationOf(7)

        assertEquals(assigned, store.peekGeneration(7))
    }

    @Test
    fun `same numeric profile id after delete gets a different generation`() {
        val store = ProfileGenerationStore(InMemoryProfileGenerationStorage())

        val before = store.generationOf(2)
        assertTrue(store.onProfileDeleted(2))
        assertFalse(store.hasGeneration(2))

        val after = store.generationOf(2)

        assertNotEquals(before, after)
        assertEquals(after, store.generationOf(2))
    }

    @Test
    fun `deleting an unknown profile returns false and changes nothing`() {
        val storage = RecordingProfileGenerationStorage()
        val store = ProfileGenerationStore(storage)
        store.generationOf(2)

        assertFalse(store.onProfileDeleted(9))

        // Only the single assignment snapshot was persisted; the failed delete
        // must not have written anything.
        assertEquals(1, storage.persistedSnapshots.size)
        assertTrue(storage.persistedSnapshots.single().containsKey(2))
    }

    @Test
    fun `deletion is persisted and survives reload from storage`() {
        val storage = InMemoryProfileGenerationStorage()
        val first = ProfileGenerationStore(storage)
        val generation = first.generationOf(4)
        first.onProfileDeleted(4)

        val reloaded = ProfileGenerationStore(storage)

        assertFalse(reloaded.hasGeneration(4))
        assertNull(reloaded.peekGeneration(4))
        assertNotEquals(generation, reloaded.generationOf(4))
    }

    @Test
    fun `generations survive reload from storage without reassignment`() {
        val storage = InMemoryProfileGenerationStorage()
        val first = ProfileGenerationStore(storage)
        val generation = first.generationOf(4)

        val reloaded = ProfileGenerationStore(storage)

        assertEquals(generation, reloaded.generationOf(4))
        assertEquals(generation, reloaded.peekGeneration(4))
    }

    @Test
    fun `generator is injectable and only runs on assignment`() {
        val values = ArrayDeque(listOf("gen-a", "gen-b", "gen-c"))
        val store = ProfileGenerationStore(InMemoryProfileGenerationStorage()) { values.removeFirst() }

        assertEquals("gen-a", store.generationOf(1))
        assertEquals("gen-a", store.generationOf(1)) // no reassignment
        assertEquals("gen-b", store.generationOf(2))

        store.onProfileDeleted(1)
        assertEquals("gen-c", store.generationOf(1)) // reused numeric id -> fresh generation
    }

    @Test
    fun `clear all removes every generation and survives reload`() {
        val storage = InMemoryProfileGenerationStorage()
        val store = ProfileGenerationStore(storage)
        store.generationOf(2)
        store.generationOf(3)

        assertTrue(store.clearAll())
        assertFalse(store.clearAll())

        val reloaded = ProfileGenerationStore(storage)
        assertFalse(reloaded.hasGeneration(2))
        assertFalse(reloaded.hasGeneration(3))
    }

    @Test
    fun `concurrent assignment for one profile id yields a single generation`() {
        val store = ProfileGenerationStore(InMemoryProfileGenerationStorage())
        val threads = 8
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)

        try {
            val futures = (1..threads).map {
                pool.submit<String> {
                    ready.countDown()
                    go.await()
                    store.generationOf(5)
                }
            }
            ready.await()
            go.countDown()
            val generations = futures.map { it.get() }.toSet()
            assertEquals(1, generations.size)
        } finally {
            pool.shutdownNow()
        }
    }
}
