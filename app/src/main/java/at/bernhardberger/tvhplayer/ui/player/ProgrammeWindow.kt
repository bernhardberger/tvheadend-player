package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

/** Display geometry only. No scheduled edge is a seek target. */
data class ProgrammeWindow(
    val event: EpgEvent,
    val estimatedPosition: Instant,
    val positionFraction: Float,
    val availableStartFraction: Float,
    val availableEndFraction: Float,
    val liveFraction: Float?,
    val targetAvailable: Boolean,
)

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
    val event = eventAt(position)?.takeIf { it.start <= position && position < it.stop } ?: return null
    val start = history.select(history.start)?.let(mapping::estimate) ?: return null
    val end = history.select(history.end)?.let(mapping::estimate) ?: return null
    val span = (event.stop - event.start).inWholeMilliseconds.toDouble()
    if (span <= 0.0) return null
    fun fraction(time: Instant) = ((time - event.start).inWholeMilliseconds / span).toFloat().coerceIn(0f, 1f)
    val targetAvailable = target.position in history.start..history.end ||
        (state.timingKnown && target === state.playbackTarget && target.position >= history.start)
    return ProgrammeWindow(event, position, fraction(position), fraction(start), fraction(end),
        fraction(end).takeIf { end >= event.start && end <= event.stop }, targetAvailable)
}

internal fun programmeWindowClockLabels(event: EpgEvent, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
    val start = java.time.Instant.ofEpochSecond(event.start.epochSeconds).atZone(zone)
    val end = java.time.Instant.ofEpochSecond(event.stop.epochSeconds).atZone(zone)
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return start.format(formatter) to end.format(formatter)
}
