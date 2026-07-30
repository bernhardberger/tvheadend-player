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
        messageTexts.forEach { (kind, english, german) ->
            setStartupContent(MainStartupPresentation.Passive(kind))
            composeRule.onNodeWithText(english).assertIsDisplayed()

            setStartupContent(
                presentation = MainStartupPresentation.Passive(kind),
                locale = Locale.GERMAN,
            )
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
        composeRule.onNodeWithText("Starting TVHeadend Player")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Connecting to TVHeadend…").assertIsDisplayed()
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
        composeRule.onNodeWithText("Action needed")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("TVHeadend is unavailable. Try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Connection settings").assertIsDisplayed()
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()
    }

    @Test
    fun initialFocusUsesFirstSemanticActionForEverySupportedSet() {
        setStartupContent(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE,
                retryAndSettings,
            ),
        )
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()

        setStartupContent(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.CONFIGURATION_REQUIRED,
                settingsOnly,
            ),
        )
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()

        setStartupContent(
            MainStartupPresentation.Actionable(
                MainStartupMessageKind.SIMPLE_TV_FAILURE,
                retryAndExit,
            ),
        )
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()
    }

    @Test
    fun retryAndSettingsGraphContainsEveryOuterAndVerticalEdge() {
        assertTwoActionGraph(retryAndSettings, MainStartupActionId.CONNECTION_SETTINGS)
    }

    @Test
    fun retryAndExitGraphContainsEveryOuterAndVerticalEdge() {
        assertTwoActionGraph(retryAndExit, MainStartupActionId.EXIT_SIMPLE_TV)
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
                MainStartupMessageKind.SIMPLE_TV_FAILURE,
                retryAndExit,
            ),
            onAction = { action = it },
        )

        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY))
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.EXIT_SIMPLE_TV))
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(MainStartupActionId.EXIT_SIMPLE_TV, action) }
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
    fun longGermanSimpleTvFailureFitsTheConstrainedProductionRoot() {
        composeRule.setContent {
            GermanStartupContent {
                TVHeadendPlayerTheme {
                    Box(
                        modifier = Modifier
                            .size(width = 960.dp, height = 540.dp)
                            .testTag(VIEWPORT_TAG),
                    ) {
                        MainStartupScreen(
                            presentation = MainStartupPresentation.Actionable(
                                MainStartupMessageKind.SIMPLE_TV_FAILURE,
                                retryAndExit,
                            ),
                            contentPadding = PaddingValues(),
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        val root = composeRule.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(960f, viewport.width, 1f)
        assertEquals(540f, viewport.height, 1f)
        assertTrue(root.left >= viewport.left)
        assertTrue(root.top >= viewport.top)
        assertTrue(root.right <= viewport.right)
        assertTrue(root.bottom <= viewport.bottom)
        composeRule.onNodeWithText("Fernsehen ist in Einfachem TV derzeit nicht verfügbar.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Erneut versuchen").assertIsDisplayed()
        composeRule.onNodeWithText("Einfaches TV verlassen").assertIsDisplayed()
        listOf(
            composeRule.onNodeWithText(
                "Fernsehen ist in Einfachem TV derzeit nicht verfügbar.",
            ).fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY))
                .fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag(actionTag(MainStartupActionId.EXIT_SIMPLE_TV))
                .fetchSemanticsNode().boundsInRoot,
        ).forEach { bounds ->
            assertTrue(bounds.left >= root.left)
            assertTrue(bounds.top >= root.top)
            assertTrue(bounds.right <= root.right)
            assertTrue(bounds.bottom <= root.bottom)
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
        const val VIEWPORT_TAG = "main-startup-test-viewport"

        val retryAndSettings = listOf(
            MainStartupActionId.RETRY,
            MainStartupActionId.CONNECTION_SETTINGS,
        )
        val settingsOnly = listOf(MainStartupActionId.CONNECTION_SETTINGS)
        val retryAndExit = listOf(
            MainStartupActionId.RETRY,
            MainStartupActionId.EXIT_SIMPLE_TV,
        )

        val messageTexts = listOf(
            MessageText(MainStartupMessageKind.PREPARING, "Preparing TVHeadend Player…", "TVHeadend Player wird vorbereitet…"),
            MessageText(MainStartupMessageKind.CONNECTING, "Connecting to TVHeadend…", "Verbindung mit TVHeadend wird hergestellt…"),
            MessageText(MainStartupMessageKind.SYNCING_CHANNELS, "Loading channel information…", "Senderinformationen werden geladen…"),
            MessageText(MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA, "Preparing channel information…", "Senderinformationen werden vorbereitet…"),
            MessageText(MainStartupMessageKind.RECONNECTING, "Reconnecting to TVHeadend…", "Verbindung mit TVHeadend wird wiederhergestellt…"),
            MessageText(MainStartupMessageKind.STARTING_TELEVISION, "Starting television…", "Fernsehen wird gestartet…"),
            MessageText(MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS, "No channels are available for this account.", "Für dieses Konto sind keine Sender verfügbar."),
            MessageText(MainStartupMessageKind.RETRYABLE_FAILURE, "TVHeadend is unavailable. Try again.", "TVHeadend ist nicht verfügbar. Versuchen Sie es erneut."),
            MessageText(MainStartupMessageKind.CONFIGURATION_REQUIRED, "Set up the TVHeadend connection to load channels.", "Richten Sie die TVHeadend-Verbindung ein, um Sender zu laden."),
            MessageText(MainStartupMessageKind.CREDENTIAL_UNAVAILABLE, "The saved credential is unavailable. Open connection settings and enter it again.", "Die gespeicherten Zugangsdaten sind nicht verfügbar. Öffnen Sie die Verbindungseinstellungen und geben Sie sie erneut ein."),
            MessageText(MainStartupMessageKind.SIMPLE_TV_FAILURE, "Television is unavailable in Simple TV.", "Fernsehen ist in Einfachem TV derzeit nicht verfügbar."),
        )
    }
}

private data class MessageText(
    val kind: MainStartupMessageKind,
    val english: String,
    val german: String,
)

private fun actionTag(action: MainStartupActionId): String = "main-startup-action-${action.name}"
