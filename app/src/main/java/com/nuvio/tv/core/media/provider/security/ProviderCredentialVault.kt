package com.nuvio.tv.core.media.provider.security

/**
 * Address of one provider credential. The numeric [profileId] is only used to
 * look up the immutable profile generation (see [ProfileGenerationStore]); all
 * cryptographic binding uses the generation UUID, so a deleted-and-recreated
 * profile that reuses the same numeric id can never read the old ciphertext.
 */
data class CredentialRecordMeta(
    val profileId: Int,
    val providerId: String,
    val recordId: String,
) {
    init {
        require(profileId > 0) { "profileId must be positive" }
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(recordId.isNotBlank()) { "recordId must not be blank" }
    }
}

/** Base type for vault failures; message/cause never contain plaintext. */
sealed class CredentialVaultException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class CredentialNotFoundException(record: CredentialRecordMeta) :
    CredentialVaultException("No stored credential for ${record.describe()}")

class CredentialDecryptionException(record: CredentialRecordMeta, cause: Throwable) :
    CredentialVaultException(
        "Decryption failed for ${record.describe()} (missing/invalidated key, tampered ciphertext, " +
            "or AAD mismatch across install/profile-generation/provider/record)",
        cause,
    )

private fun CredentialRecordMeta.describe() =
    "credential record (profileId=$profileId, providerId=$providerId, recordId=$recordId)"

/**
 * Profile-local BYOK credential vault.
 *
 * Properties:
 *  - secrets are encrypted with AES-256-GCM through an injectable
 *    [KeystoreBridge], one Keystore key per profile generation + provider + record;
 *  - the GCM AAD binds installation UUID + profile generation UUID + provider
 *    id + record id, so ciphertext swapped between installs, profile
 *    generations, providers or records fails authentication (fail closed);
 *  - the AAD inputs are re-resolved inside every operation, so a changed
 *    profile generation invalidates previously stored ciphertext;
 *  - there is no plaintext getter: [use] hands the decrypted secret only to
 *    the passed operation callback and returns its result; the decrypted
 *    buffer is zeroed afterwards;
 *  - [CipherTextStore] persists metadata and ciphertext only.
 */
class ProviderCredentialVault(
    private val keystoreBridge: KeystoreBridge,
    private val cipherTextStore: CipherTextStore,
    private val installIdentity: InstallIdentity,
    private val profileGenerationStore: ProfileGenerationStore,
) {

    /**
     * Encrypts [secret] under this record's address. Re-storing an existing
     * record overwrites ciphertext + metadata (re-encryption with a fresh IV);
     * the Keystore key is reused.
     */
    suspend fun store(record: CredentialRecordMeta, secret: ProviderSecret) {
        val generation = profileGenerationStore.generationOf(record.profileId)
        val aad = VaultAad.forRecord(
            installationId = installIdentity.installationId(),
            profileGenerationId = generation,
            providerId = record.providerId,
            recordId = record.recordId,
        )
        val keyAlias = keyAliasFor(record, generation)
        val payload = secret.use { plaintext ->
            keystoreBridge.encrypt(keyAlias, plaintext, aad)
        }
        cipherTextStore.put(
            storageKeyFor(record),
            EncryptedCredentialRecord.of(
                profileId = record.profileId,
                providerId = record.providerId,
                recordId = record.recordId,
                profileGenerationId = generation,
                iv = payload.iv,
                ciphertext = payload.ciphertext,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Decrypts the record and hands it to [operation] only. The plaintext is
     * never returned to the caller: the return value is whatever [operation]
     * produced. Throws [CredentialNotFoundException] when nothing is stored at
     * this address and [CredentialDecryptionException] when GCM authentication
     * fails (wrong AAD, tampered ciphertext, or missing/invalidated key).
     */
    suspend fun <R> use(
        record: CredentialRecordMeta,
        operation: (ProviderSecret) -> R,
    ): R {
        val stored = cipherTextStore.get(storageKeyFor(record))
            ?: throw CredentialNotFoundException(record)
        val plaintext = try {
            val currentGeneration = profileGenerationStore.generationOf(record.profileId)
            if (stored.profileGenerationId != currentGeneration) {
                throw IllegalStateException("Stored credential belongs to a stale profile generation")
            }
            keystoreBridge.decrypt(
                keyAlias = keyAliasFor(record, stored.profileGenerationId),
                payload = EncryptedPayload(stored.iv, stored.ciphertext),
                aad = VaultAad.forRecord(
                    installationId = installIdentity.installationId(),
                    profileGenerationId = currentGeneration,
                    providerId = record.providerId,
                    recordId = record.recordId,
                ),
            )
        } catch (cause: Exception) {
            throw CredentialDecryptionException(record, cause)
        }
        try {
            return operation(ProviderSecret.wrapVaultBuffer(plaintext))
        } finally {
            plaintext.fill(0)
        }
    }

    /** Removes ciphertext, metadata and the Keystore key for this address. */
    suspend fun delete(record: CredentialRecordMeta): Boolean {
        val stored = cipherTextStore.get(storageKeyFor(record))
        val generation = stored?.profileGenerationId
            ?: profileGenerationStore.peekGeneration(record.profileId)
        val alias = generation?.let { keyAliasFor(record, it) }
        var removedKey = false
        if (alias != null && keystoreBridge.hasKey(alias)) {
            check(keystoreBridge.deleteKey(alias)) { "Could not delete provider credential key" }
            removedKey = true
        }
        val removedCiphertext = cipherTextStore.remove(storageKeyFor(record))
        return removedCiphertext || removedKey
    }

    /** Removes every ciphertext/key belonging to [profileId], then invalidates its generation. */
    suspend fun deleteProfile(profileId: Int): Boolean {
        val matching = cipherTextStore.all().values.filter { it.profileId == profileId }
        var removed = false
        matching.forEach { stored ->
            val record = stored.meta()
            val alias = keyAliasFor(record, stored.profileGenerationId)
            if (keystoreBridge.hasKey(alias)) {
                check(keystoreBridge.deleteKey(alias)) { "Could not delete provider credential key" }
                removed = true
            }
            removed = cipherTextStore.remove(storageKeyFor(record)) || removed
        }
        return profileGenerationStore.onProfileDeleted(profileId) || removed
    }

    /** Removes all provider credentials and profile generations known to this installation. */
    suspend fun deleteAllProfiles(): Boolean {
        val all = cipherTextStore.all().values
        var removed = false
        all.forEach { stored ->
            val record = stored.meta()
            val alias = keyAliasFor(record, stored.profileGenerationId)
            if (keystoreBridge.hasKey(alias)) {
                check(keystoreBridge.deleteKey(alias)) { "Could not delete provider credential key" }
                removed = true
            }
            removed = cipherTextStore.remove(storageKeyFor(record)) || removed
        }
        keystoreBridge.aliases(KEY_ALIAS_PREFIX).forEach { alias ->
            check(keystoreBridge.deleteKey(alias)) { "Could not delete orphaned provider credential key" }
            removed = true
        }
        removed = cipherTextStore.removeCorruptEntries() > 0 || removed
        removed = profileGenerationStore.clearAll() || removed
        return removed
    }

    suspend fun contains(record: CredentialRecordMeta): Boolean =
        cipherTextStore.contains(storageKeyFor(record))

    companion object {
        internal const val STORAGE_KEY_PREFIX = "nuvio_byok_v1"
        private const val KEY_ALIAS_PREFIX = "$STORAGE_KEY_PREFIX."

        /** Stable, collision-resistant storage address independent of the Keystore alias scheme. */
        internal fun storageKeyFor(record: CredentialRecordMeta): String =
            "$STORAGE_KEY_PREFIX|p${record.profileId}|${record.providerId.aliasDigest()}|${record.recordId.aliasDigest()}"

        /** One Keystore key per profile generation + provider + record. */
        internal fun keyAliasFor(record: CredentialRecordMeta, profileGenerationId: String): String =
            "$STORAGE_KEY_PREFIX.${profileGenerationId.aliasDigest()}.${record.providerId.aliasDigest()}.${record.recordId.aliasDigest()}"

        /** Compatibility helper for tests that only need deterministic addressing. */
        internal fun keyAliasFor(record: CredentialRecordMeta): String =
            "$STORAGE_KEY_PREFIX.legacy.${record.providerId.aliasDigest()}.${record.recordId.aliasDigest()}"

        private fun String.aliasDigest(): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(toByteArray(Charsets.UTF_8))
                .take(ALIAS_DIGEST_BYTES)
                .joinToString("") { byte -> "%02x".format(byte) }

        private const val ALIAS_DIGEST_BYTES = 12
    }
}

private fun EncryptedCredentialRecord.meta() = CredentialRecordMeta(
    profileId = profileId,
    providerId = providerId,
    recordId = recordId,
)

/** Domain-separated AAD bytes bound into every GCM operation. */
internal object VaultAad {
    private const val FORMAT = "nuvio.provider-credential.aad.v1"

    fun forRecord(
        installationId: String,
        profileGenerationId: String,
        providerId: String,
        recordId: String,
    ): ByteArray = java.io.ByteArrayOutputStream().use { bytes ->
        java.io.DataOutputStream(bytes).use { output ->
            listOf(FORMAT, installationId, profileGenerationId, providerId, recordId)
                .forEach { value ->
                    val encoded = value.toByteArray(Charsets.UTF_8)
                    output.writeInt(encoded.size)
                    output.write(encoded)
                }
        }
        bytes.toByteArray()
    }
}
