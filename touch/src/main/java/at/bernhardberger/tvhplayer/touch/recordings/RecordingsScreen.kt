package at.bernhardberger.tvhplayer.touch.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.OnDestinationReselected
import at.bernhardberger.tvhplayer.touch.ui.TouchFrame
import kotlin.math.roundToInt
import at.bernhardberger.tvhplayer.core.DvrArchiveView
import at.bernhardberger.tvhplayer.core.DvrArchiveShow
import at.bernhardberger.tvhplayer.core.DvrArchiveGrouping

/**
 * Recordings > Browse. The tablet keeps one list column and a pane for the picked recording; the
 * upright tablet and the phone open a pick as a page. Folders open one level at a time.
 */
@Composable
internal fun RecordingsScreen(
    state: RecordingsState,
    actions: RecordingsActions,
    modifier: Modifier = Modifier,
    initialPath: List<String> = emptyList(),
    initiallyPicked: String? = null,
    destinations: List<Destination> = Destination.entries,
) {
    val layout = recordingsLayout()
    var path by rememberSaveable(state.view.grouping) { mutableStateOf<List<String>>(ArrayList(initialPath)) }
    var picked by rememberSaveable(state.view.grouping) { mutableStateOf(initiallyPicked) }
    var sheet by remember { mutableStateOf<SheetPage?>(null) }

    val levels = state.root.levels(path)
    val pickedItem = picked?.let(state.root::find)
    val pageItem = pickedItem?.takeIf { layout != RecordingsLayout.Wide }
    val page = pageItem != null
    val listed = state.listed
    val browse = rememberSaveableStateHolder()
    LaunchedEffect(state.availability, levels, path, picked, pickedItem) {
        if (state.availability != RecordingsAvailability.Current) return@LaunchedEffect
        path = ArrayList(levels.drop(1).map { it.id })
        picked = pickedItem?.id
    }

    Box(modifier.fillMaxSize()) {
        TouchFrame(
            initialDestination = Destination.Recordings, destinations = destinations,
            showTopBar = { !(it == Destination.Recordings && page) },
            showNavigation = { !(it == Destination.Recordings && page && layout == RecordingsLayout.Phone) },
            lastActionIsIconOnly = layout.compact,
            actions = { destination ->
                if (destination == Destination.Recordings && listed) {
                    TitleActions(state.space, layout, sheetOpen = sheet != null) { sheet = SheetPage.Main }
                }
            },
            belowTopBar = { destination ->
                if (destination == Destination.Recordings) RecordingsLoadingLine(state.availability, layout)
            },
        ) { destination ->
            if (destination != Destination.Recordings) return@TouchFrame
            // Registered after the frame's own handler, so a page or a folder level takes Back first.
            val closeDetails = { picked = null }
            BackHandler(enabled = page, onBack = closeDetails)
            BackHandler(enabled = !page && levels.size > 1) { path = ArrayList(path.take(levels.size - 2)) }
            if (!listed) {
                RecordingsStandIn(state.availability, layout, actions)
            } else if (pageItem != null) {
                OnDestinationReselected(Destination.Recordings) { closeDetails() }
                RecordingPage(pageItem, state.clock, layout, actions, closeDetails)
            } else {
                browse.SaveableStateProvider("RecordingsBrowse") {
                    Browse(
                        state, levels, layout, pickedItem, actions,
                        onOpen = { path = ArrayList(levels.drop(1).map { it.id } + it) },
                        onUp = { path = ArrayList(path.take(levels.size - 2)) },
                        onPick = { picked = it },
                    )
                }
            }
        }
        sheet?.let { current ->
            ViewOptionsSheet(
                page = current, view = state.view,
                side = layout != RecordingsLayout.Phone,
                onPage = { sheet = it }, onView = actions.onView, onClose = { sheet = null },
            )
        }
    }
}

@Composable
private fun recordingsLayout(): RecordingsLayout {
    val window = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> RecordingsLayout.Wide
        window.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> RecordingsLayout.Upright
        else -> RecordingsLayout.Phone
    }
}

internal enum class RecordingsLayout {
    Phone, Upright, Wide;

    val compact: Boolean get() = this == Phone
}

/** The folders from the root to the deepest one [path] still names. */
private fun RecordingFolder.levels(path: List<String>): List<RecordingFolder> {
    val levels = mutableListOf(this)
    for (id in path) levels += levels.last().folders.find { it.id == id } ?: break
    return levels
}

private fun RecordingFolder.find(id: String): RecordingItem? =
    recordings.find { it.id == id } ?: folders.firstNotNullOfOrNull { it.find(id) }

@Composable
private fun TitleActions(
    space: RecordingSpace?,
    layout: RecordingsLayout,
    sheetOpen: Boolean,
    openViewOptions: () -> Unit,
) {
    val label = stringResource(R.string.recordings_view_options)
    if (layout.compact) {
        IconButton(onClick = openViewOptions) { Icon(painterResource(R.drawable.recordings_tune), label) }
        return
    }
    val viewOptionsFocus = remember { FocusRequester() }
    var restoreFocus by remember { mutableStateOf(false) }
    LaunchedEffect(sheetOpen) {
        if (!sheetOpen && restoreFocus) {
            viewOptionsFocus.requestFocus()
            restoreFocus = false
        }
    }
    if (space != null) FreeSpace(space)
    FilledTonalButton(
        onClick = {
            restoreFocus = true
            openViewOptions()
        },
        modifier = Modifier.padding(start = 16.dp).focusRequester(viewOptionsFocus),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(painterResource(R.drawable.recordings_tune), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

@Composable
private fun FreeSpace(space: RecordingSpace) {
    val used = space.usedFraction.coerceIn(0f, 1f)
    val description = stringResource(
        R.string.recordings_space_description, space.free, space.total, (used * PERCENT).roundToInt(),
    )
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = { used },
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            stringResource(R.string.recordings_space, space.free, space.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Browse(
    state: RecordingsState,
    levels: List<RecordingFolder>,
    layout: RecordingsLayout,
    picked: RecordingItem?,
    actions: RecordingsActions,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onPick: (String) -> Unit,
) {
    val margin = when (layout) {
        RecordingsLayout.Wide -> Modifier.padding(start = 8.dp, end = BrowseEndInset)
        RecordingsLayout.Upright -> Modifier.padding(horizontal = BrowseEndInset)
        RecordingsLayout.Phone -> Modifier.padding(horizontal = 12.dp)
    }
    if (state.availability != RecordingsAvailability.Offline) {
        BrowseLevel(state, levels, layout, margin, picked, actions, onOpen, onUp, onPick)
        return
    }
    Column(Modifier.fillMaxSize()) {
        RecordingsOfflineBanner(layout, actions.onOpenConnectionSettings, margin.padding(top = 8.dp))
        Box(Modifier.weight(1f)) { BrowseLevel(state, levels, layout, margin, picked, actions, onOpen, onUp, onPick) }
    }
}

@Composable
private fun BrowseLevel(
    state: RecordingsState,
    levels: List<RecordingFolder>,
    layout: RecordingsLayout,
    margin: Modifier,
    picked: RecordingItem?,
    actions: RecordingsActions,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onPick: (String) -> Unit,
) {
    val level = levels.last()
    val head: @Composable () -> Unit = { LevelHead(state.view, levels, layout, onUp) }
    // Inside a channel's folder every row is that channel's.
    val oneChannel = state.view.grouping == DvrArchiveGrouping.CHANNELS && levels.size > 1
    when {
        state.root.isEmpty && state.view.show == DvrArchiveShow.ALL -> RecordingsMessage(
            R.drawable.video_library_outline, R.string.recordings_none, R.string.recordings_none_reason,
        )
        level.isEmpty -> Column(margin.fillMaxSize()) {
            head()
            FilterEmpty(state.view, actions.onView)
        }
        layout == RecordingsLayout.Wide -> Row(margin.fillMaxSize()) {
            RecordingList(level, state.clock, oneChannel, picked?.id, head, onOpen, onPick, Modifier.weight(1f))
            Spacer(Modifier.width(24.dp))
            Surface(
                Modifier.weight(1f).padding(top = 16.dp, bottom = 8.dp).testTag("recordings-pane"),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                if (picked == null) Placeholder(level) else RecordingPane(picked, state.clock, actions)
            }
        }
        else -> RecordingList(
            level, state.clock, oneChannel, null, head, onOpen, onPick, margin, highlightPlaying = true,
        )
    }
}

private val RecordingFolder.isEmpty: Boolean get() = folders.isEmpty() && recordings.isEmpty()

@Composable
private fun FilterEmpty(view: DvrArchiveView, onView: (DvrArchiveView) -> Unit) {
    val (icon, title, reason) = when (view.show) {
        DvrArchiveShow.UNWATCHED -> Triple(
            R.drawable.recordings_done_all, R.string.recordings_empty_unwatched,
            R.string.recordings_empty_unwatched_reason,
        )
        DvrArchiveShow.PARTIALLY_WATCHED -> Triple(
            R.drawable.video_library_outline, R.string.recordings_empty_partially_watched,
            R.string.recordings_empty_partially_watched_reason,
        )
        DvrArchiveShow.WATCHED, DvrArchiveShow.ALL -> Triple(
            R.drawable.video_library_outline, R.string.recordings_empty_watched,
            R.string.recordings_empty_watched_reason,
        )
    }
    RecordingsMessage(icon, title, reason) {
        FilledTonalButton(onClick = { onView(view.copy(show = DvrArchiveShow.ALL)) }) {
            Text(stringResource(R.string.recordings_show_everything))
        }
    }
}

@Composable
private fun RecordingsMessage(icon: Int, title: Int, reason: Int, action: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(icon), null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Text(
                stringResource(title), Modifier.padding(top = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(reason), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Box(Modifier.padding(top = 16.dp)) { action() }
        }
    }
}

@Composable
private fun Placeholder(level: RecordingFolder) {
    val recordings = pluralStringResource(R.plurals.recordings_count, level.recordingCount, level.recordingCount)
    val folders = level.folderCount
    val count = if (folders == 0) recordings else stringResource(
        R.string.recordings_in_folders, recordings,
        pluralStringResource(R.plurals.recordings_folder_count, folders, folders),
    )
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painterResource(R.drawable.video_library_outline), null, Modifier.size(48.dp).padding(bottom = 8.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                stringResource(R.string.recordings_pick),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private val BrowseEndInset = 24.dp
private const val PERCENT = 100

/** The list shows while it is current or saved; otherwise a stand-in takes its place. */
private val RecordingsState.listed: Boolean
    get() = availability == RecordingsAvailability.Current || availability == RecordingsAvailability.Offline
