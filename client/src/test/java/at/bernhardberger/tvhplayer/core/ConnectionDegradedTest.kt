package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import org.junit.Assert.*
import org.junit.Test

class ConnectionDegradedTest {
    @Test fun initialConnectionLossRecoveryAndConfiguration() {
        for (state in listOf(ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels, ConnectionUiState.Reconnecting)) {
            assertFalse(state.isDegraded(false))
            assertTrue(state.isDegraded(true))
        }
        for (lost in listOf(false, true)) {
            assertFalse(ConnectionUiState.Ready.isDegraded(lost))
            assertFalse(ConnectionUiState.NeedsConfiguration.isDegraded(lost))
            assertTrue(ConnectionUiState.CredentialUnavailable.isDegraded(lost))
            assertTrue(ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE,
                SessionRecoveryDisposition.AUTOMATIC_BACKOFF).isDegraded(lost))
        }
    }
}
