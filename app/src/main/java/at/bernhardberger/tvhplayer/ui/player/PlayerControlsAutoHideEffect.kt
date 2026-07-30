package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

@Composable
internal fun PlayerControlsAutoHideEffect(
    eligible: Boolean,
    interactionToken: Int,
    timeoutMillis: Long,
    onHide: () -> Unit,
) {
    val currentOnHide by rememberUpdatedState(onHide)

    LaunchedEffect(eligible, interactionToken, timeoutMillis) {
        if (!eligible) return@LaunchedEffect
        delay(timeoutMillis)
        currentOnHide()
    }
}
