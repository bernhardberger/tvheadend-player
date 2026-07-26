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

fun canSeekTimeshiftBackward(state: TimeshiftState): Boolean =
    state.available && state.positionMs - state.bufferStartMs > 1_000L

fun canSeekTimeshiftForward(state: TimeshiftState): Boolean =
    state.available &&
        state.liveEdgeMs - state.positionMs > TIMESHIFT_LIVE_EDGE_TOLERANCE_MS

fun isTimeshiftActive(state: TimeshiftState): Boolean =
    state.available && (
        state.paused ||
            state.liveEdgeMs - state.positionMs > TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
        )

fun shouldShowProgrammeTimeline(
    state: TimeshiftState,
    hasCurrentProgramme: Boolean,
): Boolean = hasCurrentProgramme && !isTimeshiftActive(state)

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

fun timeshiftAbsoluteTargetUs(
    state: TimeshiftState,
    decision: TimeshiftSeekDecision,
): Long? = state.serverEndUs?.plus(decision.targetMs * 1_000L)
