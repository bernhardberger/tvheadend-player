package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.core.TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
import at.bernhardberger.tvhplayer.core.programmeTimingDescribesPlayback
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

/** Length of the placeholder axis without a programme (AOSP TimeShiftManager's placeholder programmes). */
internal const val PROGRAMME_SLOT_SECONDS = 30L * 60L

/**
 * The half-hour slot that contains [at], aligned to the local clock of [zone] (`:00` or `:30` also
 * where the offset is not whole hours): `[floor(at, 30 min), +30 min)`.
 */
internal fun halfHourSlot(at: Instant, zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
    val offset = zone.rules.getOffset(java.time.Instant.ofEpochSecond(at.epochSeconds)).totalSeconds
    val start = Math.floorDiv(at.epochSeconds + offset, PROGRAMME_SLOT_SECONDS) * PROGRAMME_SLOT_SECONDS - offset
    return Instant.fromEpochSeconds(start) to Instant.fromEpochSeconds(start + PROGRAMME_SLOT_SECONDS)
}

/** Timestamp lookup includes display-only history; command selection must use live metadata. */
internal fun currentProgrammeEvent(observation: SessionObservation, displayed: EpgEvent?): EpgEvent? =
    displayed?.takeIf { observation.event(it.id) == it }

/**
 * Now describes playback: a [liveStart] plays the live edge from its start (after a zap, with or
 * without known timing) until a pause or timeshift command. Otherwise this requires no playback
 * target and [programmeTimingDescribesPlayback]. Schedule and programme info share this rule.
 */
internal fun nowDescribesPlayback(state: AppTimeshiftState, liveStart: Boolean): Boolean =
    liveStart || (!state.available || state.timingKnown) &&
        state.playbackTarget == null && programmeTimingDescribesPlayback(state)

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
    if (committedWindow != null) committedWindow.event
    else currentBroadcast.takeIf { nowDescribesPlayback(committedState, liveStart) }
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
    // An explicit selection remains displayable while the seek has no committed position.
    if (!state.available || target == null) return null
    val history = state.timeline ?: return null
    if (mappingTimeline?.describesSameSegment(history) != true) return null
    val mapping = mappingTimeline.wallClockMapping as? TimeshiftWallClockMapping.Estimate ?: return null
    val position = mapping.estimate(target) ?: return null
    val event = eventAt(position)?.takeIf { it.start <= position && position < it.stop }
    val (axisStart, axisStop) = event?.let { it.start to it.stop } ?: halfHourSlot(position)
    // Keep the historical boundary anchored to one SDK estimate for the segment. Remapping
    // unchanged content through every new status estimate makes that boundary wobble.
    val startMapping = (state.historyStartTimeline
        ?.takeIf { it.describesSameSegment(history) }
        ?.wallClockMapping as? TimeshiftWallClockMapping.Estimate) ?: mapping
    val start = history.select(history.start)?.let(startMapping::estimate) ?: return null
    val end = history.select(history.end)?.let(mapping::estimate) ?: return null
    val span = (axisStop - axisStart).inWholeMilliseconds.toDouble()
    if (span <= 0.0) return null
    fun fraction(time: Instant) = ((time - axisStart).inWholeMilliseconds / span).toFloat().coerceIn(0f, 1f)
    val targetAvailable = target.position in history.start..history.end ||
        (state.timingKnown && target === state.playbackTarget && target.position >= history.start)
    // Different SDK estimate snapshots can straddle the oldest coordinate by a few pixels.
    // An in-history target must not appear before the stable history boundary. Keep genuinely
    // evicted targets outside, and retain the original estimate for programme identity/clocks.
    val liveFraction = fraction(end).takeIf { end >= axisStart && end <= axisStop }
    val positionFraction = when {
        // Playback within the live-edge tolerance sits on the edge; its estimate must not fall short of it.
        liveFraction != null && target.position >= history.start &&
            (end - position).inWholeMilliseconds <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS -> liveFraction
        target.position >= history.start -> maxOf(fraction(position), fraction(start))
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
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return start.format(formatter) to end.format(formatter)
}
