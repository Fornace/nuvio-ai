package com.nuvio.tv.core.media.provider.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM output produced by [KeystoreBridge.encrypt]. */
class EncryptedPayload(val iv: ByteArray, val ciphertext: ByteArray) {
    fun snapshot(): EncryptedPayload = EncryptedPayload(iv.copyOf(), ciphertext.copyOf())
    override fun toString(): String =
        "EncryptedPayload(ivLength=${iv.size}, ciphertextLength=${ciphertext.size})"
}

/**
 * Injectable seam over the hardware-backed key provider used by
 * [ProviderCredentialVault]. The vault owns AAD computation and policy; the
 * bridge only turns (key, plaintext, AAD) into authenticated ciphertext and
 * back. Implementations MUST make GCM authentication cover the AAD, so that
 * ciphertexts swapped across installations, profile generations, providers or
 * records fail decryption.
 */
interface KeystoreBridge {
    fun encrypt(keyAlias: String, plaintext: ByteArray, aad: ByteArray): EncryptedPayload

    fun decrypt(keyAlias: String, payload: EncryptedPayload, aad: ByteArray): ByteArray

    fun hasKey(keyAlias: String): Boolean

    fun deleteKey(keyAlias: String): Boolean
}

/**
 * Production bridge over the Android Keystore. One AES-256-GCM key is created
 * per provider + record pair (key alias) and never leaves the Keystore.
 *
 * This class is intentionally thin: the Android Keystore cannot be exercised in
 * JVM unit tests, so every decision that matters (AAD binding, address
 * derivation, overwrite/delete semantics) lives in [ProviderCredentialVault],
 * which is tested against a pure-JVM fake of this interface. The only logic
 * kept here is key provisioning and cipher invocation.
 */
class AndroidKeystoreBridge : KeystoreBridge {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    override fun encrypt(keyAlias: String, plaintext: ByteArray, aad: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Randomized encryption is required on the key, so no explicit IV is
        // passed here; the Keystore generates a fresh one per encryption.
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(keyAlias))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv ?: error("Android Keystore produced no IV for $keyAlias")
        return EncryptedPayload(iv, ciphertext)
    }

    @Synchronized
    override fun decrypt(keyAlias: String, payload: EncryptedPayload, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(keyAlias),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(payload.ciphertext)
    }

    @Synchronized
    override fun hasKey(keyAlias: String): Boolean = keyStore.containsAlias(keyAlias)

    @Synchronized
    override fun deleteKey(keyAlias: String): Boolean {
        val existed = keyStore.containsAlias(keyAlias)
        if (existed) {
            keyStore.deleteEntry(keyAlias)
        }
        return existed
    }

    private fun secretKey(keyAlias: String): SecretKey {
        keyStore.getEntry(keyAlias, null)?.let { entry ->
            return (entry as? KeyStore.SecretKeyEntry)?.secretKey
                ?: throw IllegalStateException("Keystore alias $keyAlias is not a secret key")
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_LENGTH_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generator.generateKey()
        return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_LENGTH_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128

        // Not used by the Keystore path (which generates its own IVs); shared
        // here so JVM fakes in the test source set derive their parameters
        // from the same constants.
        val FAKE_IV_SOURCE: SecureRandom = SecureRandom()
    }
}
