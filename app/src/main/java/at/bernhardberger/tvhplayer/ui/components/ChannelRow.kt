package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.profiling.profileLayout
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import coil3.ImageLoader

/**
 * Channels row width. Height is the library's: [ListItem] applies its
 * own standard padding and two-line minimum, so the row is 64dp when the text fits
 * and grows with the user's text scale instead of clipping.
 */
internal val ChannelRowWidth = 340.dp

/** Row gap, and the list's horizontal reserve for the library's focus scale. */
internal val ChannelRowGap = 4.dp
internal val ChannelRowEdgeInset = 12.dp

internal val ChannelPiconWidth = 60.dp
internal val ChannelPiconHeight = 36.dp
private val ChannelProgressGap = 3.dp
internal val ChannelRowProgressHeight = 2.dp

/**
 * One D-pad target per channel, on the library's standard [ListItem].
 *
 * `docs/DESIGN.md` section 3 requires the library component rather than an equivalent,
 * so the component owns the container, indication (transparent rest, light inverse focus
 * pill, focus scale, shape, border, glow), the selected state for the playing channel,
 * slot padding and standard headline/supporting typography. The screen contributes
 * only the programme line and the 2dp progress.
 *
 * Playing and recording markers sit beside the channel title and appear only when they
 * apply. They share the headline line, so the programme line and the progress keep one
 * width on every row regardless of a channel's status.
 */
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
    programStartSec: Long? = null,
    recordingNow: Boolean = false,
    playingNow: Boolean = false,
    playbackIndicator: ChannelPlaybackIndicator = if (playingNow) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
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
            } else {
                Modifier
            }
            Row(modifier = drawProbe, verticalAlignment = Alignment.CenterVertically) {
                ChannelTitle(
                    number = number,
                    name = name,
                    modifier = Modifier.weight(1f).testTag("channel-title"),
                )
                if (playbackIndicator != ChannelPlaybackIndicator.NONE || recordingNow) {
                    Spacer(Modifier.width(TvSpacing8))
                    ChannelNowIndicators(
                        playingNow = playingNow,
                        recordingNow = recordingNow,
                        playbackIndicator = playbackIndicator,
                    )
                }
            }
        },
        supportingContent = {
            Column(Modifier.fillMaxWidth()) {
                // This TV Material release defaults supporting text to bodySmall even
                // on ListItem. Use its 14sp bodyMedium for the accepted ten-foot line.
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    ChannelProgrammeSubtitle(
                        title = programTitle,
                        startSec = programStartSec,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (progress != null) {
                    Spacer(Modifier.height(ChannelProgressGap))
                    ProgressStrip(
                        progress = progress,
                        height = ChannelRowProgressHeight,
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
            // Bare picon: fitted inside 60x36 with its own aspect ratio, no logo box.
            PiconBox(
                imageLoader = imageLoader,
                currentSession = currentSession,
                piconPath = piconPath,
                modifier = Modifier
                    .testTag("channel-picon")
                    .size(width = ChannelPiconWidth, height = ChannelPiconHeight),
            )
        },
        modifier = modifier
            .profileLayout("channels:row")
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
    )
}
