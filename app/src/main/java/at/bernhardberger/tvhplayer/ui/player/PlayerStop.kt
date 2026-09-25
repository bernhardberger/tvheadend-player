package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import at.bernhardberger.tvhplayer.core.playerStopKeyClosesScreen
import kotlinx.coroutines.flow.Flow

/**
 * Every way a player screen closes itself goes through [close], which calls the route's
 * close at most once. A screen that is closing (for example still composed while its exit
 * transition fades it out) therefore never navigates a second time.
 */
@Stable
internal class PlayerClose(private val onClose: () -> Unit) {
    var requested: Boolean = false
        private set

    fun close() {
        if (requested) return
        requested = true
        onClose()
    }
}

@Composable
internal fun rememberPlayerClose(onClose: () -> Unit): PlayerClose {
    val currentOnClose by rememberUpdatedState(onClose)
    return remember { PlayerClose { currentOnClose() } }
}

/**
 * A player screen's entry step, decided once while the screen first composes, so before
 * any of its effects (playback start, warm reuse, retune, route restore) can run.
 * [enter] is the runtime's `enterPlayerScreen`: when a user stop is the latest playback
 * event it returns null, and the screen, which must then start and restore nothing,
 * closes through [close]; otherwise opening the screen is noted as viewing intent and
 * [enter] returns its generation.
 */
@Composable
internal fun rememberPlayerEntry(enter: () -> Long?, close: PlayerClose): Long? {
    val entry = remember { enter() }
    if (entry == null) {
        LaunchedEffect(close) { close.close() }
    }
    return entry
}

/**
 * A media-session Stop (Assistant, Now-playing card, remote Stop key) has already stopped
 * playback in the runtime, which only delivers it while no newer playback was requested.
 * The showing player then closes the way its Stop button closes after stopping; a screen
 * that already requested its close ignores it.
 */
@Composable
internal fun CloseOnSessionStop(sessionStops: Flow<Unit>, close: PlayerClose) {
    LaunchedEffect(sessionStops, close) {
        sessionStops.collect { close.close() }
    }
}

/**
 * The remote Stop key on a player screen without an active target (nothing the media
 * session could stop): the first key down runs the screen's Stop button path and starts a
 * key cycle, so the screen's key-cycle suppression consumes the repeats and the key up.
 * With an active target the key is left to the media session.
 */
internal fun handlePlayerStopKeyWithoutTarget(
    event: KeyEvent,
    hasActiveTarget: Boolean,
    beginKeyCycle: (Int) -> Unit,
    stopAndClose: () -> Unit,
): Boolean {
    val keyCode = event.nativeKeyEvent.keyCode
    if (event.type != KeyEventType.KeyDown ||
        !playerStopKeyClosesScreen(keyCode, event.nativeKeyEvent.repeatCount, hasActiveTarget)
    ) {
        return false
    }
    beginKeyCycle(keyCode)
    stopAndClose()
    return true
}
