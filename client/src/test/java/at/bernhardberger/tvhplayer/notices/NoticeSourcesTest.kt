package at.bernhardberger.tvhplayer.notices

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoticeSourcesTest {
    @Test fun externalSeriesCancellationsGroupDistinctEntriesSeparatelyFromSchedules() = runTest {
        val session = FakeTvheadendSession(readyObservation())
        val identity = session.observation.value.currentSession!!.generationIdentity
        val center = NoticeCenter({ testScheduler.currentTime }, { NoticeContext(0, identity) })
        backgroundScope.launch { DvrChangeNoticeSource(session.dvrRepository, center).run() }
        runCurrent()
        fun emit(id: Long, kind: DvrChangeKind, rule: String = "series") {
            val entry = DvrEntry.create(DvrEntryId(id), state = DvrEntryState.SCHEDULED, autorecRuleId = AutorecRuleId(rule))
            session.dvrRepository.emitChange(DvrChange.create(kind,
                previous = if (kind == DvrChangeKind.REMOVED) entry else null,
                current = if (kind == DvrChangeKind.REMOVED) null else entry,
                origin = DvrChangeOrigin.External, generationIdentity = identity))
        }
        emit(1, DvrChangeKind.SCHEDULED); emit(2, DvrChangeKind.SCHEDULED); emit(3, DvrChangeKind.SCHEDULED)
        runCurrent(); advanceTimeBy(500)
        emit(1, DvrChangeKind.REMOVED); emit(2, DvrChangeKind.REMOVED); emit(2, DvrChangeKind.REMOVED)
        emit(4, DvrChangeKind.REMOVED, "other")
        runCurrent(); advanceTimeBy(1_999); runCurrent()
        assertEquals(DvrChangeKind.SCHEDULED, (center.state.value.pending.single().notice as Notice.Dvr).kind)
        advanceTimeBy(1); runCurrent()
        val facts = center.state.value.pending.map { it.notice as Notice.Dvr }
        assertEquals(listOf(DvrChangeKind.SCHEDULED, DvrChangeKind.REMOVED, DvrChangeKind.REMOVED), facts.map { it.kind })
        assertEquals(listOf(1, 2, 1), facts.map { it.count })
    }

    @Test fun firstReadyAfterProfileChangeIsAcceptedWhenProfileArrivesFirst() = firstReadyAfterProfileChange(true)
    @Test fun firstReadyAfterProfileChangeIsAcceptedWhenObservationArrivesFirst() = firstReadyAfterProfileChange(false)

    private fun firstReadyAfterProfileChange(profileFirst: Boolean) = runTest {
        val observation = MutableStateFlow(SessionObservation.create(sessionState = SessionState.Connecting))
        val profile = MutableStateFlow(0L)
        val center = NoticeCenter({ testScheduler.currentTime }, { NoticeContext(profile.value, observation.value.currentSession?.generationIdentity) })
        backgroundScope.launch { ConnectionNoticeSource(observation, profile, center).run() }
        runCurrent()
        // Both inputs advance before combine's collector resumes; no prior Ready to retire.
        if (profileFirst) { profile.value = 1; observation.value = readyObservation() }
        else { observation.value = readyObservation(); profile.value = 1 }
        runCurrent()
        observation.value = SessionObservation.create(sessionState = SessionState.Connecting)
        runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.LOST), center.state.value.candidate!!.notice)
        observation.value = SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AuthenticationRejected))
        runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.LOGIN_REJECTED), center.state.value.candidate!!.notice)
    }

    @Test fun supersededReadyCannotAuthorizeLoginRejection() = runTest {
        val observation = MutableStateFlow(readyObservation())
        val profile = MutableStateFlow(0L)
        var advanceDuringPrune = true
        val center = NoticeCenter({ testScheduler.currentTime }) {
            if (advanceDuringPrune) {
                advanceDuringPrune = false
                observation.value = SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AuthenticationRejected))
            }
            NoticeContext(profile.value, observation.value.currentSession?.generationIdentity)
        }
        backgroundScope.launch { ConnectionNoticeSource(observation, profile, center).run() }
        runCurrent()
        assertNull(center.state.value.candidate)
    }

    @Test fun loginRejectionCannotBeStampedWithTheProfileThatReplacedItsObservation() = rejectedAtPostBoundary(false)
    @Test fun restoredCannotBeStampedWithTheProfileThatReplacedItsObservation() = rejectedAtPostBoundary(true)

    private fun rejectedAtPostBoundary(restoring: Boolean) = runTest {
        val observation = MutableStateFlow(readyObservation())
        val profile = MutableStateFlow(0L)
        var readsUntilReplacement = 0
        val center = NoticeCenter({ testScheduler.currentTime }) {
            if (readsUntilReplacement > 0 && --readsUntilReplacement == 0) {
                profile.value = 1
                observation.value = readyObservation()
            }
            NoticeContext(profile.value, observation.value.currentSession?.generationIdentity)
        }
        backgroundScope.launch { ConnectionNoticeSource(observation, profile, center).run() }
        runCurrent()
        if (restoring) {
            observation.value = SessionObservation.create(sessionState = SessionState.Connecting)
            runCurrent(); advanceTimeBy(3_000); runCurrent()
            assertEquals(Notice.Connection(ConnectionNoticeKind.LOST), center.state.value.candidate!!.notice)
        }
        // First read prunes on collection; second occurs when the outcome is posted.
        readsUntilReplacement = 2
        observation.value = if (restoring) readyObservation() else SessionObservation.create(
            sessionState = SessionState.Unavailable(SessionFailure.AuthenticationRejected))
        runCurrent()
        assertEquals(1L, profile.value)
        assertNull(center.state.value.candidate)
    }

    @Test fun debounceCannotBeStampedWithAReplacementSessionAndCanStartAgain() = runTest {
        val observation = MutableStateFlow(readyObservation())
        val profile = MutableStateFlow(0L)
        var replaceAtPost = false
        val center = NoticeCenter({ testScheduler.currentTime }) {
            if (replaceAtPost) { replaceAtPost = false; observation.value = readyObservation() }
            NoticeContext(profile.value, observation.value.currentSession?.generationIdentity)
        }
        backgroundScope.launch { ConnectionNoticeSource(observation, profile, center).run() }
        runCurrent()
        observation.value = SessionObservation.create(sessionState = SessionState.Connecting)
        runCurrent(); advanceTimeBy(3_000)
        replaceAtPost = true
        runCurrent()
        assertNull(center.state.value.candidate)
        observation.value = SessionObservation.create(sessionState = SessionState.Connecting)
        runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.LOST), center.state.value.candidate!!.notice)
    }

    @Test fun automaticBackoffKeepsDebounceThroughSynchronizationButProfileReplacementIsSilent() = runTest {
        val observation = MutableStateFlow(readyObservation())
        val profile = MutableStateFlow(0L)
        val center = NoticeCenter({ testScheduler.currentTime }, { NoticeContext(profile.value, observation.value.currentSession?.generationIdentity) })
        backgroundScope.launch { ConnectionNoticeSource(observation, profile, center).run() }
        runCurrent()
        observation.value = SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.TransportUnavailable))
        runCurrent(); advanceTimeBy(1_000)
        observation.value = SessionObservation.create(sessionState = SessionState.Synchronizing)
        runCurrent(); advanceTimeBy(2_000); runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.LOST), center.state.value.candidate!!.notice)
        observation.value = readyObservation(); runCurrent()
        profile.value = 1; runCurrent()
        assertNull(center.state.value.candidate)
        observation.value = SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AuthenticationRejected))
        runCurrent()
        assertNull(center.state.value.candidate)
        observation.value = readyObservation(); runCurrent()
        observation.value = SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AuthenticationRejected))
        runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.LOGIN_REJECTED), center.state.value.candidate!!.notice)
    }

    @Test fun startedEntryDoesNotReceiveAnOlderBufferedSeriesSchedule() = runTest {
        val session = FakeTvheadendSession(readyObservation())
        val identity = session.observation.value.currentSession!!.generationIdentity
        val center = NoticeCenter({ testScheduler.currentTime }, { NoticeContext(0, identity) })
        backgroundScope.launch { DvrChangeNoticeSource(session.dvrRepository, center).run() }
        runCurrent()
        val scheduled = DvrEntry.create(DvrEntryId(1), state = DvrEntryState.SCHEDULED, autorecRuleId = AutorecRuleId("series"))
        session.dvrRepository.emitChange(DvrChange.create(DvrChangeKind.SCHEDULED, null, scheduled, DvrChangeOrigin.External, identity))
        runCurrent(); advanceTimeBy(1_000)
        session.dvrRepository.emitChange(DvrChange.create(DvrChangeKind.RECORDING_STARTED, scheduled,
            DvrEntry.create(DvrEntryId(1), state = DvrEntryState.RECORDING), DvrChangeOrigin.External, identity))
        runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertEquals(DvrChangeKind.RECORDING_STARTED, (center.state.value.pending.single().notice as Notice.Dvr).kind)
    }

    @Test fun serverFactsForOwnActionsAreShownAndExternalArchiveRemovalIsSilent() = runTest {
        val session = FakeTvheadendSession(readyObservation())
        val identity = session.observation.value.currentSession!!.generationIdentity
        val context = NoticeContext(0, identity)
        val center = NoticeCenter({ testScheduler.currentTime }, { context })
        backgroundScope.launch { DvrChangeNoticeSource(session.dvrRepository, center).run() }
        runCurrent()
        fun emit(id: Int, kind: DvrChangeKind, state: DvrEntryState, origin: DvrChangeOrigin) {
            val entry = DvrEntry.create(DvrEntryId(id.toLong()), state = state)
            session.dvrRepository.emitChange(DvrChange.create(kind,
                previous = if (kind == DvrChangeKind.REMOVED) entry else null,
                current = if (kind == DvrChangeKind.REMOVED) null else entry,
                origin = origin, generationIdentity = identity))
        }
        emit(1, DvrChangeKind.SCHEDULED, DvrEntryState.SCHEDULED, DvrChangeOrigin.ThisClient(DvrMutationKind.SCHEDULE))
        emit(2, DvrChangeKind.RECORDING_STARTED, DvrEntryState.RECORDING, DvrChangeOrigin.ThisClient(DvrMutationKind.SCHEDULE))
        emit(3, DvrChangeKind.REMOVED, DvrEntryState.COMPLETED, DvrChangeOrigin.External)
        emit(4, DvrChangeKind.REMOVED, DvrEntryState.SCHEDULED, DvrChangeOrigin.External)
        emit(5, DvrChangeKind.REMOVED, DvrEntryState.COMPLETED, DvrChangeOrigin.ThisClient(DvrMutationKind.DELETE))
        emit(6, DvrChangeKind.RECORDING_ABORTED, DvrEntryState.COMPLETED, DvrChangeOrigin.External)
        runCurrent()
        assertEquals(listOf(1L, 2L, 4L, 5L, 6L), center.state.value.pending.map { (it.notice as Notice.Dvr).entry.id.value })
    }

    @Test fun seriesGroupsDistinctEntriesPerRuleAndRejectsRetiredGeneration() = runTest {
        val session = FakeTvheadendSession(readyObservation())
        val identity = session.observation.value.currentSession!!.generationIdentity
        var context = NoticeContext(0, identity)
        val center = NoticeCenter({ testScheduler.currentTime }, { context })
        backgroundScope.launch { DvrChangeNoticeSource(session.dvrRepository, center).run() }
        runCurrent()
        fun emit(id: Int, rule: String) = session.dvrRepository.emitChange(DvrChange.create(
            DvrChangeKind.SCHEDULED, previous = null,
            current = DvrEntry.create(DvrEntryId(id.toLong()), state = DvrEntryState.SCHEDULED, autorecRuleId = AutorecRuleId(rule)),
            origin = DvrChangeOrigin.External, generationIdentity = identity))
        emit(1, "a"); emit(2, "a"); emit(2, "a"); emit(3, "b")
        runCurrent(); advanceTimeBy(1_999); runCurrent()
        assertTrue(center.state.value.pending.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(2, 1), center.state.value.pending.map { (it.notice as Notice.Dvr).count })
        emit(4, "c"); runCurrent()
        context = NoticeContext(1, readyObservation().currentSession!!.generationIdentity)
        center.prune()
        emit(5, "d"); runCurrent(); advanceTimeBy(2_000); runCurrent()
        assertNull(center.state.value.candidate)
    }

    @Test fun startupFailuresAndBriefReconnectsAreSilentThenLostRestoredArePaired() = runTest {
        val observation = MutableStateFlow(SessionObservation.create(sessionState = SessionState.Connecting))
        val profile = MutableStateFlow(0L)
        val center = NoticeCenter({ testScheduler.currentTime }, { NoticeContext(profile.value, observation.value.currentSession?.generationIdentity) })
        backgroundScope.launch { ConnectionNoticeSource(observation, profile, center).run() }
        runCurrent(); advanceTimeBy(4_000); runCurrent()
        assertNull(center.state.value.candidate)
        observation.value = readyObservation(); runCurrent()
        observation.value = SessionObservation.create(sessionState = SessionState.Connecting); runCurrent()
        advanceTimeBy(2_999); runCurrent()
        observation.value = readyObservation(); runCurrent()
        assertNull(center.state.value.candidate)
        observation.value = SessionObservation.create(sessionState = SessionState.Connecting); runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.LOST), center.state.value.candidate!!.notice)
        assertTrue(center.show(center.state.value.candidate!!.id, 6_000))
        observation.value = readyObservation(); runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.RESTORED), center.state.value.candidate!!.notice)
    }

    @Test fun loginRejectionRequiresReadyAndProfileChangeCancelsPendingLoss() = runTest {
        val rejected = SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.AuthenticationRejected))
        val observation = MutableStateFlow(rejected)
        val profile = MutableStateFlow(0L)
        val center = NoticeCenter({ testScheduler.currentTime }, { NoticeContext(profile.value, observation.value.currentSession?.generationIdentity) })
        backgroundScope.launch { ConnectionNoticeSource(observation, profile, center).run() }
        runCurrent()
        assertNull(center.state.value.candidate)
        observation.value = readyObservation(); runCurrent()
        observation.value = rejected; runCurrent()
        assertEquals(Notice.Connection(ConnectionNoticeKind.LOGIN_REJECTED), center.state.value.candidate!!.notice)
        observation.value = readyObservation(); runCurrent()
        observation.value = SessionObservation.create(sessionState = SessionState.Connecting); runCurrent()
        advanceTimeBy(1_000)
        profile.value = 1; runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertNull(center.state.value.candidate)
        observation.value = rejected; runCurrent()
        assertNull(center.state.value.candidate)
    }
}
