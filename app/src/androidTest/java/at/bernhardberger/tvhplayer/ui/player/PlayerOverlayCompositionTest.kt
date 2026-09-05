package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.runtime.mutableStateOf
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvOverlaySidePadding
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class PlayerOverlayCompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun debugVideoBackdropExistsOnlyWhenRequested() {
        val visible = mutableStateOf(false)
        composeRule.setContent {
            DebugVideoBackdrop(
                visible = visible.value,
                modifier = Modifier.fillMaxSize(),
            )
        }

        composeRule.onNodeWithTag("debug-video-backdrop").assertDoesNotExist()

        composeRule.runOnIdle { visible.value = true }

        composeRule.onNodeWithTag("debug-video-backdrop").assertExists()
    }

    @Test
    fun playbackOptionsAttachToTheRightEdgeAtFullHeight() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                PlaybackOptionsOverlayFrame {
                    androidx.tv.material3.Text("Playback options")
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("playback-options-overlay")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(root.right, panel.right, 1f)
        assertEquals(root.bottom, panel.bottom, 1f)
        assertEquals(root.height, panel.height, 1f)
        assertTrue(panel.width < root.width / 2f)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun liveOverlaySeparatesIdentityTimelineAndControls() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "ORF 1 HD",
                    piconPath = null,
                    nowEvent = event(1, 3_600, 7_200, "Zeit im Bild"),
                    nextEvent = event(2, 7_200, 9_000, "Wetter"),
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(
                        available = true,
                        bufferStartMs = -3_600_000,
                        positionMs = -30_000,
                        liveEdgeMs = 0,
                    ),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        composeRule.waitForIdle()
        val picon = composeRule.onNodeWithTag("player-picon").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithTag("player-programme-title")
            .fetchSemanticsNode().boundsInRoot
        val channel = composeRule.onNodeWithTag("player-channel-identity")
            .fetchSemanticsNode().boundsInRoot
        val next = composeRule.onNodeWithTag("player-next-programme")
            .fetchSemanticsNode().boundsInRoot
        val clock = composeRule.onNodeWithTag("player-clock").fetchSemanticsNode().boundsInRoot
        val actions = composeRule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("player-seekbar").fetchSemanticsNode().boundsInRoot
        val goLive = composeRule.onNodeWithTag("player-go-live").fetchSemanticsNode().boundsInRoot
        val icons = listOf("player-info", "player-settings", "player-record", "player-stop")
            .map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val sidePaddingPx = with(composeRule.density) { TvOverlaySidePadding.toPx() }

        assertEquals(root.left + sidePaddingPx, picon.left, 1f)
        assertEquals(root.right - sidePaddingPx, clock.right, 1f)
        assertTrue(picon.left < title.left)
        assertTrue(picon.height > title.height)
        assertTrue(channel.bottom <= title.top)
        assertTrue(title.bottom <= next.top)
        assertTrue(clock.left > title.left)
        assertTrue(clock.height > 0f)
        assertEquals(channel.top, clock.top, 1f)
        assertTrue(title.bottom < timeline.top)
        assertTrue(actions.bottom <= timeline.top)
        assertTrue(icons.zipWithNext().all { (left, right) -> left.right < right.left })
        assertTrue(goLive.left > icons.last().right)
        composeRule.onNodeWithTag("player-transport-actions").assertDoesNotExist()
        assertEquals(0, composeRule.onAllNodesWithText("Channels").fetchSemanticsNodes().size)
        assertEquals(
            0,
            composeRule.onAllNodesWithText(
                "Next ${formatClock(7_200)} - ${formatClock(9_000)}: Wetter",
            ).fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithText("Programme timing unavailable").assertExists()

        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
        composeRule.onNodeWithTag("player-info").requestFocus().performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.onNodeWithTag("player-seekbar").assertIsFocused()
    }

    @Test
    fun timelineKeepsOneAxisWhetherFocusedOrNot() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "ORF 1 HD",
                    piconPath = null,
                    nowEvent = event(1, 3_600, 7_200, "Zeit im Bild"),
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(
                        available = true,
                        bufferStartMs = -3_600_000,
                        positionMs = -4_000,
                        liveEdgeMs = 0,
                    ),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Live").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("player-seekbar").requestFocus()
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText("Live").fetchSemanticsNodes().size)
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("player-seekbar-thumb")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun liveContextLabelAppearsOnlyForAFocusedNonObviousAction() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "ORF 1 HD",
                    piconPath = null,
                    nowEvent = null,
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(
                        available = true,
                        bufferStartMs = -60_000,
                        positionMs = -30_000,
                        liveEdgeMs = 0,
                    ),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        val actionsBefore = composeRule.onNodeWithTag("player-actions")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("player-action-context-label").assertDoesNotExist()
        composeRule.onNodeWithTag("player-info").requestFocus()
        composeRule.onNodeWithTag("player-action-context-label").assertExists()
        composeRule.onNodeWithText("Info", useUnmergedTree = true).assertExists()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-settings").assertIsFocused()
        composeRule.onNodeWithText("Settings", useUnmergedTree = true).assertExists()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-record").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag("player-stop").assertIsFocused()
        composeRule.onNodeWithTag("player-action-context-label").assertExists()
        val actionsAfter = composeRule.onNodeWithTag("player-actions")
            .fetchSemanticsNode().boundsInRoot
        val contextLabel = composeRule.onNodeWithTag("player-action-context-label")
            .fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("player-seekbar")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(actionsBefore, actionsAfter)
        assertTrue(contextLabel.left >= actionsAfter.left)
        assertTrue(contextLabel.right <= actionsAfter.right)
        assertTrue(actionsAfter.bottom <= timeline.top)
        assertTrue(contextLabel.top >= actionsAfter.top)

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("player-record").assertIsFocused()
    }

    @Test
    fun liveHeaderKeepsItsAnchorsWhenTheTitleWraps() {
        val eventTitle = mutableStateOf("Short title")
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = imageLoader,
                    channelNumber = 1,
                    channelName = "Channel",
                    piconPath = null,
                    nowEvent = event(1, 3_600, 7_200, eventTitle.value),
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(),
                    timeshiftFeedback = null,
                    onToggleTimeshiftPause = {},
                    onSeekTimeshift = {},
                    onGoLive = {},
                )
            }
        }

        composeRule.waitForIdle()
        val shortEyebrow = composeRule.onNodeWithTag("player-channel-identity")
            .fetchSemanticsNode().boundsInRoot
        val shortPicon = composeRule.onNodeWithTag("player-picon")
            .fetchSemanticsNode().boundsInRoot

        composeRule.runOnIdle {
            eventTitle.value = "A deliberately long programme title that wraps onto a second " +
                "line without moving the header anchors"
        }
        composeRule.waitForIdle()
        val longEyebrow = composeRule.onNodeWithTag("player-channel-identity")
            .fetchSemanticsNode().boundsInRoot
        val longPicon = composeRule.onNodeWithTag("player-picon")
            .fetchSemanticsNode().boundsInRoot
        val clock = composeRule.onNodeWithTag("player-clock").fetchSemanticsNode().boundsInRoot

        assertEquals(shortEyebrow.top, longEyebrow.top, 1f)
        assertEquals(shortPicon.top, longPicon.top, 1f)
        assertEquals(longEyebrow.top, clock.top, 1f)
    }

    private fun event(id: Int, start: Long, stop: Long, title: String) = EpgEvent.create(
        id = EventId(id.toLong()),
        channelId = ChannelId(1),
        start = Instant.fromEpochSeconds(start),
        stop = Instant.fromEpochSeconds(stop),
        title = title,
    )
}
