package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.guide.FactKey
import at.bernhardberger.tvhplayer.touch.guide.GuideActions
import at.bernhardberger.tvhplayer.touch.guide.ProgramDetailsColumn
import at.bernhardberger.tvhplayer.touch.recordings.RecordingDetailsBody
import at.bernhardberger.tvhplayer.touch.recordings.RecordingProgress
import at.bernhardberger.tvhplayer.touch.recordings.dayText
import at.bernhardberger.tvhplayer.touch.recordings.partText
import at.bernhardberger.tvhplayer.touch.recordings.recordingDay
import at.bernhardberger.tvhplayer.touch.recordings.recordingTime
import at.bernhardberger.tvhplayer.touch.ui.DetailsFacts
import at.bernhardberger.tvhplayer.touch.ui.DetailsSectionTitle
import at.bernhardberger.tvhplayer.touch.ui.joinParts
import at.bernhardberger.tvhplayer.presentation.R as SharedR

@Composable
internal fun ColumnScope.RecordingInfo(
    details: RecordingPlayerInfo,
    panel: PlayerPanel,
    info: InfoPage,
    onPanel: (PlayerPanel?) -> Unit,
    onChoice: (PlayerChoice) -> Unit,
) {
    val openAiring: (String) -> Unit = { id -> info.openDetails(id, onPanel, onChoice) }
    val rootScroll = rememberScrollState()
    val body = Modifier.weight(1f)
    if (panel == PlayerPanel.InfoDetails) {
        val airing = details.airingDetails[info.stack.shown] ?: return
        key(airing.id) {
            ProgramDetailsColumn(
                airing,
                GuideActions(
                    onWatch = null, onRecord = null, onRecordOptions = null,
                    onDay = {}, onJump = {}, onTag = {}, onShowInGuide = null, onAiring = openAiring,
                ),
                body.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                inPlayer = true,
            )
        }
    } else {
        Column(body.verticalScroll(rootScroll).padding(horizontal = 16.dp).testTag("recording-info")) {
            RecordingDetailsBody(
                details.item, recordingInfoWhenLine(details),
                afterSynopsis = { RecordingInfoSections(details, openAiring) },
                programmeFacts = details.program?.facts.orEmpty()
                    .filterNot { it.key == FactKey.Genre && details.item.details.genre != null }
                    .map { stringResource(it.key.label) to it.value },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordingInfoSections(details: RecordingPlayerInfo, openAiring: (String) -> Unit) {
    val program = details.program ?: return
    if (program.otherAirings.isNotEmpty()) {
        DetailsSectionTitle(stringResource(SharedR.string.details_other_airings, details.item.title))
        program.otherAirings.forEach { airing ->
            ListItem(
                onClick = { openAiring(airing.id) },
                supportingContent = { Text(joinParts(airing.line)) },
            ) { Text(airing.time) }
        }
    }
    DetailsFacts(
        stringResource(SharedR.string.details_cast_and_crew),
        program.credits.map { pluralStringResource(it.role.label, it.names.size) to it.names.joinToString(", ") },
        columns = 1, keyWidth = 116.dp,
    )
}

@Composable
private fun recordingInfoWhenLine(details: RecordingPlayerInfo): String {
    val clock = details.clock
    val running = details.item.state as? RecordingProgress.Recording
        ?: return joinParts(recordingInfoWhen(details.item, clock).map { partText(it) })
    val ends = recordingTime(running.until, clock)
    val start = details.item.start
    return if (start == null) {
        stringResource(SharedR.string.recording_ends_at, ends)
    } else {
        val recorded = joinParts(listOf(dayText(recordingDay(start, clock)), recordingTime(start, clock)))
        stringResource(SharedR.string.player_recorded_ends, recorded, ends)
    }
}
