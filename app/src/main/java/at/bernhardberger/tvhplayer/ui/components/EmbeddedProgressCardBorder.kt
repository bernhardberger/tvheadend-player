package at.bernhardberger.tvhplayer.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CardScale
import at.bernhardberger.tvhplayer.ui.TvCardFocusedScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val FocusOutlineWidth = 2.5.dp
private val FocusOutlineInset = 4.25.dp
internal val EmbeddedProgressCardBorderOutset = FocusOutlineInset + FocusOutlineWidth / 2

/** The card itself draws no ring and keeps its size: [cardFocusRing] does both. */
@Composable
@ReadOnlyComposable
internal fun embeddedProgressCardBorder(): CardBorder =
    CardDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None)
internal val EmbeddedProgressCardScale = CardScale.None

/** The ring's alpha at the faint end of its pulse, as Google TV Home's ring reads over its backdrop. */
internal const val CardFocusRingPulseLow = 0.45f

/** One way of the pulse, solid to faint; a whole breath takes twice as long. */
internal const val CardFocusRingPulseMs = 1500

private val PulseEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// TV Material 1.1.0's card scale motion, which the card no longer runs itself.
private val ScaleEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** How long TV Material 1.1.0 takes a card's scale to its new target after [interaction]. */
internal fun cardFocusScaleMillis(interaction: Interaction): Int = when (interaction) {
    is FocusInteraction.Focus -> 300
    is FocusInteraction.Unfocus -> 500
    is PressInteraction.Press -> 120
    else -> 300
}

/**
 * A focused card's ring and focus scale. TV Material's [Border] paints a fixed brush, so a
 * breathing ring through it would recompose the card every frame; [cardFocusRing] draws the ring
 * instead and reads [alpha] only while drawing. It owns the scale too: the native scale layer lies
 * inside the card's modifier, where this ring would not grow with it.
 */
@Stable
internal class CardFocusRing {
    internal val scale = Animatable(1f)
    /** Solid at 1; breathes toward [CardFocusRingPulseLow] while focused. */
    internal val alpha = Animatable(1f)
    internal var shown by mutableStateOf(false)
}

/**
 * Follows [interactionSource] as TV Material's card would: the ring shows while focused or
 * pressed, and the card grows to [TvCardFocusedScale] while focused but not pressed. A focused
 * card's ring starts solid and breathes slowly between solid and see-through; pressed, or with
 * [motionEnabled] off, it stays solid. Only a focused card runs the pulse.
 */
@Composable
internal fun rememberCardFocusRing(
    interactionSource: InteractionSource,
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
): CardFocusRing {
    val ring = remember { CardFocusRing() }
    // Motion changes restart animation, not the interactions already held by this source.
    val focuses = remember(interactionSource) { mutableListOf<FocusInteraction.Focus>() }
    val presses = remember(interactionSource) { mutableListOf<PressInteraction.Press>() }
    LaunchedEffect(ring, interactionSource, motionEnabled) {
        ring.alpha.snapTo(1f)
        ring.shown = focuses.isNotEmpty() || presses.isNotEmpty()
        val restingFocus = focuses.isNotEmpty() && presses.isEmpty()
        ring.scale.snapTo(if (restingFocus) TvCardFocusedScale else 1f)
        var scaling: Job? = null
        var pulse: Job? = if (restingFocus && motionEnabled) launch {
            ring.alpha.animateTo(CardFocusRingPulseLow,
                infiniteRepeatable(tween(CardFocusRingPulseMs, easing = PulseEasing), RepeatMode.Reverse))
        } else null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> focuses += interaction
                is FocusInteraction.Unfocus -> focuses -= interaction.focus
                is PressInteraction.Press -> presses += interaction
                is PressInteraction.Release -> presses -= interaction.press
                is PressInteraction.Cancel -> presses -= interaction.press
                else -> return@collect
            }
            val focused = focuses.isNotEmpty()
            val pressed = presses.isNotEmpty()
            ring.shown = focused || pressed
            val target = if (focused && !pressed) TvCardFocusedScale else 1f
            scaling?.cancel()
            scaling = launch { ring.scale.animateTo(target, tween(cardFocusScaleMillis(interaction), easing = ScaleEasing)) }
            pulse?.cancel()
            pulse = launch {
                ring.alpha.snapTo(1f)
                if (focused && !pressed && motionEnabled) ring.alpha.animateTo(CardFocusRingPulseLow,
                    infiniteRepeatable(tween(CardFocusRingPulseMs, easing = PulseEasing), RepeatMode.Reverse))
            }
        }
    }
    return ring
}

/**
 * Scales the card with [ring] and draws its ring: a [FocusOutlineWidth] stroke centred
 * [FocusOutlineInset] outside the card, leaving a gap inside it, in [color]. The ring's radius adds
 * that inset to [cardRadius] (TV Material 1.1.0's card default is 8dp) so the corners stay
 * concentric. Goes first on the card's modifier, so its glow and content scale within. [color] is
 * `onSurface`, matching TV Material's focused buttons.
 *
 * The scale applies to the drawing only: the card's focus and semantics bounds stay at rest, as
 * under TV Material's own scale, so lists bring the same rectangle into view. The card's own
 * layer keeps the pulse's redraws to this card.
 */
internal fun Modifier.cardFocusRing(ring: CardFocusRing, color: Color, cardRadius: Dp = 8.dp): Modifier = this
    .graphicsLayer()
    .drawWithCache {
        val inset = FocusOutlineInset.toPx()
        val ringSize = Size(size.width + 2 * inset, size.height + 2 * inset)
        val outline = RoundedCornerShape(cardRadius + FocusOutlineInset).createOutline(ringSize, layoutDirection, this)
        val stroke = Stroke(FocusOutlineWidth.toPx())
        onDrawWithContent {
            scale(ring.scale.value) {
                this@onDrawWithContent.drawContent()
                if (ring.shown) inset(-inset) { drawOutline(outline, color, alpha = ring.alpha.value, style = stroke) }
            }
        }
    }
