package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.LivePauseState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import java.time.ZoneId
import kotlin.time.Instant

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
 * Wall-clock Now/Next reflects the live broadcast, so while playback is behind
 * live the header must not claim that the current broadcast describes the
 * historical content on screen. That only holds while playback is actually
 * behind the live edge (or its position is unknown), not merely because a
 * timeshift buffer exists: on TVHeadend a live subscription essentially always
 * has one.
 */
fun programmeTimingDescribesPlayback(state: AppTimeshiftState): Boolean =
    !state.available || timeshiftPositionPresentation(state).atLiveEdge

/** A tune describes Now until a pause/command; otherwise require committed live-edge timing. */
fun nowDescribesPlayback(state: AppTimeshiftState, liveStart: Boolean): Boolean =
    liveStart || (!state.available || state.timingKnown) &&
        state.playbackTarget == null && programmeTimingDescribesPlayback(state)

/** Pending channel intent takes precedence over the installed player's transient local state. */
fun livePlayWhenReady(state: LivePauseState, playerPlayWhenReady: Boolean): Boolean =
    if (state.selectionPending) !state.pending
    else playerPlayWhenReady && !state.pending && !state.pausedSeekRefresh

/** A resolved slot has no programme: it must not borrow the current broadcast. */
fun committedProgrammeEvent(
    hasWindow: Boolean,
    windowEvent: EpgEvent?,
    state: AppTimeshiftState,
    currentBroadcast: EpgEvent?,
    liveStart: Boolean = false,
): EpgEvent? = if (hasWindow) windowEvent
    else currentBroadcast.takeIf { nowDescribesPlayback(state, liveStart) }

/** Shared by the info bar and session metadata; a slot never borrows broadcast Next. */
fun nextProgrammeEvent(
    windowPosition: Instant?,
    windowEvent: EpgEvent?,
    previewing: Boolean,
    state: AppTimeshiftState,
    nextAtNow: EpgEvent?,
    liveStart: Boolean,
    currentProgramme: EpgEvent?,
    nextAt: (Instant) -> EpgEvent?,
): EpgEvent? = if (windowPosition != null) {
    windowEvent?.let { nextAt(windowPosition) }
} else nextAtNow.takeIf { currentProgramme != null && !previewing && nowDescribesPlayback(state, liveStart) }

/** Wall-clock identity and bounds only; display fractions and labels belong to the UI. */
data class ProgrammePositionWindow(
    val event: EpgEvent?,
    val estimatedPosition: Instant,
    val start: Instant,
    val stop: Instant,
    val historyStart: Instant,
    val historyEnd: Instant,
)

/** Length of the placeholder axis without a programme; a slot is never programme information. */
private const val PROGRAMME_SLOT_SECONDS = 30L * 60L

/** The half-hour containing [at], aligned to the local clock even with fractional-hour offsets. */
fun halfHourSlot(at: Instant, zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
    val offset = zone.rules.getOffset(java.time.Instant.ofEpochSecond(at.epochSeconds)).totalSeconds
    val start = Math.floorDiv(at.epochSeconds + offset, PROGRAMME_SLOT_SECONDS) * PROGRAMME_SLOT_SECONDS - offset
    return Instant.fromEpochSeconds(start) to Instant.fromEpochSeconds(start + PROGRAMME_SLOT_SECONDS)
}

fun programmePositionWindow(
    state: AppTimeshiftState,
    target: TimeshiftContentTarget? = state.playbackTarget.takeIf { state.timingKnown },
    mappingTimeline: TimeshiftTimeline? = state.timeline,
    historyStartMapping: TimeshiftWallClockMapping.Estimate? = null,
    eventAt: (Instant) -> EpgEvent?,
): ProgrammePositionWindow? {
    // An explicit selection remains displayable while the seek has no committed position.
    if (!state.available || target == null) return null
    val history = state.timeline ?: return null
    if (mappingTimeline?.describesSameSegment(history) != true) return null
    val mapping = mappingTimeline.wallClockMapping as? TimeshiftWallClockMapping.Estimate ?: return null
    val position = mapping.estimate(target) ?: return null
    val event = eventAt(position)?.takeIf { it.start <= position && position < it.stop }
    val (start, stop) = event?.let { it.start to it.stop } ?: halfHourSlot(position)
    val historyStart = history.select(history.start)?.let((historyStartMapping ?: mapping)::estimate) ?: return null
    val historyEnd = history.select(history.end)?.let(mapping::estimate) ?: return null
    if ((stop - start).inWholeMilliseconds <= 0L) return null
    return ProgrammePositionWindow(event, position, start, stop, historyStart, historyEnd)
}

/**
 * The state as it would present at a seek target the server has not served yet.
 * The current server shift does not describe that target, so it is measured
 * against the history instead.
 */
fun projectedTimeshiftState(state: AppTimeshiftState, targetMs: Long): AppTimeshiftState =
    state.copy(positionMs = targetMs, serverBehindLiveMs = null)

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
