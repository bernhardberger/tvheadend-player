package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatHm
import at.bernhardberger.tvhplayer.ui.common.formatDayHeader
import at.bernhardberger.tvhplayer.ui.common.formatDayMonth
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Width-independent schedule column: its caller owns navigation, data and the surrounding sheet. */
@Composable
internal fun ChannelSchedule(
    channel: Channel?, events: List<EpgEvent>, nowSec: Long, viewerSec: Long?,
    loading: Boolean, offline: Boolean,
    recording: (EpgEvent) -> DvrEntryState?,
    onOpen: (EpgEvent) -> Unit, onClose: () -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initialEvent: EpgEvent? = scheduleInitialEvent(events, viewerSec, nowSec),
    onFocused: (EpgEvent) -> Unit = {},
    onContextMenu: ((EpgEvent) -> Unit)? = null,
) {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    fun date(event: EpgEvent) = Instant.ofEpochSecond(event.start.epochSeconds).atZone(zone).toLocalDate()
    val days = remember(events, zone) { events.map(::date).distinct() }
    val initialIndex = events.indexOfFirst { it.id == initialEvent?.id }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val scope = rememberCoroutineScope()
    val rowFocus = remember(events.map { it.id }) { events.associate { it.id to FocusRequester() } }
    val selectedTabFocus = remember { FocusRequester() }
    val emptyFocus = remember { FocusRequester() }
    var selectedDay by remember { mutableStateOf(initialEvent?.let(::date)) }
    var entered by remember { mutableStateOf(false) }
    val channelName = channel?.name ?: stringResource(R.string.epg_search_unknown_channel)
    val title = stringResource(R.string.channel_schedule_pane, channelName)
    LaunchedEffect(loading, events.isEmpty()) {
        if (events.isEmpty() || !entered) {
            withFrameNanos { }
            if (events.isNotEmpty()) {
                listState.scrollToItem(initialIndex)
                withFrameNanos { }
                entered = rowFocus[events[initialIndex].id]?.requestFocus() == true
            } else { entered = false; emptyFocus.requestFocus() }
        }
    }
    Column(modifier.focusGroup().semantics { paneTitle = title }
        .padding(horizontal = 12.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PiconBox(channel?.icon, Modifier.size(56.dp, 32.dp))
            Text(listOfNotNull(channel?.number?.toString(), channelName).joinToString(" "),
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium)
            Text(formatClock(nowSec), style = MaterialTheme.typography.bodyMedium)
        }
        if (offline) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.channel_schedule_offline), Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        if (events.isEmpty()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(if (loading) R.string.channel_schedule_loading else R.string.no_epg),
                    Modifier.then(if (loading) Modifier.focusRequester(emptyFocus).focusable() else Modifier))
                if (loading) repeat(4) {
                    Box(Modifier.fillMaxWidth().height(48.dp).clip(MaterialTheme.shapes.small)
                        .background(TvSurfaceColors.containerHighest))
                } else {
                    Text(stringResource(R.string.channel_schedule_empty), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onClose, modifier = Modifier.focusRequester(emptyFocus)) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        } else {
            val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
            AppTabRow(days.indexOf(selectedDay).coerceAtLeast(0), AppTabStyle.Section,
                selectedTabFocus = selectedTabFocus,
                onMoveToContent = {
                    val target = if (selectedDay == today) scheduleInitialEvent(events.filter { date(it) == today }, viewerSec, nowSec)
                        else events.firstOrNull { date(it) == selectedDay }
                    target?.let { event -> scope.launch {
                        listState.scrollToItem(events.indexOf(event))
                        withFrameNanos { }
                        rowFocus[event.id]?.requestFocus()
                    } }
                    true
                }) {
                days.forEachIndexed { index, day ->
                    val target = if (day == today) scheduleInitialEvent(events.filter { date(it) == day }, viewerSec, nowSec)
                        else events.firstOrNull { date(it) == day }
                    AppTab(selected = selectedDay == day,
                        onFocus = {
                                selectedDay = day
                                target?.let { event -> scope.launch { listState.scrollToItem(events.indexOf(event)) } }
                        }, first = index == 0, last = index == days.lastIndex,
                        modifier = Modifier.testTag("schedule-tab-$day"),
                        label = when (day) {
                            today -> stringResource(R.string.channel_schedule_today)
                            today.plusDays(1) -> stringResource(R.string.channel_schedule_tomorrow)
                            else -> day.format(DateTimeFormatter.ofPattern("EEE d", locale))
                        })
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("channel-schedule-list"),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(events, key = { _, event -> event.id.value }) { index, event ->
                    val day = date(event)
                    val previous = events.getOrNull(index - 1)
                    if (previous == null || date(previous) != day) Text(
                        formatDayHeader(day, locale),
                        Modifier.padding(12.dp).semantics { heading() }, style = MaterialTheme.typography.labelMedium)
                    if (previous != null && previous.stop < event.start) Text(
                        stringResource(R.string.channel_schedule_gap,
                            (if (date(previous) != day) "${formatDayMonth(date(previous), locale)} " else "") + formatHm(previous.stop.epochSeconds),
                            formatHm(event.start.epochSeconds)),
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    ChannelScheduleRow(event, nowSec, viewerSec, recording(event), onClick = { onOpen(event) },
                        onContextMenu = onContextMenu?.let { open -> { open(event) } },
                         modifier = Modifier.focusRequester(rowFocus.getValue(event.id))
                             .onPreviewKeyEvent { key ->
                                 if (index == events.lastIndex && key.key == Key.DirectionDown) {
                                     if (key.type == KeyEventType.KeyDown) scope.launch { listState.animateScrollToItem(index) }
                                     true
                                 } else false
                             }
                            .focusProperties {
                                left = FocusRequester.Cancel; right = FocusRequester.Cancel
                                 if (index == 0) up = selectedTabFocus
                                 if (index == events.lastIndex) down = FocusRequester.Cancel
                            }
                            .onFocusChanged { if (it.isFocused) { selectedDay = day; onFocused(event) } })
                }
                item { Text(stringResource(R.string.channel_schedule_end,
                    days.last().format(DateTimeFormatter.ofPattern("EEE d", locale))),
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ChannelScheduleRow(
    event: EpgEvent, nowSec: Long, viewerSec: Long?, recording: DvrEntryState?,
    onClick: () -> Unit, modifier: Modifier = Modifier,
    onContextMenu: (() -> Unit)? = null,
) {
    val contextMenu = rememberBrowseContextMenuTrigger(onContextMenu)
    val start = event.start.epochSeconds
    val stop = event.stop.epochSeconds
    val onAir = nowSec in start until stop
    val watched = viewerSec != null && viewerSec in start until stop
    val state = when {
        onAir -> stringResource(R.string.details_status_on_now)
        stop <= nowSec -> stringResource(R.string.channel_schedule_ended)
        else -> ""
    }
    Surface(onClick = onClick, onLongClick = contextMenu.onLongClick,
        modifier = modifier.then(contextMenu.modifier).fillMaxWidth().testTag("schedule-row-${event.id.value}"),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (stop <= nowSec) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val session = LocalArtworkContext.current.currentSession
            Box(Modifier.size(80.dp, 45.dp).clip(MaterialTheme.shapes.small).background(TvSurfaceColors.containerLowest)) {
                if (event.image != null && session != null) AsyncImage(AppArtworkSource(session, event.image!!), null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatHm(start), style = MaterialTheme.typography.bodyMedium)
                    Text(event.title.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    if (watched) ChannelPlaybackMarker(channelWatchingIndicator(event.channelId))
                    if (recording == DvrEntryState.SCHEDULED) ScheduledIndicator() else RecordingBadge(recording, label = false, compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val behind = if (watched && viewerSec != null && nowSec - viewerSec > 5)
                        stringResource(R.string.timeshift_behind_live, formatPlaybackDuration((nowSec - viewerSec) * 1000)) else ""
                    Text(listOf(stringResource(R.string.recordings_until, formatHm(stop)), state, behind).filter { it.isNotEmpty() }.joinToString(" · "),
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (onAir || watched) ProgressStrip(
                    (((if (watched) viewerSec!! else nowSec) - start).toFloat() / (stop - start)).coerceIn(0f, 1f),
                    Modifier.fillMaxWidth(), height = 3.dp,
                    color = if (watched) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** Unknown viewer timing falls back to on air; a schedule gap chooses the next programme. */
internal fun scheduleInitialEvent(events: List<EpgEvent>, viewerSec: Long?, nowSec: Long): EpgEvent? {
    val at = viewerSec ?: nowSec
    return events.firstOrNull { it.start.epochSeconds <= at && at < it.stop.epochSeconds }
        ?: events.firstOrNull { it.start.epochSeconds >= at } ?: events.lastOrNull()
}
