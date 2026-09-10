package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import at.bernhardberger.tvhplayer.core.seekStepMs
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import at.bernhardberger.tvhplayer.core.projectedTimeshiftState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TimeshiftCommandConsistencyTest {
    @get:Rule val rule = createComposeRule()

    @Test fun acquisitionRetainsTimelineAndDirectionalRecoveryWithoutResuming() {
        val state = mutableStateOf(AppTimeshiftState())
        val feedback = mutableStateOf<String?>(null)
        val feedbackIsError = mutableStateOf(false)
        var toggles = 0
        val commands = mutableListOf<Long>()
        lateinit var input: InputModeManager
        rule.mainClock.autoAdvance = false
        rule.setContent {
            input = LocalInputModeManager.current
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                    channelName = "Fixture", channelNumber = 1, piconPath = null,
                    nowEvent = null, nextEvent = null, nowSec = 0L,
                    controlsVisible = true, optionsOpen = false,
                    onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                    timeshiftState = state.value, paused = true,
                    onSeekTimeshift = commands::add, timeshiftFeedback = feedback.value,
                    timeshiftFeedbackIsError = feedbackIsError.value,
                    onToggleTimeshiftPause = { toggles++ }, onGoLive = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(100L)
        rule.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithTag("player-timeline-track", useUnmergedTree = true).assertExists()
        rule.runOnIdle { state.value = AppTimeshiftState(available = true, timingKnown = false) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(1_480L, ignoreFrameDuration = true)
        rule.onNodeWithText("Playback timing unavailable", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("player-seekbar").requestFocus().assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        assertEquals(emptyList<Long>(), commands)
        rule.runOnIdle {
            state.value = AppTimeshiftState(available = true, positionMs = 1_000L, liveEdgeMs = 3_000L)
        }
        rule.mainClock.advanceTimeBy(100L)
        rule.onNodeWithTag("player-seekbar").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionLeft) }
        assertEquals(listOf(30_000L, -30_000L), commands)
        rule.runOnIdle { state.value = state.value.copy(timingKnown = false) }
        rule.mainClock.advanceTimeByFrame()
        val restartedAt = rule.mainClock.currentTime
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("player-seekbar").assertIsFocused()
        rule.runOnIdle { feedback.value = "Command failed"; feedbackIsError.value = true }
        rule.mainClock.advanceTimeBy(16L)
        rule.onNodeWithText("Command failed", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle { feedback.value = "Reached the available buffer limit"; feedbackIsError.value = false }
        rule.mainClock.advanceTimeBy(restartedAt + 1_480L - rule.mainClock.currentTime, ignoreFrameDuration = true)
        rule.onNodeWithText("Playback timing unavailable", useUnmergedTree = true).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(32L)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Playback timing unavailable", useUnmergedTree = true).assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag("player-pause").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag("player-seekbar").assertIsFocused()
        assertEquals(0, toggles)
    }

    @Test fun normalDpadPassesTheSameUnclampedStepAsReducedAndRejectsHiddenInput() {
        val visible = mutableStateOf(true)
        val commands = mutableListOf<Long>()
        lateinit var input: InputModeManager
        rule.setContent {
            input = LocalInputModeManager.current
            TVHeadendPlayerTheme {
                OverlayControlsTv(
                    imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                    channelName = "Fixture", channelNumber = 1, piconPath = null,
                    nowEvent = null, nextEvent = null, nowSec = 0L,
                    controlsVisible = visible.value, optionsOpen = false,
                    onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                    timeshiftState = AppTimeshiftState(available = true, bufferStartMs = 0L,
                        positionMs = 500L, liveEdgeMs = 1_000L),
                    onSeekTimeshift = commands::add,
                    timeshiftFeedback = null, onToggleTimeshiftPause = {}, onGoLive = {},
                )
            }
        }
        rule.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithTag("player-seekbar").requestFocus()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        assertEquals(listOf(-seekStepMs(0), seekStepMs(0)), commands)
        rule.onNodeWithTag("player-seekbar").assertIsFocused()
        rule.onRoot().performKeyInput { keyDown(Key.DirectionLeft); advanceEventTime(1_500L); keyUp(Key.DirectionLeft) }
        org.junit.Assert.assertTrue(commands.drop(2).size > 1)
        org.junit.Assert.assertTrue(commands.drop(2).any { it == -seekStepMs(12) })
        rule.onNodeWithTag("player-seekbar").assertIsFocused()
        val admitted = commands.size
        rule.runOnIdle { visible.value = false }
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        assertEquals(admitted, commands.size)
    }

    @Test fun acceptedPausedAndPlayingEdgeRequestsSurviveDelayedSamplesAndKeepFocusRecovery() {
        val scope = TestScope()
        val owner = LiveTimelinePresentationState(scope, { 0L }, { scope.testScheduler.currentTime })
        val fixture = TimeshiftTestFixture(600.seconds).apply { updateHistory(0.seconds, 600.seconds) }
        val sample = mutableStateOf(fixture.state.value.toAppPresentation(fixture.playbackPosition(590.seconds)))
        val reduced = mutableStateOf(false)
        var toggles = 0
        lateinit var input: InputModeManager
        rule.setContent {
            input = LocalInputModeManager.current
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    val preview = owner.previewForTimeline(sample.value.timeline)
                    if (reduced.value) {
                        preview?.let {
                            TimeshiftSeekPreview(sample.value, it.decision, feedback = owner.feedback,
                                feedbackIsError = owner.feedbackIsError,
                                modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    } else {
                        OverlayControlsTv(
                            imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                            channelName = "Fixture", channelNumber = 1, piconPath = null,
                            nowEvent = null, nextEvent = null, nowSec = 0L,
                            controlsVisible = true, optionsOpen = false,
                            onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                            timeshiftState = preview?.let { projectedTimeshiftState(sample.value, it.decision.targetMs) }
                                ?: sample.value,
                            committedTimeshiftState = sample.value, paused = sample.value.paused,
                            previewing = preview != null, timeshiftFeedback = owner.feedback,
                            timeshiftFeedbackIsError = owner.feedbackIsError,
                            onSeekTimeshift = {}, onToggleTimeshiftPause = { toggles++ }, onGoLive = {},
                        )
                    }
                }
            }
        }
        rule.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        try {
            for (paused in listOf(false, true)) for (forward in listOf(false, true)) {
                val result = fixture.completed(readerReached = null)
                val target = if (forward) 600.seconds else 0.seconds
                rule.runOnIdle {
                    sample.value = fixture.state.value.toAppPresentation(
                        fixture.playbackPosition(if (forward) 590.seconds else 10.seconds)
                    ).copy(paused = paused)
                    owner.queueRelativeSeek(sample.value, if (forward) 30_000L else -30_000L,
                        "Unavailable", "Expired", "Replaced", "Uncertain") { result }
                    scope.advanceTimeBy(400L); scope.runCurrent()
                }
                rule.onNodeWithTag("player-seekbar").requestFocus()
                rule.runOnIdle {
                    scope.advanceTimeBy(3_000L); scope.runCurrent()
                    scope.launch {
                        sample.value = requireNotNull(owner.sampleTimeshiftPresentation {
                            fixture.state.value.toAppPresentation().copy(paused = paused)
                        })
                    }
                    scope.runCurrent()
                    org.junit.Assert.assertNotNull(owner.preview)
                }
                rule.onNodeWithTag("player-seekbar").assertIsFocused()
                rule.onNodeWithText("Seek requested. Waiting for playback position", useUnmergedTree = true).assertIsDisplayed()
                rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                rule.onNodeWithTag("player-pause").assertIsFocused()
                rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                rule.onNodeWithTag("player-seekbar").assertIsFocused()
                val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                    "p37-settlement-captures/waiting-${if (paused) "paused" else "playing"}-${if (forward) "latest" else "earliest"}.png")
                output.parentFile!!.mkdirs()
                output.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
                rule.runOnIdle { reduced.value = true }
                rule.onNodeWithTag("timeshift-seek-preview").assertExists()
                rule.runOnIdle { reduced.value = false }
                rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                rule.onNodeWithTag("player-seekbar").assertIsFocused()
                rule.runOnIdle {
                    scope.launch {
                        sample.value = requireNotNull(owner.sampleTimeshiftPresentation {
                            fixture.state.value.toAppPresentation(fixture.playbackPosition(
                                if (forward) target + 1.seconds else target, result.seek))
                                .copy(paused = paused)
                        })
                    }
                    scope.runCurrent()
                    org.junit.Assert.assertNull(owner.preview)
                    assertEquals(paused, sample.value.paused)
                }
                rule.onNodeWithTag("player-seekbar").assertIsFocused()
                rule.onNodeWithText("Reached the available buffer limit", substring = true).assertDoesNotExist()
                assertEquals(0, toggles)
            }
        } finally {
            rule.runOnIdle { owner.dispose() }
        }
    }

    @Test fun pendingSeekKeepsThumbAndAcceptsFurtherDirectionalSelection() {
        val scope = TestScope()
        val owner = LiveTimelinePresentationState(scope, { 0L }, { scope.testScheduler.currentTime })
        val fixture = TimeshiftTestFixture(600.seconds).apply { updateHistory(0.seconds, 600.seconds) }
        val sample = mutableStateOf(fixture.state.value.toAppPresentation(fixture.playbackPosition(300.seconds)))
        val requests = mutableListOf<Long>()
        var toggles = 0
        lateinit var input: InputModeManager
        fun capture(name: String) {
            val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
                "p42-control-captures/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use {
                rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        rule.setContent {
            input = LocalInputModeManager.current
            TVHeadendPlayerTheme {
                val preview = owner.previewForTimeline(sample.value.timeline)
                OverlayControlsTv(
                    imageLoader = ImageLoader.Builder(LocalContext.current).build(),
                    channelName = "Fixture", channelNumber = 1, piconPath = null,
                    nowEvent = null, nextEvent = null, nowSec = 0L,
                    controlsVisible = true, optionsOpen = false,
                    onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                    timeshiftState = preview?.let { projectedTimeshiftState(sample.value, it.decision.targetMs) }
                        ?: sample.value,
                    committedTimeshiftState = sample.value, paused = sample.value.paused,
                    previewing = preview != null, timeshiftFeedback = owner.feedback,
                    onSeekTimeshift = { delta ->
                        owner.queueRelativeSeek(sample.value, delta,
                            "Unavailable", "Expired", "Replaced", "Uncertain") { selection ->
                            val target = selection.target
                            requests += target.position.inWholeMilliseconds
                            fixture.completed(readerReached = null)
                        }
                    },
                    onToggleTimeshiftPause = { toggles++ }, onGoLive = {},
                )
            }
        }
        rule.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
        try {
            for (paused in listOf(false, true)) {
                rule.runOnIdle {
                    owner.cancelPendingSeek()
                    sample.value = fixture.state.value.toAppPresentation(fixture.playbackPosition(300.seconds))
                        .copy(paused = paused)
                    requests.clear()
                }
                rule.onNodeWithTag("player-seekbar").requestFocus()
                rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
                rule.runOnIdle { scope.advanceTimeBy(400L); scope.runCurrent() }
                assertEquals(listOf(270_000L), requests)
                rule.runOnIdle {
                    scope.launch {
                        sample.value = requireNotNull(owner.sampleTimeshiftPresentation {
                            fixture.state.value.toAppPresentation().copy(paused = paused)
                        })
                    }
                    scope.runCurrent()
                }
                rule.onNodeWithTag("player-seekbar").assertIsFocused()
                rule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertIsDisplayed()
                rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
                rule.runOnIdle { scope.advanceTimeBy(400L); scope.runCurrent() }
                assertEquals(listOf(270_000L, 240_000L), requests)
                rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                rule.onNodeWithTag("player-pause").assertIsFocused()
                rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                rule.onNodeWithTag("player-seekbar").assertIsFocused()
                assertEquals(0, toggles)
                capture("pending-${if (paused) "paused" else "playing"}")
                rule.runOnIdle {
                    // UI continuity only: production packet/frame settlement is tested in the SDK.
                    val acceptedSeek = requireNotNull(owner.preview?.acceptedSeek)
                    scope.launch {
                        sample.value = requireNotNull(owner.sampleTimeshiftPresentation {
                            fixture.state.value.toAppPresentation(fixture.playbackPosition(240.seconds, acceptedSeek))
                                .copy(paused = paused)
                        })
                    }
                    scope.runCurrent()
                    org.junit.Assert.assertNull(owner.preview)
                }
                rule.onNodeWithTag("player-seekbar").assertIsFocused()
                rule.onNodeWithTag("player-seekbar-thumb", useUnmergedTree = true).assertIsDisplayed()
                capture("settled-${if (paused) "paused" else "playing"}")
            }
        } finally {
            rule.runOnIdle { owner.dispose() }
        }
    }

    @Test fun reducedPreviewExposesUncertainCommandFeedback() {
        rule.setContent {
            TVHeadendPlayerTheme {
                Box(Modifier.fillMaxSize()) {
                    DebugVideoBackdrop(visible = true, modifier = Modifier.fillMaxSize())
                    TimeshiftSeekPreview(
                        state = AppTimeshiftState(available = true, positionMs = 540_000L, liveEdgeMs = 600_000L),
                        decision = TimeshiftSeekDecision(510_000L, -30_000L, false),
                        feedback = "Seek result uncertain",
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        rule.onNodeWithTag("timeshift-seek-preview")
            .assertContentDescriptionContains("Seek result uncertain", substring = true)
        rule.onNodeWithText("Seek result uncertain", useUnmergedTree = true).assertIsDisplayed()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "p36-numeric-captures/reduced-uncertain.png")
        output.parentFile!!.mkdirs()
        output.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun stackedTimeoutKeepsOnlyDispatchedOutcomeVisibleForFeedbackInterval() {
        val scope = TestScope()
        val owner = LiveTimelinePresentationState(scope, { 0L }, { scope.testScheduler.currentTime })
        val fixture = TimeshiftTestFixture(600.seconds).apply {
            updateHistory(0.seconds, 600.seconds)
        }
        val state = fixture.state.value.toAppPresentation(fixture.playbackPosition(540.seconds))
        val result = CompletableDeferred<TimeshiftContentSeekResult>()
        rule.setContent {
            TVHeadendPlayerTheme {
                owner.previewForTimeline(state.timeline)?.let { preview ->
                    TimeshiftSeekPreview(state, preview.decision, feedback = owner.feedback)
                }
            }
        }
        try {
            rule.runOnIdle {
                owner.queueRelativeSeek(state, -30_000L,
                    "unavailable", "expired", "replaced", "Seek result uncertain") { result.await() }
                scope.advanceTimeBy(400L)
                scope.runCurrent()
                owner.queueRelativeSeek(state, -30_000L,
                    "unavailable", "expired", "replaced", "Seek result uncertain") {
                    error("Discarded stacked input must not dispatch")
                }
                scope.advanceTimeBy(401L)
                result.complete(fixture.completed(TimeshiftCommandResult.TIMEOUT))
                scope.runCurrent()
                assertEquals(510_000L, owner.preview?.decision?.targetMs)
                assertEquals(true, owner.preview?.dispatched)
            }
            rule.onNodeWithTag("timeshift-seek-preview")
                .assertContentDescriptionContains("Seek result uncertain", substring = true)
            rule.onNodeWithText("Seek result uncertain", useUnmergedTree = true).assertIsDisplayed()
            rule.runOnIdle { scope.advanceTimeBy(949L); scope.runCurrent() }
            rule.onNodeWithText("Seek result uncertain", useUnmergedTree = true).assertIsDisplayed()
            rule.runOnIdle { scope.advanceTimeBy(1L); scope.runCurrent() }
            rule.onNodeWithTag("timeshift-seek-preview").assertDoesNotExist()
        } finally {
            rule.runOnIdle { owner.dispose() }
        }
    }
}
