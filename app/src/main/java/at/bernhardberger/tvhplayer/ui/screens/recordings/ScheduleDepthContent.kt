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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.DvrScheduleSection
import at.bernhardberger.tvhplayer.core.offeredResumePosition
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
    BoxWithConstraints(modifier) {
        val paneWidth = (maxWidth - contentPadding.calculateStartPadding(layoutDirection) -
            TvBrowseColumnWidth - TvBrowseColumnGap).coerceIn(0.dp, TvBrowseColumnWidth)
        val levels = rememberScheduleLevels(
            groups, contentPadding, paneWidth, onOpenRecording, onOpenContextMenu,
            piconForEntry, admissionForEntry, ruleForEntry,
        )
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
    contentPadding: PaddingValues,
    paneWidth: Dp,
    onOpenRecording: (DvrEntry) -> Unit,
    onOpenContextMenu: (DvrEntry) -> Unit,
    piconForEntry: (DvrEntry) -> ChannelIcon?,
    admissionForEntry: (DvrEntry) -> RecordingPlaybackAdmission?,
    ruleForEntry: (DvrEntry) -> RecordingRule?,
): Map<String, DepthLevel> {
    val latestOpen = rememberUpdatedState(onOpenRecording)
    val latestMenu = rememberUpdatedState(onOpenContextMenu)
    val latestPicon = rememberUpdatedState(piconForEntry)
    val latestAdmission = rememberUpdatedState(admissionForEntry)
    val latestRule = rememberUpdatedState(ruleForEntry)
    return remember(groups, contentPadding, paneWidth) {
        val levels = linkedMapOf<String, DepthLevel>()
        val rows = groups.flatMapIndexed { sectionIndex, section ->
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
            emptyContent = { ModeEmptyState(R.string.recordings_schedule_empty) },
        )
        levels
    }
}
