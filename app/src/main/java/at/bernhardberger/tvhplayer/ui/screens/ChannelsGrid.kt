package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
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
import at.bernhardberger.tvhplayer.ui.components.ChannelPlaybackIndicator
import at.bernhardberger.tvhplayer.ui.components.CompactZapCardGap
import at.bernhardberger.tvhplayer.ui.components.tabFocus

/** Four columns of the immersive row's cards. */
internal const val ChannelsGridColumns = 4

/** Tighter than the columns, so more rows fit below the header. */
private val ChannelsGridRowGap = 16.dp

/** Room on every side for a focused card's native scale and its outer outline. */
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
            ChannelsGridBringIntoViewSpec(topInsetPx = ChannelsGridEdgeInset.toPx())
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
                start = ChannelsGridEdgeInset,
                end = ChannelsGridEdgeInset,
                top = ChannelsGridEdgeInset,
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
                },
        ) {
            itemsIndexed(channels, key = { _, channel -> channelLazyItemKey(channel.id) }) { index, channel ->
                val channelId = channel.id
                // A focused lazy item can stay pinned for the publication that removes it.
                if (isCurrent && channelId !in focusRequesters) return@itemsIndexed
                val now = nowSec()
                val event = remember(channelId, frame.observation, now) {
                    frame.observation.eventAt(channelId, kotlin.time.Instant.fromEpochSeconds(now))
                }
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
 * Holds the focused row at the grid's top, below its inset, so the rows move under it and none is
 * cut off above it. Near the end the scroll stops at its bound, which leaves the last rows in place.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ChannelsGridBringIntoViewSpec(private val topInsetPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - topInsetPx
}
