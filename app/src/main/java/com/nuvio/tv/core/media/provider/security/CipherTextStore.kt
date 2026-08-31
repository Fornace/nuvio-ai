package com.nuvio.tv.core.media.provider.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Metadata + authenticated ciphertext for one stored provider credential.
 * Never contains plaintext. Arrays are defensively copied on construction.
 */
class EncryptedCredentialRecord(
    val profileId: Int,
    val providerId: String,
    val recordId: String,
    val profileGenerationId: String,
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val createdAtMillis: Long,
) {
    fun snapshot(): EncryptedCredentialRecord = EncryptedCredentialRecord(
        profileId = profileId,
        providerId = providerId,
        recordId = recordId,
        profileGenerationId = profileGenerationId,
        iv = iv.copyOf(),
        ciphertext = ciphertext.copyOf(),
        createdAtMillis = createdAtMillis,
    )

    override fun toString(): String =
        "EncryptedCredentialRecord(profileId=$profileId, providerId=$providerId, " +
            "recordId=$recordId, profileGenerationId=$profileGenerationId, " +
            "ivLength=${iv.size}, ciphertextLength=${ciphertext.size}, " +
            "createdAtMillis=$createdAtMillis)"

    companion object {
        fun of(
            profileId: Int,
            providerId: String,
            recordId: String,
            profileGenerationId: String,
            iv: ByteArray,
            ciphertext: ByteArray,
            createdAtMillis: Long,
        ) = EncryptedCredentialRecord(
            profileId = profileId,
            providerId = providerId,
            recordId = recordId,
            profileGenerationId = profileGenerationId,
            iv = iv.copyOf(),
            ciphertext = ciphertext.copyOf(),
            createdAtMillis = createdAtMillis,
        )
    }
}

/**
 * Injectable persistence for ciphertext + metadata. In this round the only
 * implementation is the in-memory [InMemoryCipherTextStore]; a DataStore-backed
 * implementation is the documented wiring point for a later lane. Whatever
 * implements it must persist metadata and ciphertext only — never plaintext.
 */
interface CipherTextStore {
    suspend fun put(key: String, record: EncryptedCredentialRecord)

    suspend fun get(key: String): EncryptedCredentialRecord?

    suspend fun remove(key: String): Boolean

    suspend fun contains(key: String): Boolean
}

/** Process-local store; put/get hand out defensive snapshots. */
class InMemoryCipherTextStore : CipherTextStore {
    private val records = ConcurrentHashMap<String, EncryptedCredentialRecord>()

    override suspend fun put(key: String, record: EncryptedCredentialRecord) {
        records[key] = record.snapshot()
    }

    override suspend fun get(key: String): EncryptedCredentialRecord? = records[key]?.snapshot()

    override suspend fun remove(key: String): Boolean = records.remove(key) != null

    override suspend fun contains(key: String): Boolean = records.containsKey(key)
}
