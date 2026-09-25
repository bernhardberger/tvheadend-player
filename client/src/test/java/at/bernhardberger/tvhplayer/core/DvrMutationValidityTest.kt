package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import org.junit.Assert.*
import org.junit.Test

class DvrMutationValidityTest {
    @Test fun scheduledToRecordingInvalidatesCancel() = checkTransition(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING, false)
    @Test fun recordingToCompletedInvalidatesStop() = checkTransition(DvrEntryState.RECORDING, DvrEntryState.COMPLETED, false)
    @Test fun removedEntryInvalidatesCancel() = checkTransition(DvrEntryState.SCHEDULED, null, false)
    @Test fun removedEntryInvalidatesStop() = checkTransition(DvrEntryState.RECORDING, null, false)
    @Test fun unchangedScheduledKeepsCancel() = checkTransition(DvrEntryState.SCHEDULED, DvrEntryState.SCHEDULED, true)
    @Test fun unchangedRecordingKeepsStop() = checkTransition(DvrEntryState.RECORDING, DvrEntryState.RECORDING, true)
    @Test fun differentSessionInvalidatesBoth() {
        for (state in listOf(DvrEntryState.SCHEDULED, DvrEntryState.RECORDING)) {
            val session = FakeTvheadendSession(observation(state))
            val captured = requireNotNull(session.observation.value.currentSession)
            session.replaceGeneration(observation(state))
            assertFalse(dvrMutationStateIsCurrent(captured, DvrEntryId(1), state, session.observation.value))
        }
    }

    private fun checkTransition(before: DvrEntryState, after: DvrEntryState?, valid: Boolean) {
        val session = FakeTvheadendSession(observation(before))
        val captured = requireNotNull(session.observation.value.currentSession)
        session.publish(observation(after))
        assertSame(captured, session.observation.value.currentSession)
        assertEquals(valid, dvrMutationStateIsCurrent(captured, DvrEntryId(1), before, session.observation.value))
    }

    private fun observation(state: DvrEntryState?) = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(
            streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED,
        )),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create()),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = state?.let {
            listOf(DvrEntry.create(id = DvrEntryId(1), state = it))
        }.orEmpty())),
    )
}
