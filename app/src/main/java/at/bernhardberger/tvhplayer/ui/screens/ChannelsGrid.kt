package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.ui.TvGridVerticalMargin
import at.bernhardberger.tvhplayer.ui.components.CardGlowReach
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardGap
import at.bernhardberger.tvhplayer.ui.components.cardGlowRoom
import at.bernhardberger.tvhplayer.ui.components.tabFocus

/** Four columns of the immersive row's cards. */
internal const val ChannelsGridColumns = 4

/** Tighter than the columns, so more rows fit below the header. */
private val ChannelsGridRowGap = 16.dp

/**
 * Room on every side for a focused card's scale and its outer outline. The grid draws its glow
 * further out, to [CardGlowReach], past its own bounds.
 */
internal val ChannelsGridEdgeInset = 16.dp

/**
 * Wall of the immersive row's cards in [ChannelsGridColumns] columns. D-pad moves within the grid; Right at the last
 * column and Down at the last row stay, Left at the first column leaves for the navigation rail
 * and Up from the first row reaches the header buttons. The grid scrolls in place below the
 * headline with the focused row pinned at its top, so no row is ever cut off there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelsGrid(
    channels: List<Channel>,
    frame: ChannelsTabBody,
    nowSec: () -> Long,
    state: LazyGridState,
    programmePicture: Boolean,
    /** Whether each card's channel is named below it. */
    channelNames: Boolean,
    isCurrent: Boolean,
    focusRequesters: Map<ChannelId, FocusRequester>,
    navigationFocus: FocusRequester?,
    headerFocus: FocusRequester?,
    onFocusChannel: (ChannelId) -> Unit,
    onPlay: (Channel) -> Unit,
    onOpenContextMenu: (ChannelId) -> Unit,
    onExitTowardNavigation: () -> Unit,
    onPage: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val columns = ChannelsGridColumns
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current
    val bringIntoView = remember(density) {
        with(density) {
            ChannelsGridBringIntoViewSpec(topInsetPx = CardGlowReach.toPx())
        }
    }
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val lastRow = channels.lastIndex / columns
    BoxWithConstraints(modifier) {
    val cardWidth = (maxWidth - ChannelsGridEdgeInset * 2 - CompactZapCardGap * (columns - 1)) / columns
    CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            userScrollEnabled = isCurrent,
            horizontalArrangement = Arrangement.spacedBy(CompactZapCardGap),
            verticalArrangement = Arrangement.spacedBy(ChannelsGridRowGap),
            contentPadding = PaddingValues(
                start = CardGlowReach,
                end = CardGlowReach,
                top = CardGlowReach,
                bottom = TvGridVerticalMargin,
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag("channels-grid")
                .focusGroup()
                .focusRestorer()
                .onPreviewKeyEvent { event ->
                    if (!isCurrent) return@onPreviewKeyEvent true
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val index = focusedIndex.takeIf { it in channels.indices }
                    val towardNavigation = if (ltr) Key.DirectionLeft else Key.DirectionRight
                    val awayFromNavigation = if (ltr) Key.DirectionRight else Key.DirectionLeft
                    when {
                        index == null -> false
                        event.key == towardNavigation -> {
                            if (index % columns == 0) onExitTowardNavigation()
                            false
                        }
                        event.key == awayFromNavigation -> index % columns == columns - 1 || index == channels.lastIndex
                        event.key == Key.DirectionDown -> index / columns == lastRow
                        else -> ChannelNavigation.pageDirectionForKeyCode(event.nativeKeyEvent.keyCode)?.let(onPage) ?: false
                    }
                }
                // The focused card's glow reaches past the inset, into the headline gap above.
                .cardGlowRoom(
                    horizontal = CardGlowReach - ChannelsGridEdgeInset,
                    top = CardGlowReach - ChannelsGridEdgeInset,
                    bottom = 0.dp,
                ),
        ) {
            itemsIndexed(channels, key = { _, channel -> channelLazyItemKey(channel.id) }) { index, channel ->
                val channelId = channel.id
                // A focused lazy item can stay pinned for the publication that removes it.
                if (isCurrent && channelId !in focusRequesters) return@itemsIndexed
                val now = nowSec()
                val event = remember(channelId, frame.observation, now) {
                    frame.observation.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(now))
                }
                Box(Modifier.clipAboveInset(state, index)) {
                    ChannelsRowCard(
                        channel = channel,
                        event = event,
                        nowSec = now,
                        playbackIndicator = frame.playbackIndicator.takeIf { channelId == frame.playbackChannelId }
                            ?: ChannelPlaybackIndicator.NONE,
                        recording = channelId in frame.recordingIds,
                        onClick = { onPlay(channel) },
                        onLongClick = { onOpenContextMenu(channelId) },
                        programmeLoading = frame.guideLoading,
                        programmePicture = programmePicture,
                        channelName = channelNames,
                        width = cardWidth,
                        tagPrefix = "channels-grid",
                        modifier = Modifier
                            .then(if (isCurrent) Modifier.focusRequester(focusRequesters.getValue(channelId)) else Modifier)
                            .tabFocus()
                            .focusProperties {
                                if (index % columns == 0) {
                                    if (ltr) left = navigationFocus ?: FocusRequester.Default
                                    else right = navigationFocus ?: FocusRequester.Default
                                }
                                if (index < columns && headerFocus != null) up = headerFocus
                            }
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focusedIndex = index
                                    onFocusChannel(channelId)
                                }
                            }
                            .testTag("channel-card-${channelId.value}"),
                    )
                }
            }
        }
    }
    }
}

/** Lays the content out [overflow] taller, reaching up by that much from its own top. */
internal fun Modifier.overflowTop(overflow: Dp): Modifier = layout { measurable, constraints ->
    val extra = overflow.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = constraints.minHeight + extra,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + extra else constraints.maxHeight,
        ),
    )
    layout(placeable.width, placeable.height - extra) { placeable.place(0, -extra) }
}

/**
 * Only a card whose resting top is inside the viewport can spill glow above the inset. During
 * scrolling, fade the departing content and glow together; at rest retain the original inset clip.
 * Read placement during drawing, not focus: focus changes before the scroll starts.
 */
private fun Modifier.clipAboveInset(state: LazyGridState, index: Int) = drawWithCache {
    val paint = Paint()
    val inset = ChannelsGridEdgeInset.toPx()
    val reach = CardGlowReach.toPx()
    onDrawWithContent {
        // Offsets count from the content padding's end, where the focused row stays.
        val top = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset?.y
        if (top == null || top >= 0) drawContent()
        else {
            val edge = -inset - top
            clipRect(-size.width, edge, 2 * size.width, 2 * size.height) {
                if (state.isScrollInProgress) {
                    // Isolate only this departing item, without cropping its side/bottom glow.
                    val bounds = Rect(-reach, edge, size.width + reach, size.height + reach)
                    drawContext.canvas.saveLayer(bounds, paint)
                    this@onDrawWithContent.drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent, 0.25f to Color.Black.copy(alpha = 0.1f),
                            0.75f to Color.Black.copy(alpha = 0.9f), 1f to Color.Black,
                            startY = edge, endY = edge + inset,
                        ),
                        topLeft = Offset(bounds.left, edge),
                        size = Size(bounds.width, inset),
                        blendMode = BlendMode.DstIn,
                    )
                    drawContext.canvas.restore()
                } else this@onDrawWithContent.drawContent()
            }
        }
    }
}

/**
 * Holds the focused row at the grid's top, below its inset, so the rows move under it and none is
 * cut off above it. Near the end the scroll stops at its bound, which leaves the last rows in place.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ChannelsGridBringIntoViewSpec(private val topInsetPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - topInsetPx
}
