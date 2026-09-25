package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
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
                DvrMutationResult.AccessDenied
            },
            deleteEntry = { currentSession, id ->
                deleted = currentSession to id
                DvrMutationResult.TransportUnavailable
            },
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
            actions.execute(DvrMutationAction.CreateProgramme(target, configId)),
        )
        assertEquals(
            DvrMutationFeedback.CONFIRMED,
            actions.execute(DvrMutationAction.Stop(capability, recordingId)),
        )
        assertEquals(
            DvrMutationFeedback.PERMISSION_DENIED,
            actions.execute(DvrMutationAction.Cancel(capability, recordingId)),
        )
        assertEquals(
            DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            actions.execute(DvrMutationAction.Delete(capability, recordingId)),
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
            scheduleEntry = { _, _ -> calls += "schedule"; DvrMutationResult.NotReady },
            stopEntry = { _, _ -> calls += "stop"; DvrMutationResult.AcceptedButUnconfirmed(Unit) },
            cancelEntry = { _, _ -> calls += "cancel"; DvrMutationResult.NotReady },
            deleteEntry = { _, _ -> calls += "delete"; DvrMutationResult.NotReady },
        )

        val feedback = actions.execute(DvrMutationAction.Stop(capability, DvrEntryId(4)))

        assertEquals(listOf("stop"), calls)
        assertEquals(DvrMutationFeedback.ACCEPTED_UNCONFIRMED, feedback)
    }

    @Test
    fun missingCapturedActionPresentsNotReadyWithoutDispatch() = runTest {
        var dispatchCount = 0
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.NotReady
            },
            stopEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.NotReady
            },
            cancelEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.NotReady
            },
            deleteEntry = { _, _ ->
                dispatchCount++
                DvrMutationResult.NotReady
            },
        )

        val feedback = actions.execute(null)

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
            DvrMutationResult.AccessDenied to DvrMutationFeedback.PERMISSION_DENIED,
            DvrMutationResult.ConnectionLimit to DvrMutationFeedback.CONNECTION_LIMIT,
            DvrMutationResult.ServerRejected to DvrMutationFeedback.REJECTED,
            DvrMutationResult.NotSupported to DvrMutationFeedback.NOT_SUPPORTED,
            DvrMutationResult.Timeout to DvrMutationFeedback.TIMEOUT,
            DvrMutationResult.NotReady to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.ObservationExpired to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
            DvrMutationResult.TransportUnavailable to DvrMutationFeedback.CONNECTION_UNAVAILABLE,
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
