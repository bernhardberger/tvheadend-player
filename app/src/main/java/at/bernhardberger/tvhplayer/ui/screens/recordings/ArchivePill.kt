package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.screens.BrowseLevelsPanel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry

/**
 * The pills at the end of the Archive header, in focus order.
 * Group, Sort and Show open a panel; Schedule opens its screen.
 */
internal enum class ArchivePill { GROUP, SORT, SHOW, SCHEDULE }

internal fun DvrArchiveGrouping.labelRes(): Int = when (this) {
    DvrArchiveGrouping.FLAT -> R.string.recordings_grouping_flat
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres
}

private fun DvrArchiveGrouping.detailRes(): Int = when (this) {
    DvrArchiveGrouping.FLAT -> R.string.recordings_grouping_none_detail
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders_detail
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels_detail
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres_detail
}

private fun DvrArchiveSort.labelRes(): Int = when (this) {
    DvrArchiveSort.NEWEST -> R.string.recordings_sort_newest
    DvrArchiveSort.NAME -> R.string.recordings_sort_name
    DvrArchiveSort.LARGEST -> R.string.recordings_sort_largest
}

private fun DvrArchiveShow.labelRes(): Int = when (this) {
    DvrArchiveShow.ALL -> R.string.recordings_show_option_all
    DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched
    DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched
    DvrArchiveShow.WATCHED -> R.string.recordings_show_watched
}

/** The plain-language line under a Show choice; All needs none. */
private fun DvrArchiveShow.detailRes(): Int? = when (this) {
    DvrArchiveShow.ALL -> null
    DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched_detail
    DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched_detail
    DvrArchiveShow.WATCHED -> R.string.recordings_show_watched_detail
}

/**
 * Group, Sort and Show, each naming its current value and filled while it is not the default, then Schedule
 * after a wider gap, in the Channels header's buttons. Like that header, Up stays, Down goes to the list
 * ([onDown]), Left from the first pill leaves for the navigation rail ([navigationFocus]) and Right stops at
 * Schedule. Schedule carries a count badge while [problems] has entries. Back steps a folder up
 * ([onBack]); from the root it is left to the screen.
 */
@Composable
internal fun ArchivePillRow(
    view: DvrArchiveView,
    problems: Int,
    showView: Boolean,
    focusRequesters: Map<ArchivePill, FocusRequester>,
    navigationFocus: FocusRequester?,
    canStepBack: Boolean,
    onOpen: (ArchivePill) -> Unit,
    onDown: () -> Unit,
    onBack: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = hasFocus && canStepBack, onBack = onBack)
    val rail = navigationFocus ?: FocusRequester.Default
    val firstPill = if (showView) ArchivePill.GROUP else ArchivePill.SCHEDULE
    Row(
        modifier
            .onFocusChanged {
                hasFocus = it.hasFocus
                onFocusChanged(it.hasFocus)
            }
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onDown().let { true }
            }
            .testTag("recordings-pills"),
        horizontalArrangement = Arrangement.spacedBy(TvGridGutter * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fun pillModifier(pill: ArchivePill) = Modifier
            .focusProperties {
                up = FocusRequester.Cancel
                if (pill == firstPill) left = rail
                if (pill == ArchivePill.SCHEDULE) right = FocusRequester.Cancel
            }
            .testTag("recordings-pill-${pill.name.lowercase()}")
        if (showView) ArchiveViewPills(view, focusRequesters, onOpen, ::pillModifier)
        val schedule = stringResource(R.string.recordings_schedule)
        RoundIconButton(
            icon = painterResource(R.drawable.ic_event),
            label = schedule,
            value = schedule,
            active = false,
            badge = problems.takeIf { it > 0 },
            description = if (problems > 0) {
                pluralStringResource(R.plurals.recordings_schedule_problems_description, problems, problems)
            } else null,
            onClick = { onOpen(ArchivePill.SCHEDULE) },
            focusRequester = focusRequesters.getValue(ArchivePill.SCHEDULE),
            onFocused = {},
            modifier = pillModifier(ArchivePill.SCHEDULE),
        )
    }
}

@Composable
private fun ArchiveViewPills(
    view: DvrArchiveView,
    focusRequesters: Map<ArchivePill, FocusRequester>,
    onOpen: (ArchivePill) -> Unit,
    pillModifier: (ArchivePill) -> Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TvSpacing16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = painterResource(R.drawable.ic_account_tree),
            label = stringResource(R.string.recordings_grouping_group_by),
            value = stringResource(view.grouping.labelRes()),
            active = view.grouping != DvrArchiveView().grouping,
            onClick = { onOpen(ArchivePill.GROUP) },
            focusRequester = focusRequesters.getValue(ArchivePill.GROUP),
            onFocused = {},
            modifier = pillModifier(ArchivePill.GROUP),
        )
        RoundIconButton(
            icon = painterResource(R.drawable.ic_swap_vert),
            label = stringResource(R.string.recordings_sort_title),
            value = stringResource(view.sort.labelRes()),
            active = view.sort != DvrArchiveView().sort,
            onClick = { onOpen(ArchivePill.SORT) },
            focusRequester = focusRequesters.getValue(ArchivePill.SORT),
            onFocused = {},
            modifier = pillModifier(ArchivePill.SORT),
        )
        RoundIconButton(
            icon = painterResource(R.drawable.ic_visibility),
            label = stringResource(R.string.recordings_show_title),
            value = stringResource(view.show.labelRes()),
            active = view.show != DvrArchiveView().show,
            onClick = { onOpen(ArchivePill.SHOW) },
            focusRequester = focusRequesters.getValue(ArchivePill.SHOW),
            onFocused = {},
            modifier = pillModifier(ArchivePill.SHOW),
        )
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
    val title = when (show) {
        DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_empty_started
        DvrArchiveShow.WATCHED -> R.string.recordings_empty_watched
        else -> if (atRoot) R.string.recordings_empty_not_watched_root else R.string.recordings_empty_not_watched_folder
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
 * The panel a pill opens, straight at its own choice. OK on a row picks it and closes the panel;
 * Back and Left from its root close it unchanged.
 */
@Composable
internal fun ArchivePillPanel(
    pill: ArchivePill,
    current: DvrArchiveView,
    onChoose: (DvrArchiveView) -> Unit,
    onClose: () -> Unit,
) {
    val pick = { next: DvrArchiveView -> onChoose(next); onClose() }
    when (pill) {
        ArchivePill.GROUP -> BrowseLevelsPanel(
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
        ArchivePill.SORT -> BrowseLevelsPanel(
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
        ArchivePill.SHOW -> BrowseLevelsPanel(
            stringResource(R.string.recordings_show_title), "recordings-show-panel", onClose,
            DvrArchiveShow.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-show-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.show,
                    onClick = { pick(current.copy(show = option)) },
                    supporting = option.detailRes()?.let { stringResource(it) },
                )
            },
            radio = true,
        )
        ArchivePill.SCHEDULE -> Unit
    }
}
