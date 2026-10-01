package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

/** What the typed digits lead to: the matching channel, none yet, or none at all. */
@Immutable
internal sealed interface ChannelNumberTarget {
    data class Channel(val name: String, val picon: ArtworkId?) : ChannelNumberTarget
    /** Not known yet: more digits may still name a channel, or the list has not loaded. */
    data object Pending : ChannelNumberTarget
    /** The complete number names no channel. */
    data object None : ChannelNumberTarget
}

@Immutable
private data class ChannelNumberEntry(val number: String, val target: ChannelNumberTarget)

/**
 * A compact number badge that extends to show a known destination on the same line.
 * Only the digit slot reserves width; a pending entry has no destination compartment.
 */
@Composable
internal fun ChannelNumberOverlay(
    number: String,
    target: ChannelNumberTarget,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
    maxDigits: Int = 3,
) {
    AnimatedContent(
        targetState = ChannelNumberEntry(number, target),
        contentKey = { it.number.isNotEmpty() },
        // Fade the whole badge; digit and destination updates never wait for motion.
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
        modifier = modifier,
        label = "channel number",
    ) { displayed ->
        // The outgoing content owns its digits and target until its background has faded out.
        if (displayed.number.isNotEmpty()) {
            Surface(
                colors = SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier
                        .animateContentSize(
                            animationSpec = tween(PlayerMotion.ShortMs, easing = PlayerMotion.Standard),
                            alignment = Alignment.TopStart,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum")
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "0".repeat(maxDigits.coerceAtLeast(3)),
                            style = style,
                            modifier = Modifier.alpha(0f).clearAndSetSemantics { },
                        )
                        Text(
                            text = displayed.number,
                            style = style,
                            maxLines = 1,
                        )
                    }
                    if (displayed.target != ChannelNumberTarget.Pending) {
                        ChannelNumberTargetView(displayed.target, imageLoader, currentSession,
                            Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelNumberTargetView(
    target: ChannelNumberTarget,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.widthIn(max = 240.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val channel = target as? ChannelNumberTarget.Channel
        if (channel?.picon != null && currentSession != null) {
            SubcomposeAsyncImage(
                model = AppArtworkSource(currentSession, channel.picon),
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                loading = {},
                error = {},
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(48.dp, 28.dp)
                            .testTag("channel-number-picon"),
                    )
                },
            )
        }
        Text(
            text = when (target) {
                is ChannelNumberTarget.Channel -> target.name
                ChannelNumberTarget.None -> stringResource(R.string.player_channel_number_none)
                ChannelNumberTarget.Pending -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag("channel-number-target"),
        )
    }
}
