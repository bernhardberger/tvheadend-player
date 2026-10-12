package at.bernhardberger.tvhplayer.ui.screens.recordings

import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingEndActionTest {
    @Test
    fun runningRecordingIsStoppedAndScheduledRecordingIsCancelled() {
        assertEquals(RecordingDetailsAction.STOP, recordingEndAction(DvrEntryState.RECORDING))
        assertEquals(RecordingDetailsAction.CANCEL, recordingEndAction(DvrEntryState.SCHEDULED))
    }

    @Test
    fun otherStatesOfferNeitherStopNorCancel() {
        listOf(DvrEntryState.COMPLETED, DvrEntryState.MISSED, DvrEntryState.INVALID,
            DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING,
            DvrEntryState.UNKNOWN)
            .forEach { assertNull(recordingEndAction(it)) }
        assertNull(recordingEndAction(null))
    }
}
