package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Banner and the controls are modes of one chrome, which is not composed while hidden. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsWinOverTheBannerWhichShowsAQuickStepOnlyWhileOneShows() {
        assertEquals(PlayerChromeMode.HIDDEN, playerChromeMode(controls = false, banner = false, stepPreview = false))
        assertEquals(PlayerChromeMode.HIDDEN, playerChromeMode(controls = false, banner = false, stepPreview = true))
        assertEquals(PlayerChromeMode.BANNER, playerChromeMode(controls = false, banner = true, stepPreview = false))
        assertEquals(PlayerChromeMode.BANNER_STEP, playerChromeMode(controls = false, banner = true, stepPreview = true))
        assertEquals(PlayerChromeMode.CONTROLS, playerChromeMode(controls = true, banner = true, stepPreview = true))
        assertEquals(PlayerChromeMode.CONTROLS, playerChromeMode(controls = true, banner = false, stepPreview = false))
    }

    @Test fun thePassiveBannerHandsItsInfoToTheControlsAndHiddenChromeLeavesComposition() {
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        compose.setContent {
            val context = LocalContext.current
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = mode,
                    content = PlayerChromeContent("20:15", liveInfoBarData(1, "One", null, null, false, 1_800, "Programme")),
                    timeline = PlayerChromeTimeline.Live(
                        AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0),
                        nowSec = 1_800,
                    ),
                    actions = PlayerChromeActions(active = mode == PlayerChromeMode.CONTROLS),
                    imageLoader = remember { ImageLoader(context) },
                    currentSession = null,
                    onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("player-banner").assertExists()
        compose.onNodeWithTag("player-seekbar").assertDoesNotExist()
        compose.onNodeWithTag("player-actions").assertDoesNotExist()
        compose.onAllNodes(isFocusable(), useUnmergedTree = true).assertCountEquals(0)

        compose.runOnIdle { mode = PlayerChromeMode.CONTROLS }
        compose.waitForIdle()
        compose.onNodeWithTag("player-banner").assertDoesNotExist()
        compose.onAllNodesWithTag("player-info-bar", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("player-seekbar").assertExists()
        compose.onNodeWithTag("player-pause").assertIsFocused()

        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            mode = PlayerChromeMode.HIDDEN
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        // Leaving, the controls keep their layout while they fade.
        compose.onNodeWithTag("player-actions", useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("player-footer", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithTag("player-info-bar", useUnmergedTree = true).assertCountEquals(0)
    }
}
