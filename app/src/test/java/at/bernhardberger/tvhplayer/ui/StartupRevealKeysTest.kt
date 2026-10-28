package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The autoplay reveal: its hold and fade, and keys during it: the first ends it, and only the
 * next reaches the player.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupRevealKeysTest {
    @get:Rule val compose = createComposeRule()

    private val dispatcher = MainStartupActivityKeyDispatcher(MainStartupKeyCycleOwner())
    private var contract = MainStartupActivityKeyContract(mode = MainStartupKeyMode.Inactive)
    private val playerKeys = mutableListOf<String>()

    @Test fun aKeyDuringTheRevealEndsItAndOnlyTheNextKeyReachesThePlayer() {
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION), ChannelsKey,
            navigationAllowed = true, contentAllowed = false))
        var revealed = 0
        val playerFocus = FocusRequester()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                MainStartupComposition(
                    state = state, onBack = {}, onAction = {},
                    registerActivityKeyContract = { registered ->
                        contract = registered
                        { if (contract === registered) contract = MainStartupActivityKeyContract(MainStartupKeyMode.Inactive) }
                    },
                    motionEnabled = true,
                    navigation = { _, contentAllowed ->
                        if (contentAllowed) {
                            Box(Modifier.fillMaxSize().focusRequester(playerFocus)
                                .onKeyEvent { playerKeys += it.type.toString(); true }.focusable())
                            LaunchedEffect(Unit) { playerFocus.requestFocus() }
                        }
                    },
                    onRevealed = {
                        revealed++
                        state = state.copy(revealRequestId = null, contentAllowed = true)
                    },
                )
            }
        }
        frames()
        // Autoplay's handoff: the startup screen starts dissolving into the first video frames.
        state = state.copy(presentation = MainStartupPresentation.Inactive, revealRequestId = 1)
        frames()
        assertEquals(0, revealed)
        assertEquals(1, startupScreens())

        assertTrue("the key that ends the reveal is consumed", key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN))
        assertEquals(1, revealed)
        frames()
        assertEquals(0, startupScreens())
        assertTrue("its up stays consumed", key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP))
        assertEquals(emptyList<String>(), playerKeys)

        key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN)
        key(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP)
        assertEquals(listOf("KeyDown", "KeyUp"), playerKeys)
        assertEquals(1, revealed)
    }

    @Test fun theRevealHoldsForTheFirstPictureThenFadesOverTheLongDuration() {
        var state by mutableStateOf(MainStartupCompositionState(
            MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION), ChannelsKey,
            navigationAllowed = true, contentAllowed = false))
        var revealed = 0
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                MainStartupComposition(
                    state = state, onBack = {}, onAction = {}, registerActivityKeyContract = { {} },
                    motionEnabled = true,
                    navigation = { _, _ -> Box(Modifier.fillMaxSize().background(Color.Red)) },
                    onRevealed = { revealed++ },
                )
            }
        }
        frames()
        val covered = pixel(view)
        assertTrue("the startup screen covers the player", covered != android.graphics.Color.RED)

        val start = compose.mainClock.currentTime
        state = state.copy(presentation = MainStartupPresentation.Inactive, revealRequestId = 1)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(PlayerMotion.PictureHoldMs - 20L)
        assertEquals("opaque through the hold", covered, pixel(view))
        compose.mainClock.advanceTimeBy(20L + StartupRevealMs / 2)
        // The pixel halfway tells when the fade started, so a late start shows in the messages.
        val fading = pixel(view)
        val handoff = "handoff at $start ms, halfway ${Integer.toHexString(fading)}"
        assertTrue("fading: $handoff", fading != covered && fading != android.graphics.Color.RED)
        assertEquals(0, revealed)
        compose.mainClock.advanceTimeBy(StartupRevealMs / 2 + 40L)
        compose.waitForIdle()
        assertEquals(
            "the player shows after hold + long2 ($handoff, now ${compose.mainClock.currentTime} ms)",
            Integer.toHexString(android.graphics.Color.RED), Integer.toHexString(pixel(view)),
        )
        assertEquals(1, revealed)
    }

    private fun pixel(view: View): Int = compose.runOnIdle {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.getPixel(2, 2).also { bitmap.recycle() }
    }

    /** The activity's dispatch: the startup contract first, then the composition. */
    private fun key(keyCode: Int, action: Int): Boolean {
        if (dispatcher.dispatch(contract, keyCode, action)) return true
        compose.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(KeyEvent(action, keyCode)))
        compose.waitForIdle()
        return false
    }

    private fun frames() = repeat(4) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun startupScreens() = compose.onAllNodes(hasTestTag("main-startup-root")).fetchSemanticsNodes().size
}
