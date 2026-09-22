package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.testing.TimeshiftTestFixture
import at.bernhardberger.tvhplayer.playback.toAppPresentation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProgrammeWindowTest {
    private val live = Instant.parse("2026-09-07T20:30:00Z")
    private val a = event(1, "2026-09-07T19:00:00Z", "2026-09-07T20:00:00Z", "A")
    private val b = event(2, "2026-09-07T20:00:00Z", "2026-09-07T21:00:00Z", "B")
    private val snapshot = EpgSnapshot.create(events = listOf(b), historicalEvents = listOf(a))
    private val observation = SessionObservation.create(epgState = EpgRepositoryState.Current(snapshot))
    private fun event(id: Long, start: String, stop: String, title: String) = EpgEvent.create(
        id = EventId(id), channelId = ChannelId(1), start = Instant.parse(start), stop = Instant.parse(stop), title = title)
    private fun lookup(time: Instant) = observation.eventAt(ChannelId(1), time)
    private fun fixture() = TimeshiftTestFixture(120.minutes).apply {
        updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
    }
    private fun TimeshiftTestFixture.presentation(position: Int = 65) = state.value.toAppPresentation(playbackPosition(position.minutes))

    @Test fun archivedProgrammeIsDisplayOnlyAndColdSessionDoesNotInventHistory() {
        val fixture = fixture()
        val before = SessionObservation.create(epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(a))))
        assertEquals(a, before.eventAt(ChannelId(1), a.start + 55.minutes))
        assertEquals(listOf(b), snapshot.events)
        assertEquals(listOf(a), snapshot.historicalEvents)
        assertNull(observation.event(a.id))
        assertNull(currentProgrammeEvent(observation, a))
        assertEquals(b, currentProgrammeEvent(observation, b))
        val state = fixture.presentation(55)
        assertEquals(a, programmeWindow(state, eventAt = ::lookup)!!.event)
        val cold = SessionObservation.create(epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(b))))
        assertNull(programmeWindow(state) { cold.eventAt(ChannelId(1), it) })
    }

    @Test fun adjacentRetainedProgrammesFillToTheirOwnEndAndSwitchAtExclusiveBoundary() {
        val fixture = fixture()
        val state = fixture.presentation()
        fun window(seconds: Int) = programmeWindow(state, state.timeline!!.select(seconds.seconds), eventAt = ::lookup)!!
        val before = window(3_599)
        assertEquals(a, before.event)
        assertEquals(3_599f / 3_600, before.positionFraction, 0.00001f)
        assertEquals(1f, before.availableEndFraction, 0f)
        assertEquals(b, window(3_600).event)
        assertEquals(0f, window(3_600).positionFraction, 0f)
        assertEquals(a, window(3_599).event)
    }

    @Test fun previewIdentityNeverBorrowsCurrentBroadcastAndCancelRestoresLatestPlayback() {
        val fixture = fixture()
        val state = fixture.presentation()
        val committed = programmeWindow(state, eventAt = ::lookup)!!
        val target = state.timeline!!.select(55.minutes)!!
        val preview = programmeWindow(state, target, state.timeline, ::lookup)!!
        assertEquals(a, displayedProgrammeEvent(true, preview, committed, state, b))
        assertNull(displayedProgrammeEvent(true, null, committed, state, b))
        assertEquals(b, displayedProgrammeEvent(false, null, committed, state, b))
        assertNull(displayedProgrammeEvent(false, null, null, state, b))

        fixture.updateHistory(50.minutes, 91.minutes, estimatedLiveEdgeTime = live + 63.seconds)
        val latest = fixture.presentation()
        val stablePreview = programmeWindow(latest, target, state.timeline, ::lookup)!!
        assertEquals(preview.estimatedPosition, stablePreview.estimatedPosition)
        assertEquals(preview.positionFraction, stablePreview.positionFraction, 0f)
        assertEquals(preview.event, stablePreview.event)
        val actual = programmeWindow(latest, eventAt = ::lookup)!!
        assertNotEquals(committed.estimatedPosition, actual.estimatedPosition)
        assertEquals(b, displayedProgrammeEvent(false, null, actual, latest, a))
    }

    @Test fun unknownCommittedTimingCannotAuthorizeCurrentScheduleAfterPreviewDismissal() {
        val unknown = at.bernhardberger.tvhplayer.playback.AppTimeshiftState(
            available = true, paused = true, timingKnown = false,
        )
        assertNull(displayedProgrammeEvent(false, null, null, unknown, b))
        assertEquals(b, displayedProgrammeEvent(false, null, null,
            at.bernhardberger.tvhplayer.playback.AppTimeshiftState(), b))
    }

    @Test fun historicalBoundaryIgnoresEstimateWobbleButFollowsEvictionAndSegmentReplacement() = runTest {
        val fixture = fixture()
        fixture.updateHistory(80.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        val owner = LiveTimelinePresentationState(this, { 0L }, { testScheduler.currentTime })
        suspend fun sample(known: Boolean = true, position: Int = 85) = owner.sampleTimeshiftPresentation {
            fixture.presentation(position).copy(timingKnown = known)
        }!!.copy(historyStartTimeline = owner.historyStartTimeline)
        val first = sample()
        val firstWindow = programmeWindow(first, eventAt = ::lookup)!!
        assertEquals(20f / 60, firstWindow.availableStartFraction, 0.0001f)
        fixture.updateHistory(80.minutes, 91.minutes, estimatedLiveEdgeTime = live + 63.seconds)
        val shifted = sample()
        val shiftedWindow = programmeWindow(shifted, eventAt = ::lookup)!!
        assertEquals(firstWindow.availableStartFraction, shiftedWindow.availableStartFraction, 0f)
        assertNotEquals(firstWindow.availableEndFraction, shiftedWindow.availableEndFraction)
        assertNotEquals(firstWindow.estimatedPosition, shiftedWindow.estimatedPosition)
        assertSame(first.historyStartTimeline, sample(known = false).historyStartTimeline)
        fixture.updateHistory(81.minutes, 92.minutes, estimatedLiveEdgeTime = live + 117.seconds)
        assertEquals(21f / 60, programmeWindow(sample(), eventAt = ::lookup)!!.availableStartFraction, 0.0001f)
        val oldest = programmeWindow(sample(position = 81), eventAt = ::lookup)!!
        assertEquals(oldest.availableStartFraction, oldest.positionFraction, 0f)
        val evicted = programmeWindow(sample(position = 80), eventAt = ::lookup)!!
        assertTrue(evicted.positionFraction < evicted.availableStartFraction)
        assertFalse(evicted.targetAvailable)
        fixture.updateHistory(95.minutes, 105.minutes, estimatedLiveEdgeTime = live + 903.seconds)
        val beyondOriginalEdge = sample(position = 100)
        assertSame(first.historyStartTimeline, beyondOriginalEdge.historyStartTimeline)
        assertEquals(35f / 60, programmeWindow(beyondOriginalEdge, eventAt = ::lookup)!!.availableStartFraction, 0.0001f)
        fixture.restartSegment()
        fixture.updateHistory(81.minutes, 92.minutes, estimatedLiveEdgeTime = live + 117.seconds)
        val replacement = sample()
        assertNotSame(first.historyStartTimeline, replacement.historyStartTimeline)
        val fresh = programmeWindow(replacement.copy(historyStartTimeline = null), eventAt = ::lookup)!!
        assertEquals(fresh.availableStartFraction, programmeWindow(replacement, eventAt = ::lookup)!!.availableStartFraction, 0f)
        owner.dispose()
    }

    @Test fun windowsUseScheduleButAvailabilityUsesRuntimeHistory() {
        val fixture = fixture()
        val state = fixture.presentation()
        val current = requireNotNull(programmeWindow(state, eventAt = ::lookup))
        assertEquals("B", current.event.title)
        assertEquals(5f / 60, current.positionFraction, 0.0001f)
        assertEquals(0f, current.availableStartFraction)
        assertEquals(0.5f, current.liveFraction!!, 0.0001f)
        val previous = requireNotNull(programmeWindow(state, state.timeline!!.select(55.minutes), eventAt = ::lookup))
        assertEquals("A", previous.event.title)
        assertEquals(50f / 60, previous.availableStartFraction, 0.0001f)
        assertEquals(1f, previous.availableEndFraction)
        assertNull(previous.liveFraction)
        assertTrue(previous.targetAvailable)
        assertEquals("19:00" to "20:00", programmeWindowClockLabels(previous.event, ZoneId.of("UTC")))
        assertNull(state.timeline.select(0.minutes))
    }

    @Test fun validPresentedPositionAheadOfStatusEdgeDoesNotLookExpired() {
        val fixture = fixture()
        val state = fixture.presentation(91)
        val committed = requireNotNull(programmeWindow(state, eventAt = ::lookup))
        assertTrue(committed.targetAvailable)
        assertEquals(0.5f, committed.availableEndFraction)
        assertNull(state.timeline!!.select(91.minutes))
        val differentTarget = fixture.presentation(92).playbackTarget
        assertFalse(requireNotNull(programmeWindow(state, differentTarget, eventAt = ::lookup)).targetAvailable)
    }

    @Test fun elapsedFillTracksMappedLiveEndNotPlaybackOrPreviewPosition() {
        val fixture = fixture()
        val early = requireNotNull(programmeWindow(fixture.presentation(65), eventAt = ::lookup))
        val later = requireNotNull(programmeWindow(fixture.presentation(80), eventAt = ::lookup))
        assertNotEquals(early.positionFraction, later.positionFraction)
        assertEquals(0.5f, early.availableEndFraction, 0.0001f)
        assertEquals(early.availableEndFraction, later.availableEndFraction)

        fixture.updateHistory(50.minutes, 100.minutes, estimatedLiveEdgeTime = live + 10.minutes)
        val advanced = requireNotNull(programmeWindow(fixture.presentation(65), eventAt = ::lookup))
        assertEquals(early.positionFraction, advanced.positionFraction)
        assertEquals(40f / 60, advanced.availableEndFraction, 0.0001f)
        val completed = requireNotNull(programmeWindow(fixture.presentation(55), eventAt = ::lookup))
        assertNull(completed.liveFraction)
        assertEquals(1f, completed.availableEndFraction)
    }

    @Test fun shallowMissingGapMidnightAndReplacementStayHonest() {
        val fixture = fixture()
        fixture.updateHistory(89.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        val state = fixture.presentation(90)
        assertEquals(29f / 60, programmeWindow(state, eventAt = ::lookup)!!.availableStartFraction, 0.0001f)
        assertNull(programmeWindow(state) { null })
        assertNull(programmeWindow(state) { a })
        fixture.updateHistory(50.minutes, 90.minutes)
        assertNull(programmeWindow(fixture.presentation(), eventAt = ::lookup))
        fixture.replaceSubscription()
        fixture.updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        assertNull(programmeWindow(fixture.presentation(), state.playbackTarget, state.timeline, ::lookup))
        val midnight = event(3, "2026-09-07T23:30:00Z", "2026-09-08T00:30:00Z", "Midnight")
        val labels = programmeWindowClockLabels(midnight, ZoneId.of("UTC"))
        assertEquals("23:30", labels.first)
        assertEquals("00:30", labels.second)
        assertNull(programmeWindow(state.copy(timingKnown = false), eventAt = ::lookup))
    }

    @Test fun restartRejectsRetainedMappingEvenWhenTransportAndScheduleMatch() {
        val fixture = fixture()
        val previous = fixture.presentation()
        fixture.restartSegment()
        fixture.updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        val current = fixture.presentation()
        assertTrue(previous.timeline!!.describesSameSubscription(current.timeline))
        assertFalse(previous.timeline.describesSameSegment(current.timeline))
        assertNull(programmeWindow(current, previous.playbackTarget, previous.timeline, ::lookup))
        assertNotNull(programmeWindow(current, eventAt = ::lookup))
    }

    @Test fun pausedSeekKeepsSelectedProgrammeWhileCommittedPositionIsUnknown() {
        val fixture = fixture()
        val previous = fixture.presentation()
        val mapping = requireNotNull(previous.timeline)
        val target = requireNotNull(mapping.select(55.minutes))
        val pending = previous.copy(paused = true, timingKnown = false, playbackTarget = null)

        assertNull(programmeWindow(pending, eventAt = ::lookup))
        val selected = requireNotNull(programmeWindow(pending, target, mapping, ::lookup))
        assertEquals("A", selected.event.title)
        assertEquals(55f / 60, selected.positionFraction, 0.0001f)
        assertTrue(selected.targetAvailable)

        fixture.restartSegment()
        fixture.updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
        assertNull(programmeWindow(fixture.presentation().copy(timingKnown = false), target, mapping, ::lookup))
        assertNull(programmeWindow(pending.copy(available = false), target, mapping, ::lookup))
    }

    @Test fun previewPinsEstimateAcrossHeldInputLateMetadataPauseAndEviction() = runTest {
        val fixture = fixture()
        val owner = LiveTimelinePresentationState(this, { 0L }, { testScheduler.currentTime })
        val dispatched = mutableListOf<Long>()
        fun queue(delta: Long) = owner.queueRelativeSeek(fixture.presentation().copy(paused = true), delta,
            "unavailable", "expired", "replaced", "uncertain") { selection ->
            val target = selection.target
            fixture.seek(target) { dispatched += target.position.inWholeMilliseconds; fixture.completed() }
        }
        queue(-300_000L)
        val snapshot = owner.preview!!.mappingTimeline
        fixture.updateHistory(50.minutes, 91.minutes, serverPaused = true, estimatedLiveEdgeTime = live + 10.minutes)
        queue(-300_000L)
        val preview = owner.preview!!
        assertSame(snapshot, preview.mappingTimeline)
        assertEquals(55.minutes, preview.target.position)
        assertNull(programmeWindow(fixture.presentation(), preview.target, snapshot) { null })
        val availablePreview = requireNotNull(programmeWindow(fixture.presentation(), preview.target, snapshot, ::lookup))
        assertEquals("A", availablePreview.event.title)
        assertTrue(availablePreview.targetAvailable)
        assertEquals("B", programmeWindow(fixture.presentation(), eventAt = ::lookup)!!.event.title)
        fixture.updateHistory(56.minutes, 91.minutes, estimatedLiveEdgeTime = live)
        assertFalse(programmeWindow(fixture.presentation(), preview.target, snapshot, ::lookup)!!.targetAvailable)
        advanceTimeBy(400)
        runCurrent()
        assertTrue(dispatched.isEmpty())
        assertEquals("expired", owner.feedback)
        assertEquals(55.minutes, preview.target.position)
        owner.dispose()
    }
}
