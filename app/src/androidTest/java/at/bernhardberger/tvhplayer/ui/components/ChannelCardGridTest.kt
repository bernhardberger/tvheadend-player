package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.core.Channel
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ChannelCardGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browseGridUsesThreeAcrossGuidanceWidth() {
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                ChannelCardGrid(
                    items = (1..3).map { id ->
                        ChannelCardModel(
                            channel = Channel(id, "Channel $id", id, null),
                            number = id,
                            programmeTitle = "Programme $id",
                        )
                    },
                    imageLoader = imageLoader,
                    onFocusChannel = {},
                    onConfirmChannel = {},
                    modifier = Modifier.size(900.dp, 300.dp),
                )
            }
        }

        val bounds = (1..3).map { id ->
            composeRule.onNodeWithTag("channel-card-$id")
                .assertContentDescriptionEquals("$id Channel $id. Programme $id")
                .fetchSemanticsNode().boundsInRoot
        }
        assertEquals(268f * density, bounds[0].width, 0.5f)
        assertEquals(20f * density, bounds[1].left - bounds[0].right, 0.5f)
        assertEquals(20f * density, bounds[2].left - bounds[1].right, 0.5f)
    }

    @Test
    fun playingAndRecordingIndicatorsCanCoexist() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                ChannelCardGrid(
                    items = listOf(
                        ChannelCardModel(
                            channel = Channel(1, "Channel", 1, null),
                            number = 1,
                            programmeTitle = "Programme",
                            playingNow = true,
                            recordingNow = true,
                        )
                    ),
                    imageLoader = imageLoader,
                    onFocusChannel = {},
                    onConfirmChannel = {},
                    modifier = Modifier.size(300.dp, 240.dp),
                )
            }
        }

        composeRule.onNodeWithTag("channel-card-1")
            .assertContentDescriptionEquals(
                "1 Channel. Programme. Currently playing. Recording now"
            )
            .assertIsSelected()
        composeRule.onNodeWithTag("channel-playing-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("channel-recording-indicator").assertIsDisplayed()
    }

    @Test
    fun progressTrackStopsUsingFocusedColorAfterFocusLeavesTheGrid() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                Row {
                    ChannelCardGrid(
                        items = listOf(
                            ChannelCardModel(
                                channel = Channel(1, "Channel", 1, null),
                                number = 1,
                                programmeTitle = "Programme",
                                progress = 0.25f,
                            )
                        ),
                        imageLoader = imageLoader,
                        onFocusChannel = {},
                        onConfirmChannel = {},
                        modifier = Modifier.size(300.dp, 240.dp),
                    )
                    Button(
                        onClick = {},
                        modifier = Modifier.size(120.dp, 60.dp).testTag("outside-grid"),
                    ) {
                        Text("Outside")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("channel-card-1").requestFocus().assertIsFocused()
        val focusedTrack = composeRule.onNodeWithTag("channel-card-progress-1")
            .captureToImage().toPixelMap().let { it[it.width * 3 / 4, it.height / 2] }

        composeRule.onNodeWithTag("channel-card-1").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("outside-grid").assertIsFocused()
        val unfocusedTrack = composeRule.onNodeWithTag("channel-card-progress-1")
            .captureToImage().toPixelMap().let { it[it.width * 3 / 4, it.height / 2] }

        assertNotEquals(focusedTrack, unfocusedTrack)

        composeRule.onNodeWithTag("outside-grid").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeRule.onNodeWithTag("channel-card-1").assertIsFocused()
    }
}
