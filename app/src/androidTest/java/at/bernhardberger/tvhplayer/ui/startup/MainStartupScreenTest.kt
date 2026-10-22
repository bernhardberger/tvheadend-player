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
import at.bernhardberger.tvhplayer.core.AppErrorAction
import at.bernhardberger.tvhplayer.core.AppErrorCondition
import at.bernhardberger.tvhplayer.core.MainStartupMessageKind
import at.bernhardberger.tvhplayer.core.MainStartupLoadingFeedback
import at.bernhardberger.tvhplayer.core.MainStartupPresentation
import at.bernhardberger.tvhplayer.core.noChannelsAppError
import at.bernhardberger.tvhplayer.core.toAppError
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
        var presentation: MainStartupPresentation by mutableStateOf(messageTexts.first().presentation)
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

        messageTexts.forEach { (next, english, german) ->
            composeRule.runOnIdle {
                presentation = next
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
        composeRule.onNodeWithTag(PRIMARY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SECONDARY_TAG).assertDoesNotExist()
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
            presentation = unreachable
        }
        composeRule.onNodeWithTag(PRIMARY_TAG).assertIsDisplayed().assertIsFocused()
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
    fun actionableStatusIsTheCatalogAlertWithPolicyActionsInOrder() {
        setStartupContent(unreachable)

        composeRule.onNodeWithTag(OVERLAY_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.PaneTitle,
                    "Can’t reach the server",
                ),
            )
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.IsTraversalGroup,
                    true,
                ),
            )
        composeRule.onNodeWithTag(ANNOUNCEMENT_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
            )
        composeRule.onNodeWithTag(MARK_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Can’t reach the server").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("It didn’t respond to the connection.", substring = true).assertIsDisplayed()
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        composeRule.onNodeWithTag(PRIMARY_TAG).assertIsFocused().assertTextEquals("Retry")
        composeRule.onNodeWithTag(SECONDARY_TAG).assertIsDisplayed().assertTextEquals("Connection settings")
    }

    @Test
    fun eachNewAlertFocusesItsFirstAction() {
        var presentation: MainStartupPresentation by mutableStateOf(unreachable)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }
        composeRule.onNodeWithTag(PRIMARY_TAG).assertIsFocused().assertTextEquals("Retry")

        composeRule.runOnIdle { presentation = notConfigured }
        composeRule.onNodeWithTag(PRIMARY_TAG).assertIsFocused().assertTextEquals("Connection settings")
        composeRule.onNodeWithTag(SECONDARY_TAG).assertDoesNotExist()

        composeRule.runOnIdle { presentation = unreachable }
        composeRule.onNodeWithTag(PRIMARY_TAG).assertIsFocused().assertTextEquals("Retry")
    }

    @Test
    fun alertActionsKeepNativeFocusAppearanceInEnglishAndLargeGerman() {
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
            listOf(authenticationRejected, unreachable).forEach { alert ->
                composeRule.runOnUiThread {
                    locale = language
                    fontScale = scale
                    presentation = alert
                }
                composeRule.mainClock.advanceTimeBy(512)
                assertNativeFocusedPrimary(nativeFocusedContainer)
                composeRule.runOnUiThread {
                    presentation = MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING)
                }
                composeRule.mainClock.advanceTimeByFrame()
            }
        }
    }

    @Test
    fun retryAndSettingsGraphContainsEveryOuterAndVerticalEdge() {
        setStartupContent(unreachable)

        composeRule.onNodeWithTag(PRIMARY_TAG)
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(SECONDARY_TAG)
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag(PRIMARY_TAG).assertIsFocused()
    }

    @Test
    fun settingsOnlyGraphContainsEveryEdge() {
        setStartupContent(notConfigured)

        composeRule.onNodeWithTag(PRIMARY_TAG)
            .assertIsFocused()
            .assertTextEquals("Connection settings")
            .performKeyInput {
                pressKey(Key.DirectionLeft)
                pressKey(Key.DirectionRight)
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
            .assertIsFocused()
    }

    @Test
    fun enterInvokesTheVisibleAction() {
        var action: AppErrorAction? = null
        setStartupContent(
            presentation = unreachable,
            onAction = { action = it },
        )

        composeRule.onNodeWithTag(PRIMARY_TAG)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(SECONDARY_TAG)
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle { assertEquals(AppErrorAction.CONNECTION_SETTINGS, action) }
    }

    @Test
    fun alertWithTheSameActionsKeepsFocusOnTheSameAction() {
        var presentation: MainStartupPresentation by mutableStateOf(unreachable)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }

        composeRule.onNodeWithTag(PRIMARY_TAG)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(SECONDARY_TAG).assertIsFocused()
        composeRule.runOnIdle { presentation = noChannels }

        composeRule.onNodeWithText("No channels").assertIsDisplayed()
        composeRule.onNodeWithTag(SECONDARY_TAG).assertIsFocused()
    }

    @Test
    fun removedFocusedActionFallsBackToFirstAvailableAction() {
        var presentation: MainStartupPresentation by mutableStateOf(unreachable)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }

        composeRule.onNodeWithTag(PRIMARY_TAG)
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithTag(SECONDARY_TAG).assertIsFocused()
        composeRule.runOnIdle { presentation = notConfigured }

        composeRule.onNodeWithTag(PRIMARY_TAG)
            .assertIsFocused()
            .assertTextEquals("Connection settings")
    }

    @Test
    fun transitionToPassiveRemovesActionsAndFocusedSemanticsImmediately() {
        var presentation: MainStartupPresentation by mutableStateOf(unreachable)
        composeRule.setContent {
            TVHeadendPlayerTheme {
                MainStartupScreen(
                    presentation = presentation,
                    contentPadding = PaddingValues(),
                )
            }
        }
        composeRule.onNodeWithTag(PRIMARY_TAG).assertIsFocused()

        composeRule.runOnIdle {
            presentation = MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING)
        }

        composeRule.onNodeWithTag(PRIMARY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SECONDARY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(OVERLAY_TAG).assertDoesNotExist()
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

    private fun assertNativeFocusedPrimary(containerColor: Color) {
        val primary = composeRule.onNodeWithTag(PRIMARY_TAG).assertIsFocused()
        composeRule.mainClock.advanceTimeBy(512)
        // The alert has dialog semantics but remains in the Activity window.
        val viewport = composeRule.onNodeWithTag("startup-focus-viewport")
        val pixels = viewport.captureToImage().toPixelMap()
        val viewportBounds = viewport.fetchSemanticsNode().boundsInRoot
        val primaryBounds = primary.fetchSemanticsNode().boundsInRoot
        val sampleX = (primaryBounds.center.x - viewportBounds.left).toInt()
        val sampleY = (primaryBounds.top + primaryBounds.height / 5 - viewportBounds.top).toInt()
        // The empty interior above the caption avoids text and capsule-edge antialiasing.
        assertEquals(
            "The focused action must render the native TV filled container, not just focused semantics",
            containerColor.toArgb(),
            pixels[sampleX, sampleY].toArgb(),
        )
    }

    private fun assertStartupBoundsScenario(scenario: StartupBoundsScenario) {
        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(scenario.width.toFloat(), viewport.width, 1f)
        assertEquals(scenario.height.toFloat(), viewport.height, 1f)

        val nodes = buildList {
            if (scenario.actions.isEmpty()) {
                add(composeRule.onNodeWithTag(MARK_TAG).assertIsDisplayed())
                add(composeRule.onNodeWithText("Tvheadend Player").assertIsDisplayed())
                add(composeRule.onNodeWithText(scenario.message).assertIsDisplayed())
            } else {
                // An alert names the problem itself; the brand lockup is gone.
                composeRule.onNodeWithTag(MARK_TAG).assertDoesNotExist()
                add(composeRule.onNodeWithTag(ANNOUNCEMENT_TAG).assertIsDisplayed())
                add(composeRule.onNodeWithTag(TITLE_TAG).assertIsDisplayed().assertTextEquals(scenario.message))
            }
            scenario.actions.forEachIndexed { index, label ->
                add(composeRule.onNodeWithTag(ACTION_TAGS[index]).assertIsDisplayed().assertTextEquals(label))
            }
        }
        ACTION_TAGS.drop(scenario.actions.size).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
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
        onAction: (AppErrorAction) -> Unit = {},
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

        const val OVERLAY_TAG = "tv-recovery-overlay"
        const val ANNOUNCEMENT_TAG = "tv-recovery-announcement"
        const val TITLE_TAG = "tv-recovery-title"
        const val PRIMARY_TAG = "tv-recovery-primary"
        const val SECONDARY_TAG = "tv-recovery-secondary"
        val ACTION_TAGS = listOf(PRIMARY_TAG, SECONDARY_TAG)

        val unreachable = MainStartupPresentation.Actionable(AppErrorCondition.UNREACHABLE.toAppError())
        val authenticationRejected = MainStartupPresentation.Actionable(
            AppErrorCondition.AUTHENTICATION_REJECTED.toAppError(),
        )
        val credentialUnavailable = MainStartupPresentation.Actionable(
            AppErrorCondition.CREDENTIAL_UNAVAILABLE.toAppError(),
        )
        val notConfigured = MainStartupPresentation.Actionable(AppErrorCondition.NOT_CONFIGURED.toAppError())
        val noChannels = MainStartupPresentation.Actionable(noChannelsAppError())

        val messageTexts = listOf(
            MessageText(MainStartupPresentation.Passive(MainStartupMessageKind.PREPARING), "Preparing app", "App wird vorbereitet"),
            MessageText(MainStartupPresentation.Passive(MainStartupMessageKind.CONNECTING), "Connecting", "Verbindung wird hergestellt"),
            MessageText(MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA), "Syncing channels and guide", "Sender und Programm werden synchronisiert"),
            MessageText(MainStartupPresentation.Passive(MainStartupMessageKind.WAITING_FOR_CURRENT_CHANNEL_METADATA), "Preparing channels", "Sender werden vorbereitet"),
            MessageText(MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING), "Reconnecting", "Verbindung wird wiederhergestellt"),
            MessageText(MainStartupPresentation.Passive(MainStartupMessageKind.STARTING_TELEVISION), "Starting playback", "Wiedergabe wird gestartet"),
            MessageText(MainStartupPresentation.Passive(MainStartupMessageKind.RESUMING_PLAYBACK), "Resuming playback", "Wiedergabe wird fortgesetzt"),
            MessageText(unreachable, "Can’t reach the server", "Server nicht erreichbar"),
            MessageText(authenticationRejected, "Sign-in failed", "Anmeldung fehlgeschlagen"),
            MessageText(credentialUnavailable, "Sign-in needed", "Anmeldung erforderlich"),
            MessageText(notConfigured, "Set up your connection", "Verbindung einrichten"),
            MessageText(noChannels, "No channels", "Keine Sender"),
        )

        val startupBoundsMatrix = listOf(
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.SYNCING_METADATA),
                message = "Syncing channels and guide",
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Passive(MainStartupMessageKind.RECONNECTING),
                message = "Verbindung wird wiederhergestellt",
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = MainStartupPresentation.Passive(
                    MainStartupMessageKind.STARTING_TELEVISION,
                ),
                message = "Wiedergabe wird gestartet",
            ),
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = notConfigured,
                message = "Set up your connection",
                actions = listOf("Connection settings"),
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = credentialUnavailable,
                message = "Anmeldung erforderlich",
                actions = listOf("Verbindungseinstellungen"),
            ),
            StartupBoundsScenario(
                locale = Locale.ENGLISH,
                presentation = noChannels,
                message = "No channels",
                actions = listOf("Retry", "Connection settings"),
            ),
            StartupBoundsScenario(
                locale = Locale.GERMAN,
                presentation = unreachable,
                message = "Server nicht erreichbar",
                actions = listOf("Erneut versuchen", "Verbindungseinstellungen"),
            ),
        )
    }
}

private data class MessageText(
    val presentation: MainStartupPresentation,
    val english: String,
    val german: String,
)

private data class StartupBoundsScenario(
    val locale: Locale,
    val presentation: MainStartupPresentation,
    val message: String,
    val actions: List<String> = emptyList(),
    val fontScale: Float = if (locale.language == Locale.GERMAN.language) 1.3f else 1f,
    val width: Int = 960,
    val height: Int = 540,
)
