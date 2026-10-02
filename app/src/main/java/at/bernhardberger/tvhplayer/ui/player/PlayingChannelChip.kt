package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
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
            val watching = stringResource(R.string.player_rail_watching)
            val accent = MaterialTheme.colorScheme.tertiary
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = accent)) { append(watching) }
                    append("  ")
                    append(listOfNotNull(channelLabel.takeIf { it.isNotBlank() }, title?.takeIf { it.isNotBlank() })
                        .joinToString(" · "))
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                // On the clock's line, clear of it.
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(top = TvOverlayTopPadding + 6.dp, start = PlayerChromeTokens.gridMargin, end = 240.dp)
                    .testTag("player-playing-chip"),
            )
        }
    }
}
