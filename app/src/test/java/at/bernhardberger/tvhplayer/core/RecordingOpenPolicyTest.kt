package at.bernhardberger.tvhplayer.core

import at.bernhardberger.tvheadend.sdk.core.*
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RecordingOpenPolicyTest {
    private fun entry(state: DvrEntryState = DvrEntryState.COMPLETED, position: Long? = null, duration: Long? = 600) =
        DvrEntry.create(DvrEntryId(1), state = state, playPosition = position?.seconds,
            start = Instant.fromEpochSeconds(0), stop = duration?.let(Instant::fromEpochSeconds))

    @Test fun unstartedAndFinishedPlayFromBeginning() {
        listOf(null, 0L, 570L, 600L, 800L).forEach {
            assertEquals(RecordingOpenAction.START_OVER, recordingOpenAction(entry(position = it), true))
        }
    }
    @Test fun positiveProgressBelowSdkCompletionPointPrompts() {
        listOf(1L, 300L, 569L).forEach {
            assertEquals(RecordingOpenAction.RESUME_CHOICE, recordingOpenAction(entry(position = it), true))
        }
        assertEquals(RecordingOpenAction.RESUME_CHOICE, recordingOpenAction(entry(position = 300, duration = null), true))
    }
    @Test fun growingRecordingsUseExistingPrimaryPlaybackWithoutPrompt() {
        assertEquals(RecordingOpenAction.START_OVER, recordingOpenAction(entry(DvrEntryState.RECORDING), true))
        assertEquals(RecordingOpenAction.RESUME, recordingOpenAction(entry(DvrEntryState.RECORDING, 590), true))
    }
    @Test fun scheduledFailedAndUnavailablePlaybackOpenDetails() {
        listOf(DvrEntryState.SCHEDULED, DvrEntryState.MISSED, DvrEntryState.INVALID,
            DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING).forEach {
            assertEquals(RecordingOpenAction.DETAILS, recordingOpenAction(entry(it, 300), true))
        }
        assertEquals(RecordingOpenAction.DETAILS, recordingOpenAction(entry(position = 300), false))
    }
}
