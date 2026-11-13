package at.bernhardberger.tvhplayer.ui.screens.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveSort
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnWidth
import at.bernhardberger.tvhplayer.ui.TvIconButtonSize
import at.bernhardberger.tvhplayer.ui.components.RoundIconButton
import at.bernhardberger.tvhplayer.ui.screens.BrowseLevelsPanel
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelEntry
import at.bernhardberger.tvhplayer.ui.screens.BrowsePanelLevel

/**
 * The Browse view options: Group, Sort and Show each open a level of their choices, and under Folders
 * the Recording now folder switch sits on the root. Each change applies at once; Back steps out of
 * the level, then closes.
 */
@Composable
internal fun ArchiveViewOptionsPanel(
    current: DvrArchiveView,
    onChoose: (DvrArchiveView) -> Unit,
    onClose: () -> Unit,
) {
    val entries = buildList {
        add(optionLevel("recordings-view-group", R.string.recordings_grouping_group_by, current.grouping.labelRes(),
            DvrArchiveGrouping.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-grouping-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.grouping,
                    icon = option.iconRes(),
                    onClick = { onChoose(current.copy(grouping = option)) },
                    supporting = stringResource(option.detailRes()),
                )
            }))
        add(optionLevel("recordings-view-sort", R.string.recordings_sort_title, current.sort.labelRes(),
            DvrArchiveSort.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-sort-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.sort,
                    icon = option.iconRes(),
                    onClick = { onChoose(current.copy(sort = option)) },
                )
            }))
        add(optionLevel("recordings-view-show", R.string.recordings_show_title, current.show.labelRes(),
            DvrArchiveShow.entries.map { option ->
                BrowsePanelEntry.Choice(
                    key = "recordings-show-option-${option.name.lowercase()}",
                    label = stringResource(option.labelRes()),
                    selected = option == current.show,
                    icon = option.iconRes(),
                    onClick = { onChoose(current.copy(show = option)) },
                    supporting = option.detailRes()?.let { stringResource(it) },
                )
            }))
        if (current.grouping == DvrArchiveGrouping.FOLDERS) {
            add(BrowsePanelEntry.Divider)
            add(BrowsePanelEntry.Choice(
                key = "recordings-view-recording-now",
                label = stringResource(R.string.recordings_recording_now_folder),
                selected = false,
                onClick = { onChoose(current.copy(recordingNowFolder = !current.recordingNowFolder)) },
                checked = current.recordingNowFolder,
            ))
        }
    }
    BrowseLevelsPanel(stringResource(R.string.channels_view_options), "recordings-view-panel", onClose, entries)
}

/** A root entry that opens the level of its [choices], showing the current value. */
@Composable
private fun optionLevel(key: String, title: Int, value: Int, choices: List<BrowsePanelEntry>): BrowsePanelEntry {
    val label = stringResource(title)
    return BrowsePanelEntry.Choice(
        key = key,
        label = label,
        selected = false,
        onClick = {},
        level = BrowsePanelLevel(title = label, currentValue = stringResource(value), entries = choices),
    )
}

/**
 * The one options button at the end of the Browse headline, centred on its capitals: filled while Show
 * hides recordings, its spoken label naming the Sort and Show values that differ from the defaults.
 * Up goes to the tabs ([upFocus]), Down to the list ([onDown]), Left to the navigation rail
 * ([navigationFocus]) and Right stays. Back steps a folder up ([onBack]); from the root it is left to the
 * screen. Placed where the headline starts, it is as wide as the list column so its end meets the rows'.
 */
@Composable
internal fun ArchiveViewOptionsButton(
    view: DvrArchiveView,
    focusRequester: FocusRequester,
    upFocus: FocusRequester,
    navigationFocus: FocusRequester?,
    canStepBack: Boolean,
    onOpen: () -> Unit,
    onDown: () -> Unit,
    onBack: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    BackHandler(enabled = hasFocus && canStepBack, onBack = onBack)
    val style = RecordingsHeadlineStyle
    val baseline = rememberTextMeasurer().measure("Ag", style).firstBaseline
    val density = LocalDensity.current
    val top = with(density) {
        (baseline - style.fontSize.toPx() * CAP_HEIGHT_FRACTION / 2f).toDp() - TvIconButtonSize / 2
    }
    val label = stringResource(R.string.channels_view_options)
    val values = view.nonDefaultValues()
    Box(modifier.width(TvBrowseColumnWidth).offset(y = top), contentAlignment = Alignment.TopEnd) {
        RoundIconButton(
            icon = painterResource(R.drawable.ic_tune),
            label = label,
            description = if (values.isEmpty()) label else stringResource(
                R.string.header_button_value_description, label, values.joinToString(", "),
            ),
            active = view.show != DvrArchiveView().show,
            onClick = onOpen,
            focusRequester = focusRequester,
            onFocused = {},
            modifier = Modifier
                .focusProperties {
                    up = upFocus
                    left = navigationFocus ?: FocusRequester.Default
                    right = FocusRequester.Cancel
                }
                .onFocusChanged {
                    hasFocus = it.hasFocus
                    onFocusChanged(it.hasFocus)
                }
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && onDown().let { true }
                }
                .testTag("recordings-view-options"),
        )
    }
}

/** Cap height of the app's sans-serif as a fraction of its font size, to centre the button on capitals. */
private const val CAP_HEIGHT_FRACTION = 0.711f
