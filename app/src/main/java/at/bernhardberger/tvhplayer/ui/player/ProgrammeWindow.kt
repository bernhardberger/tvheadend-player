package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.core.TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
import at.bernhardberger.tvhplayer.core.nowDescribesPlayback
import at.bernhardberger.tvhplayer.core.committedProgrammeEvent
import at.bernhardberger.tvhplayer.core.programmePositionWindow
import at.bernhardberger.tvhplayer.core.halfHourSlot
import java.time.ZoneId
import at.bernhardberger.tvhplayer.ui.common.formatClock
import kotlin.time.Instant

/**
 * Display geometry only. No scheduled edge is a seek target. The axis runs from [start] to [stop]:
 * the airtime of [event], or without a programme at the position ([event] null) the half-hour slot
 * around it ([halfHourSlot]). A slot is a timeline axis only, never programme information.
 */
data class ProgrammeWindow(
    val event: EpgEvent?,
    val estimatedPosition: Instant,
    val positionFraction: Float,
    val availableStartFraction: Float,
    val availableEndFraction: Float,
    val liveFraction: Float?,
    val targetAvailable: Boolean,
    val start: Instant = requireNotNull(event).start,
    val stop: Instant = requireNotNull(event).stop,
)

/** Timestamp lookup includes display-only history; command selection must use live metadata. */
internal fun currentProgrammeEvent(observation: SessionObservation, displayed: EpgEvent?): EpgEvent? =
    displayed?.takeIf { observation.event(it.id) == it }

/**
 * A preview with missing metadata must never borrow the committed/current broadcast title.
 *
 * Without a committed window the current broadcast describes playback while [nowDescribesPlayback]:
 * through a [liveStart], whose timing becomes known before its window resolves, as on the bar. A
 * committed window without a programme (a slot) describes none: the current broadcast does not
 * stand in for it.
 */
internal fun displayedProgrammeEvent(
    previewing: Boolean,
    displayedWindow: ProgrammeWindow?,
    committedWindow: ProgrammeWindow?,
    committedState: AppTimeshiftState,
    currentBroadcast: EpgEvent?,
    liveStart: Boolean = false,
): EpgEvent? = if (previewing) {
    displayedWindow?.event
} else {
    committedProgrammeEvent(committedWindow != null, committedWindow?.event, committedState, currentBroadcast, liveStart)
}

/**
 * The Next line's programme: after the [displayedWindow]'s programme at its position ([nextAt]), none
 * for a slot window, or without a window [nextAtNow] under the rule of [displayedProgrammeEvent].
 */
internal fun displayedNextEvent(
    previewing: Boolean,
    displayedWindow: ProgrammeWindow?,
    committedState: AppTimeshiftState,
    nextAtNow: EpgEvent?,
    liveStart: Boolean,
    currentProgramme: EpgEvent?,
    nextAt: (Instant) -> EpgEvent?,
): EpgEvent? = if (displayedWindow != null) {
    displayedWindow.event?.let { nextAt(displayedWindow.estimatedPosition) }
} else nextAtNow.takeIf { currentProgramme != null && !previewing && nowDescribesPlayback(committedState, liveStart) }

internal fun programmeWindow(
    state: AppTimeshiftState,
    target: TimeshiftContentTarget? = state.playbackTarget.takeIf { state.timingKnown },
    mappingTimeline: TimeshiftTimeline? = state.timeline,
    eventAt: (Instant) -> EpgEvent?,
): ProgrammeWindow? {
    // Keep the historical boundary anchored to one SDK estimate for the segment. Remapping
    // unchanged content through every new status estimate makes that boundary wobble.
    val startMapping = state.historyStartTimeline
        ?.takeIf { state.timeline?.describesSameSegment(it) == true }
        ?.wallClockMapping as? TimeshiftWallClockMapping.Estimate
    val resolved = programmePositionWindow(state, target, mappingTimeline, startMapping, eventAt) ?: return null
    val history = requireNotNull(state.timeline)
    val selected = requireNotNull(target)
    val (event, position, axisStart, axisStop, start, end) = resolved
    val span = (axisStop - axisStart).inWholeMilliseconds.toDouble()
    fun fraction(time: Instant) = ((time - axisStart).inWholeMilliseconds / span).toFloat().coerceIn(0f, 1f)
    val targetAvailable = selected.position in history.start..history.end ||
        (state.timingKnown && selected === state.playbackTarget && selected.position >= history.start)
    // Different SDK estimate snapshots can straddle the oldest coordinate by a few pixels.
    // An in-history target must not appear before the stable history boundary. Keep genuinely
    // evicted targets outside, and retain the original estimate for programme identity/clocks.
    val liveFraction = fraction(end).takeIf { end >= axisStart && end <= axisStop }
    val positionFraction = when {
        // Playback within the live-edge tolerance sits on the edge; its estimate must not fall short of it.
        liveFraction != null && selected.position >= history.start &&
            (end - position).inWholeMilliseconds <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS -> liveFraction
        selected.position >= history.start -> maxOf(fraction(position), fraction(start))
        else -> fraction(position)
    }
    return ProgrammeWindow(event, position, positionFraction, fraction(start), fraction(end), liveFraction, targetAvailable,
        axisStart, axisStop)
}

internal fun programmeWindowClockLabels(window: ProgrammeWindow, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> =
    programmeWindowClockLabels(window.start, window.stop, zone)

internal fun programmeWindowClockLabels(event: EpgEvent, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> =
    programmeWindowClockLabels(event.start, event.stop, zone)

private fun programmeWindowClockLabels(startAt: Instant, stopAt: Instant, zone: ZoneId): Pair<String, String> {
    val start = java.time.Instant.ofEpochSecond(startAt.epochSeconds).atZone(zone)
    val end = java.time.Instant.ofEpochSecond(stopAt.epochSeconds).atZone(zone)
    return formatClock(start) to formatClock(end)
}
