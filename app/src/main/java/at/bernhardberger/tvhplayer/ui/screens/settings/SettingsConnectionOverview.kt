package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.*
import org.koin.compose.koinInject

internal const val CONNECTION_EDITOR = "connection-editor"
internal const val EDIT_CONNECTION = "edit-connection"
internal const val RETRY_CONNECTION = "retry-connection"

@Composable
internal fun settingsConnectionLevels(
    navigation: DepthNavigationState,
    presentation: AppConnectionPresentation,
    onRetry: () -> Unit,
    owner: AppProfileOwner = koinInject(),
): List<DepthLevel> {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val available = profile as? ServerProfileReadResult.Available
    val connection = presentation.connection
    val saved = presentation.currentChannelReadiness is CurrentChannelReadiness.Browsable
    val status = stringResource(when {
        profile == null -> R.string.connection_overview_loading
        profile == ServerProfileReadResult.Missing -> R.string.connection_overview_not_configured
        profile == ServerProfileReadResult.Unavailable -> R.string.credential_unavailable
        presentation.degraded && saved && (connection !is ConnectionUiState.Error ||
            connection.kind == ConnectionFailureKind.UNREACHABLE) -> R.string.connection_offline_saved
        presentation.degraded && connection !is ConnectionUiState.Error -> R.string.status_connection_failed_unreachable
        else -> when (connection) {
            ConnectionUiState.Connecting, ConnectionUiState.Reconnecting -> R.string.connection_overview_connecting
            ConnectionUiState.SyncingChannels -> R.string.connection_overview_synchronizing
            ConnectionUiState.Ready -> R.string.connection_overview_connected
            ConnectionUiState.NeedsConfiguration -> R.string.connection_overview_not_configured
            ConnectionUiState.CredentialUnavailable -> R.string.credential_unavailable
            is ConnectionUiState.Error -> when (connection.kind) {
                ConnectionFailureKind.AUTHENTICATION -> R.string.status_connection_failed_authentication
                ConnectionFailureKind.UNREACHABLE -> R.string.status_connection_failed_unreachable
                ConnectionFailureKind.TIMEOUT -> R.string.status_connection_failed_timeout
                ConnectionFailureKind.INCOMPATIBLE_SERVER -> R.string.status_connection_failed_incompatible
                ConnectionFailureKind.PERMISSION_DENIED -> R.string.status_connection_failed_permission
                ConnectionFailureKind.ZERO_CHANNELS -> R.string.status_connection_failed_zero_channels
                ConnectionFailureKind.OTHER -> R.string.status_connection_failed_other
            }
        }
    })
    return listOf(
        settingsConnectionOverview(status, available?.host, available?.port,
            degraded = presentation.degraded, onRetry = onRetry,
            connecting = connection == ConnectionUiState.Connecting || connection == ConnectionUiState.Reconnecting ||
                connection == ConnectionUiState.SyncingChannels,
            recoveryRequiresSettings = connection.primaryRecoveryAction() == ConnectionRecoveryAction.SETTINGS),
        settingsLevel(CONNECTION_EDITOR, stringResource(R.string.edit_connection),
            listOf(settingsRow("editor-placeholder", stringResource(R.string.edit_connection))),
            activeContent = { requester, left ->
                val visit = navigation.stack.visit
                SettingsConnection(requester, onNavigateLeft = left, onSaved = {
                    if (navigation.stack.visit == visit && navigation.stack.active.levelId == CONNECTION_EDITOR) navigation.pop()
                })
            }),
    )
}

/** Safe endpoint fields only. No editor state, credential read, username or password reaches this API. */
@Composable
internal fun settingsConnectionOverview(
    status: String, host: String?, port: Int?, degraded: Boolean = false,
    onRetry: () -> Unit = {}, recoveryRequiresSettings: Boolean = false,
    connecting: Boolean = false,
): DepthLevel {
    val showRetry = degraded && !recoveryRequiresSettings
    val retryAction: () -> Unit = { if (!connecting) onRetry() }
    val missing = stringResource(R.string.connection_overview_unavailable)
    val edit = settingsRow(EDIT_CONNECTION, stringResource(R.string.edit_connection),
        supporting = stringResource(R.string.edit_connection_description), trailingIcon = R.drawable.ic_edit)
    val retry = settingsRow(RETRY_CONNECTION, stringResource(R.string.connection_try_again),
        supporting = stringResource(if (connecting) R.string.connection_overview_connecting else R.string.connection_retry_description),
        trailingIcon = R.drawable.ic_refresh, onClick = retryAction)
    val retryRow = DepthRow(DepthItem(RETRY_CONNECTION), onActivate = retryAction) { modifier, activate ->
        Column {
            ConnectionValueRow(stringResource(R.string.connection_overview_status), status)
            retry.content(modifier, activate)
        }
    }
    // Information scrolls with its next action; it never becomes a D-pad stop.
    val row = DepthRow(DepthItem(EDIT_CONNECTION), leafLevelId = CONNECTION_EDITOR) { modifier, activate ->
        Column {
            if (!showRetry) ConnectionValueRow(stringResource(R.string.connection_overview_status), status)
            ConnectionValueRow(stringResource(R.string.connection_overview_server), host ?: missing)
            ConnectionValueRow(stringResource(R.string.port_htsp), port?.toString() ?: missing)
            edit.content(modifier, activate)
        }
    }
    return settingsLevel(SettingsSection.CONNECTION.name, stringResource(R.string.settings_connection_nav),
        (if (showRetry) listOf(retryRow) else emptyList()) + row,
        initialItemId = if (showRetry) RETRY_CONNECTION else EDIT_CONNECTION)
}

@Composable
private fun ConnectionValueRow(title: String, value: String) {
    ListItem(selected = false, onClick = {},
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth().padding(bottom = TvSpacing8)
            .focusProperties { canFocus = false }
            .clearAndSetSemantics { text = AnnotatedString("$title\n$value") },
    )
}
