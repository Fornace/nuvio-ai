package com.nuvio.tv.core.di

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.media.provider.host.AndroidPackageInstallerBridge
import com.nuvio.tv.core.media.provider.host.AndroidProviderContractClient
import com.nuvio.tv.core.media.provider.host.AndroidProviderPackageOperations
import com.nuvio.tv.core.media.provider.host.OkHttpProviderApkDownloader
import com.nuvio.tv.core.media.provider.host.PackageInstallerBridge
import com.nuvio.tv.core.media.provider.host.ProviderContractClient
import com.nuvio.tv.core.media.provider.host.ProviderContractValidator
import com.nuvio.tv.core.media.provider.host.ProviderPackageOperations
import com.nuvio.tv.core.media.provider.host.PackageManagerProviderPackageScanner
import com.nuvio.tv.core.media.provider.host.ProviderApkDownloader
import com.nuvio.tv.core.media.provider.host.ProviderArtifactVerifier
import com.nuvio.tv.core.media.provider.host.ProviderPackageScanner
import com.nuvio.tv.core.media.provider.host.ProviderRegistryClient
import com.nuvio.tv.core.media.provider.host.ProviderRegistryHttpClientFactory
import com.nuvio.tv.core.media.provider.security.AndroidCipherTextStore
import com.nuvio.tv.core.media.provider.security.AndroidInstallationIdStorage
import com.nuvio.tv.core.media.provider.security.AndroidKeystoreBridge
import com.nuvio.tv.core.media.provider.security.AndroidProfileGenerationStorage
import com.nuvio.tv.core.media.provider.security.CipherTextStore
import com.nuvio.tv.core.media.provider.security.InstallIdentity
import com.nuvio.tv.core.media.provider.security.InstallationIdStorage
import com.nuvio.tv.core.media.provider.security.KeystoreBridge
import com.nuvio.tv.core.media.provider.security.ProfileGenerationStorage
import com.nuvio.tv.core.media.provider.security.ProfileGenerationStore
import com.nuvio.tv.core.media.provider.security.ProviderCredentialVault
import com.nuvio.tv.core.media.provider.security.ProviderProfileCredentialStore
import com.nuvio.tv.core.media.provider.security.ProviderTlsClientFactory
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderSecurityBindings {
    @Binds
    @Singleton
    abstract fun bindCipherTextStore(storage: AndroidCipherTextStore): CipherTextStore

    @Binds
    @Singleton
    abstract fun bindInstallationIdStorage(storage: AndroidInstallationIdStorage): InstallationIdStorage

    @Binds
    @Singleton
    abstract fun bindProfileGenerationStorage(
        storage: AndroidProfileGenerationStorage,
    ): ProfileGenerationStorage

    @Binds
    @IntoSet
    abstract fun bindProviderProfileCredentialStore(
        storage: ProviderProfileCredentialStore,
    ): ProfileScopedCredentialStore
}

@Module
@InstallIn(SingletonComponent::class)
object ProviderHostModule {
    @Provides
    @Singleton
    fun provideKeystoreBridge(): KeystoreBridge = AndroidKeystoreBridge()

    @Provides
    @Singleton
    fun provideInstallIdentity(storage: InstallationIdStorage): InstallIdentity =
        InstallIdentity(storage)

    @Provides
    @Singleton
    fun provideProfileGenerationStore(storage: ProfileGenerationStorage): ProfileGenerationStore =
        ProfileGenerationStore(storage)

    @Provides
    @Singleton
    fun provideProviderCredentialVault(
        keystoreBridge: KeystoreBridge,
        cipherTextStore: CipherTextStore,
        installIdentity: InstallIdentity,
        profileGenerationStore: ProfileGenerationStore,
    ): ProviderCredentialVault = ProviderCredentialVault(
        keystoreBridge = keystoreBridge,
        cipherTextStore = cipherTextStore,
        installIdentity = installIdentity,
        profileGenerationStore = profileGenerationStore,
    )

    @Provides
    @Singleton
    fun provideProviderTlsClientFactory(): ProviderTlsClientFactory = ProviderTlsClientFactory()

    @Provides
    @Singleton
    fun provideProviderRegistryClient(): ProviderRegistryClient = ProviderRegistryClient()

    @Provides
    @Singleton
    fun provideProviderArtifactVerifier(): ProviderArtifactVerifier = ProviderArtifactVerifier()

    @Provides
    @Singleton
    fun provideProviderPackageScanner(
        @ApplicationContext context: Context,
    ): ProviderPackageScanner = PackageManagerProviderPackageScanner(context.packageManager)

    @Provides
    @Singleton
    fun provideProviderContractValidator(): ProviderContractValidator =
        ProviderContractValidator(BuildConfig.VERSION_CODE)

    @Provides
    @Singleton
    fun provideProviderContractClient(
        @ApplicationContext context: Context,
        scanner: ProviderPackageScanner,
        validator: ProviderContractValidator,
    ): ProviderContractClient = AndroidProviderContractClient(context, scanner, validator)

    @Provides
    @Singleton
    fun provideProviderPackageOperations(
        @ApplicationContext context: Context,
    ): ProviderPackageOperations = AndroidProviderPackageOperations(context)

    @Provides
    @Singleton
    fun provideProviderApkDownloader(): ProviderApkDownloader =
        OkHttpProviderApkDownloader(ProviderRegistryHttpClientFactory.create())

    @Provides
    @Singleton
    fun providePackageInstallerBridge(
        @ApplicationContext context: Context,
    ): PackageInstallerBridge = AndroidPackageInstallerBridge(context)
}
