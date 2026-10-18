package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.ui.TvSpacing8

@Composable
fun ChannelNowIndicators(
    playingNow: Boolean,
    recordingNow: Boolean,
    modifier: Modifier = Modifier,
    announceState: Boolean = true,
    playbackIndicator: ChannelPlaybackIndicator = if (playingNow) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
    /** Null keeps each marker's own size; a label line sets one size for both. */
    iconSize: Dp? = null,
) {
    if (playbackIndicator == ChannelPlaybackIndicator.NONE && !recordingNow) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconSize != null) ChannelPlaybackMarker(playbackIndicator, announceState = announceState, size = iconSize)
        else ChannelPlaybackMarker(playbackIndicator, announceState = announceState)
        if (recordingNow) {
            RecordingStatusIndicator(
                state = DvrEntryState.RECORDING,
                announceState = announceState,
                modifier = (if (iconSize != null) Modifier.size(iconSize) else Modifier).testTag("channel-recording-indicator"),
            )
        }
    }
}
