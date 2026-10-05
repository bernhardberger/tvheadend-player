package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveFolder
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.components.depth.*
import coil3.ImageLoader

internal fun archiveLevelId(path: List<String>): String = "archive:${path.joinToString("/")}"

internal fun archiveLevelPath(id: String): List<String> =
    id.removePrefix("archive:").split('/').filter(String::isNotEmpty)

/** Preserve every surviving parent viewport and reconcile selection by the depth owner's index policy. */
internal fun reconcileArchiveStack(stack: DepthStack, root: DvrArchiveFolder): DepthStack {
    val frames = stack.frames.takeWhile { root.folderAt(archiveLevelPath(it.levelId)) != null }
        .map { frame ->
            val items = requireNotNull(root.folderAt(archiveLevelPath(frame.levelId))).listItems()
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
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    piconForEntry: (DvrEntry) -> ArtworkId?,
    modifier: Modifier = Modifier,
) {
    val latestOnOpenRecording = rememberUpdatedState(onOpenRecording)
    val latestPiconForEntry = rememberUpdatedState(piconForEntry)
    val levels = remember(root, imageLoader, currentSession) {
        val levels = linkedMapOf<String, DepthLevel>()
        fun addFolder(folder: DvrArchiveFolder) {
            val id = archiveLevelId(folder.path)
            levels[id] = DepthLevel(
                id = id,
                heading = { ArchiveLevelHeading(folder.name) },
                emptyContent = {
                    Text(stringResource(R.string.recordings_archive_empty), style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface)
                },
                rows = folder.listItems().map { item ->
                    when (item) {
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
                                heading = { ArchiveLevelHeading("") },
                                passiveContent = {
                                    RecordingMetadataPane(item.entry, latestPiconForEntry.value(item.entry), imageLoader, currentSession)
                                },
                            )
                            DepthRow(
                                DepthItem(item.key),
                                onActivate = { latestOnOpenRecording.value(item.entry) },
                                previewLevelId = previewId,
                            ) { rowModifier, activate ->
                                RecordingListRow(
                                    entry = item.entry, piconPath = latestPiconForEntry.value(item.entry), imageLoader = imageLoader,
                                    currentSession = currentSession, selected = false, modifier = rowModifier,
                                    onClick = activate,
                                )
                            }
                        }
                    }
                },
            )
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

@Composable
private fun ArchiveLevelHeading(name: String) {
    val style = MaterialTheme.typography.titleMedium
    val lineHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Box(Modifier.fillMaxWidth().height(maxOf(32.dp, lineHeight + 8.dp))) {
        if (name.isNotEmpty()) Text(
            name, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("archive-level-heading"),
        )
    }
}
