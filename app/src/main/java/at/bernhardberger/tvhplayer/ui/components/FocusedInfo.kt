package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import kotlinx.coroutines.flow.collectLatest

/** How long focus must rest after a held key before the focused card's info returns. */
internal const val FocusedInfoSettleMs = 250L

private const val FocusedInfoOutDelayMs = 30
private const val FocusedInfoOutMs = 120
private const val FocusedInfoInDelayMs = 120
private const val FocusedInfoInMs = 180
private const val FocusedInfoTotalMs = FocusedInfoInDelayMs + FocusedInfoInMs

/**
 * The text that follows focus, from one focused card or programme to the next. Between a crossfade,
 * where two texts stack at half strength and smear, and an out-then-in, which blinks through nothing:
 * the old text is nearly gone when the new one starts, so they meet only faintly.
 */
internal fun focusedInfoSwap(): ContentTransform =
    ContentTransform(
        fadeIn(tween(FocusedInfoInMs, delayMillis = FocusedInfoInDelayMs, easing = LinearEasing)),
        fadeOut(tween(FocusedInfoOutMs, delayMillis = FocusedInfoOutDelayMs, easing = LinearEasing)),
        sizeTransform = null,
    )

/**
 * The picture beside that text, ending with it. A new picture fades in over the old one, which stays
 * until it is covered, so there is no dip to the background and no two ghosts of a picture.
 */
internal fun focusedPictureSwap(): ContentTransform =
    ContentTransform(
        fadeIn(tween(FocusedInfoTotalMs, easing = LinearEasing)),
        fadeOut(tween(durationMillis = 1, delayMillis = FocusedInfoTotalMs)),
        sizeTransform = null,
    )

/**
 * [value], or null while [held] and for [settleMs] after it is released. Each single press
 * shows its value at once, so the info swaps; a held key shows and loads nothing per
 * card until focus rests where it stopped.
 */
@Composable
internal fun <T : Any> rememberUnlessHeld(value: T?, held: Boolean, settleMs: Long = FocusedInfoSettleMs): T? =
    value.takeIf { rememberRested(held, settleMs) }

/**
 * [value], or the one last shown while [held] and for [settleMs] after it is released. For the
 * browse screens, where the backdrop stays too and a blank would leave a picture without its text.
 * The default rests when the backdrop does, so both change together.
 */
@Composable
internal fun <T> rememberHeldBack(value: T, held: Boolean, settleMs: Long = BrowseMotionPolicy.backdropSettleMs): T {
    val rested = rememberRested(held, settleMs)
    val last = remember { object { var shown = value } }
    val shown = if (rested) value else last.shown
    SideEffect { last.shown = shown }
    return shown
}

/**
 * [rememberHeldBack] for a value read outside composition, as [FocusBackdrop] reads its target, so
 * focus steps don't recompose the caller. [held] is read with the value: a key's first repeat sets
 * it before focus moves, so that step is held back too.
 */
@Composable
internal fun <T> rememberHeldBackReader(
    value: () -> T,
    held: () -> Boolean,
    settleMs: Long = BrowseMotionPolicy.backdropSettleMs,
): () -> T {
    val currentValue by rememberUpdatedState(value)
    val currentHeld by rememberUpdatedState(held)
    var rested by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        snapshotFlow { currentHeld() }.collectLatest { isHeld ->
            if (isHeld) rested = false
            else if (!rested) {
                delay(settleMs)
                rested = true
            }
        }
    }
    val last = remember { object { var shown = value() } }
    return remember { { if (!currentHeld() && rested) currentValue().also { last.shown = it } else last.shown } }
}

@Composable
private fun rememberRested(held: Boolean, settleMs: Long): Boolean {
    var rested by remember { mutableStateOf(true) }
    LaunchedEffect(held) {
        if (held) rested = false
        else if (!rested) {
            delay(settleMs)
            rested = true
        }
    }
    return !held && rested
}

/** Whether this Left or Right event is a key held down and repeating; null for any other key. */
internal fun KeyEvent.horizontalHold(): Boolean? =
    if (key == Key.DirectionLeft || key == Key.DirectionRight) repeating() else null

/** The same for all four directions, for what moves both ways. */
internal fun KeyEvent.directionalHold(): Boolean? =
    if (key == Key.DirectionUp || key == Key.DirectionDown) repeating() else horizontalHold()

private fun KeyEvent.repeating() = type == KeyEventType.KeyDown && nativeKeyEvent.repeatCount > 0

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
