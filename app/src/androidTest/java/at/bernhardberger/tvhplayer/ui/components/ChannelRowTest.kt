package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
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

        composeTestRule.onNodeWithText("ČT1 HD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Večerní zprávy").assertIsDisplayed()

        composeTestRule.onNodeWithTag("row").performClick()
        assertTrue(confirmed)
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
        val trackEnd = pixels[image.width - 2, y]

        assertNotEquals(fill, trackEnd)
    }
}
