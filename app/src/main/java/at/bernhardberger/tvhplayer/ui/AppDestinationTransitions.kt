package at.bernhardberger.tvhplayer.ui

import android.animation.ValueAnimator
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion

internal const val APP_DESTINATION_CROSSFADE_DURATION_MILLIS = 150

/**
 * Drawer destinations commit on focus and can be retargeted by rapid D-pad input.
 * Keep the crossfade brief so it masks the content handoff without delaying the
 * latest destination's focus or restoring Navigation Compose's 700 ms default.
 */
internal fun appDestinationEnterTransition(): EnterTransition = fadeIn(
    animationSpec = tween(
        durationMillis = APP_DESTINATION_CROSSFADE_DURATION_MILLIS,
        easing = LinearEasing,
    ),
)

internal fun appDestinationExitTransition(): ExitTransition = fadeOut(
    animationSpec = tween(
        durationMillis = APP_DESTINATION_CROSSFADE_DURATION_MILLIS,
        easing = LinearEasing,
    ),
)

internal fun appDestinationContentTransform(): ContentTransform =
    appDestinationEnterTransition() togetherWith appDestinationExitTransition()

/**
 * Into a player, and between players: a slower cross-fade that decelerates into place. Without
 * animations the destinations keep the browse crossfade, as before.
 */
internal fun playerDestinationContentTransform(
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
): ContentTransform {
    if (!motionEnabled) return appDestinationContentTransform()
    val spec = tween<Float>(PlayerMotion.DestinationMs, easing = PlayerMotion.EmphasizedDecelerate)
    return fadeIn(spec) togetherWith fadeOut(spec)
}

/**
 * Out of a player: the player leaves quickly on an accelerating fade while browse decelerates
 * into view. Without animations the destinations keep the browse crossfade, as before.
 */
internal fun playerExitContentTransform(
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
): ContentTransform {
    if (!motionEnabled) return appDestinationContentTransform()
    return fadeIn(tween(PlayerMotion.DestinationExitMs, easing = PlayerMotion.EmphasizedDecelerate)) togetherWith
        fadeOut(tween(PlayerMotion.DestinationExitMs, easing = PlayerMotion.EmphasizedAccelerate))
}

internal fun appRouteContentTransform(player: Boolean): ContentTransform =
    if (player) playerDestinationContentTransform() else appDestinationContentTransform()
