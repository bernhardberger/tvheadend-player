package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import coil3.ImageLoader

@Composable
fun ChannelRow(
    modifier: Modifier = Modifier,
    number: Int?,
    name: String,
    programTitle: String,
    progress: Float?,
    imageLoader: ImageLoader,
    piconPath: String?,
    focused: Boolean,
    recordingNow: Boolean = false,
    playingNow: Boolean = false,
    onFocus: () -> Unit,
    onConfirm: () -> Unit,
) {
    ListItem(
        selected = playingNow,
        onClick = onConfirm,
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        number?.let {
                            append(it)
                            append("  ")
                        }
                        append(name)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (playingNow) {
                    Spacer(Modifier.width(TvSpacing8))
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_on_now),
                        modifier = Modifier
                            .testTag("channel-playing-indicator")
                            .size(20.dp),
                    )
                }
            }
        },
        supportingContent = {
            Column(Modifier.padding(top = 3.dp)) {
                Text(
                    text = programTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress != null) {
                    Spacer(Modifier.height(6.dp))
                    ProgressStrip(
                        progress = progress,
                        trackColor = if (focused) {
                            MaterialTheme.colorScheme.inverseOnSurface.copy(
                                alpha = TvTextDisabledAlpha,
                            )
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = TvTrackAlpha)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel-progress"),
                    )
                }
            }
        },
        leadingContent = {
            PiconBox(
                imageLoader = imageLoader,
                piconPath = piconPath,
                modifier = Modifier
                    .testTag("channel-picon")
                    .width(56.dp)
                    .height(40.dp),
            )
        },
        trailingContent = if (recordingNow) {
            {
                RecordingStatusIndicator(
                    state = at.bernhardberger.tvhplayer.htsp.DvrState.RECORDING,
                    modifier = Modifier.testTag("channel-recording-indicator"),
                )
            }
        } else null,
        scale = ListItemDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
    )
}
