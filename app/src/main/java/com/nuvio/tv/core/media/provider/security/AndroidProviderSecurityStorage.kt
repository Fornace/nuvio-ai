package com.nuvio.tv.core.media.provider.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** App-private durable storage for AI provider security metadata and ciphertext. */
private const val PREFERENCES_NAME = "ai_media_provider_security_v1"
private const val INSTALLATION_ID_KEY = "installation_id"
private const val PROFILE_GENERATIONS_KEY = "profile_generations"
private const val CIPHERTEXT_PREFIX = "ciphertext|"

private val storageJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
private data class StoredEncryptedCredential(
    val profileId: Int,
    val providerId: String,
    val recordId: String,
    val profileGenerationId: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val createdAtMillis: Long,
)

@Singleton
class AndroidInstallationIdStorage @Inject constructor(
    @ApplicationContext context: Context,
) : InstallationIdStorage {
    private val preferences = preferences(context)

    override fun load(): String? = preferences.getString(INSTALLATION_ID_KEY, null)
        ?.takeIf(String::isNotBlank)

    override fun persist(installationId: String) {
        require(installationId.isNotBlank()) { "installationId must not be blank" }
        check(preferences.edit().putString(INSTALLATION_ID_KEY, installationId).commit()) {
            "Could not persist installation identity"
        }
    }
}

@Singleton
class AndroidProfileGenerationStorage @Inject constructor(
    @ApplicationContext context: Context,
) : ProfileGenerationStorage {
    private val preferences = preferences(context)
    private val lock = Any()

    override fun loadAll(): Map<Int, String> = synchronized(lock) {
        val encoded = preferences.getString(PROFILE_GENERATIONS_KEY, null) ?: return@synchronized emptyMap()
        runCatching { storageJson.decodeFromString<Map<String, String>>(encoded) }
            .getOrDefault(emptyMap())
            .mapNotNull { (profileId, generation) ->
                profileId.toIntOrNull()?.takeIf { it > 0 }
                    ?.let { it to generation.takeIf(String::isNotBlank) }
            }
            .mapNotNull { (profileId, generation) -> generation?.let { profileId to it } }
            .toMap()
    }

    override fun persistAll(generations: Map<Int, String>) = synchronized(lock) {
        val encoded = storageJson.encodeToString(
            generations.entries.associate { (profileId, generation) ->
                require(profileId > 0) { "profileId must be positive" }
                require(generation.isNotBlank()) { "profile generation must not be blank" }
                profileId.toString() to generation
            }
        )
        check(preferences.edit().putString(PROFILE_GENERATIONS_KEY, encoded).commit()) {
            "Could not persist profile generations"
        }
    }
}

@Singleton
class AndroidCipherTextStore @Inject constructor(
    @ApplicationContext context: Context,
) : CipherTextStore {
    private val preferences = preferences(context)
    private val lock = Any()

    override suspend fun put(key: String, record: EncryptedCredentialRecord) = synchronized(lock) {
        val encoded = storageJson.encodeToString(record.toStored())
        check(preferences.edit().putString(preferenceKey(key), encoded).commit()) {
            "Could not persist encrypted provider credential"
        }
    }

    override suspend fun get(key: String): EncryptedCredentialRecord? = synchronized(lock) {
        decode(preferences.getString(preferenceKey(key), null))
    }

    override suspend fun remove(key: String): Boolean = synchronized(lock) {
        val preferenceKey = preferenceKey(key)
        val existed = preferences.contains(preferenceKey)
        if (existed) {
            check(preferences.edit().remove(preferenceKey).commit()) {
                "Could not remove encrypted provider credential"
            }
        }
        existed
    }

    override suspend fun contains(key: String): Boolean = synchronized(lock) {
        preferences.contains(preferenceKey(key))
    }

    override suspend fun all(): Map<String, EncryptedCredentialRecord> = synchronized(lock) {
        preferences.all.entries.mapNotNull { (storedKey, value) ->
            if (!storedKey.startsWith(CIPHERTEXT_PREFIX)) return@mapNotNull null
            decode(value as? String)?.let { storedKey.removePrefix(CIPHERTEXT_PREFIX) to it }
        }.toMap()
    }

    override suspend fun removeCorruptEntries(): Int = synchronized(lock) {
        val corruptKeys = preferences.all.entries.mapNotNull { (storedKey, value) ->
            storedKey.takeIf {
                it.startsWith(CIPHERTEXT_PREFIX) && decode(value as? String) == null
            }
        }
        if (corruptKeys.isEmpty()) return@synchronized 0
        val editor = preferences.edit()
        corruptKeys.forEach(editor::remove)
        check(editor.commit()) { "Could not remove corrupt provider credentials" }
        corruptKeys.size
    }

    private fun decode(encoded: String?): EncryptedCredentialRecord? {
        if (encoded == null) return null
        return runCatching {
            val stored = storageJson.decodeFromString<StoredEncryptedCredential>(encoded)
            EncryptedCredentialRecord.of(
                profileId = stored.profileId,
                providerId = stored.providerId,
                recordId = stored.recordId,
                profileGenerationId = stored.profileGenerationId,
                iv = Base64.decode(stored.ivBase64, Base64.NO_WRAP),
                ciphertext = Base64.decode(stored.ciphertextBase64, Base64.NO_WRAP),
                createdAtMillis = stored.createdAtMillis,
            ).also {
                require(it.profileId > 0 && it.providerId.isNotBlank() && it.recordId.isNotBlank())
                require(it.profileGenerationId.isNotBlank() && it.iv.isNotEmpty() && it.ciphertext.isNotEmpty())
            }
        }.getOrNull()
    }

    private fun EncryptedCredentialRecord.toStored() = StoredEncryptedCredential(
        profileId = profileId,
        providerId = providerId,
        recordId = recordId,
        profileGenerationId = profileGenerationId,
        ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
        ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        createdAtMillis = createdAtMillis,
    )

    private fun preferenceKey(key: String): String = CIPHERTEXT_PREFIX + key
}

private fun preferences(context: Context): SharedPreferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
