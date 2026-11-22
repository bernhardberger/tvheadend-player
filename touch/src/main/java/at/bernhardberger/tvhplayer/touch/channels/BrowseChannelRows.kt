package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.ScrollToStartOnReselect

@Composable
internal fun BrowseChannelRows(
    list: ChannelList,
    actions: ChannelsActions,
    offline: Boolean = false,
    emptyContent: @Composable () -> Unit = {},
) {
    // A restored scroll position belongs to the tag it was saved with.
    var savedTag by rememberSaveable { mutableStateOf(list.selectedTagId) }
    val scroll = rememberLazyListState()
    SideEffect {
        if (savedTag != list.selectedTagId) {
            scroll.requestScrollToItem(0)
            savedTag = list.selectedTagId
        }
    }
    if (list.rows.isEmpty()) {
        emptyContent()
        return
    }
    scroll.ScrollToStartOnReselect(Destination.Channels)
    LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        itemsIndexed(list.rows, key = { _, row -> row.id }) { index, row ->
            BrowseChannelRow(
                row = row,
                shapes = ListItemDefaults.segmentedShapes(index, list.rows.size),
                offline = offline,
                onOpen = actions.onOpenChannel?.takeUnless { offline }?.let { open -> { open(row.id) } },
                onPlay = actions.onPlay?.let { play -> { play(row.id) } },
            )
        }
    }
}

@Composable
private fun BrowseChannelRow(
    row: ChannelRow,
    shapes: ListItemShapes,
    offline: Boolean,
    onOpen: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    logoModifier: Modifier = Modifier,
) {
    val modifier = Modifier.fillMaxWidth().alpha(if (offline) SAVED_ROW_ALPHA else 1f)
    val leading: @Composable () -> Unit = { BrowseChannelLogo(row, offline, logoModifier) }
    val trailing: (@Composable () -> Unit)? = onPlay?.let { play -> { BrowseChannelPlay(row.name, !offline, play) } }
    val content: @Composable () -> Unit = { BrowseChannelText(row, offline) }
    val padding = PaddingValues(horizontal = BrowseRowInset, vertical = BrowseRowVerticalPadding)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onOpen == null) {
        SegmentedListItem(
            shapes = shapes, colors = colors, modifier = modifier, leadingContent = leading, trailingContent = trailing,
            verticalAlignment = Alignment.CenterVertically, contentPadding = padding, content = content,
        )
    } else {
        SegmentedListItem(
            onClick = onOpen, shapes = shapes, colors = colors, modifier = modifier.semantics { role = Role.Button },
            leadingContent = leading, trailingContent = trailing,
            verticalAlignment = Alignment.CenterVertically, contentPadding = padding, content = content,
        )
    }
}

@Composable
private fun BrowseChannelLogo(row: ChannelRow, offline: Boolean, modifier: Modifier) {
    Column(Modifier.width(BrowseTileWidth), verticalArrangement = Arrangement.spacedBy(ProgressGap)) {
        ChannelLogoTile(row.logo, row.logoTint, modifier.size(BrowseTileWidth, BrowseTileHeight), model = row.logoModel)
        ChannelProgress(
            row.now, row.playback.takeUnless { offline }, row.recording.takeUnless { offline }, Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BrowseChannelPlay(channel: String, enabled: Boolean, onPlay: () -> Unit) {
    FilledTonalIconButton(onClick = onPlay, enabled = enabled) {
        Icon(painterResource(R.drawable.channels_play), stringResource(R.string.channels_watch_named, channel))
    }
}

@Composable
private fun BrowseChannelText(row: ChannelRow, offline: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(MarkGap), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!offline && row.recording != null) {
                RecordingBadge(row.recording, compact = row.recording == ChannelRecording.Channel)
            }
        }
        BrowseNow(row.now)
        BrowseNext(row.next.takeIf { row.now != null }, !offline)
    }
}

@Composable
private fun BrowseNow(program: ChannelProgram?) {
    val style = MaterialTheme.typography.bodyMedium
    val colors = MaterialTheme.colorScheme
    val title = program?.title ?: stringResource(R.string.channels_no_program)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, Modifier.weight(1f, fill = false), style = style,
            color = if (program == null) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        program?.timeLeft?.let {
            Text(
                stringResource(R.string.channels_row_fact, stringResource(R.string.channels_time_left, it)),
                Modifier.padding(start = FactGap), style = style, color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BrowseNext(program: ChannelProgram?, showMarks: Boolean) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (program == null) {
        // Reserve the same text line for sparse guide data, without announcing an empty line.
        Text("", style = style, minLines = 1)
        return
    }
    val title = stringResource(R.string.channels_row_next, program.title)
    val showMark = showMarks && program.scheduled
    BoxWithConstraints {
        val candidate = program.start?.let { stringResource(R.string.channels_program_and_time_left, title, it) }
        val measurer = rememberTextMeasurer()
        val textWidth = with(LocalDensity.current) {
            (constraints.maxWidth - if (showMark) ScheduledMarkSize.roundToPx() + MarkGap.roundToPx() else 0)
                .coerceAtLeast(0)
        }
        // Reserve the recording mark before deciding whether the entire title and time fit.
        val line = candidate?.takeUnless {
            measurer.measure(it, style, maxLines = 1, constraints = Constraints(maxWidth = textWidth)).hasVisualOverflow
        } ?: title
        Row(horizontalArrangement = Arrangement.spacedBy(MarkGap), verticalAlignment = Alignment.CenterVertically) {
            Text(line, Modifier.weight(1f, fill = false), style = style, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showMark) ScheduledProgramMark(program)
        }
    }
}

private val BrowseRowInset = 12.dp
private val BrowseRowVerticalPadding = 8.dp
private val BrowseTileWidth = 88.dp
private val BrowseTileHeight = 48.dp
private val ProgressGap = 4.dp
private val MarkGap = 6.dp
private val FactGap = 5.dp
