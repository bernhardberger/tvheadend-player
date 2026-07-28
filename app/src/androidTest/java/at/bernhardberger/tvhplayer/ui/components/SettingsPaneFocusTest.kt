package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    fun okFromPlayerCategoryEntersFirstSettingBeforeNestedProfileGroup() {
        composeTestRule.setContent {
            val contentFocus = remember { FocusRequester() }
            val contentFocusRequesters = remember {
                listOf(
                    SettingsRoutes.GENERAL,
                    SettingsRoutes.OPTIONS,
                    SettingsRoutes.CHANNEL_TAGS,
                    SettingsRoutes.CONNECTION,
                    SettingsRoutes.PLAYER,
                    SettingsRoutes.APPLIANCE,
                    SettingsRoutes.SIMPLE_TV,
                ).associateWith { route ->
                    if (route == SettingsRoutes.PLAYER) contentFocus else FocusRequester()
                }
            }
            val categoryFocusRequesters = remember {
                contentFocusRequesters.keys.associateWith { FocusRequester() }
            }
            TVHeadendPlayerTheme {
                Row(Modifier.fillMaxSize()) {
                    SettingsSubRail(
                        currentRoute = SettingsRoutes.PLAYER,
                        categoryFocusRequesters = categoryFocusRequesters,
                        contentFocusRequesters = contentFocusRequesters,
                        onNavigate = {},
                    )
                    Spacer(Modifier.width(32.dp))
                    SettingsPane(title = "Player") {
                        SettingsSwitchRow(
                            label = "Timeshift",
                            checked = false,
                            onClick = {},
                            modifier = Modifier.focusRequester(contentFocus),
                        )
                        SettingsSwitchRow(
                            label = "Match content frame rate",
                            checked = true,
                            onClick = {},
                        )
                        Column {
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
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.onNodeWithText("Timeshift").assertIsFocused()
        composeTestRule.onNodeWithText("Timeshift").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithText("Match content frame rate").assertIsFocused()
        composeTestRule.onNodeWithText("Match content frame rate").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithText("Direct streaming").assertIsFocused()
        composeTestRule.onNodeWithText("Direct streaming").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.onNodeWithText("Match content frame rate").assertIsFocused()
        composeTestRule.onNodeWithText("Match content frame rate").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.onNodeWithText("Timeshift").assertIsFocused()
        composeTestRule.onNodeWithText("Timeshift").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeTestRule.onNodeWithText("Player").assertIsFocused()
        composeTestRule.onNodeWithText("Player").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.onNodeWithText("Timeshift").assertIsFocused()
    }
}
