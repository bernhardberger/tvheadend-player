package at.bernhardberger.tvhplayer.ui.screens.recordings

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppError
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingsEmptyStatePolicyTest {
    @Test
    fun connectionFailuresWithoutRecordingsAreTheCatalogAlert() {
        val expected = mapOf(
            ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY) to
                AppError(AppErrorCondition.UNREACHABLE, listOf(AppErrorAction.RETRY, AppErrorAction.CONNECTION_SETTINGS)),
            ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF) to
                AppError(AppErrorCondition.RECONNECTING, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            ConnectionUiState.Error(ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED) to
                AppError(AppErrorCondition.AUTHENTICATION_REJECTED, listOf(AppErrorAction.CONNECTION_SETTINGS)),
            ConnectionUiState.CredentialUnavailable to
                AppError(AppErrorCondition.CREDENTIAL_UNAVAILABLE, listOf(AppErrorAction.CONNECTION_SETTINGS)),
        )
        for ((connection, error) in expected) {
            assertEquals(error, recordingsAppError(empty = true, connection))
            // Saved recordings on screen are never blocked.
            assertNull(recordingsAppError(empty = false, connection))
        }
    }

    @Test
    fun noRecordingsLoadingAndReconnectingStayPassive() {
        assertNull(recordingsAppError(empty = true, ConnectionUiState.Ready))
        assertEquals(R.string.recordings_empty, recordingsEmptyMessageRes(ConnectionUiState.Ready))
        for (loading in listOf(ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels)) {
            assertNull(recordingsAppError(empty = true, loading))
            assertEquals(R.string.recordings_loading, recordingsEmptyMessageRes(loading))
        }
        assertNull(recordingsAppError(empty = true, ConnectionUiState.Reconnecting))
        assertEquals(R.string.recordings_reconnecting, recordingsEmptyMessageRes(ConnectionUiState.Reconnecting))
    }
}
