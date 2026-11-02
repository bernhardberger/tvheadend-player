package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import kotlinx.coroutines.delay

/** How long focus must rest after a held key before the focused card's info returns. */
internal const val FocusedInfoSettleMs = 250L
private const val FocusedInfoOutMs = 75
private const val FocusedInfoInDelayMs = 50

/**
 * The info above an immersive row, from one focused card to the next: out quickly, in a little
 * slower. Both sides are bottom-aligned texts of different lengths, so an overlap would stack
 * two titles and descriptions; the new side waits for the old one.
 */
internal fun focusedInfoSwap(): ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(tween(PlayerMotion.ShortMs, delayMillis = FocusedInfoInDelayMs, easing = PlayerMotion.EmphasizedDecelerate)),
        initialContentExit = fadeOut(tween(FocusedInfoOutMs, easing = PlayerMotion.StandardAccelerate)),
        sizeTransform = null,
    )

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
