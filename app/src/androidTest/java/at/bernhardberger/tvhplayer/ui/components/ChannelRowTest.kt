package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class ChannelRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsChannelAndProgramme_andConfirmsOnClick() {
        var confirmed = false
        composeTestRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                ChannelRow(
                    modifier = Modifier.testTag("row"),
                    number = 3,
                    name = "ČT1 HD",
                    programTitle = "Večerní zprávy",
                    progress = 0.5f,
                    imageLoader = imageLoader,
                    piconPath = null,
                    focused = false,
                    onFocus = {},
                    onConfirm = { confirmed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("3  ČT1 HD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Večerní zprávy").assertIsDisplayed()

        composeTestRule.onNodeWithTag("row").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun statusMarkersPreserveProgressWidth_andCoexistWithLongTitle() {
        composeTestRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                Column {
                    repeat(3) { index ->
                        ChannelRow(
                            modifier = Modifier
                                .width(400.dp)
                                .testTag("status-row-$index"),
                            number = index + 1,
                            name = if (index == 2) {
                                "Short title"
                            } else {
                                "A very long localized channel title that must ellipsize"
                            },
                            programTitle = "Večerní zprávy",
                            progress = 0.5f,
                            imageLoader = imageLoader,
                            piconPath = null,
                            focused = false,
                            recordingNow = index != 1,
                            playingNow = index != 1,
                            onFocus = {},
                            onConfirm = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onAllNodesWithTag("channel-picon")[0]
            .assertLeftPositionInRootIsEqualTo(20.dp)
        composeTestRule.onAllNodesWithTag("channel-playing-indicator")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("channel-recording-indicator")[0].assertIsDisplayed()
        composeTestRule.onNodeWithTag("status-row-0")
            .assertContentDescriptionEquals("Currently playing", "Recording now")
            .assertIsSelected()
        composeTestRule.onNodeWithTag("status-row-1").assertIsNotSelected()
        val progressNodes = composeTestRule.onAllNodesWithTag("channel-progress")
        val statusWidth = progressNodes[0].fetchSemanticsNode().boundsInRoot.width
        val plainWidth = progressNodes[1].fetchSemanticsNode().boundsInRoot.width
        assertEquals(plainWidth, statusWidth, 0.5f)
        val playingIndicators = composeTestRule.onAllNodesWithTag("channel-playing-indicator")
        val longTitleIndicatorLeft = playingIndicators[0].fetchSemanticsNode().boundsInRoot.left
        val shortTitleIndicatorLeft = playingIndicators[1].fetchSemanticsNode().boundsInRoot.left
        assertEquals(shortTitleIndicatorLeft, longTitleIndicatorLeft, 0.5f)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dpadMovesBetweenRowsWithoutAContainerFocusStop() {
        var focusedNumber = 0
        composeTestRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                Column {
                    repeat(2) { index ->
                        val number = index + 1
                        ChannelRow(
                            modifier = Modifier.testTag("row-$number"),
                            number = number,
                            name = "Channel $number",
                            programTitle = "Programme $number",
                            progress = 0.5f,
                            imageLoader = imageLoader,
                            piconPath = null,
                            focused = focusedNumber == number,
                            onFocus = { focusedNumber = number },
                            onConfirm = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("row-1").requestFocus()
        composeTestRule.onNodeWithTag("row-1").assertIsFocused()
        composeTestRule.onNodeWithTag("row-1").assertIsNotSelected()
        composeTestRule.onNodeWithTag("row-1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithTag("row-2").assertIsFocused()
        assertEquals(2, focusedNumber)
    }

    @Test
    fun ambientProgressDoesNotDrawAStopMarkerAtTheTrackEnd() {
        composeTestRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                ChannelRow(
                    modifier = Modifier.width(400.dp),
                    number = 1,
                    name = "Channel",
                    programTitle = "Programme",
                    progress = 0.25f,
                    imageLoader = imageLoader,
                    piconPath = null,
                    focused = false,
                    onFocus = {},
                    onConfirm = {},
                )
            }
        }

        val image = composeTestRule.onNodeWithTag("channel-progress").captureToImage()
        val pixels = image.toPixelMap()
        val y = image.height / 2
        val fill = pixels[image.width / 8, y]
        val track = pixels[image.width / 2, y]
        val trackEnd = pixels[image.width - 2, y]

        assertNotEquals(fill, trackEnd)
        for (x in image.width / 2 until image.width - 4) {
            assertEquals("unexpected marker at x=$x", track, pixels[x, y])
        }
    }
}
