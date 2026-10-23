package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvhplayer.notices.toDvrMutationFeedback

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrProgressResult
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSchedule
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionObservation
import at.bernhardberger.tvhplayer.core.ProgrammeRecordingTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrMutationActionsTest {
    @Test
    fun createStopCancelAndDeleteDispatchCapturedTypedActions() = runTest {
        val capability = currentSession()
        var scheduledCapability: CurrentSessionObservation? = null
        var scheduledRequest: DvrScheduleRequest? = null
        var stopped: Pair<CurrentSessionObservation, DvrEntryId>? = null
        var cancelled: Pair<CurrentSessionObservation, DvrEntryId>? = null
        var deleted: Pair<CurrentSessionObservation, DvrEntryId>? = null
        val actions = DvrMutationActions(
            scheduleEntry = { currentSession, request ->
                scheduledCapability = currentSession
                scheduledRequest = request
                DvrMutationResult.AcceptedButUnconfirmed(DvrEntryId(70))
            },
            stopEntry = { currentSession, id ->
                stopped = currentSession to id
                DvrMutationResult.Confirmed(Unit)
            },
            cancelEntry = { currentSession, id ->
                cancelled = currentSession to id
                DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false)
            },
            deleteEntry = { currentSession, id ->
                deleted = currentSession to id
                DvrMutationResult.Failed(TvheadendFailure.TRANSPORT_UNAVAILABLE, mayHaveApplied = false)
            },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )
        val target = ProgrammeRecordingTarget(
            eventId = EventId(7),
            channelId = null,
            start = 100,
            stop = 200,
            title = "News",
            currentSession = capability,
        )
        val configId = DvrConfigId("primary")
        val recordingId = DvrEntryId(9)

        assertEquals(
            DvrMutationFeedback.ACCEPTED_UNCONFIRMED,
            actions.execute(DvrMutationAction.CreateProgramme(target, configId)).toDvrMutationFeedback(),
        )
        assertEquals(
            DvrMutationFeedback.CONFIRMED,
            actions.execute(DvrMutationAction.Stop(capability, recordingId)).toDvrMutationFeedback(),
        )
        assertEquals(
            DvrMutationFeedback.PERMISSION_DENIED,
            actions.execute(DvrMutationAction.Cancel(capability, recordingId)).toDvrMutationFeedback(),
        )
        assertEquals(
            DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            actions.execute(DvrMutationAction.Delete(capability, recordingId)).toDvrMutationFeedback(),
        )

        assertSame(capability, scheduledCapability)
        assertEquals(
            DvrScheduleRequest(
                schedule = DvrSchedule.Programme(EventId(7)),
                configId = configId,
                title = "News",
            ),
            scheduledRequest,
        )
        assertEquals(capability to recordingId, stopped)
        assertEquals(capability to recordingId, cancelled)
        assertEquals(capability to recordingId, deleted)
    }

    @Test
    fun stopDispatchesOnlyStopEntry() = runTest {
        val capability = currentSession()
        val calls = mutableListOf<String>()
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> calls += "schedule"; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            stopEntry = { _, _ -> calls += "stop"; DvrMutationResult.AcceptedButUnconfirmed(Unit) },
            cancelEntry = { _, _ -> calls += "cancel"; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            deleteEntry = { _, _ -> calls += "delete"; DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )

        val feedback = actions.execute(DvrMutationAction.Stop(capability, DvrEntryId(4))).toDvrMutationFeedback()

        assertEquals(listOf("stop"), calls)
        assertEquals(DvrMutationFeedback.ACCEPTED_UNCONFIRMED, feedback)
    }

    @Test
    fun watchStateDispatchesCapturedSessionAndDesiredStateOnceWithoutRetryingFailure() = runTest {
        val capability = currentSession()
        val recordingId = DvrEntryId(9)
        val calls = mutableListOf<Triple<CurrentSessionObservation, DvrEntryId, Boolean>>()
        val failure = DvrProgressResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true)
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> error("Unexpected schedule") },
            stopEntry = { _, _ -> error("Unexpected stop") },
            cancelEntry = { _, _ -> error("Unexpected cancel") },
            deleteEntry = { _, _ -> error("Unexpected delete") },
            updateWatchStateCommand = { session, id, watched ->
                calls += Triple(session, id, watched)
                if (watched) DvrProgressResult.Accepted else failure
            },
        )

        assertSame(DvrProgressResult.Accepted, actions.updateWatchState(capability, recordingId, true))
        assertSame(failure, actions.updateWatchState(capability, recordingId, false))
        assertEquals(listOf(Triple(capability, recordingId, true), Triple(capability, recordingId, false)), calls)
    }

    @Test
    fun watchStateFailuresPreserveExistingDvrFeedbackAndDispatchEvidence() {
        val cases = listOf(
            Triple(TvheadendFailure.ACCESS_DENIED, false, DvrMutationFeedback.PERMISSION_DENIED),
            Triple(TvheadendFailure.CONNECTION_LIMIT, false, DvrMutationFeedback.CONNECTION_LIMIT),
            Triple(TvheadendFailure.SERVER_REJECTED, false, DvrMutationFeedback.REJECTED),
            Triple(TvheadendFailure.NOT_SUPPORTED, false, DvrMutationFeedback.NOT_SUPPORTED),
            Triple(TvheadendFailure.TIMEOUT, true, DvrMutationFeedback.TIMEOUT),
            Triple(TvheadendFailure.NOT_READY, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.OBSERVATION_EXPIRED, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.OBSERVATION_EXPIRED, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
            Triple(TvheadendFailure.CONNECTION_CHANGED, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.CONNECTION_CHANGED, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
            Triple(TvheadendFailure.TRANSPORT_UNAVAILABLE, false, DvrMutationFeedback.CONNECTION_UNAVAILABLE),
            Triple(TvheadendFailure.TRANSPORT_UNAVAILABLE, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
            Triple(TvheadendFailure.MALFORMED_RESPONSE, false, DvrMutationFeedback.REJECTED),
            Triple(TvheadendFailure.MALFORMED_RESPONSE, true, DvrMutationFeedback.UNKNOWN_OUTCOME),
        )
        cases.forEach { (failure, mayHaveApplied, expected) ->
            val feedback = DvrProgressResult.Failed(failure, mayHaveApplied).toDvrMutationFeedback()
            assertEquals("$failure mayHaveApplied=$mayHaveApplied", expected, feedback)
            assertEquals(DvrMutationResult.Failed(failure, mayHaveApplied).toDvrMutationFeedback(), feedback)
            assertTrue(feedback.isFailure)
        }
    }

    @Test
    fun missingCapturedActionPresentsNotReadyWithoutDispatch() = runTest {
        var dispatchCount = 0
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            stopEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            cancelEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            deleteEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false)
            },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )

        val feedback = actions.execute(null).toDvrMutationFeedback()

        assertEquals(DvrMutationFeedback.CONNECTION_UNAVAILABLE, feedback)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun everyReleasedMutationResultHasOneStablePresentation() {
        val accepted = mapOf(
            DvrMutationResult.Confirmed(Unit) to DvrMutationFeedback.CONFIRMED,
            DvrMutationResult.AcceptedButUnconfirmed(Unit) to DvrMutationFeedback.ACCEPTED_UNCONFIRMED,
        )
        val failures = mapOf(
            DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false) to DvrMutationFeedback.PERMISSION_DENIED,
            DvrMutationResult.Failed(TvheadendFailure.CONNECTION_LIMIT, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_LIMIT,
            DvrMutationResult.Failed(TvheadendFailure.SERVER_REJECTED, mayHaveApplied = false) to DvrMutationFeedback.REJECTED,
            DvrMutationResult.Failed(TvheadendFailure.NOT_SUPPORTED, mayHaveApplied = false) to DvrMutationFeedback.NOT_SUPPORTED,
            DvrMutationResult.Failed(TvheadendFailure.TIMEOUT, mayHaveApplied = true) to DvrMutationFeedback.TIMEOUT,
            DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.NOT_READY, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.Failed(TvheadendFailure.OBSERVATION_EXPIRED, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.Failed(TvheadendFailure.TRANSPORT_UNAVAILABLE, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
        )

        accepted.forEach { (result, expected) ->
            val feedback = result.toDvrMutationFeedback()
            assertEquals(expected, feedback)
            assertFalse(feedback.isFailure)
        }
        failures.forEach { (result, expected) ->
            val feedback = result.toDvrMutationFeedback()
            assertEquals(expected, feedback)
            assertTrue(feedback.isFailure)
        }
    }

    @Test
    fun connectionChangesAndUnlistedFailuresPreserveMutationStageEvidence() {
        val cases = listOf(
            DvrMutationResult.Failed(TvheadendFailure.TRANSPORT_UNAVAILABLE, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.CONNECTION_CHANGED, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.CONNECTION_CHANGED, mayHaveApplied = false) to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) to DvrMutationFeedback.UNKNOWN_OUTCOME,
            DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = false) to DvrMutationFeedback.REJECTED,
        )
        cases.forEach { (result, expected) ->
            assertEquals(expected, result.toDvrMutationFeedback())
            assertTrue(result.toDvrMutationFeedback().isFailure)
        }
    }

    @Test
    fun malformedMutationIsUncertainAndNeverRepeated() = runTest {
        val calls = mutableListOf<String>()
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ -> calls += "schedule"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            stopEntry = { _, _ -> calls += "stop"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            cancelEntry = { _, _ -> calls += "cancel"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            deleteEntry = { _, _ -> calls += "delete"; DvrMutationResult.Failed(TvheadendFailure.MALFORMED_RESPONSE, mayHaveApplied = true) },
            updateWatchStateCommand = { _, _, _ -> error("Unexpected watch-state update") },
        )
        val current = currentSession()
        val target = ProgrammeRecordingTarget(EventId(7), null, 100, 200, "News", current)
        val actionsToRun = listOf(
            DvrMutationAction.CreateProgramme(target, null),
            DvrMutationAction.Stop(current, DvrEntryId(4)),
            DvrMutationAction.Cancel(current, DvrEntryId(4)),
            DvrMutationAction.Delete(current, DvrEntryId(4)),
        )
        actionsToRun.forEach { action ->
            assertEquals(DvrMutationFeedback.UNKNOWN_OUTCOME, actions.execute(action).toDvrMutationFeedback())
        }
        assertEquals(listOf("schedule", "stop", "cancel", "delete"), calls)
    }

    private fun currentSession(): CurrentSessionObservation = FakeSessionObservation(
        SessionObservation.create(
            sessionState = SessionState.Ready(
                ServerCapabilities.create(
                    streaming = CapabilityAccess.ALLOWED,
                    dvrWrite = CapabilityAccess.ALLOWED,
                ),
            ),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        ),
    ).captureCurrentSession()
}
