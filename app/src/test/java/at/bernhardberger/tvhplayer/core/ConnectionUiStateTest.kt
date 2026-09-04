package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.core.SessionOperationFailure
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
            SessionFailure.AuthenticationRejected to ConnectionFailureKind.AUTHENTICATION,
            SessionFailure.PermissionDenied to ConnectionFailureKind.PERMISSION_DENIED,
            SessionFailure.IncompatibleServer to ConnectionFailureKind.INCOMPATIBLE_SERVER,
            SessionFailure.NoChannels to ConnectionFailureKind.ZERO_CHANNELS,
            SessionFailure.ServerUnreachable to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.NetworkUnavailable to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.TransportUnavailable to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.SynchronizationFailed(SessionOperationFailure.SERVER_REJECTED) to
                ConnectionFailureKind.OTHER,
            SessionFailure.SynchronizationFailed(SessionOperationFailure.ACCESS_DENIED) to
                ConnectionFailureKind.PERMISSION_DENIED,
            SessionFailure.SynchronizationFailed(SessionOperationFailure.CONNECTION_LIMIT) to
                ConnectionFailureKind.OTHER,
            SessionFailure.SynchronizationFailed(SessionOperationFailure.TIMEOUT) to
                ConnectionFailureKind.TIMEOUT,
            SessionFailure.SynchronizationFailed(
                SessionOperationFailure.TRANSPORT_UNAVAILABLE
            ) to ConnectionFailureKind.UNREACHABLE,
            SessionFailure.SynchronizationFailed(SessionOperationFailure.NOT_SUPPORTED) to
                ConnectionFailureKind.INCOMPATIBLE_SERVER,
            SessionFailure.UnexpectedFailure to ConnectionFailureKind.OTHER,
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
        assertEquals(
            ConnectionRecoveryAction.RETRY,
            ConnectionUiState.Error(
                ConnectionFailureKind.ZERO_CHANNELS,
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
    fun playbackReconnectActionRequiresExplicitRetryFailure() {
        val retryCommandByState = listOf(
            ConnectionState.Disconnected to PlaybackRetryCommand.NONE,
            ConnectionState.Connecting to PlaybackRetryCommand.NONE,
            ConnectionState.Connected to PlaybackRetryCommand.NONE,
            ConnectionState.Error(
                ConnectionFailureKind.OTHER,
                SessionRecoveryDisposition.AUTOMATIC_BACKOFF,
            ) to PlaybackRetryCommand.NONE,
            ConnectionState.Error(
                ConnectionFailureKind.ZERO_CHANNELS,
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
            val model = playbackRecoveryUiModel(
                surface = PlaybackRecoverySurface.LIVE,
                connectionState = connectionState,
                retryTargetAvailable = false,
                secondaryAction = PlaybackRecoverySecondaryAction.CLOSE,
            )

            assertEquals(expectedCommand, model.retryCommand)
            assertEquals(
                if (expectedCommand == PlaybackRetryCommand.NONE) {
                    PlaybackRecoveryInitialAction.CLOSE
                } else {
                    PlaybackRecoveryInitialAction.RETRY
                },
                model.initialAction,
            )
        }
    }
}
