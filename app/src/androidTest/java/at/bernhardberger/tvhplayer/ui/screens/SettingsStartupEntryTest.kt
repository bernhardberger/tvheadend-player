package at.bernhardberger.tvhplayer.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.ui.MainStartupComposition
import at.bernhardberger.tvhplayer.ui.MainStartupCompositionState
import at.bernhardberger.tvhplayer.ui.AppNavKey
import at.bernhardberger.tvhplayer.ui.ChannelsKey
import at.bernhardberger.tvhplayer.ui.SettingsKey
import at.bernhardberger.tvhplayer.ui.SettingsSection
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.navigateTopLevel
import at.bernhardberger.tvhplayer.ui.popNavigation
import at.bernhardberger.tvhplayer.ui.rememberAppNavBackStack
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsAppliance
import at.bernhardberger.tvhplayer.ui.screens.settings.SettingsGeneral
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SettingsStartupEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ordinaryEntryStartsGeneralAndFocusesItsSemanticCategory() {
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(
                    currentSection = SettingsSection.GENERAL,
                    initialFocusEnabled = true,
                    onNavigate = {},
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

        composeRule.onNodeWithText("General").assertIsSelected().assertIsFocused()
        composeRule.onNodeWithText("Content ${SettingsSection.GENERAL}").assertExists()
        composeRule.onNodeWithText("Content ${SettingsSection.CONNECTION}").assertDoesNotExist()
    }

    @Test
    fun generalCategoryEntersProductionFirstLanguageControl() {
        val settingsStore = UiSettingsStore(composeRule.activity.applicationContext)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(
                    currentSection = SettingsSection.GENERAL,
                    onNavigate = {},
                ) { _, focusRequester ->
                    SettingsGeneral(
                        initialFocusRequester = focusRequester,
                        settingsStore = settingsStore,
                    )
                }
            }
        }

        composeRule.onNode(hasText("General") and hasClickAction()).performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.language_follow_system)
        ).assertIsFocused()
    }

    @Test
    fun applianceCategoryEntersAutoStartAndReachesAccessibilitySettings() {
        val context = composeRule.activity.applicationContext
        val settingsStore = UiSettingsStore(context)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                SettingsScreenNavigation(
                    currentSection = SettingsSection.APPLIANCE,
                    onNavigate = {},
                ) { _, focusRequester ->
                    SettingsAppliance(
                        initialFocusRequester = focusRequester,
                        settingsStore = settingsStore,
                    )
                }
            }
        }

        composeRule.onNode(hasText("Appliance") and hasClickAction()).performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.auto_start_playback)
        ).assertIsFocused()

        composeRule.onRoot().performKeyInput {
            repeat(12) { pressKey(Key.DirectionDown) }
        }
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.open_accessibility_settings)
        ).assertIsFocused()
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
                        currentSection = SettingsSection.CONNECTION,
                        initialFocusEnabled = true,
                        onNavigate = {},
                    ) { route, focusRequester ->
                        Button(
                            onClick = {
                                if (route == SettingsSection.CONNECTION) {
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
        composeRule.onNodeWithText("First control ${SettingsSection.CONNECTION}").assertExists()
        composeRule.onNodeWithText("First control ${SettingsSection.GENERAL}").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, startupActions)
            assertEquals(0, firstConnectionControlInvocations)
        }
    }

    @Test
    fun backReturnsFromSettingsContentBeforeDelegatingToTheShell() {
        var shellBackCount = 0
        composeRule.setContent {
            TVHeadendPlayerTheme {
                BackHandler { shellBackCount++ }
                SettingsScreenNavigation(
                    currentSection = SettingsSection.GENERAL,
                    onNavigate = {},
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

        composeRule.onNodeWithText("General").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText("Content ${SettingsSection.GENERAL}").assertIsFocused()

        dispatchBack()
        composeRule.onNodeWithText("General").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, shellBackCount) }

        dispatchBack()
        composeRule.runOnIdle { assertEquals(1, shellBackCount) }
    }

    @Test
    fun playerSettingsContentFocusRestoresAfterVisitingAnotherDestination() {
        lateinit var backStack: MutableList<AppNavKey>
        composeRule.setContent {
            backStack = rememberAppNavBackStack(SettingsKey(SettingsSection.PLAYER))
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.popNavigation() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<SettingsKey> { key ->
                        SettingsScreenNavigation(
                            currentSection = key.section,
                            onNavigate = { backStack.navigateTopLevel(SettingsKey(it)) },
                        ) { section, focusRequester ->
                            Button(
                                onClick = {},
                                modifier = Modifier.focusRequester(focusRequester),
                            ) {
                                Text("Content $section")
                            }
                        }
                    }
                    entry<ChannelsKey> {
                        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
                        Button(
                            onClick = {},
                            modifier = Modifier.focusRequester(focusRequester),
                        ) {
                            Text("Channels destination")
                        }
                    }
                },
            )
        }

        composeRule.onNodeWithText("Player").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText("Content ${SettingsSection.PLAYER}").assertIsFocused()

        composeRule.runOnIdle { backStack.navigateTopLevel(ChannelsKey) }
        composeRule.onNodeWithText("Channels destination").assertIsFocused()
        composeRule.runOnIdle {
            backStack.navigateTopLevel(SettingsKey(SettingsSection.PLAYER))
        }

        composeRule.onNodeWithText("Content ${SettingsSection.PLAYER}").assertIsFocused()
    }

    private fun dispatchBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
    }
}
