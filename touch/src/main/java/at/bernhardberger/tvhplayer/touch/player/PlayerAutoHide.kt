package at.bernhardberger.tvhplayer.touch.player

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player

/**
 * How long the shown controls wait before they leave by themselves, or null while something
 * holds them (touch design section 6, "Gestures"; the TV's `playerControlsAutoHideEligible`):
 * paused, tuning or buffering, a drag or a finger resting on the face, an open menu or sheet,
 * reconnecting or a message in the picture's place, and the picture-in-picture window. A null
 * [timeoutMs] never hides them.
 */
internal fun controlsHideAfter(
    shown: Boolean,
    status: LivePlayerStatus,
    paused: Boolean,
    progressing: Boolean,
    dragging: Boolean,
    fingerDown: Boolean,
    panelOpen: Boolean,
    inWindow: Boolean,
    timeoutMs: Long?,
): Long? = timeoutMs?.takeIf {
    shown && status == LivePlayerStatus.Playing && !paused && progressing && !dragging && !fingerDown &&
        !panelOpen && !inWindow
}

/** The system's timeout for people who need longer; with touch exploration on they stay. */
internal fun controlsTimeout(recommendedMs: Int, touchExploration: Boolean): Long? =
    recommendedMs.toLong().takeUnless { touchExploration }

/** The controls' timeout as the accessibility settings ask, following touch exploration as it changes. */
@Composable
internal fun rememberControlsTimeout(): Long? {
    val manager = LocalContext.current.getSystemService(AccessibilityManager::class.java)
        ?: return CONTROLS_TIMEOUT_MS.toLong()
    var exploring by remember(manager) { mutableStateOf(manager.isTouchExplorationEnabled) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        manager.addTouchExplorationStateChangeListener(listener)
        exploring = manager.isTouchExplorationEnabled
        onDispose { manager.removeTouchExplorationStateChangeListener(listener) }
    }
    val recommended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        manager.getRecommendedTimeoutMillis(
            CONTROLS_TIMEOUT_MS,
            AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_ICONS or
                AccessibilityManager.FLAG_CONTENT_TEXT,
        )
    } else {
        CONTROLS_TIMEOUT_MS
    }
    return controlsTimeout(recommended, exploring)
}

/** The picture moves: playing and ready, not buffering. */
@Composable
internal fun rememberPlayerProgressing(player: Player): State<Boolean> = produceState(player.isPlaying, player) {
    val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            value = isPlaying
        }
    }
    player.addListener(listener)
    value = player.isPlaying
    awaitDispose { player.removeListener(listener) }
}

/** The TV's delay and Media3's `PlayerControlView.DEFAULT_SHOW_TIMEOUT_MS`. */
internal const val CONTROLS_TIMEOUT_MS = 5_000
