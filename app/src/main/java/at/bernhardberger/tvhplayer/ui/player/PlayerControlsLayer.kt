package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The controls layer's visibility, so the shared chrome can move its header and footer in. */
internal val LocalPlayerControlsMotion = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * How the controls enter: a viewer's reveal moves header and footer in, a zap fades them in place,
 * and a reveal from the Banner takes over its info and timeline where they rest and slides them up
 * over the incoming action row ([PlayerChrome] owns that handover; the layer never sees it).
 */
internal enum class PlayerControlsEntry { TRAVEL, FADE, FROM_BANNER }

/** The entry style of the current show, fixed when the show began. */
internal val LocalPlayerControlsEntry = compositionLocalOf { PlayerControlsEntry.TRAVEL }

/** The controls layer's alpha as drawn, for what draws apart from the layer but in step with it. */
internal val LocalPlayerControlsAlpha = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * Stays composed across a modal so the controls can hand over to it: they leave
 * quickly under an opening panel and return once it closes. Auto-hide and Back
 * share one exit because the layer state does not tell them apart.
 *
 * @param entry how a show that begins now enters; a later change, such as a zap
 * while the controls are up, neither restarts nor alters the entry in progress.
 */
@Composable
internal fun PlayerControlsLayer(
    visible: Boolean,
    modalVisible: Boolean,
    modifier: Modifier = Modifier,
    entry: PlayerControlsEntry = PlayerControlsEntry.TRAVEL,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val shown = visible && !modalVisible
    // Taken when the layer turns shown and kept until it turns hidden.
    val showEntry = remember(shown) { entry }
    AnimatedVisibility(
        visible = shown,
        // Both fades run below: the fade in after the first frames, the exit with the layer's alpha.
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        modifier = modifier,
    ) {
        val appear = animateEnterAfterFirstFrames(tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate))
        // An exit accelerates: a decelerating fade drops fast and then lingers faint as a ghost. A
        // reveal that interrupts it recovers on a spring, as an AnimatedVisibility fade would.
        val exitFade = transition.animateFloat(
            transitionSpec = {
                if (EnterExitState.Visible isTransitioningTo EnterExitState.PostExit) {
                    tween(
                        if (modalVisible) PlayerMotion.FastMs else PlayerMotion.MediumMs,
                        easing = PlayerMotion.StandardAccelerate,
                    )
                } else {
                    spring(stiffness = Spring.StiffnessMediumLow)
                }
            },
            label = "controls-exit",
        ) { if (it == EnterExitState.PostExit) 0f else 1f }
        val viewport = LocalPlayerPageScrim.current
        val fade = animateShown(
            tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
            tween(if (modalVisible) PlayerMotion.FastMs else PlayerMotion.MediumMs, easing = PlayerMotion.StandardAccelerate),
            "viewport-chrome",
        )
        androidx.compose.runtime.SideEffect { viewport?.alpha = { fade.value * appear.value } }
        androidx.compose.runtime.DisposableEffect(viewport) {
            onDispose { viewport?.alpha = { 0f } }
        }
        // Entering, the layer draws at its appear alpha; leaving, or recovering from a leave, at its exit fade.
        val layerAlpha = remember {
            { exitFade.value * if (transition.targetState == EnterExitState.Visible) appear.value else 1f }
        }
        PlayerMotionFrame(leaving = leaving, modifier = Modifier.graphicsLayer { alpha = layerAlpha() }) {
            CompositionLocalProvider(
                LocalPlayerControlsMotion provides this,
                LocalPlayerControlsEntry provides showEntry,
                LocalPlayerControlsAlpha provides layerAlpha,
            ) { content() }
        }
    }
}
