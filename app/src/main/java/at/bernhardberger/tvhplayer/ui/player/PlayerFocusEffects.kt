package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.playerRootFocusRequired

@Composable
internal fun PlayerRootFocusEffect(
    foregroundLayer: PlayerForegroundLayer,
    rootFocusRequester: FocusRequester,
) {
    LaunchedEffect(foregroundLayer, rootFocusRequester) {
        if (playerRootFocusRequired(foregroundLayer)) {
            rootFocusRequester.requestFocus()
        }
    }
}
