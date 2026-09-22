package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.ui.TvSpacing8

@Composable
fun ChannelNowIndicators(
    playingNow: Boolean,
    recordingNow: Boolean,
    modifier: Modifier = Modifier,
    announceState: Boolean = true,
    playbackIndicator: ChannelPlaybackIndicator = if (playingNow) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
) {
    if (playbackIndicator == ChannelPlaybackIndicator.NONE && !recordingNow) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelPlaybackMarker(playbackIndicator, announceState = announceState)
        if (recordingNow) {
            RecordingStatusIndicator(
                state = DvrEntryState.RECORDING,
                announceState = announceState,
                modifier = Modifier.testTag("channel-recording-indicator"),
            )
        }
    }
}
