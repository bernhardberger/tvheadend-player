package at.bernhardberger.tvhplayer.ui.player

import android.app.Activity
import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateChange
import at.bernhardberger.tvhplayer.playback.SurfaceFrameRateRequest
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowSurface
import org.robolectric.shadows.ShadowSurfaceView
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [31],
    application = Application::class,
    shadows = [VideoSurfaceFrameRateTest.VotingSurface::class, VideoSurfaceFrameRateTest.DisplayPreference::class],
)
class VideoSurfaceFrameRateTest {
    private val film = SurfaceFrameRateRequest(24f, SurfaceFrameRateChange.ALWAYS)

    @Test
    fun onlyAnInstalledVoteWithAndroidAlwaysCanUnpinTheAppRate() = withPlayer { player ->
        val (frameRate, holder, preference, appModeId, pinnedMode) = player
        var platform = holder.platform
        frameRate.request(film, playerShown = true)
        assertEquals(appModeId, pinnedMode())
        assertNull(platform.rate)

        platform.onVote = { assertEquals(appModeId, pinnedMode()) }
        holder.create()
        platform.onVote = {}
        assertEquals(24f, platform.rate)
        assertEquals(Surface.CHANGE_FRAME_RATE_ALWAYS, platform.strategy)
        assertEquals(appModeId, pinnedMode())

        preference.value = DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY
        frameRate.request(film, playerShown = true)
        assertEquals(appModeId, pinnedMode())
        preference.value = -1
        frameRate.request(film, playerShown = true)
        assertEquals(appModeId, pinnedMode())
        preference.value = DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
        frameRate.request(film, playerShown = true)
        assertEquals(0, pinnedMode())

        holder.destroy()
        assertEquals(0, pinnedMode())
        frameRate.request(film, playerShown = true)
        assertEquals(0, pinnedMode())
        platform = holder.platform
        assertNull(platform.rate)
        holder.create()
        assertEquals(24f, platform.rate)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(0, pinnedMode())

        frameRate.request(SurfaceFrameRateRequest(), playerShown = true)
        assertEquals(0f, platform.rate)
        assertEquals(appModeId, pinnedMode())
        frameRate.request(film, playerShown = true)
        assertEquals(0, pinnedMode())
        frameRate.request(film.copy(rate = 50f), playerShown = false)
        assertEquals(0f, platform.rate)
        assertEquals(appModeId, pinnedMode())
        frameRate.request(film, playerShown = true)
        assertEquals(0, pinnedMode())
        frameRate.release()
        assertEquals(0f, platform.rate)
        assertEquals(appModeId, pinnedMode())
    }

    @Test
    fun aMissingReplacementSurfaceRestoresThePinAfterTheGracePeriod() = withPlayer { player ->
        val (frameRate, holder, preference, appModeId, pinnedMode) = player
        preference.value = DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
        frameRate.request(film, playerShown = true)
        holder.create()
        assertEquals(0, pinnedMode())

        holder.destroy()
        assertEquals(0, pinnedMode())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(appModeId, pinnedMode())
    }

    @Test
    fun aRejectedRateWithdrawsThePreviousVote() = withPlayer { player ->
        val (frameRate, holder, preference, appModeId, pinnedMode) = player
        preference.value = DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
        frameRate.request(film, playerShown = true)
        holder.create()
        holder.platform.rejectedRate = 25f
        frameRate.request(film.copy(rate = 25f), playerShown = true)
        assertEquals(appModeId, pinnedMode())
        assertEquals(0f, holder.platform.rate)
    }

    @Test
    fun anIdenticalRequestCanRetryARejectedRate() = withPlayer { player ->
        val (frameRate, holder, preference, appModeId, pinnedMode) = player
        preference.value = DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
        frameRate.request(film, playerShown = true)
        holder.platform.rejectedRate = 24f
        holder.create()
        holder.platform.rejectedRate = null
        holder.platform.onVote = { assertEquals(appModeId, pinnedMode()) }
        frameRate.request(film, playerShown = true)
        holder.platform.onVote = {}
        assertEquals(24f, holder.platform.rate)
        assertEquals(0, pinnedMode())
    }

    @Test
    fun releaseClearsTheVoteEvenIfOffWasRejected() = withPlayer { player ->
        val (frameRate, holder, preference, appModeId, pinnedMode) = player
        preference.value = DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS
        frameRate.request(film, playerShown = true)
        holder.create()
        holder.platform.rejectedRate = 0f
        frameRate.request(SurfaceFrameRateRequest(), playerShown = true)
        assertEquals(24f, holder.platform.rate)
        assertEquals(appModeId, pinnedMode())
        holder.platform.rejectedRate = null
        frameRate.release()
        assertEquals(0f, holder.platform.rate)
    }

    private fun withPlayer(checks: (TestPlayer) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            val window = activity.window
            val appMode = AppDisplayMode.of(window)
            val display = requireNotNull(appMode.display)
            val base = display.mode
            val appModeId = base.modeId + 1
            val fifty = ReflectionHelpers.callConstructor(
                Display.Mode::class.java,
                from(Int::class.javaPrimitiveType, appModeId),
                from(Int::class.javaPrimitiveType, base.physicalWidth),
                from(Int::class.javaPrimitiveType, base.physicalHeight),
                from(Float::class.javaPrimitiveType, 50f),
            )
            ShadowDisplayManager.setSupportedModes(display.displayId, base, fifty)
            appMode.useAppRate(50f)
            val preference = Shadow.extract<DisplayPreference>(activity.getSystemService(DisplayManager::class.java))
            fun pinnedMode() = window.attributes.preferredDisplayModeId
            val holder = TestHolder()
            val view = object : SurfaceView(activity) {
                override fun getHolder(): SurfaceHolder = holder
            }
            activity.setContentView(view)
            val frameRate = VideoSurfaceFrameRate()
            frameRate.attach(view)
            try {
                checks(TestPlayer(frameRate, holder, preference, appModeId, ::pinnedMode))
            } finally {
                frameRate.release()
            }
        }
    }

    private data class TestPlayer(
        val frameRate: VideoSurfaceFrameRate,
        val holder: TestHolder,
        val preference: DisplayPreference,
        val appModeId: Int,
        val pinnedMode: () -> Int,
    )

    private class TestHolder : ShadowSurfaceView.FakeSurfaceHolder() {
        private var testSurface = Shadow.newInstanceOf(Surface::class.java)
        val platform: VotingSurface get() = Shadow.extract(testSurface)
        override fun getSurface() = testSurface

        fun create() {
            platform.valid = true
            callbacks.forEach { it.surfaceCreated(this) }
        }

        fun destroy() {
            callbacks.forEach { it.surfaceDestroyed(this) }
            platform.valid = false
            testSurface = Shadow.newInstanceOf(Surface::class.java)
        }
    }

    @Implements(DisplayManager::class)
    class DisplayPreference : ShadowDisplayManager() {
        var value = DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER

        @Implementation
        fun getMatchContentFrameRateUserPreference(): Int = value
    }

    @Implements(Surface::class)
    class VotingSurface : ShadowSurface() {
        var valid = false
        var rejectedRate: Float? = null
        var onVote: () -> Unit = {}
        var rate: Float? = null
        var strategy: Int? = null

        @Implementation
        public override fun isValid(): Boolean = valid

        @Implementation
        fun setFrameRate(rate: Float, compatibility: Int) {
            setFrameRate(rate, compatibility, Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS)
        }

        @Implementation
        fun setFrameRate(rate: Float, @Suppress("UNUSED_PARAMETER") compatibility: Int, strategy: Int) {
            require(rate != rejectedRate)
            onVote()
            this.rate = rate
            this.strategy = strategy
        }
    }
}
