package at.bernhardberger.tvhplayer.core

data class ConnectionAttemptState(
    val currentAttemptId: Long = 0L,
)

data class ConnectionAttempt(
    val state: ConnectionAttemptState,
    val attemptId: Long,
)

fun beginConnectionAttempt(state: ConnectionAttemptState): ConnectionAttempt {
    val attemptId = state.currentAttemptId + 1L
    return ConnectionAttempt(
        state = ConnectionAttemptState(currentAttemptId = attemptId),
        attemptId = attemptId,
    )
}

fun invalidateConnectionAttempts(state: ConnectionAttemptState): ConnectionAttemptState =
    ConnectionAttemptState(currentAttemptId = state.currentAttemptId + 1L)

fun connectionAttemptMayPublish(state: ConnectionAttemptState, attemptId: Long): Boolean =
    state.currentAttemptId == attemptId

fun shouldRestartConnectionRetry(
    reconnectJobActive: Boolean,
    connectionIsConnecting: Boolean,
): Boolean = !reconnectJobActive || !connectionIsConnecting

data class ReconnectAttemptPhase(
    val inFlightAttemptId: Long? = null,
)

fun beginReconnectAttemptPhase(
    phase: ReconnectAttemptPhase,
    attemptId: Long,
): ReconnectAttemptPhase = phase.copy(inFlightAttemptId = attemptId)

fun completeReconnectAttemptPhase(
    phase: ReconnectAttemptPhase,
    attemptId: Long,
): ReconnectAttemptPhase = if (phase.inFlightAttemptId == attemptId) {
    ReconnectAttemptPhase()
} else {
    phase
}

fun invalidateReconnectAttemptPhase(
    phase: ReconnectAttemptPhase,
): ReconnectAttemptPhase = phase.copy(inFlightAttemptId = null)
