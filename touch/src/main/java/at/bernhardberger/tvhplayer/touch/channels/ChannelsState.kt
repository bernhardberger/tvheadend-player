package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.ui.graphics.Color
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.touch.ui.ProgramRecordControl
import at.bernhardberger.tvhplayer.touch.ui.RuleSchedule
import androidx.compose.ui.graphics.painter.Painter
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.core.AppError

internal sealed interface ChannelsState {
    data object Loading : ChannelsState
    data object Unreachable : ChannelsState
    data object SignInFailed : ChannelsState

    /** The saved password can't be read; the message opens the editor on Password. */
    data object SignInNeeded : ChannelsState

    /** No channels to show and a catalog failure to say why, in the message form. */
    data class Failed(val error: AppError) : ChannelsState

    data class Ready(val list: ChannelList) : ChannelsState
    data class Offline(val list: ChannelList, val retryIn: String? = null) : ChannelsState
}

internal data class ChannelList(
    val rows: List<ChannelRow>,
    val tags: List<ChannelTag> = emptyList(),
    val selectedTagId: String? = null,
    // Based on the full list, so filtering doesn't change the placement of channel names.
    val hasLogos: Boolean = rows.any { it.logo != null },
)

internal data class ChannelTag(val id: String, val label: String)

internal data class ChannelRow(
    val id: String,
    val name: String,
    val logo: Painter? = null,
    /** The logo's tile tint, supplied by the caller; null uses the neutral surface. */
    val logoTint: Color? = null,
    val now: ChannelProgram? = null,
    val next: ChannelProgram? = null,
    val playback: ChannelPlayback? = null,
    val recording: ChannelRecording? = null,
    val details: ChannelDetails? = null,
    /** The server image's model for the touch-level image composable; the fixture painter wins. */
    val logoModel: Any? = null,
)

/** The producer supplies the viewer's position, not the on-air position. */
internal data class ChannelPlayback(val progress: Float?)

/** A channel fallback mark is distinct from a linked program's running recording. */
internal enum class ChannelRecording { Program, Channel }

internal data class ChannelProgram(
    val title: String,
    /** Null leaves the start's line out. */
    val start: String?,
    val timeRange: String? = null,
    val progress: Float? = null,
    val timeLeft: String? = null,
    val scheduled: Boolean = false,
    /** Scheduled by an automatic recording or a repeating timer: "Scheduled" in place of [record]. */
    val ruleSchedule: RuleSchedule? = null,
    /** What its recording control does; null offers none. */
    val record: ProgramRecordControl? = ProgramRecordControl.Record,
    /** The event drawn on this row, if the row can dispatch a recording action. */
    val eventId: EventId? = null,
)

internal data class ChannelDetails(
    val artwork: Painter? = null,
    val episode: String? = null,
    val metadata: String? = null,
    val synopsis: String? = null,
    val following: List<ChannelProgram> = emptyList(),
    /** The server image's model for the touch-level image composable; the fixture painter wins. */
    val artworkModel: Any? = null,
)

/**
 * A null callback leaves its control out. Without [onPlay] a tap on a row opens or closes it, and
 * Watch is not offered. [onSignIn] is what the sign-in-needed message's button does, else
 * [onOpenConnectionSettings].
 */
internal class ChannelsActions(
    val onPlay: ((String) -> Unit)?,
    val onTag: (String?) -> Unit,
    val onRetry: () -> Unit,
    val onOpenConnectionSettings: () -> Unit,
    val onRecordControl: ((String, EventId, ProgramRecordControl) -> Unit)? = null,
    val onDetails: ((String) -> Unit)? = null,
    val onShowInGuide: ((String) -> Unit)? = null,
    val onSignIn: (() -> Unit)? = null,
)

/**
 * The row texts from the shared formatters; a null text leaves its line out. [TitlesOnly] gives
 * none, for a row that shows the titles alone.
 */
internal class ChannelsTexts(
    /** "9:00 PM". */
    val clock: (epochSec: Long) -> String?,
    /** "8:00–9:00 PM". */
    val timeRange: (startSec: Long, stopSec: Long) -> String?,
    /** "13m", "1h 27m". */
    val duration: (seconds: Long) -> String?,
    /** "S2 E4 • The Long Tide". */
    val episode: (EpgEvent) -> String?,
    /** "Drama". */
    val genre: (EpgEvent) -> String?,
) {
    companion object {
        val TitlesOnly = ChannelsTexts({ null }, { _, _ -> null }, { null }, { null }, { null })
    }
}
