package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Rule
import org.junit.Test

class SettingsSwitchRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun checkedRow_exposesSwitchRoleAndOnState() {
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                SettingsSwitchRow(
                    label = "Match content frame rate",
                    checked = true,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Match content frame rate")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Switch,
                )
            )
            .assertIsOn()
    }

    @Test
    fun uncheckedRow_exposesOffState() {
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                SettingsSwitchRow(
                    label = "Match content frame rate",
                    checked = false,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Match content frame rate").assertIsOff()
    }
}
