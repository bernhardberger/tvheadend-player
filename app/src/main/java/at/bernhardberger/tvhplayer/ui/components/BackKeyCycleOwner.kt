package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import at.bernhardberger.tvhplayer.core.BackKeyCycle

/** Disabled owners forget a press even if its release is delivered somewhere else. */
@Composable
internal fun rememberBackKeyCycle(enabled: Boolean = true): BackKeyCycle {
    val cycle = remember { BackKeyCycle() }
    SideEffect { if (!enabled) cycle.reset() }
    return cycle
}

/** Observe the whole owner's subtree, not just its first focusable child. */
internal fun Modifier.backKeyCycleFocus(vararg cycles: BackKeyCycle): Modifier =
    onFocusChanged { state -> if (!state.hasFocus) cycles.forEach { it.reset() } }.focusGroup()
