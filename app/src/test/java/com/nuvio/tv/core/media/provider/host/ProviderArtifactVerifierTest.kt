package com.nuvio.tv.core.media.provider.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test

class ProviderArtifactVerifierTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val verifier = ProviderArtifactVerifier()
    private val apkBytes = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 7, 8, 9)
    private val apkSha256 = HostCrypto.sha256Hex(apkBytes)

    private fun apkFile(): java.io.File =
        tempFolder.newFile("provider.apk").apply { writeBytes(apkBytes) }

    @Test
    fun `matching digest verifies and exposes the actual digest`() {
        val result = verifier.verify(apkFile(), apkSha256)

        assertEquals(ProviderArtifactVerificationResult.Verified(apkSha256), result)
        assertEquals(apkSha256, (result as ProviderArtifactVerificationResult.Verified).actualSha256)
    }

    @Test
    fun `mismatched digest reports both digests`() {
        val other = byteArrayOf(1, 2, 3)
        val expected = HostCrypto.sha256Hex(other)

        val result = verifier.verify(apkFile(), expected)

        assertEquals(
            ProviderArtifactVerificationResult.DigestMismatch(expected, apkSha256),
            result
        )
    }

    @Test
    fun `digest is computed incrementally over a larger file`() {
        val large = ByteArray(256 * 1024 + 17) { (it % 251).toByte() }
        val file = tempFolder.newFile("large.apk").apply { writeBytes(large) }

        val result = verifier.verify(file, HostCrypto.sha256Hex(large))

        assertTrue(result is ProviderArtifactVerificationResult.Verified)
    }

    @Test
    fun `invalid expected digest is rejected without touching the file`() {
        val file = apkFile()

        val result = verifier.verify(file, "not-a-digest")

        assertEquals(
            ProviderArtifactVerificationResult.InvalidExpectedDigest("not-a-digest"),
            result
        )
    }
}
