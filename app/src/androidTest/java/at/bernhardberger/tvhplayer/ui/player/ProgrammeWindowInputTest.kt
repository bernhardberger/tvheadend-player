package at.bernhardberger.tvhplayer.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.projectedTimeshiftState
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import coil3.ImageLoader
import java.io.File
import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Production chrome and seek owner with the published offline SDK fixture, never a server. */
@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class ProgrammeWindowInputTest(private val scenario: String) {
    @get:Rule val rule = createComposeRule()

    @Test fun captureAndExerciseProgrammeWindow() {
        val originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        lateinit var owner: LiveTimelinePresentationState
        try {
            val base = Instant.parse(if (scenario == "midnight") "2026-09-07T23:30:00Z" else "2026-09-07T19:00:00Z")
            val events = listOf(0 to "A", 60 to "B").mapIndexed { index, (minute, title) ->
                EpgEvent.create(id = EventId(index + 1L), channelId = ChannelId(1),
                    start = base + minute.minutes, stop = base + (minute + 60).minutes, title = "Programme $title")
            }
            val fixture = TimeshiftTestFixture(120.minutes)
            val shallow = scenario == "shallow"
            val missing = scenario == "missing"
            fixture.updateHistory((if (shallow) 89 else 50).minutes, 90.minutes,
                estimatedLiveEdgeTime = (base + 90.minutes).takeUnless { missing })
            var state by mutableStateOf(fixture.state.value.toAppPresentation(fixture.playbackPosition((if (shallow) 90 else 65).minutes)))
            var metadata by mutableStateOf(scenario != "late")
            var paused by mutableStateOf(scenario == "paused")
            var compactPreview by mutableStateOf(false)
            val dispatches = mutableListOf<Long>()
            lateinit var input: InputModeManager
            rule.setContent {
                input = LocalInputModeManager.current
                val scope = rememberCoroutineScope()
                owner = remember { LiveTimelinePresentationState(scope, { base.toEpochMilliseconds() }, { rule.mainClock.currentTime }) }
                val context = LocalContext.current
                val imageLoader = remember { ImageLoader.Builder(context).build() }
                val unavailable = stringResource(R.string.timeshift_unavailable)
                val clamped = stringResource(R.string.timeshift_seek_clamped)
                val expired = stringResource(R.string.timeshift_target_expired)
                val replaced = stringResource(R.string.timeshift_target_replaced)
                val uncertain = stringResource(R.string.timeshift_seek_uncertain)
                fun lookup(time: Instant) = events.singleOrNull { metadata && time >= it.start && time < it.stop }
                val preview = owner.preview
                val committed = programmeWindow(state, mappingTimeline = preview?.mappingTimeline ?: state.timeline, eventAt = ::lookup)
                val window = if (preview == null) committed else programmeWindow(state, preview.target, preview.mappingTimeline, ::lookup)
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if (scenario == "font-scaled") 1.5f else 1f)) {
                TVHeadendPlayerTheme {
                    Box(Modifier.fillMaxSize()) {
                        DebugVideoBackdrop(true, Modifier.fillMaxSize())
                        if (compactPreview && preview != null) {
                            TimeshiftSeekPreview(state, preview.decision, Modifier.align(androidx.compose.ui.Alignment.BottomCenter), window)
                        } else OverlayControlsTv(imageLoader = imageLoader, channelNumber = 1, channelName = "Fixture TV", piconPath = null,
                            nowEvent = committed?.event ?: events[1], nextEvent = null, nowSec = (base + 90.minutes).epochSeconds,
                            controlsVisible = true, optionsOpen = false, onOpenChannels = {}, onStopPlayback = {}, onUserInteraction = {}, onOpenOptions = {},
                            timeshiftState = preview?.let { projectedTimeshiftState(state, it.decision.targetMs) } ?: state,
                            committedTimeshiftState = state, committedWindow = committed, programmeWindow = window,
                            previewing = preview != null,
                            timeshiftFeedback = owner.feedback, paused = paused, onToggleTimeshiftPause = { paused = !paused },
                            onCommitSeek = owner::commitPendingSeek,
                            onSeekTimeshift = { delta ->
                                owner.queueRelativeSeek(state, delta, unavailable, clamped, expired, replaced, uncertain) { target ->
                                    fixture.seek(target) {
                                        dispatches += target.position.inWholeMilliseconds
                                        state = fixture.state.value.toAppPresentation(fixture.playbackPosition(target.position))
                                        fixture.completed()
                                    }
                                }
                            },
                            onGoLive = {
                                owner.cancelPendingSeek()
                                state = fixture.state.value.toAppPresentation(fixture.playbackPosition(90.minutes))
                                paused = false
                            })
                    }
                }
                }
            }
            rule.runOnIdle { input.requestInputMode(InputMode.Keyboard) }
            rule.mainClock.advanceTimeBy(100)
            rule.onNodeWithTag("player-pause").assertIsFocused()
            capture("initial")
            if (shallow) rule.onNodeWithTag("player-window-available").assertTextEquals("1:00 available")
            val actionTop = rule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot.top
            val trackTop = rule.onNodeWithTag("player-seekbar").fetchSemanticsNode().boundsInRoot.top
            rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            rule.onNodeWithTag("player-seekbar").assertIsFocused()
            capture("focused")
            rule.mainClock.autoAdvance = false
            if (scenario == "held") {
                val now = android.os.SystemClock.uptimeMillis()
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                for (repeat in listOf(12, 13)) instrumentation.sendKeySync(android.view.KeyEvent(now, now,
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT, repeat))
                instrumentation.sendKeySync(android.view.KeyEvent(now, now,
                    android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_LEFT, 0))
            } else rule.onRoot().performKeyInput { repeat(if (shallow) 1 else 11) { pressKey(Key.DirectionLeft) } }
            rule.mainClock.advanceTimeByFrame()
            val target = requireNotNull(owner.preview).target
            assertEquals((if (shallow) 89.5.minutes else if (scenario == "held") 55.minutes else 59.5.minutes), target.position)
            if (!missing && scenario != "late") {
                rule.onNodeWithTag("player-window-title").assertTextEquals(if (shallow) "Programme B" else "Programme A")
                rule.onNodeWithTag("player-programme-title").assertTextEquals("Programme B")
                if (!shallow && scenario != "midnight") {
                    rule.onNodeWithTag("player-window-start").assertTextEquals("19:00")
                    rule.onNodeWithTag("player-window-end").assertTextEquals("20:00")
                }
            }
            if (scenario == "late") {
                rule.runOnIdle { metadata = true }
                rule.mainClock.advanceTimeByFrame()
                rule.onNodeWithTag("player-window-title").assertTextEquals("Programme A")
                assertSame(target, owner.preview!!.target)
            }
            if (scenario == "evicted") {
                rule.runOnIdle {
                    fixture.updateHistory(60.minutes, 90.minutes, estimatedLiveEdgeTime = base + 90.minutes)
                    state = fixture.state.value.toAppPresentation(fixture.playbackPosition(65.minutes))
                }
                rule.mainClock.advanceTimeByFrame()
                assertSame(target, owner.preview!!.target)
            }
            capture("preview")
            if (scenario == "evicted") {
                rule.onNodeWithTag("player-seekbar").assertContentDescriptionContains(
                    "That position is no longer in the buffer.", substring = true)
                rule.onNodeWithText("That position is no longer in the buffer.").assertExists()
            }
            if (shallow || scenario == "held") {
                assertEquals(rule.onNodeWithTag("player-seekbar-thumb").fetchSemanticsNode().boundsInRoot.center.x,
                    rule.onNodeWithTag("timeshift-preview-target").fetchSemanticsNode().boundsInRoot.center.x, 1f)
            }
            assertEquals(actionTop, rule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot.top)
            assertEquals(trackTop, rule.onNodeWithTag("player-seekbar").fetchSemanticsNode().boundsInRoot.top)
            if (!missing) rule.onNodeWithTag("timeshift-preview-target").assertExists()
            if (!missing) {
                val expected = requireNotNull(programmeWindow(state, target, owner.preview!!.mappingTimeline) { time ->
                    events.singleOrNull { time >= it.start && time < it.stop }
                })
                assertEquals(expected.positionFraction, rule.onNodeWithTag("player-seekbar").fetchSemanticsNode()
                    .config[SemanticsProperties.ProgressBarRangeInfo].current)
                assertEquals(scenario != "evicted", expected.targetAvailable)
            }
            if (scenario == "go-live-pending") {
                rule.onNodeWithTag("player-go-live").requestFocus().performKeyInput { pressKey(Key.Enter) }
                rule.mainClock.advanceTimeBy(500)
                rule.runOnIdle {
                    assertTrue(dispatches.isEmpty())
                    assertNull(owner.preview)
                    assertEquals(90.minutes.inWholeMilliseconds, state.positionMs)
                }
                capture("live")
                return
            }
            if (scenario == "held" || scenario == "evicted") {
                rule.runOnIdle { compactPreview = true }
                rule.mainClock.advanceTimeByFrame()
                capture("compact-preview")
                if (scenario == "evicted") rule.onNodeWithTag("timeshift-seek-preview").assertContentDescriptionContains(
                    "That position is no longer in the buffer.", substring = true)
                rule.runOnIdle { compactPreview = false }
                rule.mainClock.advanceTimeBy(50)
                rule.onNodeWithTag("player-seekbar").requestFocus()
            }
            val priorPaused = paused
            rule.onRoot().performKeyInput { pressKey(Key.Enter) }
            rule.mainClock.advanceTimeByFrame()
            assertEquals(!priorPaused, paused)
            rule.mainClock.advanceTimeBy(450)
            rule.waitForIdle()
            assertEquals(if (scenario == "evicted") emptyList<Long>() else listOf(target.position.inWholeMilliseconds), dispatches)
            if (scenario == "evicted") assertEquals("That position is no longer in the buffer.", owner.feedback)
            rule.mainClock.advanceTimeBy(1_000)
            rule.mainClock.autoAdvance = true
            capture("settled")
            assertEquals(actionTop, rule.onNodeWithTag("player-actions").fetchSemanticsNode().boundsInRoot.top)
            rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            rule.onNodeWithTag("player-pause").assertIsFocused()
            rule.onRoot().performKeyInput { repeat(5) { pressKey(Key.DirectionRight) } }
            rule.onNodeWithTag("player-go-live").assertIsFocused()
            rule.onRoot().performKeyInput { pressKey(Key.Enter) }
            rule.runOnIdle {
                assertEquals(90.minutes.inWholeMilliseconds, state.positionMs)
                assertNull(owner.preview)
                assertFalse(paused)
            }
            capture("live")
        } finally {
            rule.runOnIdle { owner.dispose() }
            TimeZone.setDefault(originalZone)
        }
    }

    private fun capture(stage: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "programme-window-captures")
        assertTrue(directory.isDirectory || directory.mkdirs())
        File(directory, "$scenario-$stage.png").outputStream().use {
            assertTrue(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun scenarios() = listOf("essential", "shallow", "missing", "midnight", "paused", "held", "late", "evicted", "go-live-pending", "font-scaled")
    }
}
