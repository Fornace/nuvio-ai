package com.nuvio.tv.core.media.provider.security

import java.util.UUID

/**
 * Injectable persistence for profile generations. Semantics required from any
 * implementation: [loadAll] returns the currently persisted id -> generation
 * map; [persistAll] atomically replaces it. A DataStore-backed implementation
 * is the wiring point for a later lane.
 */
interface ProfileGenerationStorage {
    fun loadAll(): Map<Int, String>

    fun persistAll(generations: Map<Int, String>)
}

/** Process-local default used until DataStore wiring lands. */
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
 * Integration point (deliberately not wired in this round — the callers live
 * outside this lane's ownership): `ProfileManager.createProfile` /
 * `ProfileDataStore.upsertProfile` may pre-assign a generation via
 * [generationOf]; `ProfileManager.deleteProfileDataAsync` /
 * `ProfileDataStore.deleteProfile` must call [onProfileDeleted] alongside the
 * existing per-profile file cleanup.
 */
class ProfileGenerationStore(
    private val storage: ProfileGenerationStorage = InMemoryProfileGenerationStorage(),
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

    private fun persistSnapshot() = storage.persistAll(HashMap(generations))
}
