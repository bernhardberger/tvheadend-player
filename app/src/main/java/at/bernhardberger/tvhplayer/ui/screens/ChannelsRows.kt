package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.visibleChannelNumber
import at.bernhardberger.tvhplayer.ui.LocalWarmPlayback
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.CompactZapCard
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardGap
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardWidth
import at.bernhardberger.tvhplayer.ui.components.CompactZapRowOffscreen
import at.bernhardberger.tvhplayer.ui.components.DetailsHero
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.ProgressStrip
import at.bernhardberger.tvhplayer.ui.components.RowChangeMotion
import at.bernhardberger.tvhplayer.ui.components.StartKeylineBringIntoViewSpec
import at.bernhardberger.tvhplayer.ui.components.channelTitleText
import at.bernhardberger.tvhplayer.ui.components.navigationEdgeFadeMask
import at.bernhardberger.tvhplayer.ui.components.overflowingRowViewport
import at.bernhardberger.tvhplayer.ui.components.rowContentEmphasis
import at.bernhardberger.tvhplayer.ui.components.rowEmphasis
import at.bernhardberger.tvhplayer.ui.components.rowHeaderEmphasis
import at.bernhardberger.tvhplayer.ui.components.tabFocus
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Instant

/** Browsing settles this long before the background follows, so fast steps do not flash art. */
private const val RowsBackgroundDebounceMillis = 300L
private const val RowsBackgroundCrossfadeMillis = 300

/** Room above and below a row's cards for their native focus scale and outline. */
private val RowsFocusRoom = 12.dp

/** Between a row's heading and its cards. */
private val RowsHeadingGap = 8.dp

/** The channel logo standing in for a missing programme picture, clear of the header above. */
private val RowsFallbackLogoTop = 24.dp
private val RowsFallbackLogoSize = 240.dp to 135.dp

private val RowsHeroWidth = 480.dp

private val RowsBackgroundTopFade = 48.dp

/**
 * Background of the Rows layout below the header: the focused channel's programme picture under
 * the kit's cinematic scrim, or its logo on the theme background. Warm playback keeps the video as
 * the background, so nothing is drawn.
 */
@Composable
internal fun ChannelsRowsBackground(
    channels: List<Channel>,
    channelId: () -> ChannelId?,
    observation: SessionObservation,
    nowSec: () -> Long,
    endInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (LocalWarmPlayback.current) return
    val target = channelId()
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (shown != target) {
            delay(RowsBackgroundDebounceMillis)
            shown = target
        }
    }
    Crossfade(
        shown,
        modifier.testTag("channels-rows-background"),
        tween(RowsBackgroundCrossfadeMillis),
        label = "rowsBackground",
    ) { id ->
        val channel = channels.firstOrNull { it.id == id } ?: return@Crossfade
        val now = nowSec()
        val art = remember(observation, id, now) {
            id?.let { observation.eventAt(it, Instant.fromEpochSeconds(now)) }?.image
        }
        val session = observation.currentSession
        Box(Modifier.fillMaxSize()) {
            if (art != null && session != null) {
                DetailsHero(art, Modifier.align(Alignment.TopEnd).testTag("channels-rows-art")
                    // The picture starts below the header; its top edge fades in rather than cutting across.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = RowsBackgroundTopFade.toPx()),
                            blendMode = BlendMode.DstIn,
                        )
                    })
            } else {
                PiconBox(channel.icon,
                    Modifier.align(Alignment.TopEnd).padding(top = RowsFallbackLogoTop, end = endInset)
                        .size(RowsFallbackLogoSize.first, RowsFallbackLogoSize.second)
                        .testTag("channels-rows-picon"),
                )
            }
        }
    }
}

/** Card-row facts shared by every row; only the current row takes focus and keys. */
internal class ChannelsRowsCards(
    val frame: ChannelsTabBody,
    val nowSec: () -> Long,
    val keyline: Dp,
    val programmePicture: Boolean,
    val focusRequesters: Map<ChannelId, FocusRequester>,
    val navigationFocus: FocusRequester?,
    val headerFocus: FocusRequester?,
    val onFocusChannel: (ChannelId) -> Unit,
    val onPlay: (Channel) -> Unit,
    val onOpenContextMenu: (ChannelId) -> Unit,
    val onExitTowardNavigation: () -> Unit,
    val onPage: (Int) -> Boolean,
)

/**
 * Rows: the focused channel's programme on top, then one row of compact zap cards per channel
 * group, the current group's row first and the next row peeking below. Up and Down change the row
 * with one [RowChangeMotion]; each row keeps its own position and focused card. Up from the first
 * row reaches the header, Left at a row's start the navigation rail.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelsRowsBody(
    rows: List<ChannelsGroup>,
    currentTagId: ChannelTagId?,
    cards: ChannelsRowsCards,
    rowState: (ChannelTagId?) -> LazyListState,
    onChangeRow: (ChannelsGroup) -> Unit,
    modifier: Modifier = Modifier,
    currentRowModifier: Modifier = Modifier,
) {
    val currentIndex = rows.indexOfFirst { it.tagId == currentTagId }.coerceAtLeast(0)
    val position by animateFloatAsState(currentIndex.toFloat(), RowChangeMotion, label = "channels-row")
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val cardHeight = with(density) { 70.dp + typography.titleMedium.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp() }
    val rowHeight = with(density) { typography.titleMedium.lineHeight.toDp() } + RowsHeadingGap + cardHeight + RowsFocusRoom * 2
    val rowPx = with(density) { rowHeight.toPx() }
    val quietScale = typography.titleSmall.fontSize.value / typography.titleMedium.fontSize.value
    // Rows within reach of the moving position, plus the current one however far a change went.
    val composed by remember(rows) {
        derivedStateOf {
            ((floor(position).toInt() - 1)..(ceil(position).toInt() + 2)).filter { it in rows.indices }
        }
    }
    // Holds focus while the rows trade places, so the leaving row cannot pass focus along its cards.
    val handover = remember { FocusRequester() }
    var handingOver by remember { mutableStateOf(false) }
    Column(modifier) {
        ChannelsRowsHero(rows.getOrNull(currentIndex)?.channels.orEmpty(), cards, Modifier.padding(start = cards.keyline, top = 8.dp))
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                // The previous row leaves upward under the hero rather than across it.
                .clipToBounds()
                .testTag("channels-rows")
                .onPreviewKeyEvent { event ->
                    val step = when (event.key) {
                        Key.DirectionDown -> 1
                        Key.DirectionUp -> -1
                        else -> 0
                    }
                    // Up from the first row is the cards' own: it reaches the header.
                    if (step == 0 || step < 0 && currentIndex == 0) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        adjacentChannelsRow(rows, currentTagId, step)?.let {
                            handingOver = true
                            handover.requestFocus()
                            onChangeRow(it)
                        }
                    }
                    true
                },
        ) {
            Box(Modifier.focusRequester(handover)
                .onFocusChanged { if (!it.isFocused) handingOver = false }
                .focusProperties { canFocus = handingOver }
                .focusable())
            for (index in (composed + currentIndex).distinct()) {
                val row = rows[index]
                key(row.tagId) {
                    val emphasis = { rowEmphasis(index - position) }
                    val current = index == currentIndex
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = (index - position) * rowPx
                                // The hero owns the room above: the previous row is gone halfway up.
                                alpha = (1f + 2f * (index - position)).coerceIn(0f, 1f)
                            }
                            .testTag("channels-row-${row.tagId?.value ?: "all"}"),
                    ) {
                        Text(
                            row.name ?: stringResource(R.string.all_channels),
                            style = typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = cards.keyline)
                                .widthIn(max = RowsHeroWidth)
                                // The row above the current one is invisible, so its heading leaves the tree too.
                                .then(if (index < currentIndex) Modifier.clearAndSetSemantics { } else Modifier.semantics { heading() })
                                .rowHeaderEmphasis(quietScale, emphasis = emphasis),
                        )
                        Spacer(Modifier.height(RowsHeadingGap - RowsFocusRoom.coerceAtMost(RowsHeadingGap)))
                        ChannelsCardRow(
                            channels = row.channels,
                            cards = cards,
                            state = rowState(row.tagId),
                            current = current,
                            tag = "channels-row-${row.tagId?.value ?: "all"}-cards",
                            modifier = Modifier
                                .rowContentEmphasis(emphasis)
                                .then(if (current) currentRowModifier else Modifier),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One overflowing card row; the focused card rests on the start keyline like the player rail.
 * Only the [current] row's cards can take focus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelsCardRow(
    channels: List<Channel>,
    cards: ChannelsRowsCards,
    state: LazyListState,
    current: Boolean,
    tag: String,
    modifier: Modifier,
) {
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val offscreenPx = with(density) { CompactZapRowOffscreen.roundToPx() }
    val bringIntoView = remember(density, cards.keyline) {
        StartKeylineBringIntoViewSpec(with(density) { cards.keyline.toPx() } + offscreenPx)
    }
    val frame = cards.frame
    var focusedIndex by remember { mutableIntStateOf(-1) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyRow(
            state = state,
            userScrollEnabled = current,
            horizontalArrangement = Arrangement.spacedBy(CompactZapCardGap),
            contentPadding = PaddingValues(horizontal = cards.keyline + CompactZapRowOffscreen, vertical = RowsFocusRoom),
            modifier = modifier
                .fillMaxWidth()
                // The card before the keyline passes under the navigation rail: fade it out there.
                .navigationEdgeFadeMask(cards.keyline - CompactZapCardGap)
                .overflowingRowViewport(offscreenPx)
                .testTag(tag)
                .focusGroup()
                .focusRestorer()
                .onPreviewKeyEvent { event ->
                    if (!current || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val index = focusedIndex.takeIf { it in channels.indices }
                    val towardNavigation = if (ltr) Key.DirectionLeft else Key.DirectionRight
                    val awayFromNavigation = if (ltr) Key.DirectionRight else Key.DirectionLeft
                    when {
                        index == null -> false
                        event.key == towardNavigation -> {
                            if (index == 0) cards.onExitTowardNavigation()
                            false
                        }
                        event.key == awayFromNavigation -> index == channels.lastIndex
                        else -> ChannelNavigation.pageDirectionForKeyCode(event.nativeKeyEvent.keyCode)?.let(cards.onPage) ?: false
                    }
                },
        ) {
            itemsIndexed(channels, key = { _, channel -> channelLazyItemKey(channel.id) }) { index, channel ->
                val channelId = channel.id
                val requester = cards.focusRequesters[channelId]?.takeIf { current }
                // A focused lazy item can stay pinned for the publication that removes it.
                if (current && requester == null) return@itemsIndexed
                val now = cards.nowSec()
                val event = remember(channelId, frame.observation, now) {
                    frame.observation.eventAt(channelId, Instant.fromEpochSeconds(now))
                }
                CompactZapCard(
                    channel = channel,
                    number = channel.visibleChannelNumber,
                    event = event,
                    nowSec = now,
                    playbackIndicator = frame.playbackIndicator.takeIf { channelId == frame.playbackChannelId }
                        ?: ChannelPlaybackIndicator.NONE,
                    recording = channelId in frame.recordingIds,
                    onClick = { if (current) cards.onPlay(channel) },
                    onLongClick = { if (current) cards.onOpenContextMenu(channelId) },
                    tagPrefix = "channels-rows",
                    menuKey = true,
                    programmePicture = cards.programmePicture,
                    modifier = Modifier
                        .width(CompactZapCardWidth)
                        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                        .tabFocus()
                        .focusProperties {
                            canFocus = current
                            if (index == 0) {
                                if (ltr) left = cards.navigationFocus ?: FocusRequester.Default
                                else right = cards.navigationFocus ?: FocusRequester.Default
                            }
                            cards.headerFocus?.let { up = it }
                        }
                        .onFocusChanged {
                            if (it.isFocused) {
                                focusedIndex = index
                                cards.onFocusChannel(channelId)
                            }
                        }
                        .then(if (current) Modifier.testTag("channel-card-${channelId.value}") else Modifier),
                )
            }
        }
    }
}

/** The focused channel's identity and programme, passive; it dissolves like the list preview. */
@Composable
private fun ChannelsRowsHero(
    channels: List<Channel>,
    cards: ChannelsRowsCards,
    modifier: Modifier,
) {
    val frame = cards.frame
    val channelId = frame.focusedId()?.takeIf { id -> channels.any { it.id == id } }
        ?: browsingFocusChannelId(channels, frame.entryId())
    val typography = MaterialTheme.typography
    // A fixed height keeps the rows in place while the hero's text changes length.
    val height = with(LocalDensity.current) {
        36.dp + 8.dp + typography.headlineSmall.lineHeight.toDp() + 4.dp + typography.bodyMedium.lineHeight.toDp() +
            12.dp + typography.bodyMedium.lineHeight.toDp() * 2
    }
    AnimatedContent(
        targetState = channelId,
        transitionSpec = {
            fadeIn(tween(ChannelsDetailCrossfadeMillis)) togetherWith
                fadeOut(tween(ChannelsDetailCrossfadeMillis)) using
                SizeTransform(clip = false) { _, _ -> snap() }
        },
        contentAlignment = Alignment.TopStart,
        modifier = modifier.height(height).widthIn(max = RowsHeroWidth).testTag("channels-rows-hero"),
        label = "rowsHero",
    ) { id ->
        val channel = channels.firstOrNull { it.id == id } ?: return@AnimatedContent
        val now = cards.nowSec()
        val observation = frame.observation
        val current = remember(observation, id, now) { observation.eventAt(channel.id, Instant.fromEpochSeconds(now)) }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PiconBox(channel.icon, Modifier.size(60.dp, 36.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    channelTitleText(channel.visibleChannelNumber, channel.name.orEmpty()),
                    style = typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                current?.title?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_epg),
                style = typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).testTag("channels-rows-title"),
            )
            if (current != null) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatTimeInterval(current.start.epochSeconds, current.stop.epochSeconds),
                        style = typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(12.dp))
                    ProgressStrip(current.progress(now), Modifier.width(160.dp))
                }
                channelsProgrammeSynopsis(current)?.let {
                    Text(it, style = typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}
