package com.nuvio.tv.core.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.nuvio.tv.core.media.provider.FakeSubtitleCuesProvider
import com.nuvio.tv.core.media.provider.InMemoryMediaCapabilityGrantStore
import com.nuvio.tv.core.media.provider.MediaCapabilityGrantStore
import com.nuvio.tv.core.media.provider.MediaCapabilityNonce
import com.nuvio.tv.core.media.provider.MediaCapabilityNonceSource
import com.nuvio.tv.core.media.provider.MediaProviderDispatchers
import com.nuvio.tv.core.media.provider.MediaTransformProviderRegistry
import com.nuvio.tv.core.media.provider.PackageIdentitySource
import com.nuvio.tv.core.media.provider.SubtitleCuesProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import javax.inject.Singleton

/**
 * Typed native media transform providers: registry, capability grants and the deterministic fake
 * subtitle cues provider. All components are first-party, in-process and main-sourceSet; they are
 * never QuickJS scripts or externally loaded DEX plugins.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaProviderModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun providePackageIdentitySource(
        @ApplicationContext context: Context
    ): PackageIdentitySource = PackageIdentitySource { packageName ->
        try {
            val packageManager = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo?.apkContentsSigners ?: emptyArray()
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                packageInfo.signatures ?: emptyArray()
            }
            signatures.map(::sha256Hex).toSet()
        } catch (e: PackageManager.NameNotFoundException) {
            emptySet()
        }
    }

    @Provides
    @Singleton
    fun provideMediaProviderDispatchers(): MediaProviderDispatchers =
        MediaProviderDispatchers(processing = kotlinx.coroutines.Dispatchers.Default)

    @Provides
    @Singleton
    fun provideMediaCapabilityNonceSource(): MediaCapabilityNonceSource {
        val secureRandom = SecureRandom()
        return MediaCapabilityNonceSource {
            val bytes = ByteArray(NONCE_LENGTH_BYTES)
            secureRandom.nextBytes(bytes)
            MediaCapabilityNonce(bytes.joinToString("") { byte -> "%02x".format(byte) })
        }
    }

    @Provides
    @Singleton
    fun provideMediaCapabilityGrantStore(
        clock: Clock,
        nonceSource: MediaCapabilityNonceSource
    ): MediaCapabilityGrantStore = InMemoryMediaCapabilityGrantStore(
        clock = clock,
        nonceSource = nonceSource
    )

    @Provides
    @Singleton
    fun provideFakeSubtitleCuesProvider(
        @ApplicationContext context: Context,
        packageIdentitySource: PackageIdentitySource,
        dispatchers: MediaProviderDispatchers
    ): FakeSubtitleCuesProvider {
        // The fake ships inside the app, so its typed identity is the app's own package and signer.
        val appDigests = packageIdentitySource
            .signingCertificateSha256Digests(context.packageName)
        check(appDigests.isNotEmpty()) { "App signing digest unavailable for fake provider identity" }
        return FakeSubtitleCuesProvider(
            providerId = com.nuvio.tv.core.media.provider.MediaTransformProviderId(
                packageName = context.packageName,
                signingCertificateSha256 = appDigests.first()
            ),
            configuration = FakeSubtitleCuesProvider.Configuration(
                bytesPerCue = FAKE_BYTES_PER_CUE,
                inputLengthBytes = FAKE_INPUT_LENGTH_BYTES
            ),
            dispatchers = dispatchers
        )
    }

    @Provides
    @Singleton
    fun provideSubtitleCuesProvider(
        fakeSubtitleCuesProvider: FakeSubtitleCuesProvider
    ): SubtitleCuesProvider = fakeSubtitleCuesProvider

    @Provides
    @Singleton
    fun provideMediaTransformProviderRegistry(
        packageIdentitySource: PackageIdentitySource,
        fakeSubtitleCuesProvider: FakeSubtitleCuesProvider
    ): MediaTransformProviderRegistry = MediaTransformProviderRegistry(packageIdentitySource)
        .register(fakeSubtitleCuesProvider)

    private fun sha256Hex(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }

    private const val NONCE_LENGTH_BYTES = 16
    private const val FAKE_BYTES_PER_CUE = 32_768L
    private const val FAKE_INPUT_LENGTH_BYTES = 32_768_000L
}
