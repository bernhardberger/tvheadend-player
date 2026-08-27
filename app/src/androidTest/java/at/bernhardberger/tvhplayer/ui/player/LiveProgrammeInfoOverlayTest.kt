package at.bernhardberger.tvhplayer.ui.player

import android.content.res.Configuration
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.core.DvrActionFailure
import at.bernhardberger.tvheadend.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingDecision
import at.bernhardberger.tvhplayer.core.LiveInfoRecordingState
import at.bernhardberger.tvhplayer.core.liveInfoRecordingCompletion
import at.bernhardberger.tvhplayer.core.liveInfoRecordingDecision
import at.bernhardberger.tvhplayer.core.liveInfoRecordingDismissed
import at.bernhardberger.tvhplayer.core.programmeRecordingTarget
import at.bernhardberger.tvheadend.core.EpgEventEntry
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalTestApi::class)
class LiveProgrammeInfoOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noEpgRemainsOpenChannelIdentifiedAndSafelyFocused() {
        setInfoOverlay(event = { null })

        composeRule.onNodeWithText("Programme information unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("7 • Das Erste").assertIsDisplayed()
        composeRule.onNodeWithText("No programme information is available for Das Erste right now.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
        composeRule.onNodeWithTag("live-info-overlay").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.PaneTitle,
                "Programme information",
            )
        )
    }

    @Test
    fun epgDisappearanceTransitionsToUnavailableWithoutClosingInfo() {
        var currentEvent by mutableStateOf<EpgEventEntry?>(event(id = 42))
        setInfoOverlay(event = { currentEvent })

        composeRule.onNodeWithTag("live-info-record").requestFocus()
        composeRule.runOnIdle { currentEvent = null }

        composeRule.onNodeWithText("Programme information unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("7 • Das Erste").assertIsDisplayed()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
    }

    @Test
    fun recordAvailabilityLossMovesFocusToClose() {
        val event = event(id = 42)
        var recordingScheduled by mutableStateOf(false)
        var canRecord by mutableStateOf(true)
        setInfoOverlay(
            event = { event },
            recordingScheduled = { recordingScheduled },
            canRecord = { canRecord },
        )

        composeRule.onNodeWithTag("live-info-record").requestFocus()
        composeRule.runOnIdle { recordingScheduled = true }
        composeRule.onNodeWithTag("live-info-record").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()

        composeRule.runOnIdle { recordingScheduled = false }
        composeRule.onNodeWithTag("live-info-record").requestFocus()
        composeRule.runOnIdle { canRecord = false }
        composeRule.onNodeWithTag("live-info-record").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
    }

    @Test
    fun confirmationEligibilityLossInvalidatesWithoutDispatch() {
        val event = event(id = 42)
        var recordingScheduled by mutableStateOf(false)
        var canRecord by mutableStateOf(true)
        var state by mutableStateOf<LiveInfoRecordingState>(LiveInfoRecordingState.Idle)
        var confirmationVisible by mutableStateOf(false)
        var restoreRecordFocus by mutableStateOf(false)
        var dispatches = 0
        setInfoOverlay(
            event = { event },
            recordingState = { state },
            confirmationVisible = { confirmationVisible },
            restoreRecordFocus = { restoreRecordFocus },
            recordingScheduled = { recordingScheduled },
            canRecord = { canRecord },
            onRecord = {
                state = LiveInfoRecordingState.Confirming(event.programmeRecordingTarget())
                confirmationVisible = true
            },
            onRecordingActivate = { dispatches++ },
            onConfirmationInvalidated = {
                state = LiveInfoRecordingState.Idle
                confirmationVisible = false
                restoreRecordFocus = true
            },
            onRecordFocusRestored = { restoreRecordFocus = false },
        )

        composeRule.onNodeWithTag("live-info-record").requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("programme-recording-cancel").assertIsFocused()
        composeRule.runOnIdle { recordingScheduled = true }
        composeRule.onNodeWithTag("programme-recording-cancel").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, dispatches) }

        composeRule.runOnIdle {
            recordingScheduled = false
            canRecord = true
        }
        composeRule.onNodeWithTag("live-info-record").requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("programme-recording-cancel").assertIsFocused()
        composeRule.runOnIdle { canRecord = false }
        composeRule.onNodeWithTag("programme-recording-cancel").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
        composeRule.runOnIdle { assertEquals(0, dispatches) }
    }

    @Test
    fun confirmationContainsFocusAndCancelRestoresRecordWithoutDispatch() {
        val event = event(id = 42, title = "Abendnachrichten")
        var state by mutableStateOf<LiveInfoRecordingState>(LiveInfoRecordingState.Idle)
        var confirmationVisible by mutableStateOf(false)
        var restoreRecordFocus by mutableStateOf(false)
        var dispatches = 0
        setInfoOverlay(
            event = { event },
            recordingState = { state },
            confirmationVisible = { confirmationVisible },
            restoreRecordFocus = { restoreRecordFocus },
            onRecord = {
                state = LiveInfoRecordingState.Confirming(event.programmeRecordingTarget())
                confirmationVisible = true
            },
            onRecordingActivate = { dispatches++ },
            onRecordingDismiss = {
                state = liveInfoRecordingDismissed(state)
                confirmationVisible = false
                restoreRecordFocus = true
            },
            onRecordFocusRestored = { restoreRecordFocus = false },
        )

        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithTag("live-info-record").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.onNodeWithTag("programme-recording-cancel").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionLeft) }
            .assertIsFocused()
        composeRule.onNodeWithTag("programme-recording-confirm")
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionRight) }
            .assertIsFocused()
        composeRule.onNodeWithTag("programme-recording-cancel")
            .requestFocus()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.onNodeWithTag("programme-recording-confirm").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-record").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(0, dispatches)
            assertFalse(restoreRecordFocus)
        }
    }

    @Test
    fun completeBackCycleCancelsConfirmationOnceAndRestoresRecord() {
        val event = event(id = 42)
        var state by mutableStateOf<LiveInfoRecordingState>(LiveInfoRecordingState.Idle)
        var confirmationVisible by mutableStateOf(false)
        var restoreRecordFocus by mutableStateOf(false)
        var backActions = 0
        var dispatches = 0
        val dismissConfirmation = {
            backActions++
            state = liveInfoRecordingDismissed(state)
            confirmationVisible = false
            restoreRecordFocus = true
        }
        setInfoOverlay(
            event = { event },
            recordingState = { state },
            confirmationVisible = { confirmationVisible },
            restoreRecordFocus = { restoreRecordFocus },
            onRecord = {
                state = LiveInfoRecordingState.Confirming(event.programmeRecordingTarget())
                confirmationVisible = true
            },
            onRecordingActivate = { dispatches++ },
            onRecordingDismiss = dismissConfirmation,
            onRecordFocusRestored = { restoreRecordFocus = false },
            onBack = dismissConfirmation,
        )

        composeRule.onNodeWithTag("live-info-record").requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("programme-recording-cancel").assertIsFocused()

        dispatchBack("programme-recording-cancel", AndroidKeyEvent.ACTION_DOWN)
        dispatchBack("programme-recording-cancel", AndroidKeyEvent.ACTION_DOWN, repeatCount = 1)
        dispatchBack("programme-recording-cancel", AndroidKeyEvent.ACTION_UP)

        composeRule.onNodeWithTag("programme-recording-cancel").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-record").assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(1, backActions)
            assertEquals(0, dispatches)
        }
    }

    @Test
    fun oneActivationBecomesBusyAndCompletionKeepsCapturedIdentity() {
        var currentEvent by mutableStateOf(event(id = 42, title = "Captured programme"))
        var state by mutableStateOf<LiveInfoRecordingState>(LiveInfoRecordingState.Idle)
        var confirmationVisible by mutableStateOf(false)
        var dispatches = 0
        setInfoOverlay(
            event = { currentEvent },
            recordingState = { state },
            confirmationVisible = { confirmationVisible },
            onRecord = {
                state = LiveInfoRecordingState.Confirming(
                    currentEvent.programmeRecordingTarget()
                )
                confirmationVisible = true
            },
            onRecordingActivate = {
                when (
                    val decision = liveInfoRecordingDecision(
                        state,
                        currentEvent,
                        actionEligible = true,
                    )
                ) {
                    is LiveInfoRecordingDecision.Dispatch -> {
                        state = LiveInfoRecordingState.Dispatching(decision.target)
                        dispatches++
                    }
                    LiveInfoRecordingDecision.Ignore,
                    LiveInfoRecordingDecision.Invalidate -> Unit
                }
            },
            onRecordingDismiss = {
                state = liveInfoRecordingDismissed(state)
                confirmationVisible = false
            },
        )

        composeRule.onNodeWithTag("live-info-record").requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("programme-recording-confirm")
            .requestFocus()
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.onNodeWithTag("programme-recording-confirm")
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("programme-recording-close")
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("programme-recording-confirm").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, dispatches)
            assertTrue(state is LiveInfoRecordingState.Dispatching)
            currentEvent = event(id = 43, title = "New current programme")
            val completion = liveInfoRecordingCompletion(
                state = state,
                result = DvrActionResult.Accepted(entryId = 9),
                infoOpen = true,
            )
            state = completion.state
            confirmationVisible = completion.showResult
        }

        composeRule.onNodeWithText("Captured programme", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("New current programme", substring = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("programme-recording-close").assertIsFocused()
        composeRule.onNodeWithTag("programme-recording-result").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)
        )
    }

    @Test
    fun failureFocusesRetryAndRevalidatesBeforeRetryDispatch() {
        var currentEvent by mutableStateOf(event(id = 42, title = "Captured programme"))
        val target = currentEvent.programmeRecordingTarget()
        var state by mutableStateOf<LiveInfoRecordingState>(
            LiveInfoRecordingState.Failed(target, DvrActionFailure.CONNECTION)
        )
        var confirmationVisible by mutableStateOf(true)
        var dispatches = 0
        setInfoOverlay(
            event = { currentEvent },
            recordingState = { state },
            confirmationVisible = { confirmationVisible },
            onRecordingActivate = {
                when (
                    val decision = liveInfoRecordingDecision(
                        state,
                        currentEvent,
                        actionEligible = true,
                    )
                ) {
                    is LiveInfoRecordingDecision.Dispatch -> dispatches++
                    LiveInfoRecordingDecision.Invalidate -> {
                        state = LiveInfoRecordingState.Idle
                        confirmationVisible = false
                    }
                    LiveInfoRecordingDecision.Ignore -> Unit
                }
            },
        )

        composeRule.onNodeWithTag("programme-recording-retry")
            .assertIsFocused()
            .assertIsEnabled()
        composeRule.runOnIdle {
            currentEvent = event(id = 43, title = "Replacement programme")
        }
        composeRule.onNodeWithTag("programme-recording-retry")
            .performKeyInput { pressKey(Key.Enter) }

        composeRule.onNodeWithTag("programme-recording-retry").assertDoesNotExist()
        composeRule.onNodeWithText("Replacement programme").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, dispatches) }
    }

    @Test
    fun closingInfoRecomposesControlsAndRestoresTheInfoAction() {
        var infoOpen by mutableStateOf(false)
        var restoreInfoFocus by mutableStateOf(false)
        composeRule.setContent {
            val context = LocalContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            TVHeadendPlayerTheme {
                PlayerControlsLayer(
                    visible = true,
                    modalVisible = infoOpen,
                ) {
                    OverlayControlsTv(
                        imageLoader = imageLoader,
                        channelNumber = 7,
                        channelName = "Das Erste",
                        piconPath = null,
                        nowEvent = event(id = 42),
                        nextEvent = null,
                        nowSec = 1_500L,
                        controlsVisible = true,
                        optionsOpen = false,
                        onOpenChannels = {},
                        onOpenInfo = { infoOpen = true },
                        onStopPlayback = {},
                        onUserInteraction = {},
                        onOpenOptions = {},
                        timeshiftState = AppTimeshiftState(),
                        timeshiftFeedback = null,
                        onToggleTimeshiftPause = {},
                        onSeekTimeshift = {},
                        onGoLive = {},
                        restoreInfoFocus = restoreInfoFocus,
                        onInfoFocusRestored = { restoreInfoFocus = false },
                    )
                }
                if (infoOpen) {
                    LiveProgrammeInfoOverlay(
                        event = event(id = 42),
                        channelIdentity = "7 • Das Erste",
                        channelName = "Das Erste",
                        recordingScheduled = false,
                        canRecord = true,
                        recordingState = LiveInfoRecordingState.Idle,
                        confirmationVisible = false,
                        restoreRecordFocus = false,
                        onRecord = {},
                        onRecordingActivate = {},
                        onRecordingDismiss = {},
                        onClose = {
                            restoreInfoFocus = true
                            infoOpen = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("live-info-action").requestFocus()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("live-info-action").assertDoesNotExist()
        composeRule.onNodeWithTag("live-info-close").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("live-info-action").assertIsFocused()
        composeRule.runOnIdle { assertFalse(restoreInfoFocus) }
    }

    @Test
    fun longConfirmationStaysInsideTheExactTvViewport() {
        val event = event(
            id = 42,
            title = "Eine außergewöhnlich lange deutschsprachige Sendungsbezeichnung mit Zusatz",
        )
        val target = event.programmeRecordingTarget()
        composeRule.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val germanConfiguration = remember(configuration) {
                Configuration(configuration).apply {
                    setLocale(Locale.GERMAN)
                }
            }
            val germanContext = remember(context, germanConfiguration) {
                context.createConfigurationContext(germanConfiguration)
            }
            CompositionLocalProvider(
                LocalContext provides germanContext,
                LocalConfiguration provides germanConfiguration,
                LocalResources provides germanContext.resources,
                LocalDensity provides Density(density = 1f, fontScale = 1.3f),
            ) {
                TVHeadendPlayerTheme {
                    Box(
                        Modifier
                            .size(width = 960.dp, height = 540.dp)
                            .testTag("live-info-test-viewport")
                    ) {
                        LiveProgrammeInfoOverlay(
                            event = event,
                            channelIdentity = "7 • Das Erste",
                            channelName = "Das Erste",
                            recordingScheduled = false,
                            canRecord = true,
                            recordingState = LiveInfoRecordingState.Confirming(target),
                            confirmationVisible = true,
                            restoreRecordFocus = false,
                            onRecord = {},
                            onRecordingActivate = {},
                            onRecordingDismiss = {},
                            onClose = {},
                        )
                    }
                }
            }
        }

        val viewport = composeRule.onNodeWithTag("live-info-test-viewport")
            .fetchSemanticsNode().boundsInRoot
        val panel = composeRule.onNodeWithTag("live-info-panel")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(960f, viewport.width, 1f)
        assertEquals(540f, viewport.height, 1f)
        assertTrue(panel.left >= viewport.left)
        assertTrue(panel.top >= viewport.top)
        assertTrue(panel.right <= viewport.right)
        assertTrue(panel.bottom <= viewport.bottom)
        composeRule.onNodeWithText("Abbrechen").assertIsDisplayed()
        composeRule.onNodeWithText("Aufnehmen").assertIsDisplayed()
        composeRule.onNodeWithText(
            "TVHeadend fügt diese Sendung zu Ihren Aufnahmen hinzu.",
        ).assertIsDisplayed()
        listOf(
            composeRule.onNodeWithText(
                "Eine außergewöhnlich lange deutschsprachige Sendungsbezeichnung",
                substring = true,
            ).fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag("programme-recording-cancel")
                .fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithTag("programme-recording-confirm")
                .fetchSemanticsNode().boundsInRoot,
            composeRule.onNodeWithText(
                "TVHeadend fügt diese Sendung zu Ihren Aufnahmen hinzu."
            ).fetchSemanticsNode().boundsInRoot,
        ).forEach { bounds ->
            assertTrue(bounds.left >= panel.left)
            assertTrue(bounds.top >= panel.top)
            assertTrue(bounds.right <= panel.right)
            assertTrue(bounds.bottom <= panel.bottom)
        }
        composeRule.onNodeWithTag("live-info-overlay").assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.IsDialog)
        )
        composeRule.onNodeWithText(
            "Eine außergewöhnlich lange deutschsprachige Sendungsbezeichnung",
            substring = true,
        ).assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        )
    }

    private fun setInfoOverlay(
        event: () -> EpgEventEntry?,
        recordingState: () -> LiveInfoRecordingState = { LiveInfoRecordingState.Idle },
        confirmationVisible: () -> Boolean = { false },
        restoreRecordFocus: () -> Boolean = { false },
        recordingScheduled: () -> Boolean = { false },
        canRecord: () -> Boolean = { true },
        onRecord: () -> Unit = {},
        onRecordingActivate: () -> Unit = {},
        onRecordingDismiss: () -> Unit = {},
        onConfirmationInvalidated: () -> Unit = {},
        onRecordFocusRestored: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            val dispatchBack = rememberPlayerBackDispatcher(onBack)
            LiveInfoRecordingValidityEffect(
                state = recordingState(),
                currentEvent = event(),
                actionEligible = !recordingScheduled() && canRecord(),
                confirmationVisible = confirmationVisible(),
                onInvalidated = onConfirmationInvalidated,
            )
            TVHeadendPlayerTheme {
                Box(
                    Modifier
                        .size(width = 960.dp, height = 540.dp)
                        .testTag("live-info-test-viewport")
                        .onPreviewKeyEvent(dispatchBack)
                ) {
                    LiveProgrammeInfoOverlay(
                        event = event(),
                        channelIdentity = "7 • Das Erste",
                        channelName = "Das Erste",
                        recordingScheduled = recordingScheduled(),
                        canRecord = canRecord(),
                        recordingState = recordingState(),
                        confirmationVisible = confirmationVisible(),
                        restoreRecordFocus = restoreRecordFocus(),
                        onRecord = onRecord,
                        onRecordingActivate = onRecordingActivate,
                        onRecordingDismiss = onRecordingDismiss,
                        onClose = {},
                        onRecordFocusRestored = onRecordFocusRestored,
                    )
                }
            }
        }
    }

    private fun dispatchBack(
        nodeTag: String,
        action: Int,
        repeatCount: Int = 0,
    ) {
        val eventTime = android.os.SystemClock.uptimeMillis()
        composeRule.onNodeWithTag(nodeTag).performKeyPress(
            ComposeKeyEvent(
                AndroidKeyEvent(
                    eventTime,
                    eventTime,
                    action,
                    AndroidKeyEvent.KEYCODE_BACK,
                    repeatCount,
                )
            )
        )
    }

    private fun event(
        id: Int,
        channelId: Int = 7,
        title: String = "Programme $id",
    ) = EpgEventEntry(
        eventId = id,
        channelId = channelId,
        start = 1_000L,
        stop = 2_000L,
        title = title,
        summary = "Summary",
        description = "Description",
    )
}
