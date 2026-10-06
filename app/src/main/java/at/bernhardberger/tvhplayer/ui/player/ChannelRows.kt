package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
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

/** Trial: a programme tile two grid columns and their gutter wide (124dp), 16:9; six fill the content width. */
internal val ChannelRowTileWidth =
    (960.dp - PlayerChromeTokens.gridMargin * 2 - PlayerChromeTokens.gridGutter * 11) / 12 * 2 + PlayerChromeTokens.gridGutter
internal val ChannelRowTileHeight = 70.dp
/** One row: 24dp header, 8dp focus room, tile, 6dp gap, title and subtitle lines, 8dp focus room, 8dp gap. */
internal val ChannelRowHeight = 160.dp
/** Rows start below the now-playing strip and clock line. */
internal val ChannelRowsTop = 80.dp
/** The focused row's fixed line inside the rows; the previous row shows above it, partly. */
internal val ChannelRowsAnchor = 104.dp
internal const val ChannelRowDimAlpha = 0.42f
/** Rows rise one after another, each over one panel step: previous, focused, next, the one after. */
internal const val ChannelRowStaggerMs = PlayerMotion.FastMs / 2
internal const val ChannelRowsRevealMs = PlayerMotion.PanelMs + 3 * ChannelRowStaggerMs

/** The linear open/close clock of the rows, 0 to 1 over [ChannelRowsRevealMs]; fully open by default. */
internal val LocalChannelRowsReveal = compositionLocalOf<() -> Float> { { 1f } }

/** How far the row at [ordinal] (0 = the row above the focused one) has risen at linear [reveal]. */
internal fun channelRowRise(reveal: Float, ordinal: Int): Float {
    val ms = reveal * ChannelRowsRevealMs - ordinal.coerceIn(0, 3) * ChannelRowStaggerMs
    return PlayerMotion.EmphasizedDecelerate.transform((ms / PlayerMotion.PanelMs).coerceIn(0f, 1f))
}

/** The tile's second line: the subtitle, else the short summary, else the description's first line. */
internal fun channelRowSubtitle(event: EpgEvent?): String? = event?.let {
    listOf(it.subtitle, it.summary, it.description?.lineSequence()?.firstOrNull())
        .firstOrNull { line -> !line.isNullOrBlank() }?.trim()
}

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
    /** [keyCode] is the key that closed the rows on its press, for its release and repeats to be absorbed. */
    onClose: (keyCode: Int?) -> Unit,
    onOpenDetails: (Channel, EpgEvent) -> Unit = { _, _ -> },
    /** False while a layer above owns focus; becoming true again restores focus on the selected row. */
    active: Boolean = true,
) {
    val ids = remember(channels) { channels.map { it.id } }
    var selected by remember(ids) { mutableIntStateOf(ids.indexOf(playingChannelId).coerceAtLeast(0)) }
    val vertical = rememberLazyListState(selected)
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    val emptyFocus = remember { FocusRequester() }
    // The programme whose details opened over the rows takes focus again when they close.
    var detailsTile by remember { mutableStateOf<Pair<ChannelId, Int>?>(null) }
    val detailsReturn = remember { FocusRequester() }
    val reveal = LocalChannelRowsReveal.current
    // Google TV rows: the first tile holds the margin until focus would pass the far margin, and a
    // focused tile never runs into either margin.
    val marginPx = with(LocalDensity.current) { PlayerChromeTokens.gridMargin.toPx() }
    val horizontalBringIntoView = remember(marginPx) { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            val trailing = offset + size - (containerSize - marginPx)
            return when {
                offset < marginPx -> offset - marginPx
                trailing > 0f -> trailing
                else -> 0f
            }
        }
    } }
    // Vertical positioning belongs to this surface, not nested bring-into-view requests.
    val anchored = remember { object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
    } }
    // A page that is still returning from details cannot take focus yet.
    val focusable = active && LocalPlayerPageActive.current
    LaunchedEffect(selected, ids, focusable) {
        if (!focusable) return@LaunchedEffect
        if (ids.isEmpty()) {
            emptyFocus.requestFocus()
            return@LaunchedEffect
        }
        // Focus moves at once; a row a held key has outrun is snapped in rather than waited for.
        if (vertical.layoutInfo.visibleItemsInfo.none { it.index == selected }) {
            vertical.scrollToItem(selected)
            withFrameNanos { }
        }
        val returning = detailsTile?.takeIf { it.first == ids[selected] }
        detailsTile = null
        (if (returning != null) detailsReturn else requesters.getValue(ids[selected])).requestFocus()
        val target = vertical.layoutInfo.visibleItemsInfo.firstOrNull { it.index == selected } ?: return@LaunchedEffect
        if (target.offset != 0) vertical.animateScrollBy(target.offset.toFloat(),
            tween(PlayerMotion.PanelMs, easing = PlayerMotion.EmphasizedDecelerate))
    }
    Column(Modifier.fillMaxSize()
        .testTag("player-channel-rows")
        .onPreviewKeyEvent { event ->
            when (event.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) {
                        val up = event.key == Key.DirectionUp
                        // Up from the top leaves the way Down came in; a held key's repeats stop at the top.
                        if (up && selected == 0) {
                            if (event.nativeKeyEvent.repeatCount == 0) onClose(event.nativeKeyEvent.keyCode)
                        } else if (ids.isNotEmpty()) {
                            selected = (selected + if (up) -1 else 1).coerceIn(ids.indices)
                        }
                    }
                    true
                }
                Key.Back -> {
                    if (event.type == KeyEventType.KeyUp) onClose(null)
                    true
                }
                else -> false
            }
        }) {
        Spacer(Modifier.height(ChannelRowsTop))
        if (channels.isEmpty()) {
            Column(Modifier.padding(horizontal = PlayerChromeTokens.gridMargin)
                .padding(top = ChannelRowsAnchor).graphicsLayer { alpha = channelRowRise(reveal(), 1) }) {
                Text(stringResource(R.string.empty_channel_tag), color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { onClose(null) }, modifier = Modifier.focusRequester(emptyFocus)) { Text(stringResource(R.string.close)) }
            }
        } else CompositionLocalProvider(LocalBringIntoViewSpec provides anchored) {
            LazyColumn(
                state = vertical,
                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                contentPadding = PaddingValues(top = ChannelRowsAnchor, bottom = 300.dp),
                userScrollEnabled = false,
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id.value }) { index, channel ->
                    ChannelProgrammeRow(channel, index == selected, index - selected + 1,
                        requesters.getValue(channel.id), detailsTile?.takeIf { it.first == channel.id }?.second, detailsReturn,
                        nowSec, schedule(channel.id), acquireSchedule, imageLoader, currentSession, horizontalBringIntoView,
                        onPickChannel = { onPickChannel(channel) },
                        onOpenDetails = { tile, event ->
                            detailsTile = channel.id to tile
                            onOpenDetails(channel, event)
                        })
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
    /** 0 is the row above the focused one, 1 the focused one. */
    ordinal: Int,
    firstFocus: FocusRequester,
    returnTile: Int?,
    returnFocus: FocusRequester,
    nowSec: Long,
    events: List<EpgEvent>,
    acquireSchedule: suspend (ChannelId) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    horizontalBringIntoView: BringIntoViewSpec,
    onPickChannel: () -> Unit,
    onOpenDetails: (tile: Int, EpgEvent) -> Unit,
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
    // A row is entered on its now tile; one left behind settles back there while it is dimmed.
    LaunchedEffect(selected) {
        if (selected) horizontal.scrollToItem(0)
        else if (horizontal.firstVisibleItemIndex != 0 || horizontal.firstVisibleItemScrollOffset != 0) {
            horizontal.animateScrollToItem(0)
        }
    }
    val emphasis by animateFloatAsState(if (selected) 1f else ChannelRowDimAlpha,
        tween(PlayerMotion.MediumMs, easing = PlayerMotion.Standard), label = "channel-row-emphasis")
    val reveal = LocalChannelRowsReveal.current
    val identity = listOfNotNull(channel.visibleChannelNumber?.toString(), channel.name).joinToString(" · ")
    val now = events.firstOrNull { it.start.epochSeconds <= nowSec && it.stop.epochSeconds > nowSec }
    val tiles = listOf(now) + events.filter { it.start.epochSeconds > nowSec }
    val status = when {
        loading -> R.string.channel_rows_loading
        failed -> R.string.channel_rows_error
        else -> R.string.channel_rows_no_programme
    }
    val onNow = stringResource(R.string.channel_rows_on_now)
    Column(Modifier.fillMaxWidth().height(ChannelRowHeight)
        .graphicsLayer {
            val rise = channelRowRise(reveal(), ordinal)
            alpha = emphasis * rise
            translationY = PlayerMotion.PanelOffset.toPx() * (1f - rise)
        }
        .testTag("player-channel-row-${channel.id.value}")) {
        Row(Modifier.padding(horizontal = PlayerChromeTokens.gridMargin).height(24.dp)
            .semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (channel.icon != null) PiconBox(imageLoader = imageLoader, currentSession = currentSession,
                piconPath = channel.icon, modifier = Modifier.size(32.dp, 20.dp))
            Text(identity, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoView) {
        LazyRow(state = horizontal, contentPadding = PaddingValues(horizontal = PlayerChromeTokens.gridMargin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(PlayerChromeTokens.gridGutter)) {
            itemsIndexed(tiles, key = { index, event -> if (index == 0) "now" else event!!.id.value.toString() }) { index, event ->
                val title = event?.title ?: stringResource(status)
                val subtitle = channelRowSubtitle(event)
                val start = event?.let { formatClock(it.start.epochSeconds) }
                val tag = "player-channel-row-${channel.id.value}-tile-$index"
                val description = listOfNotNull(onNow.takeIf { index == 0 && event != null }, start, title, subtitle)
                    .joinToString(", ")
                Column(Modifier.width(ChannelRowTileWidth)) {
                Card(
                    onClick = { if (index == 0) onPickChannel() else event?.let { onOpenDetails(index, it) } },
                    border = embeddedProgressCardBorder(),
                    scale = CardDefaults.scale(focusedScale = PlayerChromeTokens.cardFocusedScale),
                    modifier = Modifier.size(ChannelRowTileWidth, ChannelRowTileHeight)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .then(if (index == returnTile) Modifier.focusRequester(returnFocus) else Modifier)
                        .focusProperties { left = if (index == 0) FocusRequester.Cancel else FocusRequester.Default
                            right = if (index == tiles.lastIndex && !failed) FocusRequester.Cancel else FocusRequester.Default }
                        .semantics { contentDescription = description }
                        .testTag(tag),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val artwork = ArtworkId.parse(event?.image)
                        var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
                        ChannelCardFace(channel.icon, channel.id, channel.name.orEmpty(), imageLoader, currentSession,
                            logoTag = "$tag-picon", nameTag = "$tag-channel-name", markWidth = 72.dp, markHeight = 32.dp,
                            lift = 0.dp)
                        if (artwork != null && currentSession != null && !artworkFailed) AsyncImage(
                            model = AppArtworkSource(currentSession, artwork), contentDescription = null,
                            imageLoader = imageLoader, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(), onError = { artworkFailed = true })
                        if (index == 0 && event != null) Text(onNow, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                .background(MaterialTheme.colorScheme.tertiary, PlayerChromeTokens.badgeShape)
                                .padding(horizontal = 4.dp).testTag("$tag-on-now"))
                        if (start != null) Text(start, style = MaterialTheme.typography.labelSmall,
                            color = Color.White, maxLines = 1, softWrap = false,
                            modifier = Modifier.align(Alignment.BottomStart)
                                .padding(start = 4.dp, bottom = if (index == 0) 7.dp else 4.dp)
                                .background(Color.Black.copy(alpha = 0.64f), PlayerChromeTokens.chipShape)
                                .padding(horizontal = 4.dp).testTag("$tag-start"))
                        if (index == 0 && event != null) ProgressStrip(
                            progress = ((nowSec - event.start.epochSeconds).toFloat() /
                                (event.stop.epochSeconds - event.start.epochSeconds).coerceAtLeast(1)).coerceIn(0f, 1f),
                            height = 3.dp, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                    }
                }
                Column(Modifier.clearAndSetSemantics { }) {
                    Text(title, Modifier.padding(top = 6.dp).testTag("$tag-title"),
                        color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, Modifier.testTag("$tag-subtitle"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                }
            }
            if (failed) item {
                Button(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
        }
        }
    }
}
