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
)

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
    Back, Settings, PictureInPicture, FullScreen, BackTen, TogglePlay, ForwardThirty,
    GoLive, Record, Audio, Subtitles, Info, Channels, ChannelGroups, WatchLive,
}

internal enum class PlayerLayout(val upright: Boolean, val trackThickness: Dp, val skipIconSize: Dp) {
    Tablet(false, 10.dp, 36.dp),
    PhoneLandscape(false, 8.dp, 30.dp),
    Upright(true, 6.dp, 32.dp),
    Phone(true, 6.dp, 26.dp),
}
