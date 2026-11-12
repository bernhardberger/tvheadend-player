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
import at.bernhardberger.tvhplayer.ui.TvSpacing16
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.screens.BrowseLevelsPanel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry

/** The pills at the end of the Archive header, in focus order; each opens its panel. */
internal enum class ArchivePill { GROUP, SORT, SHOW }

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

/** The full name of a sort, as the panel lists it and the pill is spoken. */
private fun DvrArchiveSort.labelRes(): Int = when (this) {
    DvrArchiveSort.NEWEST -> R.string.recordings_sort_newest
    DvrArchiveSort.OLDEST -> R.string.recordings_sort_oldest
    DvrArchiveSort.NAME -> R.string.recordings_sort_name
    DvrArchiveSort.NAME_DESCENDING -> R.string.recordings_sort_name_descending
    DvrArchiveSort.LARGEST -> R.string.recordings_sort_largest
    DvrArchiveSort.SMALLEST -> R.string.recordings_sort_smallest
}

/** The one word the pill shows, with [Pair.second] the arrow giving the direction: down for newest, Z to A, largest. */
private fun DvrArchiveSort.pillRes(): Pair<Int, Int> = when (this) {
    DvrArchiveSort.NEWEST -> R.string.recordings_sort_field_date to R.drawable.ic_arrow_downward
    DvrArchiveSort.OLDEST -> R.string.recordings_sort_field_date to R.drawable.ic_arrow_upward
    DvrArchiveSort.NAME -> R.string.recordings_sort_field_name to R.drawable.ic_arrow_upward
    DvrArchiveSort.NAME_DESCENDING -> R.string.recordings_sort_field_name to R.drawable.ic_arrow_downward
    DvrArchiveSort.LARGEST -> R.string.recordings_sort_field_size to R.drawable.ic_arrow_downward
    DvrArchiveSort.SMALLEST -> R.string.recordings_sort_field_size to R.drawable.ic_arrow_upward
}

private fun DvrArchiveShow.labelRes(): Int = when (this) {
    DvrArchiveShow.ALL -> R.string.recordings_show_option_all
    DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched
    DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched
    DvrArchiveShow.WATCHED -> R.string.recordings_show_watched
}

/** The one word the pill shows. */
private fun DvrArchiveShow.pillLabelRes(): Int = when (this) {
    DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partial
    else -> labelRes()
}

/** The mark a row of that kind carries, so the pill and the rows speak the same symbols. */
private fun DvrArchiveShow.iconRes(): Int = when (this) {
    DvrArchiveShow.ALL -> R.drawable.ic_visibility
    DvrArchiveShow.UNWATCHED -> R.drawable.ic_radio_button_unchecked
    DvrArchiveShow.PARTIALLY_WATCHED -> R.drawable.ic_clock_loader_40_outlined
    DvrArchiveShow.WATCHED -> R.drawable.ic_check_circle_outlined
}

/** The plain-language line under a Show choice; All needs none. */
private fun DvrArchiveShow.detailRes(): Int? = when (this) {
    DvrArchiveShow.ALL -> null
    DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched_detail
    DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched_detail
    DvrArchiveShow.WATCHED -> R.string.recordings_show_watched_detail
}

/**
 * Group, Sort and Show from the content's start edge, each naming its current value and filled while
 * it is not the default, in the Channels header's buttons. They keep their place whatever the values
 * say. Like that header, Up stays, Down goes to the list ([onDown]), Left from Group leaves for the
 * navigation rail ([navigationFocus]) and Right stops at Show. Back steps a folder up ([onBack]); from
 * the root it is left to the screen.
 */
@Composable
internal fun ArchivePillRow(
    view: DvrArchiveView,
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
    val default = DvrArchiveView()
    fun pillModifier(pill: ArchivePill) = Modifier
        .focusProperties {
            up = FocusRequester.Cancel
            if (pill == ArchivePill.GROUP) left = rail
            if (pill == ArchivePill.SHOW) right = FocusRequester.Cancel
        }
        .testTag("recordings-pill-${pill.name.lowercase()}")
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
        horizontalArrangement = Arrangement.spacedBy(TvSpacing16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = painterResource(R.drawable.ic_account_tree),
            label = stringResource(R.string.recordings_grouping_group_by),
            value = stringResource(view.grouping.labelRes()),
            active = view.grouping != default.grouping,
            onClick = { onOpen(ArchivePill.GROUP) },
            focusRequester = focusRequesters.getValue(ArchivePill.GROUP),
            onFocused = {},
            modifier = pillModifier(ArchivePill.GROUP),
        )
        val sort = stringResource(R.string.recordings_sort_title)
        val (sortField, sortArrow) = view.sort.pillRes()
        RoundIconButton(
            icon = painterResource(sortArrow),
            label = sort,
            value = stringResource(sortField),
            description = stringResource(
                R.string.header_button_value_description, sort, stringResource(view.sort.labelRes()),
            ),
            active = view.sort != default.sort,
            onClick = { onOpen(ArchivePill.SORT) },
            focusRequester = focusRequesters.getValue(ArchivePill.SORT),
            onFocused = {},
            modifier = pillModifier(ArchivePill.SORT),
        )
        val show = stringResource(R.string.recordings_show_title)
        RoundIconButton(
            icon = painterResource(view.show.iconRes()),
            label = show,
            value = stringResource(view.show.pillLabelRes()),
            description = stringResource(
                R.string.header_button_value_description, show, stringResource(view.show.labelRes()),
            ),
            active = view.show != default.show,
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
                    icon = when (option) {
                        DvrArchiveGrouping.FLAT -> R.drawable.ic_view_list
                        DvrArchiveGrouping.FOLDERS -> R.drawable.ic_folder
                        DvrArchiveGrouping.CHANNELS -> R.drawable.ic_live_tv_outlined
                        DvrArchiveGrouping.GENRES -> R.drawable.ic_theater_comedy
                    },
                    onClick = { pick(current.copy(grouping = option)) },
                    supporting = stringResource(option.detailRes()),
                )
            },
        )
        ArchivePill.SORT -> BrowseLevelsPanel(
            stringResource(R.string.recordings_sort_title), "recordings-sort-panel", onClose,
            DvrArchiveSort.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-sort-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.sort,
                    icon = option.pillRes().second,
                    onClick = { pick(current.copy(sort = option)) },
                )
            },
        )
        ArchivePill.SHOW -> BrowseLevelsPanel(
            stringResource(R.string.recordings_show_title), "recordings-show-panel", onClose,
            DvrArchiveShow.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-show-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.show,
                    icon = option.iconRes(),
                    onClick = { pick(current.copy(show = option)) },
                    supporting = option.detailRes()?.let { stringResource(it) },
                )
            },
        )
    }
}
