package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProgrammeWindowTest {
    private val live = Instant.parse("2026-09-07T20:30:00Z")
    private val a = event(1, "2026-09-07T19:00:00Z", "2026-09-07T20:00:00Z", "A")
    private val b = event(2, "2026-09-07T20:00:00Z", "2026-09-07T21:00:00Z", "B")
    private fun event(id: Long, start: String, stop: String, title: String) = EpgEvent.create(
        id = EventId(id), channelId = ChannelId(1), start = Instant.parse(start), stop = Instant.parse(stop), title = title)
    private fun lookup(time: Instant) = listOf(a, b).singleOrNull { time >= it.start && time < it.stop }
    private fun fixture() = TimeshiftTestFixture(120.minutes).apply {
        updateHistory(50.minutes, 90.minutes, estimatedLiveEdgeTime = live)
    }
    private fun TimeshiftTestFixture.presentation(position: Int = 65) = state.value.toAppPresentation(playbackPosition(position.minutes))

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
