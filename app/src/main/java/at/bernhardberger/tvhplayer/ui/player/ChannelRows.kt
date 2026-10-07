package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.ui.TvPassiveAlpha
import at.bernhardberger.tvhplayer.ui.TvGridGutter
import at.bernhardberger.tvhplayer.ui.TvGridHorizontalMargin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sign

/*
 * Trial, Google TV rows. Programme tiles are the channel card (196×110dp, three grid columns), four
 * across with the fifth peeking. The focused row carries a large header and the text below its
 * tiles; other rows show a small quiet header and dimmed images only.
 *
 * A focused row (from its top): 32dp header, 12dp, tiles 110dp, 8dp, title and subtitle 36dp,
 * 18dp = 216dp. Any other row: 20dp header, 8dp, tiles 110dp, 18dp = 156dp.
 */
internal val ChannelRowFocusedHeight = 216.dp
internal val ChannelRowHeight = 156.dp
private val FocusedTilesTop = 44.dp
private val TilesTop = 28.dp
/** Rows fade in below the now-playing strip; the denser rail scrim starts here too. */
internal val ChannelRowsTop = 60.dp
/** The focused row's top on screen; the previous row rests fully above it, fading toward [ChannelRowsTop]. */
internal val ChannelRowsAnchor = 116.dp
internal const val ChannelRowDimAlpha = 0.4f
private const val ChannelRowHeaderQuietAlpha = TvPassiveAlpha
/** The focused header is headlineSmall (24sp); the others read at titleSmall's size (14sp). */
internal const val ChannelRowHeaderQuietScale = 14f / 24f

/** Open: the footer leaves first, then the rows rise as one group with a gentle stagger. */
private const val ChannelRowsRiseDelayMs = PlayerMotion.FastMs
internal const val ChannelRowStaggerMs = 30
internal const val ChannelRowRiseMs = 300
internal const val ChannelRowsRevealMs = ChannelRowsRiseDelayMs + 3 * ChannelRowStaggerMs + ChannelRowRiseMs
internal const val ChannelRowsHideMs = 300
/** Row change: the column, the headers and the dimming all follow one position on this clock. */
internal const val ChannelRowChangeMs = 300

/** The linear open/close clock of the rows, 0 to 1; fully open by default. */
internal val LocalChannelRowsReveal = compositionLocalOf<() -> Float> { { 1f } }

/** How far the row at [ordinal] (0 = the row above the focused one) has risen at linear [reveal]. */
internal fun channelRowRise(reveal: Float, ordinal: Int): Float {
    val ms = reveal * ChannelRowsRevealMs - ChannelRowsRiseDelayMs - ordinal.coerceIn(0, 3) * ChannelRowStaggerMs
    return PlayerMotion.Standard.transform((ms / ChannelRowRiseMs).coerceIn(0f, 1f))
}

/** A row's emphasis at [distance] rows from the column position: 1 focused, 0 one row or more away. */
internal fun channelRowEmphasis(distance: Float): Float = (1f - abs(distance)).coerceIn(0f, 1f)

/** A row's top on screen at [distance] rows from the column position; the focused row is taller. */
internal fun channelRowTop(distance: Float): Dp = ChannelRowsAnchor + when {
    distance <= 0f -> ChannelRowHeight * distance
    distance <= 1f -> ChannelRowFocusedHeight * distance
    else -> ChannelRowFocusedHeight + ChannelRowHeight * (distance - 1f)
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
    // The column's position in rows: every row's place, header size and dimming follow it.
    val position = remember(ids) { Animatable(selected.toFloat()) }
    // The row holding focus stays composed until focus has left it, however far a held key went.
    var focusedRow by remember(ids) { mutableIntStateOf(selected) }
    val composedRows by remember(ids) { derivedStateOf {
        val p = position.value
        ((floor(p).toInt() - 1..ceil(p).toInt() + 2) + (selected - 1..selected + 2) + focusedRow)
            .filter { it in ids.indices }.distinct().sorted()
    } }
    val requesters = remember(ids) { ids.associateWith { FocusRequester() } }
    val emptyFocus = remember { FocusRequester() }
    // The programme whose details opened over the rows takes focus again when they close.
    var detailsTile by remember { mutableStateOf<Pair<ChannelId, Int>?>(null) }
    val detailsReturn = remember { FocusRequester() }
    val reveal = LocalChannelRowsReveal.current
    // Google TV rows: the first tile holds the margin until focus would pass the far margin, and a
    // focused tile never runs into either margin.
    val marginPx = with(LocalDensity.current) { TvGridHorizontalMargin.toPx() }
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
    LaunchedEffect(selected, ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        val target = selected.toFloat()
        // A held key never leaves the column more than a row behind; each step still glides.
        val behind = target - position.value
        if (abs(behind) > 1f) position.snapTo(target - sign(behind))
        position.animateTo(target, tween(ChannelRowChangeMs, easing = PlayerMotion.Standard))
    }
    // A page that is still returning from details cannot take focus yet.
    val focusable = active && LocalPlayerPageActive.current
    LaunchedEffect(selected, ids, focusable) {
        if (!focusable) return@LaunchedEffect
        if (ids.isEmpty()) {
            emptyFocus.requestFocus()
            return@LaunchedEffect
        }
        // A row a held key has just reached attaches its tiles with this frame's layout.
        withFrameNanos { }
        val returning = detailsTile?.takeIf { it.first == ids[selected] }
        detailsTile = null
        (if (returning != null) detailsReturn else requesters.getValue(ids[selected])).requestFocus()
    }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()
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
        }
        // The previous row fades into the page toward the strip instead of meeting a clip line.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black),
                startY = ChannelRowsTop.toPx(), endY = ChannelRowsAnchor.toPx()), blendMode = BlendMode.DstIn)
        }) {
        if (channels.isEmpty()) {
            Column(Modifier.padding(horizontal = TvGridHorizontalMargin)
                .padding(top = ChannelRowsAnchor).graphicsLayer { alpha = channelRowRise(reveal(), 1) }) {
                Text(stringResource(R.string.empty_channel_tag), color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { onClose(null) }, modifier = Modifier.focusRequester(emptyFocus)) { Text(stringResource(R.string.close)) }
            }
        } else for (index in composedRows) {
            val channel = channels[index]
            key(channel.id.value) {
                ChannelProgrammeRow(channel, index == selected, index - selected + 1,
                    distance = { index - position.value },
                    requesters.getValue(channel.id), detailsTile?.takeIf { it.first == channel.id }?.second, detailsReturn,
                    nowSec, schedule(channel.id), acquireSchedule, imageLoader, currentSession, horizontalBringIntoView,
                    onFocused = { focusedRow = index },
                    onPickChannel = { onPickChannel(channel) },
                    onOpenDetails = { tile, event ->
                        detailsTile = channel.id to tile
                        onOpenDetails(channel, event)
                    },
                    modifier = Modifier.offset { IntOffset(0, with(density) { channelRowTop(index - position.value).roundToPx() }) })
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
    /** Rows from the column position; read only while laying out and drawing. */
    distance: () -> Float,
    firstFocus: FocusRequester,
    returnTile: Int?,
    returnFocus: FocusRequester,
    nowSec: Long,
    events: List<EpgEvent>,
    acquireSchedule: suspend (ChannelId) -> Unit,
    imageLoader: ImageLoader,
    currentSession: CurrentSessionObservation?,
    horizontalBringIntoView: BringIntoViewSpec,
    onFocused: () -> Unit,
    onPickChannel: () -> Unit,
    onOpenDetails: (tile: Int, EpgEvent) -> Unit,
    modifier: Modifier = Modifier,
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
    val emphasis = { channelRowEmphasis(distance()) }
    // Text below the tiles belongs to the focused row only; it fades with the row change.
    val textShown by remember { derivedStateOf { emphasis() > 0f } }
    var focusedTile by remember { mutableIntStateOf(-1) }
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
    val tilesTop = { lerp(TilesTop, FocusedTilesTop, emphasis()) }
    Box(modifier.fillMaxWidth()
        .graphicsLayer {
            val rise = channelRowRise(reveal(), ordinal)
            alpha = rise
            translationY = PlayerMotion.PanelOffset.toPx() * (1f - rise)
        }
        .testTag("player-channel-row-${channel.id.value}")) {
        // The header grows from titleSmall's size to headlineSmall on its baseline side, above the tiles.
        Row(Modifier.padding(horizontal = TvGridHorizontalMargin)
            .layout { measurable, constraints ->
                val header = measurable.measure(constraints)
                layout(header.width, header.height) {
                    val gap = lerp(8.dp, 12.dp, emphasis())
                    header.place(0, (tilesTop() - gap).roundToPx() - header.height)
                }
            }
            .graphicsLayer {
                val e = emphasis()
                scaleX = lerp(ChannelRowHeaderQuietScale, 1f, e)
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 1f)
                alpha = lerp(ChannelRowHeaderQuietAlpha, 1f, e)
            }
            .semantics(mergeDescendants = true) { heading() }
            .testTag("player-channel-row-${channel.id.value}-header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (channel.icon != null) PiconBox(imageLoader = imageLoader, currentSession = currentSession,
                piconPath = channel.icon, modifier = Modifier.size(48.dp, 30.dp))
            Text(identity, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalBringIntoView) {
        // 8dp above and below the tiles is room for the focus scale and glow.
        LazyRow(state = horizontal,
            modifier = Modifier.offset { IntOffset(0, (tilesTop() - 8.dp).roundToPx()) }
                .onFocusChanged { if (it.hasFocus) onFocused() },
            contentPadding = PaddingValues(horizontal = TvGridHorizontalMargin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(TvGridGutter)) {
            itemsIndexed(tiles, key = { index, event -> if (index == 0) "now" else event!!.id.value.toString() }) { index, event ->
                val title = event?.title
                val subtitle = channelRowSubtitle(event)
                val start = event?.let { formatClock(it.start.epochSeconds) }
                val statusText = stringResource(status)
                val tag = "player-channel-row-${channel.id.value}-tile-$index"
                val description = if (event == null) statusText else listOfNotNull(
                    onNow.takeIf { index == 0 }, start, title, subtitle).joinToString(", ")
                Column(Modifier.width(PlayerChromeTokens.channelCardWidth)) {
                Card(
                    onClick = { if (index == 0) onPickChannel() else event?.let { onOpenDetails(index, it) } },
                    border = embeddedProgressCardBorder(),
                    scale = CardDefaults.scale(focusedScale = PlayerChromeTokens.cardFocusedScale),
                    modifier = Modifier.size(PlayerChromeTokens.channelCardWidth, PlayerChromeTokens.channelCardHeight)
                        .graphicsLayer { alpha = lerp(ChannelRowDimAlpha, 1f, emphasis()) }
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .then(if (index == returnTile) Modifier.focusRequester(returnFocus) else Modifier)
                        .focusProperties { left = if (index == 0) FocusRequester.Cancel else FocusRequester.Default
                            right = if (index == tiles.lastIndex && !failed) FocusRequester.Cancel else FocusRequester.Default }
                        .onFocusChanged {
                            if (it.isFocused) focusedTile = index else if (focusedTile == index) focusedTile = -1
                        }
                        .semantics { contentDescription = description }
                        .testTag(tag),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        val artwork = ArtworkId.parse(event?.image)
                        var artworkFailed by remember(artwork, currentSession) { mutableStateOf(false) }
                        ChannelCardFace(channel.icon, channel.id, channel.name.orEmpty(), imageLoader, currentSession,
                            logoTag = "$tag-picon", nameTag = "$tag-channel-name", markWidth = 112.dp, markHeight = 48.dp,
                            lift = 0.dp)
                        if (artwork != null && currentSession != null && !artworkFailed) AsyncImage(
                            model = AppArtworkSource(currentSession, artwork), contentDescription = null,
                            imageLoader = imageLoader, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(), onError = { artworkFailed = true })
                        if (index == 0 && event != null) TileChip(onNow, live = true,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).testTag("$tag-on-now"))
                        // The start time, or the row's state where it has no programme yet.
                        TileChip(start ?: statusText, modifier = Modifier.align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = if (index == 0 && event != null) 9.dp else 6.dp)
                            .testTag(if (start != null) "$tag-start" else "$tag-status"))
                        if (index == 0 && event != null) ProgressStrip(
                            progress = ((nowSec - event.start.epochSeconds).toFloat() /
                                (event.stop.epochSeconds - event.start.epochSeconds).coerceAtLeast(1)).coerceIn(0f, 1f),
                            height = 3.dp, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
                    }
                }
                // The focused tile carries title and subtitle, the others in its row a quiet title.
                if (textShown && title != null) Column(Modifier.padding(top = 8.dp)
                    .graphicsLayer { alpha = emphasis() }.clearAndSetSemantics { }) {
                    val focused = focusedTile == index
                    Text(title, Modifier.testTag("$tag-title"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 1f else .7f),
                        style = if (focused) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (focused && subtitle != null) Text(subtitle, Modifier.testTag("$tag-subtitle"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
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

/** Google TV's in-image label: a small dark translucent chip; [live] adds the orange live dot. */
@Composable
private fun TileChip(text: String, modifier: Modifier = Modifier, live: Boolean = false) {
    Row(modifier.background(PlayerChromeTokens.chipScrim, PlayerChromeTokens.chipShape)
        .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (live) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}
