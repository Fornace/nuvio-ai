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

/** BYOK entry dialog: input never persists beyond the save call. */
@Composable
internal fun ProviderCredentialDialog(
    providerName: String,
    apiKeyInput: String,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    com.nuvio.tv.ui.components.NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.provider_center_key_dialog_title, providerName),
        subtitle = stringResource(R.string.provider_center_key_dialog_hint),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
        ) {
            com.nuvio.tv.ui.screens.account.InputField(
                value = apiKeyInput,
                onValueChange = onInputChange,
                placeholder = stringResource(R.string.provider_center_key_dialog_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                OutlinedButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.provider_center_key_dialog_cancel))
                }
                Button(onClick = onSave, enabled = apiKeyInput.isNotBlank()) {
                    Text(text = stringResource(R.string.provider_center_key_dialog_save))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
