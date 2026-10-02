package at.bernhardberger.tvhplayer.ui.startup

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.core.MainStartupActionId
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback
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
                presentation = if (kind in passiveKinds) MainStartupPresentation.Passive(kind)
                    else MainStartupPresentation.Actionable(kind, settingsOnly)
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
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.IsDialog))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
        composeRule.onNodeWithText("Connecting")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithText("Tvheadend Player")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Connecting").assertIsDisplayed()
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertDoesNotExist()
        composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertDoesNotExist()
    }

    @Test
    fun allBlockingStagesUseOneCircularIndicatorAndNoCountersOrLinearPromotion() {
        var presentation by mutableStateOf(MainStartupPresentation.Passive(
            MainStartupMessageKind.CONNECTING,
        ))
        composeRule.setContent {
            LocaleStartupContent(Locale.US) {
                TVHeadendPlayerTheme { MainStartupScreen(presentation, TvFullScreenPadding, motionEnabled = true) }
            }
        }
        val loader = composeRule.onNodeWithTag(LOADER_TAG).fetchSemanticsNode()
        for (stage in listOf(MainStartupMessageKind.CONNECTING, MainStartupMessageKind.SYNCING_METADATA, MainStartupMessageKind.STARTING_TELEVISION)) {
            composeRule.runOnIdle { presentation = MainStartupPresentation.Passive(stage) }
            composeRule.onAllNodesWithTag(LOADER_TAG).assertCountEquals(1)
            assertEquals(loader.id, composeRule.onNodeWithTag(LOADER_TAG).fetchSemanticsNode().id)
            composeRule.onNodeWithTag(STATUS_TAG).assertIsDisplayed()
            composeRule.onNode(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
            )).assertIsDisplayed()
        }
    }

    @Test
    fun hiddenAndWaitingFeedbackKeepTheApprovedBrandAnchor() {
        var feedback by mutableStateOf(MainStartupLoadingFeedback.HIDDEN)
        composeRule.setContent {
            LocaleStartupContent(Locale.US) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                    TVHeadendPlayerTheme {
                        Box(Modifier.size(960.dp, 540.dp)) {
                            MainStartupScreen(
                                MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA),
                                TvFullScreenPadding,
                                loadingFeedback = feedback,
                                motionEnabled = true,
                            )
                        }
                    }
                }
            }
        }
        val mark = composeRule.onNodeWithTag(MARK_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(156f, mark.top, 1f)
        assertEquals(80f, mark.width, 1f)
        assertEquals(80f, mark.height, 1f)
        composeRule.onNodeWithTag(STATUS_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(LOADER_TAG).assertDoesNotExist()

        composeRule.runOnIdle { feedback = MainStartupLoadingFeedback.WAITING }
        val loader = composeRule.onNodeWithTag(LOADER_TAG).assertIsDisplayed().fetchSemanticsNode()
        assertEquals(44, loader.layoutInfo.width)
        assertEquals(44, loader.layoutInfo.height)
        assertEquals(480f, loader.boundsInRoot.center.x, 1f)
        assertEquals(340f, loader.boundsInRoot.center.y, 1f)
        assertEquals(ProgressBarRangeInfo.Indeterminate, loader.config[SemanticsProperties.ProgressBarRangeInfo])
        composeRule.onNodeWithTag(STATUS_TAG).assertTextEquals("Syncing channels and guide")
        assertEquals(mark, composeRule.onNodeWithTag(MARK_TAG).fetchSemanticsNode().boundsInRoot)

    }

    @Test
    fun brandedStartupKeepsItsCompositionAndBrandFreeReturnKeepsThePlayerAnchor() {
        var player by mutableStateOf(false)
        var brandingVisible by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.size(960.dp, 540.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        if (player) at.bernhardberger.tvhplayer.ui.player.PlayerBusyIndicator(
                            at.bernhardberger.tvhplayer.ui.player.PlayerBusyStatus.TUNING,
                        ) else MainStartupScreen(
                            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
                            TvFullScreenPadding, motionEnabled = true, brandingVisible = brandingVisible,
                        )
                    }
                }
            }
        }
        val startupRing = composeRule.onNodeWithTag(LOADER_TAG).fetchSemanticsNode().boundsInRoot
        val status = composeRule.onNodeWithTag(STATUS_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(156f, composeRule.onNodeWithTag(MARK_TAG).fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(340f, startupRing.center.y, 1f)
        assertEquals(16f, status.top - startupRing.bottom, 1f)
        composeRule.runOnIdle { brandingVisible = false }
        composeRule.onNodeWithTag(MARK_TAG).assertDoesNotExist()
        val returnRing = composeRule.onNodeWithTag(LOADER_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(270f, returnRing.center.y, 1f)
        composeRule.runOnIdle { player = true }
        assertEquals(returnRing, composeRule.onNodeWithTag("player-busy-indicator").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun feedbackIsIndependentOfTheAssemblyFrame() {
        val millis = mutableFloatStateOf(0f)
        var presentation by mutableStateOf<MainStartupPresentation>(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
        )
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation, TvFullScreenPadding,
                    brandMillis = { millis.floatValue },
                    loadingFeedback = MainStartupLoadingFeedback.WAITING,
                    motionEnabled = true,
                )
            }
        }
        for (time in listOf(0f, 400f, 900f, 1200f)) {
            composeRule.runOnIdle { millis.floatValue = time }
            composeRule.onNodeWithTag(STATUS_TAG).assertIsDisplayed()
            composeRule.onNodeWithTag(LOADER_TAG).assertIsDisplayed()
        }
        composeRule.runOnIdle { millis.floatValue = 1300f }
        composeRule.onNodeWithTag(STATUS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(LOADER_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            millis.floatValue = 100f
            presentation = MainStartupPresentation.Actionable(
                MainStartupMessageKind.RETRYABLE_FAILURE, retryAndSettings,
            )
        }
        composeRule.onNodeWithTag("main-startup-action-RETRY").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun reducedMotionShowsTruthfulStatusWithoutFrozenIndicators() {
        var feedback by mutableStateOf(MainStartupLoadingFeedback.HIDDEN)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    MainStartupPresentation.Passive(MainStartupMessageKind.PREPARING),
                    TvFullScreenPadding,
                    loadingFeedback = feedback,
                    motionEnabled = false,
                    brandMillis = { 0f },
                )
            }
        }
        composeRule.onNodeWithTag(STATUS_TAG).assertDoesNotExist()
        for (next in listOf(MainStartupLoadingFeedback.WAITING)) {
            composeRule.runOnIdle { feedback = next }
            composeRule.onNodeWithTag(STATUS_TAG).assertIsDisplayed().assertTextEquals("Preparing app")
            composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            composeRule.onNodeWithText("Tvheadend Player").assertIsDisplayed()
        }
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
                    "Can’t connect to Tvheadend",
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
        composeRule.onNodeWithText("Can’t connect to Tvheadend").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Make sure the server is running and reachable, then try again.").assertIsDisplayed()
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
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
    fun settingsRecoveryKeepsNativeFocusAppearanceAndIdentityOnEntryRemovalAndExpansion() {
        var presentation: MainStartupPresentation by mutableStateOf(
            MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING),
        )
        var locale by mutableStateOf(Locale.US)
        var fontScale by mutableStateOf(1f)
        var nativeFocusedContainer = Color.Unspecified
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            LocaleStartupContent(locale) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    TVHeadendPlayerTheme {
                        val focusedContainer = MaterialTheme.colorScheme.onSurface
                        SideEffect { nativeFocusedContainer = focusedContainer }
                        Box(Modifier.size(960.dp, 540.dp).testTag("startup-focus-viewport")) {
                            MainStartupScreen(presentation, TvFullScreenPadding)
                        }
                    }
                }
            }
        }

        listOf(Locale.US to 1f, Locale.GERMANY to 1.3f).forEach { (language, scale) ->
            composeRule.runOnUiThread {
                locale = language
                fontScale = scale
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.AUTHENTICATION_FAILURE, settingsOnly,
                )
            }
            composeRule.mainClock.advanceTimeBy(512)
            assertNativeFocusedSettings(nativeFocusedContainer)

            // A fresh recovery starts on Retry, matching the reported capture transition.
            composeRule.runOnUiThread {
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.runOnUiThread {
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.RETRYABLE_FAILURE, retryAndSettings,
                )
            }
            composeRule.mainClock.advanceTimeBy(512)
            composeRule.onNodeWithTag(actionTag(MainStartupActionId.RETRY)).assertIsFocused()
            val settingsId = composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
                .fetchSemanticsNode().id

            composeRule.runOnUiThread {
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.AUTHENTICATION_FAILURE, settingsOnly,
                )
            }
            composeRule.mainClock.advanceTimeBy(512)
            assertEquals(settingsId, assertNativeFocusedSettings(nativeFocusedContainer))

            composeRule.runOnUiThread {
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.RETRYABLE_FAILURE, retryAndSettings,
                )
            }
            composeRule.mainClock.advanceTimeBy(512)
            assertEquals(settingsId, assertNativeFocusedSettings(nativeFocusedContainer))
            composeRule.runOnUiThread {
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
            }
            composeRule.mainClock.advanceTimeByFrame()
        }
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
    fun startupSafeBoundsMatrixUsesProductionPaddingForEnglishAndLargeGermanText() {
        var scenario by mutableStateOf(startupBoundsMatrix.first())
        composeRule.setContent {
            LocaleStartupContent(scenario.locale) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = scenario.fontScale),
                ) {
                    TVHeadendPlayerTheme {
                        Box(
                            modifier = Modifier
                                .size(width = scenario.width.dp, height = scenario.height.dp)
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

        val scenarios = startupBoundsMatrix +
            startupBoundsMatrix.map { it.copy(width = 848, height = 480) } +
            startupBoundsMatrix.map { it.copy(width = 640, height = 480) }
        scenarios.forEach { nextScenario ->
            composeRule.runOnIdle { scenario = nextScenario }
            composeRule.waitForIdle()
            assertStartupBoundsScenario(nextScenario)
        }
    }

    private fun assertNativeFocusedSettings(containerColor: Color): Int {
        val settings = composeRule.onNodeWithTag(actionTag(MainStartupActionId.CONNECTION_SETTINGS))
            .assertIsFocused()
        composeRule.mainClock.advanceTimeBy(512)
        // The production pane has dialog semantics but remains in the Activity window.
        val viewport = composeRule.onNodeWithTag("startup-focus-viewport")
        val pixels = viewport.captureToImage().toPixelMap()
        val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
        val settingsBounds = settings.fetchSemanticsNode().boundsInRoot
        val sampleX = (settingsBounds.center.x - viewportBounds.left).toInt()
        val sampleY = (settingsBounds.top + settingsBounds.height / 5 - viewportBounds.top).toInt()
        // The empty interior above the caption avoids text and capsule-edge antialiasing.
        assertEquals(
            "Focused Settings must render the native TV filled container, not just focused semantics",
            containerColor.toArgb(),
            pixels[sampleX, sampleY].toArgb(),
        )
        return settings.fetchSemanticsNode().id
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
        assertEquals(scenario.width.toFloat(), viewport.width, 1f)
        assertEquals(scenario.height.toFloat(), viewport.height, 1f)

        val nodes = buildList {
            add(composeRule.onNodeWithTag(MARK_TAG).assertIsDisplayed())
            add(composeRule.onNodeWithText(scenario.title).assertIsDisplayed())
            add(composeRule.onNodeWithText(scenario.message).assertIsDisplayed())
            scenario.problemTitle?.let { add(composeRule.onNodeWithText(it).assertIsDisplayed()) }
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
        const val STATUS_TAG = "main-startup-status"
        const val LOADER_TAG = "main-startup-loader"

        val retryAndSettings = listOf(
            MainStartupActionId.RETRY,
            MainStartupActionId.CONNECTION_SETTINGS,
        )
        val settingsOnly = listOf(MainStartupActionId.CONNECTION_SETTINGS)
        val passiveKinds = setOf(
            MainStartupMessageKind.PREPARING, MainStartupMessageKind.CONNECTING,
            MainStartupMessageKind.SYNCING_METADATA, MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA,
            MainStartupMessageKind.RECONNECTING, MainStartupMessageKind.STARTING_TELEVISION,
            MainStartupMessageKind.RESUMING_PLAYBACK,
        )

        val messageTexts = listOf(
            MessageText(MainStartupMessageKind.PREPARING, "Preparing app", "App wird vorbereitet"),
            MessageText(MainStartupMessageKind.CONNECTING, "Connecting", "Verbindung wird hergestellt"),
            MessageText(MainStartupMessageKind.SYNCING_METADATA, "Syncing channels and guide", "Sender und Programm werden synchronisiert"),
            MessageText(MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA, "Preparing channels", "Sender werden vorbereitet"),
            MessageText(MainStartupMessageKind.RECONNECTING, "Reconnecting", "Verbindung wird wiederhergestellt"),
            MessageText(MainStartupMessageKind.STARTING_TELEVISION, "Starting playback", "Wiedergabe wird gestartet"),
            MessageText(MainStartupMessageKind.RESUMING_PLAYBACK, "Resuming playback", "Wiedergabe wird fortgesetzt"),
            MessageText(MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS, "Check the channels and access permissions for this account, then try again.", "Prüfen Sie die Sender und Zugriffsrechte dieses Kontos und versuchen Sie es erneut."),
            MessageText(MainStartupMessageKind.RETRYABLE_FAILURE, "Make sure the server is running and reachable, then try again.", "Prüfen Sie, ob der Server läuft und erreichbar ist, und versuchen Sie es dann erneut."),
            MessageText(MainStartupMessageKind.AUTHENTICATION_FAILURE, "Check your username and password in connection settings.", "Prüfen Sie Benutzername und Passwort in den Verbindungseinstellungen."),
            MessageText(MainStartupMessageKind.PERMISSION_DENIED, "Check this account’s access permissions in Tvheadend.", "Prüfen Sie die Zugriffsrechte dieses Kontos in Tvheadend."),
            MessageText(MainStartupMessageKind.INCOMPATIBLE_SERVER, "This server does not support the required connection. Check its version and connection settings.", "Dieser Server unterstützt die benötigte Verbindung nicht. Prüfen Sie seine Version und die Verbindungseinstellungen."),
            MessageText(MainStartupMessageKind.TIMEOUT_FAILURE, "Try again. If loading keeps timing out, check your Tvheadend server.", "Versuchen Sie es erneut. Prüfen Sie bei wiederholten Zeitüberschreitungen Ihren Tvheadend-Server."),
            MessageText(MainStartupMessageKind.SYNCHRONIZATION_FAILURE, "Try again, or check your connection settings if the problem continues.", "Versuchen Sie es erneut oder prüfen Sie bei anhaltenden Problemen die Verbindungseinstellungen."),
            MessageText(MainStartupMessageKind.CONFIGURATION_REQUIRED, "Set up your Tvheadend server connection to load channels.", "Richten Sie die Verbindung mit Ihrem Tvheadend-Server ein, um Sender zu laden."),
            MessageText(MainStartupMessageKind.CREDENTIAL_UNAVAILABLE, "Open connection settings and enter your sign-in again.", "Öffnen Sie die Verbindungseinstellungen und melden Sie sich erneut an."),
        )

        val startupBoundsMatrix = listOf(
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA),
                title = "Tvheadend Player",
                message = "Syncing channels and guide",
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING),
                title = "Tvheadend Player",
                message = "Verbindung wird wiederhergestellt",
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Passive(
                    MainStartupMessageKind.STARTING_TELEVISION,
                ),
                title = "Tvheadend Player",
                message = "Wiedergabe wird gestartet",
            ),
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.CONFIGURATION_REQUIRED,
                    settingsOnly,
                ),
                title = "Tvheadend Player",
                message = "Set up your Tvheadend server connection to load channels.",
                problemTitle = "Set up your connection",
                actions = listOf(StartupActionLabel(MainStartupActionId.CONNECTION_SETTINGS, "Connection settings")),
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.CREDENTIAL_UNAVAILABLE,
                    settingsOnly,
                ),
                title = "Tvheadend Player",
                message = "Öffnen Sie die Verbindungseinstellungen und melden Sie sich erneut an.",
                problemTitle = "Gespeicherte Anmeldung nicht verfügbar",
                actions = listOf(StartupActionLabel(MainStartupActionId.CONNECTION_SETTINGS, "Verbindungseinstellungen")),
            ),
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = MainStartupPresentation.Actionable(
                    MainStartupMessageKind.AUTHORITATIVE_NO_CHANNELS,
                    retryAndSettings,
                ),
                title = "Tvheadend Player",
                message = "Check the channels and access permissions for this account, then try again.",
                problemTitle = "No channels available",
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
                message = "Prüfen Sie, ob der Server läuft und erreichbar ist, und versuchen Sie es dann erneut.",
                problemTitle = "Keine Verbindung zu Tvheadend",
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
    val problemTitle: String? = null,
    val fontScale: Float = if (locale.language == Locale.GERMAN.language) 1.3f else 1f,
    val width: Int = 960,
    val height: Int = 540,
)

private data class StartupActionLabel(
    val id: MainStartupActionId,
    val label: String,
)

private fun actionTag(action: MainStartupActionId): String = "main-startup-action-${action.name}"
