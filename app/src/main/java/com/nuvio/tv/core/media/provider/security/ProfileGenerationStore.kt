package com.nuvio.tv.core.media.provider.security

import java.util.UUID

/** Injectable durable persistence for profile generation identifiers. */
interface ProfileGenerationStorage {
    fun loadAll(): Map<Int, String>

    fun persistAll(generations: Map<Int, String>)
}

/** Process-local implementation for unit tests. */
class InMemoryProfileGenerationStorage : ProfileGenerationStorage {
    private val lock = Any()

    @Volatile
    private var map: Map<Int, String> = emptyMap()

    override fun loadAll(): Map<Int, String> = synchronized(lock) { map.toMap() }

    override fun persistAll(generations: Map<Int, String>) {
        synchronized(lock) { map = generations.toMap() }
    }
}

/**
 * Assigns every profile id an immutable generation UUID, created lazily on
 * first use ("at creation" of the credential relationship) and stable for as
 * long as the profile exists.
 *
 * Numeric profile ids are recycled by the app's profile manager; when a profile
 * is deleted, [onProfileDeleted] clears its generation, so the next profile
 * that reuses the same numeric id receives a different generation UUID. The
 * vault binds this UUID into AAD, which is what makes old ciphertext unreadable
 * after id reuse.
 *
 * ProfileManager pre-assigns a generation when creating a profile and clears it
 * after the vault has removed all profile credentials during profile deletion.
 */
class ProfileGenerationStore(
    private val storage: ProfileGenerationStorage,
    private val generator: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Any()

    private val generations: MutableMap<Int, String> = HashMap(storage.loadAll())

    /**
     * Returns the generation UUID for [profileId], assigning and persisting a
     * fresh one if (and only if) none exists yet. Once assigned, the value is
     * immutable until [onProfileDeleted] is called for that id.
     */
    fun generationOf(profileId: Int): String {
        synchronized(lock) {
            generations[profileId]?.let { return it }
            val generated = generator()
            generations[profileId] = generated
            persistSnapshot()
            return generated
        }
    }

    /** Queries the generation without ever creating one. */
    fun peekGeneration(profileId: Int): String? = synchronized(lock) { generations[profileId] }

    fun hasGeneration(profileId: Int): Boolean = synchronized(lock) { profileId in generations }

    /**
     * Clears the generation after profile deletion. Returns whether a
     * generation existed. The same numeric id, when reused, will get a new
     * generation from the next [generationOf] call.
     */
    fun onProfileDeleted(profileId: Int): Boolean {
        synchronized(lock) {
            val removed = generations.remove(profileId) != null
            if (removed) {
                persistSnapshot()
            }
            return removed
        }
    }

    /** Clears every generation. Used only after all matching ciphertext and keys are removed. */
    fun clearAll(): Boolean {
        synchronized(lock) {
            if (generations.isEmpty()) return false
            generations.clear()
            persistSnapshot()
            return true
        }
    }

    private fun persistSnapshot() = storage.persistAll(HashMap(generations))
}
