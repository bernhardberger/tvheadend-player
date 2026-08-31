package at.bernhardberger.tvhplayer.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

@Composable
internal fun PlayerBackHandler(onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    BackHandler(onBack = currentOnBack)
}
