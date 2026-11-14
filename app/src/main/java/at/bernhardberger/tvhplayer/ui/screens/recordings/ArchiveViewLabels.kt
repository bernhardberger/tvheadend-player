package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.ui.TvSpacing8

internal fun DvrArchiveGrouping.labelRes(): Int = when (this) {
    DvrArchiveGrouping.FLAT -> R.string.recordings_grouping_flat
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres
}

/** The Browse root's headline: the grouping's name, or All recordings for the flat list. */
internal fun DvrArchiveGrouping.rootHeadingRes(): Int =
    if (this == DvrArchiveGrouping.FLAT) R.string.recordings_archive_all else labelRes()

internal fun DvrArchiveGrouping.detailRes(): Int = when (this) {
    DvrArchiveGrouping.FLAT -> R.string.recordings_grouping_none_detail
    DvrArchiveGrouping.FOLDERS -> R.string.recordings_grouping_folders_detail
    DvrArchiveGrouping.CHANNELS -> R.string.recordings_grouping_channels_detail
    DvrArchiveGrouping.GENRES -> R.string.recordings_grouping_genres_detail
}

internal fun DvrArchiveGrouping.iconRes(): Int = when (this) {
    DvrArchiveGrouping.FLAT -> R.drawable.ic_view_list
    DvrArchiveGrouping.FOLDERS -> R.drawable.ic_folder
    DvrArchiveGrouping.CHANNELS -> R.drawable.ic_live_tv_outlined
    DvrArchiveGrouping.GENRES -> R.drawable.ic_theater_comedy
}

internal fun DvrArchiveSort.labelRes(): Int = when (this) {
    DvrArchiveSort.NEWEST -> R.string.recordings_sort_newest
    DvrArchiveSort.OLDEST -> R.string.recordings_sort_oldest
    DvrArchiveSort.NAME -> R.string.recordings_sort_name
    DvrArchiveSort.NAME_DESCENDING -> R.string.recordings_sort_name_descending
    DvrArchiveSort.LARGEST -> R.string.recordings_sort_largest
    DvrArchiveSort.SMALLEST -> R.string.recordings_sort_smallest
}

internal fun DvrArchiveSort.iconRes(): Int = when (this) {
    DvrArchiveSort.NEWEST -> R.drawable.ic_sort_newest_first
    DvrArchiveSort.OLDEST -> R.drawable.ic_sort_oldest_first
    DvrArchiveSort.NAME -> R.drawable.ic_sort_name_a_z
    DvrArchiveSort.NAME_DESCENDING -> R.drawable.ic_sort_name_z_a
    DvrArchiveSort.LARGEST -> R.drawable.ic_sort_largest_first
    DvrArchiveSort.SMALLEST -> R.drawable.ic_sort_smallest_first
}

internal fun DvrArchiveShow.labelRes(): Int = when (this) {
    DvrArchiveShow.ALL -> R.string.recordings_show_option_all
    DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched
    DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched
    DvrArchiveShow.WATCHED -> R.string.recordings_show_watched
}

/** The mark a row of that kind carries, so the choice and the rows speak the same symbols. */
internal fun DvrArchiveShow.iconRes(): Int = when (this) {
    DvrArchiveShow.ALL -> R.drawable.ic_visibility
    DvrArchiveShow.UNWATCHED -> R.drawable.ic_radio_button_unchecked
    DvrArchiveShow.PARTIALLY_WATCHED -> R.drawable.ic_clock_loader_40_outlined
    DvrArchiveShow.WATCHED -> R.drawable.ic_check_circle_outlined
}

/** The plain-language line under a Show choice; All needs none. */
internal fun DvrArchiveShow.detailRes(): Int? = when (this) {
    DvrArchiveShow.ALL -> null
    DvrArchiveShow.UNWATCHED -> R.string.recordings_show_unwatched_detail
    DvrArchiveShow.PARTIALLY_WATCHED -> R.string.recordings_show_partially_watched_detail
    DvrArchiveShow.WATCHED -> R.string.recordings_show_watched_detail
}

/** The Sort and Show values that differ from the defaults, in that order: the headline's quiet line. */
@Composable
internal fun DvrArchiveView.nonDefaultValues(): List<String> {
    val default = DvrArchiveView()
    return buildList {
        if (sort != default.sort) add(stringResource(sort.labelRes()))
        if (show != default.show) add(stringResource(show.labelRes()))
    }
}

/**
 * The Archive level with nothing to list: the plain empty line, or, while Show narrows the list, what the
 * filter hides here ([hidden] recordings at any depth below).
 */
@Composable
internal fun ArchiveEmptyLevel(show: DvrArchiveShow, hidden: Int) {
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
        else -> R.string.recordings_empty_not_watched_root
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
