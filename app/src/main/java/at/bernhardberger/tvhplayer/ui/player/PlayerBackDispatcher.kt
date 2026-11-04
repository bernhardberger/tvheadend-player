package at.bernhardberger.tvhplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as AndroidKeyEvent
import at.bernhardberger.tvhplayer.core.PlayerBackKeyCycle
import at.bernhardberger.tvhplayer.core.PlayerBackKeyResult

/** Ordered registrations preserve the top page/dialog's identity even when it cannot go Back. */
internal class PlayerModalBackOwner(val enabled: State<Boolean>)

internal val LocalPlayerModalRegistry = staticCompositionLocalOf<MutableList<PlayerModalBackOwner>?> { null }

@Composable
internal fun RegisterPlayerModal(backEnabled: Boolean = true, identity: Any? = Unit) {
    val registry = LocalPlayerModalRegistry.current
    val enabled = rememberUpdatedState(backEnabled)
    val owner = remember(identity) { PlayerModalBackOwner(enabled) }
    DisposableEffect(registry, owner) {
        registry?.add(owner)
        onDispose { registry?.remove(owner) }
    }
}

internal fun PlayerBackKeyCycle.intercept(
    event: KeyEvent,
    holdAllowed: Boolean,
    onBack: () -> Unit,
    stopAndClose: () -> Unit,
    activityKeyCycleOwner: at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner? = null,
    backOwner: Any = Unit,
): Boolean {
    val native = event.nativeKeyEvent
    if (native.keyCode != AndroidKeyEvent.KEYCODE_BACK) return false
    if (native.action == AndroidKeyEvent.ACTION_UP && native.isCanceled) {
        onKey(false, 0, false, canceled = true, holdAllowed = false)
        return true
    }
    when (onKey(native.action == AndroidKeyEvent.ACTION_DOWN, native.repeatCount,
        native.isLongPress, native.isCanceled, holdAllowed = holdAllowed, owner = backOwner)) {
        PlayerBackKeyResult.BACK -> if (backOwner !is PlayerModalBackOwner || backOwner.enabled.value) onBack()
        PlayerBackKeyResult.HOLD -> {
            activityKeyCycleOwner?.consumeUntilUp(AndroidKeyEvent.KEYCODE_BACK)
            stopAndClose()
        }
        PlayerBackKeyResult.NONE -> Unit
    }
    return true
}

@Composable
internal fun PlayerBackHandler(onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    BackHandler(onBack = currentOnBack)
}
