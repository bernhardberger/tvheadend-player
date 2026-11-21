@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R

@Composable
internal fun LiveOffer(
    source: State<LiveTimeline?>,
    offered: Set<PlayerControl>,
    preview: Int?,
    onAction: (PlayerAction) -> Unit,
    layout: PlayerLayout,
    modifier: Modifier = Modifier,
    live: Boolean = true,
) {
    // The distance behind live and Go live belong to timeshift; without it the row keeps Full screen only.
    // Only this row reads the sampled distance, so a sample recomposes it and not the face around it.
    val timeline = source.value
    val showLive = live && timeline != null && PlayerControl.Timeshift in offered
    val behind = timeline?.let { it.behindAt(preview ?: it.position) } ?: 0
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showLive && (behind > 0 || preview == null)) {
            Text(
                behindLive(behind), color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (layout.upright) Spacer(Modifier.weight(1f))
        if (showLive && behind > 0) GoLiveButton(dragging = preview != null, onAction)
        if (layout == PlayerLayout.Phone && preview == null) FullScreenButton(offered, onAction)
    }
}

@Composable
private fun GoLiveButton(dragging: Boolean, onAction: (PlayerAction) -> Unit) {
    val buttonFill = Color(0xCC37393A)
    Button(
        onClick = { onAction(PlayerAction.GoLive) }, enabled = !dragging,
        modifier = Modifier.alpha(if (dragging) DRAG_ALPHA else 1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonFill, contentColor = Color.White,
            disabledContainerColor = buttonFill, disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 13.dp),
    ) {
        Icon(painterResource(R.drawable.player_go_live), null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.player_go_live), maxLines = 1)
    }
}

@Composable
private fun FullScreenButton(offered: Set<PlayerControl>, onAction: (PlayerAction) -> Unit) {
    if (PlayerControl.FullScreen !in offered) return
    PlayerIconButton(R.drawable.player_fullscreen, R.string.player_full_screen, { onAction(PlayerAction.FullScreen) })
}
