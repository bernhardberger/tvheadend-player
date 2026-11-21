package at.bernhardberger.tvhplayer.touch.ui

import at.bernhardberger.tvhplayer.core.BrowseContextAction
import at.bernhardberger.tvhplayer.core.BrowseContextAction.CANCEL
import at.bernhardberger.tvhplayer.core.BrowseContextAction.DELETE
import at.bernhardberger.tvhplayer.core.BrowseContextAction.PLAY
import at.bernhardberger.tvhplayer.core.BrowseContextAction.RECORD
import at.bernhardberger.tvhplayer.core.BrowseContextAction.REMOVE_ENTRY
import at.bernhardberger.tvhplayer.core.BrowseContextAction.RESUME
import at.bernhardberger.tvhplayer.core.BrowseContextAction.SKIP
import at.bernhardberger.tvhplayer.core.BrowseContextAction.STOP
import at.bernhardberger.tvhplayer.core.BrowseContextAction.UNSKIP
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which control the touch screens offer for the shared DVR actions; Stop, Skip and Unskip wait for later. */
class DvrControlsTest {
    @Test
    fun theRecordKeyActionBecomesRecordOrCancel() {
        val offered = mapOf(RECORD to ProgramRecordControl.Record, CANCEL to ProgramRecordControl.Cancel)
        (BrowseContextAction.entries + null).forEach {
            assertEquals(it.toString(), offered[it], programRecordControl(it))
        }
    }

    @Test
    fun aRecordingsContextActionsGiveItsRemoveControl() {
        listOf(
            listOf(PLAY, RESUME, DELETE) to RecordingRemoveControl.Delete,
            listOf(REMOVE_ENTRY) to RecordingRemoveControl.RemoveEntry,
            listOf(STOP) to null,
            listOf(CANCEL) to null,
            listOf(SKIP, UNSKIP) to null,
            emptyList<BrowseContextAction>() to null,
        ).forEach { (actions, control) -> assertEquals(actions.toString(), control, recordingRemoveControl(actions)) }
    }
}
