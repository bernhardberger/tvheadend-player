package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.HumanTrackLabel
import at.bernhardberger.tvhplayer.touch.channels.ChannelList
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.channels.ChannelsState
import at.bernhardberger.tvhplayer.touch.guide.GuideProgram
import at.bernhardberger.tvhplayer.touch.guide.GuideAvailability
import at.bernhardberger.tvhplayer.touch.guide.ProgramDetails
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import java.time.LocalDate
import java.time.LocalTime

internal data class LivePlayerState(
    val channel: ChannelRow,
    val channels: ChannelsState,
    val frame: Painter?,
    /** The bar's moving values, read only by what draws them so a sample redraws the bar, not the face. */
    val timeline: State<LiveTimeline?>,
    val status: LivePlayerStatus = LivePlayerStatus.Playing,
    val paused: Boolean = false,
    val brightness: Float = 0.62f,
    val tracks: PlayerTracks = PlayerTracks(),
    val options: PlaybackOptions = PlaybackOptions(),
    val schedule: ChannelSchedule? = null,
    val offered: Set<PlayerControl> = PlayerControl.entries.toSet(),
    val liveRecord: LiveRecordOffer? = null,
)

internal val ChannelsState.displayedList: ChannelList?
    get() = when (this) {
        is ChannelsState.Ready -> list
        is ChannelsState.Offline -> list
        else -> null
    }

/**
 * The face's controls that have something behind them; one not offered is left out, not dimmed.
 * [Timeshift] is pause, the skips, the drag and Go live; the timeline itself always shows the program.
 */
internal enum class PlayerControl {
    Timeshift, Record, Audio, Subtitles, Settings, Channels, Info, PictureInPicture, FullScreen,
}

/**
 * The watched channel's day for the Info sheet, as the Guide's By channel view shows it: [days] for the
 * day picker with [selectedDay] and its [date], and the [details] loaded so far by program id.
 */
internal data class ChannelSchedule(
    val programs: List<GuideProgram>,
    val days: List<String>,
    val selectedDay: Int = 0,
    val date: String,
    val details: (String) -> ProgramDetails? = { null },
    val availability: GuideAvailability = GuideAvailability.Current,
    val dayLoading: Boolean = false,
    val dayDates: List<LocalDate> = emptyList(),
) {
    /** The program marked as playing in the player, which Info opens on. */
    val watchingId: String? get() = programs.firstOrNull { it.watching }?.id
}

/**
 * The Info sheet's pages: [open] is the program opened in the day list, [details] the Details pages on
 * top of it, newest last (a program's Details, then an airing's). Back takes one page off (section 9).
 */
internal data class InfoStack(
    val open: String? = null,
    val details: List<String> = emptyList(),
    val followWatching: Boolean = false,
) {
    val shown: String? get() = details.lastOrNull()

    /** Details of [id] over the current page; from the day list it is the opened program. */
    fun push(id: String): InfoStack = copy(open = if (details.isEmpty()) id else open, details = details + id)

    fun back(): InfoStack = copy(details = details.dropLast(1))
}

/** [Default] is Automatic for audio and `Default (<the Subtitles setting>)` for subtitles. */
internal sealed interface TrackPick {
    data object Default : TrackPick
    data object Off : TrackPick
    data class Track(val index: Int) : TrackPick
}

/**
 * The stream's tracks in the shared label lines (name, overline, detail; summary for the track
 * Automatic plays). [playingAudio] is the track Automatic plays; a null pick marks no line;
 * [subtitlesSetting] null means the setting is Off.
 */
internal data class PlayerTracks(
    val audio: List<HumanTrackLabel> = emptyList(),
    val audioPick: TrackPick? = TrackPick.Default,
    val playingAudio: Int? = null,
    val subtitles: List<HumanTrackLabel> = emptyList(),
    val subtitlePick: TrackPick? = TrackPick.Default,
    val subtitlesSetting: String? = null,
)

internal enum class DisplayMode { Auto, Fill16By9, Fill4By3 }

internal enum class StatsLevel { Off, StreamPath, Debug }

/** A server stream profile; the direct one is shown as Direct streaming with its server name below. */
internal data class StreamProfile(val name: String, val direct: Boolean = false)

/**
 * [profilePick] null is `Default (<the connection's profile>)`, the profile at [defaultProfile]
 * (null: the server's default). No [profiles] leaves out the Stream profile row, null [stats] the
 * Stats row.
 */
internal data class PlaybackOptions(
    val displayMode: DisplayMode = DisplayMode.Auto,
    val profiles: List<StreamProfile> = emptyList(),
    val defaultProfile: Int? = 0,
    val profilePick: Int? = null,
    val stats: StatsLevel? = StatsLevel.Off,
)

/** A choice made in one of the player's menus or sheets. */
internal sealed interface PlayerChoice {
    data class Audio(val pick: TrackPick) : PlayerChoice
    data class Subtitles(val pick: TrackPick) : PlayerChoice
    data class Profile(val index: Int?) : PlayerChoice
    data class Display(val mode: DisplayMode) : PlayerChoice
    data class Stats(val level: StatsLevel) : PlayerChoice
    data class ChannelTag(val id: String?) : PlayerChoice
    data class Day(val index: Int) : PlayerChoice
    data class Record(val programId: String, val control: ProgramRecordControl) : PlayerChoice
    data class LiveRecord(val offer: LiveRecordOffer) : PlayerChoice

    /** A Details page opened in the Info sheet, a program's or another airing's: its details may need loading. */
    data class Details(val programId: String) : PlayerChoice
}

/** What the player shows over its face: a menu at a button, or a sheet and the page it is on. */
internal enum class PlayerPanel(val menu: Boolean) {
    Audio(true), Subtitles(true), Channels(false), Info(false), InfoDetails(false),
    Options(false), DisplayMode(false), Profile(false), Stats(false),
    ;

    /** The button that opened this panel: Info's details page still belongs to Info. */
    val button: PlayerPanel get() = if (this == InfoDetails) Info else this

    val optionsPage: Boolean get() = this == Options || this == DisplayMode || this == Profile || this == Stats
}

/**
 * Offsets are seconds from [programStart]; [live] is the end of the seekable buffer inside the bar,
 * which is the live edge itself when [liveEdge], else the program's end (the viewer watches an earlier
 * program). [behind] is how far the viewer is behind live, 0 at the live edge; it can exceed the bar.
 */
internal data class LiveTimeline(
    val programStart: LocalTime,
    val duration: Int,
    val bufferStart: Int?,
    val position: Int,
    val live: Int,
    val behind: Int = live - position,
    val liveEdge: Boolean = true,
) {
    init {
        require(duration > 0 && position in 0..live && live <= duration && behind >= 0)
        require(bufferStart == null || bufferStart in 0..position)
    }

    /** Pause, the skips and the drag have a buffer to move in. */
    val seekable: Boolean get() = bufferStart != null && bufferStart < live

    /** How far behind live the viewer would be at [shown], a dragged position or the current one. */
    fun behindAt(shown: Int): Int = (behind + position - shown).coerceAtLeast(0)
}

/** What a skip or a drag asks of the runtime. */
internal sealed interface TimeshiftMove {
    data object None : TimeshiftMove
    data object GoLive : TimeshiftMove
    data class By(val deltaMs: Long) : TimeshiftMove
}

internal sealed interface LivePlayerStatus {
    data object Playing : LivePlayerStatus
    data object Tuning : LivePlayerStatus
    data class Reconnecting(val nextAttemptSeconds: Int?) : LivePlayerStatus
    data object PauseEnded : LivePlayerStatus

    /** The channel can't play; [condition] says why, null for the drawn busy-tuner case. */
    data class Unavailable(val condition: AppErrorCondition? = null) : LivePlayerStatus
}

internal enum class PlayerAction {
    Back, PictureInPicture, FullScreen, BackTen, TogglePlay, ForwardThirty,
    GoLive, WatchLive, RetryChannels,
}

internal enum class PlayerLayout(val upright: Boolean, val trackThickness: Dp, val skipIconSize: Dp) {
    Tablet(false, 10.dp, 36.dp),
    PhoneLandscape(false, 8.dp, 30.dp),
    Upright(true, 6.dp, 32.dp),
    Phone(true, 6.dp, 26.dp),
}
