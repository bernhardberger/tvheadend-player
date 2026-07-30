package at.bernhardberger.tvhplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import at.bernhardberger.tvhplayer.core.playerParentConsumesRecoveryKey

@Composable
internal fun ApplianceLaunchRecoveryHost(
    visible: Boolean,
    actionable: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var consumeBackKeyUp by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (
                event.key == Key.Back &&
                event.type == KeyEventType.KeyUp &&
                consumeBackKeyUp
            ) {
                consumeBackKeyUp = false
                return@onPreviewKeyEvent true
            }
            if (!visible) return@onPreviewKeyEvent false
            if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                if (!consumeBackKeyUp) {
                    consumeBackKeyUp = true
                    onBack()
                }
                return@onPreviewKeyEvent true
            }
            if (actionable) {
                playerParentConsumesRecoveryKey(event.nativeKeyEvent.keyCode)
            } else {
                true
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (visible) {
                        Modifier.semantics { hideFromAccessibility() }
                    } else {
                        Modifier
                    }
                ),
            content = content,
        )
        overlay()
    }
}
