package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.getBoundsInRoot
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Format
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h600dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerVideoSurfaceCoverTest {
    @get:Rule val compose = createComposeRule()
    private val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
    private lateinit var view: View

    @After fun release() = player.release()

    @Test fun opaqueCoverHidesTheSurfaceUntilTheTargetFrameIsVisible() {
        val visible = mutableStateOf(false)
        compose.setContent { PlayerVideoSurface(player, AspectRatioMode.FIT, videoVisible = visible.value) }

        compose.onNodeWithTag("video-cover").assertIsDisplayed()

        visible.value = true
        compose.waitForIdle()
        compose.onNodeWithTag("video-cover").assertDoesNotExist()

        visible.value = false
        compose.waitForIdle()
        compose.onNodeWithTag("video-cover").assertIsDisplayed()
    }

    @Test fun autoLaysAnSdBroadcastPictureOutAtItsNominalShapeAndOtherPicturesAtTheirOwn() {
        var report: (VideoSize) -> Unit = {}
        var size = VideoSize.UNKNOWN
        val reporting = object : ForwardingPlayer(player) {
            override fun getVideoSize() = size
            override fun addListener(listener: Player.Listener) {
                report = listener::onVideoSizeChanged
                super.addListener(listener)
            }
        }
        compose.setContent {
            view = LocalView.current
            PlayerVideoSurface(reporting, AspectRatioMode.FIT, videoVisible = true)
        }
        // The window is 960x600, not 16:9: a snapped 960x540 frame differs from the full screen, and
        // only RESIZE_MODE_FILL stretches the picture over that frame (FIT would pillarbox it again).
        fun show(next: VideoSize): DpRect {
            compose.runOnIdle { size = next; report(next) }
            compose.waitForIdle()
            return compose.onNodeWithTag("video-surface").getBoundsInRoot()
        }

        // 720x576 with 16:11 pixels measures 1.82:1 but is 16:9.
        val wide = show(VideoSize(720, 576, 0, 16f / 11f))
        assertEquals(960f, wide.width.value, 0.5f)
        assertEquals(540f, wide.height.value, 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView().resizeMode)
        // 12:11 pixels are a 4:3 programme: pillarboxed to 800x600.
        val standard = show(VideoSize(720, 576, 0, 12f / 11f))
        assertEquals(800f, standard.width.value, 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView().resizeMode)
        // HD fits at its own aspect. Unknown size retains that shape until geometry arrives.
        val hd = show(VideoSize(1920, 1080, 0, 1f))
        assertEquals(960f, hd.width.value, 0.5f)
        assertEquals(540f, hd.height.value, 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView().resizeMode)
        assertEquals(540f, show(VideoSize.UNKNOWN).height.value, 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView().resizeMode)
    }

    private fun playerView(): PlayerView = compose.runOnIdle {
        fun find(group: ViewGroup): PlayerView? = (0 until group.childCount).firstNotNullOfOrNull {
            when (val child = group.getChildAt(it)) {
                is PlayerView -> child
                is ViewGroup -> find(child)
                else -> null
            }
        }
        find(view as ViewGroup)!!
    }

    @Test fun zapKeepsThePresentedShapeUntilTheNextPictureIsRevealed() {
        val visible = mutableStateOf(true)
        val mode = mutableStateOf(AspectRatioMode.FORCE_4_3)
        var size = VideoSize(720, 576, 0, 16f / 11f)
        val listeners = mutableSetOf<Player.Listener>()
        val reporting = object : ForwardingPlayer(player) {
            override fun getVideoSize() = size
            override fun addListener(listener: Player.Listener) { listeners += listener }
            override fun removeListener(listener: Player.Listener) { listeners -= listener }
        }
        compose.setContent {
            view = LocalView.current
            PlayerVideoSurface(reporting, mode.value, videoVisible = visible.value)
        }
        fun assertShape(width: Float, height: Float, resize: Int) {
            val bounds = compose.onNodeWithTag("video-surface").getBoundsInRoot()
            assertEquals(width, bounds.width.value, 0.5f)
            assertEquals(height, bounds.height.value, 0.5f)
            assertEquals(resize, playerView().resizeMode)
        }
        fun report(next: VideoSize) = compose.runOnIdle {
            size = next
            listeners.toList().forEach { it.onVideoSizeChanged(next) }
        }
        val fill = AspectRatioFrameLayout.RESIZE_MODE_FILL
        var previous = Triple(800f, 600f, fill)
        // Live and recordings use this same surface contract. Include both directions, Fit SD
        // snapping, forced modes, and a same-size target (no size callback on its reveal).
        val pictures = listOf(
            Triple(AspectRatioMode.FIT, VideoSize(1920, 1080), Triple(960f, 540f, fill)),
            Triple(AspectRatioMode.FIT, VideoSize(720, 576, 0, 16f / 11f), Triple(960f, 540f, fill)),
            Triple(AspectRatioMode.FORCE_4_3, VideoSize(1920, 1080), Triple(800f, 600f, fill)),
            Triple(AspectRatioMode.FORCE_16_9, VideoSize(1920, 1080), Triple(960f, 540f, fill)),
            Triple(AspectRatioMode.FIT, VideoSize(720, 576, 0, 12f / 11f), Triple(800f, 600f, fill)),
        )
        for ((nextMode, nextSize, expected) in pictures) {
            compose.runOnIdle { visible.value = false }
            compose.runOnIdle { mode.value = nextMode }
            assertShape(previous.first, previous.second, previous.third)
            compose.onNodeWithTag("video-cover").assertIsDisplayed()
            if (size != nextSize) {
                report(VideoSize.UNKNOWN)
                assertShape(previous.first, previous.second, previous.third)
                report(nextSize)
                assertShape(previous.first, previous.second, previous.third)
            }
            // The publisher has accepted the new target's picture; commit its mode and size in
            // this composition, even when animations are disabled and the cover clears at once.
            compose.runOnIdle { visible.value = true }
            assertShape(expected.first, expected.second, expected.third)
            compose.onNodeWithTag("video-cover").assertDoesNotExist()
            report(nextSize)
            assertShape(expected.first, expected.second, expected.third)
            previous = expected
        }
        // A Playback options change on the already visible picture must not wait for a frame.
        compose.runOnIdle { mode.value = AspectRatioMode.FORCE_16_9 }
        assertShape(960f, 540f, fill)
        compose.runOnIdle { mode.value = AspectRatioMode.FIT }
        assertShape(800f, 600f, fill)
    }

    @Test fun revealSamplesTheCurrentPictureAfterSkippedTargetsAndCoalescedSizeEvents() {
        val visible = mutableStateOf(true)
        val mode = mutableStateOf(AspectRatioMode.FIT)
        var size = VideoSize(720, 576, 0, 12f / 11f)
        val reporting = object : ForwardingPlayer(player) {
            override fun getVideoSize() = size
        }
        compose.setContent {
            view = LocalView.current
            PlayerVideoSurface(reporting, mode.value, videoVisible = visible.value)
        }
        fun width() = compose.onNodeWithTag("video-surface").getBoundsInRoot().width.value
        assertEquals(800f, width(), 0.5f)
        compose.runOnIdle { visible.value = false; mode.value = AspectRatioMode.FORCE_16_9 }
        assertEquals(800f, width(), 0.5f)
        // A second target replaces the first before either picture was presented. The view must
        // not commit the skipped mode or require a size callback after composition catches up.
        compose.runOnIdle { mode.value = AspectRatioMode.FIT; size = VideoSize(1920, 1080) }
        assertEquals(800f, width(), 0.5f)
        compose.runOnIdle { visible.value = true }
        assertEquals(960f, width(), 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView().resizeMode)
        // Failed install / recovery can re-present the same target without a mode or size event.
        compose.runOnIdle { visible.value = false }
        assertEquals(960f, width(), 0.5f)
        compose.runOnIdle { visible.value = true }
        assertEquals(960f, width(), 0.5f)
        compose.onNodeWithTag("video-cover").assertDoesNotExist()
    }

    @Test fun tunneledFallbackRevealUsesTheCurrentFormatWhenVideoSizeIsUnknown() {
        assertTunneledFallbackShape(VideoSize.UNKNOWN)
    }

    @Test fun tunneledFallbackRevealUsesTheCurrentFormatWhenVideoSizeBelongsToThePreviousTarget() {
        assertTunneledFallbackShape(VideoSize(1920, 1080))
    }

    @Test fun tunneledFallbackRevealDoesNotMistakeMatchingDimensionsForCurrentPixelShape() {
        assertTunneledFallbackShape(VideoSize(720, 576, 0, 12f / 11f))
    }

    @Test fun tunneledFallbackRevealMatchesTheRenderersAppliedRotationAndPixelRatio() {
        assertTunneledFallbackShape(VideoSize.UNKNOWN, rotationDegrees = 90)
    }

    @Test fun tunneledHdFallbackRevealFitsTheActualSurfaceWithAnUnknownReportedSize() {
        assertTunneledFallbackShape(VideoSize.UNKNOWN, nextSize = VideoSize(1920, 1080))
    }

    @Test fun tunneledHdFallbackRevealFitsTheActualSurfaceBeforeTheOldSdSizeIsUpdated() {
        assertTunneledFallbackShape(VideoSize(720, 576, 0, 12f / 11f), nextSize = VideoSize(1920, 1080))
    }

    @Test fun tunneledFallbackWithoutGeometryRetainsThePresentedShapeUntilGeometryArrives() {
        assertTunneledFallbackShape(VideoSize.UNKNOWN, delayFormat = true)
    }

    @Test fun tunneledFallbackWithoutGeometryDoesNotFallBackToThePreviousTargetsSize() {
        assertTunneledFallbackShape(VideoSize(1920, 1080), delayFormat = true)
    }

    @Test fun nonTunneledGeometryStillUsesTheDecodedSizeRatherThanTheInputFormat() {
        val reporting = object : ExoPlayer by player {
            override fun getVideoSize() = VideoSize(720, 576, 0, 16f / 11f)
            override fun getVideoFormat() = Format.Builder().setWidth(1920).setHeight(1080).build()
            override fun isTunnelingEnabled() = false
        }
        compose.setContent {
            view = LocalView.current
            PlayerVideoSurface(reporting, AspectRatioMode.FIT, videoVisible = true)
        }
        val bounds = compose.onNodeWithTag("video-surface").getBoundsInRoot()
        assertEquals(960f, bounds.width.value, 0.5f)
        assertEquals(540f, bounds.height.value, 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView().resizeMode)
    }

    @Test fun tunneledPlaybackFollowsDecodedGeometryAfterRevealAndResetsOnTheNextReveal() {
        val visible = mutableStateOf(false)
        val narrow = VideoSize(720, 576, 0, 12f / 11f)
        val wide = VideoSize(720, 576, 0, 16f / 11f)
        var size = wide
        var format = Format.Builder().setWidth(720).setHeight(576)
            .setPixelWidthHeightRatio(narrow.pixelWidthHeightRatio).build()
        val listeners = mutableSetOf<Player.Listener>()
        val formatListeners = mutableSetOf<AnalyticsListener>()
        val reporting = object : ExoPlayer by player {
            override fun getVideoSize() = size
            override fun getVideoFormat() = format
            override fun isTunnelingEnabled() = true
            override fun addListener(listener: Player.Listener) { listeners += listener }
            override fun removeListener(listener: Player.Listener) { listeners -= listener }
            override fun addAnalyticsListener(listener: AnalyticsListener) { formatListeners += listener }
            override fun removeAnalyticsListener(listener: AnalyticsListener) { formatListeners -= listener }
        }
        compose.setContent {
            view = LocalView.current
            PlayerVideoSurface(reporting, AspectRatioMode.FIT, videoVisible = visible.value)
        }
        fun reportSize(next: VideoSize) = compose.runOnIdle {
            size = next
            listeners.toList().forEach { it.onVideoSizeChanged(next) }
        }
        fun reportFormat(next: VideoSize) = compose.runOnIdle {
            format = format.buildUpon().setPixelWidthHeightRatio(next.pixelWidthHeightRatio).build()
            val event = AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0, Timeline.EMPTY, 0, null, 0, 0)
            formatListeners.toList().forEach { it.onVideoInputFormatChanged(event, format, null) }
        }
        fun assertShape(width: Float, height: Float) {
            val bounds = compose.onNodeWithTag("video-surface").getBoundsInRoot()
            assertEquals(width, bounds.width.value, 0.5f)
            assertEquals(height, bounds.height.value, 0.5f)
            val surface = playerView().videoSurfaceView!!
            assertEquals(width, surface.width.toFloat(), 0.5f)
            assertEquals(height, surface.height.toFloat(), 0.5f)
        }
        repeat(2) {
            // Covered callbacks do not establish the decoded size of the next shown target.
            reportFormat(narrow)
            reportSize(wide)
            compose.runOnIdle { visible.value = true }
            assertShape(800f, 600f)
            // An unknown post-reveal size must not end the input-format fallback either.
            reportSize(VideoSize.UNKNOWN)
            assertShape(800f, 600f)
            reportSize(narrow)
            assertShape(800f, 600f)
            // The renderer reads the upcoming ad's format ahead of the picture on screen.
            reportFormat(wide)
            assertShape(800f, 600f)
            // Only its decoded-size callback may reshape the visible picture.
            reportSize(wide)
            assertShape(960f, 540f)
            compose.runOnIdle { visible.value = false }
            assertShape(960f, 540f)
        }
    }

    private fun assertTunneledFallbackShape(
        reportedAtReveal: VideoSize,
        rotationDegrees: Int = 0,
        nextSize: VideoSize = VideoSize(720, 576, 0, 16f / 11f),
        delayFormat: Boolean = false,
    ) {
        val visible = mutableStateOf(true)
        val mode = mutableStateOf(AspectRatioMode.FORCE_4_3)
        var size = reportedAtReveal.takeUnless { it == VideoSize.UNKNOWN } ?: VideoSize(720, 576, 0, 12f / 11f)
        var format = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(size.width).setHeight(size.height).setPixelWidthHeightRatio(size.pixelWidthHeightRatio).build()
        val rotated = rotationDegrees == 90 || rotationDegrees == 270
        val nextFormat = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(if (rotated) nextSize.height else nextSize.width)
            .setHeight(if (rotated) nextSize.width else nextSize.height)
            .setPixelWidthHeightRatio(if (rotated) 1f / nextSize.pixelWidthHeightRatio else nextSize.pixelWidthHeightRatio)
            .setRotationDegrees(rotationDegrees)
            .build()
        val listeners = mutableSetOf<Player.Listener>()
        val formatListeners = mutableSetOf<AnalyticsListener>()
        val reporting = object : ExoPlayer by player {
            override fun getVideoSize() = size
            override fun getVideoFormat() = format
            override fun isTunnelingEnabled() = true
            override fun getPlaybackState() = Player.STATE_READY
            override fun getCurrentTracks() = Tracks(listOf(
                Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            ))
            override fun addListener(listener: Player.Listener) { listeners += listener }
            override fun removeListener(listener: Player.Listener) { listeners -= listener }
            override fun addAnalyticsListener(listener: AnalyticsListener) { formatListeners += listener }
            override fun removeAnalyticsListener(listener: AnalyticsListener) { formatListeners -= listener }
        }
        compose.setContent {
            view = LocalView.current
            PlayerVideoSurface(reporting, mode.value, videoVisible = visible.value)
        }
        fun assertShape(width: Float, height: Float) {
            val bounds = compose.onNodeWithTag("video-surface").getBoundsInRoot()
            assertEquals(width, bounds.width.value, 0.5f)
            assertEquals(height, bounds.height.value, 0.5f)
            assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, playerView().resizeMode)
            // Check the actual SurfaceView too: a correct outer box can still contain a stale
            // FIT layout if PlayerView independently sizes it from the previous VideoSize.
            val surface = playerView().videoSurfaceView!!
            assertEquals(width, surface.width.toFloat(), 0.5f)
            assertEquals(height, surface.height.toFloat(), 0.5f)
        }
        assertShape(800f, 600f)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle {
            mode.value = AspectRatioMode.FIT
            // Renderer input format arrives before tunnelled frame/size callbacks. At fallback
            // reveal the unscoped size getter can still be UNKNOWN or the previous picture.
            format = if (delayFormat) Format.Builder().build() else nextFormat
            size = reportedAtReveal
        }
        assertShape(800f, 600f)
        compose.onNodeWithTag("video-cover").assertIsDisplayed()
        // Model the existing 1,500 ms fallback: reveal without first-frame or size evidence.
        compose.runOnIdle { visible.value = true }
        if (delayFormat) assertShape(800f, 600f) else assertShape(960f, 540f)
        compose.onNodeWithTag("video-cover").assertDoesNotExist()
        if (delayFormat) {
            compose.runOnIdle {
                format = nextFormat
                val event = AnalyticsListener.EventTime(0, Timeline.EMPTY, 0, null, 0, Timeline.EMPTY, 0, null, 0, 0)
                formatListeners.toList().forEach { it.onVideoInputFormatChanged(event, format, null) }
            }
            // No decoded-size callback is required to leave the held shape.
            assertShape(960f, 540f)
        }
        compose.runOnIdle {
            format = nextFormat
            size = nextSize
            listeners.toList().forEach { it.onVideoSizeChanged(size) }
        }
        // One change at reveal if geometry was available; otherwise retain the old shape until
        // geometry arrives rather than show an intermediate full-screen or previous-target shape.
        assertShape(960f, 540f)
        compose.runOnIdle { mode.value = AspectRatioMode.FORCE_4_3 }
        assertShape(800f, 600f)
    }

    @Test fun theCoverClearsOverTheEntryDurationAndFasterOnAZap() {
        val visible = mutableStateOf(false)
        val fade = mutableStateOf(PlayerMotion.EntryPictureMs)
        cover(visible, fade)
        assertEquals("covered", 0f, light())

        compose.mainClock.autoAdvance = false
        val entry = lift(visible, PlayerMotion.EntryPictureMs)
        assertHeldThenStandardFade(entry, PlayerMotion.EntryPictureMs)
        compose.mainClock.advanceTimeBy(64)
        assertEquals(1f, light(), 0.01f)
        compose.onNodeWithTag("video-cover").assertDoesNotExist()

        // A new tune covers the old picture in its first frame.
        change { visible.value = false; fade.value = PlayerMotion.ZapPictureMs }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, light())

        val zap = lift(visible, PlayerMotion.ZapPictureMs)
        assertHeldThenStandardFade(zap, PlayerMotion.ZapPictureMs)
        assertTrue("the zap fade is shorter: $zap", zap.size < entry.size)
        compose.mainClock.advanceTimeBy(64)
        assertEquals(1f, light(), 0.01f)
    }

    /**
     * Fully covered for the hold, so the panel can show the picture underneath; then clearing
     * steadily with standard easing, not in the first frame of the fade.
     */
    private fun assertHeldThenStandardFade(samples: List<Float>, millis: Int) {
        val held = PlayerMotion.PictureHoldMs / 25
        assertTrue("covered through the hold: $samples", samples.take(held).all { it == 0f })
        val fade = samples.drop(held)
        assertTrue("the cover clears in steps: $samples", fade.zipWithNext().all { (a, b) -> b >= a })
        // Emphasized decelerate cleared most of the cover in the fade's first frame.
        assertTrue("no cut at the start of the fade: $samples", fade.first() < 0.3f)
        assertTrue("visible over several frames: $samples", fade.count { it in 0.02f..0.98f } >= 3)
        assertTrue("mostly clear midway: $samples", fade[millis / 50] in 0.6f..0.99f)
        assertTrue("clear at the end: $samples", fade.last() > 0.98f)
    }

    @Test fun withoutAFadeTheCoverLeavesInTheFrameThePictureAppears() {
        val visible = mutableStateOf(false)
        cover(visible, mutableStateOf(0))
        compose.mainClock.autoAdvance = false
        change { visible.value = true }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, light(), 0.01f)
        compose.onNodeWithTag("video-cover").assertDoesNotExist()
    }

    @Test fun theFirstPictureAfterEnteringThePlayerTakesTheEntryFadeAndLaterZapsTheShortOne() {
        val shown = mutableStateOf(false)
        val visible = mutableStateOf(true)
        val instant = mutableStateOf(false)
        var fade = -1
        compose.setContent {
            fade = rememberVideoCoverFadeMillis(shown.value, visible.value, instant.value)
        }
        fun step(block: () -> Unit): Int { compose.runOnIdle(block); compose.waitForIdle(); return fade }

        assertEquals("browse", PlayerMotion.ZapPictureMs, fade)
        // Entering with the warm picture still showing; the entry's own tune follows.
        assertEquals(PlayerMotion.EntryPictureMs, step { shown.value = true })
        assertEquals(PlayerMotion.EntryPictureMs, step { visible.value = false })
        assertEquals("the entry's first picture", PlayerMotion.EntryPictureMs, step { visible.value = true })
        assertEquals(PlayerMotion.ZapPictureMs, step { visible.value = false })
        assertEquals("a zap's first picture", PlayerMotion.ZapPictureMs, step { visible.value = true })
        step { shown.value = false }
        assertEquals("entering again", PlayerMotion.EntryPictureMs, step { shown.value = true; visible.value = false })
        assertEquals("startup or no animations", 0, step { instant.value = true })
    }

    private fun cover(visible: androidx.compose.runtime.State<Boolean>, fade: androidx.compose.runtime.State<Int>) {
        compose.setContent {
            view = LocalView.current
            Box(Modifier.size(20.dp).background(Color.White)) {
                VideoCover(visible.value, fade.value, Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
    }

    /** Light through the cover, sampled per 25 ms from the frame the picture appears to the fade's end. */
    private fun lift(visible: androidx.compose.runtime.MutableState<Boolean>, millis: Int): List<Float> {
        change { visible.value = true }
        compose.mainClock.advanceTimeByFrame()
        return listOf(light()) + (1..(PlayerMotion.PictureHoldMs + millis) / 25).map {
            compose.mainClock.advanceTimeBy(25)
            light()
        }
    }

    private fun change(block: () -> Unit) = compose.runOnIdle {
        block()
        Snapshot.sendApplyNotifications()
    }

    /** 0 under a fully opaque cover, 1 with the white backdrop fully visible. */
    private fun light(): Float = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val red = android.graphics.Color.red(bitmap.getPixel(10, 10))
        bitmap.recycle()
        red / 255f
    }
}
