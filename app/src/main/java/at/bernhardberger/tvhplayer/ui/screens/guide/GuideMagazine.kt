package at.bernhardberger.tvhplayer.ui.screens.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import at.bernhardberger.tvhplayer.ui.components.InsetBringIntoViewSpec
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
) {
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
    val width = TvGridColumnWidth * 3 + TvGridGutter
    val platformScroll = LocalBringIntoViewSpec.current
    val reserve = with(LocalDensity.current) { TvSpacing12.toPx() }
    val listScroll = remember(platformScroll, reserve) {
        InsetBringIntoViewSpec(platformScroll, topInsetPx = reserve, bottomInsetPx = reserve)
    }
    // Reserves belong to the existing gutter, rather than increasing the channel pitch.
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TvGridGutter - ChannelRowEdgeInset * 2),
    ) {
        columns.forEachIndexed { c, column ->
            Column(Modifier.width(width + ChannelRowEdgeInset * 2).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ChannelRowEdgeInset).heightIn(min = TvSpacing32),
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
                    drawLine(line, Offset(0f, size.height), Offset(size.width + TvGridGutter.toPx(), size.height), 1.dp.toPx())
                }) {
                    if (focusedColumn == c) Box(Modifier.width(width).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
                Box(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides listScroll) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(horizontal = ChannelRowEdgeInset, vertical = TvSpacing12),
                            // Picture-bearing rows grow more than the Channels list's short rows.
                            verticalArrangement = Arrangement.spacedBy(TvSpacing12),
                        ) {
                            column.entries.forEachIndexed { e, entry ->
                                val start = entry.event.start.epochSeconds
                                fun neighbor(index: Int): FocusRequester =
                                    columns.getOrNull(index)?.entries?.indices
                                        ?.minByOrNull { abs(columns[index].entries[it].event.start.epochSeconds - start) }
                                        ?.let { requests[index][it] } ?: FocusRequester.Cancel
                                MagazineProgram(
                                    entry, nowSec, { onOpenProgram(entry.event) },
                                    Modifier.fillMaxWidth()
                                        .focusRequester(requests[c][e])
                                        .focusProperties {
                                            up = requests[c].getOrNull(e - 1) ?: headerFocus
                                            down = requests[c].getOrNull(e + 1) ?: FocusRequester.Cancel
                                            left = if (c == 0) navigationFocus else neighbor(c - 1)
                                            right = neighbor(c + 1)
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

@Composable
private fun MagazineProgram(
    entry: MagazineEntry,
    nowSec: Long,
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
        Column(Modifier.padding(TvSpacing12)) {
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
            val art = programmeArtwork(event)
            if (entry.priority && art != null) {
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
