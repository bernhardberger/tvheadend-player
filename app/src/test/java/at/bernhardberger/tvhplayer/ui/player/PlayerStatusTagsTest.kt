package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStatusTagsTest {
    @Test fun currentDvrUpdatesRetireTheGrowingRecording() {
        val id = DvrEntryId(4)
        fun observation(state: DvrEntryState?) = SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(emptyList())),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(
                entries = state?.let { listOf(DvrEntry.create(id = id, state = it)) } ?: emptyList(),
            )),
        )
        val fake = FakeTvheadendSession(observation(DvrEntryState.RECORDING))
        val selection = currentRecordingPlaybackSelection(fake.observation.value, id)!!
        assertFalse(currentRecordingIsGrowing(fake.observation.value, null))
        assertTrue(currentRecordingIsGrowing(fake.observation.value, selection))
        for (state in listOf(DvrEntryState.COMPLETED, DvrEntryState.INVALID, null)) {
            fake.publish(observation(state))
            assertFalse(currentRecordingIsGrowing(fake.observation.value, selection))
        }
        fake.publish(observation(DvrEntryState.RECORDING))
        assertTrue(currentRecordingIsGrowing(fake.observation.value, selection))
        fake.replaceGeneration(observation(DvrEntryState.RECORDING))
        assertFalse(currentRecordingIsGrowing(fake.observation.value, selection))
    }
}
