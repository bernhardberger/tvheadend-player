package at.bernhardberger.tvhplayer.touch.player

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.player.surfaceFrameAspect

/**
 * The runtime's player in the face's picture box: no controller, the picture at its display
 * aspect inside the box, and black until the runtime says the picture is visible (a SurfaceView
 * can't be hidden by its view's alpha). The screen stays on by [keepPlayerScreenOn].
 */
@OptIn(UnstableApi::class)
@Composable
internal fun TouchVideoSurface(
    player: ExoPlayer,
    videoVisible: Boolean,
    modifier: Modifier = Modifier,
    onView: (PlayerView?) -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var keepScreenOn by remember { mutableStateOf(false) }
    // The last presented picture's aspect stays while a new channel starts.
    var aspect by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(player, lifecycle, videoVisible) {
        fun update() {
            keepScreenOn = keepPlayerScreenOn(
                foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                videoVisible = videoVisible,
                playing = player.isPlaying,
                hasVideo = player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO),
                failed = player.playerError != null,
            )
            if (videoVisible) pictureAspect(player)?.let { aspect = it }
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = update()
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        update()
        onDispose {
            player.removeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setKeepContentOnPlayerReset(true)
                    setEnableComposeSurfaceSyncWorkaround(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    isFocusable = false
                    isClickable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }.also(onView)
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = keepScreenOn
            },
            onRelease = { view ->
                view.keepScreenOn = false
                view.player = null
                onView(null)
            },
            modifier = aspect?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize(),
        )
        if (!videoVisible) Box(Modifier.matchParentSize().background(Color.Black))
    }
}

/** The decoded size, else the input format's (tunnelled playback may report no decoded size). */
@OptIn(UnstableApi::class)
internal fun pictureAspect(player: ExoPlayer): Float? {
    val size = player.videoSize
    val format = player.videoFormat
    return when {
        size.width > 0 && size.height > 0 ->
            surfaceFrameAspect(AspectRatioMode.FIT, size.width, size.height, size.pixelWidthHeightRatio)
        format != null && format.width > 0 && format.height > 0 ->
            surfaceFrameAspect(AspectRatioMode.FIT, format.width, format.height, format.pixelWidthHeightRatio)
        else -> null
    }
}

/** The TV's rule: the screen stays on only while a visible picture plays without an error. */
internal fun keepPlayerScreenOn(
    foreground: Boolean,
    videoVisible: Boolean,
    playing: Boolean,
    hasVideo: Boolean,
    failed: Boolean,
): Boolean = foreground && videoVisible && playing && hasVideo && !failed
