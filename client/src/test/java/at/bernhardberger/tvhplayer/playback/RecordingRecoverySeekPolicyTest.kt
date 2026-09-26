package at.bernhardberger.tvhplayer.playback

import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingRecoverySeekPolicyTest {
    private val recording = DvrEntryId(1)
    private val twentyMinutes = RecordingRecoveryPosition(recording, positionMs = 1_200_000, durationMs = 3_600_000)

    @Test fun aCompletedRecordingContinuesWhereItStood() {
        assertEquals(1_200_000L, recordingRecoverySeekMs(twentyMinutes, recording, completedRecording = true))
        assertEquals(1_200_000L, recordingRecoverySeekMs(twentyMinutes.copy(durationMs = null), recording, completedRecording = true))
    }

    @Test fun aGrowingRecordingKeepsTheViewersStart() {
        assertNull(recordingRecoverySeekMs(twentyMinutes, recording, completedRecording = false))
    }

    @Test fun onlyTheSameRecordingCarriesItsPosition() {
        assertNull(recordingRecoverySeekMs(twentyMinutes, DvrEntryId(2), completedRecording = true))
        assertNull(recordingRecoverySeekMs(null, recording, completedRecording = true))
    }

    @Test fun theStartOrAFinishedPositionKeepsTheViewersStart() {
        assertNull(recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 0), recording, completedRecording = true))
        // The SDK's default orderly-completion fraction (0.95) marks the finished point.
        assertNull(recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 3_420_000), recording, completedRecording = true))
        assertEquals(3_419_999L, recordingRecoverySeekMs(twentyMinutes.copy(positionMs = 3_419_999), recording, completedRecording = true))
    }
}
