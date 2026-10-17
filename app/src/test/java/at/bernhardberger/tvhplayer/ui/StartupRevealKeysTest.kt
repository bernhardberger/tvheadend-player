package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.view.KeyEvent
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyCycleOwner
import at.bernhardberger.tvhplayer.ui.startup.MainStartupKeyMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Keys during the autoplay reveal: the first ends it, and only the next reaches the player. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
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
