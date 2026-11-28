package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.testutil.changeState
import at.bernhardberger.tvhplayer.testutil.drawnBrightness
import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.testutil.tagBounds
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Banner and the controls are modes of one chrome. */
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

    @Test fun thePassiveBannerHandsOffToPrewarmedControlsThatHoldTheirClockAndPositionWhenHidden() {
        var mode by mutableStateOf(PlayerChromeMode.BANNER)
        var nowSec by mutableStateOf(PlayerChromeClockSec)
        var positionMs by mutableStateOf(-300_000L)
        var feedback by mutableStateOf<String?>(null)
        var controlsCompositions = 0
        var controlsMounts = 0
        var soughtAt: Long? = null
        lateinit var view: View
        compose.setContent {
            val context = LocalContext.current
            view = LocalView.current
            val tickPosition = positionMs
            val tickTime = nowSec
            val decoration = remember {
                @Composable { _: () -> Float, controls: @Composable () -> Unit ->
                    SideEffect { controlsCompositions++ }
                    DisposableEffect(Unit) { controlsMounts++; onDispose {} }
                    Box(Modifier.fillMaxSize()) {
                        controls()
                        Box(Modifier.align(Alignment.TopStart).size(8.dp).background(Color.White))
                        Box(Modifier.align(Alignment.BottomStart).size(8.dp).background(Color.White))
                    }
                }
            }
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = mode,
                    content = PlayerChromeContent(nowSec,
                        liveInfoBarData(context.resources, 1, "One", null, null, 1_800, "Programme for this evening")),
                    timeline = PlayerChromeTimeline.Live(
                        AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = positionMs, liveEdgeMs = 0),
                        nowSec = nowSec,
                        feedback = feedback,
                    ),
                    actions = PlayerChromeActions(active = mode == PlayerChromeMode.CONTROLS),
                    onTogglePause = {}, onSeek = { soughtAt = tickPosition + it }, onStop = {},
                    onInfo = { soughtAt = tickTime }, onOptions = {}, onInteraction = {},
                    controlsDecoration = decoration,
                    tuneWarmupToken = { 1 },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("player-banner").assertExists()
        compose.onNodeWithTag("player-seekbar").assertDoesNotExist()
        compose.onNodeWithTag("player-actions").assertDoesNotExist()
        compose.onAllNodes(isFocusable()).assertCountEquals(0)
        assertEquals("the controls are already mounted behind the Banner", 1, controlsMounts)
        compose.onNodeWithTag("player-pause", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.RequestFocus) { assertFalse(it()) }

        val bannerCompositions = controlsCompositions
        repeat(3) {
            compose.changeState { nowSec += 60; positionMs += 10_000 }
            compose.mainClock.advanceTimeBy(1_000)
            assertEquals("retained controls paint no header behind the Banner",
                0f, compose.drawnBrightness(view, Rect(0f, 0f, 8f, 8f)))
            assertEquals("retained controls paint no footer behind the Banner",
                0f, compose.drawnBrightness(view, Rect(0f, view.height - 8f, 8f, view.height.toFloat())))
        }
        assertEquals("Banner ticks leave the controls held", bannerCompositions, controlsCompositions)
        compose.changeState { feedback = "Seek" }
        compose.waitForIdle()

        fun titleInk(): Float {
            val title = compose.tagBounds("player-info-title")
            // The short seek label doesn't reach the right half of this long programme title.
            return compose.drawnBrightness(view, Rect(title.center.x, title.top, title.right, title.bottom))
        }
        compose.mainClock.autoAdvance = false
        compose.changeState { mode = PlayerChromeMode.CONTROLS }
        val handoffInk = List(3) {
            compose.mainClock.advanceTimeByFrame()
            titleInk()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val hiddenTitleInk = titleInk()
        compose.changeState { feedback = null }
        compose.waitForIdle()
        assertTrue("the sampled region contains the programme when exposed (${titleInk()} vs $hiddenTitleInk)",
            titleInk() > hiddenTitleInk)
        assertTrue("seek feedback hides the programme from the first handoff frame: $handoffInk vs $hiddenTitleInk",
            handoffInk.all { it <= hiddenTitleInk })
        compose.onNodeWithTag("player-banner").assertDoesNotExist()
        compose.onAllNodesWithTag("player-info-bar", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("player-seekbar").assertExists()
        compose.onNodeWithTag("player-pause").assertIsFocused()
        assertEquals("the Banner hands off without remounting the controls", 1, controlsMounts)
        fun clock() = compose.onNodeWithTag("player-top-cluster", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        fun progressWidth() = compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true)
            .fetchSemanticsNode().size.width
        val shownClock = clock()
        val shownProgress = progressWidth()

        compose.mainClock.autoAdvance = false
        compose.changeState { mode = PlayerChromeMode.HIDDEN }
        compose.mainClock.advanceTimeByFrame()
        // Leaving, the controls keep their layout while they fade.
        compose.onNodeWithTag("player-actions", useUnmergedTree = true).assertExists()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("player-footer").assertDoesNotExist()
        compose.onAllNodesWithTag("player-info-bar").assertCountEquals(0)
        assertEquals(
            "The hidden clock should stay mounted without exposing semantics", 1,
            compose.onAllNodesWithTag("player-top-cluster", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        val hiddenCompositions = controlsCompositions

        repeat(5) {
            compose.changeState {
                nowSec += 60
                positionMs += 30_000
            }
            compose.mainClock.advanceTimeBy(1_000)
        }
        compose.waitForIdle()
        assertEquals(shownClock, clock())
        assertEquals(shownProgress, progressWidth())
        assertEquals("Clock and position ticks must not recompose the retained controls", hiddenCompositions, controlsCompositions)

        compose.mainClock.autoAdvance = false
        compose.changeState { mode = PlayerChromeMode.CONTROLS }
        compose.settleFrames(1, 16L)
        // The header starts offscreen; use the whole surface to detect the first ink from chrome.
        val area = Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())
        val (firstClock, firstProgress) = requireNotNull((1..8).firstNotNullOfOrNull {
            compose.mainClock.advanceTimeByFrame()
            if (compose.drawnBrightness(view, area) > 0f) clock() to progressWidth() else null
        }) { "The reveal must draw the controls" }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val currentClock = clock()
        val currentProgress = progressWidth()
        val trackWidth = compose.onNodeWithTag("player-timeline-track", useUnmergedTree = true)
            .fetchSemanticsNode().size.width
        assertEquals("the settled clock shows the advanced fixture time", formatClock(nowSec),
            currentClock.single().substringBefore(","))
        assertEquals("the settled bar shows the advanced fixture position",
            trackWidth * (positionMs + 600_000L) / 600_000f, currentProgress.toFloat(), 1f)
        assertEquals("The first reveal clock must match current time", currentClock, firstClock)
        assertEquals("The first reveal position must match current position", currentProgress, firstProgress)
        assertEquals(null, soughtAt)
        compose.onNodeWithTag("player-pause").assertIsFocused()
    }
}
