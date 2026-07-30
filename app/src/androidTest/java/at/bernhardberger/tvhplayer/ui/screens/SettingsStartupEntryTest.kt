package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.MainStartupComposition
import at.bernhardberger.tvhplayer.ui.MainStartupCompositionState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsStartupEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ordinaryEntryStartsGeneralAndFocusesItsSemanticCategory() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(
                    startRoute = SettingsRoutes.GENERAL,
                    initialFocusEnabled = true,
                    showSimpleTvSettings = true,
                ) { route, focusRequester ->
                    Button(
                        onClick = {},
                        modifier = Modifier.focusRequester(focusRequester),
                    ) {
                        Text("Content $route")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Language").assertIsSelected().assertIsFocused()
        composeRule.onNodeWithText("Content ${SettingsRoutes.GENERAL}").assertExists()
        composeRule.onNodeWithText("Content ${SettingsRoutes.CONNECTION}").assertDoesNotExist()
    }

    @Test
    fun startupEntryStartsConnectionAndOpeningActivationCannotInvokeItsFirstControl() {
        var settingsVisible by mutableStateOf(false)
        var startupActions = 0
        var firstConnectionControlInvocations = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                if (settingsVisible) {
                    SettingsScreenNavigation(
                        startRoute = SettingsRoutes.CONNECTION,
                        initialFocusEnabled = true,
                        showSimpleTvSettings = true,
                    ) { route, focusRequester ->
                        Button(
                            onClick = {
                                if (route == SettingsRoutes.CONNECTION) {
                                    firstConnectionControlInvocations++
                                }
                            },
                            modifier = Modifier.focusRequester(focusRequester),
                        ) {
                            Text("First control $route")
                        }
                    }
                } else {
                    MainStartupComposition(
                        state = MainStartupCompositionState(
                            presentation = MainStartupPresentation.Actionable(
                                messageKind = MainStartupMessageKind.CONFIGURATION_REQUIRED,
                                actions = listOf(MainStartupActionId.CONNECTION_SETTINGS),
                            ),
                            navigationStartDestination = null,
                            navigationAllowed = false,
                        ),
                        simpleTvActive = false,
                        onBack = {},
                        onAction = { action ->
                            if (action == MainStartupActionId.CONNECTION_SETTINGS) {
                                startupActions++
                                settingsVisible = true
                            }
                        },
                        registerActivityKeyContract = { {} },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Connection settings").performKeyInput {
            pressKey(Key.Enter)
        }

        composeRule.onNodeWithText("Connection").assertIsSelected().assertIsFocused()
        composeRule.onNodeWithText("First control ${SettingsRoutes.CONNECTION}").assertExists()
        composeRule.onNodeWithText("First control ${SettingsRoutes.GENERAL}").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, startupActions)
            assertEquals(0, firstConnectionControlInvocations)
        }
    }
}
