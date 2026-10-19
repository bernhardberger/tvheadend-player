package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.data.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionUiStateTest {
    @Test
    fun unavailableFailureKeepsDetailedCopyKindAndSdkRecoveryGuidance() {
        val cases = listOf(
            SessionFailure.AUTHENTICATION_REJECTED to ConnectionFailureKind.AUTHENTICATION,
            SessionFailure.INCOMPATIBLE_SERVER to ConnectionFailureKind.INCOMPATIBLE_SERVER,
            SessionFailure.SERVER_UPGRADE_REQUIRED to ConnectionFailureKind.INCOMPATIBLE_SERVER,
            SessionFailure.METADATA_OVERFLOW to ConnectionFailureKind.OTHER,
            SessionFailure.SERVER_UNREACHABLE to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.NETWORK_UNAVAILABLE to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.TRANSPORT_UNAVAILABLE to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.synchronizationFailed(TvheadendFailure.SERVER_REJECTED) to
                ConnectionFailureKind.OTHER,
            SessionFailure.synchronizationFailed(TvheadendFailure.ACCESS_DENIED) to
                ConnectionFailureKind.PERMISSION_DENIED,
            SessionFailure.synchronizationFailed(TvheadendFailure.CONNECTION_LIMIT) to
                ConnectionFailureKind.OTHER,
            SessionFailure.synchronizationFailed(TvheadendFailure.TIMEOUT) to
                ConnectionFailureKind.TIMEOUT,
            SessionFailure.synchronizationFailed(
                TvheadendFailure.TRANSPORT_UNAVAILABLE
            ) to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.synchronizationFailed(TvheadendFailure.NOT_SUPPORTED) to
                ConnectionFailureKind.INCOMPATIBLE_SERVER,
            SessionFailure.UNEXPECTED_FAILURE to ConnectionFailureKind.OTHER,
            SessionFailure.synchronizationFailed(TvheadendFailure.MALFORMED_RESPONSE) to
                ConnectionFailureKind.OTHER,
        )

        cases.forEach { (failure, kind) ->
            assertEquals(
                ConnectionUiState.Error(kind, failure.recoveryDisposition),
                SessionState.Unavailable(failure).toConnectionUiState(),
            )
            assertEquals(
                ConnectionState.Error(kind, failure.recoveryDisposition),
                SessionState.Unavailable(failure).toConnectionState(),
            )
        }
    }

    @Test
    fun recoveryActionUsesSdkDispositionAndKeepsSubscriptionRetry() {
        assertEquals(ConnectionRecoveryAction.RETRY,
            SessionState.Unavailable(SessionFailure.SERVER_UPGRADE_REQUIRED).toConnectionUiState().primaryRecoveryAction())
        assertEquals(ConnectionRecoveryAction.NONE,
            SessionState.Unavailable(SessionFailure.METADATA_OVERFLOW).toConnectionUiState().primaryRecoveryAction())
        assertEquals(ConnectionRecoveryAction.RETRY,
            SessionState.Unavailable(SessionFailure.synchronizationFailed(TvheadendFailure.MALFORMED_RESPONSE))
                .toConnectionUiState().primaryRecoveryAction())
        assertEquals(
            ConnectionRecoveryAction.RETRY,
            ConnectionUiState.Error(
                ConnectionFailureKind.OTHER,
                SessionRecoveryDisposition.EXPLICIT_RETRY,
            ).primaryRecoveryAction(),
        )
        assertEquals(
            ConnectionRecoveryAction.SETTINGS,
            ConnectionUiState.Error(
                ConnectionFailureKind.AUTHENTICATION,
                SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED,
            ).primaryRecoveryAction(),
        )
        assertEquals(
            ConnectionRecoveryAction.NONE,
            ConnectionUiState.Error(
                ConnectionFailureKind.UNREACHABLE,
                SessionRecoveryDisposition.AUTOMATIC_BACKOFF,
            ).primaryRecoveryAction(),
        )
    }

    @Test
    fun recordingReconnectActionRequiresExplicitRetryFailure() {
        val retryCommandByState = listOf(
            ConnectionState.Disconnected to PlaybackRetryCommand.NONE,
            ConnectionState.Connecting to PlaybackRetryCommand.NONE,
            ConnectionState.Connected to PlaybackRetryCommand.RESUME_RECORDING,
            ConnectionState.Error(
                ConnectionFailureKind.OTHER,
                SessionRecoveryDisposition.AUTOMATIC_BACKOFF,
            ) to PlaybackRetryCommand.NONE,
            ConnectionState.Error(
                ConnectionFailureKind.OTHER,
                SessionRecoveryDisposition.EXPLICIT_RETRY,
            ) to PlaybackRetryCommand.RECONNECT,
            ConnectionState.Error(
                ConnectionFailureKind.AUTHENTICATION,
                SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED,
            ) to PlaybackRetryCommand.NONE,
            ConnectionState.Error(
                ConnectionFailureKind.OTHER,
                SessionRecoveryDisposition.NO_RETRY,
            ) to PlaybackRetryCommand.NONE,
        )

        retryCommandByState.forEach { (connectionState, expectedCommand) ->
            assertEquals(expectedCommand, recordingRetryCommand(connectionState, retryTargetAvailable = true))
            // Without a recording to resume nothing retries, whatever the connection.
            assertEquals(
                PlaybackRetryCommand.NONE,
                recordingRetryCommand(connectionState, retryTargetAvailable = false),
            )
        }
    }
}
