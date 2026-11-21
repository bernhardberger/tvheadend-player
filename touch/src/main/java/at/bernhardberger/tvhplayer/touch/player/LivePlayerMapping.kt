@file:Suppress("LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.PlaybackStatusPresentation
import at.bernhardberger.tvhplayer.core.liveChannelCondition
import at.bernhardberger.tvhplayer.core.liveInterrupted
import at.bernhardberger.tvhplayer.core.playbackStatusPresentation
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import at.bernhardberger.tvhplayer.touch.channels.ChannelList
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.channels.channelRow
import at.bernhardberger.tvhplayer.touch.channels.channelsState
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState
import java.time.ZoneId
import kotlin.time.Instant

/** The live channel the runtime plays or is starting; a failed or finished target plays nothing. */
internal fun playingChannelId(target: AppPlaybackTarget?, state: AppPlaybackState): ChannelId? =
    (target as? AppPlaybackTarget.Live)?.channelId
        ?.takeUnless { state is AppPlaybackState.Failed || state is AppPlaybackState.Finished }

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
 * The running program's line with the viewer at the live edge: until timeshift is wired the
 * position is the on-air moment and nothing behind it is seekable. Null outside the program.
 */
internal fun liveTimeline(startSec: Long, stopSec: Long, nowSec: Long, zone: ZoneId): LiveTimeline? {
    val duration = stopSec - startSec
    if (duration <= 0 || nowSec !in startSec until stopSec) return null
    val live = (nowSec - startSec).toInt()
    val start = java.time.Instant.ofEpochSecond(startSec).atZone(zone).toLocalTime()
    return LiveTimeline(start, duration.toInt(), bufferStart = null, position = live, live = live)
}

/** The TV's rule: the screen stays on only while a visible picture plays without an error. */
internal fun keepPlayerScreenOn(
    foreground: Boolean,
    videoVisible: Boolean,
    playing: Boolean,
    hasVideo: Boolean,
    failed: Boolean,
): Boolean = foreground && videoVisible && playing && hasVideo && !failed

/**
 * The face for [shownId] without its clock and episode texts, which need resources (see
 * [withProgramTexts]). The channel list below the picture marks [shownId] as playing. A channel
 * the scope does not know yet keeps an empty name so Back and the picture are there at once.
 * Nothing past the picture, Back and the channel list is wired yet, so no other control is offered.
 */
internal fun livePlayerState(
    shownId: ChannelId,
    scope: ChannelScopeState,
    observation: SessionObservation,
    connection: ConnectionUiState,
    degraded: Boolean,
    status: LivePlayerStatus,
    now: Instant,
    zone: ZoneId,
): LivePlayerState {
    val id = shownId.value.toString()
    val channel = scope.scope.allChannels.firstOrNull { it.id == shownId }
    val list = when (val channels = channelsState(scope, connection, degraded, observation, now, playingId = id)) {
        is ChannelsState.Ready -> channels.list
        is ChannelsState.Offline -> channels.list
        else -> ChannelList(emptyList())
    }
    val event = observation.eventAt(shownId, now)
    return LivePlayerState(
        channel = channel?.let { channelRow(it, observation, now) } ?: ChannelRow(id, ""),
        channels = list,
        frame = null,
        timeline = event?.let { liveTimeline(it.start.epochSeconds, it.stop.epochSeconds, now.epochSeconds, zone) },
        status = status,
        offered = emptySet(),
    )
}
