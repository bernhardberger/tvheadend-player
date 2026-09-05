package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
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
    LaunchedEffect(ids) {
        if (ids.isEmpty()) {
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
    val focused = channels.firstOrNull { it.id == focusedId }
    val now = focused?.let { nowEvent(it.id) }
    val next = focused?.let { nextEvent(it.id) }
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(
            0f to Color.Transparent, 0.18f to Color.Black.copy(alpha = 0.94f), 1f to Color.Black,
        ))
            .padding(top = 32.dp, bottom = 32.dp).testTag("player-channel-shelf")
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionUp) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onCloseDrawer(event.nativeKeyEvent.keyCode)
                    true
                } else false
            },
    ) {
        Column(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 56.dp)) {
            Text(focused?.name.orEmpty(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            Text(now?.let { "${formatClock(it.start.epochSeconds)} - ${formatClock(it.stop.epochSeconds)}  ${it.title.orEmpty()}" }
                ?: stringResource(R.string.no_epg), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            next?.let {
                Text(stringResource(R.string.player_next_event_with_range,
                    "${formatClock(it.start.epochSeconds)} - ${formatClock(it.stop.epochSeconds)}", it.title.orEmpty()),
                    color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (channels.isEmpty()) {
            Text(stringResource(R.string.empty_channel_tag), Modifier.padding(horizontal = 48.dp), color = MaterialTheme.colorScheme.onSurface)
            androidx.tv.material3.Button(
                onClick = { onCloseDrawer(null) },
                modifier = Modifier.padding(horizontal = 48.dp).focusRequester(emptyFocus),
            ) { Text(stringResource(R.string.close)) }
        }
        LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 56.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(channels, key = { it.id.value }) { channel ->
                Card(onClick = { onPickChannel(channel) },
                     border = androidx.tv.material3.CardDefaults.border(
                         focusedBorder = androidx.tv.material3.Border(
                             androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                         ),
                     ),
                    modifier = Modifier.width(184.dp).height(112.dp)
                        .focusRequester(requesters.getValue(channel.id))
                        .onFocusChanged {
                            if (it.isFocused) { focusedId = channel.id; onFocusChannel(channel.id) }
                        }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PiconBox(imageLoader = imageLoader, currentSession = currentSession,
                            piconPath = channel.icon, modifier = Modifier.size(52.dp, 36.dp))
                        Text(channelTitleText(ChannelNavigation.numberForId(ids, numbers, channel.id), channel.name.orEmpty()),
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (channel.id == playingChannelId) Text(stringResource(R.string.player_shelf_playing), style = MaterialTheme.typography.labelSmall)
                            if (channel.id in recordingChannelIds) Text(stringResource(R.string.player_shelf_recording),
                                color = at.bernhardberger.tvhplayer.ui.TvRecordingColor, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
