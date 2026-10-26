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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
    var geometryVersion by remember(player) { mutableIntStateOf(0) }
    val inputPictureSize = remember(player, geometryVersion, videoVisible) {
        val format = (player as? ExoPlayer)?.videoFormat
        // Media3 1.11.1 derives tunnelled output geometry from the input format (no codec crop).
        // Match MediaCodecVideoRenderer.onOutputFormatChanged's applied rotation and pixel ratio.
        if (format != null && format.width > 0 && format.height > 0) {
            val rotated = format.rotationDegrees == 90 || format.rotationDegrees == 270
            VideoSize(
                if (rotated) format.height else format.width,
                if (rotated) format.width else format.height,
                if (rotated) 1f / format.pixelWidthHeightRatio else format.pixelWidthHeightRatio,
            )
        } else {
            VideoSize.UNKNOWN
        }
    }
    var hasCurrentSize by remember(player, videoVisible) {
        // A normal tunnelled size callback precedes the first frame and thus the reveal. Read
        // again in this composition, rather than requiring a callback after listener replacement.
        val decoded = player.videoSize
        mutableStateOf(
            videoVisible && inputPictureSize.width > 0 && inputPictureSize.height > 0 &&
                decoded.width == inputPictureSize.width && decoded.height == inputPictureSize.height &&
                decoded.pixelWidthHeightRatio == inputPictureSize.pixelWidthHeightRatio,
        )
    }
    DisposableEffect(player, videoVisible) {
        val sizeListener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (videoVisible && size.width > 0 && size.height > 0) hasCurrentSize = true
                geometryVersion++
            }
        }
        val exoPlayer = player as? ExoPlayer
        val formatListener = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                // A fallback reveal with no geometry must also recover when only the input
                // format arrives: tunnelled size callbacks can remain absent or be unchanged.
                // Once decoded geometry arrives, input formats can be ahead of the shown frame.
                if (!hasCurrentSize) geometryVersion++
            }
        }
        player.addListener(sizeListener)
        exoPlayer?.addAnalyticsListener(formatListener)
        geometryVersion++
        onDispose {
            player.removeListener(sizeListener)
            exoPlayer?.removeAnalyticsListener(formatListener)
        }
    }
    // The target's mode changes before its picture arrives. Size callbacks (including UNKNOWN
    // during replacement) are not target-scoped, so neither may reshape the retained picture.
    // Use the existing presentation reveal as the commit point, without changing its cover rules.
    // Re-read the player there too: a same-size target need not send another size callback.
    val pictureSize = remember(player, geometryVersion, videoVisible, hasCurrentSize) {
        val exoPlayer = player as? ExoPlayer
        // Media3 1.11.1 publishes tunnelled sizes only from processed-frame callbacks, which
        // may lag the cover's fallback reveal. Use input geometry until the decoded size matches
        // at reveal or a valid size callback arrives afterward. Then decoded sizes keep in-stream
        // aspect changes aligned with the shown frame rather than the buffered input format.
        if (hasCurrentSize || exoPlayer?.isTunnelingEnabled != true) {
            player.videoSize
        } else {
            // With no current tunnelled format, even a non-zero reported size may be stale.
            inputPictureSize
        }
    }
    var presentedFrameAspect by remember(player) { mutableStateOf<Float?>(null) }
    // Fit non-nominal pictures at their own display aspect here too. PlayerView's inner FIT
    // layout reads the same potentially stale VideoSize independently; FILL ignores that ratio.
    val pictureAspect = if (pictureSize.width > 0 && pictureSize.height > 0) {
        (pictureSize.width * pictureSize.pixelWidthHeightRatio / pictureSize.height)
            .takeIf { it > 0f && it.isFinite() }
    } else {
        null
    }
    val frameAspect = if (videoVisible) {
        surfaceFrameAspect(aspectRatio, pictureSize.width, pictureSize.height, pictureSize.pixelWidthHeightRatio)
            ?: pictureAspect ?: presentedFrameAspect
    } else {
        presentedFrameAspect
    }
    SideEffect { if (videoVisible) presentedFrameAspect = frameAspect }
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
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
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
