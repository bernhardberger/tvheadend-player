package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvhplayer.core.BrowseContextAction.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowseContextActionsTest {
    private val event = EpgEvent.create(EventId(1), ChannelId(1), Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(200))
    private fun recording(state: DvrEntryState, position: Long = 0) =
        DvrEntry.create(DvrEntryId(1), state = state, playPosition = position.seconds)

    @Test fun channelsWithoutProgrammeOrPermissionDoNotOfferRecording() {
        assertEquals(listOf(WATCH, SHOW_GUIDE), channelContextActions(null, 100, null, true))
        assertEquals(listOf(WATCH, DETAILS, SHOW_GUIDE), channelContextActions(event, 100, null, false))
    }

    @Test fun channelsAndGuideReuseProgrammeRecordingPolicy() {
        listOf(null to RECORD, DvrEntryState.RECORDING to STOP, DvrEntryState.SCHEDULED to CANCEL).forEach { (state, action) ->
            val recording = state?.let { recording(it) }
            assertEquals(listOf(WATCH, DETAILS, action, SHOW_GUIDE), channelContextActions(event, 100, recording, true))
            assertEquals(listOf(DETAILS, action, WATCH), guideContextActions(event, 100, recording, true))
            assertEquals(listOf(DETAILS, WATCH), guideContextActions(event, 100, recording, false))
        }
        assertEquals(listOf(DETAILS, WATCH), guideContextActions(event, 200, null, true))
    }

    @Test fun completedRecordingsOfferResumeOnlyWithProgress() {
        assertEquals(listOf(PLAY, DETAILS, DELETE), recordingContextActions(recording(DvrEntryState.COMPLETED), true, true))
        assertEquals(listOf(RESUME, START_OVER, DETAILS, DELETE), recordingContextActions(recording(DvrEntryState.COMPLETED, 20), true, true))
    }

    @Test fun recordingAndScheduledActionsMatchLifecycle() {
        assertEquals(listOf(WATCH, START_OVER, DETAILS, STOP), recordingContextActions(recording(DvrEntryState.RECORDING, 20), true, true))
        assertEquals(listOf(WATCH, DETAILS, STOP), recordingContextActions(recording(DvrEntryState.RECORDING), true, true))
        assertEquals(listOf(DETAILS, CANCEL), recordingContextActions(recording(DvrEntryState.SCHEDULED), true, true))
    }

    @Test fun failedRecordingsOnlyOfferDetailsAndDelete() {
        listOf(DvrEntryState.MISSED, DvrEntryState.INVALID, DvrEntryState.RECORDING_ERROR,
            DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals(listOf(DETAILS, DELETE), recordingContextActions(recording(it), true, true))
        }
    }

    @Test fun unavailableAuthorityRemovesPlaybackAndMutations() {
        listOf(DvrEntryState.COMPLETED, DvrEntryState.RECORDING, DvrEntryState.SCHEDULED,
            DvrEntryState.MISSED, DvrEntryState.INVALID, DvrEntryState.RECORDING_ERROR,
            DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals(listOf(DETAILS), recordingContextActions(recording(it, 20), false, false))
        }
    }

    @Test fun playbackAndMutationAuthorityAreIndependent() {
        assertEquals(listOf(RESUME, START_OVER, DETAILS), recordingContextActions(
            recording(DvrEntryState.COMPLETED, 20), playbackEligible = true, canModifyRecordings = false))
        assertEquals(listOf(DETAILS, DELETE), recordingContextActions(
            recording(DvrEntryState.COMPLETED, 20), playbackEligible = false, canModifyRecordings = true))
    }
}
