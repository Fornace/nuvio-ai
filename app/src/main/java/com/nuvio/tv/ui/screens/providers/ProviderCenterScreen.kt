package com.nuvio.tv.ui.screens.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.media.provider.host.ProviderCenterCompletion
import com.nuvio.tv.core.media.provider.host.ProviderCenterItem
import com.nuvio.tv.core.media.provider.host.ProviderCenterOperationState
import com.nuvio.tv.core.media.provider.host.ProviderCenterRefreshState
import com.nuvio.tv.core.media.provider.host.ProviderInstallState
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.settings.SettingsActionRow
import com.nuvio.tv.ui.screens.settings.SettingsDetailHeader
import com.nuvio.tv.ui.screens.settings.SettingsGroupCard
import kotlinx.coroutines.delay

/** In-app AI media provider center: discover, install, verify, configure BYOK. */
@Composable
fun ProviderCenterScreen(
    onBackPress: () -> Unit,
    viewModel: ProviderCenterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.LaunchedEffect(uiState.lastMessage) {
        val message = uiState.lastMessage ?: return@LaunchedEffect
        kotlinx.coroutines.delay(6_000)
        viewModel.clearMessage()
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = NuvioTheme.spacing.xxl,
                    vertical = NuvioTheme.spacing.xl,
                ),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
        ) {
        SettingsDetailHeader(
            title = stringResource(R.string.provider_center_title),
            subtitle = stringResource(R.string.provider_center_subtitle),
        )

        if (!uiState.canRequestInstalls) {
            UnknownSourcesCard(onOpenSettings = viewModel::openUnknownSources)
        }

        SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow(
                title = stringResource(R.string.provider_center_refresh),
                subtitle = when (val refresh = uiState.refresh) {
                    is ProviderCenterRefreshState.Ready ->
                        stringResource(R.string.provider_center_updated_at, refresh.generatedAt.orEmpty())
                    is ProviderCenterRefreshState.Loading ->
                        stringResource(R.string.provider_center_loading)
                    is ProviderCenterRefreshState.Error ->
                        stringResource(R.string.provider_center_error)
                    ProviderCenterRefreshState.Idle ->
                        stringResource(R.string.provider_center_never_refreshed)
                },
                onClick = viewModel::refresh,
                leadingIcon = Icons.Default.Refresh,
            )
        }

        when (val refresh = uiState.refresh) {
            is ProviderCenterRefreshState.Error -> ErrorCard(reason = refresh.reason)
            ProviderCenterRefreshState.Idle, ProviderCenterRefreshState.Loading -> Unit
            is ProviderCenterRefreshState.Ready -> {
                if (uiState.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.provider_center_empty),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = NuvioTheme.colors.TextSecondary,
                    )
                }
            }
        }

        uiState.items.forEach { item ->
            ProviderCard(
                item = item,
                operation = uiState.operation,
                onInstall = viewModel::install,
                onCancelInstall = viewModel::cancelInstall,
                onVerify = viewModel::verify,
                onUninstall = viewModel::uninstall,
                onOpenCredentials = viewModel::openCredentialDialog,
                onDeleteCredential = viewModel::deleteCredential,
            )
        }
        }

        uiState.lastMessage?.let { message ->
            CompletionMessageRow(
                message = message,
                onDismiss = viewModel::clearMessage,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(bottom = NuvioTheme.spacing.xl),
            )
        }
    }

    uiState.credentialDialog?.let { dialog ->
        val context = androidx.compose.ui.platform.LocalContext.current
        ProviderCredentialDialog(
            providerName = dialog.providerName,
            vendorOptions = dialog.vendorOptions,
            selectedVendor = dialog.selectedVendor,
            fieldInputs = dialog.fieldInputs,
            onSelectVendor = viewModel::selectVendor,
            onFieldChange = viewModel::updateAuthField,
            onOpenKeyUrl = { url ->
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        )
                    )
                }
            },
            onSave = viewModel::saveCredential,
            onDismiss = viewModel::dismissCredentialDialog,
        )
    }
}

@Composable
private fun ProviderCard(
    item: ProviderCenterItem,
    operation: ProviderCenterOperationState,
    onInstall: (String) -> Unit,
    onCancelInstall: () -> Unit,
    onVerify: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onOpenCredentials: (String) -> Unit,
    onDeleteCredential: (String) -> Unit,
) {
    val busyOperation = operation as? ProviderCenterOperationState.Busy
    val isBusy = busyOperation?.providerId == item.entry.id
    val installState = if (isBusy) busyOperation?.installState else null

    SettingsGroupCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = item.entry.name,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md),
        )
        Text(
            text = buildString {
                append(item.entry.capability)
                append(" · ")
                append(item.entry.version.orEmpty())
            },
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md),
        )

        when {
            installState != null -> InstallProgressRow(
                state = installState,
                providerId = item.entry.id,
                onCancel = onCancelInstall,
            )
            item.hostTooOld -> DisabledRow(text = stringResource(R.string.provider_center_host_too_old))
            !item.entry.installable -> DisabledRow(text = stringResource(R.string.provider_center_not_installable))
            else -> {
                if (item.installed == null) {
                    SettingsActionRow(
                        title = stringResource(R.string.provider_center_install),
                        subtitle = stringResource(R.string.provider_center_install_subtitle),
                        onClick = { onInstall(item.entry.id) },
                        leadingIcon = Icons.Default.Refresh,
                    )
                } else {
                    StatusRow(item = item)
                    if (item.updateAvailable) {
                        SettingsActionRow(
                            title = stringResource(R.string.provider_center_update),
                            subtitle = stringResource(
                                R.string.provider_center_update_subtitle,
                                item.entry.version.orEmpty(),
                            ),
                            onClick = { onInstall(item.entry.id) },
                            leadingIcon = Icons.Default.Refresh,
                        )
                    }
                    if (item.signerTrusted) {
                        SettingsActionRow(
                            title = stringResource(R.string.provider_center_verify),
                            subtitle = stringResource(R.string.provider_center_verify_subtitle),
                            onClick = { onVerify(item.entry.id) },
                            leadingIcon = Icons.Default.VerifiedUser,
                        )
                    } else {
                        SignatureWarningRow(item = item, onUninstall = onUninstall)
                    }
                }
            }
        }

        SettingsActionRow(
            title = stringResource(
                if (item.credentialsConfigured) {
                    R.string.provider_center_edit_key
                } else {
                    R.string.provider_center_add_key
                }
            ),
            subtitle = stringResource(R.string.provider_center_key_subtitle),
            onClick = { onOpenCredentials(item.entry.id) },
            leadingIcon = Icons.Default.Key,
        )
        if (item.credentialsConfigured) {
            SettingsActionRow(
                title = stringResource(R.string.provider_center_delete_key),
                subtitle = null,
                onClick = { onDeleteCredential(item.entry.id) },
                leadingIcon = Icons.Default.Delete,
            )
        }
    }
}

@Composable
private fun CompletionMessageRow(
    message: ProviderCenterCompletion,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = when (message) {
        is ProviderCenterCompletion.Installed ->
            stringResource(R.string.provider_center_msg_installed, message.packageName, message.versionName)
        ProviderCenterCompletion.CredentialSaved ->
            stringResource(R.string.provider_center_msg_credential_saved)
        ProviderCenterCompletion.CredentialDeleted ->
            stringResource(R.string.provider_center_msg_credential_deleted)
        is ProviderCenterCompletion.ContractVerified ->
            stringResource(
                R.string.provider_center_msg_verified,
                message.contract.capability,
                message.contract.engineStatus,
            )
        ProviderCenterCompletion.VerificationOpened ->
            stringResource(R.string.provider_center_msg_verification_opened)
        ProviderCenterCompletion.UninstallOpened ->
            stringResource(R.string.provider_center_msg_uninstall_opened)
        is ProviderCenterCompletion.Failed ->
            stringResource(R.string.provider_center_msg_failed, message.reason.name)
    }
    SettingsGroupCard(modifier = modifier.fillMaxWidth()) {
        SettingsActionRow(
            title = text,
            subtitle = null,
            onClick = onDismiss,
            leadingIcon = Icons.Default.CheckCircle,
        )
    }
}

@Composable
private fun StatusRow(item: ProviderCenterItem) {
    Text(
        text = buildString {
            append(stringResource(R.string.provider_center_installed_version, item.installed?.versionName.orEmpty()))
            append('\n')
            append(
                if (item.signerTrusted) {
                    stringResource(R.string.provider_center_signer_trusted)
                } else {
                    stringResource(R.string.provider_center_signer_mismatch)
                }
            )
        },
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = NuvioTheme.colors.TextSecondary,
        modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md),
    )
}

@Composable
private fun SignatureWarningRow(item: ProviderCenterItem, onUninstall: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.provider_center_signer_mismatch_hint),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.Error,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md),
        )
        SettingsActionRow(
            title = stringResource(R.string.provider_center_uninstall),
            subtitle = null,
            onClick = { onUninstall(item.entry.id) },
            leadingIcon = Icons.Default.Delete,
        )
    }
}

@Composable
private fun DisabledRow(text: String) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        color = NuvioTheme.colors.TextSecondary,
        modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md),
    )
}

@Composable
private fun InstallProgressRow(
    state: ProviderInstallState,
    providerId: String,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val label = when (state) {
            is ProviderInstallState.Downloading ->
                stringResource(R.string.provider_center_downloading, (state.progress * 100).toInt())
            ProviderInstallState.Verifying -> stringResource(R.string.provider_center_verifying)
            ProviderInstallState.Installing -> stringResource(R.string.provider_center_installing)
            is ProviderInstallState.Installed -> stringResource(R.string.provider_center_installed)
            is ProviderInstallState.Failed -> stringResource(R.string.provider_center_install_failed)
            ProviderInstallState.Idle -> stringResource(R.string.provider_center_preparing)
        }
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md),
        )
        if (state is ProviderInstallState.Failed) {
            SettingsActionRow(
                title = stringResource(R.string.provider_center_retry),
                subtitle = null,
                onClick = { onCancel() },
                leadingIcon = Icons.Default.Refresh,
            )
        } else if (state !is ProviderInstallState.Installed) {
            SettingsActionRow(
                title = stringResource(R.string.provider_center_cancel_install),
                subtitle = null,
                onClick = onCancel,
                leadingIcon = Icons.Default.Delete,
            )
        }
    }
}
