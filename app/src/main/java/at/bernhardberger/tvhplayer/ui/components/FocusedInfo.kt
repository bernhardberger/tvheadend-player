package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import at.bernhardberger.tvhplayer.ui.BrowseMotionPolicy
import kotlinx.coroutines.delay

/** How long focus must rest after a held key before the focused card's info returns. */
internal const val FocusedInfoSettleMs = 250L

/**
 * What follows focus, from one focused card or programme to the next: a plain linear crossfade, with
 * the backdrop's duration, so a picture and its text change together.
 */
internal fun focusedInfoSwap(): ContentTransform {
    val fade = tween<Float>(BrowseMotionPolicy.focusChangeFadeMs, easing = LinearEasing)
    return ContentTransform(fadeIn(fade), fadeOut(fade), sizeTransform = null)
}

/**
 * [value], or null while [held] and for [settleMs] after it is released. Each single press
 * shows its value at once, so the info swaps; a held key shows and loads nothing per
 * card until focus rests where it stopped.
 */
@Composable
internal fun <T : Any> rememberUnlessHeld(value: T?, held: Boolean, settleMs: Long = FocusedInfoSettleMs): T? {
    var rested by remember { mutableStateOf(true) }
    LaunchedEffect(held) {
        if (held) rested = false
        else if (!rested) {
            delay(settleMs)
            rested = true
        }
    }
    return value.takeIf { !held && rested }
}

/** Whether this Left or Right event is a key held down and repeating; null for any other key. */
internal fun KeyEvent.horizontalHold(): Boolean? =
    if (key == Key.DirectionLeft || key == Key.DirectionRight) {
        type == KeyEventType.KeyDown && nativeKeyEvent.repeatCount > 0
    } else {
        null
    }

/**
 * Ends a hold whose release may never arrive: another window took the keys, or the row that saw
 * the press left the screen. Otherwise the info would stay hidden until the next Left or Right.
 */
@Composable
internal fun EndHoldOnInterruption(onHold: (Boolean) -> Unit) {
    val hold by rememberUpdatedState(onHold)
    val window = LocalWindowInfo.current
    LaunchedEffect(window) {
        snapshotFlow { window.isWindowFocused }.collect { if (!it) hold(false) }
    }
    DisposableEffect(Unit) { onDispose { hold(false) } }
}
