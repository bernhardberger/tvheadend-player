package at.bernhardberger.tvhplayer.ui.screens.recordings

import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.recordingDetailsActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingEndActionTest {
    private fun endAction(state: DvrEntryState?) = recordingDetailsActions(
        DvrEntry.create(DvrEntryId(1), state = state), null, true, false,
    ).singleOrNull { it == BrowseContextAction.STOP || it == BrowseContextAction.CANCEL }
    @Test
    fun runningRecordingIsStoppedAndScheduledRecordingIsCancelled() {
        assertEquals(BrowseContextAction.STOP, endAction(DvrEntryState.RECORDING))
        assertEquals(BrowseContextAction.CANCEL, endAction(DvrEntryState.SCHEDULED))
    }

    @Test
    fun otherStatesOfferNeitherStopNorCancel() {
        listOf(DvrEntryState.COMPLETED, DvrEntryState.MISSED, DvrEntryState.INVALID,
            DvrEntryState.RECORDING_ERROR, DvrEntryState.COMPLETED_ERROR, DvrEntryState.FILE_MISSING,
            DvrEntryState.UNKNOWN)
            .forEach { assertNull(endAction(it)) }
        assertNull(endAction(null))
    }
}
