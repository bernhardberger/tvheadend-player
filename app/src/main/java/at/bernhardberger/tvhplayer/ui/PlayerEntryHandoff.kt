package at.bernhardberger.tvhplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The handoff of the browse shell an overlay is shown in; none outside an app shell. */
internal val LocalPlayerEntryHandoff = staticCompositionLocalOf<PlayerEntryHandoff?> { null }

/**
 * Closes the overlay that started playback (details, a context menu, the resume choice) only once
 * the player has replaced browse, so the overlay fades out with the browse shell instead of
 * uncovering its screen first. Outside an app shell it closes at once.
 *
 * Call it from the overlay's own composition: a close it defers is dropped when that composition
 * leaves, so an overlay dismissed or replaced meanwhile is never closed by an older one's request.
 */
@Composable
internal fun rememberCloseBehindPlayer(): (close: () -> Unit) -> Unit {
    val handoff = LocalPlayerEntryHandoff.current
    val overlay = remember(handoff) { Any() }
    DisposableEffect(handoff, overlay) { onDispose { handoff?.dismissed(overlay) } }
    return remember(handoff, overlay) {
        val defer: (() -> Unit) -> Unit = { close -> if (handoff == null) close() else handoff.closeBehindPlayer(overlay, close) }
        defer
    }
}

/**
 * Whether a player request from this shell is pending; none outside an app shell. An overlay that
 * stays up for the player consumes its keys meanwhile, so no other action or confirmation opens.
 */
@Composable
internal fun rememberPlayerEntryPending(): () -> Boolean {
    val handoff = LocalPlayerEntryHandoff.current
    return remember(handoff) { { handoff?.pending == true } }
}

/**
 * The player request from browse and the overlay closes waiting on it. One request runs at a time:
 * activations that arrive while it is pending are consumed. A close runs when no request is pending
 * and browse is still shown, so a request that opens no player closes its overlay after all; one
 * that opens the player leaves the overlay to fade and go with its shell.
 */
@Stable
internal class PlayerEntryHandoff {
    /** True from the activation until its request has opened its player or not. */
    var pending by mutableStateOf(false)
        private set
    private val closes = mutableStateListOf<DeferredClose>()

    /** The outgoing overlay stays up through the request and the shell's fade into the player. */
    val departing: Boolean get() = closes.isNotEmpty()

    private class DeferredClose(val overlay: Any, val close: () -> Unit)

    fun closeBehindPlayer(overlay: Any, close: () -> Unit) {
        if (!pending) closes += DeferredClose(overlay, close)
    }

    /** The overlay left composition: what it deferred no longer applies. */
    fun dismissed(overlay: Any) {
        closes.removeAll { it.overlay === overlay }
    }

    /** Runs [request] in [scope] unless another is pending, in which case this activation is consumed. */
    fun request(scope: CoroutineScope, request: suspend () -> Unit) {
        if (pending) return
        pending = true
        scope.launch {
            try {
                request()
            } finally {
                pending = false
            }
        }
    }

    internal fun closesDue(playerShown: Boolean): Boolean = closes.isNotEmpty() && !pending && !playerShown

    internal fun runCloses() {
        val due = closes.toList()
        closes.clear()
        due.forEach { it.close() }
    }

    internal fun dropCloses() = closes.clear()
}

/** The browse shell's [content] with [handoff]'s overlay closes; they go with the shell when it leaves. */
@Composable
internal fun PlayerEntryOverlays(handoff: PlayerEntryHandoff, playerShown: () -> Boolean, content: @Composable () -> Unit) {
    LaunchedEffect(handoff) {
        snapshotFlow { handoff.closesDue(playerShown()) }.collect { due -> if (due) handoff.runCloses() }
    }
    DisposableEffect(handoff) { onDispose { handoff.dropCloses() } }
    CompositionLocalProvider(LocalPlayerEntryHandoff provides handoff, content = content)
}
