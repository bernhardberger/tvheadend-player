package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingList
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvSpacing48
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow
import at.bernhardberger.tvhplayer.ui.components.tabFocus

internal const val SCHEDULE_NEW_TIMER = "schedule-new-timer"
internal const val SCHEDULE_AUTOMATIC_ROW = "schedule-automatic-recordings"
internal const val SCHEDULE_AUTOMATIC_LEVEL = "schedule:automatic"

internal fun scheduleManagementKeys(canModify: Boolean, hasAutomatic: Boolean): List<String> = buildList {
    if (canModify) add(SCHEDULE_NEW_TIMER)
    if (canModify || hasAutomatic) add(SCHEDULE_AUTOMATIC_ROW)
}

internal fun scheduleManagementRows(
    canModify: Boolean,
    automatic: AutomaticRecordingList,
    empty: Boolean,
    onNewTimer: State<() -> Unit>,
): List<DepthRow> = buildList {
    if (canModify) add(DepthRow(
        DepthItem(SCHEDULE_NEW_TIMER), onActivate = { onNewTimer.value() },
    ) { modifier, activate ->
        Column {
            Column(Modifier.testTag("schedule-upcoming-heading")) {
                ArchiveLevelHeading(stringResource(R.string.schedule_upcoming), { 1f }, null)
            }
            ScheduleManagementRow(SCHEDULE_NEW_TIMER, stringResource(R.string.schedule_new_timer),
                stringResource(R.string.schedule_new_timer_help), R.drawable.ic_schedule, R.drawable.ic_add,
                modifier, activate)
            Spacer(Modifier.height(4.dp))
        }
    })
    if (canModify || automatic.items.isNotEmpty()) add(DepthRow(
        DepthItem(SCHEDULE_AUTOMATIC_ROW, SCHEDULE_AUTOMATIC_LEVEL),
    ) { modifier, activate ->
        Column {
            if (!canModify) ArchiveLevelHeading(stringResource(R.string.schedule_upcoming), { 1f }, null)
            ScheduleManagementRow(SCHEDULE_AUTOMATIC_ROW, stringResource(R.string.automatic_recording_new_overline),
                automaticRecordingCountLabel(automatic), R.drawable.ic_fiber_smart_record,
                R.drawable.ic_keyboard_arrow_right, modifier, activate)
            HorizontalDivider(Modifier.padding(vertical = TvSpacing8))
            if (empty) Column(Modifier.fillMaxWidth().padding(vertical = TvSpacing48)) {
                ModeEmptyState(R.string.recordings_schedule_empty)
            }
        }
    })
}

@Composable
internal fun ScheduleManagementRow(
    tag: String,
    title: String,
    support: String,
    leading: Int,
    trailing: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val owner = LocalTabOwner.current
    ListItem(
        selected = false,
        onClick = { if (owner?.isCurrent != false) onClick() },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(support, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(painterResource(leading), null, Modifier.size(32.dp)) },
        trailingContent = { Icon(painterResource(trailing), null) },
        modifier = modifier.tabFocus().fillMaxWidth().testTag(tag),
    )
}

@Composable
internal fun automaticRecordingCountLabel(list: AutomaticRecordingList): String {
    if (list.items.isEmpty()) return stringResource(R.string.schedule_automatic_empty_help)
    val on = pluralStringResource(R.plurals.schedule_automatic_on_count, list.onCount, list.onCount)
    return if (list.offCount == 0) on else stringResource(R.string.schedule_automatic_counts, on,
        pluralStringResource(R.plurals.schedule_automatic_off_count, list.offCount, list.offCount))
}
