package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

internal sealed interface ChannelsState {
    data object Loading : ChannelsState
    data object Unreachable : ChannelsState
    data object SignInFailed : ChannelsState

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
)

/** The producer supplies the viewer's position, not the on-air position. */
internal data class ChannelPlayback(val progress: Float)

/** A channel fallback mark is distinct from a linked program's running recording. */
internal enum class ChannelRecording { Program, Channel }

internal data class ChannelProgram(
    val title: String,
    val start: String,
    val timeRange: String? = null,
    val progress: Float? = null,
    val timeLeft: String? = null,
    val scheduled: Boolean = false,
)

internal data class ChannelDetails(
    val artwork: Painter? = null,
    val episode: String? = null,
    val metadata: String? = null,
    val synopsis: String? = null,
    val following: List<ChannelProgram> = emptyList(),
)

internal class ChannelsActions(
    val onPlay: (String) -> Unit,
    val onTag: (String?) -> Unit,
    val onRetry: () -> Unit,
    val onOpenConnectionSettings: () -> Unit,
    val onRecord: ((String) -> Unit)? = null,
    val onDetails: ((String) -> Unit)? = null,
    val onShowInGuide: ((String) -> Unit)? = null,
)
