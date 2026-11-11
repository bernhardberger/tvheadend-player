package at.bernhardberger.tvhplayer.ui

import androidx.compose.runtime.Composable
import at.bernhardberger.tvheadend.sdk.core.ServerProfileAuthenticationMode
import at.bernhardberger.tvheadend.sdk.core.ServerProfileId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileListResult
import at.bernhardberger.tvheadend.sdk.core.ServerProfileSummary
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.screens.settings.ConnectionOverviewState
import at.bernhardberger.tvhplayer.ui.screens.settings.ConnectionOverviewStatus
import at.bernhardberger.tvhplayer.ui.screens.settings.ServerProfileActions
import at.bernhardberger.tvhplayer.ui.screens.settings.settingsServerLevels

/** A stored server for Settings tests and captures; hosts and users are fictional. */
internal fun fixtureServer(
    index: Int,
    host: String,
    username: String? = null,
    label: String? = null,
    port: Int = 9982,
    credentialsAvailable: Boolean = true,
): ServerProfileSummary = ServerProfileSummary.create(
    id = ServerProfileId.from("00000000-0000-4000-8000-%012d".format(index)),
    label = label,
    host = host,
    port = port,
    authenticationMode = if (username == null) ServerProfileAuthenticationMode.ANONYMOUS else ServerProfileAuthenticationMode.PASSWORD,
    username = username,
    credentialsAvailable = credentialsAvailable,
)

internal val fixtureActiveServer = fixtureServer(1, "tvheadend.local", username = "fixture")

internal fun fixtureStatus(title: String, state: ConnectionOverviewState = ConnectionOverviewState.OFFLINE) =
    ConnectionOverviewStatus(title, state)

/** The Connection list with one server in use, and that server's level, as Settings builds them. */
@Composable
internal fun fixtureConnectionLevels(
    status: ConnectionOverviewStatus,
    profiles: ServerProfileListResult? = ServerProfileListResult.Available(listOf(fixtureActiveServer), fixtureActiveServer.id),
    actions: ServerProfileActions = ServerProfileActions(),
    showRetry: Boolean = false,
    connecting: Boolean = false,
    onRetry: () -> Unit = {},
): List<DepthLevel> = settingsServerLevels(profiles, status, actions, showRetry, connecting, onRetry)
