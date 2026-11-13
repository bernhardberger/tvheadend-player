package at.bernhardberger.tvhplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import at.bernhardberger.tvhplayer.ui.TvScrimModalAlpha
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.RegisterPlayerModal

/** Modal frame and input ownership, independent of the centred card's content. */
@Composable
internal fun CentredDialogShell(
    paneTitle: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    verticalKeys: Boolean = false,
    visibility: MutableTransitionState<Boolean> = remember {
        MutableTransitionState(false).apply { targetState = true }
    },
    card: @Composable () -> Unit,
) {
    RegisterPlayerModal(backEnabled)
    var openingReleasePending by remember { mutableStateOf(true) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visibility.isIdle, visibility.currentState, visibility.targetState) {
        if (visibility.isIdle && !visibility.currentState && !visibility.targetState) latestDismiss()
    }
    val back = onBack ?: { visibility.targetState = false }
    BackHandler(enabled = backEnabled) { back() }
    // Expand over the rail without registering opaque details: the shell stays visible below
    // this single scrim, and focus never crosses an Android window boundary on dismissal.
    AnimatedVisibility(visibleState = visibility, modifier = modifier.browseDetailsViewport(),
        enter = fadeIn(tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard)),
        exit = fadeOut(tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard))) {
        val layering = LocalBrowseContentLayering.current
        val key = remember { Any() }
        val scrim = transition.animateFloat(
            transitionSpec = { tween(PlayerMotion.PanelMs, easing = PlayerMotion.Standard) },
            label = "modal-rail-scrim",
        ) { if (it == EnterExitState.Visible) TvScrimModalAlpha else 0f }
        DisposableEffect(layering, scrim) {
            layering?.modalScrims?.set(key, scrim)
            onDispose { layering?.modalScrims?.remove(key) }
        }
        val backCycle = rememberBackKeyCycle(backEnabled && visibility.targetState)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = TvScrimModalAlpha))
            .backKeyCycleFocus(backCycle)
            .testTag("centred-dialog-scrim")
            .semantics { dialog(); this.paneTitle = paneTitle }
            .onPreviewKeyEvent { event ->
                if (!visibility.targetState) {
                    backCycle.reset()
                    return@onPreviewKeyEvent true
                }
                if (!backEnabled) backCycle.reset()
                if (backEnabled && event.key == Key.Back) {
                    return@onPreviewKeyEvent backCycle.handle(event.type == KeyEventType.KeyDown,
                        event.nativeKeyEvent.repeatCount, event.nativeKeyEvent.isCanceled) { back() }
                }
                val activation = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (activation && openingReleasePending) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        openingReleasePending = false
                    } else {
                        if (event.type == KeyEventType.KeyUp) openingReleasePending = false
                        return@onPreviewKeyEvent true
                    }
                }
                !verticalKeys && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
            }, contentAlignment = Alignment.Center) {
            card()
        }
    }
}
