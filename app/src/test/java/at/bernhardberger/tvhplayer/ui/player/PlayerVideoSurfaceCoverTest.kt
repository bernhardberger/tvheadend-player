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
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
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
        // HD and unknown sizes keep the screen-filling fit.
        val hd = show(VideoSize(1920, 1080, 0, 1f))
        assertEquals(960f, hd.width.value, 0.5f)
        assertEquals(600f, hd.height.value, 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, playerView().resizeMode)
        assertEquals(600f, show(VideoSize.UNKNOWN).height.value, 0.5f)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, playerView().resizeMode)
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
