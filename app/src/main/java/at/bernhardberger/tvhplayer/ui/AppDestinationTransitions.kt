package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

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
