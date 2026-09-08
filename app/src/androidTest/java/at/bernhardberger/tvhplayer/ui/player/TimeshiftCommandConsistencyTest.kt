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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class TimeshiftCommandConsistencyTest {
    @get:Rule val rule = createComposeRule()

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
                    "unavailable", "clamped", "expired", "replaced", "Seek result uncertain") { result.await() }
                scope.advanceTimeBy(400L)
                scope.runCurrent()
                owner.queueRelativeSeek(state, -30_000L,
                    "unavailable", "clamped", "expired", "replaced", "Seek result uncertain") {
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
