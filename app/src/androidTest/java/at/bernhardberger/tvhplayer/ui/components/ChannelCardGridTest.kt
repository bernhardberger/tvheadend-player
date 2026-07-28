package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ChannelCardGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressTrackStopsUsingFocusedColorAfterFocusLeavesTheGrid() {
        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                Row {
                    ChannelCardGrid(
                        items = listOf(
                            ChannelCardModel(
                                channel = ChannelUi(1, "Channel", 1, null),
                                number = 1,
                                programmeTitle = "Programme",
                                progress = 0.25f,
                            )
                        ),
                        imageLoader = imageLoader,
                        onFocusChannel = {},
                        onConfirmChannel = {},
                        columns = 1,
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
    }
}
