package at.bernhardberger.tvhplayer.touch.connect

import androidx.annotation.StringRes
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.toAppError
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.ui.common.hintRes
import at.bernhardberger.tvhplayer.ui.common.titleRes
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation

/** The catalog's title, except where the shared words name the TV this app does not run on. */
@StringRes
internal fun AppErrorCondition.touchTitleRes(): Int =
    if (this == AppErrorCondition.NO_NETWORK) R.string.connect_network_title else titleRes()

/** The catalog's hint, with the same exception as [touchTitleRes]. */
@StringRes
internal fun AppErrorCondition.touchHintRes(): Int? =
    if (this == AppErrorCondition.NO_NETWORK) R.string.connect_network_hint else hintRes()

/**
 * The Connections card's state line, by the mapping the TV's connection overview uses: a catalog
 * condition reads its title (reconnecting keeps the working look), else the overview's own words.
 */
internal fun connectionStatus(
    profile: ServerProfileReadResult?,
    presentation: AppConnectionPresentation,
): ConnectionStatus {
    val connection = presentation.connection
    val failure = connectionFailure(profile, presentation)
    return when {
        failure == AppErrorCondition.RECONNECTING -> working(failure.touchTitleRes())
        failure != null -> failed(failure.touchTitleRes())
        profile == null -> working(SharedR.string.connection_overview_loading)
        profile == ServerProfileReadResult.Missing ||
            connection == ConnectionUiState.NeedsConfiguration ->
            failed(SharedR.string.connection_overview_not_configured)
        presentation.degraded && presentation.currentChannelReadiness is CurrentChannelReadiness.Browsable ->
            failed(SharedR.string.connection_offline_saved)
        // A failure the catalog has no condition for.
        connection is ConnectionUiState.Error -> failed(SharedR.string.connection_overview_unavailable)
        connection == ConnectionUiState.SyncingChannels -> working(SharedR.string.connection_overview_synchronizing)
        connection == ConnectionUiState.Ready -> ConnectionStatus.Connected
        else -> working(SharedR.string.connection_overview_connecting)
    }
}

private fun connectionFailure(
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

private fun working(@StringRes text: Int) = ConnectionStatus(ConnectionStatus.Kind.Working, text)

private fun failed(@StringRes text: Int) = ConnectionStatus(ConnectionStatus.Kind.Failed, text)
