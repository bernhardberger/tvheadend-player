package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * The pages of an options panel, shared by the player's options sheet and the browse side panels:
 * a deeper page arrives from the end and a shallower one from the start, the outgoing page
 * leaves the other way already without focus or semantics, and each visit composes its page
 * afresh so returning to a page that is still leaving focuses its intended row again. [depth]
 * orders the pages (the root is the shallowest); [animate] false switches pages at once, which a
 * list that applies its focused row as it closes needs. [content] draws one page, header included.
 */
@Composable
internal fun <T> OptionsPageHost(
    page: T,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    if (!animate) {
        content(page)
        return
    }
    val pageTransition = updateTransition(rememberVisit(page), label = "options-page")
    pageTransition.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate)) togetherWith
                fadeOut(tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate))).using(null)
        },
    ) { visit ->
        val travel = animateShown(
            enter = tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate),
            exit = tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate),
            label = "options-page-travel",
        )
        PlayerMotionFrame(
            leaving = leaving,
            modifier = Modifier.graphicsLayer {
                val deeper = depth(pageTransition.targetState.value) > depth(pageTransition.currentState.value)
                val towardEnd = if (deeper) 1f else -1f
                val side = if (leaving) -towardEnd else towardEnd
                translationX = side * PlayerMotion.PageOffset.toPx() * (1f - travel.value)
            },
        ) { content(visit.value) }
    }
}

/**
 * The logical end key opens a row's level; the logical start key does the same step as Back.
 * Bubble handling lets a focused control use either key first. A back step waits for release,
 * keeping the entire press inside the panel even when the step closes it over the player.
 */
@Composable
internal fun Modifier.optionsPageKeys(atRoot: Boolean, onBack: () -> Unit): Modifier {
    val end = optionsPageEndKey()
    val start = if (end == Key.DirectionRight) Key.DirectionLeft else Key.DirectionRight
    var backing by remember { mutableStateOf(false) }
    return onKeyEvent { event ->
        when (event.key) {
            start -> {
                if (event.type == KeyEventType.KeyDown) backing = true
                if (event.type == KeyEventType.KeyUp && backing) {
                    backing = false
                    if (!event.nativeKeyEvent.isCanceled) onBack()
                }
                true
            }
            end -> event.type == KeyEventType.KeyUp || !atRoot
            else -> false
        }
    }
}

@Composable
internal fun optionsPageEndKey(): Key =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) Key.DirectionLeft else Key.DirectionRight
