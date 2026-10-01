package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

internal fun playerChromeAlpha(timelineFocused: Boolean, previewing: Boolean): Float = when {
    !timelineFocused -> 1f
    previewing -> 0f
    else -> 0.55f
}

@Composable
internal fun rememberPlayerChromeAlpha(timelineFocused: Boolean, previewing: Boolean): State<Float> =
    animateFloatAsState(
        playerChromeAlpha(timelineFocused, previewing),
        animationSpec = tween(PlayerMotion.EmphasisMs),
        label = "player-surrounding-chrome",
    )

// Keep explicit D-pad destinations mounted and focusable. Receiving focus ends the
// focus view, even when the playback owner still has an asynchronous seek pending.
/**
 * [revealAlpha] is a fade of the same content, such as the action row fading in under the Banner
 * taken over. It multiplies into this one layer: a second alpha layer around the content would
 * render into a buffer of the unexpanded bounds and clip the focused control's scale and glow.
 */
internal fun Modifier.playerChromeEmphasis(
    alpha: State<Float>,
    hidden: Boolean,
    focusOverflow: Dp = 0.dp,
    revealAlpha: () -> Float = { 1f },
): Modifier =
    // Alpha < 1 creates an offscreen buffer even with clip=false. Expand only that
    // buffer, not the measured action strip or its stable 48dp control positions.
    layout { measurable, constraints ->
        val reserve = focusOverflow.roundToPx()
        val child = measurable.measure(constraints.offset(reserve * 2, reserve * 2))
        layout(child.width - reserve * 2, child.height - reserve * 2) {
            child.placeRelative(-reserve, -reserve)
        }
    }
        .graphicsLayer { this.alpha = alpha.value * revealAlpha() }
        .padding(focusOverflow)
        .then(if (hidden) Modifier.clearAndSetSemantics { } else Modifier)
