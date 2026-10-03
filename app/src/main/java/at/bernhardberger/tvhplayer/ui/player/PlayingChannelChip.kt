package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.ui.TvOverlayTopPadding

/**
 * Trial: while the rail browses other channels, the header keeps the clock and names what keeps
 * playing in one quiet line on the top scrim. Passive, never focused.
 */
@Composable
internal fun PlayingChannelChip(
    visible: Boolean,
    channelLabel: String,
    title: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.StandardDecelerate)),
        exit = fadeOut(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate)),
    ) {
        Box(Modifier.fillMaxWidth().focusProperties { canFocus = false }) {
            val onSurface = MaterialTheme.colorScheme.onSurface
            // On the clock's baseline: an empty run in the clock's style sets it, the rest stands on it.
            Row(
                Modifier.align(Alignment.TopStart)
                    .padding(top = TvOverlayTopPadding, start = PlayerChromeTokens.gridMargin, end = 240.dp)
                    .testTag("player-playing-chip"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(0.dp).alignByBaseline())
                Text(stringResource(R.string.player_rail_watching), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary, maxLines = 1, modifier = Modifier.alignByBaseline())
                Text(
                    buildAnnotatedString {
                        append(channelLabel)
                        title?.takeIf { it.isNotBlank() }?.let {
                            withStyle(SpanStyle(color = onSurface.copy(alpha = 0.72f))) { append(if (channelLabel.isBlank()) it else "  ·  $it") }
                        }
                    },
                    style = MaterialTheme.typography.titleMedium, color = onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}
