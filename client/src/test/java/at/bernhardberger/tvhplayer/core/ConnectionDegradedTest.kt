package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
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

    @Test fun theGuideIsAwaitedOnlyWhileItCanStillArrive() {
        val retained = EpgRepositoryState.Stale(EpgSnapshot.create())
        for (state in listOf(ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels, ConnectionUiState.Reconnecting)) {
            assertTrue(state.awaitsGuide(EpgRepositoryState.Empty))
            assertFalse(state.awaitsGuide(retained))
        }
        assertTrue(ConnectionUiState.Ready.awaitsGuide(EpgRepositoryState.Synchronizing(null)))
        assertFalse(ConnectionUiState.Ready.awaitsGuide(EpgRepositoryState.Synchronizing(EpgSnapshot.create())))
        assertFalse(ConnectionUiState.Ready.awaitsGuide(EpgRepositoryState.Current(EpgSnapshot.create())))
        assertFalse(ConnectionUiState.Ready.awaitsGuide(EpgRepositoryState.Empty))
        assertFalse(ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE,
            SessionRecoveryDisposition.AUTOMATIC_BACKOFF).awaitsGuide(EpgRepositoryState.Empty))
        assertFalse(ConnectionUiState.NeedsConfiguration.awaitsGuide(EpgRepositoryState.Empty))
    }

    @Test fun failureStaysDegradedThroughRetryAttemptsUntilAConnectionSucceeds() {
        val failed = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
        // Before a failure, initial connecting is not degraded; the backoff loop then alternates.
        val attempts = listOf(ConnectionUiState.Connecting, failed, ConnectionUiState.Connecting, failed,
            ConnectionUiState.Connecting, ConnectionUiState.SyncingChannels, ConnectionUiState.Ready, ConnectionUiState.Connecting)
        val shown = attempts.runningFold(false) { before, state -> state.isDegraded(lossAnnounced = false, degradedBefore = before) }.drop(1)
        assertEquals(listOf(false, true, true, true, true, true, false, false), shown)
        assertFalse(ConnectionUiState.NeedsConfiguration.isDegraded(lossAnnounced = false, degradedBefore = true))
    }
}
