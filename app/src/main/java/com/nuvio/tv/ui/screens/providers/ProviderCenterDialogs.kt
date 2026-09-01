package com.nuvio.tv.ui.screens.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.media.provider.host.ProviderCenterError
import com.nuvio.tv.core.media.provider.host.VendorCatalogEntry
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

/** Unknown-sources recovery card shown when the host cannot install packages yet. */
@Composable
internal fun UnknownSourcesCard(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.provider_center_unknown_sources_hint),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.Error,
        )
        Button(onClick = onOpenSettings) {
            Text(text = stringResource(R.string.provider_center_open_unknown_sources))
        }
    }
}

@Composable
internal fun ErrorCard(reason: ProviderCenterError) {
    val text = when (reason) {
        ProviderCenterError.NETWORK -> stringResource(R.string.provider_center_error_network)
        ProviderCenterError.HTTP -> stringResource(R.string.provider_center_error_http)
        ProviderCenterError.PARSE -> stringResource(R.string.provider_center_error_parse)
    }
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = NuvioTheme.colors.Error,
    )
}

/** BYOK entry dialog: vendor picker plus per-vendor auth fields. Input never persists beyond the save call. */
@Composable
internal fun ProviderCredentialDialog(
    providerName: String,
    vendorOptions: List<VendorCatalogEntry>,
    selectedVendor: VendorCatalogEntry?,
    fieldInputs: Map<String, String>,
    onSelectVendor: (String) -> Unit,
    onFieldChange: (String, String) -> Unit,
    onOpenKeyUrl: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val requiredFields = selectedVendor?.authFields ?: listOf("apiKey")
    val allFieldsFilled = requiredFields.all { !fieldInputs[it].isNullOrBlank() }

    com.nuvio.tv.ui.components.NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.provider_center_key_dialog_title, providerName),
        subtitle = stringResource(R.string.provider_center_key_dialog_hint),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
        ) {
            if (vendorOptions.size > 1) {
                Text(
                    text = stringResource(R.string.provider_center_vendor_section),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.colors.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
                    vendorOptions.forEach { vendor ->
                        val isSelected = vendor.id == selectedVendor?.id
                        if (isSelected) {
                            Button(onClick = { onSelectVendor(vendor.id) }) {
                                Text(text = vendor.name)
                            }
                        } else {
                            OutlinedButton(onClick = { onSelectVendor(vendor.id) }) {
                                Text(text = vendor.name)
                            }
                        }
                    }
                }
            }

            selectedVendor?.let { vendor ->
                Text(
                    text = vendor.pricingHint,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                )
                vendor.notes?.let { notes ->
                    Text(
                        text = notes,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary,
                    )
                }
                OutlinedButton(onClick = { onOpenKeyUrl(vendor.keyUrl) }) {
                    Text(text = stringResource(R.string.provider_center_get_api_key))
                }
            }

            requiredFields.forEach { fieldId ->
                com.nuvio.tv.ui.screens.account.InputField(
                    value = fieldInputs[fieldId].orEmpty(),
                    onValueChange = { onFieldChange(fieldId, it) },
                    placeholder = authFieldLabel(fieldId),
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { if (fieldId == "apiKey") it.focusRequester(focusRequester) else it },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                OutlinedButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.provider_center_key_dialog_cancel))
                }
                Button(onClick = onSave, enabled = allFieldsFilled) {
                    Text(text = stringResource(R.string.provider_center_key_dialog_save))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun authFieldLabel(fieldId: String): String = stringResource(
    when (fieldId) {
        "accountId" -> R.string.provider_center_field_account_id
        "workspaceId" -> R.string.provider_center_field_workspace_id
        else -> R.string.provider_center_key_dialog_placeholder
    }
)
