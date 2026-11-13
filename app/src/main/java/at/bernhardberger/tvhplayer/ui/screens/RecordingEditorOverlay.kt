package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import at.bernhardberger.tvhplayer.ui.components.LocalBrowseContentLayering
import at.bernhardberger.tvhplayer.ui.components.browseDetailsViewport

@Composable
internal fun RecordingEditorOverlay(
    owner: Any,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layering = LocalBrowseContentLayering.current
    val pageKey = remember { Any() }
    val opacity = remember { mutableFloatStateOf(1f) }
    DisposableEffect(layering, owner) {
        layering?.details?.set(pageKey, opacity)
        onDispose { layering?.details?.remove(pageKey) }
    }
    Box(
        modifier
            .browseDetailsViewport()
            .fillMaxSize()
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
    ) {
        content()
    }
}
