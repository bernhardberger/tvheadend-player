package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The controls layer's visibility, so the shared chrome can move its header and footer in. */
internal val LocalPlayerControlsMotion = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** How the controls enter: a viewer's reveal moves header and footer in, a zap fades them in place. */
internal enum class PlayerControlsEntry { TRAVEL, FADE }

/** The entry style of the current show, fixed when the show began. */
internal val LocalPlayerControlsEntry = compositionLocalOf { PlayerControlsEntry.TRAVEL }

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
        enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate)),
        exit = fadeOut(
            if (modalVisible) tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)
            else tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)
        ),
        modifier = modifier,
    ) {
        PlayerMotionFrame(leaving = leaving) {
            CompositionLocalProvider(
                LocalPlayerControlsMotion provides this,
                LocalPlayerControlsEntry provides showEntry,
            ) { content() }
        }
    }
}
