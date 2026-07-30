package at.bernhardberger.tvhplayer.core

import android.view.KeyEvent

enum class MediaPlaybackAction {
    NONE,
    PLAY,
    PAUSE,
    TOGGLE,
}

enum class ChannelPickAction {
    CLOSE_DRAWER,
    TUNE,
}

enum class ChannelKeyAction {
    PAGE_LIST,
    TUNE,
}

enum class PlaybackOverlayFocusTarget {
    TIMESHIFT_TOGGLE,
    CHANNELS,
    CONTROLS_CLUSTER,
}

enum class PlayerSurface {
    LIVE,
    RECORDING,
}

enum class PlayerKeyAction {
    PASS_THROUGH,
    REVEAL_CONTROLS,
    REVEAL_AND_TOGGLE_PAUSE,
    HIDE_CONTROLS,
    OPEN_CHANNELS,
    OPEN_INFO,
    SEEK_BACK,
    SEEK_FORWARD,
    CLOSE_PLAYER,
    DISMISS_OVERLAY_ONLY,
}

data class PlayerKeyContext(
    val surface: PlayerSurface,
    val controlsVisible: Boolean,
    val seekbarFocused: Boolean,
    val timeshiftAvailable: Boolean,
    val simpleTvActive: Boolean,
    val optionsOpen: Boolean = false,
    val infoOpen: Boolean = false,
    val statsOpen: Boolean = false,
    val drawerOpen: Boolean = false,
)

fun initialPlaybackOverlayFocus(timeshiftAvailable: Boolean): PlaybackOverlayFocusTarget =
    PlaybackOverlayFocusTarget.CONTROLS_CLUSTER

fun shouldRevealPlaybackControls(controlsVisible: Boolean, keyCode: Int): Boolean {
    if (controlsVisible) return false
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN -> true
        else -> false
    }
}

fun playbackSuppressesRevealingKey(
    revealingKeyCode: Int?,
    keyCode: Int,
): Boolean = revealingKeyCode == keyCode

fun playerKeyActionStartsOpeningCycle(action: PlayerKeyAction): Boolean = when (action) {
    PlayerKeyAction.REVEAL_CONTROLS,
    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE,
    PlayerKeyAction.OPEN_CHANNELS,
    PlayerKeyAction.OPEN_INFO -> true
    PlayerKeyAction.PASS_THROUGH,
    PlayerKeyAction.HIDE_CONTROLS,
    PlayerKeyAction.SEEK_BACK,
    PlayerKeyAction.SEEK_FORWARD,
    PlayerKeyAction.CLOSE_PLAYER,
    PlayerKeyAction.DISMISS_OVERLAY_ONLY -> false
}

fun playerParentConsumesRecoveryKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_BACK -> false
    else -> true
}

/**
 * Hidden-control player key contract.
 *
 * Center on seekable media both toggles pause/play and reveals controls; the
 * revealing key cycle must be suppressed so the same press cannot activate a
 * newly focused control.
 */
fun playerKeyAction(
    context: PlayerKeyContext,
    keyCode: Int,
): PlayerKeyAction {
    if (context.infoOpen && keyCode != KeyEvent.KEYCODE_BACK) {
        return PlayerKeyAction.PASS_THROUGH
    }
    if (keyCode == KeyEvent.KEYCODE_INFO) return PlayerKeyAction.OPEN_INFO
    if (
        keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU ||
        keyCode == KeyEvent.KEYCODE_TV_NUMBER_ENTRY ||
        keyCode == KeyEvent.KEYCODE_BOOKMARK
    ) {
        // Dedicated list / guide-style remote keys open the channel picker in
        // both normal and Simple TV live playback.
        return if (context.surface == PlayerSurface.LIVE) {
            PlayerKeyAction.OPEN_CHANNELS
        } else {
            PlayerKeyAction.PASS_THROUGH
        }
    }
    if (context.seekbarFocused) {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> PlayerKeyAction.SEEK_BACK
            KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerKeyAction.SEEK_FORWARD
            KeyEvent.KEYCODE_BACK -> when {
                context.infoOpen || context.optionsOpen || context.statsOpen ||
                    context.drawerOpen -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
                context.controlsVisible -> PlayerKeyAction.HIDE_CONTROLS
                context.simpleTvActive -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
                else -> PlayerKeyAction.CLOSE_PLAYER
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKeyAction.PASS_THROUGH
            else -> PlayerKeyAction.PASS_THROUGH
        }
    }

    if (context.controlsVisible) {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> when {
                context.infoOpen || context.optionsOpen || context.statsOpen ||
                    context.drawerOpen -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
                else -> PlayerKeyAction.HIDE_CONTROLS
            }
            else -> PlayerKeyAction.PASS_THROUGH
        }
    }

    // Controls hidden.
    return when (keyCode) {
        KeyEvent.KEYCODE_BACK -> when {
            context.infoOpen || context.optionsOpen || context.statsOpen ||
                context.drawerOpen -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
            context.simpleTvActive -> PlayerKeyAction.DISMISS_OVERLAY_ONLY
            else -> PlayerKeyAction.CLOSE_PLAYER
        }
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
            context.surface == PlayerSurface.RECORDING || context.timeshiftAvailable ->
                PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE
            else -> PlayerKeyAction.REVEAL_CONTROLS
        }
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKeyAction.REVEAL_CONTROLS
        KeyEvent.KEYCODE_DPAD_LEFT -> when {
            context.surface == PlayerSurface.RECORDING || context.timeshiftAvailable ->
                PlayerKeyAction.SEEK_BACK
            // Simple TV must not steal Left for the channel grid — Left/Right are
            // needed for in-grid focus. Use the remote list key or the on-screen
            // channels control instead (Material for TV / remote-first).
            context.surface == PlayerSurface.LIVE && !context.simpleTvActive ->
                PlayerKeyAction.OPEN_CHANNELS
            else -> PlayerKeyAction.PASS_THROUGH
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> when {
            context.surface == PlayerSurface.RECORDING || context.timeshiftAvailable ->
                PlayerKeyAction.SEEK_FORWARD
            else -> PlayerKeyAction.PASS_THROUGH
        }
        else -> PlayerKeyAction.PASS_THROUGH
    }
}

fun channelPickAction(currentChannelId: Int, pickedChannelId: Int): ChannelPickAction =
    if (currentChannelId == pickedChannelId) {
        ChannelPickAction.CLOSE_DRAWER
    } else {
        ChannelPickAction.TUNE
    }

fun playbackChannelKeyAction(browserVisible: Boolean): ChannelKeyAction =
    if (browserVisible) ChannelKeyAction.PAGE_LIST else ChannelKeyAction.TUNE

fun mediaPlaybackAction(
    keyCode: Int,
    playKeyCode: Int,
    pauseKeyCode: Int,
    toggleKeyCode: Int,
): MediaPlaybackAction = when (keyCode) {
    playKeyCode -> MediaPlaybackAction.PLAY
    pauseKeyCode -> MediaPlaybackAction.PAUSE
    toggleKeyCode -> MediaPlaybackAction.TOGGLE
    else -> MediaPlaybackAction.NONE
}
