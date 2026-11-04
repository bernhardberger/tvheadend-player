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
internal const val CONNECTION_STATUS = "connection-status"

/**
 * Settings > Connection: the stored servers, adding one, and the connection's status, with
 * "Try again" while the server in use is unreachable and the fix is not in Settings.
 */
@Composable
internal fun settingsConnectionLevels(
    presentation: AppConnectionPresentation,
    onRetry: () -> Unit,
    actions: ServerProfileActions,
    owner: AppProfileOwner = koinInject(),
): List<DepthLevel> {
    val profile by owner.serverProfile.collectAsStateWithLifecycle()
    val profiles by owner.profiles.collectAsStateWithLifecycle()
    val hidden by owner.hiddenServerProfileIds.collectAsStateWithLifecycle(initialValue = emptySet())
    val ask by owner.askAtStartup.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val listed by owner.switcherProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val connection = presentation.connection
    val status = connectionOverviewStatus(profile, presentation)
    return settingsServerLevels(profiles, hidden, status.text, actions,
        showRetry = presentation.degraded && connection.primaryRecoveryAction() != ConnectionRecoveryAction.SETTINGS,
        connecting = connection == ConnectionUiState.Connecting || connection == ConnectionUiState.Reconnecting ||
            connection == ConnectionUiState.SyncingChannels,
        onRetry = onRetry,
        askAtStartup = ask.takeIf { !at.bernhardberger.tvhplayer.BuildConfig.APPLIANCE_BUILD && listed.size >= 2 },
        onAskAtStartup = { scope.launch { owner.setAskAtStartup(it) } })
}

/** The overview's status: a failure's title and reason, or its own state with no reason. */
internal class ConnectionOverviewStatus(val title: String, val reason: String? = null) {
    val text: String get() = if (reason == null) title else "$title\n$reason"
}

/** Read through [owner], for the root row that shows only the status title. */
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
    if (failure != null) return ConnectionOverviewStatus(text.title(failure), text.reason(failure))
    return ConnectionOverviewStatus(stringResource(when {
        profile == null -> R.string.connection_overview_loading
        profile == ServerProfileReadResult.Missing -> R.string.connection_overview_not_configured
        presentation.degraded && presentation.currentChannelReadiness is CurrentChannelReadiness.Browsable ->
            R.string.connection_offline_saved
        connection == ConnectionUiState.SyncingChannels -> R.string.connection_overview_synchronizing
        connection == ConnectionUiState.Ready -> R.string.connection_overview_connected
        connection == ConnectionUiState.NeedsConfiguration -> R.string.connection_overview_not_configured
        else -> R.string.connection_overview_connecting
    }))
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
