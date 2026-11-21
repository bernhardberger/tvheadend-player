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
import at.bernhardberger.tvheadend.sdk.core.AutorecRuleId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.TimerecRuleId
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

    @Test
    fun onlyAnEnabledRuleScheduleShowsAsScheduledByRule() {
        val series = AutorecRuleId("series")
        val timer = TimerecRuleId("timer")
        listOf(
            entry(DvrEntryState.SCHEDULED, series) to true,
            entry(DvrEntryState.SCHEDULED, timer = timer) to true,
            entry(DvrEntryState.SCHEDULED) to false,
            entry(DvrEntryState.SCHEDULED, series, enabled = false) to false,
            entry(DvrEntryState.RECORDING, series) to false,
            null to false,
        ).forEach { (entry, byRule) -> assertEquals(entry.toString(), byRule, scheduledByRule(entry)) }
    }

    private fun entry(
        state: DvrEntryState,
        series: AutorecRuleId? = null,
        timer: TimerecRuleId? = null,
        enabled: Boolean? = null,
    ) = DvrEntry.create(
        DvrEntryId(1), channelId = ChannelId(1), state = state, autorecRuleId = series, timerecRuleId = timer,
        enabled = enabled,
    )
}
