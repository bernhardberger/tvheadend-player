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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.runtime.mutableStateOf
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.htsp.EpgEventEntry
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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

        visible.value = true

        composeRule.onNodeWithTag("debug-video-backdrop").assertExists()
    }

    @Test
    fun playbackOptionsUseCompactContextualOverlay() {
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

        assertTrue(panel.right < root.right)
        assertTrue(panel.bottom < root.bottom)
        assertTrue(panel.height < root.height * 0.8f)
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
                    nowEvent = EpgEventEntry(
                        eventId = 1,
                        channelId = 1,
                        start = 3_600,
                        stop = 7_200,
                        title = "Zeit im Bild",
                    ),
                    nextEvent = EpgEventEntry(
                        eventId = 2,
                        channelId = 1,
                        start = 7_200,
                        stop = 9_000,
                        title = "Wetter",
                    ),
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = TimeshiftState(
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
        val programmeEnd = composeRule.onNodeWithTag("player-programme-end")
            .fetchSemanticsNode().boundsInRoot
        val actions = composeRule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot
        val timeline = composeRule.onNodeWithTag("player-timeline").fetchSemanticsNode().boundsInRoot
        val goLive = composeRule.onNodeWithTag("player-go-live").fetchSemanticsNode().boundsInRoot
        val navigation = composeRule.onNodeWithTag("player-navigation-actions")
            .fetchSemanticsNode().boundsInRoot
        val transport = composeRule.onNodeWithTag("player-transport-actions")
            .fetchSemanticsNode().boundsInRoot
        val utilities = composeRule.onNodeWithTag("player-utility-actions")
            .fetchSemanticsNode().boundsInRoot
        val terminal = composeRule.onNodeWithTag("player-terminal-actions")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(picon.left < title.left)
        assertTrue(picon.height > title.height)
        assertTrue(channel.bottom <= title.top)
        assertTrue(title.bottom <= next.top)
        assertTrue(clock.left > title.left)
        assertTrue(clock.height > 0f)
        assertEquals(channel.top, clock.top, 1f)
        assertTrue(clock.bottom <= programmeEnd.top)
        assertTrue(title.bottom < timeline.top)
        assertTrue(timeline.bottom <= actions.top)
        assertTrue(goLive.left >= transport.left)
        assertTrue(goLive.right <= transport.right)
        assertTrue(navigation.right < transport.left)
        assertTrue(transport.right < utilities.left)
        assertTrue(utilities.right + 8f < terminal.left)
        assertEquals(0, composeRule.onAllNodesWithText("Channels").fetchSemanticsNodes().size)
        assertEquals(
            1,
            composeRule.onAllNodesWithText(
                "Up next at ${formatClock(7_200)}: Wetter",
            ).fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithTag("player-channels").requestFocus().performKeyInput {
            pressKey(Key.DirectionUp)
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
                    nowEvent = EpgEventEntry(
                        eventId = 1,
                        channelId = 1,
                        start = 3_600,
                        stop = 7_200,
                        title = "Zeit im Bild",
                    ),
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = TimeshiftState(
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
        assertEquals(1, composeRule.onAllNodesWithText("29:56").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("1:00:00").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("player-seekbar").requestFocus()
        composeRule.waitForIdle()
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("player-programme-progress")
                .fetchSemanticsNodes().size,
        )
        assertEquals(1, composeRule.onAllNodesWithText("29:56").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("1:00:00").fetchSemanticsNodes().size)
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("player-seekbar-thumb")
                .fetchSemanticsNodes().size,
        )
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
                    nowEvent = EpgEventEntry(
                        eventId = 1,
                        channelId = 1,
                        start = 3_600,
                        stop = 7_200,
                        title = eventTitle.value,
                    ),
                    nextEvent = null,
                    nowSec = 5_400,
                    controlsVisible = true,
                    optionsOpen = false,
                    onOpenChannels = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    onOpenOptions = {},
                    timeshiftState = TimeshiftState(),
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
}
