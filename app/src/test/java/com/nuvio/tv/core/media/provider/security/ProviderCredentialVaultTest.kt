package com.nuvio.tv.core.media.provider.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.AEADBadTagException

private const val SECRET = "sk-lane-b-canary-plaintext-never-to-be-exposed"
private const val OTHER_SECRET = "sk-lane-b-canary-replacement-plaintext"

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun Throwable.hasAeadTagFailureInChain(): Boolean =
    generateSequence(this) { it.cause }.any { it is AEADBadTagException }

private class VaultHarness {
    val installationStorage = InMemoryInstallationIdStorage()
    val profileGenerationStorage = InMemoryProfileGenerationStorage()
    val profileGenerations = ProfileGenerationStore(profileGenerationStorage)
    val keystore = FakeKeystoreBridge()
    val cipherStore = RecordingCipherTextStore()
    val vault = ProviderCredentialVault(
        keystoreBridge = keystore,
        cipherTextStore = cipherStore,
        installIdentity = InstallIdentity(installationStorage),
        profileGenerationStore = profileGenerations,
    )

    /** Same keystore + ciphertext store, but a freshly generated install id. */
    fun vaultForOtherInstallation(): ProviderCredentialVault =
        ProviderCredentialVault(
            keystoreBridge = keystore,
            cipherTextStore = cipherStore,
            installIdentity = InstallIdentity(InMemoryInstallationIdStorage()),
            profileGenerationStore = profileGenerations,
        )
}

class ProviderCredentialVaultTest {

    private lateinit var harness: VaultHarness

    private val meta = CredentialRecordMeta(profileId = 3, providerId = "openai", recordId = "primary")

    @Before
    fun setUp() {
        harness = VaultHarness()
    }

    private fun storageKey() = ProviderCredentialVault.storageKeyFor(meta)

    private fun storeSecret(secret: String = SECRET) = runBlocking {
        harness.vault.store(meta, ProviderSecret.copyOf(secret.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun storeThenUseRoundTripsSecretAndStoresMetadataOnly() = runBlocking {
        storeSecret()

        val digest = harness.vault.use(meta) { secret -> secret.use(::sha256Hex) }
        assertEquals(sha256Hex(SECRET.toByteArray(Charsets.UTF_8)), digest)

        val record = harness.cipherStore.records.values.single()
        assertEquals(3, record.profileId)
        assertEquals("openai", record.providerId)
        assertEquals("primary", record.recordId)
        assertEquals(
            harness.profileGenerations.peekGeneration(3),
            record.profileGenerationId,
        )
        // Nothing persisted (ciphertext, metadata, toString) contains plaintext.
        val persistedText = String(record.ciphertext, Charsets.ISO_8859_1) + record.toString()
        assertFalse(persistedText.contains(SECRET))
    }

    @Test
    fun twoEncryptionsOfTheSameSecretDiffer() = runBlocking {
        storeSecret()
        val first = harness.cipherStore.records[storageKey()]!!.snapshot()

        storeSecret()
        val second = harness.cipherStore.records[storageKey()]!!

        assertFalse("IVs must differ across encryptions", first.iv.contentEquals(second.iv))
        assertFalse("Ciphertexts must differ across encryptions", first.ciphertext.contentEquals(second.ciphertext))
        assertEquals(1, harness.cipherStore.records.size)

        // Distinct records with the same secret also produce distinct ciphertext.
        val otherRecord = CredentialRecordMeta(profileId = 3, providerId = "openai", recordId = "secondary")
        harness.vault.store(otherRecord, ProviderSecret.copyOf(SECRET.toByteArray(Charsets.UTF_8)))
        val otherPayload = harness.cipherStore.records[ProviderCredentialVault.storageKeyFor(otherRecord)]!!
        assertFalse(second.ciphertext.contentEquals(otherPayload.ciphertext))

        // And the overwritten record still decrypts correctly.
        val digest = harness.vault.use(meta) { secret -> secret.use(::sha256Hex) }
        assertEquals(sha256Hex(SECRET.toByteArray(Charsets.UTF_8)), digest)
    }

    @Test
    fun useFailsWhenInstallationIdentityDiffers() = runBlocking {
        storeSecret()

        try {
            harness.vaultForOtherInstallation().use(meta) { fail("must not reach the operation") }
            fail("Expected CredentialDecryptionException")
        } catch (expected: CredentialDecryptionException) {
            assertTrue(expected.hasAeadTagFailureInChain())
        }
    }

    @Test
    fun useFailsWhenProfileGenerationChangedForSameNumericId() = runBlocking {
        storeSecret()
        val generationBefore = harness.profileGenerations.peekGeneration(3)

        assertTrue(harness.profileGenerations.onProfileDeleted(3))
        // Same numeric profile id reused after deletion -> fresh generation UUID.

        try {
            harness.vault.use(meta) { fail("must not reach the operation") }
            fail("Expected CredentialDecryptionException")
        } catch (expected: CredentialDecryptionException) {
            assertTrue(expected.hasAeadTagFailureInChain())
            assertNotEquals(generationBefore, harness.profileGenerations.peekGeneration(3))
        }
    }

    @Test
    fun useFailsWhenCiphertextIsSwappedAcrossRecords() = runBlocking {
        val donor = CredentialRecordMeta(profileId = 3, providerId = "deepgram", recordId = "primary")
        val victim = CredentialRecordMeta(profileId = 3, providerId = "openai", recordId = "primary")
        harness.vault.store(donor, ProviderSecret.copyOf(SECRET.toByteArray(Charsets.UTF_8)))
        harness.vault.store(victim, ProviderSecret.copyOf(OTHER_SECRET.toByteArray(Charsets.UTF_8)))

        // Swap attack: donor ciphertext + IV copied over the victim's slot.
        harness.cipherStore.records[ProviderCredentialVault.storageKeyFor(victim)] =
            harness.cipherStore.records[ProviderCredentialVault.storageKeyFor(donor)]!!

        try {
            harness.vault.use(victim) { secret -> secret.use(::sha256Hex) }
            fail("Expected CredentialDecryptionException")
        } catch (expected: CredentialDecryptionException) {
            assertTrue(expected.hasAeadTagFailureInChain())
        }
    }

    @Test
    fun useFailsWhenCiphertextIsSwappedAcrossProfiles() = runBlocking {
        val donor = CredentialRecordMeta(profileId = 3, providerId = "openai", recordId = "primary")
        val victim = CredentialRecordMeta(profileId = 4, providerId = "openai", recordId = "primary")
        harness.vault.store(donor, ProviderSecret.copyOf(SECRET.toByteArray(Charsets.UTF_8)))
        harness.vault.store(victim, ProviderSecret.copyOf(OTHER_SECRET.toByteArray(Charsets.UTF_8)))

        harness.cipherStore.records[ProviderCredentialVault.storageKeyFor(victim)] =
            harness.cipherStore.records[ProviderCredentialVault.storageKeyFor(donor)]!!

        try {
            harness.vault.use(victim) { fail("must not reach the operation") }
            fail("Expected CredentialDecryptionException")
        } catch (expected: CredentialDecryptionException) {
            assertTrue(expected.hasAeadTagFailureInChain())
        }
    }

    @Test
    fun tamperedCiphertextFailsClosed() = runBlocking {
        storeSecret()
        val record = harness.cipherStore.records[storageKey()]!!
        record.ciphertext[0] = (record.ciphertext[0].toInt() xor 0x01).toByte()

        try {
            harness.vault.use(meta) { fail("must not reach the operation") }
            fail("Expected CredentialDecryptionException")
        } catch (expected: CredentialDecryptionException) {
            assertTrue(expected.hasAeadTagFailureInChain())
        }
    }

    @Test
    fun tamperedIvFailsClosed() = runBlocking {
        storeSecret()
        val record = harness.cipherStore.records[storageKey()]!!
        record.iv[0] = (record.iv[0].toInt() xor 0x01).toByte()

        try {
            harness.vault.use(meta) { fail("must not reach the operation") }
            fail("Expected CredentialDecryptionException")
        } catch (expected: CredentialDecryptionException) {
            assertTrue(expected.hasAeadTagFailureInChain())
        }
    }

    @Test
    fun missingKeystoreKeyFailsClosed() = runBlocking {
        storeSecret()
        assertTrue(harness.keystore.deleteKey(ProviderCredentialVault.keyAliasFor(meta)))

        try {
            harness.vault.use(meta) { fail("must not reach the operation") }
            fail("Expected CredentialDecryptionException")
        } catch (expected: CredentialDecryptionException) {
            // Key unavailable is surfaced as a decryption failure, never as plaintext passthrough.
        }
    }

    @Test
    fun useHidesPlaintextFromTheReturnType() = runBlocking {
        storeSecret()

        // The return value is whatever the operation produced, not secret material.
        assertEquals(42, harness.vault.use(meta) { 42 })

        // A returned ProviderSecret object still exposes no plaintext.
        val opaque = harness.vault.use(meta) { secret -> secret }
        assertFalse(opaque.toString().contains(SECRET))
        assertFalse(opaque.toString().contains("canary"))
        // It is also bound to the operation scope: the decrypted buffer behind
        // it is zeroed once the operation returns, so a secret leaked out of
        // the callback cannot be read afterwards.
        try {
            opaque.use { leaked -> if (leaked.any { it != 0.toByte() }) fail("leaked secret bytes survived the operation") }
        } catch (expected: IllegalStateException) {
            // Backing buffer already consumed/invalidated elsewhere is also acceptable.
        }

        // The vault itself exposes no String/ByteArray-typed API at all.
        val exposing = ProviderCredentialVault::class.java.declaredMethods
            .filter {
                it.returnType == java.lang.String::class.java || it.returnType == ByteArray::class.java
            }
        assertTrue("vault must not expose plaintext-shaped getters: $exposing", exposing.isEmpty())

        // Decrypted buffer passed to the operation is a copy: mutating it does
        // not corrupt the stored ciphertext.
        harness.vault.use(meta) { secret ->
            secret.use { it.fill(0) }
        }
        val digest = harness.vault.use(meta) { secret -> secret.use(::sha256Hex) }
        assertEquals(sha256Hex(SECRET.toByteArray(Charsets.UTF_8)), digest)
    }

    @Test
    fun storeOverwriteUsesLatestSecret() = runBlocking {
        storeSecret(SECRET)
        storeSecret(OTHER_SECRET)

        val digest = harness.vault.use(meta) { secret -> secret.use(::sha256Hex) }
        assertEquals(sha256Hex(OTHER_SECRET.toByteArray(Charsets.UTF_8)), digest)
        assertEquals(1, harness.cipherStore.records.size)
    }

    @Test
    fun deleteRemovesCiphertextMetadataAndKeystoreKey() = runBlocking {
        storeSecret()
        val alias = ProviderCredentialVault.keyAliasFor(meta)
        assertTrue(harness.keystore.hasKey(alias))
        assertTrue(harness.vault.contains(meta))

        assertTrue(harness.vault.delete(meta))

        assertFalse(harness.vault.contains(meta))
        assertNull(harness.cipherStore.records[storageKey()])
        assertFalse(harness.keystore.hasKey(alias))
        assertEquals(listOf(alias), harness.keystore.deletedAliases)

        try {
            harness.vault.use(meta) { fail("must not reach the operation") }
            fail("Expected CredentialNotFoundException")
        } catch (expected: CredentialNotFoundException) {
            assertFalse(expected.message.orEmpty().contains(SECRET))
        }

        assertFalse(harness.vault.delete(meta))
    }

    @Test
    fun containsIsFalseForUnknownRecord() = runBlocking {
        assertFalse(harness.vault.contains(meta))
        assertTrue(harness.cipherStore.records.isEmpty())
    }

    @Test
    fun vaultBytecodeNeverLogs() {
        val classes = securityPackageClassBytes()
        assertTrue("expected compiled security classes on the test classpath", classes.isNotEmpty())
        classes.forEach { (name, bytes) ->
            val text = String(bytes, Charsets.ISO_8859_1)
            assertFalse("$name must not reference android.util.Log", text.contains("android/util/Log"))
            assertFalse("$name must not reference android.util.Slog", text.contains("android/util/Slog"))
        }
    }
}
