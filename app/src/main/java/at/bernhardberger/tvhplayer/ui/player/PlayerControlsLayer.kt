package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The controls layer's visibility, so the shared chrome can move its header and footer in. */
internal val LocalPlayerControlsMotion = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Stays composed across a modal so the controls can hand over to it: they leave
 * quickly under an opening panel and return once it closes. Auto-hide and Back
 * share one exit because the layer state does not tell them apart.
 */
@Composable
internal fun PlayerControlsLayer(
    visible: Boolean,
    modalVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible && !modalVisible,
        enter = fadeIn(tween(PlayerMotion.MediumMs, easing = PlayerMotion.EmphasizedDecelerate)),
        exit = fadeOut(
            if (modalVisible) tween(PlayerMotion.FastMs, easing = PlayerMotion.StandardAccelerate)
            else tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard)
        ),
        modifier = modifier,
    ) {
        PlayerMotionFrame(leaving = leaving) {
            CompositionLocalProvider(LocalPlayerControlsMotion provides this) { content() }
        }
    }
}
