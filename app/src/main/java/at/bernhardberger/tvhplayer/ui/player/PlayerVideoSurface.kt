package at.bernhardberger.tvhplayer.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import at.bernhardberger.tvhplayer.settings.AspectRatioMode

@OptIn(UnstableApi::class)
@Composable
fun PlayerVideoSurface(
    player: Player,
    aspectRatio: AspectRatioMode,
    debugVideoBackdropVisible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    controllerAutoShow = false
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.resizeMode = when (aspectRatio) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FORCE_16_9,
                    AspectRatioMode.FORCE_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = Modifier.then(
                when (aspectRatio) {
                    AspectRatioMode.FIT -> Modifier.fillMaxSize()
                    AspectRatioMode.FORCE_16_9 -> Modifier.aspectRatio(16f / 9f)
                    AspectRatioMode.FORCE_4_3 -> Modifier.aspectRatio(4f / 3f)
                }
            ),
        )
        DebugVideoBackdrop(
            visible = debugVideoBackdropVisible,
            modifier = Modifier.matchParentSize(),
        )
    }
}
