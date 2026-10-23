package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.core.runningRecordings
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.depth.*
import kotlin.time.Duration

internal fun archiveLevelId(path: List<String>): String = "archive:${path.joinToString("/")}"

internal const val RecordingNowLevelId = "recording-now"

internal fun archiveLevelPath(id: String): List<String> =
    if (id.startsWith("archive:")) id.removePrefix("archive:").split('/').filter(String::isNotEmpty)
    else emptyList()

internal fun archiveLevelItems(root: DvrArchiveFolder, levelId: String): List<ArchiveListItem>? =
    when {
        levelId == RecordingNowLevelId -> root.runningRecordings().takeIf { it.isNotEmpty() }
            ?.map(ArchiveListItem::Recording)
        levelId.startsWith("archive:") -> root.folderAt(archiveLevelPath(levelId))?.listItems()
        else -> null
    }

/** Preserve every surviving parent viewport and reconcile selection by the depth owner's index policy. */
internal fun reconcileArchiveStack(stack: DepthStack, root: DvrArchiveFolder): DepthStack {
    val frames = stack.frames.takeWhile { archiveLevelItems(root, it.levelId) != null }
        .map { frame ->
            val items = requireNotNull(archiveLevelItems(root, frame.levelId))
            DepthStack(listOf(frame)).reconcile(items.map { DepthItem(it.key) }).active
        }
    return stack.copy(
        frames = frames.ifEmpty { listOf(DepthFrame(archiveLevelId(emptyList()))) },
        visit = stack.visit + if (frames.size != stack.frames.size) 1 else 0,
    )
}

/** Archive supplies content and identities; the shared Settings depth host owns navigation. */
@Composable
internal fun ArchiveDepthContent(
    root: DvrArchiveFolder,
    navigation: DepthNavigationState,
    contentPadding: PaddingValues,
    isCurrent: Boolean,
    initialFocusEnabled: Boolean,
    backEnabled: Boolean,
    contentFocus: FocusRequester,
    onModeFocus: () -> Unit,
    onDrawerFocus: (() -> Unit)?,
    onOpenRecording: (DvrEntry) -> Unit,
    piconForEntry: (DvrEntry) -> ArtworkId?,
    resumePositionForEntry: (DvrEntry) -> Duration?,
    modifier: Modifier = Modifier,
    onOpenContextMenu: ((DvrEntry) -> Unit)? = null,
) {
    val latestOnOpenRecording = rememberUpdatedState(onOpenRecording)
    val latestOnOpenContextMenu = rememberUpdatedState(onOpenContextMenu)
    val latestPiconForEntry = rememberUpdatedState(piconForEntry)
    val latestResumePositionForEntry = rememberUpdatedState(resumePositionForEntry)
    val levels = remember(root) {
        val levels = linkedMapOf<String, DepthLevel>()
        fun addLevel(id: String, heading: @Composable () -> String, items: List<ArchiveListItem>) {
            levels[id] = DepthLevel(
                id = id,
                heading = { emphasis ->
                    ArchiveLevelHeading(
                        heading(),
                        emphasis,
                    )
                },
                emptyContent = {
                    Text(stringResource(R.string.recordings_archive_empty), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                },
                rows = items.map { item ->
                    when (item) {
                        is ArchiveListItem.RecordingNow -> {
                            addLevel(RecordingNowLevelId, { stringResource(R.string.recordings_recording_now_folder) },
                                item.entries.map(ArchiveListItem::Recording))
                            DepthRow(DepthItem(item.key, RecordingNowLevelId)) { rowModifier, activate ->
                                Column {
                                    RecordingNowFolderRow(item.entries.size, rowModifier, activate)
                                    HorizontalDivider(modifier = Modifier.padding(vertical = TvSpacing8))
                                }
                            }
                        }
                        is ArchiveListItem.Folder -> DepthRow(
                            DepthItem(item.key, archiveLevelId(item.folder.path)),
                        ) { rowModifier, activate ->
                            FolderListRow(item.folder, selected = false, modifier = rowModifier, onClick = activate)
                        }
                        is ArchiveListItem.Recording -> {
                            val previewId = "metadata:${item.entry.id.value}"
                            levels[previewId] = DepthLevel(
                                id = previewId,
                                rows = emptyList(),
                                heading = { ArchiveLevelHeading("", it) },
                                passiveContent = {
                                    RecordingMetadataPane(item.entry, latestPiconForEntry.value(item.entry),
                                        latestResumePositionForEntry.value(item.entry),
                                        Modifier.padding(bottom = contentPadding.calculateBottomPadding()))
                                },
                            )
                            DepthRow(
                                DepthItem(item.key),
                                onActivate = { latestOnOpenRecording.value(item.entry) },
                                previewLevelId = previewId,
                            ) { rowModifier, activate ->
                                RecordingListRow(
                                    entry = item.entry, piconPath = latestPiconForEntry.value(item.entry),
                                    resumePosition = latestResumePositionForEntry.value(item.entry),
                                    selected = false, modifier = rowModifier,
                                    onClick = activate,
                                    onOpenContextMenu = { latestOnOpenContextMenu.value?.invoke(item.entry) },
                                )
                            }
                        }
                    }
                },
            )
        }
        fun addFolder(folder: DvrArchiveFolder) {
            addLevel(archiveLevelId(folder.path), {
                if (folder.path.isEmpty()) stringResource(R.string.recordings_archive_all) else folder.name
            }, folder.listItems())
            folder.folders.forEach(::addFolder)
        }
        addFolder(root)
        levels
    }
    DepthNavigation(
        state = navigation,
        levels = levels,
        fallbackLevel = { levels.getValue(archiveLevelId(emptyList())) },
        columnWidth = TvBrowseColumnWidth,
        columnGap = TvBrowseColumnGap,
        contentPadding = contentPadding,
        isCurrent = isCurrent,
        initialFocusEnabled = initialFocusEnabled,
        backEnabled = backEnabled,
        activeFocusRequester = contentFocus,
        onRootBack = onModeFocus,
        onRootLeft = onDrawerFocus,
        onFirstRowUp = onModeFocus,
        pageKeyDirection = at.bernhardberger.tvhplayer.core.ChannelNavigation::pageDirectionForKeyCode,
        modifier = modifier.testTag("recordings-archive-list"),
    )
}

/** Every Recordings tab's first heading uses this band, so first rows line up across tabs. */
@Composable
internal fun recordingsHeadingBandHeight(): Dp {
    val lineHeight = with(LocalDensity.current) { MaterialTheme.typography.titleLarge.lineHeight.toDp() }
    return maxOf(32.dp, lineHeight + 4.dp)
}

/** The preview-slot heading reads at the row title size: titleMedium 16sp of titleLarge 22sp. */
internal const val ArchiveHeadingPreviewScale = 16f / 22f

/**
 * Column headings are the second level under the Recordings title: titleLarge like the
 * Schedule day headings. In the preview slot they read at the row title size and grow
 * to full size as the column slides into the active slot.
 */
@Composable
private fun ArchiveLevelHeading(name: String, emphasis: () -> Float) {
    Box(Modifier.fillMaxWidth().height(recordingsHeadingBandHeight())) {
        if (name.isNotEmpty()) Text(
            name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                scaleX = lerp(ArchiveHeadingPreviewScale, 1f, emphasis())
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 1f)
            }.semantics { heading() }.testTag("archive-level-heading"),
        )
    }
}
