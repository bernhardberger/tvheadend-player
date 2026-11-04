package at.bernhardberger.tvhplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as AndroidKeyEvent
import at.bernhardberger.tvhplayer.core.PlayerBackKeyCycle
import at.bernhardberger.tvhplayer.core.PlayerBackKeyResult

/** Dialogs disable hold-to-stop; their BackHandler receives one Back on release. */
internal val LocalPlayerModalRegistry = staticCompositionLocalOf<MutableMap<Any, Unit>?> { null }

@Composable
internal fun RegisterPlayerModal() {
    val registry = LocalPlayerModalRegistry.current
    val key = remember { Any() }
    DisposableEffect(registry) {
        registry?.set(key, Unit)
        onDispose { registry?.remove(key) }
    }
}

internal fun PlayerBackKeyCycle.intercept(
    event: KeyEvent,
    holdAllowed: Boolean,
    onBack: () -> Unit,
    stopAndClose: () -> Unit,
    activityKeyCycleOwner: at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner? = null,
): Boolean {
    val native = event.nativeKeyEvent
    if (native.keyCode != AndroidKeyEvent.KEYCODE_BACK) return false
    if (native.action == AndroidKeyEvent.ACTION_UP && native.isCanceled) {
        onKey(false, 0, false, canceled = true, holdAllowed = false)
        return true
    }
    when (onKey(native.action == AndroidKeyEvent.ACTION_DOWN, native.repeatCount,
        native.isLongPress, native.isCanceled, holdAllowed = holdAllowed)) {
        PlayerBackKeyResult.BACK -> onBack()
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
