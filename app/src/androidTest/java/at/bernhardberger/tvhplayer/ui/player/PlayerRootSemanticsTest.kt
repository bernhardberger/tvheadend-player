package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Rule
import org.junit.Test

class PlayerRootSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fallbackFocusRootHasAPlayerLabelWithoutHidingItsChildren() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                Box(
                    modifier = Modifier
                        .testTag("player-root")
                        .playerRootSemantics("Live TV player")
                        .focusable(),
                ) {
                    Text("Player child", modifier = Modifier.testTag("player-root-child"))
                }
            }
        }

        composeRule.onNodeWithTag("player-root")
            .assertContentDescriptionEquals("Live TV player")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        composeRule.onNodeWithTag("player-root-child").assertIsDisplayed()
        composeRule.onNodeWithText("Player child").assertIsDisplayed()
    }
}
