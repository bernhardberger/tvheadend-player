package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ColdStartPresentationTest {
    private val channels = listOf(Channel.create(ChannelId(1)))
    private val unreachable = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
    private fun actionable(condition: AppErrorCondition, vararg actions: AppErrorAction) =
        MainStartupPresentation.Actionable(AppError(condition, actions.toList()))

    @Test fun savedOrCurrentChannelsAndAnEmptyServerShowNoStartupScreen() {
        for (connection in listOf(ConnectionUiState.Connecting, unreachable, ConnectionUiState.SyncingChannels)) {
            assertEquals(MainStartupPresentation.Inactive,
                coldStartPresentation(connection, CurrentChannelReadiness.Browsable(channels), failure = null))
        }
        assertEquals(MainStartupPresentation.Inactive,
            coldStartPresentation(ConnectionUiState.Ready, CurrentChannelReadiness.Ready(channels), null))
        // The Channels screen keeps its own no-channels state.
        assertEquals(MainStartupPresentation.Inactive,
            coldStartPresentation(ConnectionUiState.Ready, CurrentChannelReadiness.Ready(emptyList()), null))
    }

    @Test fun withoutChannelsTheStartupScreenShowsItsStages() {
        val waiting = CurrentChannelReadiness.Waiting
        assertEquals(MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
            coldStartPresentation(ConnectionUiState.Connecting, waiting, null))
        assertEquals(MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA),
            coldStartPresentation(ConnectionUiState.SyncingChannels, waiting, null))
        assertEquals(MainStartupPresentation.Passive(MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA),
            coldStartPresentation(ConnectionUiState.Ready, waiting, null))
    }

    @Test fun failuresAreActionableWithCatalogConditionsEvenDuringAutomaticBackoff() {
        val waiting = CurrentChannelReadiness.Waiting
        fun shown(connection: ConnectionUiState) = coldStartPresentation(connection, waiting, waitingConnectionFailure(connection, null))
        // 110: the connection retries by itself, so only Connection settings.
        assertEquals(actionable(AppErrorCondition.RECONNECTING, AppErrorAction.CONNECTION_SETTINGS), shown(unreachable))
        assertEquals(actionable(AppErrorCondition.UNREACHABLE, AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS),
            shown(ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)))
        assertEquals(actionable(AppErrorCondition.TIMEOUT, AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS),
            shown(ConnectionUiState.Error(ConnectionFailureKind.TIMEOUT, SessionRecoveryDisposition.EXPLICIT_RETRY)))
        assertEquals(actionable(AppErrorCondition.AUTHENTICATION_REJECTED, AppErrorAction.CONNECTION_SETTINGS),
            shown(ConnectionUiState.Error(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED)))
        assertEquals(actionable(AppErrorCondition.CREDENTIAL_UNAVAILABLE, AppErrorAction.CONNECTION_SETTINGS),
            shown(ConnectionUiState.CredentialUnavailable))
        assertEquals(actionable(AppErrorCondition.NOT_CONFIGURED, AppErrorAction.CONNECTION_SETTINGS),
            shown(ConnectionUiState.NeedsConfiguration))
    }

    @Test fun failureStaysThroughAutomaticAttemptsAndRetryShowsConnectingUntilTheNextFailure() {
        var failures = WaitingConnectionFailure()
        fun observe(connection: ConnectionUiState): MainStartupPresentation {
            val shown = coldStartPresentation(connection, CurrentChannelReadiness.Waiting, failures.shown(connection))
            failures = failures.observe(connection)
            return shown
        }
        val failed = actionable(AppErrorCondition.RECONNECTING, AppErrorAction.CONNECTION_SETTINGS)
        val connecting = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
        assertEquals(connecting, observe(ConnectionUiState.Connecting))
        assertEquals(failed, observe(unreachable))
        // The backoff's own next attempt keeps the actions where they are.
        assertEquals(failed, observe(ConnectionUiState.Connecting))
        assertEquals(failed, observe(unreachable))
        failures = failures.retry()
        // Until the session leaves the retried failure, then while it connects again.
        assertEquals(connecting, observe(unreachable))
        assertEquals(connecting, observe(ConnectionUiState.Connecting))
        assertEquals(failed, observe(unreachable))
        assertEquals(MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA), observe(ConnectionUiState.SyncingChannels))
        assertEquals(connecting, observe(ConnectionUiState.Connecting))
    }

    @Test fun autoplayStartupShowsItsAutomaticBackoffAsTheReconnectingAlert() {
        val requests = ApplianceLaunchRequests()
        requests.request()
        val startup = MainStartupState.Ready(at.bernhardberger.tvhplayer.settings.ServerSettings(host = "offline.invalid"), autoStartPlayback = true)
        assertEquals(actionable(AppErrorCondition.RECONNECTING, AppErrorAction.CONNECTION_SETTINGS),
            mainStartupPresentation(startup, requests.state.value, unreachable, CurrentChannelReadiness.Waiting))
        // Saved channels never block: autoplay then waits as reconnecting.
        assertEquals(MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING),
            mainStartupPresentation(startup, requests.state.value, unreachable, CurrentChannelReadiness.Browsable(channels)))
    }

    @Test fun aFailureStaysThroughAutomaticAttemptsUntilTheServerAnswersAndRetryClearsIt() {
        var failures = WaitingConnectionFailure().observe(unreachable)
        for (connection in listOf(ConnectionUiState.Connecting, ConnectionUiState.Reconnecting)) {
            assertEquals(unreachable, failures.shown(connection))
            failures = failures.observe(connection)
        }
        assertEquals(null, failures.shown(ConnectionUiState.SyncingChannels))
        assertEquals(WaitingConnectionFailure(), failures.observe(ConnectionUiState.SyncingChannels))
        failures = WaitingConnectionFailure().observe(unreachable).retry()
        assertEquals(null, failures.shown(unreachable))
        failures = failures.observe(unreachable)
        assertEquals(null, failures.shown(ConnectionUiState.Connecting))
        failures = failures.observe(ConnectionUiState.Connecting)
        assertEquals(unreachable, failures.shown(unreachable))
    }

    @Test fun aShownScreenStaysReadableAndOneNeverShownLeavesAtOnce() {
        assertEquals(0L, coldStartRemainingVisibleMillis(shownAtMillis = null, nowMillis = 10_000L))
        assertEquals(300L, coldStartRemainingVisibleMillis(shownAtMillis = 1_000L, nowMillis = 1_200L))
        assertEquals(0L, coldStartRemainingVisibleMillis(shownAtMillis = 1_000L, nowMillis = 1_600L))
    }
}
