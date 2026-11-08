package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

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
 * Right on a panel with pages: a row opens its page on the key's press, so the release must not
 * reach the page just opened, and a page itself never lets Right move on ([atRoot] false).
 */
internal fun Modifier.optionsPageRightKey(atRoot: Boolean): Modifier = onPreviewKeyEvent { event ->
    event.key == Key.DirectionRight && (event.type == KeyEventType.KeyUp || !atRoot)
}
