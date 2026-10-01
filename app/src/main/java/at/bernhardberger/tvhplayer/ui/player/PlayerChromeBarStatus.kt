package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.core.PlayerBarEnd
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.bufferStartClockSec
import at.bernhardberger.tvhplayer.core.liveBarEnd
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.core.TimeshiftPositionPresentation
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.common.formatClock
import kotlin.time.Instant

/**
 * How far behind live playback stands, for the bar status and the hidden chip: zero at the live edge,
 * without timeshift, while tuning and on a [liveStart] before its timing is known (both land at the
 * live edge); otherwise null before the timing is known.
 */
internal fun liveBehindMs(timeshift: AppTimeshiftState, tuning: Boolean = false, liveStart: Boolean = false): Long? = when {
    tuning -> 0L
    !timeshift.available -> 0L
    timeshift.timingKnown -> timeshiftPositionPresentation(timeshift).let { if (it.atLiveEdge) 0L else it.behindLiveMs }
    liveStart -> 0L
    else -> null
}

/** Live status and the bar agree despite decoder latency; pause and steps retain their coordinates. */
internal fun liveTimelineProgress(
    sampledProgress: Float,
    liveProgress: Float,
    position: TimeshiftPositionPresentation?,
    paused: Boolean,
    previewing: Boolean,
): Float = if (!paused && !previewing && position?.atLiveEdge == true) liveProgress else sampledProgress

internal fun liveTimelineProgress(
    range: SeekbarRange,
    position: TimeshiftPositionPresentation?,
    paused: Boolean,
    previewing: Boolean,
): Float {
    val sampled = if (range.displayEndMs <= range.displayStartMs) 0f else
        ((range.positionMs - range.displayStartMs).toDouble() / (range.displayEndMs - range.displayStartMs))
            .toFloat().coerceIn(0f, 1f)
    return liveTimelineProgress(sampled, 1f, position, paused, previewing)
}

/**
 * Now describes playback: the channel is tuning, or plays a [PlayerChromeTimeline.Live.liveStart]
 * (after a zap, with or without known timing), or [nowDescribesPlayback] accepts its committed state.
 * A pause or step away from live keeps its own axis.
 */
private val PlayerChromeTimeline.Live.describesPlaybackNow: Boolean
    get() = tuning || nowDescribesPlayback(committedTimeshift, liveStart)

/**
 * The broadcast airing now on the shown channel while now describes playback, from the moment the
 * channel is selected: the new channel's, never the previous one's. Null outside its airtime. The
 * info bar's programme follows the same [nowDescribesPlayback] rule ([displayedProgrammeEvent]).
 */
internal fun PlayerChromeTimeline.Live.airingEvent(): EpgEvent? = programme?.takeIf {
    liveAvailable && describesPlaybackNow && it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds
}

/** How far the airing programme has come at [nowSec], 0 to 1. */
internal fun EpgEvent.airingFraction(nowSec: Long): Float =
    ((nowSec - start.epochSeconds).toDouble() / (stop.epochSeconds - start.epochSeconds)).toFloat()

/**
 * The bar's axis at now while it has no programme window of its own: the airing [event], or without
 * one the half-hour slot around now ([event] null, never programme information). [startSec] and
 * [stopSec] are its clock bounds, [fraction] where now lies on it.
 */
data class ScheduleProgress(val event: EpgEvent?, val startSec: Long, val stopSec: Long, val fraction: Float) {
    val startLabel: String get() = formatClock(startSec)
    val endLabel: String get() = formatClock(stopSec)
    /** Its identity: a change is another axis, so the fill jumps to it instead of sliding. */
    val axisKey: Any get() = Triple(event?.id, startSec, stopSec)
}

/**
 * The schedule presentation of the bar while playback has no committed programme window: from the
 * channel's selection through tuning, unknown timing and known timing until the window resolves.
 * Showing the buffer axis or nothing for those moments empties the fill and brings it back. Without
 * an airing programme it is the half-hour slot at now, while timeshift is available or not yet
 * decided ([PlayerChromeTimeline.Live.timeshiftExpected]); without timeshift and programme, none. Null
 * once the window resolves, while a step previews (its target has its own window), and while now does
 * not describe playback, such as paused or stepped behind live.
 */
internal fun PlayerChromeTimeline.Live.scheduleProgress(): ScheduleProgress? {
    if (!liveAvailable || previewing || !describesPlaybackNow) return null
    // The window takes over where the bar shows the committed position on it.
    if (committedWindow != null && committedTimeshift.available && committedTimeshift.timingKnown) return null
    val airing = airingEvent()
    val (start, stop) = airing?.let { it.start.epochSeconds to it.stop.epochSeconds }
        ?: halfHourSlot(Instant.fromEpochSeconds(nowSec)).takeIf { timeshiftExpected }
            ?.let { (start, stop) -> start.epochSeconds to stop.epochSeconds }
        ?: return null
    return ScheduleProgress(airing, start, stop, ((nowSec - start).toDouble() / (stop - start)).toFloat())
}

/** The clock time the timeshift buffer of [state] starts at, when its live edge is [nowSec]. */
internal fun timeshiftStartClock(state: AppTimeshiftState, nowSec: Long): String =
    formatClock(bufferStartClockSec(nowSec, state.liveEdgeMs, timeshiftSeekbarRange(state).displayStartMs))

/**
 * Live TV's end and distance: the end clock of the axis the bar shows, the programme or slot
 * [window] (the committed one at rest, the target's while a step previews) or the schedule's while
 * there is no window; the distance how far the committed playback is behind live.
 */
internal fun PlayerChromeTimeline.Live.barEnd(window: ProgrammeWindow? = committedWindow): PlayerBarEnd? {
    if (!liveAvailable) return null
    val unknown = timeshiftExpected && !committedTimeshift.timingKnown && !tuning && !liveStart
    val behind = if (unknown) null else liveBehindMs(committedTimeshift, tuning, liveStart)
    val stop = window?.stop?.epochSeconds ?: scheduleProgress()?.stopSec
    return liveBarEnd(behind, stop?.let(::formatClock))
}

/** Null when the channel does not play (failed tune): no state cell, end or distance to claim Live or Playing. */
@Composable
internal fun PlayerChromeTimeline.Live.barStatus(
    state: PlayerStateCell,
    window: ProgrammeWindow? = committedWindow,
): PlayerBarStatus? = rememberPlayerBarStatus(state, barEnd(window)).takeIf { liveAvailable }

/** Rendered coordinates only: holding these never grants seeking or supplies programme metadata. */
internal data class LiveBarPresentation(
    val progress: Float?,
    val startLabel: String?,
    val end: PlayerBarEnd?,
    val window: ProgrammeWindow? = null,
    val availableStart: Float? = null,
    val liveEdge: Float? = null,
    val motionKey: Any? = null,
    val showTrack: Boolean = true,
)

/** A timing gap holds the whole row and its distance across Banner/controls changes. */
internal fun PlayerChromeTimeline.Live.barPresentation(previous: LiveBarPresentation?): LiveBarPresentation {
    val expectsTiming = timeshiftExpected || committedTimeshift.available
    val unknown = liveAvailable && expectsTiming && !committedTimeshift.timingKnown && !tuning && !liveStart
    if (unknown && previous != null) return previous
    val schedule = scheduleProgress()
    val range = committedTimeshift.takeIf { liveAvailable && it.available && it.timingKnown && schedule == null }
        ?.let(::timeshiftSeekbarRange)
    val position = range?.let { timeshiftPositionPresentation(committedTimeshift) }
    val window = committedWindow.takeIf { range != null }?.let {
        it.copy(positionFraction = liveTimelineProgress(it.positionFraction, it.liveFraction ?: it.positionFraction,
            position, committedTimeshift.paused, false))
    }
    val end = barEnd()?.let { it.copy(end = window?.let(::programmeWindowClockLabels)?.second ?: schedule?.endLabel ?: it.end) }
    return LiveBarPresentation(
        progress = schedule?.fraction ?: range?.let { liveTimelineProgress(it, position, committedTimeshift.paused, false) },
        startLabel = schedule?.startLabel ?: window?.let(::programmeWindowClockLabels)?.first
            ?: range?.let { timeshiftStartClock(committedTimeshift, nowSec) },
        end = end,
        window = window,
        availableStart = range?.availableStartFraction,
        liveEdge = range?.let { 1f },
        motionKey = motionKey to schedule?.axisKey,
        showTrack = schedule != null || range != null || expectsTiming || !liveAvailable,
    )
}

@Composable
internal fun rememberLiveBarPresentation(timeline: PlayerChromeTimeline.Live?): LiveBarPresentation? {
    var previous by remember(timeline?.motionKey) { mutableStateOf<LiveBarPresentation?>(null) }
    val current = timeline?.barPresentation(previous)
    SideEffect { previous = current?.takeIf { timeline.liveAvailable && it.showTrack } }
    return current
}

/**
 * The state the chip over hidden chrome shows: the real [state] only while the chrome is [hidden] and
 * playback is [available]; otherwise playing, which shows no chip.
 */
internal fun hiddenChipState(state: PlayerStateCell, hidden: Boolean, available: Boolean): PlayerStateCell =
    state.takeIf { hidden && available } ?: PlayerStateCell.PLAYING
