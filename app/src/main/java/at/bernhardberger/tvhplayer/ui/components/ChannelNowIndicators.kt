package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.core.DvrState
import at.bernhardberger.tvhplayer.ui.TvSpacing8

@Composable
fun ChannelNowIndicators(
    playingNow: Boolean,
    recordingNow: Boolean,
    modifier: Modifier = Modifier,
    playingTint: Color = LocalContentColor.current,
    announceState: Boolean = true,
) {
    if (!playingNow && !recordingNow) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TvSpacing8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playingNow) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = if (announceState) {
                    stringResource(R.string.player_on_now)
                } else {
                    null
                },
                tint = playingTint,
                modifier = Modifier
                    .testTag("channel-playing-indicator")
                    .size(20.dp),
            )
        }
        if (recordingNow) {
            RecordingStatusIndicator(
                state = DvrState.RECORDING,
                announceState = announceState,
                modifier = Modifier.testTag("channel-recording-indicator"),
            )
        }
    }
}
