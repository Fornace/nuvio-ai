package com.nuvio.tv.core.media.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransformProviderRegistryTest {

    private val signerA = "3a1076bf45ab962d4cb21c3e4e4de64d6b2e21b3d7cd1c2b8e2f6efc8e4e2a11"
    private val signerB = "b81d4f2a97c3e650178f2bd9ac4e1f30d6a8b9c2e5d7f0416c8e3b2a9d5f70c4"
    private val providerPackage = "com.example.mediatransform"

    private class RecordingProvider(
        override val providerId: MediaTransformProviderId,
        override val capabilities: Map<MediaTransformCapability, Int>
    ) : MediaTransformProvider

    private class MutableIdentitySource(
        private val knownPackage: String,
        initialSigners: Set<String>
    ) : PackageIdentitySource {
        private var installed = initialSigners

        fun setInstalled(signers: Set<String>) {
            installed = signers
        }

        override fun signingCertificateSha256Digests(packageName: String): Set<String> =
            if (packageName == knownPackage) installed else emptySet()
    }

    private fun providerId(signer: String = signerA) =
        MediaTransformProviderId(providerPackage, signer)

    private fun identitySource(vararg signers: String) =
        MutableIdentitySource(providerPackage, signers.toSet())

    @Test
    fun `resolve accepts a provider declaring the exact capability version`() {
        val provider = RecordingProvider(
            providerId(),
            mapOf(
                MediaTransformCapability.SUBTITLE_CUES_V1 to 1,
                MediaTransformCapability.DUB_ARTIFACT_V1 to 1
            )
        )
        val registry = MediaTransformProviderRegistry(identitySource(signerA)).register(provider)

        assertSame(
            provider,
            registry.resolve(providerId(), MediaTransformCapability.SUBTITLE_CUES_V1)
        )
        assertSame(
            provider,
            registry.resolve(providerId(), MediaTransformCapability.DUB_ARTIFACT_V1)
        )
    }

    @Test
    fun `resolve rejects a declared version different from the host capability version`() {
        val provider = RecordingProvider(
            providerId(),
            mapOf(MediaTransformCapability.SUBTITLE_CUES_V1 to 2)
        )
        val registry = MediaTransformProviderRegistry(identitySource(signerA)).register(provider)

        val rejection = assertRejected(MediaProviderRejectionReason.VERSION_MISMATCH) {
            registry.resolve(providerId(), MediaTransformCapability.SUBTITLE_CUES_V1)
        }
        assertTrue(rejection.message!!.contains("exact version 1"))
        assertTrue(rejection.message!!.contains("declared 2"))
    }

    @Test
    fun `resolve rejects an identity signed by a different certificate`() {
        val provider = RecordingProvider(
            providerId(signerB),
            mapOf(MediaTransformCapability.SUBTITLE_CUES_V1 to 1)
        )
        val registry = MediaTransformProviderRegistry(identitySource(signerA))

        assertRejected(MediaProviderRejectionReason.WRONG_SIGNER) {
            registry.register(provider)
        }
    }

    @Test
    fun `resolve rechecks the signer and rejects a rotated signing certificate`() {
        val provider = RecordingProvider(
            providerId(),
            mapOf(MediaTransformCapability.SUBTITLE_CUES_V1 to 1)
        )
        val source = identitySource(signerA)
        val registry = MediaTransformProviderRegistry(source).register(provider)
        source.setInstalled(setOf(signerB))

        assertRejected(MediaProviderRejectionReason.WRONG_SIGNER) {
            registry.resolve(providerId(), MediaTransformCapability.SUBTITLE_CUES_V1)
        }
    }

    @Test
    fun `resolve rejects an identity whose package is unknown to the identity source`() {
        val provider = RecordingProvider(
            providerId(),
            mapOf(MediaTransformCapability.SUBTITLE_CUES_V1 to 1)
        )
        val source = identitySource(signerA)
        val registry = MediaTransformProviderRegistry(source).register(provider)
        source.setInstalled(emptySet())

        assertRejected(MediaProviderRejectionReason.UNKNOWN_IDENTITY) {
            registry.resolve(providerId(), MediaTransformCapability.SUBTITLE_CUES_V1)
        }
    }

    @Test
    fun `register rejects an identity whose package is unknown to the identity source`() {
        val provider = RecordingProvider(
            providerId(),
            mapOf(MediaTransformCapability.SUBTITLE_CUES_V1 to 1)
        )
        val registry = MediaTransformProviderRegistry(identitySource())

        assertRejected(MediaProviderRejectionReason.UNKNOWN_IDENTITY) {
            registry.register(provider)
        }
    }

    @Test
    fun `digest formatting differences do not change provider identity`() {
        val provider = RecordingProvider(
            providerId(),
            mapOf(MediaTransformCapability.SUBTITLE_CUES_V1 to 1)
        )
        val formatted = signerA.chunked(2).joinToString(":")
        val registry = MediaTransformProviderRegistry(identitySource(formatted)).register(provider)

        assertSame(
            provider,
            registry.resolve(
                MediaTransformProviderId(providerPackage, signerA.uppercase()),
                MediaTransformCapability.SUBTITLE_CUES_V1
            )
        )
        assertEquals(
            providerId(),
            MediaTransformProviderId(providerPackage, signerA.uppercase())
        )
    }

    @Test
    fun `resolve rejects a capability the provider does not declare`() {
        val provider = RecordingProvider(providerId(), emptyMap())
        val registry = MediaTransformProviderRegistry(identitySource(signerA)).register(provider)

        assertRejected(MediaProviderRejectionReason.UNSUPPORTED_CAPABILITY) {
            registry.resolve(providerId(), MediaTransformCapability.DUB_ARTIFACT_V1)
        }
    }

    @Test
    fun `resolveSubtitleCues returns the typed contract implementation`() {
        val provider = FakeSubtitleCuesProvider(
            providerId = providerId(),
            configuration = FakeSubtitleCuesProvider.Configuration(
                bytesPerCue = 1024L,
                inputLengthBytes = 2048L
            ),
            dispatchers = MediaProviderDispatchers(kotlinx.coroutines.Dispatchers.Unconfined)
        )
        val registry = MediaTransformProviderRegistry(identitySource(signerA)).register(provider)

        assertSame(provider, registry.resolveSubtitleCues(providerId()))
    }

    private fun assertRejected(
        reason: MediaProviderRejectionReason,
        block: () -> Unit
    ): MediaProviderRejectedException = try {
        block()
        throw AssertionError("Expected MediaProviderRejectedException with reason $reason")
    } catch (expected: MediaProviderRejectedException) {
        assertEquals(reason, expected.reason)
        expected
    }
}
