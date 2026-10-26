@file:OptIn(at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekToken
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.core.liveBarEnd
import at.bernhardberger.tvhplayer.core.timeshiftSeekbarRange
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.common.formatClock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bar row of the player chrome: what its state cell, end, live state and step readout claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerChromeTimelineRowTest {
    @get:Rule val compose = createComposeRule()

    private val now = 1_800L
    private val atEdge = AppTimeshiftState(available = true, bufferStartMs = -600_000, positionMs = 0, liveEdgeMs = 0)

    // 1. Unavailable playback claims neither Live nor Playing.

    @Test fun anUnavailableChannelHasNoBarStatusAndAnAvailableOneHas() {
        var unavailable: PlayerBarStatus? = null
        var available: PlayerBarStatus? = null
        compose.setContent {
            unavailable = live(liveAvailable = false).barStatus(PlayerStateCell.PLAYING)
            available = live().barStatus(PlayerStateCell.PLAYING)
        }
        compose.waitForIdle()
        assertNull(unavailable)
        assertNotNull(available)
        assertNull(live(liveAvailable = false).barEnd())
    }

    @Test fun anAvailableChannelsBannerShowsTheStateCellAndAnnouncesTheLiveState() {
        chrome(PlayerChromeMode.BANNER, live())
        assertTrue(exists("player-state"))
        assertTrue(exists("player-live-state"))
    }

    @Test fun anAvailableChannelsControlsAnnounceTheLiveStateAndShowNoStateCell() {
        chrome(PlayerChromeMode.CONTROLS, live())
        assertFalse(exists("player-state"))
        assertTrue(exists("player-live-state"))
    }

    @Test fun anUnavailableChannelsControlsShowNoStateCellOrLiveState() {
        chrome(PlayerChromeMode.CONTROLS, live(liveAvailable = false))
        assertTrue("the controls stay", exists("player-actions"))
        assertFalse(exists("player-state"))
        assertFalse(exists("player-live-state"))
    }

    @Test fun theHiddenChipShowsTheRealStateOnlyWhileHiddenAndAvailable() {
        for (state in PlayerStateCell.entries) {
            assertEquals(state, hiddenChipState(state, hidden = true, available = true))
            assertEquals(PlayerStateCell.PLAYING, hiddenChipState(state, hidden = true, available = false))
            assertEquals(PlayerStateCell.PLAYING, hiddenChipState(state, hidden = false, available = true))
        }
    }

    // 2. The step readout agrees with the pinned progress: it measures against the verified live edge.

    private val extrapolated = atEdge.copy(displayLiveEdgeMs = 4_000)

    @Test fun theStepReadoutWithinToleranceOfTheVerifiedEdgeIsLiveEvenWithAnExtrapolatedAxis() {
        compose.setContent { TVHeadendPlayerTheme { TimeshiftSeekPreviewTimeline(extrapolated, TimeshiftSeekDecision(-4_000, -4_000, false)) } }
        compose.waitForIdle()
        assertTrue(exists("timeshift-preview-live"))
        assertTrue(texts("Live"))
    }

    @Test fun theStepReadoutBeyondToleranceOfTheVerifiedEdgeIsTheDistanceFromIt() {
        compose.setContent { TVHeadendPlayerTheme { TimeshiftSeekPreviewTimeline(extrapolated, TimeshiftSeekDecision(-6_000, -6_000, false)) } }
        compose.waitForIdle()
        assertFalse(exists("timeshift-preview-live"))
        assertTrue(exists("timeshift-preview-behind"))
        assertTrue(texts("−0:06"))
    }

    @Test fun aLiveStepBackLeadsWithTheOffsetAndShowsTheTargetClockSmall() {
        compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(atEdge.copy(timingKnown = true), TimeshiftSeekDecision(-180_000, -180_000, false), nowSec = now)
            }
        }
        compose.waitForIdle()
        assertTrue(texts("−3:00"))
        assertEquals(formatClock(now - 180), targetPosition())
    }

    @Test fun aLiveStepForwardFromTheBufferLeadsWithThePlusOffsetAndTheTargetClock() {
        val earlier = atEdge.copy(timingKnown = true, positionMs = -300_000)
        compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(earlier, TimeshiftSeekDecision(-120_000, 180_000, false), nowSec = now)
            }
        }
        compose.waitForIdle()
        assertTrue(texts("+3:00"))
        assertEquals(formatClock(now - 120), targetPosition())
        assertTrue(exists("timeshift-preview-ahead"))
    }

    @Test fun aLiveStepWithoutKnownTimingShowsTheDistanceBehindLiveAsTheTarget() {
        compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(atEdge.copy(timingKnown = false), TimeshiftSeekDecision(-180_000, -180_000, false), nowSec = now)
            }
        }
        compose.waitForIdle()
        assertTrue(texts("−3:00"))
        assertEquals("−3:00", targetPosition())
    }

    @Test fun theSeekbarReadoutAgreesWithTheVerifiedEdgeToo() {
        fun seekbar(positionMs: Long) = compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackSeekbar(
                    range = timeshiftSeekbarRange(extrapolated.copy(positionMs = positionMs)),
                    onSeekTo = {}, previewing = true, stepDeltaMs = positionMs,
                )
            }
        }
        seekbar(-4_000)
        compose.waitForIdle()
        assertTrue(exists("timeshift-preview-live"))
        assertTrue(texts("Live"))
    }

    @Test fun theSeekbarReadoutBeyondToleranceIsTheDistanceFromTheVerifiedEdge() {
        compose.setContent {
            TVHeadendPlayerTheme {
                PlaybackSeekbar(
                    range = timeshiftSeekbarRange(extrapolated.copy(positionMs = -6_000)),
                    onSeekTo = {}, previewing = true, stepDeltaMs = -6_000,
                )
            }
        }
        compose.waitForIdle()
        assertFalse(exists("timeshift-preview-live"))
        assertTrue(texts("−0:06"))
    }

    // 2b. What a step says: once, when it has been dispatched, without repeating itself.

    private fun spoken(tag: String, merged: Boolean = false): String =
        compose.onNodeWithTag(tag, useUnmergedTree = !merged).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()

    /** Lets a state change reach composition first, then runs [ms] and two more frames for the effects it launched. */
    private fun restFor(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
    }

    private fun previewOf(
        state: () -> AppTimeshiftState, decision: () -> TimeshiftSeekDecision?, settled: () -> Boolean, feedback: () -> String? = { null },
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                decision()?.let { TimeshiftSeekPreviewTimeline(state(), it, feedback = feedback(), nowSec = now, settled = settled()) }
            }
        }
        restFor(100)
    }

    @Test fun aLiveStepWithAKnownClockSaysTheOffsetTheTargetAndTheDistanceOnce() {
        previewOf({ atEdge.copy(timingKnown = true) }, { TimeshiftSeekDecision(-180_000, -180_000, false) }, { true })
        assertEquals(
            "3 minutes back, target ${formatClock(now - 180)}. 3:00 behind live. Buffer starts 10:00 behind live.",
            spoken("timeshift-seek-preview"),
        )
    }

    @Test fun aLiveStepToTheEdgeDoesNotSayLiveTwice() {
        previewOf({ extrapolated }, { TimeshiftSeekDecision(-4_000, 4_000, false) }, { true })
        val said = spoken("timeshift-seek-preview")
        assertTrue(said, said.startsWith("4 seconds forward, target Live. Buffer starts"))
        assertFalse(said, said.contains("Live. Live"))
    }

    @Test fun aLiveStepWithoutKnownTimingSaysTheDistanceBehindLiveOnce() {
        previewOf({ atEdge.copy(timingKnown = false) }, { TimeshiftSeekDecision(-180_000, -180_000, false) }, { true })
        assertEquals("3 minutes back, target 3:00 behind live. Buffer starts 10:00 behind live.", spoken("timeshift-seek-preview"))
    }

    @Test fun stepsInQuickSuccessionAreAnnouncedOnceWithTheFinalLiveValue() {
        var decision by mutableStateOf<TimeshiftSeekDecision?>(TimeshiftSeekDecision(-30_000, -30_000, false))
        var settled by mutableStateOf(false)
        previewOf({ atEdge.copy(timingKnown = true) }, { decision }, { settled })
        for (delta in listOf(-60_000L, -90_000L, -120_000L)) {
            compose.runOnIdle { decision = TimeshiftSeekDecision(delta, delta, false) }
            restFor(100)
            assertEquals("a held key announces nothing while it steps", "", spoken("timeshift-seek-preview"))
        }
        restFor(2_000)
        assertEquals("however long it holds", "", spoken("timeshift-seek-preview"))
        assertTrue("the label still follows every step", texts("−2:00"))
        compose.runOnIdle { settled = true }
        restFor(16)
        assertEquals(
            "2 minutes back, target ${formatClock(now - 120)}. 2:00 behind live. Buffer starts 10:00 behind live.",
            spoken("timeshift-seek-preview"),
        )
    }

    @Test fun aStepDispatchedAtOnceIsAnnouncedWithoutWaitingForAnyTimer() {
        // A released key commits straight away: dispatched, and the preview may close right after.
        var decision by mutableStateOf<TimeshiftSeekDecision?>(TimeshiftSeekDecision(-30_000, -30_000, false))
        previewOf({ atEdge.copy(timingKnown = true) }, { decision }, { true })
        assertEquals(
            "30 seconds back, target ${formatClock(now - 30)}. 0:30 behind live. Buffer starts 10:00 behind live.",
            spoken("timeshift-seek-preview"),
        )
        compose.runOnIdle { decision = null }
        restFor(2_000)
        assertFalse(exists("timeshift-seek-preview"))
    }

    @Test fun aCancelledStepAnnouncesNothingLate() {
        var decision by mutableStateOf<TimeshiftSeekDecision?>(TimeshiftSeekDecision(-30_000, -30_000, false))
        previewOf({ atEdge.copy(timingKnown = true) }, { decision }, { false })
        assertEquals("", spoken("timeshift-seek-preview"))
        compose.runOnIdle { decision = null }
        restFor(2_000)
        assertFalse("the preview is gone and nothing is left to speak", exists("timeshift-seek-preview"))
    }

    @Test fun aTickingBufferDoesNotAnnounceTheSameStepAgain() {
        var state by mutableStateOf(atEdge.copy(timingKnown = true))
        previewOf({ state }, { TimeshiftSeekDecision(-30_000, -30_000, false) }, { true })
        val said = spoken("timeshift-seek-preview")
        assertTrue(said, said.endsWith("Buffer starts 10:00 behind live."))
        compose.runOnIdle { state = state.copy(bufferStartMs = -660_000) }
        restFor(2_000)
        assertEquals("the buffer is read when the step is spoken", said, spoken("timeshift-seek-preview"))
    }

    @Test fun aFailureAfterTheDispatchIsStillAnnounced() {
        var feedback by mutableStateOf<String?>(null)
        previewOf({ atEdge.copy(timingKnown = true) }, { TimeshiftSeekDecision(-30_000, -30_000, false) }, { true }, { feedback })
        val said = spoken("timeshift-seek-preview")
        compose.runOnIdle { feedback = "Seek failed" }
        restFor(16)
        assertEquals("$said. Seek failed", spoken("timeshift-seek-preview"))
    }

    @Test fun aStepWhoseOffsetTheServerRefinesAfterTheDispatchIsAnnouncedOnce() {
        var decision by mutableStateOf(TimeshiftSeekDecision(-30_000, -30_000, false))
        var state by mutableStateOf(atEdge.copy(timingKnown = true))
        var announcements = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(state, decision, nowSec = now, settled = true, onAnnounced = { announcements++ })
            }
        }
        restFor(2_000)
        val said = spoken("timeshift-seek-preview")
        assertTrue(said, said.startsWith("30 seconds back, target"))
        assertEquals(1, announcements)
        // The resolved answer rewrites the offset by a few hundred milliseconds while the buffer ticks.
        compose.runOnIdle {
            decision = TimeshiftSeekDecision(-30_400, -30_400, false)
            state = state.copy(bufferStartMs = -660_000)
        }
        restFor(2_000)
        compose.runOnIdle { decision = TimeshiftSeekDecision(-29_700, -29_700, false) }
        restFor(2_000)
        assertEquals(said, spoken("timeshift-seek-preview"))
        assertEquals("the same step is not announced again", 1, announcements)
    }

    @Test fun aSuccessfulStepWhoseDispatchAndCompletionLandBeforeTheNextCompositionIsAnnouncedOnce() {
        val fixture = TimeshiftTestFixture(600.seconds).apply { updateHistory(start = 0.seconds, end = 600.seconds) }
        fun at(positionMs: Long, seek: TimeshiftSeekToken? = null) =
            fixture.state.value.toAppPresentation(
                if (seek == null) fixture.playbackPosition(positionMs.milliseconds) else fixture.playbackPosition(positionMs.milliseconds, seek),
            )
        // The state runs on its own scheduler, so the test decides when a frame is composed.
        val states = TestScope()
        val owner = LiveTimelinePresentationState(states, { 0L }, { states.testScheduler.currentTime })
        val accepted = fixture.completed(readerReached = null)
        var announcements = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                owner.preview?.let { preview ->
                    TimeshiftSeekPreviewTimeline(
                        at(540_000L), preview.decision, nowSec = now, settled = preview.dispatched,
                        onAnnounced = { announcements++; owner.stepAnnounced(preview.id) },
                    )
                }
            }
        }
        restFor(100)
        compose.runOnIdle {
            owner.queueRelativeSeek(at(540_000L), -30_000L, "unavailable", "expired", "replaced", "uncertain") { accepted }
        }
        restFor(100)
        assertEquals("a pending step announces nothing", "", spoken("timeshift-seek-preview"))
        compose.runOnIdle {
            states.advanceTimeBy(400L)
            states.runCurrent()
            assertTrue(requireNotNull(owner.preview).dispatched)
            assertNotNull(owner.preview?.acceptedSeek)
            // The correlated Player evidence of the completed seek arrives in the same frame.
            val evidence = at(510_000L, accepted.seek)
            assertEquals(evidence, runBlocking { owner.sampleTimeshiftPresentation { evidence } })
            assertNotNull("the finished step is kept to be announced", owner.preview)
        }
        restFor(16)
        val said = spoken("timeshift-seek-preview")
        assertTrue(said, said.startsWith("30 seconds back, target"))
        restFor(STEP_ANNOUNCEMENT_HOLD_MS + 100)
        assertEquals(1, announcements)
        assertNull("announced, the preview is retired", owner.preview)
        restFor(2_000)
        assertFalse(exists("timeshift-seek-preview"))
        assertEquals(1, announcements)
        owner.dispose()
    }

    @Test fun aStepQueuedAndCommittedBetweenTwoCompositionsIsAnnouncedAfreshAndAcknowledgesItself() {
        val fixture = TimeshiftTestFixture(600.seconds).apply { updateHistory(start = 0.seconds, end = 600.seconds) }
        val shown = fixture.state.value.toAppPresentation(fixture.playbackPosition(540_000L.milliseconds))
        val states = TestScope()
        val owner = LiveTimelinePresentationState(states, { 0L }, { states.testScheduler.currentTime })
        val acknowledged = mutableListOf<SeekStepId>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TVHeadendPlayerTheme {
                owner.preview?.let { preview ->
                    TimeshiftSeekPreviewTimeline(
                        shown, preview.decision, nowSec = now, settled = preview.dispatched,
                        onAnnounced = { acknowledged += preview.id }, stepId = preview.id,
                    )
                }
            }
        }
        restFor(100)
        fun step(deltaMs: Long): SeekStepId {
            val accepted = fixture.completed(readerReached = null)
            owner.queueRelativeSeek(shown, deltaMs, "unavailable", "expired", "replaced", "uncertain") { accepted }
            return requireNotNull(owner.preview).id
        }
        lateinit var first: SeekStepId
        compose.runOnIdle { first = step(-30_000L) }
        restFor(100)
        compose.runOnIdle { states.advanceTimeBy(400L); states.runCurrent() }
        restFor(STEP_ANNOUNCEMENT_HOLD_MS + 100)
        assertTrue(spoken("timeshift-seek-preview").startsWith("30 seconds back, target"))
        assertEquals(listOf(first), acknowledged)
        // Step B is queued and committed before any composition sees it pending: settled in both frames.
        lateinit var second: SeekStepId
        compose.runOnIdle {
            second = step(-30_000L)
            states.advanceTimeBy(400L)
            states.runCurrent()
            assertTrue(requireNotNull(owner.preview).dispatched)
        }
        restFor(16)
        assertTrue(spoken("timeshift-seek-preview"), spoken("timeshift-seek-preview").startsWith("1 minute back, target"))
        restFor(STEP_ANNOUNCEMENT_HOLD_MS + 100)
        assertEquals("the second step acknowledges itself, once", listOf(first, second), acknowledged)
        owner.dispose()
    }

    private val burstBuffer = atEdge.copy(timingKnown = true)
    private fun window(title: String) = ProgrammeWindow(
        EpgEvent.create(EventId(7), ChannelId(1), Instant.fromEpochSeconds(now - 3_600), Instant.fromEpochSeconds(now + 1_800), title = title),
        Instant.fromEpochSeconds(now), 0.5f, 0f, 1f, 1f, true,
    )
    private fun burstStep(deltaMs: Long, title: String, settled: Boolean) = live(
        burstBuffer.copy(positionMs = deltaMs), committedState = burstBuffer, previewing = true,
        step = TimeshiftSeekDecision(deltaMs, deltaMs, false), stepSettled = settled, programmeWindow = window(title),
    )

    @Test fun theFocusedSeekbarKeepsItsWholeSpokenValueStillDuringAHeldBurst() {
        var timeline by mutableStateOf(live(burstBuffer, programmeWindow = window("Known")))
        chromeOf(PlayerChromeMode.CONTROLS) { timeline }
        val before = spoken("player-seekbar", merged = true)
        assertTrue(before, before.startsWith("Known. "))
        for ((delta, title) in listOf(-30_000L to "Alpha", -60_000L to "Beta", -90_000L to "Gamma")) {
            compose.runOnIdle { timeline = burstStep(delta, title, settled = false) }
            restFor(100)
            assertEquals("nothing moves with the step, the programme included", before, spoken("player-seekbar", merged = true))
        }
        compose.runOnIdle { timeline = burstStep(-90_000, "Gamma", settled = true) }
        restFor(16)
        val settled = spoken("player-seekbar", merged = true)
        assertTrue(settled, settled.startsWith("Gamma. ") && settled.contains("1 minute 30 seconds back, target ${formatClock(now - 90)}"))
        for ((delta, title) in listOf(-120_000L to "Delta", -150_000L to "Epsilon")) {
            compose.runOnIdle { timeline = burstStep(delta, title, settled = false) }
            restFor(100)
            assertEquals("the last settled value stays until the next settles", settled, spoken("player-seekbar", merged = true))
        }
        compose.runOnIdle { timeline = live(burstBuffer, programmeWindow = window("Known")) }
        restFor(16)
        assertEquals("the step is over", before, spoken("player-seekbar", merged = true))
    }

    @Test fun theFocusedSeekbarSpeaksALiveStepWithAnHourOffset() {
        val buffer = atEdge.copy(timingKnown = true, bufferStartMs = -7_200_000)
        chrome(PlayerChromeMode.CONTROLS, live(buffer.copy(positionMs = -300_000), committedState = buffer.copy(positionMs = -4_000_000),
            previewing = true, step = TimeshiftSeekDecision(-300_000, 3_700_000, false), stepSettled = true))
        assertEquals(
            "1 hour 1 minute 40 seconds forward, target ${formatClock(now - 300)}. 5:00 behind live. Buffer starts 2:00:00 behind live.",
            spoken("player-seekbar", merged = true),
        )
    }

    @Test fun theFocusedRecordingSeekbarSpeaksAStepWithAnHourOffset() {
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(mode = PlayerChromeMode.CONTROLS, positionMs = 1_045_000, durationMs = 7_200_000,
                    targetMs = 4_765_000, originMs = 1_045_000, stepSettled = true)
            }
        }
        settle()
        assertEquals("1 hour 2 minutes forward, target 1:19:25. Total 2:00:00.", spoken("player-seekbar", merged = true))
    }

    // 3. A preview into a metadata gap does not borrow the committed programme.

    private val programme = EpgEvent.create(EventId(42), ChannelId(1), Instant.fromEpochSeconds(now - 3_600),
        Instant.fromEpochSeconds(now + 1_800), title = "Known")
    private val committedWindow = ProgrammeWindow(programme, Instant.fromEpochSeconds(now - 203), 0.6f, 0.4f, 0.65f, 0.65f, true)
    private val committedState = atEdge.copy(positionMs = -203_000)

    @Test fun atRestTheRightGroupCarriesTheCommittedProgrammesEnd() {
        chrome(PlayerChromeMode.CONTROLS, live(committedState, committedWindow = committedWindow))
        assertEquals("at rest the programme's end", formatClock(programme.stop.epochSeconds), endClock())
    }

    @Test fun aPreviewIntoAGapShowsTheBufferAxisNotTheCommittedProgrammesEnd() {
        chrome(PlayerChromeMode.CONTROLS, live(committedState.copy(positionMs = -500_000), committedState = committedState,
            committedWindow = committedWindow, previewing = true))
        assertFalse("no borrowed end clock", exists("player-end-clock") && endClock() == formatClock(programme.stop.epochSeconds))
        assertFalse("no programme clock on the left", exists("player-window-start"))
    }

    // 4. The ticking distance is confined to the leaf.

    @Test fun aTickingDistanceRecomposesTheLeafAndNotTheBlock() {
        val end = mutableStateOf(liveBarEnd(203_000, "21:45"))
        var blockCompositions = 0
        val counted = Modifier.composed { SideEffect { blockCompositions++ }; Modifier }
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerTimelineBlock(
                    progress = 0.5f, tone = PlayerTimelineTone.INTERACTIVE, reserveLabelSpace = true, modifier = counted,
                    status = rememberPlayerBarStatus(PlayerStateCell.PLAYING, end.value),
                )
            }
        }
        compose.waitForIdle()
        assertEquals("3:23 behind live", endText())
        assertEquals("21:45", endClock())
        val first = blockCompositions

        compose.runOnIdle { end.value = liveBarEnd(204_000, "21:45") }
        compose.waitForIdle()
        assertEquals("the leaf shows the tick", "3:24 behind live", endText())
        assertEquals("the block did not recompose", first, blockCompositions)

        compose.runOnIdle { end.value = liveBarEnd(205_000, "22:00") }
        compose.waitForIdle()
        assertEquals("3:25 behind live", endText())
        assertEquals("22:00", endClock())
        assertTrue("static row data does recompose the block", blockCompositions > first)
    }

    // 5. The buffer-start clock needs the timing, not a programme window.

    @Test fun theFirstStepShowsTheBufferStartClockOnceTheTimingIsKnown() {
        fun preview(state: AppTimeshiftState) = compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(state, TimeshiftSeekDecision(-30_000, -30_000, false),
                    barStatus = rememberPlayerBarStatus(PlayerStateCell.PLAYING, liveBarEnd(0, null)),
                    nowSec = now)
            }
        }
        preview(atEdge.copy(timingKnown = true))
        compose.waitForIdle()
        assertTrue(texts(formatClock(now - 600)))
        assertTrue(exists("timeshift-preview-buffer-start"))
    }

    @Test fun withoutKnownTimingTheStepShowsNoBufferStartClock() {
        compose.setContent {
            TVHeadendPlayerTheme {
                TimeshiftSeekPreviewTimeline(atEdge.copy(timingKnown = false), TimeshiftSeekDecision(-30_000, -30_000, false),
                    barStatus = rememberPlayerBarStatus(PlayerStateCell.PLAYING, liveBarEnd(0, null)),
                    nowSec = now)
            }
        }
        compose.waitForIdle()
        assertFalse(exists("timeshift-preview-buffer-start"))
    }

    // 6. A step to the live edge leads with the play glyph, never fast-forward.

    @Test fun aLiveBannerStepAtTheEdgeLeadsWithTheLiveGlyph() {
        chrome(PlayerChromeMode.BANNER_STEP, live(step = TimeshiftSeekDecision(0, 30_000, true)))
        assertTrue(exists("timeshift-preview-live"))
        assertFalse(exists("timeshift-preview-ahead"))
    }

    @Test fun aGrowingRecordingsBannerStepAtItsHeadLeadsWithTheLiveGlyph() {
        compose.setContent {
            TVHeadendPlayerTheme {
                RecordingChromeFixture(mode = PlayerChromeMode.BANNER_STEP, growing = true, positionMs = 3_400_000,
                    durationMs = 3_600_000, targetMs = 3_598_000, originMs = 3_400_000)
            }
        }
        settle()
        assertTrue(exists("timeshift-preview-live"))
        assertFalse(exists("timeshift-preview-ahead"))
    }

    private fun targetPosition(): String = compose.onNodeWithTag("timeshift-preview-target-position", useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun live(
        timeshift: AppTimeshiftState = atEdge,
        committedState: AppTimeshiftState = timeshift,
        committedWindow: ProgrammeWindow? = null,
        previewing: Boolean = false,
        step: TimeshiftSeekDecision? = null,
        liveAvailable: Boolean = true,
        stepSettled: Boolean = false,
        programmeWindow: ProgrammeWindow? = null,
    ) = PlayerChromeTimeline.Live(
        timeshift = timeshift, nowSec = now, committedTimeshift = committedState, committedWindow = committedWindow,
        programmeWindow = programmeWindow, previewing = previewing, step = step, stepDeltaMs = step?.deltaMs,
        stepSettled = stepSettled, liveAvailable = liveAvailable,
    )

    private fun chrome(mode: PlayerChromeMode, timeline: PlayerChromeTimeline.Live) = chromeOf(mode) { timeline }

    private fun chromeOf(mode: PlayerChromeMode, timeline: () -> PlayerChromeTimeline.Live) {
        compose.setContent {
            val context = LocalContext.current
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = mode,
                    content = PlayerChromeContent("20:15", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "One", null, null, false, now, "Programme")),
                    timeline = timeline(),
                    actions = PlayerChromeActions(active = mode == PlayerChromeMode.CONTROLS),
                    onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
                )
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.autoAdvance = false
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun exists(tag: String) =
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun texts(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun endClock(): String =
        compose.onNodeWithTagUnmerged("player-end-clock").config[SemanticsProperties.Text].joinToString { it.text }

    private fun endText(): String =
        compose.onNodeWithTagUnmerged("player-distance").config[SemanticsProperties.Text].joinToString { it.text }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithTagUnmerged(tag: String) =
        onNode(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode()
}
