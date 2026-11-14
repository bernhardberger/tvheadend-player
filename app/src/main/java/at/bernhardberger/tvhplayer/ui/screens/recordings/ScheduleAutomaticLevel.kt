package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AutomaticRecordingList
import at.bernhardberger.tvhplayer.core.AutomaticRecordingListItem
import at.bernhardberger.tvhplayer.ui.TvSpacing48
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow

internal const val SCHEDULE_NEW_AUTOMATIC = "schedule-new-automatic"

@Composable
internal fun scheduleAutomaticLevels(
    automatic: AutomaticRecordingList,
    canModify: Boolean,
    canAdd: Boolean,
    channels: List<Channel>,
    channelMap: Map<ChannelId, Channel>,
    navigation: DepthNavigationState,
    paneModifier: Modifier,
    onNew: () -> Unit,
    onEdit: (AutomaticRecordingListItem) -> Unit,
    onMenu: (AutomaticRecordingListItem) -> Unit,
): Map<String, DepthLevel> {
    val latestNew = rememberUpdatedState(onNew)
    val latestEdit = rememberUpdatedState(onEdit)
    val latestMenu = rememberUpdatedState(onMenu)
    val levels = remember(automatic, canModify, canAdd, channels, channelMap, paneModifier) {
        val levels = linkedMapOf<String, DepthLevel>()
        val rows = buildList {
            if (canModify) add(DepthRow(DepthItem(SCHEDULE_NEW_AUTOMATIC),
                onActivate = { if (canAdd) latestNew.value() }) { modifier, activate ->
                Column {
                    ScheduleManagementRow(
                        SCHEDULE_NEW_AUTOMATIC, stringResource(R.string.automatic_recording_new_title),
                        stringResource(R.string.schedule_automatic_new_help), R.drawable.ic_fiber_smart_record,
                        R.drawable.ic_add, modifier, activate, enabled = canAdd)
                    HorizontalDivider(Modifier.padding(vertical = TvSpacing8))
                    if (automatic.items.isEmpty()) Column(Modifier.padding(vertical = TvSpacing48)) {
                        ModeEmptyState(R.string.schedule_automatic_empty)
                    }
                }
            })
            automatic.items.forEach { item ->
                val previewId = "$SCHEDULE_AUTOMATIC_LEVEL:${item.rowId()}"
                levels[previewId] = DepthLevel(previewId, emptyList(),
                    heading = { ArchiveLevelHeading("", it, null, atRoot = false) },
                    passiveContent = { ScheduleRulePane(item, channels, paneModifier) })
                add(DepthRow(DepthItem(item.rowId()), onActivate = { if (canModify) latestEdit.value(item) },
                    previewLevelId = previewId) { modifier, activate ->
                    Column {
                        ScheduleRuleRow(item, channels, channelMap, modifier, activate,
                            onMenu = if (canModify) ({ latestMenu.value(item) }) else null)
                        Spacer(Modifier.height(4.dp))
                    }
                })
            }
        }
        levels[SCHEDULE_AUTOMATIC_LEVEL] = DepthLevel(
            SCHEDULE_AUTOMATIC_LEVEL, rows,
            heading = {
                ArchiveLevelHeading(stringResource(R.string.automatic_recording_new_overline), it, null, atRoot = false)
            },
            emptyContent = { ModeEmptyState(R.string.schedule_automatic_empty) },
        )
        levels
    }
    SideEffect {
        if (!canModify && automatic.items.isEmpty() && navigation.stack.active.levelId == SCHEDULE_AUTOMATIC_LEVEL) {
            navigation.pop()
        }
    }
    return levels
}
