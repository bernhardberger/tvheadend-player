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

fun shouldRevealPlaybackControls(controlsVisible: Boolean, keyCode: Int): Boolean {
    if (controlsVisible) return false
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_DOWN -> true
        else -> false
    }
}

fun channelPickAction(currentChannelId: Int, pickedChannelId: Int): ChannelPickAction =
    if (currentChannelId == pickedChannelId) {
        ChannelPickAction.CLOSE_DRAWER
    } else {
        ChannelPickAction.TUNE
    }

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
