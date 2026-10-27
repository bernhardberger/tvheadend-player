package at.bernhardberger.tvhplayer.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes

/** What the rail's top section shows in place of the mark while something plays behind browse. */
@Immutable
internal data class RailNowPlaying(
    val target: AppPlaybackTarget,
    /** The live channel, or the channel a recording was made on when the server still knows it. */
    val channelId: ChannelId?,
    val channelNumber: Long?,
    val channelName: String,
    val picon: ArtworkId?,
    /** Set for a recording: its title stands where a live channel shows the programme. */
    val recordingTitle: String?,
    val reconnecting: Boolean,
)

/**
 * The playing target the rail can return to, or null for the mark: nothing plays, playback ended or
 * failed, or browse stops it. A recording counts only while it is still the warm one.
 */
internal fun railNowPlaying(
    target: AppPlaybackTarget?,
    state: AppPlaybackState,
    observation: SessionObservation,
    warmRecordingId: DvrEntryId?,
    stopWhileBrowsing: Boolean,
): RailNowPlaying? {
    if (target == null || stopWhileBrowsing) return null
    when (state) {
        AppPlaybackState.Idle, AppPlaybackState.Finished, is AppPlaybackState.Failed -> return null
        AppPlaybackState.Starting, AppPlaybackState.Playing, AppPlaybackState.Buffering,
        is AppPlaybackState.Recovering -> Unit
    }
    val reconnecting = state is AppPlaybackState.Recovering
    return when (target) {
        is AppPlaybackTarget.Live -> {
            val channel = observation.channel(target.channelId) ?: return null
            RailNowPlaying(target, target.channelId, channel.number, channel.name.orEmpty(), channel.icon,
                recordingTitle = null, reconnecting = reconnecting)
        }
        is AppPlaybackTarget.Recording -> {
            if (warmRecordingId != target.recordingId) return null
            val entry = observation.dvrEntry(target.recordingId) ?: return null
            val channel = entry.channelId?.let(observation::channel)
            RailNowPlaying(target, entry.channelId, channel?.number,
                channel?.name ?: entry.channelName.orEmpty(), channel?.icon,
                recordingTitle = entry.title.orEmpty(), reconnecting = reconnecting)
        }
    }
}

internal enum class RailPlaybackStatus { LIVE, BEHIND_LIVE, PAUSED, TUNING, RECONNECTING, RECORDED }

/** One state per slot; a reconnect or tune outranks pause, and pause outranks where the viewer is. */
internal fun railPlaybackStatus(
    nowPlaying: RailNowPlaying,
    indicator: ChannelPlaybackIndicator,
    behindLive: Boolean,
): RailPlaybackStatus = when {
    nowPlaying.reconnecting -> RailPlaybackStatus.RECONNECTING
    indicator == ChannelPlaybackIndicator.TUNING -> RailPlaybackStatus.TUNING
    indicator == ChannelPlaybackIndicator.PAUSED -> RailPlaybackStatus.PAUSED
    nowPlaying.target is AppPlaybackTarget.Recording -> RailPlaybackStatus.RECORDED
    behindLive -> RailPlaybackStatus.BEHIND_LIVE
    else -> RailPlaybackStatus.LIVE
}

/** Whole minutes the viewer is behind live, or null while the position is unlocated or within a minute. */
internal fun railBehindLiveMinutes(viewerEpochSec: Long?, nowSec: Long): Long? =
    viewerEpochSec?.let { (nowSec - it) / 60 }?.takeIf { it >= 1 }

/** The state as the player names it; behind live counts whole minutes once they are known. */
internal fun railStatusLabel(status: RailPlaybackStatus, behindLiveMinutes: Long?, resources: Resources): String =
    when (status) {
        RailPlaybackStatus.LIVE -> resources.getString(R.string.player_live)
        RailPlaybackStatus.BEHIND_LIVE -> behindLiveMinutes
            ?.let { resources.getString(R.string.timeshift_behind_live, formatDurationMinutes(it, resources)) }
            ?: resources.getString(R.string.player_behind)
        RailPlaybackStatus.PAUSED -> resources.getString(R.string.player_paused)
        RailPlaybackStatus.TUNING -> resources.getString(R.string.player_tuning)
        RailPlaybackStatus.RECONNECTING -> resources.getString(R.string.main_startup_message_reconnecting)
        RailPlaybackStatus.RECORDED -> resources.getString(R.string.details_recorded)
    }

/**
 * Line two after the playback mark: the state, then the channel when the title above it is a
 * programme or recording. The mark already says live and paused, so those name only the channel,
 * or the state when there is no channel to show.
 */
internal fun railStatusLine(
    status: RailPlaybackStatus,
    behindLiveMinutes: Long?,
    channel: String?,
    resources: Resources,
): String {
    val label = railStatusLabel(status, behindLiveMinutes, resources)
    return when {
        channel == null -> label
        status == RailPlaybackStatus.LIVE || status == RailPlaybackStatus.PAUSED -> channel
        else -> resources.getString(R.string.rail_now_playing_line, label, channel)
    }
}
