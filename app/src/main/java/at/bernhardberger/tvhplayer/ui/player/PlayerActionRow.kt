package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGroupGap
import at.bernhardberger.tvhplayer.ui.TvOverlayFocusInset
import at.bernhardberger.tvhplayer.ui.TvOverlayTerminalGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTextSecondaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBlockGap

@Composable
fun PlayerActionRow(
    modifier: Modifier = Modifier,
    contextLabel: String? = null,
    navigation: (@Composable RowScope.() -> Unit)? = null,
    transport: (@Composable RowScope.() -> Unit)? = null,
    utilities: (@Composable RowScope.() -> Unit)? = null,
    terminal: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvOverlayFocusInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGroupGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigation?.let { slot ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                        verticalAlignment = Alignment.CenterVertically,
                        content = slot,
                    )
                }
                transport?.let { slot ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                        verticalAlignment = Alignment.CenterVertically,
                        content = slot,
                    )
                }
                utilities?.let { slot ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                        verticalAlignment = Alignment.CenterVertically,
                        content = slot,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            terminal?.let { slot ->
                Row(
                    modifier = Modifier.padding(start = TvOverlayTerminalGap),
                    horizontalArrangement = Arrangement.spacedBy(TvOverlayActionGap),
                    verticalAlignment = Alignment.CenterVertically,
                    content = slot,
                )
            }
        }
        contextLabel?.let { label ->
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = TvOverlayTextSecondaryAlpha,
                ),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -TvOverlayTimelineBlockGap)
                    .fillMaxWidth()
                    .semantics {
                        testTag = "player-action-context-label"
                        hideFromAccessibility()
                    },
            )
        }
    }
}
