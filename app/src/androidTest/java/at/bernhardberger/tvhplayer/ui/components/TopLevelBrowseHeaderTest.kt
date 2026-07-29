package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TopLevelBrowseHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleAnchorDoesNotMoveWhenHeaderHasActions() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                Column {
                    TopLevelBrowseHeader(
                        title = "Channels",
                        modifier = Modifier.testTag("plain-header"),
                    )
                    TopLevelBrowseHeader(
                        title = "Guide",
                        modifier = Modifier.testTag("action-header"),
                        actions = {
                            Box(Modifier.size(40.dp))
                        },
                    )
                }
            }
        }

        val plainHeader = composeRule.onNodeWithTag("plain-header")
            .fetchSemanticsNode().boundsInRoot
        val actionHeader = composeRule.onNodeWithTag("action-header")
            .fetchSemanticsNode().boundsInRoot
        val plainTitle = composeRule.onNodeWithText("Channels")
            .fetchSemanticsNode().boundsInRoot
        val actionTitle = composeRule.onNodeWithText("Guide")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(plainHeader.height, actionHeader.height, 0.5f)
        assertEquals(
            plainTitle.top - plainHeader.top,
            actionTitle.top - actionHeader.top,
            0.5f,
        )
    }
}
