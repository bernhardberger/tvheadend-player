package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.htsp.ChannelTagUi
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChannelTagBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allChannelsAndServerTagsRemainSelectable() {
        var selectedTagId by mutableStateOf<Int?>(null)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagBar(
                    tags = listOf(ChannelTagUi(id = 7, name = "News", index = 1)),
                    activeTagId = selectedTagId,
                    onSelectTag = { selectedTagId = it },
                )
            }
        }

        composeRule.onNodeWithText("All channels").assertIsDisplayed()
        composeRule.onNodeWithText("News").performClick()
        composeRule.runOnIdle { assertEquals(7, selectedTagId) }
    }
}
