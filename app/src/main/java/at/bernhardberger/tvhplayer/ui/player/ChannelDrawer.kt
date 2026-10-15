package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.ChannelNowIndicators
import at.bernhardberger.tvhplayer.ui.components.ChannelTitle
import at.bernhardberger.tvhplayer.ui.components.channelTitleText

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.CompactCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.embeddedProgressCardBorder
import coil3.ImageLoader
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
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation? = null,
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
    // 48dp screen-safe space plus native card enlargement/outline overflow.
    val edgeInset = 64.dp
    // The viewport reaches this far past both screen edges so the next cards of a held
    // D-pad are already composed and placed; otherwise each step past the edge runs a
    // synchronous beyond-bounds search inside key dispatch and the rail stalls.
    val offscreen = 440.dp
    val offscreenPx = with(LocalDensity.current) { offscreen.roundToPx() }
    val edgeInsetPx = with(LocalDensity.current) { edgeInset.toPx() } + offscreenPx
    // The rail keeps the focused card on the start keyline: the row scrolls, and at the list
    // end scrolling stops and focus moves on.
    val bringIntoView = remember(edgeInsetPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                offset - edgeInsetPx
        }
    }
    val pageActive = LocalPlayerPageActive.current
    val tabOwner = LocalTabOwner.current
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
            modifier = Modifier.layout { measurable, constraints ->
                check(constraints.hasBoundedWidth) { "The rail needs a bounded width" }
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minWidth + 2 * offscreenPx,
                        maxWidth = constraints.maxWidth + 2 * offscreenPx,
                    ),
                )
                layout((placeable.width - 2 * offscreenPx).coerceAtLeast(0), placeable.height) { placeable.place(-offscreenPx, 0) }
            },
            state = listState,
            // Room above and below the cards for the focus scale and border.
            contentPadding = PaddingValues(horizontal = edgeInset + offscreen, vertical = ChannelCardRoom),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
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
                    playbackIndicator = playbackIndicator.takeIf { channel.id == playbackChannelId } ?: ChannelPlaybackIndicator.NONE,
                    recording = channel.id in recordingChannelIds,
                    imageLoader = imageLoader,
                    currentSession = currentSession,
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
    onLongClick: (() -> Unit)? = null,
) {
    val contextMenu = at.bernhardberger.tvhplayer.ui.components.rememberBrowseContextMenuTrigger(onLongClick, menuKey = false)
    val currentEvent = event?.takeIf { it.start.epochSeconds <= nowSec && nowSec < it.stop.epochSeconds }
    // The programme leads; without one, the channel does.
    val programme = currentEvent?.title?.takeIf { it.isNotBlank() }
    val identity = channelTitleText(number, channel.name.orEmpty())
    // Preserve the kit's 54dp picon region; let the two text baselines grow with font scale.
    val cardHeight = with(LocalDensity.current) {
        70.dp + MaterialTheme.typography.titleMedium.lineHeight.toDp() + MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }
    CompactCard(
        onClick = onClick,
        onLongClick = contextMenu.onLongClick,
        border = embeddedProgressCardBorder(),
        // The kit scrim keeps text legible over artwork; this card's text sits on a flat surface.
        scrimBrush = SolidColor(Color.Transparent),
        modifier = modifier.then(contextMenu.modifier).width(196.dp),
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
                    text = programme ?: identity,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("player-channel-${channel.id.value}-${if (programme != null) "now" else "identity"}"),
                )
                // The new marker fades in; the old one leaves at once so two glyphs never
                // overlap in the slot and its tag and announcement go with it.
                AnimatedContent(
                    targetState = playbackIndicator,
                    transitionSpec = {
                        (fadeIn(tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardDecelerate)) togetherWith
                            ExitTransition.None).using(null)
                    },
                    label = "zap-card-marker",
                ) { indicator ->
                    if (!leaving) ChannelNowIndicators(playingNow = indicator == ChannelPlaybackIndicator.PLAYING,
                        recordingNow = recording, playbackIndicator = indicator)
                }
            }
        },
        subtitle = {
            // The picon already names the channel, so only its text may shorten; time left stays whole.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                Text(
                    text = if (programme != null) identity else stringResource(R.string.no_epg),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                        .testTag("player-channel-${channel.id.value}-${if (programme != null) "identity" else "now"}"),
                )
                if (programme != null && currentEvent != null) {
                    Text(
                        stringResource(R.string.quick_zap_minutes_left, ((currentEvent.stop.epochSeconds - nowSec + 59) / 60).toInt()),
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.testTag("player-channel-${channel.id.value}-left"),
                    )
                }
            }
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
