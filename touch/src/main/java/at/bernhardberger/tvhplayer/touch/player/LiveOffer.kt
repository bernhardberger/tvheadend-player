@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
    val behind = timeline?.let { it.behindAt(preview ?: it.position) } ?: 0
    val hasAhead = if (preview == null) timeline?.hasAhead == true else behind > 0
    val showBehind = live && hasAhead && PlayerControl.Timeshift in offered
    val phone = layout == PlayerLayout.Phone
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBehind && phone) {
            PhoneBehindLive(behind, Modifier.weight(1f))
        } else {
            if (showBehind) {
                Text(
                    behindLive(behind), color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (layout.upright) Spacer(Modifier.weight(1f))
        }
        if (showBehind) GoLiveButton(dragging = preview != null, onAction)
        if (phone && preview == null) FullScreenButton(offered, onAction)
    }
}

/** The actions get measured first. Never render a truncated timestamp in what is left. */
@Composable
private fun PhoneBehindLive(seconds: Int, modifier: Modifier) {
    val style = MaterialTheme.typography.labelLarge
    val color = MaterialTheme.colorScheme.tertiary
    val label = behindLive(seconds)
    val offset = behindLiveOffset(seconds)
    val measure = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val available = constraints.maxWidth
        val minimumEllipsis = label.substringBefore(offset) + offset + "…"
        val fitsEllipsis = seconds == 0 || measure.measure(minimumEllipsis, style, maxLines = 1).size.width <= available
        val fitsOffset = fitsEllipsis || measure.measure(offset, style, maxLines = 1).size.width <= available
        val showOffsetOnly = !fitsEllipsis && fitsOffset
        Text(
            if (showOffsetOnly) offset else label,
            if (showOffsetOnly) Modifier.clearAndSetSemantics { text = AnnotatedString(label) } else Modifier,
            color = if (fitsOffset) color else Color.Transparent,
            style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun behindLiveOffset(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

@Composable
private fun GoLiveButton(dragging: Boolean, onAction: (PlayerAction) -> Unit) {
    PictureOfferButton(R.string.player_go_live, R.drawable.player_go_live, dragging, onClick = {
        onAction(PlayerAction.GoLive)
    })
}

@Composable
internal fun PictureOfferButton(
    label: Int,
    icon: Int?,
    dragging: Boolean,
    onClick: () -> Unit,
    bare: Boolean = false,
) {
    val buttonFill = if (bare) Color(0xB3000000) else Color(0xCC37393A)
    Button(
        onClick = onClick, enabled = !dragging,
        modifier = Modifier.alpha(if (dragging) DRAG_ALPHA else 1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonFill, contentColor = Color.White,
            disabledContainerColor = buttonFill, disabledContentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 13.dp),
    ) {
        icon?.let {
            Icon(painterResource(it), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(stringResource(label), maxLines = 1)
    }
}

@Composable
internal fun FullScreenButton(offered: Set<PlayerControl>, onAction: (PlayerAction) -> Unit) {
    if (PlayerControl.FullScreen !in offered) return
    PlayerIconButton(R.drawable.player_fullscreen, R.string.player_full_screen, { onAction(PlayerAction.FullScreen) })
}
