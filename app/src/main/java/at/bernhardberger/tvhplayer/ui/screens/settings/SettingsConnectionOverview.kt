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
import kotlinx.coroutines.launch

internal const val EDIT_CONNECTION = "edit-connection"
internal const val RETRY_CONNECTION = "retry-connection"

/**
 * Settings > Connection: the stored servers, adding one, and "Try again" while the server in use is
 * unreachable and the fix is not in Settings. The status of the server in use shows on its own row.
 */
@Composable
internal fun settingsConnectionLevels(
    presentation: AppConnectionPresentation,
    onRetry: () -> Unit,
    actions: ServerProfileActions,
    owner: AppProfileOwner = koinInject(),
    switching: Boolean = false,
): List<DepthLevel> {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val profiles by owner.profiles.collectAsStateWithLifecycle()
    val switchInProgress by owner.switchInProgress.collectAsStateWithLifecycle()
    val connection = presentation.connection
    // A switch the user started shows in its own dialog; the rows say nothing about it, least of all
    // on the connection still in use, which the attempt disconnects.
    val status = if (switching) null else connectionOverviewStatus(profile, presentation)
    return settingsServerLevels(profiles, status, actions,
        actionsEnabled = !switchInProgress && !switching,
        showRetry = !switching && presentation.degraded &&
            connection.primaryRecoveryAction() != ConnectionRecoveryAction.SETTINGS,
        connecting = connection == ConnectionUiState.Connecting || connection == ConnectionUiState.Reconnecting ||
            connection == ConnectionUiState.SyncingChannels,
        onRetry = onRetry)
}

/** What a connection's status icon shows: connected, on its way to connecting, or not connected. */
internal enum class ConnectionOverviewState { CONNECTED, CONNECTING, OFFLINE }

/** The overview's status: the title of a failure or of its own state, and which icon state it belongs to. */
internal class ConnectionOverviewStatus(val title: String, val state: ConnectionOverviewState)

/** Read through [owner], for the root row. */
@Composable
internal fun connectionOverviewStatus(
    presentation: AppConnectionPresentation,
    owner: AppProfileOwner = koinInject(),
): ConnectionOverviewStatus {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    return connectionOverviewStatus(profile, presentation)
}

@Composable
internal fun connectionOverviewStatus(
    profile: ServerProfileReadResult?,
    presentation: AppConnectionPresentation,
): ConnectionOverviewStatus {
    val connection = presentation.connection
    val context = LocalContext.current
    val text = remember(context) { AppErrorText(context) }
    val failure = connectionOverviewFailure(profile, presentation)
    if (failure != null) {
        val state = if (failure == AppErrorCondition.RECONNECTING) {
            ConnectionOverviewState.CONNECTING
        } else {
            ConnectionOverviewState.OFFLINE
        }
        return ConnectionOverviewStatus(text.title(failure), state)
    }
    val connecting = ConnectionOverviewState.CONNECTING
    val offline = ConnectionOverviewState.OFFLINE
    val connected = ConnectionOverviewState.CONNECTED
    val (title, state) = when {
        profile == null -> R.string.connection_overview_loading to connecting
        profile == ServerProfileReadResult.Missing -> R.string.connection_overview_not_configured to offline
        presentation.degraded && presentation.currentChannelReadiness is CurrentChannelReadiness.Browsable ->
            R.string.connection_offline_saved to offline
        connection == ConnectionUiState.SyncingChannels -> R.string.connection_overview_synchronizing to connecting
        connection == ConnectionUiState.Ready -> R.string.connection_overview_connected to connected
        connection == ConnectionUiState.NeedsConfiguration -> R.string.connection_overview_not_configured to offline
        else -> R.string.connection_overview_connecting to connecting
    }
    return ConnectionOverviewStatus(stringResource(title), state)
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
