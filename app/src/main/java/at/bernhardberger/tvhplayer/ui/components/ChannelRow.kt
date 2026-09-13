package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.profiling.profileLayout
import at.bernhardberger.tvhplayer.profiling.profileTrace

internal val ChannelRowVerticalPadding = 3.dp

@Composable
fun ChannelRow(
    modifier: Modifier = Modifier,
    number: Int?,
    name: String,
    programTitle: String,
    progress: Float?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    piconPath: String?,
    recordingNow: Boolean = false,
    playingNow: Boolean = false,
    onFocus: () -> Unit,
    onConfirm: () -> Unit,
) = profileTrace("P1:compose:channelRow") {
    var focused by remember { mutableStateOf(false) }
    ListItem(
        selected = playingNow,
        onClick = onConfirm,
        headlineContent = {
            val drawProbe = if (BuildConfig.PROFILE_TRACE) {
                // The headline inherits the native Surface foreground. Observe it
                // without replacing its interaction source, colours or focus handling.
                val contentColor = LocalContentColor.current
                val colors = ListItemDefaults.colors()
                val focusColor = if (playingNow) {
                    colors.focusedSelectedContentColor
                } else {
                    colors.focusedContentColor
                }
                val unfocusedColor = if (playingNow) colors.selectedContentColor else colors.contentColor
                val drawLabel = when {
                    focusColor == unfocusedColor -> "P2:focusedRowDraw:ambiguousColor"
                    contentColor == focusColor -> "P2:focusedRowDraw:nativeFocusColor"
                    else -> "P2:focusedRowDraw:otherColor"
                }
                Modifier.drawWithContent {
                    if (focused) {
                        profileTrace(drawLabel) { drawContent() }
                    } else {
                        drawContent()
                    }
                }
            } else Modifier
            Row(modifier = drawProbe, verticalAlignment = Alignment.CenterVertically) {
                ChannelTitle(
                    number = number,
                    name = name,
                    modifier = Modifier.weight(1f),
                )
                if (playingNow || recordingNow) {
                    Spacer(Modifier.width(TvSpacing8))
                    ChannelNowIndicators(
                        playingNow = playingNow,
                        recordingNow = recordingNow,
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
                currentSession = currentSession,
                piconPath = piconPath,
                modifier = Modifier
                    .testTag("channel-picon")
                    .width(56.dp)
                    .height(40.dp),
            )
        },
        scale = ListItemDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
        ),
        modifier = modifier
            .profileLayout("channels:row")
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = ChannelRowVerticalPadding)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
    )
}
