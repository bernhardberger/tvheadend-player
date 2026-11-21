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

/** Which control the touch screens offer for the shared DVR actions; Skip and Unskip wait for later. */
class DvrControlsTest {
    @Test
    fun theRecordKeyActionBecomesRecordCancelOrStop() {
        val offered = mapOf(
            RECORD to ProgramRecordControl.Record, CANCEL to ProgramRecordControl.Cancel, STOP to ProgramRecordControl.Stop,
        )
        (BrowseContextAction.entries + null).forEach {
            assertEquals(it.toString(), offered[it], programRecordControl(it))
        }
    }

    @Test
    fun aRecordingsContextActionsGiveItsRemoveControl() {
        listOf(
            listOf(PLAY, RESUME, DELETE) to RecordingRemoveControl.Delete,
            listOf(REMOVE_ENTRY) to RecordingRemoveControl.RemoveEntry,
            listOf(STOP) to RecordingRemoveControl.Stop,
            listOf(CANCEL) to null,
            listOf(SKIP, UNSKIP) to null,
            emptyList<BrowseContextAction>() to null,
        ).forEach { (actions, control) -> assertEquals(actions.toString(), control, recordingRemoveControl(actions)) }
    }

    @Test
    fun ruleSchedulesShowWhetherTheEpisodeWillRecord() {
        val series = AutorecRuleId("series")
        val timer = TimerecRuleId("timer")
        listOf(
            entry(DvrEntryState.SCHEDULED, series) to RuleSchedule.Scheduled,
            entry(DvrEntryState.SCHEDULED, timer = timer) to RuleSchedule.Scheduled,
            entry(DvrEntryState.SCHEDULED) to null,
            entry(DvrEntryState.SCHEDULED, series, enabled = false) to RuleSchedule.Skipped,
            entry(DvrEntryState.RECORDING, series) to null,
            null to null,
        ).forEach { (entry, schedule) -> assertEquals(entry.toString(), schedule, ruleSchedule(entry)) }
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
