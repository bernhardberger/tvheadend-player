package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Live Pause stays in its slot while timeshift starts or cannot pause, and explains a dimmed press. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LivePauseControlTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var view: View
    private var livePause by mutableStateOf(LivePauseAvailability.STARTING)
    private var paused by mutableStateOf(false)
    private var feedback by mutableStateOf<String?>(null)
    private var toggles = 0

    @Before fun leanback() {
        shadowOf(app.packageManager).setSystemFeature("android.software.leanback", true)
    }

    @Test fun pauseIsInItsSlotAndInitiallyFocusedInEveryUndecidedOrUnavailableState() {
        for (state in listOf(LivePauseAvailability.STARTING, LivePauseAvailability.UNAVAILABLE, LivePauseAvailability.OFF)) {
            livePause = state
            show()
            compose.onNodeWithTag("player-pause").assertIsFocused().assertIsEnabled()
            compose.onNodeWithTag("player-info").assertExists()
        }
    }

    @Test fun dimmedPauseAnnouncesWhyItCannotPause() {
        show(LivePauseAvailability.UNAVAILABLE)
        compose.onNodeWithTag("player-pause")
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.pause_unavailable_channel)))
        compose.runOnIdle { livePause = LivePauseAvailability.OFF }
        compose.onNodeWithTag("player-pause")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.pause_timeshift_off)))
        compose.runOnIdle { livePause = LivePauseAvailability.STARTING }
        compose.onNodeWithTag("player-pause").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test fun dimmedPressShowsTheReasonWithoutPausing() {
        show(LivePauseAvailability.UNAVAILABLE)
        center()
        compose.onNodeWithText(text(R.string.pause_unavailable_channel)).assertExists()
        compose.onNodeWithTag("player-pause").assertIsFocused()
        compose.runOnIdle { feedback = null; livePause = LivePauseAvailability.OFF }
        center()
        compose.onNodeWithText(text(R.string.pause_timeshift_off)).assertExists()
        assertEquals(0, toggles)
    }

    @Test fun startingPressTogglesAndPendingPauseShowsPlay() {
        show(LivePauseAvailability.STARTING)
        compose.onNodeWithTag("player-pause").assertContentDescriptionEquals(text(R.string.pause))
        center()
        assertEquals(1, toggles)
        assertEquals(null, feedback)
        compose.runOnIdle { paused = true }
        compose.onNodeWithTag("player-pause").assertContentDescriptionEquals(text(R.string.play))
    }

    @Test fun settingsStaysTheSameNumberOfRightsFromPauseInEveryLiveState() {
        for (state in LivePauseAvailability.entries.filter { it != LivePauseAvailability.READY }) {
            livePause = state
            show()
            compose.onNodeWithTag("player-pause").assertIsFocused()
            repeat(4) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; compose.waitForIdle() }
            compose.onNodeWithTag("player-settings").assertIsFocused()
        }
    }

    @Test fun capturesEnglish() = captures("en", 1f)
    @Test fun capturesEnglishLargeText() = captures("en", 1.3f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun capturesGerman() = captures("de", 1f)
    @Test @Config(qualifiers = "de-w960dp-h540dp-land-xhdpi")
    fun capturesGermanLargeText() = captures("de", 1.3f)

    private fun captures(locale: String, fontScale: Float) {
        show(LivePauseAvailability.UNAVAILABLE, fontScale)
        center()
        compose.onNodeWithTag("player-pause").assertIsFocused()
        capture("$locale-font$fontScale-unavailable-channel", fontScale)
        compose.runOnIdle { feedback = null; livePause = LivePauseAvailability.OFF }
        center()
        compose.onNodeWithTag("player-pause").assertIsFocused()
        capture("$locale-font$fontScale-timeshift-off", fontScale)
        compose.runOnIdle { feedback = null; livePause = LivePauseAvailability.STARTING }
        center()
        compose.runOnIdle { paused = true }
        compose.onNodeWithTag("player-pause").assertIsFocused().assertContentDescriptionEquals(text(R.string.play))
        capture("$locale-font$fontScale-starting-pending", fontScale)
    }

    private var shown = false

    private fun show(state: LivePauseAvailability = livePause, fontScale: Float = 1f) {
        livePause = state
        if (shown) {
            // Recreate the overlay so every state is checked from a fresh reveal.
            compose.runOnIdle { visible = false }
            compose.waitForIdle()
            compose.runOnIdle { visible = true }
            compose.waitForIdle()
            return
        }
        shown = true
        compose.setContent {
            view = LocalView.current
            val context = LocalContext.current
            val loader = remember { ImageLoader.Builder(context).diskCache(null).build() }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize().background(Color(0xFF24384A))) {
                        if (visible) Box(Modifier.align(Alignment.BottomCenter)) {
                            OverlayControlsTv(
                                imageLoader = loader, channelNumber = 7, channelName = "Documentary",
                                piconPath = null, nowEvent = null, nextEvent = null, nowSec = 1_800,
                                controlsVisible = true, optionsOpen = false,
                                onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                                timeshiftState = AppTimeshiftState(),
                                timeshiftFeedback = feedback,
                                onToggleTimeshiftPause = { toggles++ },
                                onSeekTimeshift = {}, onGoLive = {},
                                paused = paused,
                                livePause = livePause,
                                onPauseUnavailable = { feedback = it },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private var visible by mutableStateOf(true)

    private fun center() {
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
    }

    private fun text(id: Int) = app.getString(id)

    private fun capture(name: String, fontScale: Float) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            assertEquals(1920 to 1080, bitmap.width to bitmap.height)
            view.draw(Canvas(bitmap))
            val directory = File("build/outputs/pause-captures").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(directory, "$name.txt").writeText(
                "canvas=1920x1080\ndensity=2.0\nlocale=${name.substringBefore('-')}\nfontScale=$fontScale\n" +
                    "leanback=true\nfocus=player-pause\nlivePause=$livePause\npaused=$paused\nfeedback=${feedback.orEmpty()}\n" +
                    "production OverlayControlsTv; fake state, no playback\n",
            )
            bitmap.recycle()
        }
    }
}
