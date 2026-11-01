package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester

/**
 * Focus for the action a screen opens on. As a focused action leaves, a TV hands focus straight to
 * the first focusable, in the frame it attaches, and a request from an effect can run as early. A
 * TV button's highlight isn't listening yet then, so it would hold focus but look unfocused.
 * Keeping it out of reach for that frame lets this request land with it listening.
 */
@Composable
internal fun arrivalFocus(requester: FocusRequester = remember { FocusRequester() }): Modifier {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        ready = true
        requester.requestFocus()
    }
    return Modifier.focusProperties { canFocus = ready }.focusRequester(requester)
}
