package com.nuvio.tv.core.media.provider.host

import java.io.File
import java.io.IOException
import java.security.MessageDigest

sealed interface ProviderArtifactVerificationResult {
    data class Verified(val actualSha256: String) : ProviderArtifactVerificationResult
    data class DigestMismatch(val expectedSha256: String, val actualSha256: String) :
        ProviderArtifactVerificationResult
    data class InvalidExpectedDigest(val expectedSha256: String) : ProviderArtifactVerificationResult
    data class ReadError(val causeType: String) : ProviderArtifactVerificationResult
}

open class ProviderArtifactVerifier {
    open fun verify(file: File, expectedSha256: String): ProviderArtifactVerificationResult {
        if (!expectedSha256.matches(SHA256_PATTERN)) {
            return ProviderArtifactVerificationResult.InvalidExpectedDigest(expectedSha256)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        try {
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
        } catch (error: IOException) {
            return ProviderArtifactVerificationResult.ReadError(error.javaClass.simpleName)
        }

        val actualBytes = digest.digest()
        val expectedBytes = expectedSha256.hexToBytes()
        val actualHex = with(HostCrypto) { actualBytes.toLowerHex() }
        return if (MessageDigest.isEqual(actualBytes, expectedBytes)) {
            ProviderArtifactVerificationResult.Verified(actualHex)
        } else {
            ProviderArtifactVerificationResult.DigestMismatch(expectedSha256, actualHex)
        }
    }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
