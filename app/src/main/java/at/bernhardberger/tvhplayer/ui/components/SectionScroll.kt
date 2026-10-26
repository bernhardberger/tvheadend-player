package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The scroll of a page whose [scroll] holds sections one below the other. Input focus that enters
 * another section scrolls the page there in one [animation] of our own, instead of the standard
 * spring, whose pace can't be set and which overshoots a target that moves while it runs. Provide
 * the result as `LocalBringIntoViewSpec` around the scrolling content and wrap every section in
 * [scrollSection]. A scrollable nested inside needs its own spec provided around it; otherwise it
 * takes the page's.
 *
 * [sectionAt] names the section holding the content at [top], in content coordinates. [target] is
 * the offset that section rests at while input focus is in [focused]; a move reads it as it starts,
 * so it can aim at where the section ends up after the page's own animations. The page starts with
 * input focus in [initial].
 *
 * Only the focus request of a section change gives way to the move. Every other request (a focus
 * restore within its section, a requester of its own) takes the standard path to its section's
 * [target]: it scrolls with the request, completes with it and stops when its caller is cancelled.
 */
@Composable
internal fun <S> rememberSectionScrollSpec(
    scroll: ScrollState,
    initial: S,
    animation: AnimationSpec<Float>,
    sectionAt: (top: Float) -> S,
    target: (section: S, focused: S) -> Int,
): SectionScroll<S> {
    val scope = rememberCoroutineScope()
    val currentAnimation = rememberUpdatedState(animation)
    val currentSectionAt = rememberUpdatedState(sectionAt)
    val currentTarget = rememberUpdatedState(target)
    return remember(scroll, scope) {
        SectionScroll(scroll, initial, scope, currentAnimation, currentSectionAt, currentTarget)
    }
}

/**
 * Reports input focus inside this section to [sections]. As a focus group, its callback runs before
 * the newly focused item asks to be brought into view, so [sections] knows that request comes from a
 * section change.
 */
internal fun <S> Modifier.scrollSection(sections: SectionScroll<S>, section: S): Modifier =
    onFocusChanged { if (it.hasFocus) sections.focus(section) }.focusGroup()

@OptIn(ExperimentalFoundationApi::class)
internal class SectionScroll<S>(
    private val scroll: ScrollState,
    initial: S,
    private val scope: CoroutineScope,
    private val animation: State<AnimationSpec<Float>>,
    private val sectionAt: State<(Float) -> S>,
    private val target: State<(S, S) -> Int>,
) : BringIntoViewSpec {
    private var focused = initial
    // Input focus has just entered [focused]; the request bringing its item into view starts the move.
    private var entered = false
    private var moving = initial
    private var move: Job? = null

    internal fun focus(section: S) {
        if (section == focused) return
        focused = section
        entered = true
    }

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val section = sectionAt.value(offset + scroll.value)
        if (entered && section == focused) {
            entered = false
            moving = section
            // A newer section change replaces a move still running: animateScrollTo takes over the
            // scroll from it.
            move?.cancel()
            move = scope.launch { scroll.animateScrollTo(target.value(section, focused), animation.value) }
            return 0f
        }
        // The move brings this section into place already.
        if (section == moving && move?.isActive == true) return 0f
        return (target.value(section, focused) - scroll.value).toFloat()
    }
}
