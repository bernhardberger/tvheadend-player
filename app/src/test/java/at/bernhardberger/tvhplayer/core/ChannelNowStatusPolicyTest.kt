package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvhplayer.ui.screens.isScheduled
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelNowStatusPolicyTest {
    @Test fun runningRecordingsMarkTheirChannelWithOrWithoutErrors() {
        val entries = listOf(
            DvrEntryState.RECORDING, DvrEntryState.RECORDING_ERROR, DvrEntryState.SCHEDULED,
            DvrEntryState.COMPLETED_ERROR,
        ).mapIndexed { index, state ->
            DvrEntry.create(DvrEntryId(index + 1L), state = state, channelId = ChannelId(index + 1L))
        }

        assertEquals(setOf(ChannelId(1), ChannelId(2)), activeRecordingChannelIds(entries, 0))
    }

    @Test fun paddingFollowsEachProgrammesOwnEntryOnABackToBackChannel() {
        val channel = ChannelId(1)
        val boundary = Instant.fromEpochSeconds(3_600)
        val a = EpgEvent.create(EventId(1), channel, boundary - 30.minutes, boundary)
        val b = EpgEvent.create(EventId(2), channel, boundary, boundary + 30.minutes)
        val postPadding = DvrEntry.create(DvrEntryId(1), channelId = channel, eventId = a.id,
            start = a.start, stop = a.stop, stopExtraMinutes = 5.minutes, state = DvrEntryState.RECORDING)
        val prePadding = DvrEntry.create(DvrEntryId(2), channelId = channel, eventId = b.id,
            start = b.start, stop = b.stop, startExtraMinutes = 2.minutes, state = DvrEntryState.RECORDING_ERROR)

        val aOnly = observation(postPadding)
        assertEquals(emptySet<ChannelId>(), activeRecordingChannelIds(listOf(postPadding), boundary.epochSeconds))
        assertEquals(emptySet<ChannelId>(), activeRecordingChannelIds(listOf(postPadding), (boundary + 3.minutes).epochSeconds))
        assertEquals(emptySet<ChannelId>(), activeRecordingChannelIds(listOf(prePadding), (boundary - 2.minutes).epochSeconds))
        assertEquals(postPadding, aOnly.runningRecordingForEvent(a))
        assertNull("A's post-padding does not mark B", aOnly.runningRecordingForEvent(b))
        assertEquals("B is marked in its own pre-padding", prePadding, observation(prePadding).runningRecordingForEvent(b))
        val both = observation(postPadding, prePadding)
        assertEquals(postPadding, both.runningRecordingForEvent(a))
        assertEquals(prePadding, both.runningRecordingForEvent(b))
        for ((now, event) in listOf(boundary - 2.minutes to a, boundary to b, boundary + 3.minutes to b)) {
            val inWindow = channel in activeRecordingChannelIds(listOf(postPadding, prePadding), now.epochSeconds)
            assertTrue(inWindow)
            assertFalse(showChannelRecordingMark(inWindow, both.runningRecordingForEvent(event) != null))
        }
    }

    @Test fun unlinkedTimerAndStaleLinkOnlyMarkTheChannel() {
        val channel = ChannelId(1)
        val start = Instant.fromEpochSeconds(3_600)
        val timer = DvrEntry.create(DvrEntryId(1), channelId = channel,
            start = start, stop = start + 30.minutes, state = DvrEntryState.RECORDING)
        val previous = DvrEntry.create(DvrEntryId(2), channelId = channel, eventId = EventId(1),
            state = DvrEntryState.RECORDING)
        val event = EpgEvent.create(EventId(2), channel, start, start + 30.minutes)
        val stale = EpgEvent.create(event.id, channel, start, start + 30.minutes, dvrEntryId = previous.id)

        assertNull(observation(timer).runningRecordingForEvent(event))
        val inside = (start + 15.minutes).epochSeconds
        assertTrue(showChannelRecordingMark(
            channel in activeRecordingChannelIds(listOf(timer), inside),
            observation(timer).runningRecordingForEvent(event) != null,
        ))
        assertNull(observation(previous).runningRecordingForEvent(stale))
        assertEquals(setOf(channel), activeRecordingChannelIds(listOf(previous), inside))
        val scheduled = DvrEntry.create(previous.id, channelId = channel, eventId = previous.eventId,
            state = DvrEntryState.SCHEDULED)
        val withStaleSchedule = SessionObservation.create(
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(stale))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = listOf(scheduled))),
        )
        assertFalse("A stale reverse link cannot put a clock on another programme", withStaleSchedule.isScheduled(stale))
    }

    private fun observation(vararg entries: DvrEntry) =
        SessionObservation.create(dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = entries.toList())))
}
