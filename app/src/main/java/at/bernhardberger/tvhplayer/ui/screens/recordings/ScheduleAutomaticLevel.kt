package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingList
import at.bernhardberger.tvhplayer.core.AutomaticRecordingListItem
import at.bernhardberger.tvhplayer.ui.TvSpacing48
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.DepthFrame
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow

internal const val SCHEDULE_NEW_AUTOMATIC = "schedule-new-automatic"

@Composable
internal fun scheduleAutomaticLevels(
    automatic: AutomaticRecordingList,
    canModify: Boolean,
    channels: List<Channel>,
    navigation: DepthNavigationState,
    paneModifier: Modifier,
    onNew: () -> Unit,
    onEdit: (AutomaticRecordingListItem) -> Unit,
    onMenu: (AutomaticRecordingListItem) -> Unit,
): Map<String, DepthLevel> {
    val levels = linkedMapOf<String, DepthLevel>()
    val rows = buildList {
        if (canModify) add(DepthRow(DepthItem(SCHEDULE_NEW_AUTOMATIC), onActivate = onNew) { modifier, activate ->
            Column {
                ScheduleManagementRow(SCHEDULE_NEW_AUTOMATIC, stringResource(R.string.automatic_recording_new_title),
                    stringResource(R.string.schedule_automatic_new_help), R.drawable.ic_fiber_smart_record,
                    R.drawable.ic_add, modifier, activate)
                HorizontalDivider(Modifier.padding(vertical = TvSpacing8))
                if (automatic.items.isEmpty()) Column(Modifier.padding(vertical = TvSpacing48)) {
                    ModeEmptyState(R.string.schedule_automatic_empty)
                }
            }
        })
        automatic.items.forEach { item ->
            val previewId = "$SCHEDULE_AUTOMATIC_LEVEL:${item.rowId()}"
            levels[previewId] = DepthLevel(previewId, emptyList(),
                heading = { ArchiveLevelHeading("", it, null) },
                passiveContent = { ScheduleRulePane(item, channels, paneModifier) })
            add(DepthRow(DepthItem(item.rowId()), onActivate = { if (canModify) onEdit(item) },
                previewLevelId = previewId) { modifier, activate ->
                Column {
                    ScheduleRuleRow(item, channels.associateBy { it.id }, modifier, activate,
                        onMenu = if (canModify) ({ onMenu(item) }) else null)
                    Spacer(Modifier.height(4.dp))
                }
            })
        }
    }
    val stack = navigation.stack
    val needsInitialFrame = automatic.available && SCHEDULE_AUTOMATIC_LEVEL !in stack.remembered &&
        stack.frames.none { it.levelId == SCHEDULE_AUTOMATIC_LEVEL }
    SideEffect {
        if (needsInitialFrame && navigation.stack == stack) {
            val first = automatic.items.firstOrNull()?.rowId() ?: rows.firstOrNull()?.item?.id
            navigation.update(stack.copy(remembered = stack.remembered + (SCHEDULE_AUTOMATIC_LEVEL to
                DepthFrame(SCHEDULE_AUTOMATIC_LEVEL, first, if (canModify && automatic.items.isNotEmpty()) 1 else 0,
                    firstVisibleItemId = rows.firstOrNull()?.item?.id))))
        }
    }
    levels[SCHEDULE_AUTOMATIC_LEVEL] = DepthLevel(
        SCHEDULE_AUTOMATIC_LEVEL, rows,
        heading = { ArchiveLevelHeading(stringResource(R.string.automatic_recording_new_overline), it, null) },
        emptyContent = { ModeEmptyState(R.string.schedule_automatic_empty) },
    )
    return levels
}
