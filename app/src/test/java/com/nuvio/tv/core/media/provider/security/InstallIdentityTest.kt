package com.nuvio.tv.core.media.provider.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class InstallIdentityTest {

    @Test
    fun `installation id is stable across reads`() {
        val storage = RecordingInstallationIdStorage()
        val identity = InstallIdentity(storage)

        val first = identity.installationId()
        val second = identity.installationId()
        val third = identity.installationId()

        assertEquals(first, second)
        assertEquals(first, third)
        assertEquals(first, identity.peek())
    }

    @Test
    fun `id is generated once and persisted exactly once`() {
        val storage = RecordingInstallationIdStorage()
        val identity = InstallIdentity(storage)

        val id = identity.installationId()
        identity.installationId()
        identity.installationId()

        assertEquals(listOf(id), storage.persistCalls)
        assertEquals(1, storage.persistCalls.size)
    }

    @Test
    fun `persisted id is reused and never regenerated`() {
        val storage = RecordingInstallationIdStorage(initial = "pre-seeded-install-id")
        val identity = InstallIdentity(storage) { error("generator must not be called") }

        assertEquals("pre-seeded-install-id", identity.installationId())
        assertEquals(0, storage.persistCalls.size)
    }

    @Test
    fun `random default generator produces distinct ids per installation`() {
        val idA = InstallIdentity(InMemoryInstallationIdStorage()).installationId()
        val idB = InstallIdentity(InMemoryInstallationIdStorage()).installationId()

        assertNotEquals(idA, idB)
        // Default generator produces UUIDs.
        assertEquals(idA, UUID.fromString(idA).toString())
    }

    @Test
    fun `generator is injectable and pure`() {
        val identity = InstallIdentity(RecordingInstallationIdStorage()) { "deterministic-install-id" }

        assertEquals("deterministic-install-id", identity.installationId())
    }

    @Test
    fun `storage survives construction of a new identity instance`() {
        val storage = RecordingInstallationIdStorage()
        val first = InstallIdentity(storage).installationId()

        val reloaded = InstallIdentity(storage).installationId()

        assertEquals(first, reloaded)
    }

    @Test
    fun `concurrent first access yields a single installation id`() {
        val storage = RecordingInstallationIdStorage()
        val identity = InstallIdentity(storage)
        val threads = 8
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)

        try {
            val futures = (1..threads).map {
                pool.submit<String> {
                    ready.countDown()
                    go.await()
                    identity.installationId()
                }
            }
            ready.await()
            go.countDown()
            val ids = futures.map { it.get() }.toSet()
            assertEquals(1, ids.size)
            assertEquals(1, storage.persistCalls.size)
        } finally {
            pool.shutdownNow()
        }
    }
}
