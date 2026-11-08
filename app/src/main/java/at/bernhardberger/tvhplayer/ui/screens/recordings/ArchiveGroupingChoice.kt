package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelList
import at.bernhardberger.tvhplayer.ui.screens.BrowseSidePanel

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

/**
 * The View options button of the Archive root, in the title row, naming the current grouping. It is its own focus stop between the
 * tabs and the list: every direction is decided here so no neighbour's key handling can move or
 * activate through it. [onUp], [onDown] and [onLeft] move focus on the first press only; held
 * repeats are swallowed, and Right goes nowhere.
 */
@Composable
internal fun ArchiveGroupingButton(
    grouping: DvrArchiveGrouping,
    /** Only a filter that narrows the list makes the button tonal; the grouping and sort name themselves. */
    active: Boolean,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: (() -> Unit)?,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BackHandler(enabled = focused, onBack = onUp)
    RoundIconButton(
        icon = painterResource(R.drawable.ic_tune),
        label = stringResource(R.string.channels_view_options),
        value = stringResource(grouping.labelRes()),
        active = active,
        onClick = onOpen,
        focusRequester = focusRequester,
        onFocused = {},
        modifier = modifier
            .focusProperties { right = FocusRequester.Cancel }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .onPreviewKeyEvent { event ->
                val move: (() -> Unit)? = when (event.key) {
                    Key.DirectionUp -> onUp
                    Key.DirectionDown -> onDown
                    Key.DirectionLeft -> onLeft ?: {}
                    Key.DirectionRight -> ({})
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) move?.invoke()
                true
            }
            .testTag("recordings-grouping-button"),
    )
}

private fun DvrArchiveSort.labelRes(): Int = when (this) {
    DvrArchiveSort.NEWEST -> R.string.recordings_sort_newest
    DvrArchiveSort.NAME -> R.string.recordings_sort_name
    DvrArchiveSort.LARGEST -> R.string.recordings_sort_largest
}

/**
 * View options for the Archive: how recordings are grouped and sorted (one choice each) and whether
 * watched ones are hidden. As in the Channels and Guide panels a choice applies at once and the panel
 * stays, with focus on the row just pressed; Back closes it ([onClose]).
 */
@Composable
internal fun ArchiveGroupingPanel(
    current: DvrArchiveView,
    onChoose: (DvrArchiveView) -> Unit,
    onClose: () -> Unit,
) {
    val entries = buildList {
        add(BrowsePanelEntry.Section(stringResource(R.string.recordings_grouping_group_by)))
        DvrArchiveGrouping.entries.forEach { option ->
            add(BrowsePanelEntry.Choice(
                key = "recordings-grouping-option-${option.name.lowercase()}",
                label = stringResource(option.labelRes()),
                selected = option == current.grouping,
                onClick = { onChoose(current.copy(grouping = option)) },
                supporting = stringResource(option.detailRes()),
            ))
        }
        add(BrowsePanelEntry.Section(stringResource(R.string.recordings_sort_title)))
        DvrArchiveSort.entries.forEach { option ->
            add(BrowsePanelEntry.Choice(
                key = "recordings-sort-option-${option.name.lowercase()}",
                label = stringResource(option.labelRes()),
                selected = option == current.sort,
                onClick = { onChoose(current.copy(sort = option)) },
            ))
        }
        add(BrowsePanelEntry.Section(stringResource(R.string.recordings_filter_title)))
        add(BrowsePanelEntry.Choice(
            key = "recordings-hide-watched",
            label = stringResource(R.string.recordings_hide_watched),
            selected = false,
            onClick = { onChoose(current.copy(hideWatched = !current.hideWatched)) },
            checked = current.hideWatched,
        ))
    }
    BrowseSidePanel(stringResource(R.string.channels_view_options), "recordings-grouping-panel", onClose) {
        BrowsePanelList(entries, radio = true)
    }
}
