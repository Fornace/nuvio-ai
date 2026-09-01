package com.nuvio.tv.core.media.provider.host

import com.nuvio.tv.core.media.provider.security.CredentialRecordMeta
import com.nuvio.tv.core.media.provider.security.CredentialVaultException
import com.nuvio.tv.core.media.provider.security.ProviderCredentialVault
import com.nuvio.tv.core.media.provider.security.ProviderSecret
import com.nuvio.tv.core.profile.ActiveProfileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Aggregated state for one provider as shown in the provider center. */
data class ProviderCenterItem(
    val entry: ProviderRegistryEntry,
    val installed: InstalledProviderInfo?,
    val installedPackageName: String?,
    val signerTrusted: Boolean,
    val updateAvailable: Boolean,
    val hostTooOld: Boolean,
    val credentialsConfigured: Boolean,
)

sealed interface ProviderCenterRefreshState {
    data object Idle : ProviderCenterRefreshState
    data object Loading : ProviderCenterRefreshState
    data class Ready(val generatedAt: String?) : ProviderCenterRefreshState
    data class Error(val reason: ProviderCenterError) : ProviderCenterRefreshState
}

enum class ProviderCenterError { NETWORK, HTTP, PARSE }

sealed interface ProviderCenterOperationState {
    data object None : ProviderCenterOperationState
    data class Busy(val providerId: String, val installState: ProviderInstallState) :
        ProviderCenterOperationState
    data class Done(val providerId: String, val message: ProviderCenterCompletion) :
        ProviderCenterOperationState
}

sealed interface ProviderCenterCompletion {
    data class Installed(val packageName: String, val versionName: String) : ProviderCenterCompletion
    data object CredentialSaved : ProviderCenterCompletion
    data object CredentialDeleted : ProviderCenterCompletion
    data class ContractVerified(val contract: TrustedProviderContract) : ProviderCenterCompletion
    data object VerificationOpened : ProviderCenterCompletion
    data object UninstallOpened : ProviderCenterCompletion
    data class Failed(val reason: ProviderContractFailure) : ProviderCenterCompletion
}

/**
 * Domain core of the in-app provider center: registry discovery, installed and
 * update status, signature trust, installs, verification negotiation and
 * profile-scoped BYOK credentials. UI-free and unit-testable.
 */
class ProviderCenterController(
    private val registryClient: ProviderRegistryClient,
    private val packageScanner: ProviderPackageScanner,
    private val contractClient: ProviderContractClient,
    private val installCoordinator: ProviderInstallCoordinator,
    private val packageOperations: ProviderPackageOperations,
    private val credentialVault: ProviderCredentialVault,
    private val activeProfileProvider: ActiveProfileProvider,
    private val contractValidator: ProviderContractValidator,
    private val vendorSelectionStore: VendorSelectionStore,
    private val externalScope: CoroutineScope,
) {
    private val mutableRefresh = MutableStateFlow<ProviderCenterRefreshState>(ProviderCenterRefreshState.Idle)
    val refreshState: StateFlow<ProviderCenterRefreshState> = mutableRefresh.asStateFlow()

    private val mutableItems = MutableStateFlow<List<ProviderCenterItem>>(emptyList())
    val items: StateFlow<List<ProviderCenterItem>> = mutableItems.asStateFlow()

    private val mutableOperation = MutableStateFlow<ProviderCenterOperationState>(ProviderCenterOperationState.None)
    val operation: StateFlow<ProviderCenterOperationState> = mutableOperation.asStateFlow()

    private val mutableActiveProfile = MutableStateFlow<Int?>(null)
    val activeProfileId: StateFlow<Int?> = mutableActiveProfile.asStateFlow()

    private val mutableVendorCatalog = MutableStateFlow<ParsedVendorCatalog?>(null)
    val vendorCatalog: StateFlow<ParsedVendorCatalog?> = mutableVendorCatalog.asStateFlow()

    private var activeProfileJob: Job? = null

    init {
        activeProfileJob = externalScope.launch {
            activeProfileProvider.activeProfileId.collect { profileId ->
                mutableActiveProfile.value = profileId
                recomputeCredentialFlags()
            }
        }
    }

    fun start() {
        if (mutableRefresh.value is ProviderCenterRefreshState.Idle) refresh()
    }

    fun refresh(): Job = externalScope.launch {
        mutableRefresh.value = ProviderCenterRefreshState.Loading
        mutableVendorCatalog.value = when (val vendors = registryClient.fetchVendorCatalog()) {
            is VendorCatalogResult.Success -> vendors.catalog
            else -> null
        }
        mutableRefresh.value = when (val result = registryClient.fetch()) {
            is ProviderRegistryResult.Success -> {
                rebuildItems(result.registry.providers)
                ProviderCenterRefreshState.Ready(result.registry.generatedAt)
            }
            is ProviderRegistryResult.NetworkError -> ProviderCenterRefreshState.Error(ProviderCenterError.NETWORK)
            is ProviderRegistryResult.HttpStatusError -> ProviderCenterRefreshState.Error(ProviderCenterError.HTTP)
            is ProviderRegistryResult.ParseError -> ProviderCenterRefreshState.Error(ProviderCenterError.PARSE)
        }
    }

    fun observeInstallState() {
        externalScope.launch {
            installCoordinator.state.collect { state ->
                val busyProvider = (mutableOperation.value as? ProviderCenterOperationState.Busy)?.providerId
                if (busyProvider != null) {
                    if (state is ProviderInstallState.Failed || state is ProviderInstallState.Installed) {
                        refreshInstalledState()
                        mutableOperation.value = when (state) {
                            is ProviderInstallState.Installed -> ProviderCenterOperationState.Done(
                                busyProvider,
                                ProviderCenterCompletion.Installed(state.packageName, state.versionName),
                            )
                            is ProviderInstallState.Failed -> ProviderCenterOperationState.Done(
                                busyProvider,
                                ProviderCenterCompletion.Failed(ProviderContractFailure.PROVIDER_REPORTED_ERROR),
                            )
                            else -> ProviderCenterOperationState.Busy(busyProvider, state)
                        }
                    } else {
                        mutableOperation.value = ProviderCenterOperationState.Busy(busyProvider, state)
                    }
                }
            }
        }
    }

    fun install(providerId: String): Job? {
        val item = items.value.firstOrNull { it.entry.id == providerId } ?: return null
        val packageName = item.entry.packageNames.firstOrNull() ?: return null
        val version = item.entry.version ?: return null
        val request = ProviderInstallRequest(
            providerId = providerId,
            apkUrl = item.entry.apkUrl ?: return null,
            expectedApkSha256 = item.entry.apkSha256 ?: return null,
            packageName = packageName,
            versionName = version,
        )
        mutableOperation.value = ProviderCenterOperationState.Busy(providerId, ProviderInstallState.Idle)
        return installCoordinator.launchInstall(request)
    }

    fun cancelInstall() {
        installCoordinator.cancel()
    }

    suspend fun verify(providerId: String): ProviderCenterCompletion {
        val item = items.value.firstOrNull { it.entry.id == providerId }
            ?: return ProviderCenterCompletion.Failed(ProviderContractFailure.PACKAGE_NOT_ALLOWED)
        val packageName = item.installedPackageName
            ?: item.entry.packageNames.firstOrNull()
            ?: return ProviderCenterCompletion.Failed(ProviderContractFailure.PACKAGE_NOT_INSTALLED)
        return when (val result = contractClient.negotiate(item.entry, packageName)) {
            is ProviderContractResult.Trusted -> ProviderCenterCompletion.ContractVerified(result.contract)
            is ProviderContractResult.Rejected -> ProviderCenterCompletion.Failed(result.reason)
        }
    }

    fun canRequestInstalls(): Boolean = packageOperations.canRequestPackageInstalls()

    fun openUnknownSourcesSettings(): Boolean {
        val opened = packageOperations.openUnknownSourcesSettings()
        if (opened) {
            mutableOperation.value = ProviderCenterOperationState.Done(
                currentBusyProviderId() ?: "",
                ProviderCenterCompletion.VerificationOpened,
            )
        }
        return opened
    }

    fun uninstall(providerId: String): Boolean {
        val item = items.value.firstOrNull { it.entry.id == providerId } ?: return false
        val packageName = item.installedPackageName ?: return false
        val opened = packageOperations.openUninstall(packageName)
        if (opened) {
            mutableOperation.value = ProviderCenterOperationState.Done(
                providerId,
                ProviderCenterCompletion.UninstallOpened,
            )
        }
        return opened
    }

    /** Vendor options for a capability, ordered as published in the catalog. */
    fun vendorOptions(capability: String): List<VendorCatalogEntry> =
        mutableVendorCatalog.value?.vendors?.filter { it.capability == capability }.orEmpty()

    fun selectedVendor(providerId: String): VendorSelection? =
        vendorSelectionStore.load(providerId)

    suspend fun saveCredential(
        providerId: String,
        vendorId: String,
        apiKey: CharArray,
        auxFields: Map<String, String> = emptyMap(),
    ): ProviderCenterCompletion {
        val envelope = try {
            CredentialEnvelope.build(vendorId, apiKey, auxFields)
        } catch (_: IllegalArgumentException) {
            apiKey.fill(Char.MIN_VALUE)
            return ProviderCenterCompletion.Failed(ProviderContractFailure.PROVIDER_REPORTED_ERROR)
        }
        val secret = ProviderSecret.copyOf(envelope)
        apiKey.fill(Char.MIN_VALUE)
        val profileId = mutableActiveProfile.value ?: 1
        val record = CredentialRecordMeta(
            profileId = profileId,
            providerId = providerId,
            recordId = CREDENTIAL_RECORD_ID,
        )
        return try {
            withContext(externalScope.coroutineContext) {
                credentialVault.store(record, secret)
            }
            vendorSelectionStore.save(providerId, VendorSelection(vendorId, auxFields))
            recomputeCredentialFlags()
            ProviderCenterCompletion.CredentialSaved
        } catch (_: CredentialVaultException) {
            ProviderCenterCompletion.Failed(ProviderContractFailure.PROVIDER_REPORTED_ERROR)
        }
    }

    suspend fun deleteCredential(providerId: String): ProviderCenterCompletion {
        val profileId = mutableActiveProfile.value ?: 1
        val record = CredentialRecordMeta(
            profileId = profileId,
            providerId = providerId,
            recordId = CREDENTIAL_RECORD_ID,
        )
        val deleted = withContext(externalScope.coroutineContext) {
            credentialVault.delete(record)
        }
        vendorSelectionStore.clear(providerId)
        recomputeCredentialFlags()
        return ProviderCenterCompletion.CredentialDeleted
    }

    fun hasCredential(providerId: String): Boolean =
        items.value.firstOrNull { it.entry.id == providerId }?.credentialsConfigured == true

    private suspend fun rebuildItems(entries: List<ProviderRegistryEntry>) {
        val installed = packageScanner.installedProviders(entries.flatMap { it.packageNames })
        val profileId = mutableActiveProfile.value ?: 1
        mutableItems.value = entries.map { entry ->
            val packageName = entry.packageNames.firstOrNull { installed.containsKey(it) }
                ?: entry.packageNames.firstOrNull()
            val installedInfo = packageName?.let { installed[it] }
            val signerTrusted = installedInfo != null &&
                contractValidator.validatePreflight(entry, packageName, installedInfo) == null
            val updateAvailable = installedInfo != null && entry.version?.let { registry ->
                compareSemverishVersions(registry, installedInfo.versionName) > 0
            } == true
            ProviderCenterItem(
                entry = entry,
                installed = installedInfo,
                installedPackageName = packageName,
                signerTrusted = signerTrusted,
                updateAvailable = updateAvailable,
                hostTooOld = (entry.minHostAppVersion ?: 0) > contractValidator.hostVersionCode,
                credentialsConfigured = hasStoredCredential(profileId, entry.id),
            )
        }
    }

    private suspend fun refreshInstalledState() {
        rebuildItems(currentEntries())
    }

    private fun currentEntries(): List<ProviderRegistryEntry> = items.value.map { it.entry }

    private suspend fun recomputeCredentialFlags() {
        val profileId = mutableActiveProfile.value ?: return
        if (items.value.isEmpty()) return
        mutableItems.value = items.value.map { item ->
            item.copy(credentialsConfigured = hasStoredCredential(profileId, item.entry.id))
        }
    }

    private suspend fun hasStoredCredential(profileId: Int, providerId: String): Boolean {
        val record = CredentialRecordMeta(
            profileId = profileId,
            providerId = providerId,
            recordId = CREDENTIAL_RECORD_ID,
        )
        return try {
            withContext(externalScope.coroutineContext) { credentialVault.contains(record) }
        } catch (_: CredentialVaultException) {
            false
        }
    }

    private fun currentBusyProviderId(): String? =
        (mutableOperation.value as? ProviderCenterOperationState.Busy)?.providerId

    companion object {
        private const val CREDENTIAL_RECORD_ID = "primary"
    }
}
