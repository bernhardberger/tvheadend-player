package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.ChannelTitle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import at.bernhardberger.tvhplayer.ui.components.LocalTabOwner
import at.bernhardberger.tvhplayer.ui.components.rememberTabListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.profiling.profileTrace
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import at.bernhardberger.tvhplayer.ui.components.CompactZapCard
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardGap
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardWidth
import at.bernhardberger.tvhplayer.ui.components.CompactZapRowOffscreen
import at.bernhardberger.tvhplayer.ui.components.StartKeylineBringIntoViewSpec
import at.bernhardberger.tvhplayer.ui.components.overflowingRowViewport
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

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
    active: Boolean = true,
    nowSec: Long = System.currentTimeMillis() / 1000L,
    onFocusChannel: (ChannelId) -> Unit,
    onPickChannel: (Channel) -> Unit,
    onCloseDrawer: (Int?) -> Unit,
    onLongClickChannel: ((Channel, EpgEvent) -> Unit)? = null,
    browserState: PlayerChannelCollectionState? = null,
) {
    val ids = remember(channels) { channels.map { it.id } }
    val numbers = remember(channels) { channels.associate { it.id to it.visibleChannelNumber } }
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    // Start on the entry card, rather than measure item 0 then discard it after scrollToItem.
    val initialId = browserState?.focusedId ?: playingChannelId ?: selectedId
    val localListState = rememberLazyListState(initialFirstVisibleItemIndex = ids.indexOf(initialId).coerceAtLeast(0))
    val listState = rememberTabListState(browserState?.listState ?: localListState)
    // A replaced collection (a new session) starts over rather than keep the old one's focus.
    var focusedId by remember(browserState) { mutableStateOf(initialId) }
    var entered by remember(browserState) { mutableStateOf(false) }
    var focusedCatalog by remember(browserState) { mutableStateOf(emptyList<ChannelId>()) }
    var observedPlayingId by remember { mutableStateOf(playingChannelId) }
    var pendingPickId by remember { mutableStateOf<ChannelId?>(null) }
    var confirmedId by remember { mutableStateOf(playingChannelId) }
    var reanchorId by remember { mutableStateOf<ChannelId?>(null) }
    val emptyFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // The kit's side margin, so four cards span its twelve columns like the browse template.
    val edgeInset = TvGridHorizontalMargin
    val offscreen = CompactZapRowOffscreen
    val offscreenPx = with(LocalDensity.current) { offscreen.roundToPx() }
    val edgeInsetPx = with(LocalDensity.current) { edgeInset.toPx() } + offscreenPx
    val bringIntoView = remember(edgeInsetPx) { StartKeylineBringIntoViewSpec(edgeInsetPx) }
    val pageActive = LocalPlayerPageActive.current
    val tabOwner = LocalTabOwner.current
    val peek = LocalChannelPeek.current
    val peekAnchor = LocalChannelPeekAnchor.current
    val cardRoomPx = with(LocalDensity.current) { ChannelCardRoom.toPx() }
    // Capture before disposal can automatically focus a neighbouring lazy item.
    val entryId = focusedId?.takeIf { it in ids }
        ?: playingChannelId?.takeIf { it in ids }
        ?: selectedId?.takeIf { it in ids } ?: ids.firstOrNull()
    // Read when called: a card's retained accessibility action outlives the composition that made it.
    val currentActive by rememberUpdatedState(active)
    val currentPageActive by rememberUpdatedState(pageActive)
    val attached = remember { booleanArrayOf(true) }
    DisposableEffect(Unit) { onDispose { attached[0] = false } }
    fun ownsInput() = attached[0] && currentActive && currentPageActive && tabOwner?.isCurrent != false
    LaunchedEffect(ids, active, playingChannelId, pageActive, browserState) {
        if (!ownsInput()) {
            entered = false
            // Once the closed rail rests, its peeking cards are the ones it opens on.
            val target = entryId
            if (!active && peek != null && target != null) {
                snapshotFlow { peek.resting }.first { it }
                snapshotFlow { listState.layoutInfo }
                    .first { it.totalItemsCount == ids.size && it.visibleItemsInfo.isNotEmpty() }
                if (browserState == null || listState.layoutInfo.visibleItemsInfo.none { it.key == target.value }) {
                    listState.scrollToItem(ids.indexOf(target))
                }
            }
            return@LaunchedEffect
        }
        if (browserState == null && playingChannelId != observedPlayingId) {
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
            browserState?.focusedId = null
            entered = false
            if (active) {
                withFrameNanos { }
                emptyFocus.requestFocus()
            }
            return@LaunchedEffect
        }
        if (entered && focusedId in ids && focusedCatalog == ids) return@LaunchedEffect
        val target = entryId ?: ids.first()
        focusedId = target
        browserState?.focusedId = target
        // Once the rail is laid out, the anchor moves to the start keyline.
        withFrameNanos { }
        snapshotFlow { listState.layoutInfo }
            .first { it.totalItemsCount == ids.size && it.visibleItemsInfo.isNotEmpty() }
        val index = ids.indexOf(target)
        if (browserState == null || listState.layoutInfo.visibleItemsInfo.none { it.key == target.value }) {
            listState.scrollToItem(index)
        }
        if (!ownsInput()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == target.value } }.first { it }
        withFrameNanos { }
        focusedCatalog = ids
        if (ownsInput()) requesters.getValue(target).requestFocus()
        entered = true
    }
    Column(
        Modifier.fillMaxWidth()
            .focusProperties { canFocus = ownsInput() }
            .then(if (!active) Modifier.clearAndSetSemantics { } else Modifier)
            .testTag("player-channel-shelf")
            .onPreviewKeyEvent { event ->
                if (!ownsInput()) return@onPreviewKeyEvent true
                val channelStep = ChannelNavigation.directionForKeyCode(event.nativeKeyEvent.keyCode)
                    .takeIf { active }
                when {
                    event.key == Key.DirectionUp -> {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            onCloseDrawer(event.nativeKeyEvent.keyCode)
                        }
                        true
                    }
                    channelStep != null -> {
                        if (event.type == KeyEventType.KeyDown) {
                            focusManager.moveFocus(if (channelStep > 0) FocusDirection.Right else FocusDirection.Left)
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        if (channels.isEmpty()) {
            Text(stringResource(R.string.empty_channel_tag), Modifier.padding(horizontal = 58.dp), color = MaterialTheme.colorScheme.onSurface)
            androidx.tv.material3.Button(
                onClick = { if (ownsInput()) onCloseDrawer(null) },
                modifier = Modifier.padding(start = 58.dp, end = 58.dp, top = 16.dp).focusRequester(emptyFocus).testTag("player-shelf-close"),
            ) { Text(stringResource(R.string.close)) }
        }
        // Insets belong to list content, not its viewport: adjacent cards travel to the screen edge.
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyRow(
            modifier = Modifier.overflowingRowViewport(offscreenPx)
                .then(if (peek != null && peekAnchor) Modifier.onGloballyPositioned { peek.cards(it, cardRoomPx) } else Modifier),
            state = listState,
            // Room above and below the cards for the focus scale and border.
            contentPadding = PaddingValues(horizontal = edgeInset + offscreen, vertical = ChannelCardRoom),
            horizontalArrangement = Arrangement.spacedBy(CompactZapCardGap),
        ) {
            items(channels, key = { it.id.value }) { channel ->
                // Details consume the opening release. Retire the native Card's held-click state
                // when input ownership leaves, without replacing the remembered row or viewport.
                androidx.compose.runtime.key(ownsInput()) {
                CompactZapCard(
                    channel = channel,
                    number = ChannelNavigation.numberForId(ids, numbers, channel.id),
                    event = nowEvent(channel.id),
                    nowSec = nowSec,
                    // The peek stays still: the sound bars start once the rail opens.
                    playbackIndicator = playbackIndicator.takeIf { channel.id == playbackChannelId && peek?.resting != true }
                        ?: ChannelPlaybackIndicator.NONE,
                    recording = channel.id in recordingChannelIds,
                    onClick = {
                        if (ownsInput()) {
                            if (channel.id != playingChannelId) pendingPickId = channel.id
                            reanchorId = null
                            onPickChannel(channel)
                        }
                    },
                    onLongClick = onLongClickChannel?.let { open -> {
                        if (ownsInput()) nowEvent(channel.id)?.takeIf {
                            it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds
                        }?.let { open(channel, it) }
                    } },
                    modifier = Modifier
                        .width(CompactZapCardWidth)
                        .focusProperties { canFocus = ownsInput() }
                        .testTag("player-channel-card-${channel.id.value}")
                        .focusRequester(requesters.getValue(channel.id))
                        .onFocusChanged {
                            if (it.isFocused && ownsInput() && (focusedCatalog.isEmpty() || focusedCatalog == ids)) {
                                profileTrace("P44:focus:rail") {
                                    focusedId = channel.id
                                    browserState?.focusedId = channel.id
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
}

