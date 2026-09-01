package com.nuvio.tv.ui.screens.providers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.media.provider.host.ProviderCenterCompletion
import com.nuvio.tv.core.media.provider.host.ProviderCenterController
import com.nuvio.tv.core.media.provider.host.ProviderCenterItem
import com.nuvio.tv.core.media.provider.host.ProviderCenterOperationState
import com.nuvio.tv.core.media.provider.host.ProviderCenterRefreshState
import com.nuvio.tv.core.media.provider.host.VendorCatalogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for one provider's BYOK dialog. */
data class ProviderCredentialDialog(
    val providerId: String,
    val providerName: String,
    val capability: String,
    val vendorOptions: List<VendorCatalogEntry> = emptyList(),
    val selectedVendorId: String? = null,
    val fieldInputs: Map<String, String> = emptyMap(),
) {
    val selectedVendor: VendorCatalogEntry?
        get() = vendorOptions.firstOrNull { it.id == selectedVendorId }
}

data class ProviderCenterUiState(
    val refresh: ProviderCenterRefreshState = ProviderCenterRefreshState.Idle,
    val items: List<ProviderCenterItem> = emptyList(),
    val operation: ProviderCenterOperationState = ProviderCenterOperationState.None,
    val canRequestInstalls: Boolean = true,
    val credentialDialog: ProviderCredentialDialog? = null,
    val activeProfileId: Int = 1,
    val lastMessage: ProviderCenterCompletion? = null,
)

@HiltViewModel
class ProviderCenterViewModel @Inject constructor(
    application: Application,
    private val controller: ProviderCenterController,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ProviderCenterUiState())
    val uiState: StateFlow<ProviderCenterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            controller.refreshState.collect { refresh ->
                _uiState.value = _uiState.value.copy(refresh = refresh)
            }
        }
        viewModelScope.launch {
            controller.items.collect { items ->
                _uiState.value = _uiState.value.copy(items = items)
            }
        }
        viewModelScope.launch {
            controller.operation.collect { operation ->
                _uiState.value = if (operation is ProviderCenterOperationState.Done) {
                    _uiState.value.copy(
                        operation = operation,
                        lastMessage = operation.message,
                    )
                } else {
                    _uiState.value.copy(operation = operation)
                }
            }
        }
        viewModelScope.launch {
            controller.activeProfileId.collect { profileId ->
                if (profileId != null) {
                    _uiState.value = _uiState.value.copy(activeProfileId = profileId)
                }
            }
        }
        controller.observeInstallState()
        _uiState.value = _uiState.value.copy(canRequestInstalls = controller.canRequestInstalls())
        controller.start()
    }

    fun refresh() {
        viewModelScope.launch { controller.refresh().join() }
        _uiState.value = _uiState.value.copy(canRequestInstalls = controller.canRequestInstalls())
    }

    fun install(providerId: String) {
        _uiState.value = _uiState.value.copy(
            canRequestInstalls = controller.canRequestInstalls(),
        )
        controller.install(providerId)
    }

    fun cancelInstall() = controller.cancelInstall()

    fun verify(providerId: String) {
        viewModelScope.launch {
            val completion = controller.verify(providerId)
            _uiState.value = _uiState.value.copy(lastMessage = completion)
        }
    }

    fun openUnknownSources() {
        controller.openUnknownSourcesSettings()
        _uiState.value = _uiState.value.copy(
            canRequestInstalls = controller.canRequestInstalls(),
            lastMessage = ProviderCenterCompletion.VerificationOpened,
        )
    }

    fun uninstall(providerId: String) {
        controller.uninstall(providerId)
    }

    fun openCredentialDialog(providerId: String) {
        val item = _uiState.value.items.firstOrNull { it.entry.id == providerId } ?: return
        val options = controller.vendorOptions(item.entry.capability)
        val selection = controller.selectedVendor(providerId)
        val selectedVendorId = selection?.vendorId?.takeIf { id -> options.any { it.id == id } }
            ?: options.firstOrNull()?.id
        val initialFields = selection?.let { sel ->
            buildMap {
                put(API_KEY_FIELD, "")
                sel.auxFields.forEach { (key, value) -> put(key, value) }
            }
        } ?: emptyMap()
        _uiState.value = _uiState.value.copy(
            credentialDialog = ProviderCredentialDialog(
                providerId = providerId,
                providerName = item.entry.name,
                capability = item.entry.capability,
                vendorOptions = options,
                selectedVendorId = selectedVendorId,
                fieldInputs = initialFields,
            )
        )
    }

    fun selectVendor(vendorId: String) {
        val dialog = _uiState.value.credentialDialog ?: return
        if (dialog.vendorOptions.none { it.id == vendorId }) return
        _uiState.value = _uiState.value.copy(
            credentialDialog = dialog.copy(
                selectedVendorId = vendorId,
                fieldInputs = mapOf(API_KEY_FIELD to ""),
            )
        )
    }

    fun updateAuthField(fieldId: String, input: String) {
        val dialog = _uiState.value.credentialDialog ?: return
        _uiState.value = _uiState.value.copy(
            credentialDialog = dialog.copy(fieldInputs = dialog.fieldInputs + (fieldId to input))
        )
    }

    fun dismissCredentialDialog() {
        _uiState.value = _uiState.value.copy(credentialDialog = null)
    }

    fun saveCredential() {
        val dialog = _uiState.value.credentialDialog ?: return
        val vendor = dialog.selectedVendor ?: return
        viewModelScope.launch {
            val apiKey = dialog.fieldInputs[API_KEY_FIELD].orEmpty().toCharArray()
            val aux = dialog.fieldInputs.filterKeys { it != API_KEY_FIELD }
                .filterValues { it.isNotBlank() }
            val completion = controller.saveCredential(
                providerId = dialog.providerId,
                vendorId = vendor.id,
                apiKey = apiKey,
                auxFields = aux,
            )
            _uiState.value = _uiState.value.copy(
                credentialDialog = null,
                lastMessage = completion,
            )
        }
    }

    fun deleteCredential(providerId: String) {
        viewModelScope.launch {
            val completion = controller.deleteCredential(providerId)
            _uiState.value = _uiState.value.copy(lastMessage = completion)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(lastMessage = null)
    }

    /** Re-query install permission after returning from system settings. */
    fun onScreenResumed() {
        val now = controller.canRequestInstalls()
        if (now != _uiState.value.canRequestInstalls) {
            _uiState.value = _uiState.value.copy(canRequestInstalls = now)
        }
    }

    companion object {
        const val API_KEY_FIELD = "apiKey"
    }
}
