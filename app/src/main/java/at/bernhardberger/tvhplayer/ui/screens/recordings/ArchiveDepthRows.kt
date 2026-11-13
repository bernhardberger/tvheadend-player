package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvhplayer.core.DvrDateSection
import at.bernhardberger.tvhplayer.ui.components.RecordingRule
import at.bernhardberger.tvhplayer.core.offeredResumePosition
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.DepthItem
import at.bernhardberger.tvhplayer.ui.components.depth.DepthLevel
import at.bernhardberger.tvhplayer.ui.components.depth.DepthRow

internal fun recordingNowRow(item: ArchiveListItem.RecordingNow) = DepthRow(
    DepthItem(item.key, RECORDING_NOW_LEVEL_ID),
) { rowModifier, activate ->
    Column {
        RecordingNowFolderRow(item.entries.size, rowModifier, activate)
        HorizontalDivider(Modifier.padding(vertical = TvSpacing8))
    }
}

internal fun recordingPreviewLevel(
    id: String,
    entry: DvrEntry,
    contentPadding: PaddingValues,
    latestPiconForEntry: State<(DvrEntry) -> ChannelIcon?>,
    latestAdmissionForEntry: State<(DvrEntry) -> RecordingPlaybackAdmission?>,
    kind: RecordingRowKind = RecordingRowKind.ARCHIVE,
    latestRuleForEntry: State<(DvrEntry) -> RecordingRule?>? = null,
    paneModifier: Modifier = Modifier,
) = DepthLevel(
    id = id,
    rows = emptyList(),
    heading = { ArchiveLevelHeading("", it, valuesLine = null) },
    passiveContent = {
        RecordingMetadataPane(entry, latestPiconForEntry.value(entry), latestAdmissionForEntry.value(entry),
            paneModifier.padding(bottom = contentPadding.calculateBottomPadding()), kind = kind,
            recordingRule = latestRuleForEntry?.value?.invoke(entry))
    },
)

/** A recording row, led by its date cut when [section] names one ([first]: the list's first row). */
internal fun archiveRecordingRow(
    item: ArchiveListItem.Recording,
    previewId: String,
    section: DvrDateSection?,
    first: Boolean,
    latestPiconForEntry: State<(DvrEntry) -> ChannelIcon?>,
    latestAdmissionForEntry: State<(DvrEntry) -> RecordingPlaybackAdmission?>,
    latestOnOpenRecording: State<(DvrEntry) -> Unit>,
    latestOnOpenContextMenu: State<((DvrEntry) -> Unit)?>,
) = DepthRow(
    DepthItem(item.key),
    onActivate = { latestOnOpenRecording.value(item.entry) },
    previewLevelId = previewId,
) { rowModifier, activate ->
    Column {
        if (section != null) ArchiveDateDivider(dateSectionLabel(section), first = first)
        RecordingListRow(
            entry = item.entry, piconPath = latestPiconForEntry.value(item.entry),
            resumePosition = offeredResumePosition(latestAdmissionForEntry.value(item.entry)),
            selected = false, modifier = rowModifier,
            onClick = activate,
            onOpenContextMenu = { latestOnOpenContextMenu.value?.invoke(item.entry) },
        )
    }
}
