package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.profiling.profileTrace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.tv.material3.Icon
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import coil3.ImageLoader
import kotlinx.coroutines.flow.first

@Composable
fun ChannelDrawer(
    channels: List<Channel>,
    selectedId: ChannelId?,
    playingChannelId: ChannelId?,
    recordingChannelIds: Set<ChannelId>,
    nowEvent: (ChannelId) -> EpgEvent?,
    nextEvent: (ChannelId) -> EpgEvent?,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
    active: Boolean = true,
    onFocusChannel: (ChannelId) -> Unit,
    onPickChannel: (Channel) -> Unit,
    onCloseDrawer: (Int?) -> Unit,
) {
    val ids = remember(channels) { channels.map { it.id } }
    val numbers = remember(channels) { channels.associate { it.id to it.number?.toInt() } }
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    val listState = rememberLazyListState()
    var focusedId by remember { mutableStateOf(playingChannelId ?: selectedId) }
    var entered by remember { mutableStateOf(false) }
    val emptyFocus = remember { FocusRequester() }
    LaunchedEffect(ids, active) {
        if (!active) {
            entered = false
            return@LaunchedEffect
        }
        if (ids.isEmpty()) {
            withFrameNanos { }
            emptyFocus.requestFocus()
            return@LaunchedEffect
        }
        if (entered && focusedId in ids) return@LaunchedEffect
        val target = (if (!entered) playingChannelId else focusedId)?.takeIf { it in ids }
            ?: selectedId?.takeIf { it in ids } ?: ids.first()
        focusedId = target
        listState.scrollToItem(ids.indexOf(target))
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == target.value } }.first { it }
        withFrameNanos { }
        requesters.getValue(target).requestFocus()
        entered = true
    }
    val cardHeight = with(LocalDensity.current) {
        40.dp + maxOf(64.dp, MaterialTheme.typography.titleMedium.lineHeight.toDp() +
            maxOf(24.dp, MaterialTheme.typography.labelMedium.lineHeight.toDp())) +
            MaterialTheme.typography.bodyMedium.lineHeight.toDp() +
            MaterialTheme.typography.labelMedium.lineHeight.toDp() * 2
    }
    Column(
        Modifier.fillMaxWidth()
            .focusProperties { canFocus = active }
            .padding(top = 8.dp).testTag("player-channel-shelf")
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionUp) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onCloseDrawer(event.nativeKeyEvent.keyCode)
                    true
                } else false
            },
    ) {
        if (channels.isEmpty()) {
            Text(stringResource(R.string.empty_channel_tag), Modifier.padding(horizontal = 48.dp), color = MaterialTheme.colorScheme.onSurface)
            androidx.tv.material3.Button(
                onClick = { onCloseDrawer(null) },
                modifier = Modifier.padding(horizontal = 56.dp).focusRequester(emptyFocus).testTag("player-shelf-close"),
            ) { Text(stringResource(R.string.close)) }
        }
        LazyRow(state = listState, contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(channels, key = { it.id.value }) { channel ->
                Card(
                    onClick = { onPickChannel(channel) },
                    colors = androidx.tv.material3.CardDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        focusedContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = androidx.tv.material3.CardDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                        ),
                    ),
                    scale = androidx.tv.material3.CardDefaults.scale(focusedScale = 1f),
                    modifier = Modifier
                        .width(288.dp)
                        .heightIn(min = cardHeight)
                        .testTag("player-channel-card-${channel.id.value}")
                        .focusRequester(requesters.getValue(channel.id))
                        .onFocusChanged {
                            if (it.isFocused) {
                                profileTrace("P44:focus:rail") {
                                    focusedId = channel.id
                                    onFocusChannel(channel.id)
                                }
                            }
                        },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PiconBox(imageLoader = imageLoader, currentSession = currentSession,
                                piconPath = channel.icon, modifier = Modifier.size(96.dp, 64.dp)
                                    .testTag("player-channel-${channel.id.value}-picon").padding(4.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Row(Modifier.height(with(LocalDensity.current) { maxOf(24.dp, MaterialTheme.typography.labelMedium.lineHeight.toDp()) }),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(ChannelNavigation.numberForId(ids, numbers, channel.id)?.toString().orEmpty(),
                                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f), maxLines = 1)
                                    if (channel.id == playingChannelId) Icon(Icons.Filled.PlayArrow,
                                        contentDescription = stringResource(R.string.player_shelf_playing),
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    if (channel.id in recordingChannelIds) Icon(Icons.Filled.FiberManualRecord,
                                        contentDescription = stringResource(R.string.player_shelf_recording),
                                        tint = at.bernhardberger.tvhplayer.ui.TvRecordingColor, modifier = Modifier.size(16.dp))
                                }
                                Text(channel.name.orEmpty(),
                                    modifier = Modifier.testTag("player-channel-${channel.id.value}-identity"),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val now = nowEvent(channel.id)
                        val next = nextEvent(channel.id)
                        val nowRange = now?.let { "${formatClock(it.start.epochSeconds)} - ${formatClock(it.stop.epochSeconds)}" }.orEmpty()
                        Column {
                            Text("${stringResource(R.string.now)} $nowRange".trim(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(now?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_epg),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("player-channel-${channel.id.value}-now"))
                        }
                        val nextRange = next?.let { "${formatClock(it.start.epochSeconds)} - ${formatClock(it.stop.epochSeconds)}" }.orEmpty()
                        val nextTitle = next?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_epg)
                        val nextDescription = stringResource(R.string.player_next_event_with_range, nextRange, nextTitle)
                        Text(
                            text = if (next != null) stringResource(R.string.player_next_event_with_range,
                                formatClock(next.start.epochSeconds), nextTitle) else nextTitle,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("player-channel-${channel.id.value}-next")
                                .semantics { contentDescription = nextDescription },
                        )
                    }
                }
            }
        }
    }
}
