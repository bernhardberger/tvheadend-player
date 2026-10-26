package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.ConnectionRecoveryAction
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.primaryRecoveryAction
import at.bernhardberger.tvhplayer.core.toAppError
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.common.AppErrorText
import at.bernhardberger.tvhplayer.ui.components.depth.*
import org.koin.compose.koinInject

internal const val CONNECTION_EDITOR = "connection-editor"
internal const val EDIT_CONNECTION = "edit-connection"
internal const val RETRY_CONNECTION = "retry-connection"
internal const val CONNECTION_STATUS = "connection-status"
internal const val CONNECTION_SERVER = "connection-server"
internal const val CONNECTION_PORT = "connection-port"

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
    val context = LocalContext.current
    val text = remember(context) { AppErrorText(context) }
    val failure = connectionOverviewFailure(profile, presentation)
    val status = if (failure != null) {
        "${text.title(failure)}\n${text.reason(failure)}"
    } else {
        stringResource(when {
            profile == null -> R.string.connection_overview_loading
            profile == ServerProfileReadResult.Missing -> R.string.connection_overview_not_configured
            presentation.degraded && presentation.currentChannelReadiness is CurrentChannelReadiness.Browsable ->
                R.string.connection_offline_saved
            connection == ConnectionUiState.SyncingChannels -> R.string.connection_overview_synchronizing
            connection == ConnectionUiState.Ready -> R.string.connection_overview_connected
            connection == ConnectionUiState.NeedsConfiguration -> R.string.connection_overview_not_configured
            else -> R.string.connection_overview_connecting
        })
    }
    return listOf(
        settingsConnectionOverview(status, available?.host, available?.port,
            degraded = presentation.degraded, onRetry = onRetry,
            connecting = connection == ConnectionUiState.Connecting || connection == ConnectionUiState.Reconnecting ||
                connection == ConnectionUiState.SyncingChannels,
            recoveryRequiresSettings = connection.primaryRecoveryAction() == ConnectionRecoveryAction.SETTINGS),
        settingsLevel(CONNECTION_EDITOR, stringResource(R.string.setup_connection),
            listOf(settingsRow("editor-placeholder", stringResource(R.string.setup_connection))),
            activeContent = { requester, _ ->
                val visit = navigation.stack.visit
                SettingsConnection(requester, onSaved = {
                    if (navigation.stack.visit == visit && navigation.stack.active.levelId == CONNECTION_EDITOR) navigation.pop()
                })
            }),
    )
}

/**
 * The catalog condition whose title and reason the overview's status shows, or null while it shows
 * its own status: loading, not configured, offline with saved channels, connecting or connected.
 * Without saved channels a degraded connection that is not an error is reconnecting (110).
 */
internal fun connectionOverviewFailure(
    profile: ServerProfileReadResult?,
    presentation: AppConnectionPresentation,
): AppErrorCondition? {
    val connection = presentation.connection
    val saved = presentation.currentChannelReadiness is CurrentChannelReadiness.Browsable
    return when {
        profile == null || profile == ServerProfileReadResult.Missing -> null
        profile == ServerProfileReadResult.Unavailable -> AppErrorCondition.CREDENTIAL_UNAVAILABLE
        presentation.degraded && saved && (connection !is ConnectionUiState.Error ||
            connection.kind == ConnectionFailureKind.UNREACHABLE) -> null
        presentation.degraded && connection !is ConnectionUiState.Error -> AppErrorCondition.RECONNECTING
        connection == ConnectionUiState.NeedsConfiguration -> null
        else -> connection.toAppError()?.condition
    }
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
    val edit = settingsRow(EDIT_CONNECTION, stringResource(R.string.setup_connection),
        supporting = stringResource(R.string.setup_connection_description), trailingIcon = R.drawable.ic_arrow_forward)
    val retry = settingsRow(RETRY_CONNECTION, stringResource(R.string.connection_try_again),
        supporting = stringResource(if (connecting) R.string.connection_overview_connecting else R.string.connection_retry_description),
        trailingIcon = R.drawable.ic_refresh, onClick = retryAction)
    val editRow = DepthRow(DepthItem(EDIT_CONNECTION), leafLevelId = CONNECTION_EDITOR, content = edit.content)
    // The current connection follows its actions as focusable rows without an action, as in TV settings.
    val info = listOf(
        settingsRow(CONNECTION_STATUS, stringResource(R.string.connection_overview_status), status),
        settingsRow(CONNECTION_SERVER, stringResource(R.string.connection_overview_server), host ?: missing),
        settingsRow(CONNECTION_PORT, stringResource(R.string.port_htsp), port?.toString() ?: missing),
    )
    return settingsLevel(SettingsSection.CONNECTION.name, stringResource(R.string.settings_connection_nav),
        (if (showRetry) listOf(retry) else emptyList()) + editRow + info,
        initialItemId = if (showRetry) RETRY_CONNECTION else EDIT_CONNECTION)
}
