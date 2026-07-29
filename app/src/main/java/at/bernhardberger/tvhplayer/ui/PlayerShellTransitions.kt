package at.bernhardberger.tvhplayer.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import at.bernhardberger.tvhplayer.core.isPlayerShellTransition

private const val DEFAULT_DESTINATION_TRANSITION_DURATION_MS = 700
private val playerDestinationRoutes = setOf(Routes.PLAYER, Routes.RECORDING_PLAYER)

internal fun playerShellEnterTransition(
    initialRoute: String?,
    targetRoute: String?,
): EnterTransition = if (
    isPlayerShellTransition(initialRoute, targetRoute, playerDestinationRoutes)
) {
    EnterTransition.None
} else {
    fadeIn(animationSpec = tween(DEFAULT_DESTINATION_TRANSITION_DURATION_MS))
}

internal fun playerShellExitTransition(
    initialRoute: String?,
    targetRoute: String?,
): ExitTransition = if (
    isPlayerShellTransition(initialRoute, targetRoute, playerDestinationRoutes)
) {
    ExitTransition.None
} else {
    fadeOut(animationSpec = tween(DEFAULT_DESTINATION_TRANSITION_DURATION_MS))
}
