package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.AutomaticRecordingList
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.core.AutomaticRecordingListItem
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.components.RecordingRule
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigation
import at.bernhardberger.tvhplayer.ui.components.depth.DepthNavigationState
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow

internal const val SCHEDULE_ROOT_LEVEL = "schedule:"

/** Schedule supplies rows and passive recording panes; the shared depth host owns their navigation. */
@Composable
internal fun ScheduleDepthContent(
    groups: List<DvrScheduleSection>,
    automatic: AutomaticRecordingList,
    canModify: Boolean,
    canAdd: Boolean,
    onNewTimer: () -> Unit,
    channels: Map<ChannelId, Channel>,
    onNewAutomatic: () -> Unit,
    onEditRule: (AutomaticRecordingListItem) -> Unit,
    onRuleMenu: (AutomaticRecordingListItem) -> Unit,
    navigation: DepthNavigationState,
    contentPadding: PaddingValues,
    isCurrent: Boolean,
    initialFocusEnabled: Boolean,
    backEnabled: Boolean,
    contentFocus: FocusRequester,
    onDrawerFocus: (() -> Unit)?,
    onFirstRowUp: () -> Unit,
    onOpenRecording: (DvrEntry) -> Unit,
    onOpenContextMenu: (DvrEntry) -> Unit,
    piconForEntry: (DvrEntry) -> ChannelIcon?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    ruleForEntry: (DvrEntry) -> RecordingRule?,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val channelList = remember(channels) { channels.values.toList() }
    BoxWithConstraints(modifier) {
        val paneWidth = (maxWidth - contentPadding.calculateStartPadding(layoutDirection) -
            TvBrowseColumnWidth - TvBrowseColumnGap).coerceIn(0.dp, TvBrowseColumnWidth)
        val levels = rememberScheduleLevels(
            groups, automatic, canModify, canAdd, onNewTimer,
            navigation.stack.frames.first().focusedItemId == null,
            contentPadding, paneWidth, onOpenRecording, onOpenContextMenu,
            piconForEntry, admissionForEntry, ruleForEntry,
        ) + scheduleAutomaticLevels(
            automatic, canModify && automatic.available, canAdd, channelList, channels, navigation,
            Modifier.widthIn(max = paneWidth), onNewAutomatic, onEditRule, onRuleMenu)
        DepthNavigation(
            state = navigation,
            levels = levels,
            fallbackLevel = { levels.getValue(SCHEDULE_ROOT_LEVEL) },
            columnWidth = TvBrowseColumnWidth,
            columnGap = TvBrowseColumnGap,
            contentPadding = contentPadding,
            isCurrent = isCurrent,
            initialFocusEnabled = initialFocusEnabled,
            backEnabled = backEnabled,
            activeFocusRequester = contentFocus,
            onRootLeft = onDrawerFocus,
            onFirstRowUp = onFirstRowUp,
            pageKeyDirection = ChannelNavigation::pageDirectionForKeyCode,
            modifier = Modifier.testTag("recordings-schedule-list"),
        )
    }
}

@Composable
private fun rememberScheduleLevels(
    groups: List<DvrScheduleSection>,
    automatic: AutomaticRecordingList,
    canModify: Boolean,
    canAdd: Boolean,
    onNewTimer: () -> Unit,
    initial: Boolean,
    contentPadding: PaddingValues,
    paneWidth: Dp,
    onOpenRecording: (DvrEntry) -> Unit,
    onOpenContextMenu: (DvrEntry) -> Unit,
    piconForEntry: (DvrEntry) -> ChannelIcon?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    ruleForEntry: (DvrEntry) -> RecordingRule?,
): Map<String, DepthLevel> {
    val latestOpen = rememberUpdatedState(onOpenRecording)
    val latestNewTimer = rememberUpdatedState(onNewTimer)
    val latestMenu = rememberUpdatedState(onOpenContextMenu)
    val latestPicon = rememberUpdatedState(piconForEntry)
    val latestAdmission = rememberUpdatedState(admissionForEntry)
    val latestRule = rememberUpdatedState(ruleForEntry)
    return remember(groups, automatic, canModify, canAdd, initial, contentPadding, paneWidth) {
        val levels = linkedMapOf<String, DepthLevel>()
        val rows = scheduleManagementRows(canModify, canAdd, automatic, groups.isEmpty(), latestNewTimer) +
            groups.flatMapIndexed { sectionIndex, section ->
            section.entries.mapIndexed { entryIndex, entry ->
                val key = "recording:${recordingItemKey(entry.id)}"
                val previewId = "$SCHEDULE_ROOT_LEVEL$key"
                levels[previewId] = recordingPreviewLevel(
                    previewId, entry, contentPadding, latestPicon, latestAdmission,
                    kind = RecordingRowKind.SCHEDULE, latestRuleForEntry = latestRule,
                    paneModifier = Modifier.widthIn(max = paneWidth),
                )
                DepthRow(
                    DepthItem(key),
                    onActivate = { latestOpen.value(entry) },
                    previewLevelId = previewId,
                ) { rowModifier, activate ->
                    Column {
                        if (entryIndex == 0) {
                            if (!canModify && automatic.items.isEmpty() && sectionIndex == 0) {
                                ArchiveLevelHeading(
                                    stringResource(R.string.schedule_upcoming), { 1f }, null, atRoot = false)
                            }
                            RecordingSectionHeader(scheduleSectionLabel(section), first = sectionIndex == 0)
                            if (sectionIndex != 0) Spacer(Modifier.height(4.dp))
                        }
                        RecordingListRow(
                            entry = entry,
                            piconPath = latestPicon.value(entry),
                            resumePosition = offeredResumePosition(latestAdmission.value(entry)),
                            selected = false,
                            modifier = rowModifier,
                            onClick = activate,
                            onOpenContextMenu = { latestMenu.value(entry) },
                            recordingRule = latestRule.value(entry),
                            kind = RecordingRowKind.SCHEDULE,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
        levels[SCHEDULE_ROOT_LEVEL] = DepthLevel(
            id = SCHEDULE_ROOT_LEVEL,
            rows = rows,
            heading = {},
            initialItemId = groups.firstOrNull()?.entries?.firstOrNull()?.takeIf { initial }
                ?.let { "recording:${recordingItemKey(it.id)}" },
            emptyContent = { ModeEmptyState(R.string.recordings_schedule_empty) },
        )
        levels
    }
}
