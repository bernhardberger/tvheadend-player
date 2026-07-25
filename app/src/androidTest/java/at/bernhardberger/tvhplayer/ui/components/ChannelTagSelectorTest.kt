package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import at.bernhardberger.tvhplayer.htsp.ChannelTagUi
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChannelTagSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chooserSelectsServerTag() {
        var selectedTagId by mutableStateOf<Int?>(null)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagSelector(
                    tags = listOf(ChannelTagUi(id = 7, name = "News", index = 1)),
                    activeTagId = selectedTagId,
                    onSelectTag = { selectedTagId = it },
                )
            }
        }

        composeRule.onNodeWithText("All channels").assertIsDisplayed()
        composeRule.onNodeWithText("All channels").performClick()
        composeRule.onNodeWithText("News").performClick()
        composeRule.runOnIdle { assertEquals(7, selectedTagId) }
    }

    @Test
    fun chooserOmitsAllChannelsWhenThatScopeIsDisabled() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                ChannelTagSelector(
                    tags = listOf(ChannelTagUi(id = 7, name = "News", index = 1)),
                    activeTagId = 7,
                    allChannelsVisible = false,
                    onSelectTag = {},
                )
            }
        }

        composeRule.onNodeWithText("News").performClick()
        assertEquals(
            0,
            composeRule.onAllNodesWithText("All channels").fetchSemanticsNodes().size,
        )
    }
}
