package com.nuvio.tv.core.media.provider.security

import java.util.UUID

/** Injectable durable persistence for the installation UUID. */
interface InstallationIdStorage {
    fun load(): String?

    fun persist(installationId: String)
}

/** Process-local implementation for unit tests. */
class InMemoryInstallationIdStorage(initial: String? = null) : InstallationIdStorage {
    @Volatile
    private var value: String? = initial

    override fun load(): String? = value

    override fun persist(installationId: String) {
        value = installationId
    }
}

/**
 * Stable per-installation random UUID. Generated once (via the injectable,
 * pure [generator]), persisted through [InstallationIdStorage], and then
 * immutable for the installation's lifetime. Feeds the vault AAD so ciphertext
 * copied to another install/backed-up and restored elsewhere cannot decrypt.
 */
class InstallIdentity(
    private val storage: InstallationIdStorage,
    private val generator: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()

    @Volatile
    private var cached: String? = null

    /** Returns the installation UUID, creating and persisting it on first use. */
    fun installationId(): String {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val persisted = storage.load()
            val id = if (persisted != null) {
                persisted
            } else {
                generator().also { storage.persist(it) }
            }
            cached = id
            return id
        }
    }

    /** Current value without generating; useful for diagnostics/tests. */
    fun peek(): String? = cached ?: storage.load()
}
