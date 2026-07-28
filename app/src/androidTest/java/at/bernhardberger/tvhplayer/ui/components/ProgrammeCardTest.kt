package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvhplayer.core.HomeCardItem
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProgrammeCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun programmeCardUsesFourAcrossGuidanceWidth() {
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            val imageLoader = ImageLoader.Builder(LocalContext.current).build()
            TVHeadendPlayerTheme {
                ProgrammeCard(
                    item = HomeCardItem(
                        key = "programme",
                        channelId = 1,
                        channelNumber = 1,
                        channelName = "Channel",
                        piconPath = null,
                        title = "Programme",
                        remainingMinutes = 10,
                        progress = 0.5f,
                        startSec = null,
                        stopSec = null,
                        recordingId = null,
                        recordingNow = false,
                        playable = true,
                    ),
                    imageLoader = imageLoader,
                    onClick = {},
                    testTag = "programme-card",
                )
            }
        }

        val width = composeRule.onNodeWithTag("programme-card")
            .assertContentDescriptionEquals("1 Channel. Programme. 10 min left")
            .fetchSemanticsNode().boundsInRoot.width
        assertEquals(196f * density, width, 0.5f)
    }
}
