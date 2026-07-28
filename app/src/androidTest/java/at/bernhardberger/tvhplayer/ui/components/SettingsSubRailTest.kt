package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.screens.SettingsRoutes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsSubRailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun categoryFocusChangesRouteAndKeepsDpadNavigationOnTheRail() {
        var selectedRoute = SettingsRoutes.GENERAL
        composeTestRule.setContent {
            var route by remember { mutableStateOf(SettingsRoutes.GENERAL) }
            val contentFocus = remember {
                listOf(
                    SettingsRoutes.GENERAL,
                    SettingsRoutes.OPTIONS,
                    SettingsRoutes.CHANNEL_TAGS,
                    SettingsRoutes.CONNECTION,
                    SettingsRoutes.PLAYER,
                    SettingsRoutes.APPLIANCE,
                    SettingsRoutes.SIMPLE_TV,
                ).associateWith { FocusRequester() }
            }
            val categoryFocus = remember {
                contentFocus.keys.associateWith { FocusRequester() }
            }
            TVHeadendPlayerTheme {
                SettingsSubRail(
                    currentRoute = route,
                    categoryFocusRequesters = categoryFocus,
                    contentFocusRequesters = contentFocus,
                    onNavigate = {
                        selectedRoute = it
                        route = it
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("Language").assertIsFocused()
        composeTestRule.onNodeWithText("Language").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.onNodeWithText("Options").assertIsFocused()
        composeTestRule.runOnIdle {
            assertEquals(SettingsRoutes.OPTIONS, selectedRoute)
        }
        composeTestRule.onNodeWithText("Options").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        // Focus stays on the category rail after its pane updates; Down moves to
        // the next rail item (Channel groups) without jumping into content.
        composeTestRule.onNodeWithText("Channel groups").assertIsFocused()
        composeTestRule.runOnIdle {
            assertEquals(SettingsRoutes.CHANNEL_TAGS, selectedRoute)
        }
    }
}
