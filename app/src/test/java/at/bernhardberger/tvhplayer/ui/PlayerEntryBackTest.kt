package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back never reaches the shell that is fading out: from the first frame of the move into or out of
 * the player only the shell on its way in and the screen's own handler answer the Back key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class PlayerEntryBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val showRail = mutableStateOf(true)
    private val playerHandlesBack = mutableStateOf(true)
    private val railHandlesBack = mutableStateOf(true)
    private val root = mutableIntStateOf(0)
    private val rail = mutableIntStateOf(0)
    private val player = mutableIntStateOf(0)

    @Test fun backDoesNotReachTheBrowseShellFadingOutIntoThePlayer() {
        // The player has no handler yet, so a press that reached the rail would be the rail's.
        playerHandlesBack.value = false
        shell()
        compose.mainClock.autoAdvance = false
        change { showRail.value = false }

        val presses = backEachFrame()

        assertEquals("the leaving rail", 0, rail.intValue)
        assertEquals("every press fell through to the app", presses, root.intValue)
    }

    @Test fun backDoesNotReachThePlayerFadingOutToBrowse() {
        // Browse answers Back through the app's own handler, which is older than the player's.
        showRail.value = false
        railHandlesBack.value = false
        shell()
        compose.mainClock.autoAdvance = false
        change { showRail.value = true }

        val presses = backEachFrame()

        assertEquals("the leaving player", 0, player.intValue)
        assertEquals("every press reached the app", presses, root.intValue)
    }

    private fun shell() {
        compose.setContent {
            TVHeadendPlayerTheme {
                // The app's own handler: older than every shell, like AppRoot's.
                BackHandler { root.intValue++ }
                MainNavigationShell(
                    showRail = showRail.value,
                    rail = {
                        if (railHandlesBack.value) BackHandler { rail.intValue++ }
                        Box(Modifier.fillMaxSize())
                    },
                    fullScreen = {
                        if (playerHandlesBack.value) BackHandler { player.intValue++ }
                        Box(Modifier.fillMaxSize())
                    },
                    motionEnabled = true,
                )
            }
        }
        compose.waitForIdle()
    }

    /** Presses Back after every frame of the move; returns how often it did. */
    private fun backEachFrame(): Int {
        var presses = 0
        repeat(PlayerMotion.DestinationMs / 16 + 2) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            presses++
        }
        assertTrue(presses > 10)
        return presses
    }

    private fun change(block: () -> Unit) = compose.runOnIdle {
        block()
        Snapshot.sendApplyNotifications()
    }
}
