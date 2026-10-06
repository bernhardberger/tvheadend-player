package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.embeddedProgressCardBorder
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Cached metadata is read only for composed rows; acquisition never gates focus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelRows(
    channels: List<Channel>,
    playingChannelId: ChannelId?,
    nowSec: Long,
    schedule: (ChannelId) -> List<EpgEvent>,
    acquireSchedule: suspend (ChannelId) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    onPickChannel: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    val ids = remember(channels) { channels.map { it.id } }
    var selected by remember(ids) { mutableIntStateOf(ids.indexOf(playingChannelId).coerceAtLeast(0)) }
    val vertical = rememberLazyListState(selected)
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    val emptyFocus = remember { FocusRequester() }
    val horizontalBringIntoView = LocalBringIntoViewSpec.current
    // Vertical positioning belongs to this surface, not nested bring-into-view requests.
    val anchored = remember { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
    } }
    LaunchedEffect(selected, ids) {
        if (ids.isEmpty()) emptyFocus.requestFocus()
        else {
            vertical.scrollToItem(selected)
            withFrameNanos { }
            requesters.getValue(ids[selected]).requestFocus()
        }
    }
    Column(Modifier.fillMaxSize()
        .testTag("player-channel-rows")
        .onPreviewKeyEvent { event ->
            when (event.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown && ids.isNotEmpty()) {
                        selected = (selected + if (event.key == Key.DirectionDown) 1 else -1).coerceIn(ids.indices)
                    }
                    true
                }
                Key.Back -> {
                    if (event.type == KeyEventType.KeyUp) onClose()
                    true
                }
                else -> false
            }
        }) {
        Spacer(Modifier.height(64.dp))
        if (channels.isEmpty()) {
            Column(Modifier.padding(PlayerChromeTokens.gridMargin)) {
                Text(stringResource(R.string.empty_channel_tag), color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = onClose, modifier = Modifier.focusRequester(emptyFocus)) { Text(stringResource(R.string.close)) }
            }
        } else CompositionLocalProvider(LocalBringIntoViewSpec provides anchored) {
            LazyColumn(
                state = vertical,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 300.dp),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id.value }) { index, channel ->
                    ChannelProgrammeRow(channel, index == selected, index < selected, requesters.getValue(channel.id),
                        nowSec, schedule(channel.id), acquireSchedule, imageLoader, currentSession, horizontalBringIntoView,
                        onPickChannel = { onPickChannel(channel) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelProgrammeRow(
    channel: Channel,
    selected: Boolean,
    hiddenAbove: Boolean,
    firstFocus: FocusRequester,
    nowSec: Long,
    events: List<EpgEvent>,
    acquireSchedule: suspend (ChannelId) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    horizontalBringIntoView: BringIntoViewSpec,
    onPickChannel: () -> Unit,
) {
    var loading by remember(channel.id, currentSession) { mutableStateOf(true) }
    var failed by remember(channel.id, currentSession) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val acquire by rememberUpdatedState(acquireSchedule)
    LaunchedEffect(channel.id, currentSession, retry) {
        loading = true
        failed = false
        try {
            failed = withTimeoutOrNull(10_000) { acquire(channel.id); false } ?: true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally { loading = false }
    }
    val horizontal = rememberLazyListState()
    LaunchedEffect(selected) { if (selected) horizontal.scrollToItem(0) }
    val identity = listOfNotNull(channel.visibleChannelNumber?.toString(), channel.name).joinToString(" · ")
    val now = events.firstOrNull { it.start.epochSeconds <= nowSec && it.stop.epochSeconds > nowSec }
    val tiles = listOf(now) + events.filter { it.start.epochSeconds > nowSec }
    val status = when {
        loading -> R.string.channel_rows_loading
        failed -> R.string.channel_rows_error
        else -> R.string.channel_rows_no_programme
    }
    // Previous rows leave above the fixed focus line, never intruding into the top strip.
    Column(Modifier.fillMaxWidth().height(216.dp).graphicsLayer { alpha = if (hiddenAbove) 0f else 1f }) {
        Row(Modifier.padding(horizontal = PlayerChromeTokens.gridMargin).height(28.dp)
            .semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel.icon != null) PiconBox(imageLoader = imageLoader, currentSession = currentSession,
                piconPath = channel.icon, modifier = Modifier.size(32.dp, 24.dp))
            Text(identity, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoView) {
        LazyRow(state = horizontal, contentPadding = PaddingValues(horizontal = PlayerChromeTokens.gridMargin, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(PlayerChromeTokens.gridGutter)) {
            itemsIndexed(tiles, key = { index, event -> if (index == 0) "now" else event!!.id.value.toString() }) { index, event ->
                val title = event?.title ?: stringResource(status)
                val time = event?.let { "${formatClock(it.start.epochSeconds)} – ${formatClock(it.stop.epochSeconds)}" }.orEmpty()
                val tag = "player-channel-row-${channel.id.value}-tile-$index"
                Column(Modifier.width(PlayerChromeTokens.channelCardWidth)) {
                Card(
                    onClick = { if (index == 0) onPickChannel() },
                    border = embeddedProgressCardBorder(),
                    modifier = Modifier.size(PlayerChromeTokens.channelCardWidth, PlayerChromeTokens.channelCardHeight)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .focusProperties { left = if (index == 0) FocusRequester.Cancel else FocusRequester.Default
                            right = if (index == tiles.lastIndex && !failed) FocusRequester.Cancel else FocusRequester.Default }
                        .semantics { contentDescription = "$identity, $title, $time" }
                        .testTag(tag),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val artwork = ArtworkId.parse(event?.image)
                        var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
                        ChannelCardFace(channel.icon, channel.id, channel.name.orEmpty(), imageLoader, currentSession,
                            logoTag = "$tag-picon", nameTag = "$tag-channel-name", lift = 0.dp)
                        if (artwork != null && currentSession != null && !artworkFailed) AsyncImage(
                            model = AppArtworkSource(currentSession, artwork), contentDescription = null,
                            imageLoader = imageLoader, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(), onError = { artworkFailed = true })
                        if (index == 0 && event != null) ProgressStrip(
                            progress = ((nowSec - event.start.epochSeconds).toFloat() /
                                (event.stop.epochSeconds - event.start.epochSeconds).coerceAtLeast(1)).coerceIn(0f, 1f),
                            height = 3.dp, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                    }
                }
                Text(title, Modifier.padding(top = 10.dp).testTag("$tag-title"),
                    color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(time, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (failed) item {
                Button(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
        }
        }
    }
}
