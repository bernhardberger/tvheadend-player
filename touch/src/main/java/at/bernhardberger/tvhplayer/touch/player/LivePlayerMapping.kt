@file:Suppress("LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.OptIn
import androidx.compose.runtime.State
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftWallClockMapping
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.PlaybackStatusPresentation
import at.bernhardberger.tvhplayer.core.ProgrammePositionWindow
import at.bernhardberger.tvhplayer.core.committedProgrammeEvent
import at.bernhardberger.tvhplayer.core.nextProgrammeEvent
import at.bernhardberger.tvhplayer.core.TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
import at.bernhardberger.tvhplayer.core.liveChannelCondition
import at.bernhardberger.tvhplayer.core.liveInterrupted
import at.bernhardberger.tvhplayer.core.playbackStatusPresentation
import at.bernhardberger.tvhplayer.core.programmePositionWindow
import at.bernhardberger.tvhplayer.core.timeshiftPositionPresentation
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import at.bernhardberger.tvhplayer.touch.channels.ChannelList
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.channelRow
import at.bernhardberger.tvhplayer.touch.channels.channelsState
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.ZoneId
import kotlin.time.Instant

/**
 * The face's status for [shownId], read as the TV player reads it: the shared channel condition
 * and status presentation, and the keep limit's notice for Pause ended. [issue] and
 * [serverStopped] come from the active live observation; [requestFailed] is a start this screen
 * asked for that did not start. A recovery without a connection problem behind it (the channel
 * failed for another reason) is Unavailable with its condition.
 */
@OptIn(UnstableApi::class)
internal fun liveStatus(
    connected: Boolean,
    state: AppPlaybackState,
    issue: SubscriptionIssue?,
    serverStopped: Boolean,
    playingId: ChannelId?,
    shownId: ChannelId,
    requestFailed: Boolean,
    notice: BackgroundPlaybackNotice?,
): LivePlayerStatus {
    if (notice == BackgroundPlaybackNotice.LIMIT_EXPIRED) return LivePlayerStatus.PauseEnded
    val failed = state as? AppPlaybackState.Failed
    val condition = liveChannelCondition(
        issue = issue?.takeIf { playingId == shownId } ?: failed?.subscriptionIssue,
        playbackFailed = requestFailed || failed != null,
        liveInterrupted = liveInterrupted(serverStopped, playingId, shownId),
        deviceFault = failed?.deviceFault == true,
    )
    val recovering = state is AppPlaybackState.Recovering
    val presentation = playbackStatusPresentation(
        connectionAvailable = connected,
        playbackStarting = state is AppPlaybackState.Starting,
        playbackRecovering = recovering,
        playbackPlaying = state.presented,
        channelCondition = condition,
    )
    return when (presentation) {
        PlaybackStatusPresentation.NONE ->
            if (state.presented && playingId == shownId) LivePlayerStatus.Playing else LivePlayerStatus.Tuning
        PlaybackStatusPresentation.COMPACT_TUNING -> LivePlayerStatus.Tuning
        PlaybackStatusPresentation.CHANNEL_UNAVAILABLE -> LivePlayerStatus.Unavailable(condition)
        PlaybackStatusPresentation.FULL_RECOVERY -> when {
            !connected || recovering -> LivePlayerStatus.Reconnecting(null)
            else -> LivePlayerStatus.Unavailable(condition)
        }
    }
}

/**
 * The bar for one timeshift sample, by the TV's programme window (app ui/player/ProgrammeWindow.kt
 * `programmeWindow`): the axis is the program the viewer is in, the buffer starts at the history's
 * start (or the program's start, whichever is later), the live tick sits at the history's end when
 * that falls inside the program, and the viewer within the live-edge tolerance of it sits on it.
 * Without a window (no timeshift on this channel or server, no position yet) the bar is the
 * program on air with the viewer at [nowSec] and nothing seekable; with no program on air, none.
 */
internal fun liveTimeline(
    sample: AppTimeshiftState,
    nowSec: Long,
    zone: ZoneId,
    eventAt: (Instant) -> EpgEvent?,
): LiveTimeline? {
    val window = positionWindow(sample, eventAt)
        ?: return eventAt(Instant.fromEpochSeconds(nowSec))?.let { onAir(it, nowSec, zone) }
    val duration = (window.stop - window.start).inWholeSeconds.toInt()
    fun offset(time: Instant) = (time - window.start).inWholeSeconds.toInt().coerceIn(0, duration)
    val liveEdge = window.historyEnd in window.start..window.stop
    val live = offset(window.historyEnd)
    val bufferStart = offset(window.historyStart).coerceAtMost(live)
    val atEdge = liveEdge &&
        (window.historyEnd - window.estimatedPosition).inWholeMilliseconds <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
    val position = if (atEdge) live else offset(window.estimatedPosition).coerceIn(bufferStart, live)
    val presentation = timeshiftPositionPresentation(sample)
    val behind = if (presentation.atLiveEdge) 0 else (presentation.behindLiveMs / MS_PER_SECOND).toInt()
    return LiveTimeline(
        programStart = localTime(window.start.epochSeconds, zone), duration = duration,
        bufferStart = bufferStart, position = position, live = live, behind = behind, liveEdge = liveEdge,
    )
}

/** The programs the face names: the one shown and the one after it. */
internal data class ShownPrograms(val now: EpgEvent?, val next: EpgEvent?)

/**
 * The program the viewer is in and the one after it, by the TV info bar's rule (app
 * ui/player/ProgrammeWindow.kt `displayedProgrammeEvent` / `displayedNextEvent` over the shared
 * `committedProgrammeEvent` / `nextProgrammeEvent`): with a position window its program (none for
 * a slot without one) and the program after the viewer's position; without a window the program
 * on air while that describes playback ([liveStart], or no timeshift / at the live edge).
 */
internal fun shownPrograms(
    sample: AppTimeshiftState,
    liveStart: Boolean,
    now: Instant,
    eventAt: (Instant) -> EpgEvent?,
    nextAt: (Instant) -> EpgEvent?,
): ShownPrograms {
    val window = positionWindow(sample, eventAt)
    val onAir = eventAt(now)
    val shown = committedProgrammeEvent(window != null, window?.event, sample, onAir, liveStart)
    val next = nextProgrammeEvent(
        window?.estimatedPosition, window?.event, previewing = false, sample, nextAt(now), liveStart, onAir, nextAt,
    )
    return ShownPrograms(shown, next)
}

private fun positionWindow(sample: AppTimeshiftState, eventAt: (Instant) -> EpgEvent?): ProgrammePositionWindow? {
    val startMapping = sample.historyStartTimeline
        ?.takeIf { sample.timeline?.describesSameSegment(it) == true }
        ?.wallClockMapping as? TimeshiftWallClockMapping.Estimate
    return programmePositionWindow(sample, historyStartMapping = startMapping, eventAt = eventAt)
        ?.takeIf { it.stop > it.start }
}

private fun onAir(event: EpgEvent, nowSec: Long, zone: ZoneId): LiveTimeline? {
    val startSec = event.start.epochSeconds
    val duration = event.stop.epochSeconds - startSec
    if (duration <= 0 || nowSec !in startSec until event.stop.epochSeconds) return null
    val live = (nowSec - startSec).toInt()
    return LiveTimeline(localTime(startSec, zone), duration.toInt(), bufferStart = null, position = live, live = live)
}

private fun localTime(epochSec: Long, zone: ZoneId) =
    java.time.Instant.ofEpochSecond(epochSec).atZone(zone).toLocalTime()

internal const val MS_PER_SECOND = 1_000L

/**
 * The history snapshot that anchors the buffer's start for a segment, as the TV holds it
 * (app ui/player/PlayerTimelinePresentationState.kt): kept while the segment lasts, replaced by a
 * new segment's first snapshot that can place itself on the clock.
 */
internal fun heldHistoryStart(held: TimeshiftTimeline?, sample: AppTimeshiftState): TimeshiftTimeline? {
    val timeline = sample.timeline?.takeIf { sample.available } ?: return null
    return when {
        held?.describesSameSegment(timeline) == true -> held
        timeline.wallClockMapping is TimeshiftWallClockMapping.Estimate -> timeline
        else -> null
    }
}

/**
 * A skip by the TV's rule (PlayerTimelinePresentationState.kt `seekRelative`): nothing without
 * known timing, back at the buffer's start, or forward at the live edge. A forward skip that
 * reaches the live edge goes live, so the viewer lands on live rather than just short of it.
 */
internal fun skipMove(sample: AppTimeshiftState, deltaMs: Long): TimeshiftMove = when {
    !sample.timingKnown || sample.playbackTarget == null -> TimeshiftMove.None
    deltaMs < 0 && sample.positionMs <= sample.bufferStartMs -> TimeshiftMove.None
    deltaMs > 0 && sample.positionMs >= sample.liveEdgeMs -> TimeshiftMove.None
    deltaMs > 0 && sample.liveEdgeMs - (sample.positionMs + deltaMs) <= TIMESHIFT_LIVE_EDGE_TOLERANCE_MS ->
        TimeshiftMove.GoLive
    else -> TimeshiftMove.By(deltaMs)
}

/** A drag let go at [target] seconds: onto the live tick goes live, anywhere else seeks by the distance. */
internal fun dragMove(timeline: LiveTimeline, target: Int): TimeshiftMove = when {
    timeline.liveEdge && target >= timeline.live -> TimeshiftMove.GoLive
    target == timeline.position -> TimeshiftMove.None
    else -> TimeshiftMove.By((target - timeline.position) * MS_PER_SECOND)
}

/** Pause, the skips, the drag and Go live are offered while the channel can pause, as on TV. */
internal fun timeshiftOffered(availability: LivePauseAvailability, status: LivePlayerStatus): Boolean =
    status == LivePlayerStatus.Playing &&
        (availability == LivePauseAvailability.READY || availability == LivePauseAvailability.STARTING)

/**
 * The face for [shownId] without its clock and episode texts, which need resources (see
 * [withProgramTexts]). The channel list below the picture marks [shownId] as playing. A channel
 * the scope does not know yet keeps an empty name so Back and the picture are there at once.
 * Past the picture, Back and the channel list only the timeshift controls are wired, and only those
 * are offered.
 */
internal fun livePlayerState(
    shownId: ChannelId,
    scope: ChannelScopeState,
    observation: SessionObservation,
    connection: ConnectionUiState,
    degraded: Boolean,
    status: LivePlayerStatus,
    timeline: State<LiveTimeline?>,
    pause: LivePauseAvailability,
    now: Instant,
    window: Boolean = false,
): LivePlayerState {
    val id = shownId.value.toString()
    val channel = scope.scope.allChannels.firstOrNull { it.id == shownId }
    val list = when (val channels = channelsState(scope, connection, degraded, observation, now, playingId = id)) {
        is ChannelsState.Ready -> channels.list
        is ChannelsState.Offline -> channels.list
        else -> ChannelList(emptyList())
    }
    return LivePlayerState(
        channel = channel?.let { channelRow(it, observation, now) } ?: ChannelRow(id, ""),
        channels = list,
        frame = null,
        timeline = timeline,
        status = status,
        offered = buildSet {
            if (timeshiftOffered(pause, status)) add(PlayerControl.Timeshift)
            // Without the window (no feature, or switched off for the app) its button is absent.
            if (window) add(PlayerControl.PictureInPicture)
        },
    )
}
