package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.painter.Painter
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.recordingMarkStops
import at.bernhardberger.tvhplayer.settings.AdBreakMode
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow

/** The properties used by the same picture, transport and menus in both faces. */
internal sealed interface PlayerFaceState {
    val key: String
    val channel: ChannelRow
    val frame: Painter?
    val status: LivePlayerStatus
    val paused: Boolean
    val brightness: Float
    val tracks: PlayerTracks
    val options: PlaybackOptions
    val offered: Set<PlayerControl>
    val seekable: Boolean
}

internal data class RecordingPlayerState(
    override val key: String,
    override val channel: ChannelRow,
    val title: String,
    val episode: String?,
    val summary: String?,
    val artwork: Painter?,
    /** Localized date of the recording, and a formatted wall clock for its projected finish. */
    val recordedOn: String,
    val endsAt: String?,
    override val frame: Painter?,
    val durationMs: Long,
    val positionMs: State<Long>,
    val marks: List<RecordingMark> = emptyList(),
    val adBreakSetting: AdBreakMode = AdBreakMode.ASK,
    val adBreakPick: AdBreakMode? = null,
    /** A runtime with entry history can withhold the bare-picture prompt after a deliberate seek. */
    val askOnEntry: Boolean = true,
    override val status: LivePlayerStatus = LivePlayerStatus.Playing,
    override val paused: Boolean = false,
    override val brightness: Float = 0.62f,
    override val tracks: PlayerTracks = PlayerTracks(),
    override val options: PlaybackOptions = PlaybackOptions(),
    override val offered: Set<PlayerControl> = setOf(
        PlayerControl.Timeshift, PlayerControl.Audio, PlayerControl.Subtitles, PlayerControl.Settings,
        PlayerControl.Info, PlayerControl.PictureInPicture, PlayerControl.FullScreen, PlayerControl.Markers,
    ),
) : PlayerFaceState {
    val stops = recordingMarkStops(marks, durationMs)
    val hasMarkers get() = PlayerControl.Markers in offered && stops.isNotEmpty()
    val adBreakMode get() = adBreakPick ?: adBreakSetting
    override val seekable get() = durationMs > 0
}
