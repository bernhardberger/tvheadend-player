package at.bernhardberger.tvhplayer.touch.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** Keep the phone list from growing and clamping its scroll while the shell hides its navigation bar. */
@Composable
internal fun PhoneBrowseViewport(enabled: Boolean, active: Boolean, content: @Composable (Modifier) -> Unit) {
    if (!enabled) {
        content(Modifier)
        return
    }
    val density = LocalDensity.current
    val inset = WindowInsets.safeDrawing.getBottom(density)
    var height by rememberSaveable(LocalWindowInfo.current.containerSize, density, inset) { mutableStateOf(0f) }
    BoxWithConstraints {
        if (active && (height == 0f || maxHeight.value < height)) {
            SideEffect { height = maxHeight.value }
        }
        content(Modifier.heightIn(max = if (height > 0f) height.dp else maxHeight))
    }
}
