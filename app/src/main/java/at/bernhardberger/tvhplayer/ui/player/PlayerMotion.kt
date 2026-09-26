package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Player motion tokens: durations, easing and travel. Exits are shorter than
 * enters; nothing springs; only alpha and translation animate.
 */
internal object PlayerMotion {
    const val FastMs = 100
    const val ShortMs = 150
    const val MediumMs = 200
    const val PanelMs = 250
    /** Chrome emphasis while the timeline previews a seek. */
    const val EmphasisMs = 180

    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Header and footer travel when the controls appear. */
    val ChromeOffset = 16.dp
    /** Side panel travel from the end edge. */
    val PanelOffset = 40.dp
    /** Panel page travel between a page and its detail. */
    val PageOffset = 24.dp
    /** Channel identity travel in the zap direction. */
    val ZapTextOffset = 8.dp
}

/** Supplies the enter/exit transition of a player side panel to its frame. */
internal val LocalPlayerPanelMotion = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Keeps a player side panel composed while it animates out, rendering the last
 * non-null [value]. The panel frame animates scrim and panel separately. A value
 * for which [closesAtOnce] holds leaves without an exit, for panels whose
 * disposal carries behavior that must not wait for motion.
 *
 * Every opening composes a fresh panel, even while the previous one is still
 * fading, so the panel's initial-focus effects run again instead of reusing a
 * leaving composition. The last value is forgotten once the exit has finished.
 */
@Composable
internal fun <T : Any> PlayerPanelVisibility(
    value: T?,
    modifier: Modifier = Modifier,
    closesAtOnce: (T) -> Boolean = { false },
    content: @Composable (T) -> Unit,
) {
    val open = value != null
    val retention = remember { PanelRetention<T>() }
    // Written ahead of the content that reads it, so no frame shows a stale value.
    if (open && !retention.open) retention.opening += 1
    if (retention.open != open) retention.open = open
    if (value != null && retention.shown != value) retention.shown = value
    if (value == null && retention.shown?.let(closesAtOnce) == true) retention.shown = null
    key(retention.opening) {
        val visibility = remember { MutableTransitionState(false) }
        visibility.targetState = open
        if (!open && visibility.isIdle && !visibility.currentState && retention.shown != null) {
            retention.shown = null
        }
        AnimatedVisibility(
            visibleState = visibility,
            modifier = modifier,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            label = "player-panel",
        ) {
            CompositionLocalProvider(LocalPlayerPanelMotion provides this) {
                retention.shown?.let { content(it) }
            }
        }
    }
}

private class PanelRetention<T : Any> {
    var open by mutableStateOf(false)
    var opening by mutableIntStateOf(0)
    var shown by mutableStateOf<T?>(null)
}

/**
 * Content shown by an AnimatedVisibility driven by [shown] leaves semantics on its
 * first exit frame. Place it ahead of the caller's modifier so their tags clear too.
 */
internal fun Modifier.semanticsWhileShown(shown: Boolean): Modifier =
    if (shown) this else this.clearAndSetSemantics { }

/**
 * Last non-null [value] seen, so exiting content keeps what it showed. Call it inside
 * the animated content, so the value is forgotten when that content leaves.
 */
@Composable
internal fun <T : Any> rememberLastShown(value: T?): T? {
    val last = remember { mutableStateOf(value) }
    if (value != null && last.value != value) last.value = value
    return last.value
}

/**
 * One showing of [value] as AnimatedContent state. Each change makes a new visit, so
 * returning to a value whose previous content is still leaving composes it afresh
 * and its initial-focus effects run again.
 */
internal class PlayerVisit<T>(val value: T)

@Composable
internal fun <T> rememberVisit(value: T): PlayerVisit<T> = remember(value) { PlayerVisit(value) }

/** True from the first frame this content animates out until it is removed. */
internal val AnimatedVisibilityScope.leaving: Boolean
    get() = transition.targetState == EnterExitState.PostExit

/** 1 while shown, 0 while hidden; drives graphics-layer alpha or translation. */
@Composable
internal fun AnimatedVisibilityScope.animateShown(
    enter: FiniteAnimationSpec<Float>,
    exit: FiniteAnimationSpec<Float>,
    label: String,
): State<Float> = transition.animateFloat(
    transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
    label = label,
) { if (it == EnterExitState.Visible) 1f else 0f }

/**
 * Hosts content that may animate out. While [leaving], the content is removed from
 * semantics and focus cannot enter it; focus inside it drops to an inert sink on the
 * first exit frame, so the successor or the player root can take it without waiting
 * for the animation and no key reaches a disappearing control.
 */
@Composable
internal fun PlayerMotionFrame(
    leaving: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sink = remember { FocusRequester() }
    val focus = remember { FrameFocus() }
    Box(
        modifier = modifier
            .then(if (leaving) Modifier.clearAndSetSemantics { } else Modifier)
            .onFocusChanged { focus.within = it.hasFocus }
            .focusProperties {
                canFocus = false
                if (leaving) onEnter = { cancelFocusChange() }
            }
            .focusTarget(),
        propagateMinConstraints = true,
    ) {
        content()
        Box(
            Modifier
                .matchParentSize()
                .onFocusChanged { focus.sinkFocused = it.isFocused }
                .focusRequester(sink)
                .focusProperties {
                    canFocus = leaving
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .focusTarget(),
        )
    }
    if (leaving) {
        // Read in composition so focus that still arrives while leaving drops again.
        val strayFocus = focus.within && !focus.sinkFocused
        SideEffect {
            if (strayFocus) sink.requestFocus()
        }
    }
}

private class FrameFocus {
    var within by mutableStateOf(false)
    var sinkFocused by mutableStateOf(false)
}
