package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DvrEventPolicyTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private fun entry(state: DvrEntryState?, id: Long = 1, rule: String? = null, start: Instant? = now + 60.seconds) =
        DvrEntry.create(id = DvrEntryId(id), state = state, title = "Nature", channelName = "Documentary",
            start = start, autorecRuleId = rule?.let(::AutorecRuleId))
    private fun intent(kind: DvrLocalIntentKind, age: Long = 0, id: Long = 1) =
        DvrLocalIntent(DvrEntryId(id), kind, now - age.seconds)
    private fun change(from: DvrEntryState, to: DvrEntryState?, intents: List<DvrLocalIntent> = emptyList()) =
        dvrEvents(listOf(entry(from)), to?.let { listOf(entry(it)) }.orEmpty(), now, intents)

    @Test fun onlyNewFutureScheduledEntriesAreScheduled() {
        val scheduled = entry(DvrEntryState.SCHEDULED)
        assertEquals(DvrEventKind.SCHEDULED, dvrEvents(emptyList(), listOf(scheduled), now, emptyList()).single().kind)
        assertTrue(dvrEvents(listOf(scheduled), listOf(scheduled), now, emptyList()).isEmpty())
        for (start in listOf(null, now, now - 1.seconds)) {
            assertTrue(dvrEvents(emptyList(), listOf(entry(DvrEntryState.SCHEDULED, start = start)), now, emptyList()).isEmpty())
        }
    }

    @Test fun seriesGroupsOnlyNewFutureEntriesOfTheSameRuleInOneDiff() {
        val old = entry(DvrEntryState.SCHEDULED, 1, "series")
        val events = dvrEvents(listOf(old), listOf(old, entry(DvrEntryState.SCHEDULED, 2, "series"),
            entry(DvrEntryState.SCHEDULED, 3, "series"), entry(DvrEntryState.SCHEDULED, 4, "other"),
            entry(DvrEntryState.SCHEDULED, 5)), now, emptyList())
        assertEquals(listOf(DvrEventKind.SERIES_SCHEDULED, DvrEventKind.SCHEDULED, DvrEventKind.SCHEDULED), events.map { it.kind })
        assertEquals(2, events.first().count)
        assertEquals("dvr-series:series", events.first().key)
        assertEquals("dvr:4", events[1].key)
        assertEquals(DvrEventKind.SCHEDULED, dvrEvents(listOf(old), listOf(old,
            entry(DvrEntryState.SCHEDULED, 2, "series")), now, emptyList()).single().kind)
    }

    @Test fun addedRecordingAndScheduledToRecordingStart() {
        assertEquals(DvrEventKind.STARTED, dvrEvents(emptyList(), listOf(entry(DvrEntryState.RECORDING)), now, emptyList()).single().kind)
        assertEquals(DvrEventKind.STARTED, change(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING).single().kind)
    }

    @Test fun naturalCompletionFinishes() {
        assertEquals(DvrEventKind.FINISHED, change(DvrEntryState.RECORDING, DvrEntryState.COMPLETED).single().kind)
    }

    @Test fun localStopCorrelatesCompletionOrRemovalThroughSixtySecondsInclusive() {
        for (age in listOf(0L, 59L, 60L)) for (to in listOf(DvrEntryState.COMPLETED, null)) {
            assertEquals(DvrEventKind.STOPPED, change(DvrEntryState.RECORDING, to,
                listOf(intent(DvrLocalIntentKind.STOP, age))).single().kind)
        }
    }

    @Test fun expiredFutureWrongIdAndWrongKindCannotStop() {
        for (local in listOf(intent(DvrLocalIntentKind.STOP, 61), intent(DvrLocalIntentKind.STOP, -1),
            intent(DvrLocalIntentKind.STOP, id = 2), intent(DvrLocalIntentKind.DELETE))) {
            assertEquals(DvrEventKind.FINISHED, change(DvrEntryState.RECORDING, DvrEntryState.COMPLETED, listOf(local)).single().kind)
            assertTrue(change(DvrEntryState.RECORDING, null, listOf(local)).isEmpty())
        }
    }

    @Test fun bothRecordingErrorsFailEvenAfterLocalStop() {
        for (state in listOf(DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR)) {
            assertEquals(DvrEventKind.FAILED, change(DvrEntryState.RECORDING, state,
                listOf(intent(DvrLocalIntentKind.STOP))).single().kind)
        }
    }

    @Test fun scheduledToMissedAndScheduledRemoval() {
        assertEquals(DvrEventKind.MISSED, change(DvrEntryState.SCHEDULED, DvrEntryState.MISSED).single().kind)
        assertEquals(DvrEventKind.CANCELED, change(DvrEntryState.SCHEDULED, null).single().kind)
    }

    @Test fun localDeleteOnlyDeletesCompletedOrFailedEntriesNotRetentionCleanup() {
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR, DvrEntryState.MISSED)) {
            assertTrue(change(state, null).isEmpty())
            assertEquals(DvrEventKind.DELETED, change(state, null, listOf(intent(DvrLocalIntentKind.DELETE, 60))).single().kind)
            assertTrue(change(state, null, listOf(intent(DvrLocalIntentKind.DELETE, 61))).isEmpty())
        }
    }

    @Test fun unrecognizedStatesAndUncoveredRemovalsAreSilent() {
        for (state in listOf(DvrEntryState.FILE_MISSING, DvrEntryState.INVALID, DvrEntryState.UNKNOWN)) {
            assertTrue(change(DvrEntryState.RECORDING, state).isEmpty())
            assertTrue(change(state, null, listOf(intent(DvrLocalIntentKind.DELETE))).isEmpty())
            assertTrue(dvrEvents(emptyList(), listOf(entry(state)), now, emptyList()).isEmpty())
        }
        assertTrue(change(DvrEntryState.RECORDING, null).isEmpty())
    }

    @Test fun metadataAndStatisticsUpdatesAreSilent() {
        val old = entry(DvrEntryState.RECORDING)
        val updated = DvrEntry.create(old.id, state = old.state, title = "Edited title", start = now,
            stop = now + 100.seconds, channelName = "Edited channel", dataSizeBytes = 1234,
            streamErrors = 4, subscriptionError = DvrSubscriptionError.BAD_SIGNAL)
        assertTrue(dvrEvents(listOf(old), listOf(updated), now, emptyList()).isEmpty())
    }

    @Test fun initialCurrentAndNewGenerationAreSilentBaselines() {
        val baseline = DvrEventBaseline()
        val source = FakeTvheadendSession(observation(listOf(entry(DvrEntryState.SCHEDULED))))
        assertNull(baseline.advance(source.observation.value))
        source.publish(observation(listOf(entry(DvrEntryState.RECORDING))))
        val (old, new) = requireNotNull(baseline.advance(source.observation.value))
        assertEquals(DvrEventKind.STARTED, dvrEvents(old, new, now, emptyList()).single().kind)
        val reconnected = FakeTvheadendSession(observation(listOf(entry(DvrEntryState.SCHEDULED, 2))))
        assertNull(baseline.advance(reconnected.observation.value))
    }

    @Test fun nonCurrentStatesNeverEmitAndNextCurrentRebaselines() {
        for (state in listOf(DvrRepositoryState.Empty, DvrRepositoryState.Synchronizing(DvrSnapshot.create()),
            DvrRepositoryState.Stale(DvrSnapshot.create()))) {
            val baseline = DvrEventBaseline()
            val source = FakeTvheadendSession(observation(listOf(entry(DvrEntryState.SCHEDULED))))
            baseline.advance(source.observation.value)
            source.publish(observation(emptyList(), state))
            assertNull(baseline.advance(source.observation.value))
            source.publish(observation(listOf(entry(DvrEntryState.RECORDING))))
            assertNull(baseline.advance(source.observation.value))
            source.publish(observation(listOf(entry(DvrEntryState.COMPLETED))))
            val (old, new) = requireNotNull(baseline.advance(source.observation.value))
            assertEquals(DvrEventKind.FINISHED, dvrEvents(old, new, now, emptyList()).single().kind)
        }
    }

    @Test fun registryIsGenerationScopedExpiresAndExcludesRejectedCommands() {
        var time = now
        val registry = RecentDvrIntents { time }
        val generation = FakeSessionObservation(observation(emptyList())).captureCurrentSession().generationIdentity
        val ticket = registry.begin(generation, DvrEntryId(1), DvrLocalIntentKind.STOP)
        assertFalse(ticket.accepted.isCompleted)
        registry.finish(ticket, true)
        assertEquals(listOf(ticket), registry.capture(generation))
        val other = FakeSessionObservation(observation(emptyList())).captureCurrentSession().generationIdentity
        assertTrue(registry.capture(other).isEmpty())
        val failed = registry.begin(generation, DvrEntryId(2), DvrLocalIntentKind.DELETE)
        registry.finish(failed, false)
        assertEquals(listOf(ticket), registry.capture(generation))
        time += 60.seconds
        assertEquals(listOf(ticket), registry.capture(generation))
        time += 1.seconds
        assertTrue(registry.capture(generation).isEmpty())
    }

    private fun observation(entries: List<DvrEntry>, state: DvrRepositoryState = DvrRepositoryState.Current(DvrSnapshot.create(entries))) =
        SessionObservation.create(sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()), dvrState = state)
}
