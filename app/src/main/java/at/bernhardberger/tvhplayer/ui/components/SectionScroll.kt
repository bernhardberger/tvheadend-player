package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The bring-into-view spec of a page whose [scroll] holds sections one below the other. Focus that
 * enters another section scrolls the page there in one [animation] of our own, instead of the
 * standard spring, whose pace can't be set and which overshoots a target that moves while it runs.
 * Provide it as `LocalBringIntoViewSpec` around the scrolling content.
 *
 * [sectionAt] names the section holding the content at [top], in content coordinates: the item's
 * position names it because focus callbacks have not run yet when the page brings it into view.
 * [target] is the offset that section rests at once the page's own animations have finished, so
 * the scroll aims there in one motion. The page starts in [initial].
 *
 * While the move runs, and for focus that stays in its section, the spec asks for no scroll of its
 * own, so there is one scroll per section change. Anything else (accessibility focus, a focus
 * restore) keeps the standard path towards the section's [target].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <S> rememberSectionScrollSpec(
    scroll: ScrollState,
    initial: S,
    animation: AnimationSpec<Float>,
    sectionAt: (top: Float) -> S,
    target: (S) -> Int,
): BringIntoViewSpec {
    val scope = rememberCoroutineScope()
    val currentAnimation = rememberUpdatedState(animation)
    val currentSectionAt = rememberUpdatedState(sectionAt)
    val currentTarget = rememberUpdatedState(target)
    return remember(scroll, scope) {
        object : BringIntoViewSpec {
            var section = initial
            var move: Job? = null

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val next = currentSectionAt.value(offset + scroll.value)
                if (next != section) {
                    section = next
                    // A newer section change replaces a move still running: animateScrollTo takes
                    // over the scroll from it.
                    move?.cancel()
                    move = scope.launch { scroll.animateScrollTo(currentTarget.value(next), currentAnimation.value) }
                    return 0f
                }
                if (move?.isActive == true) return 0f
                return (currentTarget.value(section) - scroll.value).toFloat()
            }
        }
    }
}
