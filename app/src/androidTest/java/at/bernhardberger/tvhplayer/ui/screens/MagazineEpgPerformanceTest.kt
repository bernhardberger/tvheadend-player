package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Rule
import org.junit.Test

class MagazineEpgPerformanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun syntheticThreeHundredChannelHeadersRemainVirtualizedAndScrollable() {
        val channels = (1..300).map { number ->
            ChannelUi(
                id = number,
                name = "Channel $number",
                number = number,
                icon = null,
            )
        }

        composeRule.setContent {
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                LazyRow(Modifier.testTag("magazine-columns")) {
                    items(channels, key = { it.id }) { channel ->
                        Box(Modifier.width(270.dp)) {
                            MagazineChannelHeader(
                                channel = channel,
                                number = channel.number,
                                imageLoader = imageLoader,
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("magazine-columns").performScrollToIndex(299)
        composeRule.onNodeWithText("Channel 300").assertIsDisplayed()
    }
}
