package at.bernhardberger.tvhplayer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionUiStateTest {

    @Test
    fun connectionAttempt_withoutPublishedChannels_isColdLoading() {
        assertEquals(
            ConnectionUiState.Connecting,
            connectionAttemptState(hasPublishedChannels = false),
        )
    }

    @Test
    fun connectionAttempt_withPublishedChannels_isNonBlockingRecovery() {
        assertEquals(
            ConnectionUiState.Reconnecting,
            connectionAttemptState(hasPublishedChannels = true),
        )
    }

    @Test
    fun connectionError_retainsActionableFailureKind() {
        assertEquals(
            ConnectionFailureKind.AUTHENTICATION,
            ConnectionUiState.Error(ConnectionFailureKind.AUTHENTICATION).kind,
        )
    }
}
