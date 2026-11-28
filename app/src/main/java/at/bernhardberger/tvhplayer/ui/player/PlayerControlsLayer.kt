package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics

/** The controls layer's visibility, so the shared chrome can move its header and footer in. */
internal val LocalPlayerControlsMotion = staticCompositionLocalOf<PlayerControlsMotion?> { null }

internal class PlayerControlsMotion(
    val transition: Transition<EnterExitState>,
    val appear: State<Float>,
    private val visibility: MutableTransitionState<EnterExitState>,
) {
    val hidden: Boolean get() = visibility.isIdle && visibility.currentState != EnterExitState.Visible
}

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

/** Controls are being prepared underneath a passive live Banner. */
internal val LocalControlsBehindBanner = compositionLocalOf { false }

@Composable
internal fun PlayerControlsBehindBanner(retain: Boolean, behindBanner: Boolean, content: @Composable () -> Unit) {
    if (!retain) {
        content()
    } else {
        val mask = if (behindBanner) Modifier.semantics { hideFromAccessibility() } else Modifier
        PlayerMotionFrame(
            leaving = behindBanner,
            modifier = mask.graphicsLayer { alpha = if (behindBanner) 0f else 1f },
            dropFocusToSink = false,
        ) {
            CompositionLocalProvider(LocalControlsBehindBanner provides behindBanner, content = content)
        }
    }
}

/** Build once on demand or on a later frame of the same admitted tune. */
@Composable
internal fun rememberChromeBuild(
    required: Boolean,
    warmupFrames: Int = 1,
    tuneToken: () -> Any?,
): Boolean {
    var built by remember { mutableStateOf(false) }
    if (built) return true
    val token = if (required) null else tuneToken()
    var readyToken by remember(token, warmupFrames) { mutableStateOf<Any?>(null) }
    val readToken by rememberUpdatedState(tuneToken)
    if (token != null) {
        LaunchedEffect(token, warmupFrames) {
            repeat(warmupFrames) { withFrameNanos {} }
            if (readToken() == token) readyToken = token
        }
    }
    // The wait only makes this tune a candidate. Eligibility can change before the mount.
    val mount = required || (token != null && readyToken == token)
    if (mount) SideEffect { built = true }
    return mount
}

/**
 * Keeps the chrome mounted, including at alpha zero, so a reveal doesn't have to build it.
 * It leaves quickly under an opening panel and returns once it closes. Auto-hide and Back
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
    tuneWarmupToken: () -> Any? = { null },
    content: @Composable (hidden: Boolean) -> Unit,
) {
    val shown = visible && !modalVisible
    val visibility = remember {
        MutableTransitionState(if (shown) EnterExitState.Visible else EnterExitState.PreEnter)
    }
    if (!rememberChromeBuild(shown, tuneToken = tuneWarmupToken)) return
    // Taken when the layer turns shown and kept until it turns hidden.
    val showEntry = remember(shown) { entry }
    val freshEntry = remember(shown) { shown && visibility.isIdle && visibility.currentState != EnterExitState.Visible }
    val hidden = !shown && visibility.isIdle && visibility.currentState != EnterExitState.Visible
    visibility.targetState = when {
        shown -> EnterExitState.Visible
        hidden && visibility.currentState == EnterExitState.PreEnter -> EnterExitState.PreEnter
        else -> EnterExitState.PostExit
    }
    val transition = rememberTransition(visibility, label = "player-controls")
    val entryVisit = rememberLastShown(remember(shown) { Any().takeIf { freshEntry } })
    val entryProgress = transition.animateEnterAfterFirstFrames(
        entryVisit, tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate),
    )
    val currentProgress = rememberUpdatedState(entryProgress)
    val appear = remember(currentProgress) {
        derivedStateOf { currentProgress.value.value }
    }
    val layerAlpha = rememberControlsLayerAlpha(transition, appear, freshEntry, modalVisible)
    val viewport = LocalPlayerPageScrim.current
    val fade = transition.animateShown(
        tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard),
        tween(if (modalVisible) PlayerMotion.FastMs else PlayerMotion.MediumMs, easing = PlayerMotion.StandardAccelerate),
        "viewport-chrome",
    )
    DisposableEffect(viewport, fade, appear) {
        viewport?.alpha = { fade.value * appear.value }
        onDispose { viewport?.alpha = { 0f } }
    }
    val motion = remember(transition, appear, visibility) { PlayerControlsMotion(transition, appear, visibility) }
    PlayerMotionFrame(leaving = !shown, modifier = modifier.graphicsLayer { alpha = layerAlpha() },
        dropFocusToSink = !hidden) {
        CompositionLocalProvider(
            LocalPlayerControlsMotion provides motion,
            LocalPlayerControlsEntry provides showEntry,
            LocalPlayerControlsAlpha provides layerAlpha,
        ) { content(hidden) }
    }
}

@Composable
private fun rememberControlsLayerAlpha(
    transition: Transition<EnterExitState>,
    appear: State<Float>,
    freshEntry: Boolean,
    modalVisible: Boolean,
): () -> Float {
    // An exit accelerates; a reveal that interrupts it recovers from its current alpha.
    val exitFade = transition.animateFloat(
        transitionSpec = {
            when {
                targetState == EnterExitState.PostExit -> tween(
                    if (modalVisible) PlayerMotion.FastMs else PlayerMotion.MediumMs,
                    easing = PlayerMotion.StandardAccelerate,
                )
                freshEntry -> snap()
                else -> spring(stiffness = Spring.StiffnessMediumLow)
            }
        },
        label = "controls-exit",
    ) { if (it == EnterExitState.PostExit) 0f else 1f }
    return remember {
        {
            when (transition.targetState) {
                EnterExitState.PreEnter -> 0f
                EnterExitState.Visible -> exitFade.value * appear.value
                EnterExitState.PostExit -> exitFade.value
            }
        }
    }
}
