package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.KeyEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Keys that arrive in the frame an overlay waits before focusing its action must
 * not reach what is behind it, and a deferred initial focus must not pull focus
 * back from where the viewer has since moved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-xhdpi")
class OverlayFocusTransitionTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var view: View

    @Before
    fun televisionFeature() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>().packageManager)
            .setSystemFeature("android.software.leanback", true)
    }

    @Test
    fun recoveryAppearingOverAFocusedControlKeepsOkFromIt() {
        var activations = 0
        var recoveryVisible by mutableStateOf(false)
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                Box(Modifier.fillMaxSize()) {
                    Button(onClick = { activations++ }, modifier = Modifier.testTag(BEHIND)) { Text("Behind") }
                    recovery(recoveryVisible)
                }
            }
        }
        compose.onNodeWithTag(BEHIND).requestFocus()
        compose.waitForIdle()
        // The harness delivers OK to the focused control.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(1, activations)

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { recoveryVisible = true }
        advanceToFrameComposing(hasTestTag("tv-recovery-overlay"))
        // The overlay is composed and waits one frame before focusing Retry.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_ENTER)
        assertEquals("OK reached the control behind the overlay", 1, activations)
        compose.onNodeWithTag("tv-recovery-overlay").assertIsFocused()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        assertEquals(1, activations)
    }

    @Test
    fun directionsInTheWaitingFrameStayWithTheRecoveryOverlay() {
        var recoveryVisible by mutableStateOf(false)
        var keysReachingParent = 0
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                // Stands in for the player parent, which passes directions on to the focused node.
                Box(Modifier.fillMaxSize().onKeyEvent { keysReachingParent++; false }) {
                    Button(onClick = {}, modifier = Modifier.testTag(BEHIND)) { Text("Behind") }
                    recovery(recoveryVisible)
                }
            }
        }
        compose.onNodeWithTag(BEHIND).requestFocus()
        compose.waitForIdle()

        compose.mainClock.autoAdvance = false
        compose.runOnIdle { recoveryVisible = true }
        advanceToFrameComposing(hasTestTag("tv-recovery-overlay"))
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN,
        ).forEach { key ->
            press(key)
            compose.onNodeWithTag("tv-recovery-overlay").assertIsFocused()
        }
        assertEquals("Directions left the overlay", 0, keysReachingParent)

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
    }

    @Test
    fun displayModeSelectionDoesNotPullFocusBackAfterTheViewerMoves() {
        var aspectRatio by mutableStateOf(AspectRatioMode.FORCE_16_9)
        compose.setContent {
            TVHeadendPlayerTheme {
                view = LocalView.current
                PlaybackOptionsSheetContent(
                    page = PlaybackOptionsPage.DISPLAY,
                    audioTracks = emptyList(),
                    subtitleTracks = emptyList(),
                    tracksResolving = false,
                    aspectRatio = aspectRatio,
                    statsVisible = false,
                    onPageChange = {},
                    onAudioTrackSelected = {},
                    onSubtitleTrackSelected = {},
                    onAspectRatioChange = { aspectRatio = it },
                    onStatsVisibleChange = {},
                )
            }
        }
        compose.waitForIdle()
        focusedRow(R.string.display_mode_16_9).assertExists()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitForIdle()
        focusedRow(R.string.display_mode_4_3).assertExists()

        compose.mainClock.autoAdvance = false
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(AspectRatioMode.FORCE_4_3, aspectRatio)
        advanceToFrameComposing(hasText(string(R.string.display_mode_4_3)) and isSelected())
        // The viewer moves on before the next frame.
        press(KeyEvent.KEYCODE_DPAD_UP)
        focusedRow(R.string.display_mode_16_9).assertExists()

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        focusedRow(R.string.display_mode_16_9).assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun recovery(visible: Boolean) = TvRecoveryOverlay(
        visible = visible,
        message = "Connection lost",
        primaryActionLabel = "Retry",
        onPrimaryAction = {},
        secondaryActionLabel = "Close",
        onSecondaryAction = {},
    )

    /**
     * Advances frame by frame until [node] is composed, then lets that frame's effects
     * start while the clock stays before the next frame.
     */
    private fun advanceToFrameComposing(node: SemanticsMatcher) {
        repeat(MAX_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (compose.onAllNodes(node).fetchSemanticsNodes().isNotEmpty()) return
        }
        throw AssertionError("${node.description} not composed within $MAX_FRAMES frames")
    }

    private fun focusedRow(label: Int) = compose.onNode(isFocused() and hasText(string(label)))

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun press(keyCode: Int) = compose.runOnUiThread {
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private companion object {
        const val BEHIND = "control-behind-overlay"
        const val MAX_FRAMES = 5
    }
}
