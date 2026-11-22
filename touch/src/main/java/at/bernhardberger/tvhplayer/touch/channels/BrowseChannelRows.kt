package at.bernhardberger.tvhplayer.touch.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.touch.ui.ChannelLogoTile
import at.bernhardberger.tvhplayer.touch.ui.Destination
import at.bernhardberger.tvhplayer.touch.ui.LogoShape
import at.bernhardberger.tvhplayer.touch.ui.PartsLine
import at.bernhardberger.tvhplayer.touch.ui.PlayingBars
import at.bernhardberger.tvhplayer.touch.ui.ScrollToStartOnReselect
import at.bernhardberger.tvhplayer.presentation.R as SharedR

@Composable
internal fun BrowseChannelRows(
    list: ChannelList,
    actions: ChannelsActions,
    scroll: LazyListState,
    offline: Boolean,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    onSelect: ((String) -> Unit)? = null,
) {
    scroll.ScrollToStartOnReselect(Destination.Channels)
    LazyColumn(modifier, state = scroll, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        itemsIndexed(list.rows, key = { _, row -> row.id }) { index, row ->
            val play = actions.onPlay?.let { watch -> { watch(row.id) } }
            BrowseChannelRow(
                row = row,
                shapes = ListItemDefaults.segmentedShapes(index, list.rows.size),
                offline = offline,
                onOpen = (onSelect ?: actions.onOpenChannel?.takeUnless { offline })?.let { open -> { open(row.id) } },
                onPlay = play.takeIf { onSelect == null },
                onLogoPlay = play.takeIf { onSelect != null },
                selected = row.id == selectedId,
                selectable = onSelect != null,
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
    onLogoPlay: (() -> Unit)? = null,
    selected: Boolean = false,
    selectable: Boolean = false,
) {
    val modifier = Modifier.fillMaxWidth().alpha(if (offline) SAVED_ROW_ALPHA else 1f)
    val leading: @Composable () -> Unit = { BrowseChannelLogo(row, offline, onLogoPlay, selected) }
    val trailing: (@Composable () -> Unit)? = onPlay?.let { play -> { BrowseChannelPlay(row, !offline, play) } }
    val content: @Composable () -> Unit = { BrowseChannelText(row, offline) }
    val padding = PaddingValues(horizontal = BrowseRowInset, vertical = BrowseRowVerticalPadding)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onOpen == null) {
        SegmentedListItem(
            shapes = shapes, colors = colors, modifier = modifier, leadingContent = leading, trailingContent = trailing,
            verticalAlignment = Alignment.CenterVertically, contentPadding = padding, content = content,
        )
    } else if (selectable) {
        SegmentedListItem(
            selected = selected, onClick = onOpen, shapes = shapes, colors = colors,
            modifier = modifier.semantics { role = Role.Button }, leadingContent = leading,
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
private fun BrowseChannelLogo(row: ChannelRow, offline: Boolean, onPlay: (() -> Unit)?, selected: Boolean) {
    Column(Modifier.width(BrowseTileWidth), verticalArrangement = Arrangement.spacedBy(ProgressGap)) {
        val size = Modifier.size(BrowseTileWidth, BrowseTileHeight)
        if (onPlay == null) {
            ChannelLogoTile(row.logo, row.logoTint, size, model = row.logoModel)
        } else {
            val label = watchLabel(row, !offline)
            Surface(
                onClick = onPlay, enabled = !offline, shape = LogoShape, color = Color.Transparent,
                modifier = size.semantics {
                    role = Role.Button
                    contentDescription = label
                },
            ) {
                ChannelLogoTile(row.logo, row.logoTint, size, model = row.logoModel)
            }
        }
        ChannelProgress(
            row.now, row.playback.takeUnless { offline }, row.recording.takeUnless { offline }, Modifier.fillMaxWidth(),
            surfaceTrack = selected,
        )
    }
}

@Composable
private fun BrowseChannelPlay(row: ChannelRow, enabled: Boolean, onPlay: () -> Unit) {
    FilledTonalIconButton(onClick = onPlay, enabled = enabled) {
        Icon(painterResource(R.drawable.channels_play), watchLabel(row, enabled))
    }
}

@Composable
private fun watchLabel(row: ChannelRow, enabled: Boolean): String =
    if (enabled && row.playback != null) {
        stringResource(
            R.string.channels_continue_named, stringResource(SharedR.string.player_continue_watching), row.name,
        )
    } else {
        stringResource(R.string.channels_watch_named, row.name)
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
            if (!offline && row.playback != null) PlayingBars()
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
    val title = program?.title ?: stringResource(SharedR.string.no_epg)
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
    val parts = listOfNotNull(stringResource(R.string.channels_row_next, program.title), program.start)
    Row(horizontalArrangement = Arrangement.spacedBy(MarkGap), verticalAlignment = Alignment.CenterVertically) {
        PartsLine(parts, Modifier.weight(1f, fill = false), style, color)
        if (showMarks && program.scheduled) ScheduledProgramMark(program)
    }
}

private val BrowseRowInset = 12.dp
private val BrowseRowVerticalPadding = 8.dp
private val BrowseTileWidth = 88.dp
private val BrowseTileHeight = 48.dp
private val ProgressGap = 4.dp
private val MarkGap = 6.dp
private val FactGap = 5.dp
