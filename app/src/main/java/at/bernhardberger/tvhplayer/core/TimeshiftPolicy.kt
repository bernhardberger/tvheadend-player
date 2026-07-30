package at.bernhardberger.tvhplayer.core

import kotlin.math.absoluteValue

const val TIMESHIFT_SEEK_STEP_MS = 30_000L
const val TIMESHIFT_LIVE_EDGE_TOLERANCE_MS = 5_000L
const val REQUESTED_TIMESHIFT_PERIOD_SEC = 7_200

data class TimeshiftState(
    val available: Boolean = false,
    val bufferStartMs: Long = 0,
    val positionMs: Long = 0,
    val liveEdgeMs: Long = 0,
    val paused: Boolean = false,
    val serverStartUs: Long? = null,
    val serverEndUs: Long? = null,
)

data class TimeshiftSeekDecision(
    val targetMs: Long,
    val deltaMs: Long,
    val clamped: Boolean,
)

data class TimeshiftPositionPresentation(
    val atLiveEdge: Boolean,
    val behindLiveMs: Long,
)

data class TimeshiftSeekQueueState(
    val pendingDeltaMs: Long = 0L,
    val pendingTargetMs: Long? = null,
    val pendingClamped: Boolean = false,
    val dispatchInFlight: Boolean = false,
    val inFlightBaseMs: Long? = null,
    val inFlightTargetMs: Long? = null,
    val projectedFromPositionMs: Long? = null,
    val projectedPositionMs: Long? = null,
)

data class TimeshiftSeekDispatch(
    val queue: TimeshiftSeekQueueState,
    val deltaMs: Long,
)

fun timeshiftPositionPresentation(
    positionMs: Long,
    liveEdgeMs: Long,
): TimeshiftPositionPresentation {
    val behindLiveMs = (liveEdgeMs - positionMs).coerceAtLeast(0L)
    return TimeshiftPositionPresentation(
        atLiveEdge = behindLiveMs <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS,
        behindLiveMs = behindLiveMs,
    )
}

fun timeshiftPositionPresentation(state: TimeshiftState): TimeshiftPositionPresentation =
    timeshiftPositionPresentation(
        positionMs = state.positionMs,
        liveEdgeMs = state.liveEdgeMs,
    )

fun canSeekTimeshiftBackward(state: TimeshiftState): Boolean =
    state.available && state.positionMs - state.bufferStartMs > 1_000L

fun canSeekTimeshiftForward(state: TimeshiftState): Boolean =
    state.available && !timeshiftPositionPresentation(state).atLiveEdge

fun isTimeshiftActive(state: TimeshiftState): Boolean =
    state.available && (
        state.paused ||
            !timeshiftPositionPresentation(state).atLiveEdge
        )

fun timeshiftStateFromStatus(
    advertisedPeriodSec: Int,
    shiftMicros: Long?,
    startMicros: Long?,
    endMicros: Long?,
    full: Boolean,
    speed: Int?,
    nowEpochMs: Long,
): TimeshiftState {
    if (advertisedPeriodSec <= 0 || shiftMicros == null) return TimeshiftState()
    val capacityMs = advertisedPeriodSec * 1_000L
    val positionMs = -(shiftMicros.absoluteValue / 1_000L).coerceAtMost(capacityMs)
    val reportedDurationMs = if (
        startMicros != null && endMicros != null && endMicros >= startMicros
    ) {
        ((endMicros - startMicros) / 1_000L).coerceAtMost(capacityMs)
    } else {
        null
    }
    val bufferDurationMs = when {
        reportedDurationMs != null -> reportedDurationMs
        full -> capacityMs
        else -> -positionMs
    }
    val bufferStartMs = -maxOf(bufferDurationMs.absoluteValue, positionMs.absoluteValue)
    return TimeshiftState(
        available = true,
        bufferStartMs = bufferStartMs,
        positionMs = positionMs.coerceIn(bufferStartMs, 0L),
        liveEdgeMs = 0,
        paused = speed == 0,
        serverStartUs = startMicros,
        serverEndUs = endMicros,
    )
}

fun timeshiftSeek(state: TimeshiftState, deltaMs: Long): TimeshiftSeekDecision {
    val target = (state.positionMs + deltaMs)
        .coerceIn(state.bufferStartMs, state.liveEdgeMs)
    return TimeshiftSeekDecision(
        targetMs = target,
        deltaMs = target - state.positionMs,
        clamped = target != state.positionMs + deltaMs,
    )
}

fun coalesceTimeshiftSeekDelta(
    state: TimeshiftState,
    pendingDeltaMs: Long,
    requestedDeltaMs: Long,
): Long = timeshiftSeek(state, pendingDeltaMs + requestedDeltaMs).deltaMs

fun queueTimeshiftSeek(
    queue: TimeshiftSeekQueueState,
    state: TimeshiftState,
    requestedDeltaMs: Long,
): TimeshiftSeekQueueState {
    val observedProjection = queue.projectedPositionMs?.takeUnless {
        queue.projectedFromPositionMs != null &&
            state.positionMs != queue.projectedFromPositionMs
    }
    val dispatchBaseMs = queue.inFlightTargetMs
        ?: observedProjection
        ?: state.positionMs
    val stackedBaseMs = queue.pendingTargetMs ?: dispatchBaseMs
    val requestedTargetMs = stackedBaseMs + requestedDeltaMs
    val targetMs = requestedTargetMs.coerceIn(state.bufferStartMs, state.liveEdgeMs)
    return queue.copy(
        pendingDeltaMs = targetMs - dispatchBaseMs,
        pendingTargetMs = targetMs,
        pendingClamped = queue.pendingClamped || targetMs != requestedTargetMs,
        projectedFromPositionMs = queue.projectedFromPositionMs
            .takeIf { observedProjection != null },
        projectedPositionMs = observedProjection,
    )
}

fun queuedTimeshiftSeekDecision(queue: TimeshiftSeekQueueState): TimeshiftSeekDecision {
    val targetMs = requireNotNull(queue.pendingTargetMs)
    return TimeshiftSeekDecision(
        targetMs = targetMs,
        deltaMs = queue.pendingDeltaMs,
        clamped = queue.pendingClamped,
    )
}

fun beginTimeshiftSeekDispatch(queue: TimeshiftSeekQueueState): TimeshiftSeekDispatch? {
    if (queue.dispatchInFlight || queue.pendingDeltaMs == 0L) return null
    val targetMs = requireNotNull(queue.pendingTargetMs)
    return TimeshiftSeekDispatch(
        queue = queue.copy(
            pendingDeltaMs = 0L,
            pendingTargetMs = null,
            pendingClamped = false,
            dispatchInFlight = true,
            inFlightBaseMs = targetMs - queue.pendingDeltaMs,
            inFlightTargetMs = targetMs,
            projectedFromPositionMs = null,
            projectedPositionMs = null,
        ),
        deltaMs = queue.pendingDeltaMs,
    )
}

fun completeTimeshiftSeekDispatch(
    queue: TimeshiftSeekQueueState,
    decision: TimeshiftSeekDecision?,
): TimeshiftSeekQueueState {
    if (decision == null) return TimeshiftSeekQueueState()
    return queue.copy(
        pendingDeltaMs = queue.pendingTargetMs?.minus(decision.targetMs) ?: 0L,
        dispatchInFlight = false,
        inFlightBaseMs = null,
        inFlightTargetMs = null,
        projectedFromPositionMs = queue.inFlightBaseMs,
        projectedPositionMs = decision.targetMs,
    )
}

fun cancelPendingTimeshiftSeek(queue: TimeshiftSeekQueueState): TimeshiftSeekQueueState =
    queue.copy(
        pendingDeltaMs = 0L,
        pendingTargetMs = null,
        pendingClamped = false,
    )

fun timeshiftAbsoluteTargetUs(
    state: TimeshiftState,
    decision: TimeshiftSeekDecision,
): Long? = state.serverEndUs?.plus(decision.targetMs * 1_000L)
