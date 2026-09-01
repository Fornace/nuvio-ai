package com.nuvio.tv.core.media.provider.host

import com.nuvio.tv.core.media.provider.security.FakeKeystoreBridge
import com.nuvio.tv.core.media.provider.security.InstallIdentity
import com.nuvio.tv.core.media.provider.security.ProfileGenerationStore
import com.nuvio.tv.core.media.provider.security.ProviderCredentialVault
import com.nuvio.tv.core.media.provider.security.RecordingCipherTextStore
import com.nuvio.tv.core.profile.FakeActiveProfileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderCenterControllerTest {
    private val signer = "A".repeat(64)
    private val packageName = "com.nuvio.tv.provider.subtitles"
    private val installed = InstalledProviderInfo("0.1.0-preview1", 1, setOf(signer))
    private val entry = ProviderRegistryEntry(
        id = "generated-dialogue-subtitles",
        name = "Generated Dialogue Subtitles",
        capability = "SUBTITLE_CUES_V1",
        status = "preview",
        installable = true,
        description = null,
        version = "0.1.0-preview1",
        apkUrl = "https://nuvio-extensions.fornace.net/artifacts/subtitles.apk",
        apkSha256 = "a".repeat(64),
        minHostAppVersion = 1,
        packageNames = listOf(packageName),
        signingCertSha256 = setOf(signer),
        engineStatus = "contract-preview",
        documentation = null,
        parseWarnings = emptyList(),
    )

    private val scope = TestScope(UnconfinedTestDispatcher() + SupervisorJob())
    private val coordinatorScope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob())

    private lateinit var registryClient: FakeRegistryClient
    private lateinit var scanner: FakeScanner
    private lateinit var operations: FakePackageOperations
    private lateinit var vendorSelectionStore: RecordingVendorSelectionStore
    private lateinit var profileManager: FakeActiveProfileProvider
    private lateinit var cipherStore: RecordingCipherTextStore
    private lateinit var vault: ProviderCredentialVault

    @Before
    fun setUp() {
        registryClient = FakeRegistryClient(listOf(entry))
        scanner = FakeScanner(mapOf(packageName to installed))
        operations = FakePackageOperations()
        profileManager = FakeActiveProfileProvider()
        vendorSelectionStore = RecordingVendorSelectionStore()
        cipherStore = RecordingCipherTextStore()
        vault = ProviderCredentialVault(
            keystoreBridge = FakeKeystoreBridge(),
            cipherTextStore = cipherStore,
            installIdentity = InstallIdentity(FixedInstallationIdStorage()),
            profileGenerationStore = ProfileGenerationStore(FixedProfileGenerationStorage()),
        )
    }

    @After
    fun tearDown() {
        coordinatorScope.cancel()
        scope.cancel()
    }

    private fun controller(
        contractClient: ProviderContractClient = FakeContractClient(),
    ) = ProviderCenterController(
        registryClient = registryClient,
        packageScanner = scanner,
        contractClient = contractClient,
        installCoordinator = FakeInstallCoordinator(coordinatorScope),
        packageOperations = operations,
        credentialVault = vault,
        activeProfileProvider = profileManager,
        contractValidator = ProviderContractValidator(hostVersionCode = 1051),
        vendorSelectionStore = vendorSelectionStore,
        externalScope = scope,
    )

    @Test
    fun `refresh maps registry installed signer and update state`() = runTest {
        val controller = controller()
        controller.refresh().join()

        assertTrue(controller.refreshState.value is ProviderCenterRefreshState.Ready)
        val item = controller.items.value.single()
        assertEquals(entry.id, item.entry.id)
        assertNotNull(item.installed)
        assertTrue(item.signerTrusted)
        assertFalse(item.updateAvailable)
        assertFalse(item.hostTooOld)
        assertFalse(item.credentialsConfigured)
    }

    @Test
    fun `signer mismatch marks provider untrusted without blocking list`() = runTest {
        scanner.installed = mapOf(
            packageName to installed.copy(signingCertSha256Set = setOf("B".repeat(64)))
        )
        val controller = controller()
        controller.refresh().join()

        val item = controller.items.value.single()
        assertFalse(item.signerTrusted)
        assertNotNull(item.installed)
    }

    @Test
    fun `registry error surfaces typed error`() = runTest {
        registryClient.result = ProviderRegistryResult.NetworkError("UnknownHostException")
        val controller = controller()
        controller.refresh().join()

        assertEquals(
            ProviderCenterRefreshState.Error(ProviderCenterError.NETWORK),
            controller.refreshState.value,
        )
    }

    @Test
    fun `older installed version reports update available`() = runTest {
        scanner.installed = mapOf(
            packageName to installed.copy(versionName = "0.0.9", versionCode = 0)
        )
        val controller = controller()
        controller.refresh().join()

        assertTrue(controller.items.value.single().updateAvailable)
    }

    @Test
    fun `verify returns trusted contract after negotiation`() = runTest {
        val contractClient = FakeContractClient()
        val controller = controller(contractClient)
        controller.refresh().join()

        val completion = controller.verify(entry.id)
        assertTrue(completion is ProviderCenterCompletion.ContractVerified)
        assertEquals(entry.id, contractClient.lastProviderId)
    }

    @Test
    fun `save and delete credential round trip with profile isolation`() = runTest {
        val controller = controller()
        controller.refresh().join()

        val saved = controller.saveCredential(entry.id, "groq", "sk-test-123".toCharArray())
        assertEquals(ProviderCenterCompletion.CredentialSaved, saved)
        assertTrue(controller.hasCredential(entry.id))

        profileManager.mutable.value = 2
        assertFalse(controller.hasCredential(entry.id))

        profileManager.mutable.value = 1
        val deleted = controller.deleteCredential(entry.id)
        assertEquals(ProviderCenterCompletion.CredentialDeleted, deleted)
        assertFalse(controller.hasCredential(entry.id))
    }

    @Test
    fun `vendor options filter by capability and selection persists with aux fields`() = runTest {
        val controller = controller()
        (registryClient as FakeRegistryClient).vendorResult = VendorCatalogResult.Success(
            ParsedVendorCatalog(
                schemaVersion = 1,
                updated = "2026-09-01",
                vendors = listOf(
                    VendorCatalogEntry(
                        id = "groq",
                        name = "Groq",
                        capability = entry.capability,
                        adaptor = "openai-asr",
                        apiBase = "https://api.groq.com/openai/v1",
                        model = "whisper-large-v3-turbo",
                        authFields = listOf("apiKey"),
                        keyUrl = "https://console.groq.com/keys",
                        docsUrl = "https://console.groq.com/docs/speech-to-text",
                        pricingHint = "x",
                        notes = null,
                    ),
                    VendorCatalogEntry(
                        id = "qwen",
                        name = "Qwen",
                        capability = "DUB_ARTIFACT_V1",
                        adaptor = "qwen-livetranslate-ws",
                        apiBase = "wss://example/{workspaceId}?model={model}",
                        model = "m",
                        authFields = listOf("apiKey", "workspaceId"),
                        keyUrl = "https://example.com/key",
                        docsUrl = "https://example.com/docs",
                        pricingHint = "y",
                        notes = null,
                    ),
                ),
                warnings = emptyList(),
            )
        )
        controller.refresh().join()

        assertEquals(listOf("groq"), controller.vendorOptions(entry.capability).map { it.id })

        controller.saveCredential(
            providerId = entry.id,
            vendorId = "groq",
            apiKey = "sk-test".toCharArray(),
            auxFields = emptyMap(),
        )
        assertEquals("groq", controller.selectedVendor(entry.id)?.vendorId)

        controller.deleteCredential(entry.id)
        assertEquals(null, controller.selectedVendor(entry.id))
    }

    @Test
    fun `unknown sources gate reports and opens settings`() {
        operations.canRequest = false
        val controller = controller()

        assertFalse(controller.canRequestInstalls())
        assertTrue(controller.openUnknownSourcesSettings())
        assertTrue(operations.openedUnknownSources)
    }

    @Test
    fun `uninstall routes to system uninstaller for installed package`() = runTest {
        val controller = controller()
        controller.refresh().join()

        assertTrue(controller.uninstall(entry.id))
        assertEquals(packageName, operations.uninstalledPackage)
    }

    private class FakeRegistryClient(initial: List<ProviderRegistryEntry>) : ProviderRegistryClient() {
        var result: ProviderRegistryResult = ProviderRegistryResult.Success(
            ParsedProviderRegistry(
                schemaVersion = 1,
                generatedAt = "2026-09-01T00:00:00Z",
                registryUrl = "https://nuvio-extensions.fornace.net/v1/registry.json",
                providers = initial,
                warnings = emptyList(),
            )
        )

        override suspend fun fetch(): ProviderRegistryResult = result

        var vendorResult: VendorCatalogResult = VendorCatalogResult.Success(
            ParsedVendorCatalog(schemaVersion = 1, updated = "2026-09-01", vendors = emptyList(), warnings = emptyList())
        )

        override suspend fun fetchVendorCatalog(): VendorCatalogResult = vendorResult
    }

    private class FakeScanner(
        var installed: Map<String, InstalledProviderInfo>,
    ) : ProviderPackageScanner {
        override fun installedProviders(packages: Collection<String>): Map<String, InstalledProviderInfo> =
            installed.filterKeys { it in packages }
    }

    private class FakeContractClient :
        ProviderContractClient {
        var lastProviderId: String? = null
            private set
        var lastPackageName: String? = null
            private set

        override suspend fun negotiate(
            registryEntry: ProviderRegistryEntry,
            packageName: String,
        ): ProviderContractResult {
            lastProviderId = registryEntry.id
            lastPackageName = packageName
            return ProviderContractResult.Trusted(
                TrustedProviderContract(
                    providerId = registryEntry.id,
                    packageName = packageName,
                    versionName = registryEntry.version.orEmpty(),
                    capability = registryEntry.capability,
                    capabilityVersion = 1,
                    engineStatus = registryEntry.engineStatus.orEmpty(),
                    signingCertSha256 = registryEntry.signingCertSha256,
                )
            )
        }
    }

    private class FakePackageOperations : ProviderPackageOperations {
        var canRequest = true
        var openedUnknownSources = false
        var uninstalledPackage: String? = null

        override fun canRequestPackageInstalls(): Boolean = canRequest

        override fun openUnknownSourcesSettings(): Boolean {
            openedUnknownSources = true
            return true
        }

        override fun openUninstall(packageName: String): Boolean {
            uninstalledPackage = packageName
            return true
        }
    }

    private class FakeInstallCoordinator(scope: CoroutineScope) : ProviderInstallCoordinator(
        downloader = NoopDownloader(),
        verifier = NoopVerifier(),
        installerBridge = NoopInstallerBridge(),
        downloadDir = File(System.getProperty("java.io.tmpdir"), "nuvio-center-test-installs"),
        externalScope = scope,
    )

    private class NoopDownloader : ProviderApkDownloader {
        override suspend fun download(
            apkUrl: String,
            destination: File,
            onProgress: (Float) -> Unit,
        ): File = destination.apply { writeText("apk") }
    }

    private class NoopVerifier : ProviderArtifactVerifier() {
        override fun verify(file: File, expectedSha256: String): ProviderArtifactVerificationResult =
            ProviderArtifactVerificationResult.Verified("a".repeat(64))
    }

    private class NoopInstallerBridge : PackageInstallerBridge {
        override suspend fun install(
            apkFile: File,
            packageName: String?,
            statusListener: ProviderInstallerStatusListener?,
        ): ProviderInstallerResult = ProviderInstallerResult.Installed(packageName.orEmpty())
    }

    private class FixedInstallationIdStorage :
        com.nuvio.tv.core.media.provider.security.InstallationIdStorage {
        var value: String? = "11111111-2222-3333-4444-555555555555"

        override fun load(): String? = value

        override fun persist(installationId: String) {
            value = installationId
        }
    }

    private class FixedProfileGenerationStorage :
        com.nuvio.tv.core.media.provider.security.ProfileGenerationStorage {
        private val generations = mutableMapOf<Int, String>()

        override fun loadAll(): Map<Int, String> = generations.toMap()

        override fun persistAll(generations: Map<Int, String>) {
            this.generations.putAll(generations)
        }
    }
}

private class RecordingVendorSelectionStore : VendorSelectionStore {
    val saved = mutableMapOf<String, VendorSelection>()
    override fun load(providerId: String): VendorSelection? = saved[providerId]
    override fun save(providerId: String, selection: VendorSelection) {
        saved[providerId] = selection
    }
    override fun clear(providerId: String) {
        saved.remove(providerId)
    }
}
