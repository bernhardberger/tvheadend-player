package at.bernhardberger.tvhplayer.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.serverProfileDisplayName

private const val DEFAULT_PORT = 9982

@Composable
internal fun serverAddress(profile: ServerProfileSummary): String =
    if (profile.port == DEFAULT_PORT) profile.host
    else stringResource(R.string.server_host_port, profile.host, profile.port)

internal fun ServerProfileSummary.accountName(): String? =
    username?.takeIf { authenticationMode == ServerProfileAuthenticationMode.PASSWORD && it.isNotBlank() }

/**
 * What Settings' Connection row shows under its title and by which icon: the connection's name, with
 * the status words unless the connection is connected, or that no server is in use.
 */
@Composable
internal fun connectionRootStatus(
    profiles: ServerProfileListResult?,
    status: ConnectionOverviewStatus,
): ConnectionOverviewStatus {
    val active = profiles.activeProfile()
    val title = when {
        profiles.noServerInUse() -> stringResource(R.string.server_none_in_use)
        active == null -> status.title
        status.state == ConnectionOverviewState.CONNECTED -> serverProfileDisplayName(active)
        else -> stringResource(R.string.server_detail_separator, serverProfileDisplayName(active), status.title)
    }
    val state = if (profiles.noServerInUse()) ConnectionOverviewState.OFFLINE else status.state
    return ConnectionOverviewStatus(title, state)
}

/**
 * The connection that per-connection Settings values (channel groups, stream profile) belong to, or
 * null while at most one is stored, when nothing could be mistaken for another connection's.
 */
internal fun connectionScopeName(profiles: ServerProfileListResult?): String? =
    (profiles as? ServerProfileListResult.Available)?.takeIf { it.profiles.size > 1 }
        ?.let { profiles.activeProfile() }?.let(::serverProfileDisplayName)
