package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.channels.ChannelList
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import java.time.LocalTime

internal data class LivePlayerState(
    val channel: ChannelRow,
    val channels: ChannelList,
    val frame: Painter?,
    val timeline: LiveTimeline,
    val status: LivePlayerStatus = LivePlayerStatus.Playing,
    val paused: Boolean = false,
    val brightness: Float = 0.62f,
    val tracks: PlayerTracks = PlayerTracks(),
    val options: PlaybackOptions = PlaybackOptions(),
)

/** A stream's audio or subtitle track, named by its language or role with layout and format below. */
internal data class PlayerTrack(
    val language: String,
    val format: String,
    val layout: String? = null,
    val audioDescription: Boolean = false,
)

/** [Default] is Automatic for audio and `Default (<the Subtitles setting>)` for subtitles. */
internal sealed interface TrackPick {
    data object Default : TrackPick
    data object Off : TrackPick
    data class Track(val index: Int) : TrackPick
}

/** [playingAudio] is the track Automatic plays; [subtitlesSetting] null means the setting is Off. */
internal data class PlayerTracks(
    val audio: List<PlayerTrack> = emptyList(),
    val audioPick: TrackPick = TrackPick.Default,
    val playingAudio: Int? = null,
    val subtitles: List<PlayerTrack> = emptyList(),
    val subtitlePick: TrackPick = TrackPick.Default,
    val subtitlesSetting: String? = null,
)

internal enum class DisplayMode { Auto, Fill16By9, Fill4By3 }

internal enum class StatsLevel { Off, StreamPath, Debug }

/** A server stream profile; the direct one is shown as Direct streaming with its server name below. */
internal data class StreamProfile(val name: String, val direct: Boolean = false)

/** [profilePick] null is `Default (<the connection's profile>)`, the profile at [defaultProfile]. */
internal data class PlaybackOptions(
    val displayMode: DisplayMode = DisplayMode.Auto,
    val profiles: List<StreamProfile> = emptyList(),
    val defaultProfile: Int = 0,
    val profilePick: Int? = null,
    val stats: StatsLevel = StatsLevel.Off,
)

/** A choice made in one of the player's menus or sheets. */
internal sealed interface PlayerChoice {
    data class Audio(val pick: TrackPick) : PlayerChoice
    data class Subtitles(val pick: TrackPick) : PlayerChoice
    data class Profile(val index: Int?) : PlayerChoice
    data class Display(val mode: DisplayMode) : PlayerChoice
    data class Stats(val level: StatsLevel) : PlayerChoice
    data class ChannelTag(val id: String?) : PlayerChoice
}

/** What the player shows over its face: a menu at a button, or a sheet and the page it is on. */
internal enum class PlayerPanel(val menu: Boolean) {
    Record(true), Audio(true), Subtitles(true), Channels(false),
    Options(false), DisplayMode(false), Profile(false), Stats(false),
    ;

    val optionsPage: Boolean get() = this == Options || this == DisplayMode || this == Profile || this == Stats
}

/** Offsets are seconds from [programStart]; [live] is the end of the seekable buffer. */
internal data class LiveTimeline(
    val programStart: LocalTime,
    val duration: Int,
    val bufferStart: Int?,
    val position: Int,
    val live: Int,
) {
    init {
        require(duration > 0 && position in 0..live && live <= duration)
        require(bufferStart == null || bufferStart in 0..position)
    }
}

internal sealed interface LivePlayerStatus {
    data object Playing : LivePlayerStatus
    data object Tuning : LivePlayerStatus
    data class Reconnecting(val nextAttemptSeconds: Int?) : LivePlayerStatus
    data object PauseEnded : LivePlayerStatus
    data object Unavailable : LivePlayerStatus
}

internal enum class PlayerAction {
    Back, PictureInPicture, FullScreen, BackTen, TogglePlay, ForwardThirty,
    GoLive, RecordProgram, RecordSeries, RecordAll, Info, WatchLive,
}

internal enum class PlayerLayout(val upright: Boolean, val trackThickness: Dp, val skipIconSize: Dp) {
    Tablet(false, 10.dp, 36.dp),
    PhoneLandscape(false, 8.dp, 30.dp),
    Upright(true, 6.dp, 32.dp),
    Phone(true, 6.dp, 26.dp),
}
