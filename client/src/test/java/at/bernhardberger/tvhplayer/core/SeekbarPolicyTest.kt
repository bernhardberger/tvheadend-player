@file:OptIn(at.bernhardberger.tvheadend.sdk.core.TvheadendTestingApi::class)

package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekbarPolicyTest {
    private val live = Instant.parse("2026-09-07T20:30:00Z")
    private val a = EpgEvent.create(EventId(1), ChannelId(1),
        start = Instant.parse("2026-09-07T19:00:00Z"), stop = Instant.parse("2026-09-07T20:00:00Z"), title = "A")
    private val b = EpgEvent.create(EventId(2), ChannelId(1),
        start = Instant.parse("2026-09-07T20:00:00Z"), stop = Instant.parse("2026-09-07T21:00:00Z"), title = "B")
    private fun fixture() = TimeshiftTestFixture(120.minutes).apply {
        updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
    }
    private fun TimeshiftTestFixture.presentation(position: Int = 65) =
        state.value.toAppPresentation(playbackPosition(position.minutes))
    private val observation = SessionObservation.create(epgState = EpgRepositoryState.Current(
        EpgSnapshot.create(events = listOf(b), historicalEvents = listOf(a))))
    private fun lookup(time: Instant) = observation.eventAt(ChannelId(1), time)

    @Test fun currentBroadcastFallbackRequiresLiveStartOrCommittedNowTiming() {
        val unknown = AppTimeshiftState(available = true, paused = true, timingKnown = false)
        val granted = AppTimeshiftState(available = true, timingKnown = false)
        val known = AppTimeshiftState(available = true, bufferStartMs = -600_000,
            positionMs = -300_000, liveEdgeMs = 0, timingKnown = true)
        for ((state, liveStart, expected) in listOf(
            Triple(unknown, false, null),
            Triple(AppTimeshiftState(), false, b),
            Triple(granted, false, null),
            Triple(granted, true, b),
            Triple(known, false, null),
            Triple(known, true, b),
        )) {
            assertEquals("state=$state liveStart=$liveStart", expected,
                committedProgrammeEvent(false, null, state, b, liveStart))
        }
    }

    @Test fun retainedProgrammeIdentityAndSlotsNeverBorrowTheCurrentBroadcast() {
        val fixture = fixture()
        val state = fixture.presentation(55)
        val window = requireNotNull(programmePositionWindow(state, eventAt = ::lookup))
        assertEquals(a, window.event)
        assertEquals(a, committedProgrammeEvent(true, window.event, state, b, liveStart = true))
        val cold = SessionObservation.create(epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(b))))
        val slot = requireNotNull(programmePositionWindow(state) { cold.eventAt(ChannelId(1), it) })
        assertNull(slot.event)
        assertEquals(Instant.parse("2026-09-07T19:30:00Z") to Instant.parse("2026-09-07T20:00:00Z"), slot.start to slot.stop)
        assertNull(committedProgrammeEvent(true, slot.event, state, b, liveStart = true))
    }

    @Test fun restartRejectsRetainedMappingEvenWhenTransportAndScheduleMatch() {
        val fixture = fixture()
        val previous = fixture.presentation()
        fixture.restartSegment()
        fixture.updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        val current = fixture.presentation()
        assertTrue(previous.timeline!!.describesSameSubscription(current.timeline))
        assertFalse(previous.timeline!!.describesSameSegment(current.timeline))
        assertNull(programmePositionWindow(current, previous.playbackTarget, previous.timeline, eventAt = ::lookup))
        assertTrue(programmePositionWindow(current, eventAt = ::lookup) != null)
    }

    @Test fun missingMappingOrReplacementDoesNotBorrowLiveProgramme() {
        val fixture = fixture()
        fixture.updateHistory(89.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        val state = fixture.presentation(90)
        for (slot in listOf(programmePositionWindow(state) { null }, programmePositionWindow(state) { a })) {
            assertNull(slot!!.event)
            assertEquals(Instant.parse("2026-09-07T20:30:00Z") to Instant.parse("2026-09-07T21:00:00Z"), slot.start to slot.stop)
        }
        fixture.updateHistory(50.minutes, 90.minutes)
        assertNull(programmePositionWindow(fixture.presentation(), eventAt = ::lookup))
        fixture.replaceSubscription()
        fixture.updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        assertNull(programmePositionWindow(fixture.presentation(), state.playbackTarget, state.timeline, eventAt = ::lookup))
        assertNull(programmePositionWindow(state.copy(timingKnown = false), eventAt = ::lookup))
    }

    @Test fun pausedSeekKeepsSelectedProgrammeWhileCommittedPositionIsUnknown() {
        val fixture = fixture()
        val previous = fixture.presentation()
        val mapping = requireNotNull(previous.timeline)
        val target = requireNotNull(mapping.select(55.minutes))
        val pending = previous.copy(paused = true, timingKnown = false, playbackTarget = null)
        assertNull(programmePositionWindow(pending, eventAt = ::lookup))
        assertEquals("A", requireNotNull(programmePositionWindow(pending, target, mapping, eventAt = ::lookup)).event?.title)
        fixture.restartSegment()
        fixture.updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        assertNull(programmePositionWindow(fixture.presentation().copy(timingKnown = false), target, mapping, eventAt = ::lookup))
        assertNull(programmePositionWindow(pending.copy(available = false), target, mapping, eventAt = ::lookup))
    }

    @Test fun halfHourSlotsAlignToTheLocalClock() {
        val utc = ZoneId.of("UTC")
        assertEquals(Instant.parse("2026-09-07T20:30:00Z") to Instant.parse("2026-09-07T21:00:00Z"),
            halfHourSlot(Instant.parse("2026-09-07T20:40:00Z"), utc))
        assertEquals("a slot starts at its own boundary", Instant.parse("2026-09-07T21:00:00Z"),
            halfHourSlot(Instant.parse("2026-09-07T21:00:00Z"), utc).first)
        assertEquals(Instant.parse("2026-09-07T20:30:00Z"), halfHourSlot(Instant.parse("2026-09-07T20:59:59Z"), utc).first)
        assertEquals(Instant.parse("2026-09-07T04:15:00Z") to Instant.parse("2026-09-07T04:45:00Z"),
            halfHourSlot(Instant.parse("2026-09-07T04:22:00Z"), ZoneId.of("Asia/Kathmandu")))
    }

    @Test
    fun unknownTimingDoesNotClaimAKnownSeekbarPosition() {
        val unknown = AppTimeshiftState(available = true, timingKnown = false)
        val contradictory = unknown.copy(
            bufferStartMs = -60_000L,
            positionMs = -600_000L,
            serverBehindLiveMs = 600_000L,
        )
        for (state in listOf(unknown, contradictory)) {
            assertFalse(timeshiftSeekbarRange(state).positionKnown)
        }
    }

    @Test
    fun collectedHistoryFillsTheAxisLikeAGrowingRecording() {
        val state = AppTimeshiftState(
            available = true,
            bufferStartMs = -1_800_000L,
            positionMs = -900_000L,
            capacityMs = 7_200_000L,
        )
        val range = timeshiftSeekbarRange(state)
        assertEquals(-1_800_000L, range.displayStartMs)
        assertEquals(-1_800_000L, range.startMs)
        assertEquals(0f, range.availableStartFraction, 0.001f)
        assertEquals(0.5f, range.displayProgress, 0.001f)
        assertEquals(
            -1_800_000L,
            seekbarScrub(range.copy(positionMs = -1_700_000L), -1, 100),
        )
        assertEquals(1f, timeshiftSeekbarRange(state.copy(positionMs = 0L)).displayProgress, 0f)
    }

    @Test
    fun positionWithinTheLiveEdgeToleranceShowsPinnedAtTheEndOfAnExtrapolatedAxis() {
        // The display edge runs ahead of the verified edge; playback lags the verified edge.
        val state = AppTimeshiftState(
            available = true, bufferStartMs = -60_000L, liveEdgeMs = 0L, displayLiveEdgeMs = 4_000L,
        )
        fun progress(positionMs: Long) = timeshiftSeekbarRange(state.copy(positionMs = positionMs)).displayProgress

        assertEquals(1f, progress(0L), 0f)
        assertEquals(1f, progress(-2_000L), 0f)
        assertEquals(1f, progress(-TIMESHIFT_LIVE_EDGE_TOLERANCE_MS), 0f)
        val beyond = -TIMESHIFT_LIVE_EDGE_TOLERANCE_MS - 1L
        assertEquals((beyond + 60_000L).toFloat() / 64_000f, progress(beyond), 0.0001f)
        assertEquals(0.5f * 60_000f / 64_000f, progress(-30_000L), 0.0001f)
    }

    @Test
    fun forwardStepFromSlightlyBehindReachesTheLiveEdgeAndShowsItPinned() {
        val state = AppTimeshiftState(
            available = true, bufferStartMs = -60_000L, positionMs = -20_000L, liveEdgeMs = 0L, displayLiveEdgeMs = 3_000L,
        )
        val queue = queueTimeshiftSeek(TimeshiftSeekQueueState(), state, SEEKBAR_STEP_INITIAL_MS)
        val targetMs = requireNotNull(queue.pendingTargetMs)

        assertEquals(0L, targetMs)
        assertEquals(20_000L, queue.pendingDeltaMs)
        assertEquals(1f, timeshiftSeekbarRange(projectedTimeshiftState(state, targetMs)).displayProgress, 0f)
        // The controls' scrub reaches it too.
        val scrub = seekbarScrub(timeshiftSeekbarRange(state), 1, 0)
        assertEquals(0L, scrub)
        assertEquals(1f, timeshiftSeekbarRange(state.copy(positionMs = scrub)).displayProgress, 0f)
    }

    @Test
    fun unknownCapacityDoesNotReserveEmptyHistory() {
        val state = AppTimeshiftState(available = true, bufferStartMs = -60_000L)
        assertEquals(-60_000L, timeshiftSeekbarRange(state).displayStartMs)
        val expanded = timeshiftSeekbarRange(state.copy(bufferStartMs = -10_800_000L))
        assertEquals(-10_800_000L, expanded.displayStartMs)
        assertEquals(0f, expanded.availableStartFraction, 0f)
    }

    @Test
    fun changedGrantDoesNotRescaleCollectedHistory() {
        val state = AppTimeshiftState(
            available = true,
            bufferStartMs = -1_800_000L,
            capacityMs = 3_600_000L,
        )
        assertEquals(-1_800_000L, timeshiftSeekbarRange(state).displayStartMs)
        val reduced = timeshiftSeekbarRange(state.copy(capacityMs = 600_000L))
        assertEquals(-1_800_000L, reduced.displayStartMs)
        assertEquals(-1_800_000L, reduced.startMs)
    }

    @Test
    fun growingBufferUsesElapsedContentWhileExpiredPositionDoesNotGrantHistory() {
        val initial = AppTimeshiftState(
            available = true, bufferStartMs = -60_000L, positionMs = -30_000L,
        )
        assertEquals(0.5f, timeshiftSeekbarRange(initial).displayProgress, 0.001f)
        // Thirty seconds pass while paused: the same content stays thirty seconds from start.
        val grown = timeshiftSeekbarRange(initial.copy(bufferStartMs = -90_000L, positionMs = -60_000L))
        assertEquals(1f / 3f, grown.displayProgress, 0.001f)
        assertEquals(0f, grown.availableStartFraction, 0f)
        val expired = timeshiftSeekbarRange(initial.copy(bufferStartMs = -10_000L))
        assertEquals(-30_000L, expired.displayStartMs)
        assertEquals(-10_000L, expired.startMs)
        assertEquals(0f, expired.displayProgress, 0f)
        assertTrue(expired.availableStartFraction > expired.displayProgress)
        assertEquals(-10_000L, seekbarScrub(expired, -1, 0))
    }

    @Test
    fun recordingDomainIsZeroToDuration() {
        val range = requireNotNull(
            recordingSeekbarRange(positionMs = 45_000, durationMs = 120_000)
        )
        assertEquals(SeekbarDomain.RECORDING, range.domain)
        assertEquals(0L, range.startMs)
        assertEquals(120_000L, range.endMs)
        assertEquals(45_000L, range.positionMs)
        assertEquals(0.375f, range.progress, 0.001f)
    }

    @Test
    fun recordingTimelineRequiresARealPositiveDurationForSeeking() {
        val seekable = recordingTimelinePresentation(
            positionMs = 45_000,
            durationMs = 120_000,
            growing = false,
        )
        assertEquals(
            RecordingTimelinePresentation.Seekable(
                requireNotNull(recordingSeekbarRange(45_000, 120_000))
            ),
            seekable,
        )

        assertEquals(
            RecordingTimelinePresentation.StillRecording(elapsedMs = 45_000),
            recordingTimelinePresentation(
                positionMs = 45_000,
                durationMs = null,
                growing = true,
            ),
        )
        assertEquals(
            RecordingTimelinePresentation.DurationUnavailable(elapsedMs = 45_000),
            recordingTimelinePresentation(
                positionMs = 45_000,
                durationMs = 0,
                growing = false,
            ),
        )
        assertNull(recordingSeekbarRange(positionMs = 45_000, durationMs = null))
        assertNull(recordingSeekbarRange(positionMs = 45_000, durationMs = 0))
    }

    @Test
    fun timeshiftDomainIsBufferToLiveEdge() {
        val range = timeshiftSeekbarRange(
            AppTimeshiftState(
                available = true,
                bufferStartMs = -600_000,
                positionMs = -120_000,
                liveEdgeMs = 0,
            ),
        )
        assertEquals(SeekbarDomain.TIMESHIFT, range.domain)
        assertEquals(-600_000L, range.startMs)
        assertEquals(0L, range.endMs)
        assertEquals(-120_000L, range.positionMs)
    }

    @Test
    fun repeatAccelerationGrowsFromThirtySecondsToFiveMinutes() {
        assertEquals(30_000L, seekStepMs(0))
        assertEquals(30_000L, seekStepMs(3))
        assertEquals(120_000L, seekStepMs(4))
        assertEquals(120_000L, seekStepMs(11))
        assertEquals(300_000L, seekStepMs(12))
        for ((repeat, magnitude) in listOf(0 to 10_000L, 3 to 10_000L, 4 to 120_000L, 11 to 120_000L, 12 to 300_000L)) {
            assertEquals(-magnitude, playbackSeekStepMs(-1, repeat))
            assertEquals(seekStepMs(repeat), playbackSeekStepMs(1, repeat))
        }
    }

    @Test
    fun scrubStaysInsideRange() {
        val range = requireNotNull(recordingSeekbarRange(positionMs = 10_000, durationMs = 60_000))
        assertEquals(0L, seekbarScrub(range, direction = -1, repeatCount = 0))
        assertEquals(40_000L, seekbarScrub(range, direction = 1, repeatCount = 0))
        val nearEnd = requireNotNull(
            recordingSeekbarRange(positionMs = 55_000, durationMs = 60_000)
        )
        assertEquals(60_000L, seekbarScrub(nearEnd, direction = 1, repeatCount = 0))
    }

    @Test
    fun programmeTimingDescribesLivePlaybackEvenWithATimeshiftBuffer() {
        val live = AppTimeshiftState(
            available = true,
            bufferStartMs = -3_600_000L,
            positionMs = -TIMESHIFT_LIVE_EDGE_TOLERANCE_MS,
            liveEdgeMs = 0L,
        )
        assertTrue(programmeTimingDescribesPlayback(AppTimeshiftState()))
        assertTrue(programmeTimingDescribesPlayback(live))
        assertFalse(
            programmeTimingDescribesPlayback(
                live.copy(positionMs = -TIMESHIFT_LIVE_EDGE_TOLERANCE_MS - 1L),
            ),
        )
        assertFalse(programmeTimingDescribesPlayback(live.copy(timingKnown = false)))
    }

    @Test
    fun liveIsDecidedByTheServerShiftNotByDeliveryLatency() {
        // Directly after a tune the rendered position trails the history edge by
        // pipeline latency while TVHeadend serves the live edge.
        val freshlyTuned = AppTimeshiftState(
            available = true,
            bufferStartMs = 0L,
            positionMs = 2_000L,
            liveEdgeMs = 10_000L,
            serverBehindLiveMs = 0L,
        )
        val presentation = timeshiftPositionPresentation(freshlyTuned)
        assertTrue(presentation.atLiveEdge)
        assertEquals(0L, presentation.behindLiveMs)
        assertTrue(programmeTimingDescribesPlayback(freshlyTuned))

        val shifted = freshlyTuned.copy(serverBehindLiveMs = TIMESHIFT_LIVE_EDGE_TOLERANCE_MS + 1L)
        assertFalse(timeshiftPositionPresentation(shifted).atLiveEdge)
        assertEquals(TIMESHIFT_LIVE_EDGE_TOLERANCE_MS + 1L, timeshiftPositionPresentation(shifted).behindLiveMs)

        val unreported = freshlyTuned.copy(serverBehindLiveMs = null)
        assertFalse(timeshiftPositionPresentation(unreported).atLiveEdge)
        assertEquals(8_000L, timeshiftPositionPresentation(unreported).behindLiveMs)

        assertFalse(timeshiftPositionPresentation(freshlyTuned.copy(timingKnown = false)).atLiveEdge)
    }
}
