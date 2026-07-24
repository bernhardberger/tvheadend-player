package at.bernhardberger.tvhplayer.core

import kotlin.math.absoluteValue

const val TIMESHIFT_SEEK_STEP_MS = 30_000L
const val REQUESTED_TIMESHIFT_PERIOD_SEC = 7_200

data class TimeshiftState(
    val available: Boolean = false,
    val bufferStartMs: Long = 0,
    val positionMs: Long = 0,
    val liveEdgeMs: Long = 0,
    val paused: Boolean = false,
)

data class TimeshiftSeekDecision(
    val targetMs: Long,
    val deltaMs: Long,
    val clamped: Boolean,
)

fun timeshiftStateFromStatus(
    advertisedPeriodSec: Int,
    shiftMicros: Long?,
    startMicros: Long?,
    full: Boolean,
    speed: Int?,
    nowEpochMs: Long,
): TimeshiftState {
    if (advertisedPeriodSec <= 0 || shiftMicros == null) return TimeshiftState()
    val capacityMs = advertisedPeriodSec * 1_000L
    val positionMs = -(shiftMicros.absoluteValue / 1_000L).coerceAtMost(capacityMs)
    val startEpochMs = startMicros?.div(1_000L)
    val reportedStartRelativeMs = startEpochMs
        ?.takeIf { it in 1_000_000_000_000L..nowEpochMs }
        ?.minus(nowEpochMs)
    val bufferStartMs = when {
        full -> -capacityMs
        reportedStartRelativeMs != null -> reportedStartRelativeMs.coerceIn(-capacityMs, 0L)
        else -> -capacityMs
    }
    return TimeshiftState(
        available = true,
        bufferStartMs = bufferStartMs,
        positionMs = positionMs.coerceIn(bufferStartMs, 0L),
        liveEdgeMs = 0,
        paused = speed == 0,
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
