package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.SettingsRoutes
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsPaneFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rightFromPlayerCategoryEntersFirstSettingBeforeNestedProfileGroup() {
        composeTestRule.setContent {
            TVHeadendPlayerTheme {
                Row(Modifier.fillMaxSize()) {
                    SettingsSubRail(
                        currentRoute = SettingsRoutes.PLAYER,
                        onNavigate = {},
                    )
                    Spacer(Modifier.width(32.dp))
                    SettingsPane(title = "Player") {
                        SettingsSwitchRow(
                            label = "Timeshift",
                            checked = false,
                            onClick = {},
                        )
                        Column(Modifier.focusGroup()) {
                            ListItem(
                                selected = false,
                                onClick = {},
                                headlineContent = { Text("Direct streaming") },
                            )
                            ListItem(
                                selected = false,
                                onClick = {},
                                headlineContent = { Text("Pass") },
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Player").assertIsFocused()
        composeTestRule.onNodeWithText("Player").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.onNodeWithText("Timeshift").assertIsFocused()
    }
}
