package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.screens.BrowseLevelsPanel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry

/** The three chips above the Archive list, in focus order. */
internal enum class ArchiveChip { GROUP, SORT, SHOW }

internal fun DvrArchiveGrouping.labelRes(): Int = when (this) {
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres
    DvrArchiveGrouping.NONE -> R.string.recordings_grouping_none
}

private fun DvrArchiveGrouping.detailRes(): Int = when (this) {
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders_detail
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels_detail
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres_detail
    DvrArchiveGrouping.NONE -> R.string.recordings_grouping_none_detail
}

private fun DvrArchiveSort.labelRes(): Int = when (this) {
    DvrArchiveSort.NEWEST -> R.string.recordings_sort_newest
    DvrArchiveSort.NAME -> R.string.recordings_sort_name
    DvrArchiveSort.LARGEST -> R.string.recordings_sort_largest
}

/** What the Show chip reads. */
private fun DvrArchiveShow.chipRes(): Int = when (this) {
    DvrArchiveShow.ALL -> R.string.recordings_show_all
    DvrArchiveShow.NOT_WATCHED -> R.string.recordings_show_not_watched
    DvrArchiveShow.STARTED -> R.string.recordings_show_started
}

/** The choice rows of the Show panel: the label, then the plain-language line under it. */
private fun DvrArchiveShow.optionRes(): Pair<Int, Int> = when (this) {
    DvrArchiveShow.ALL -> R.string.recordings_show_option_all to R.string.recordings_show_all_detail
    DvrArchiveShow.NOT_WATCHED -> R.string.recordings_show_not_watched to R.string.recordings_show_not_watched_detail
    DvrArchiveShow.STARTED -> R.string.recordings_show_started to R.string.recordings_show_started_detail
}

/**
 * Group by, Sort and Show, each naming its current value. Every chip opens its own choice panel; the
 * row decides every direction itself so no neighbour's key handling can move or activate through it:
 * [onUp], [onDown] and [onLeft] (from the first chip) move focus on the key's release, a held repeat
 * does nothing, and Right stops at the last chip. Only Show takes the selected look, while it narrows
 * the list. Back is [onBack].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ArchiveChipRow(
    view: DvrArchiveView,
    focusRequesters: Map<ArchiveChip, FocusRequester>,
    onOpen: (ArchiveChip) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: (() -> Unit)?,
    onBack: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    var firstFocused by remember { mutableStateOf(false) }
    // Left leaves to the drawer only when its press began on the first item, not when it moved focus there.
    var leftFromFirst by remember { mutableStateOf(false) }
    BackHandler(enabled = hasFocus, onBack = onBack)
    Row(
        modifier
            .onFocusChanged {
                hasFocus = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .onPreviewKeyEvent { event ->
                val move: (() -> Unit)? = when (event.key) {
                    Key.DirectionUp -> onUp
                    Key.DirectionDown -> onDown
                    Key.DirectionLeft -> {
                        if (event.type == KeyEventType.KeyDown) leftFromFirst = firstFocused
                        if (!leftFromFirst) return@onPreviewKeyEvent false
                        onLeft ?: {}
                    }
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyUp && !event.nativeKeyEvent.isCanceled) move?.invoke()
                true
            }
            .testTag("recordings-chips"),
        // Wide enough that a focused chip, which grows around its centre, never reaches its neighbour.
        horizontalArrangement = Arrangement.spacedBy(TvSpacing16),
    ) {
        ArchiveChip.entries.forEach { chip ->
            ArchiveFilterChip(chip, view, focusRequesters.getValue(chip), onOpen) { focused ->
                if (chip == ArchiveChip.GROUP) firstFocused = focused
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArchiveFilterChip(
    chip: ArchiveChip,
    view: DvrArchiveView,
    focusRequester: FocusRequester,
    onOpen: (ArchiveChip) -> Unit,
    onFocused: (Boolean) -> Unit,
) {
    val title = stringResource(
        when (chip) {
            ArchiveChip.GROUP -> R.string.recordings_grouping_group_by
            ArchiveChip.SORT -> R.string.recordings_sort_title
            ArchiveChip.SHOW -> R.string.recordings_show_title
        },
    )
    val value = stringResource(
        when (chip) {
            ArchiveChip.GROUP -> view.grouping.labelRes()
            ArchiveChip.SORT -> view.sort.labelRes()
            ArchiveChip.SHOW -> view.show.chipRes()
        },
    )
    FilterChip(
        selected = chip == ArchiveChip.SHOW && view.show != DvrArchiveShow.ALL,
        onClick = { onOpen(chip) },
        trailingIcon = {
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, Modifier.size(20.dp))
        },
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(
                if (chip == ArchiveChip.entries.last()) Modifier.focusProperties { right = FocusRequester.Cancel }
                else Modifier,
            )
            .onFocusChanged { onFocused(it.isFocused) }
            .semantics { contentDescription = "$title: $value" }
            .testTag("recordings-chip-${chip.name.lowercase()}"),
    ) {
        Text(value)
    }
}

/**
 * The Archive level with nothing to list: the plain empty line, or, while Show narrows the list, what the
 * filter hides here ([hidden] recordings at any depth below).
 */
@Composable
internal fun ArchiveEmptyLevel(show: DvrArchiveShow, atRoot: Boolean, hidden: Int) {
    if (show == DvrArchiveShow.ALL || hidden <= 0) {
        Text(
            stringResource(R.string.recordings_archive_empty),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        return
    }
    val title = when {
        show == DvrArchiveShow.STARTED -> R.string.recordings_empty_started
        atRoot -> R.string.recordings_empty_not_watched_root
        else -> R.string.recordings_empty_not_watched_folder
    }
    Column(verticalArrangement = Arrangement.spacedBy(TvSpacing8)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            pluralStringResource(R.plurals.recordings_hidden_count, hidden, hidden),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The panel a chip opens, straight at its own choice. OK on a row picks it and closes the panel;
 * Back closes it unchanged.
 */
@Composable
internal fun ArchiveChipPanel(
    chip: ArchiveChip,
    current: DvrArchiveView,
    onChoose: (DvrArchiveView) -> Unit,
    onClose: () -> Unit,
) {
    val pick = { next: DvrArchiveView -> onChoose(next); onClose() }
    when (chip) {
        ArchiveChip.GROUP -> BrowseLevelsPanel(
            stringResource(R.string.recordings_grouping_group_by), "recordings-group-panel", onClose,
            DvrArchiveGrouping.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-grouping-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.grouping,
                    onClick = { pick(current.copy(grouping = option)) },
                    supporting = stringResource(option.detailRes()),
                )
            },
            radio = true,
        )
        ArchiveChip.SORT -> BrowseLevelsPanel(
            stringResource(R.string.recordings_sort_title), "recordings-sort-panel", onClose,
            DvrArchiveSort.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-sort-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.sort,
                    onClick = { pick(current.copy(sort = option)) },
                )
            },
            radio = true,
        )
        ArchiveChip.SHOW -> BrowseLevelsPanel(
            stringResource(R.string.recordings_show_title), "recordings-show-panel", onClose,
            DvrArchiveShow.entries.map { option ->
                val (label, detail) = option.optionRes()
                BrowsePanelEntry.Choice(
                    key = "recordings-show-option-${option.name.lowercase()}",
                    label = stringResource(label),
                    selected = option == current.show,
                    onClick = { pick(current.copy(show = option)) },
                    supporting = stringResource(detail),
                )
            },
            radio = true,
        )
    }
}
