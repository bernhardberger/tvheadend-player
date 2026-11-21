@file:Suppress("MatchingDeclarationName") // SheetPage only names this sheet's pages.

package at.bernhardberger.tvhplayer.touch.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.TouchSideSheet

/** Where the view options sheet stands: its three rows, or the list one of them opened. */
internal enum class SheetPage { Main, GroupBy, Sort, Show }

/**
 * Group by, Sort and Show. A side sheet on the tablet, a bottom sheet on the phone; a row opens
 * its choices inside the sheet, and a choice applies at once and returns to the rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewOptionsSheet(
    page: SheetPage,
    view: RecordingsView,
    side: Boolean,
    onPage: (SheetPage) -> Unit,
    onView: (RecordingsView) -> Unit,
    onClose: () -> Unit,
) {
    val choose: (RecordingsView) -> Unit = {
        onView(it)
        onPage(SheetPage.Main)
    }
    val title = stringResource(
        when (page) {
            SheetPage.Main -> R.string.recordings_view_options
            SheetPage.GroupBy -> R.string.recordings_group_by
            SheetPage.Sort -> R.string.recordings_sort
            SheetPage.Show -> R.string.recordings_show
        },
    )
    val back = if (page == SheetPage.Main) null else ({ onPage(SheetPage.Main) })
    if (side) {
        TouchSideSheet(onDismissRequest = onClose, title = title, onBack = back) {
            SheetBody(page, view, onPage, choose, Modifier.padding(horizontal = 16.dp))
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        if (back != null) BackHandler(onBack = back)
        Column(Modifier.semantics { paneTitle = title }.windowInsetsPadding(WindowInsets.navigationBars)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (back != null) {
                    IconButton(onClick = back) {
                        Icon(painterResource(R.drawable.connect_back_outline), stringResource(R.string.side_sheet_back))
                    }
                }
                Text(
                    title,
                    Modifier.weight(1f).padding(start = if (back == null) 8.dp else 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            SheetBody(page, view, onPage, choose, Modifier.padding(horizontal = 12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetBody(
    page: SheetPage,
    view: RecordingsView,
    onPage: (SheetPage) -> Unit,
    choose: (RecordingsView) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        when (page) {
            SheetPage.Main -> {
                val rows = listOf(
                    Triple(R.string.recordings_group_by, view.grouping.label, SheetPage.GroupBy),
                    Triple(R.string.recordings_sort, view.sort.label, SheetPage.Sort),
                    Triple(R.string.recordings_show, view.show.label, SheetPage.Show),
                )
                rows.forEachIndexed { index, (label, value, target) ->
                    Segment(index, rows.size) {
                        ListItem(
                            onClick = { onPage(target) },
                            supportingContent = { Text(stringResource(value)) },
                            trailingContent = { Icon(painterResource(R.drawable.connect_next_outline), null) },
                            shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                            colors = ListItemDefaults.segmentedColors(),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
            SheetPage.GroupBy -> Choices(RecordingsGrouping.entries, view.grouping, { it.label }, { it.detail }) {
                choose(view.copy(grouping = it))
            }
            SheetPage.Sort -> Choices(RecordingsSort.entries, view.sort, { it.label }, { null }) {
                choose(view.copy(sort = it))
            }
            SheetPage.Show -> Choices(RecordingsShow.entries, view.show, { it.label }, { it.detail }) {
                choose(view.copy(show = it))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> Choices(options: List<T>, current: T, label: (T) -> Int, detail: (T) -> Int?, onChoose: (T) -> Unit) {
    options.forEachIndexed { index, option ->
        Segment(index, options.size) {
            ListItem(
                selected = option == current,
                onClick = { onChoose(option) },
                leadingContent = { RadioButton(selected = option == current, onClick = null) },
                supportingContent = detail(option)?.let { { Text(stringResource(it)) } },
                shapes = ListItemDefaults.segmentedShapes(index, options.size),
                colors = ListItemDefaults.segmentedColors(),
            ) { Text(stringResource(label(option))) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Segment(index: Int, count: Int, content: @Composable () -> Unit) {
    Box(Modifier.padding(bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp)) { content() }
}

