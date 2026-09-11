package at.bernhardberger.tvhplayer.ui.startup

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.TvFullScreenPadding
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class MainStartupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyMessageKindRendersEnglishAndGermanStatus() {
        var presentation: MainStartupPresentation by mutableStateOf(
            MainStartupPresentation.Passive(messageTexts.first().kind),
        )
        var locale by mutableStateOf(Locale.ENGLISH)
        composeRule.setContent {
            LocaleStartupContent(locale) {
                TVHeadendPlayerTheme {
                    MainStartupScreen(
                        presentation = presentation,
                        contentPadding = PaddingValues(),
                    )
                }
            }
        }

        messageTexts.forEach { (kind, english, german) ->
            composeRule.runOnIdle {
                presentation = MainStartupPresentation.Passive(kind)
                locale = Locale.ENGLISH
            }
            composeRule.onNodeWithText(english).assertIsDisplayed()

            composeRule.runOnIdle { locale = Locale.GERMAN }
            composeRule.onNodeWithText(german).assertIsDisplayed()
        }
    }

    @Test
    fun passiveStatusIsPoliteHeadingAndHasNoDialogFocusOrAction() {
        setStartupContent(MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING))

        composeRule.onNodeWithTag(ROOT_TAG)
            .assertHasNoClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.IsDialog))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        composeRule.onNodeWithText("Tvheadend Player")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Connecting to your Tvheadend server…").assertIsDisplayed()
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertDoesNotExist()
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertDoesNotExist()
    }

    @Test
    fun actionableStatusIsDialogTraversalPaneWithPolicyActionsInOrder() {
        setStartupContent(
            MainStartupPresentation.Actionable(
                messageKind = MainStartupMessageKind.RETRYABLE_FAILURE,
                actions = retryAndSettings,
            ),
        )

        composeRule.onNodeWithTag(ROOT_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    "Action needed",
                ),
            )
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.IsTraversalGroup,
                    true,
                ),
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithText("Action needed").assertDoesNotExist()
        composeRule.onNodeWithText("Tvheadend Player")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Couldn’t connect to your Tvheadend server. Try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Connection settings").assertIsDisplayed()
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()
    }

    @Test
    fun initialFocusUsesFirstActionAndPreservesSurvivingActionWhenSetExpands() {
        var presentation: MainStartupPresentation by mutableStateOf(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                retryAndSettings,
            ),
        )
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()

        composeRule.runOnIdle {
            presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.CONFIGURATION_REQUIRED,
                settingsOnly,
            )
        }
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()

        composeRule.runOnIdle {
            presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                retryAndSettings,
            )
        }
        // Settings remains a valid semantic action when Retry becomes available.
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()
    }

    @Test
    fun retryAndSettingsGraphContainsEveryOuterAndVerticalEdge() {
        assertTwoActionGraph(retryAndSettings, MainStartupActionId.CONNECTION_SETTINGS)
    }



    @Test
    fun settingsOnlyGraphContainsEveryEdge() {
        setStartupContent(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.CONFIGURATION_REQUIRED,
                settingsOnly,
            ),
        )

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
    }

    @Test
    fun enterInvokesTheVisibleActionIdentity() {
        var action: MainStartupActionId? = null
        setStartupContent(
            presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                retryAndSettings,
            ),
            onAction = { action = it },
        )

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY))
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(MainStartupActionId.CONNECTION_SETTINGS, action) }
    }

    @Test
    fun compatibleMessageUpdatePreservesFocusByActionId() {
        var presentation: MainStartupPresentation by mutableStateOf(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                retryAndSettings,
            ),
        )
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY))
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()
        composeRule.runOnIdle {
            presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS,
                retryAndSettings,
            )
        }

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()
    }

    @Test
    fun removedFocusedActionFallsBackToFirstAvailableAction() {
        var presentation by mutableStateOf(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                retryAndSettings,
            ),
        )
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()
        composeRule.runOnIdle {
            presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.CONFIGURATION_REQUIRED,
                settingsOnly,
            )
        }

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()
    }

    @Test
    fun transitionToPassiveRemovesActionsAndFocusedSemanticsImmediately() {
        var presentation: MainStartupPresentation by mutableStateOf(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                retryAndSettings,
            ),
        )
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()

        composeRule.runOnIdle {
            presentation = MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING)
        }

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertDoesNotExist()
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(ROOT_TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.IsDialog))
    }

    @Test
    fun startupSafeBoundsMatrixUsesProductionPaddingAtLargeFontScale() {
        var scenario by mutableStateOf(startupBoundsMatrix.first())
        composeRule.setContent {
            LocaleStartupContent(scenario.locale) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 1.3f),
                ) {
                    TVHeadendPlayerTheme {
                        Box(
                            modifier = Modifier
                                .size(width = 960.dp, height = 540.dp)
                                .testTag(VIEWPORT_TAG),
                        ) {
                            MainStartupScreen(
                                presentation = scenario.presentation,
                                contentPadding = TvFullScreenPadding,
                            )
                        }
                    }
                }
            }
        }

        startupBoundsMatrix.forEach { nextScenario ->
            composeRule.runOnIdle { scenario = nextScenario }
            composeRule.waitForIdle()
            assertStartupBoundsScenario(nextScenario)
        }
    }

    private fun assertTwoActionGraph(
        actions: List<MainStartupActionId>,
        secondAction: MainStartupActionId,
    ) {
        setStartupContent(
            MainStartupPresentation.Actionable(MainStartupMessageKind.RETRYABLE_FAILURE, actions),
        )

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY))
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(actionTag(secondAction))
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()
    }

    private fun assertStartupBoundsScenario(scenario: StartupBoundsScenario) {
        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(960f, viewport.width, 1f)
        assertEquals(540f, viewport.height, 1f)

        val nodes = buildList {
            add(composeRule.onNodeWithTag(MARK_TAG).assertIsDisplayed())
            add(composeRule.onNodeWithText(scenario.title).assertIsDisplayed())
            add(composeRule.onNodeWithText(scenario.message).assertIsDisplayed())
            scenario.actions.forEach { action ->
                add(composeRule.onNodeWithTag(actionTag(action.id)).assertIsDisplayed())
                composeRule.onNodeWithText(action.label).assertIsDisplayed()
            }
        }
        MainStartupActionId.entries
            .filterNot { action -> scenario.actions.any { it.id == action } }
            .forEach { action ->
                composeRule.onNodeWithTag(actionTag(action)).assertDoesNotExist()
            }
        nodes.forEach { node ->
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= viewport.left + 48f)
            assertTrue(bounds.top >= viewport.top + 32f)
            assertTrue(bounds.right <= viewport.right - 48f)
            assertTrue(bounds.bottom <= viewport.bottom - 32f)
        }
    }

    private fun setStartupContent(
        presentation: MainStartupPresentation,
        locale: Locale? = null,
        onAction: (MainStartupActionId) -> Unit = {},
    ) {
        composeRule.setContent {
            if (locale == null) {
                TVHeadendPlayerTheme {
                    MainStartupScreen(
                        presentation = presentation,
                        contentPadding = PaddingValues(),
                        onAction = onAction,
                    )
                }
            } else {
                LocaleStartupContent(locale) {
                    TVHeadendPlayerTheme {
                        MainStartupScreen(
                            presentation = presentation,
                            contentPadding = PaddingValues(),
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LocaleStartupContent(locale: Locale, content: @androidx.compose.runtime.Composable () -> Unit) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val localizedConfiguration = remember(configuration, locale) {
            Configuration(configuration).apply { setLocale(locale) }
        }
        val localizedContext = remember(context, localizedConfiguration) {
            context.createConfigurationContext(localizedConfiguration)
        }
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfiguration,
            LocalResources provides localizedContext.resources,
            content = content,
        )
    }

    @androidx.compose.runtime.Composable
    private fun GermanStartupContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        LocaleStartupContent(Locale.GERMAN) {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.3f)) {
                content()
            }
        }
    }

    private companion object {
        const val ROOT_TAG = "main-startup-root"
        const val MARK_TAG = "main-startup-mark"
        const val VIEWPORT_TAG = "main-startup-test-viewport"

        val retryAndSettings = listOf(
            MainStartupActionId.RETRY,
            MainStartupActionId.CONNECTION_SETTINGS,
        )
        val settingsOnly = listOf(MainStartupActionId.CONNECTION_SETTINGS)

        val messageTexts = listOf(
            MessageText(MainStartupMessageKind.PREPARING, "Preparing Tvheadend Player…", "Tvheadend Player wird vorbereitet…"),
            MessageText(MainStartupMessageKind.CONNECTING, "Connecting to your Tvheadend server…", "Verbindung mit Ihrem Tvheadend-Server wird hergestellt…"),
            MessageText(MainStartupMessageKind.SYNCING_CHANNELS, "Loading channel information…", "Senderinformationen werden geladen…"),
            MessageText(MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA, "Preparing channel information…", "Senderinformationen werden vorbereitet…"),
            MessageText(MainStartupMessageKind.RECONNECTING, "Reconnecting to your Tvheadend server…", "Verbindung mit Ihrem Tvheadend-Server wird wiederhergestellt…"),
            MessageText(MainStartupMessageKind.STARTING_TELEVISION, "Starting television…", "Fernsehen wird gestartet…"),
            MessageText(MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS, "No channels are available for this account.", "Für dieses Konto sind keine Sender verfügbar."),
            MessageText(MainStartupMessageKind.RETRYABLE_FAILURE, "Couldn’t connect to your Tvheadend server. Try again.", "Die Verbindung mit Ihrem Tvheadend-Server konnte nicht hergestellt werden. Versuchen Sie es erneut."),
            MessageText(MainStartupMessageKind.CONFIGURATION_REQUIRED, "Set up your Tvheadend server connection to load channels.", "Richten Sie die Verbindung mit Ihrem Tvheadend-Server ein, um Sender zu laden."),
            MessageText(MainStartupMessageKind.CREDENTIAL_UNAVAILABLE, "The saved credential is unavailable. Open connection settings and enter it again.", "Die gespeicherten Zugangsdaten sind nicht verfügbar. Öffnen Sie die Verbindungseinstellungen und geben Sie sie erneut ein."),
        )

        val startupBoundsMatrix = listOf(
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_CHANNELS),
                title = "Tvheadend Player",
                message = "Loading channel information…",
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING),
                title = "Tvheadend Player",
                message = "Verbindung mit Ihrem Tvheadend-Server wird wiederhergestellt…",
            ),
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.CONFIGURATION_REQUIRED,
                    settingsOnly,
                ),
                title = "Tvheadend Player",
                message = "Set up your Tvheadend server connection to load channels.",
                actions = listOf(StartupActionLabel(MainStartupActionId.CONNECTION_SETTINGS, "Connection settings")),
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.CREDENTIAL_UNAVAILABLE,
                    settingsOnly,
                ),
                title = "Tvheadend Player",
                message = "Die gespeicherten Zugangsdaten sind nicht verfügbar. Öffnen Sie die Verbindungseinstellungen und geben Sie sie erneut ein.",
                actions = listOf(StartupActionLabel(MainStartupActionId.CONNECTION_SETTINGS, "Verbindungseinstellungen")),
            ),
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS,
                    retryAndSettings,
                ),
                title = "Tvheadend Player",
                message = "No channels are available for this account.",
                actions = listOf(
                    StartupActionLabel(MainStartupActionId.RETRY, "Retry"),
                    StartupActionLabel(MainStartupActionId.CONNECTION_SETTINGS, "Connection settings"),
                ),
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.RETRYABLE_FAILURE,
                    retryAndSettings,
                ),
                title = "Tvheadend Player",
                message = "Die Verbindung mit Ihrem Tvheadend-Server konnte nicht hergestellt werden. Versuchen Sie es erneut.",
                actions = listOf(
                    StartupActionLabel(MainStartupActionId.RETRY, "Erneut versuchen"),
                    StartupActionLabel(MainStartupActionId.CONNECTION_SETTINGS, "Verbindungseinstellungen"),
                ),
            ),
        )
    }
}

private data class MessageText(
    val kind: MainStartupMessageKind,
    val english: String,
    val german: String,
)

private data class StartupBoundsScenario(
    val locale: Locale,
    val presentation: MainStartupPresentation,
    val title: String,
    val message: String,
    val actions: List<StartupActionLabel> = emptyList(),
)

private data class StartupActionLabel(
    val id: MainStartupActionId,
    val label: String,
)

private fun actionTag(action: MainStartupActionId): String = "main-startup-action-${action.name}"
