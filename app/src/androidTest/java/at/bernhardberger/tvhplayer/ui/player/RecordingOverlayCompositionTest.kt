package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.C
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordingOverlayCompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordingHeaderUsesSameSlotOrderAsLiveOverlay() {
        setRecordingOverlay("Recording title")

        val channel = bounds("recording-channel-identity")
        val title = bounds("recording-programme-title")
        val subtitle = bounds("recording-subtitle")
        val clock = bounds("recording-clock")

        assertTrue(channel.bottom <= title.top)
        assertTrue(title.bottom <= subtitle.top)
        assertEquals(channel.top, clock.top, 1f)
    }

    @Test
    fun recordingHeaderKeepsItsAnchorsWhenTheTitleWraps() {
        val title = mutableStateOf("Short title")
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                RecordingOverlayControls(
                    imageLoader = imageLoader,
                    piconPath = null,
                    title = title.value,
                    subtitle = null,
                    channelName = "Channel",
                    positionMs = 30_000L,
                    durationMs = 60_000L,
                    nowSec = 5_400L,
                    isPlaying = true,
                    controlsVisible = true,
                    optionsOpen = false,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    showStop = true,
                    onOpenOptions = {},
                    onOpenInfo = {},
                )
            }
        }
        composeRule.waitForIdle()
        val shortEyebrow = bounds("recording-channel-identity")
        val shortPicon = bounds("recording-picon")

        composeRule.runOnIdle {
            title.value = "A deliberately long recording title that wraps onto a second line " +
                "without moving the header anchors"
        }
        composeRule.waitForIdle()
        val longEyebrow = bounds("recording-channel-identity")
        val longPicon = bounds("recording-picon")
        val clock = bounds("recording-clock")

        assertEquals(shortEyebrow.top, longEyebrow.top, 1f)
        assertEquals(shortPicon.top, longPicon.top, 1f)
        assertEquals(longEyebrow.top, clock.top, 1f)
    }

    @Test
    fun recordingActionsFormOneClusterWithSeparatedStop() {
        setRecordingOverlay("Recording title")

        val transport = bounds("recording-transport-actions")
        val utilities = bounds("recording-utility-actions")
        val terminal = bounds("recording-terminal-actions")

        assertTrue(transport.right < utilities.left)
        assertTrue(utilities.right + 8f < terminal.left)
    }

    private fun setRecordingOverlay(title: String) {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                RecordingOverlayControls(
                    imageLoader = imageLoader,
                    piconPath = null,
                    title = title,
                    subtitle = "Episode subtitle",
                    channelName = "Channel",
                    positionMs = 30_000L,
                    durationMs = C.TIME_UNSET,
                    nowSec = 5_400L,
                    isPlaying = true,
                    controlsVisible = true,
                    optionsOpen = false,
                    onTogglePlayPause = {},
                    onSeek = {},
                    onStopPlayback = {},
                    onUserInteraction = {},
                    showStop = true,
                    onOpenOptions = {},
                    onOpenInfo = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String) =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
