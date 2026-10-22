package at.bernhardberger.tvhplayer.ui.screens.settings

import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings → Connection shows the catalog's 1xx title and reason for a failure, its own status otherwise. */
class ConnectionOverviewFailureTest {
    private val profile = ServerProfileReadResult.anonymous("offline.invalid", 9982)
    private val saved = CurrentChannelReadiness.Browsable(listOf(Channel.create(ChannelId(1), name = "Name 1", number = 1)))

    @Test fun failuresAreTheirCatalogConditions() {
        assertEquals(AppErrorCondition.AUTHENTICATION_REJECTED, failure(error(ConnectionFailureKind.AUTHENTICATION)))
        assertEquals(AppErrorCondition.UNREACHABLE, failure(error(ConnectionFailureKind.UNREACHABLE)))
        assertEquals(AppErrorCondition.TIMEOUT, failure(error(ConnectionFailureKind.TIMEOUT)))
        assertEquals(AppErrorCondition.INCOMPATIBLE_SERVER, failure(error(ConnectionFailureKind.INCOMPATIBLE_SERVER)))
        assertEquals(AppErrorCondition.PERMISSION_DENIED, failure(error(ConnectionFailureKind.PERMISSION_DENIED)))
        assertEquals(AppErrorCondition.LOAD_FAILED, failure(error(ConnectionFailureKind.OTHER)))
        assertEquals(AppErrorCondition.RECONNECTING,
            failure(error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)))
        assertEquals(AppErrorCondition.CREDENTIAL_UNAVAILABLE, failure(ConnectionUiState.CredentialUnavailable))
        assertEquals(AppErrorCondition.CREDENTIAL_UNAVAILABLE,
            connectionOverviewFailure(ServerProfileReadResult.Unavailable, AppConnectionPresentation(ConnectionUiState.Ready)))
    }

    @Test fun aDegradedConnectionReconnectsUnlessSavedChannelsShowItOffline() {
        assertEquals(AppErrorCondition.RECONNECTING, failure(ConnectionUiState.Reconnecting, degraded = true))
        assertNull(failure(ConnectionUiState.Reconnecting, degraded = true, readiness = saved))
        assertNull(failure(error(ConnectionFailureKind.UNREACHABLE), degraded = true, readiness = saved))
        assertEquals(AppErrorCondition.AUTHENTICATION_REJECTED,
            failure(error(ConnectionFailureKind.AUTHENTICATION), degraded = true, readiness = saved))
    }

    @Test fun workingConnectedAndUnconfiguredKeepTheirOwnStatus() {
        listOf(ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels, ConnectionUiState.Ready,
            ConnectionUiState.Reconnecting, ConnectionUiState.NeedsConfiguration).forEach { assertNull(failure(it)) }
        assertNull(connectionOverviewFailure(null, AppConnectionPresentation(error(ConnectionFailureKind.UNREACHABLE))))
        assertNull(connectionOverviewFailure(ServerProfileReadResult.Missing,
            AppConnectionPresentation(ConnectionUiState.NeedsConfiguration)))
    }

    private fun failure(
        connection: ConnectionUiState,
        degraded: Boolean = false,
        readiness: CurrentChannelReadiness = CurrentChannelReadiness.Waiting,
    ) = connectionOverviewFailure(profile, AppConnectionPresentation(connection, readiness, degraded))

    private fun error(
        kind: ConnectionFailureKind,
        disposition: SessionRecoveryDisposition = SessionRecoveryDisposition.EXPLICIT_RETRY,
    ) = ConnectionUiState.Error(kind, disposition)
}
