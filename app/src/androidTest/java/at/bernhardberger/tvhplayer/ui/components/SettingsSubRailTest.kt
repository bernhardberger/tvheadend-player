package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.SettingsRoutes
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsSubRailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun categoryChangeKeepsDpadNavigationOnTheRail() {
        composeTestRule.setContent {
            var route by remember { mutableStateOf(SettingsRoutes.GENERAL) }
            TVHeadendPlayerTheme {
                SettingsSubRail(
                    currentRoute = route,
                    onNavigate = { route = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Language").assertIsFocused()
        composeTestRule.onNodeWithText("Language").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.onNodeWithText("Options").assertIsFocused()
        composeTestRule.onNodeWithText("Options").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        // Focus stays on the category rail after activation; Down moves to the
        // next rail item (Channel groups) without jumping into content.
        composeTestRule.onNodeWithText("Channel groups").assertIsFocused()
    }
}
