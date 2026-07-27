package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGap
import at.bernhardberger.tvhplayer.ui.TvOverlayActionGroupGap
import at.bernhardberger.tvhplayer.ui.TvOverlayFocusInset
import at.bernhardberger.tvhplayer.ui.TvOverlayTerminalGap

@Composable
fun PlayerActionRow(
    modifier: Modifier = Modifier,
    navigation: (@Composable RowScope.() -> Unit)? = null,
    transport: (@Composable RowScope.() -> Unit)? = null,
    utilities: (@Composable RowScope.() -> Unit)? = null,
    terminal: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = TvOverlayFocusInset),
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
}
