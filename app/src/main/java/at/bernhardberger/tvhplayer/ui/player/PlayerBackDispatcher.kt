package at.bernhardberger.tvhplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

@Composable
internal fun rememberPlayerBackDispatcher(onBack: () -> Unit): (KeyEvent) -> Boolean {
    val currentOnBack by rememberUpdatedState(onBack)
    var hardwareCycleActive by remember { mutableStateOf(false) }

    BackHandler {
        if (!hardwareCycleActive) currentOnBack()
    }

    return { event ->
        if (event.key != Key.Back) {
            false
        } else {
            when (event.type) {
                KeyEventType.KeyDown -> hardwareCycleActive = true
                KeyEventType.KeyUp -> {
                    val applyBack = hardwareCycleActive
                    hardwareCycleActive = false
                    if (applyBack) currentOnBack()
                }
            }
            true
        }
    }
}
