package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision

const val TIMESHIFT_SEEK_STEP_MS = 30_000L
const val TIMESHIFT_LIVE_EDGE_TOLERANCE_MS = 5_000L

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

/**
 * Where playback stands relative to live, as the viewer understands it.
 *
 * The server reader shift is the timeshift position TVHeadend actually serves,
 * so it decides whether playback is live. The measured distance between the
 * rendered position and the history edge also contains delivery and decode
 * latency, which is present directly after every tune and is not timeshift; it
 * is only used when the server has not reported a shift.
 */
fun timeshiftPositionPresentation(state: AppTimeshiftState): TimeshiftPositionPresentation {
    val serverBehindLiveMs = state.serverBehindLiveMs
    val position = if (serverBehindLiveMs != null) {
        TimeshiftPositionPresentation(
            atLiveEdge = serverBehindLiveMs <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS,
            behindLiveMs = serverBehindLiveMs,
        )
    } else {
        timeshiftPositionPresentation(
            positionMs = state.positionMs,
            liveEdgeMs = state.liveEdgeMs,
        )
    }
    return if (state.timingKnown) position else position.copy(atLiveEdge = false)
}

/**
 * Whether wall-clock Now/Next describes what the viewer is watching.
 *
 * The SDK provides no programme wall-clock mapping for timeshifted playback, so
 * the header must not claim that the current broadcast describes historical
 * content. That only holds while playback is actually behind the live edge (or
 * its position is unknown), not merely because a timeshift buffer exists: on
 * TVHeadend a live subscription essentially always has one.
 */
fun programmeTimingDescribesPlayback(state: AppTimeshiftState): Boolean =
    !state.available || timeshiftPositionPresentation(state).atLiveEdge

/**
 * The state as it would present at a seek target the server has not served yet.
 * The current server shift does not describe that target, so it is measured
 * against the history instead.
 */
fun projectedTimeshiftState(state: AppTimeshiftState, targetMs: Long): AppTimeshiftState =
    state.copy(positionMs = targetMs, serverBehindLiveMs = null)

fun canSeekTimeshiftBackward(state: AppTimeshiftState): Boolean =
    state.available && state.timingKnown && state.positionMs - state.bufferStartMs > 1_000L

fun canSeekTimeshiftForward(state: AppTimeshiftState): Boolean =
    state.available && state.timingKnown && !timeshiftPositionPresentation(state).atLiveEdge

fun queueTimeshiftSeek(
    queue: TimeshiftSeekQueueState,
    state: AppTimeshiftState,
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
    accepted: Boolean,
): TimeshiftSeekQueueState {
    if (!accepted) return TimeshiftSeekQueueState()
    val targetMs = requireNotNull(queue.inFlightTargetMs)
    return queue.copy(
        pendingDeltaMs = queue.pendingTargetMs?.minus(targetMs) ?: 0L,
        dispatchInFlight = false,
        inFlightBaseMs = null,
        inFlightTargetMs = null,
        projectedFromPositionMs = queue.inFlightBaseMs,
        projectedPositionMs = targetMs,
    )
}

fun cancelPendingTimeshiftSeek(queue: TimeshiftSeekQueueState): TimeshiftSeekQueueState =
    queue.copy(
        pendingDeltaMs = 0L,
        pendingTargetMs = null,
        pendingClamped = false,
    )
