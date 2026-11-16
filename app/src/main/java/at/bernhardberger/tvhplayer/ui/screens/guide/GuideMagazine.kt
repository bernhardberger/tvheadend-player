package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.programmeArtwork
import at.bernhardberger.tvhplayer.core.programmeArtworkModel
import at.bernhardberger.tvhplayer.ui.*
import at.bernhardberger.tvhplayer.ui.common.formatDurationMinutes
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.LocalArtworkContext
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.RecordingStatusIndicator
import at.bernhardberger.tvhplayer.ui.components.ChannelRowEdgeInset
import at.bernhardberger.tvhplayer.ui.components.ListBottomReserve
import at.bernhardberger.tvhplayer.ui.components.listEdgeFades
import at.bernhardberger.tvhplayer.ui.components.rememberListEdgeBringIntoView
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import kotlin.math.abs

internal data class MagazineEntry(
    val event: EpgEvent,
    val priority: Boolean = false,
    val recording: DvrEntryState? = null,
)

internal data class MagazineColumn(val channel: Channel, val entries: List<MagazineEntry>)

/**
 * Trial presentation only: the caller supplies the chosen-time schedule and owns details/Back.
 * Heads stay fixed while each channel's programme list scrolls independently.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun GuideMagazine(
    columns: List<MagazineColumn>,
    nowSec: Long,
    onOpenProgram: (EpgEvent) -> Unit,
    modifier: Modifier = Modifier,
    navigationFocus: FocusRequester = FocusRequester.Default,
    headerFocus: FocusRequester = FocusRequester.Default,
    initialFocus: FocusRequester? = null,
    columnCount: Int = 3,
) {
    require(columnCount in 3..4)
    val scope = rememberCoroutineScope()
    val lists = columns.map { key(it.channel.id) { rememberLazyListState() } }
    val requests = remember(columns, initialFocus) {
        columns.mapIndexed { c, column ->
            column.entries.mapIndexed { e, _ ->
                if (c == 0 && e == 0 && initialFocus != null) initialFocus else FocusRequester()
            }
        }
    }
    var focusedColumn by remember { mutableIntStateOf(-1) }
    LaunchedEffect(requests) {
        // Let the TV Surface's interaction collector attach before requesting initial focus.
        withFrameNanos { }
        requests.firstOrNull()?.firstOrNull()?.requestFocus()
    }
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTrackAlpha)
    val columnGap = if (columnCount == 3) TvGridGutter * 2 else TvSpacing32
    BoxWithConstraints(modifier) {
        // Four columns fill the viewport; three reserve 48dp of the next column's text.
        val width = when {
            columnCount == 3 -> (maxWidth - ChannelRowEdgeInset * 2 - TvSpacing48) / 3 - columnGap
            else -> (maxWidth - ChannelRowEdgeInset * 2 - columnGap * 3) / 4
        }
        // Reserves belong to the existing gutter, rather than increasing the channel pitch.
        Row(
            Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(columnGap - ChannelRowEdgeInset * 2),
        ) {
            columns.forEachIndexed { c, column ->
                val list = lists[c]
                val listScroll = rememberListEdgeBringIntoView(list, TvSpacing12)
                Column(Modifier.width(width + ChannelRowEdgeInset * 2).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = ChannelRowEdgeInset + TvSpacing12, end = ChannelRowEdgeInset - TvSpacing12,
                        ).heightIn(min = TvSpacing32),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TvSpacing4),
                    ) {
                        PiconBox(column.channel.icon, Modifier.size(width = TvSpacing48, height = TvGridVerticalMargin))
                        Text(
                            "${column.channel.number}  ${column.channel.name}", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.padding(horizontal = ChannelRowEdgeInset).fillMaxWidth().height(TvCompactProgressStripHeight).drawBehind {
                        drawLine(line, Offset(0f, size.height), Offset(size.width + columnGap.toPx(), size.height), 1.dp.toPx())
                    }) {
                        if (focusedColumn == c) Box(Modifier.width(width).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    }
                    Box(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides listScroll) {
                            LazyColumn(
                                Modifier.fillMaxSize().listEdgeFades(list),
                                state = list,
                                contentPadding = PaddingValues(
                                    start = ChannelRowEdgeInset, end = ChannelRowEdgeInset,
                                    top = TvSpacing12, bottom = ListBottomReserve,
                                ),
                                // Picture-bearing rows grow more than the Channels list's short rows.
                                verticalArrangement = Arrangement.spacedBy(TvSpacing4),
                            ) {
                                itemsIndexed(column.entries, key = { _, entry -> entry.event.id.value }) { e, entry ->
                                    val start = entry.event.start.epochSeconds
                                    fun neighbor(index: Int): Int? =
                                        columns.getOrNull(index)?.entries?.indices
                                            ?.minByOrNull { abs(columns[index].entries[it].event.start.epochSeconds - start) }
                                    MagazineProgram(
                                        entry, nowSec, TvSpacing8,
                                        { onOpenProgram(entry.event) },
                                        Modifier.fillMaxWidth()
                                            .focusRequester(requests[c][e])
                                            .onPreviewKeyEvent { event ->
                                                val targetColumn = when (event.key) {
                                                    Key.DirectionLeft -> c - 1
                                                    Key.DirectionRight -> c + 1
                                                    else -> return@onPreviewKeyEvent false
                                                }
                                                if (targetColumn < 0) return@onPreviewKeyEvent false
                                                val target = neighbor(targetColumn)
                                                if (event.type == KeyEventType.KeyDown && target != null) {
                                                    scope.launch {
                                                        val targetList = lists[targetColumn]
                                                        if (targetList.layoutInfo.visibleItemsInfo.none { it.index == target }) {
                                                            targetList.scrollToItem(target)
                                                        }
                                                        withFrameNanos { }
                                                        requests[targetColumn][target].requestFocus()
                                                    }
                                                }
                                                true
                                            }
                                            .focusProperties {
                                                up = if (e == 0) headerFocus else FocusRequester.Default
                                                down = if (e == column.entries.lastIndex) FocusRequester.Cancel else FocusRequester.Default
                                                left = if (c == 0) navigationFocus else FocusRequester.Cancel
                                                right = FocusRequester.Cancel
                                            }
                                            .onFocusChanged { if (it.isFocused) focusedColumn = c },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MagazineProgram(
    entry: MagazineEntry,
    nowSec: Long,
    verticalPadding: Dp,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val event = entry.event
    val start = event.start.epochSeconds
    val end = event.stop.epochSeconds
    val live = nowSec >= start && nowSec < end
    val list = ListItemDefaults.colors()
    val listShape = ListItemDefaults.shape()
    val listScale = ListItemDefaults.scale()
    val art = programmeArtwork(event)?.takeIf { entry.priority }
    Surface(
        onClick = onOpen,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(
            shape = listShape.shape,
            focusedShape = listShape.focusedShape,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = list.contentColor,
            focusedContainerColor = list.focusedContainerColor, focusedContentColor = list.focusedContentColor,
        ),
        scale = ClickableSurfaceDefaults.scale(
            scale = listScale.scale, focusedScale = listScale.focusedScale, pressedScale = listScale.pressedScale,
        ),
    ) {
        Column(Modifier.padding(horizontal = TvSpacing12, vertical = verticalPadding)) {
            val minutes = ((if (live) end - nowSec else end - start) / 60).coerceAtLeast(0)
            val duration = formatDurationMinutes(minutes, LocalResources.current)
            val length = if (live) stringResource(R.string.player_minutes_left, duration) else duration
            Text(
                "${formatHm(start)} • $length",
                style = MaterialTheme.typography.bodySmall,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                color = if (live && !focused) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current.copy(alpha = TvTextSecondaryAlpha),
            )
            // A stable top-trailing mark works for both one- and two-line headlines.
            Row(verticalAlignment = Alignment.Top) {
                Text(event.title.orEmpty(), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    // Keep the list's title size, with the compact Guide/identity-title leading.
                    lineHeight = MaterialTheme.typography.titleSmall.lineHeight,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.recording?.let {
                    RecordingStatusIndicator(it, Modifier.padding(start = TvSpacing4),
                        focused = focused)
                }
            }
            if (art != null) {
                Spacer(Modifier.height(TvSpacing8))
                AsyncImage(
                    model = programmeArtworkModel(art, LocalArtworkContext.current.currentSession), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
        }
        if (live) ProgressStrip(
            ((nowSec - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f),
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = TvSpacing12),
            height = TvCompactProgressStripHeight,
        )
    }
}
