package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackMarker

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CompactCard
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.embeddedProgressCardBorder
import coil3.ImageLoader
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelDrawer(
    channels: List<Channel>,
    selectedId: ChannelId?,
    playingChannelId: ChannelId?,
    playbackChannelId: ChannelId? = playingChannelId,
    playbackIndicator: ChannelPlaybackIndicator = if (playingChannelId != null) ChannelPlaybackIndicator.PLAYING else ChannelPlaybackIndicator.NONE,
    recordingChannelIds: Set<ChannelId>,
    nowEvent: (ChannelId) -> EpgEvent?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    active: Boolean = true,
    nowSec: Long = System.currentTimeMillis() / 1000L,
    onFocusChannel: (ChannelId) -> Unit,
    onPickChannel: (Channel) -> Unit,
    onCloseDrawer: (Int?) -> Unit,
) {
    val ids = remember(channels) { channels.map { it.id } }
    val numbers = remember(channels) { channels.associate { it.id to it.visibleChannelNumber } }
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    val listState = rememberLazyListState()
    var focusedId by remember { mutableStateOf(playingChannelId ?: selectedId) }
    var entered by remember { mutableStateOf(false) }
    var focusedCatalog by remember { mutableStateOf(emptyList<ChannelId>()) }
    var observedPlayingId by remember { mutableStateOf(playingChannelId) }
    var pendingPickId by remember { mutableStateOf<ChannelId?>(null) }
    var confirmedId by remember { mutableStateOf(playingChannelId) }
    var reanchorId by remember { mutableStateOf<ChannelId?>(null) }
    val emptyFocus = remember { FocusRequester() }
    // 48dp screen-safe space plus native card enlargement/outline overflow.
    val edgeInset = 64.dp
    val edgeInsetPx = with(LocalDensity.current) { edgeInset.toPx() }
    val bringIntoView = remember(edgeInsetPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
                offset < edgeInsetPx -> offset - edgeInsetPx
                offset + size > containerSize - edgeInsetPx -> offset + size - containerSize + edgeInsetPx
                else -> 0f
            }
        }
    }
    LaunchedEffect(ids, active, playingChannelId) {
        if (playingChannelId != observedPlayingId) {
            // Null means awaiting presentation, not a different channel selection.
            // Preserve the local pick until a non-null confirmation resolves it.
            if (playingChannelId != null) {
                // A delayed confirmation of our own pick is not an external channel change,
                // nor is re-confirming the same channel after a failed or recovering tune.
                // An external change while open must not steal browse focus; it re-anchors
                // the rail when it closes.
                if (playingChannelId != pendingPickId && playingChannelId != confirmedId) {
                    if (active) reanchorId = playingChannelId else {
                        focusedId = playingChannelId
                        reanchorId = null
                    }
                }
                pendingPickId = null
                confirmedId = playingChannelId
            }
            observedPlayingId = playingChannelId
        }
        if (!active) {
            entered = false
            reanchorId?.let { focusedId = it }
            reanchorId = null
        }
        if (ids.isEmpty()) {
            entered = false
            if (active) {
                withFrameNanos { }
                emptyFocus.requestFocus()
            }
            return@LaunchedEffect
        }
        if (entered && focusedId in ids && focusedCatalog == ids) return@LaunchedEffect
        val target = focusedId?.takeIf { it in ids }
            ?: playingChannelId?.takeIf { it in ids }
            ?: selectedId?.takeIf { it in ids } ?: ids.first()
        focusedId = target
        // Keep the persistent viewport when the anchor is already composed. Focus's
        // safe-edge BringIntoViewSpec handles partially visible cards without left-snapping.
        withFrameNanos { }
        val layout = snapshotFlow { listState.layoutInfo }
            .first { it.totalItemsCount == ids.size && it.visibleItemsInfo.isNotEmpty() }
        if (layout.visibleItemsInfo.none { it.key == target.value }) {
            val index = ids.indexOf(target)
            val lastVisible = layout.visibleItemsInfo.last()
            val offset = if (index > lastVisible.index) {
                // Enter from the nearest (right) safe edge, not the left edge.
                -(layout.viewportEndOffset - layout.afterContentPadding - lastVisible.size).coerceAtLeast(0)
            } else 0
            listState.scrollToItem(index, offset)
        }
        if (!active) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == target.value } }.first { it }
        withFrameNanos { }
        requesters.getValue(target).requestFocus()
        focusedCatalog = ids
        entered = true
    }
    Column(
        Modifier.fillMaxWidth()
            .focusProperties { canFocus = active }
            .then(if (!active) Modifier.clearAndSetSemantics { } else Modifier)
            .testTag("player-channel-shelf")
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionUp) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        onCloseDrawer(event.nativeKeyEvent.keyCode)
                    }
                    true
                } else false
            },
    ) {
        if (channels.isEmpty()) {
            Text(stringResource(R.string.empty_channel_tag), Modifier.padding(horizontal = 48.dp), color = MaterialTheme.colorScheme.onSurface)
            androidx.tv.material3.Button(
                onClick = { onCloseDrawer(null) },
                modifier = Modifier.padding(horizontal = 48.dp).focusRequester(emptyFocus).testTag("player-shelf-close"),
            ) { Text(stringResource(R.string.close)) }
        }
        // Insets belong to list content, not its viewport: adjacent cards travel to the screen edge.
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = edgeInset, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(channels, key = { it.id.value }) { channel ->
                CompactZapCard(
                    channel = channel,
                    number = ChannelNavigation.numberForId(ids, numbers, channel.id),
                    event = nowEvent(channel.id),
                    nowSec = nowSec,
                    playbackIndicator = playbackIndicator.takeIf { channel.id == playbackChannelId } ?: ChannelPlaybackIndicator.NONE,
                    recording = channel.id in recordingChannelIds,
                    imageLoader = imageLoader,
                    currentSession = currentSession,
                    onClick = {
                        if (active) {
                            if (channel.id != playingChannelId) pendingPickId = channel.id
                            reanchorId = null
                            onPickChannel(channel)
                        }
                    },
                    modifier = Modifier
                        .focusProperties { canFocus = active }
                        .testTag("player-channel-card-${channel.id.value}")
                        .focusRequester(requesters.getValue(channel.id))
                        .onFocusChanged {
                            if (it.isFocused && active) {
                                profileTrace("P44:focus:rail") {
                                    focusedId = channel.id
                                    onFocusChannel(channel.id)
                                }
                            }
                        },
                )
            }
        }
        }
    }
}

@Composable
private fun CompactZapCard(
    channel: Channel,
    number: Long?,
    event: EpgEvent?,
    nowSec: Long,
    playbackIndicator: ChannelPlaybackIndicator,
    recording: Boolean,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentEvent = event?.takeIf { it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds }
    // Preserve the kit's 54dp picon region; let the two text baselines grow with font scale.
    val cardHeight = with(LocalDensity.current) {
        70.dp + MaterialTheme.typography.titleMedium.lineHeight.toDp() + MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }
    CompactCard(
        onClick = onClick,
        border = embeddedProgressCardBorder(),
        modifier = modifier.width(196.dp),
        image = {
            Box(Modifier.fillMaxWidth().height(cardHeight).background(TvSurfaceColors.containerLowest)) {
                PiconBox(
                    imageLoader = imageLoader, currentSession = currentSession, piconPath = channel.icon,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(100.dp, 45.dp)
                        .testTag("player-channel-${channel.id.value}-picon"),
                )
            }
        },
        title = {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOfNotNull(number?.toString(), channel.name).joinToString(" "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("player-channel-${channel.id.value}-identity"),
                )
                ChannelPlaybackMarker(playbackIndicator, size = 16.dp)
                if (recording) Icon(painterResource(R.drawable.ic_fiber_manual_record),
                    contentDescription = stringResource(R.string.player_shelf_recording),
                    tint = TvRecordingColor, modifier = Modifier.size(12.dp))
            }
        },
        subtitle = {
            val title = currentEvent?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_epg)
            val subtitle = if (currentEvent == null) title else {
                "${formatClock(currentEvent.start.epochSeconds)} · $title" +
                    stringResource(R.string.quick_zap_minutes_left, ((currentEvent.stop.epochSeconds - nowSec + 59) / 60).toInt())
            }
            Text(
                subtitle,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)
                    .testTag("player-channel-${channel.id.value}-now"),
            )
        },
        description = {
            if (currentEvent != null) {
                ProgressStrip(
                    progress = ((nowSec - currentEvent.start.epochSeconds).toDouble() /
                        (currentEvent.stop.epochSeconds - currentEvent.start.epochSeconds)).toFloat(),
                    height = 2.dp,
                    modifier = Modifier.fillMaxWidth().testTag("player-channel-${channel.id.value}-progress"),
                )
            } else Box(Modifier.fillMaxWidth().height(2.dp))
        },
    )
}
