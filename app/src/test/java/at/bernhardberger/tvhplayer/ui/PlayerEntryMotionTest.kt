package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.testutil.drawnBrightness
import at.bernhardberger.tvhplayer.testutil.tagBounds
import at.bernhardberger.tvhplayer.testutil.changeState
import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.requestFocus
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.player.PlayerChrome
import at.bernhardberger.tvhplayer.ui.player.PlayerChromeActions
import at.bernhardberger.tvhplayer.ui.player.PlayerChromeContent
import at.bernhardberger.tvhplayer.ui.player.PlayerChromeMode
import at.bernhardberger.tvhplayer.ui.player.PlayerChromeTimeline
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import at.bernhardberger.tvhplayer.ui.player.liveInfoBarData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Into and out of the player the browse shell with its rail and the full-screen player cross-fade
 * as one motion: nothing leaves in one frame, and the player's Banner rides the same fade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerEntryMotionTest {
    @get:Rule val compose = createComposeRule()

    private val showRail = mutableStateOf(true)
    private lateinit var view: View

    @Test fun theRailFadesOutWithThePlayerTransitionWithoutMoving() {
        shell(motionEnabled = true) { Box(Modifier.fillMaxSize().testTag("player")) }
        val rail = Rect(0f, 0f, 40f, 40f)
        val beside = Rect(48f, 0f, 88f, 40f)
        val full = compose.drawnBrightness(view, rail)
        assertTrue(full > 0f)

        compose.mainClock.autoAdvance = false
        compose.changeState { showRail.value = false }
        val steps = (1..PlayerMotion.DestinationMs / 50).map {
            compose.mainClock.advanceTimeBy(50)
            compose.drawnBrightness(view, rail)
        }
        assertTrue("the rail is still there after the first step: $steps", steps.first() in 1f..full)
        assertTrue("the rail fades in steps: $steps", steps.zipWithNext().all { (a, b) -> b <= a })
        assertTrue("the rail is partly faded midway: $steps", steps[steps.size / 2] in 1f..(full * 0.9f))
        assertEquals("the rail does not move", 0f, compose.drawnBrightness(view, beside))
        compose.onNodeWithTag("player").assertExists()

        compose.mainClock.advanceTimeBy(64)
        assertEquals(0f, compose.drawnBrightness(view, rail))
    }

    @Test fun theRailFadesOutOnTheEmphasizedDecelerateCurve() {
        shell(motionEnabled = true) { Box(Modifier.fillMaxSize()) }
        val rail = Rect(0f, 0f, 40f, 40f)
        val full = compose.drawnBrightness(view, rail)

        compose.mainClock.autoAdvance = false
        compose.changeState { showRail.value = false }
        compose.mainClock.advanceTimeBy(96)
        // Decelerating: a third of the way in, most of the fade is done (ease-in-out would have 80% left).
        assertTrue("the rail has mostly faded a third in: ${compose.drawnBrightness(view, rail)} of $full", compose.drawnBrightness(view, rail) < full / 2)
        assertTrue("the rail is still fading a third in", compose.drawnBrightness(view, rail) > 0f)
    }

    @Test fun thePlayerFadesOutAcceleratingWhileBrowseDeceleratesIn() {
        showRail.value = false
        val playerArea = Rect(100f, 0f, 140f, 40f)
        shell(motionEnabled = true) {
            Box(Modifier.fillMaxSize()) { Box(Modifier.offset(100.dp, 0.dp).size(40.dp).background(Color.White)) }
        }
        val rail = Rect(0f, 0f, 40f, 40f)
        val playerFull = compose.drawnBrightness(view, playerArea)
        assertTrue(playerFull > 0f)
        assertEquals(0f, compose.drawnBrightness(view, rail))

        compose.mainClock.autoAdvance = false
        compose.changeState { showRail.value = true }
        compose.mainClock.advanceTimeBy(96)
        val playerMidway = compose.drawnBrightness(view, playerArea)
        val railMidway = compose.drawnBrightness(view, rail)
        val railFull = 40f * 40f * 3 * 255
        assertTrue("the player holds on while it accelerates away: $playerMidway of $playerFull", playerMidway > playerFull * 0.7f)
        assertTrue("browse decelerates in: $railMidway of $railFull", railMidway > railFull * 0.6f)

        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs - 96L + 64)
        assertEquals("the exit is over within DestinationExitMs", 0f, compose.drawnBrightness(view, playerArea))
        assertEquals("browse is fully in", railFull, compose.drawnBrightness(view, rail))
    }

    @Test fun theBannerIsPresentFromThePlayersFirstFrameAndFadesInWithItOnly() {
        shell(motionEnabled = true) {
            val context = LocalContext.current
            PlayerChrome(
                mode = PlayerChromeMode.BANNER,
                content = PlayerChromeContent("20:15", liveInfoBarData(context.resources, 1, "One", null, null, false, 1_800, "Programme")),
                timeline = PlayerChromeTimeline.Live(
                    AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0),
                    nowSec = 1_800,
                ),
                actions = PlayerChromeActions(active = false),
                onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
            )
        }
        compose.mainClock.autoAdvance = false
        compose.changeState { showRail.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("player-banner").assertExists()
        val header = compose.tagBounds("player-header")
        val footer = compose.tagBounds("player-footer")
        val footerInk = mutableListOf(compose.drawnBrightness(view, footer))
        repeat(PlayerMotion.DestinationMs / 50) {
            compose.mainClock.advanceTimeBy(50)
            footerInk += compose.drawnBrightness(view, footer)
            assertEquals("no header travel", header, compose.tagBounds("player-header"))
            assertEquals("no footer travel", footer, compose.tagBounds("player-footer"))
        }
        compose.mainClock.advanceTimeBy(64)
        val settled = compose.drawnBrightness(view, footer)
        assertTrue("the Banner fades in with the player: $footerInk", footerInk.first() < settled / 2)
        assertTrue("the Banner fades in steps: $footerInk", footerInk.zipWithNext().all { (a, b) -> b >= a })
        compose.mainClock.advanceTimeBy(500)
        assertEquals("no second animation after the fade", settled, compose.drawnBrightness(view, footer))
    }

    @Test fun withoutAnimationsTheShellsSwapInOneFrame() {
        shell(motionEnabled = false) { Box(Modifier.fillMaxSize().testTag("player")) }
        val rail = Rect(0f, 0f, 40f, 40f)
        assertTrue(compose.drawnBrightness(view, rail) > 0f)

        compose.mainClock.autoAdvance = false
        compose.changeState { showRail.value = false }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0f, compose.drawnBrightness(view, rail))
        compose.onNodeWithTag("player").assertExists()
    }

    @Test fun reversingTheEntryMidFadeReturnsFocusToTheOriginatingItem() {
        shell(
            motionEnabled = true,
            rail = {
                Row {
                    Box(Modifier.size(40.dp).testTag("item-1").focusable())
                    Box(Modifier.size(40.dp).testTag("item-2").focusable())
                }
            },
        ) {
            // Like the player root: it takes focus as soon as it is composed.
            val root = remember { FocusRequester() }
            Box(Modifier.fillMaxSize().testTag("player").focusRequester(root).focusable())
            LaunchedEffect(Unit) { root.requestFocus() }
        }
        compose.onNodeWithTag("item-2").requestFocus().assertIsFocused()

        compose.mainClock.autoAdvance = false
        compose.changeState { showRail.value = false }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("player").assertIsFocused()
        compose.changeState { showRail.value = true }
        compose.mainClock.advanceTimeBy(PlayerMotion.DestinationExitMs + 100L)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithTag("item-2").assertIsFocused()
    }

    private fun shell(
        motionEnabled: Boolean,
        rail: @androidx.compose.runtime.Composable () -> Unit = { Box(Modifier.size(40.dp).background(Color.White)) },
        player: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            view = LocalView.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    MainNavigationShell(
                        showRail = showRail.value,
                        rail = rail,
                        fullScreen = player,
                        motionEnabled = motionEnabled,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

}
