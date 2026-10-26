package at.bernhardberger.tvhplayer.ui.player

import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import at.bernhardberger.tvhplayer.core.shouldKeepPlaybackScreenOn
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateRequest
import at.bernhardberger.tvhplayer.settings.AspectRatioMode

@OptIn(UnstableApi::class)
@Composable
fun PlayerVideoSurface(
    player: Player,
    aspectRatio: AspectRatioMode,
    videoVisible: Boolean,
    modifier: Modifier = Modifier,
    debugVideoBackdropVisible: Boolean = false,
    /** How long the black cover takes to clear a first picture; 0 removes it at once. */
    coverFadeMillis: Int = 0,
    /** Display rate to ask for while the player is shown; one without a rate asks for none. */
    frameRate: SurfaceFrameRateRequest = SurfaceFrameRateRequest(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val surfaceFrameRate = remember { VideoSurfaceFrameRate() }
    var keepScreenOn by remember(player, lifecycle, videoVisible) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle, videoVisible) {
        fun updateScreenOn() {
            // A retained surface or paused READY player is not active video playback.
            keepScreenOn = shouldKeepPlaybackScreenOn(
                isForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                isVideoVisible = videoVisible,
                isPlaying = player.isPlaying,
                hasSelectedVideo = player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO),
                hasError = player.playerError != null,
            )
        }
        val playerListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = updateScreenOn()
        }
        val lifecycleObserver = LifecycleEventObserver { _, _ -> updateScreenOn() }
        player.addListener(playerListener)
        lifecycle.addObserver(lifecycleObserver)
        updateScreenOn()
        onDispose {
            player.removeListener(playerListener)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val sizeListener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
        }
        player.addListener(sizeListener)
        videoSize = player.videoSize
        onDispose { player.removeListener(sizeListener) }
    }
    // Null fills the screen and lets the view fit the picture; a ratio lays the surface out at it.
    val frameAspect = surfaceFrameAspect(
        aspectRatio, videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio,
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                    controllerAutoShow = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = false
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    this.keepScreenOn = false
                    (videoSurfaceView as? SurfaceView)?.let(surfaceFrameRate::attach)
                }
            },
            update = { view ->
                view.player = player
                view.keepScreenOn = keepScreenOn
                surfaceFrameRate.request(frameRate)
                view.alpha = if (videoVisible) 1f else 0f
                view.resizeMode = if (frameAspect == null) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = (if (frameAspect == null) Modifier.fillMaxSize() else Modifier.aspectRatio(frameAspect))
                .testTag("video-surface"),
            onRelease = { view ->
                view.player = null
                view.keepScreenOn = false
                surfaceFrameRate.release()
            },
        )
        // View alpha does not hide a SurfaceView's content: cover the old picture until the
        // current target renders its first frame.
        VideoCover(videoVisible, coverFadeMillis, Modifier.matchParentSize())
        DebugVideoBackdrop(
            visible = debugVideoBackdropVisible,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * Covers the surface the moment [videoVisible] turns false. Once the first frame is visible the
 * cover holds for [PlayerMotion.PictureHoldMs], then clears over [fadeMillis] (M3 standard easing)
 * above the presented picture, or at once for 0.
 */
@Composable
internal fun VideoCover(videoVisible: Boolean, fadeMillis: Int, modifier: Modifier) {
    val alpha = remember { Animatable(if (videoVisible) 0f else 1f) }
    LaunchedEffect(videoVisible) {
        if (videoVisible && fadeMillis > 0) {
            alpha.animateTo(0f, tween(fadeMillis, PlayerMotion.PictureHoldMs, PlayerMotion.Standard))
        } else {
            alpha.snapTo(if (videoVisible) 0f else 1f)
        }
    }
    val fading by remember { derivedStateOf { alpha.value > 0f } }
    if (!videoVisible || fadeMillis > 0 && fading) {
        Box(
            modifier
                .graphicsLayer { this.alpha = if (videoVisible) alpha.value else 1f }
                .background(Color.Black)
                .testTag("video-cover"),
        )
    }
}

/**
 * How long the cover takes to clear a first picture: [PlayerMotion.EntryPictureMs] for the first
 * one while the player is shown, [PlayerMotion.ZapPictureMs] for a later zap, and 0 when [instant]
 * (no animations, or the startup screen and its reveal own the first picture).
 */
@Composable
internal fun rememberVideoCoverFadeMillis(playerShown: Boolean, videoVisible: Boolean, instant: Boolean): Int {
    val visit = remember { PlayerPictureVisit() }
    SideEffect {
        if (!playerShown) visit.pictures = 0 else if (videoVisible && !visit.visible) visit.pictures++
        visit.visible = videoVisible
    }
    return when {
        instant -> 0
        playerShown && visit.pictures == 0 -> PlayerMotion.EntryPictureMs
        else -> PlayerMotion.ZapPictureMs
    }
}

/** First pictures shown since the player was entered, read in the frame the next one appears. */
private class PlayerPictureVisit {
    var pictures = 0
    var visible = false
}
