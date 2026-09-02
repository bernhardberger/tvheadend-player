package at.bernhardberger.tvhplayer.ui.screens

import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EpgSearchObservationPolicyTest {
    @Test
    fun staleSearchResultCannotAcquireTheReconnectedGeneration() {
        val searched = observation()
        val reconnected = observation()

        assertNotSame(searched.currentSession, reconnected.currentSession)
        assertNull(searchResultObservation(searched, reconnected))
    }

    @Test
    fun currentSearchResultUsesTheLatestObservation() {
        val current = observation()

        assertSame(current, searchResultObservation(current, current))
    }

    @Test
    fun searchEventFindsRecordingOutsideRetainedTimelineWindow() {
        val event = EpgEvent.create(
            id = EventId(21),
            channelId = ChannelId(7),
            start = Instant.fromEpochSeconds(1_800_000_000),
            stop = Instant.fromEpochSeconds(1_800_003_600),
        )
        val recording = DvrEntry.create(
            id = DvrEntryId(31),
            eventId = event.id,
        )
        val observation = observation(recordings = listOf(recording))

        assertSame(recording, observation.dvrEntryForProgramme(event))
    }

    @Test
    fun pendingConfirmationDoesNotDispatchAfterGenerationReplacement() = runTest {
        val opened = observation()
        val replacement = observation()
        val openedSession = requireNotNull(opened.currentSession)
        val replacementSession = requireNotNull(replacement.currentSession)
        val mutation = DvrMutationAction.Cancel(openedSession, DvrEntryId(31))
        var dispatchCount = 0
        val actions = DvrMutationActions(
            scheduleEntry = { _, _ ->
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

        assertSame(mutation, currentDvrMutation(mutation, opened, openedSession))
        val feedback = actions.execute(
            currentDvrMutation(mutation, opened, replacementSession)
        )

        assertEquals(DvrMutationFeedback.CONNECTION_UNAVAILABLE, feedback)
        assertEquals(0, dispatchCount)
    }

    private fun observation(
        recordings: List<DvrEntry> = emptyList(),
    ): SessionObservation = SessionObservation.create(
        sessionState = SessionState.Ready(
            ServerCapabilities.create(
                streaming = CapabilityAccess.ALLOWED,
                dvrWrite = CapabilityAccess.ALLOWED,
            ),
        ),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = recordings)),
    )
}
