package com.nuvio.tv.core.media.provider.security

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Pure-JVM [KeystoreBridge] fake: real AES-256-GCM via the JDK provider with
 * in-memory keys and random IVs, so authentication (and AAD binding) behaves
 * exactly like the production crypto without the Android Keystore.
 */
class FakeKeystoreBridge : KeystoreBridge {
    private val keys = mutableMapOf<String, SecretKey>()

    val deletedAliases = mutableListOf<String>()
    val encryptAliases = mutableListOf<String>()

    @Synchronized
    override fun encrypt(keyAlias: String, plaintext: ByteArray, aad: ByteArray): EncryptedPayload {
        encryptAliases += keyAlias
        val key = keys.getOrPut(keyAlias) {
            KeyGenerator.getInstance("AES").apply { init(AES_KEY_BITS) }.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key) // no explicit IV -> fresh random IV per call
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv ?: error("JVM provider produced no IV")
        return EncryptedPayload(iv, ciphertext)
    }

    @Synchronized
    override fun decrypt(keyAlias: String, payload: EncryptedPayload, aad: ByteArray): ByteArray {
        val key = keys[keyAlias] ?: error("no key for alias $keyAlias")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, payload.iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(payload.ciphertext)
    }

    @Synchronized
    override fun hasKey(keyAlias: String): Boolean = keys.containsKey(keyAlias)

    @Synchronized
    override fun deleteKey(keyAlias: String): Boolean {
        deletedAliases += keyAlias
        return keys.remove(keyAlias) != null
    }

    companion object {
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}

/**
 * [CipherTextStore] fake that stores raw references (no defensive copies) so
 * tests can observe the exact stored bytes and simulate tampering/swap
 * attacks directly against the persisted arrays.
 */
class RecordingCipherTextStore : CipherTextStore {
    val records = LinkedHashMap<String, EncryptedCredentialRecord>()

    override suspend fun put(key: String, record: EncryptedCredentialRecord) {
        records[key] = record
    }

    override suspend fun get(key: String): EncryptedCredentialRecord? = records[key]

    override suspend fun remove(key: String): Boolean = records.remove(key) != null

    override suspend fun contains(key: String): Boolean = records.containsKey(key)
}

/** [InstallationIdStorage] fake that records persist calls. */
class RecordingInstallationIdStorage(initial: String? = null) : InstallationIdStorage {
    val persistCalls = mutableListOf<String>()

    @Volatile
    private var value: String? = initial

    override fun load(): String? = value

    override fun persist(installationId: String) {
        persistCalls += installationId
        value = installationId
    }
}

/** [ProfileGenerationStorage] fake that records every persisted snapshot. */
class RecordingProfileGenerationStorage : ProfileGenerationStorage {
    val persistedSnapshots = mutableListOf<Map<Int, String>>()

    @Volatile
    private var map: Map<Int, String> = emptyMap()

    override fun loadAll(): Map<Int, String> = map

    override fun persistAll(generations: Map<Int, String>) {
        persistedSnapshots += generations
        map = generations
    }
}

/**
 * Compiled bytecode of the production classes in the security package, loaded
 * by resource name (AGP packages main classes into a jar, so class-directory
 * listing is not reliable). Used for negative hygiene assertions: no DI
 * module references, no android.util.Log usage, etc.
 */
internal val SECURITY_MAIN_CLASSES = listOf(
    "ProviderCredentialVault",
    "ProviderSecret",
    "KeystoreBridge",
    "AndroidKeystoreBridge",
    "EncryptedPayload",
    "CipherTextStore",
    "EncryptedCredentialRecord",
    "InMemoryCipherTextStore",
    "CredentialRecordMeta",
    "CredentialVaultException",
    "CredentialNotFoundException",
    "CredentialDecryptionException",
    "VaultAad",
    "InstallIdentity",
    "InstallationIdStorage",
    "InMemoryInstallationIdStorage",
    "ProfileGenerationStore",
    "ProfileGenerationStorage",
    "InMemoryProfileGenerationStorage",
    "ProviderTlsClientFactory",
)

internal fun securityPackageClassBytes(): List<Pair<String, ByteArray>> {
    val loader = ProviderCredentialVault::class.java.classLoader
        ?: error("no classloader available")
    return SECURITY_MAIN_CLASSES.mapNotNull { name ->
        loader.getResourceAsStream("com/nuvio/tv/core/media/provider/security/$name.class")
            ?.use { stream -> name to stream.readBytes() }
    }
}
