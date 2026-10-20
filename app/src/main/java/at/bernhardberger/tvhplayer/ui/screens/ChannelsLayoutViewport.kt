package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos
import at.bernhardberger.tvheadend.sdk.core.ChannelId

/**
 * The scrolling container of the active Channels layout. Restoration, paging and scope
 * entry share one policy; each layout only says how its items scroll into place.
 */
internal interface ChannelsLayoutViewport {
    fun isVisible(channelId: ChannelId): Boolean

    /** Channels CH+/CH- moves focus by. */
    fun pageSize(): Int

    suspend fun scrollToChannel(index: Int, animate: Boolean)

    /** Positions [index] in the next layout pass without requesting focus. */
    fun requestScrollToChannel(index: Int)
}

/** A vertical list positioned at [offsetFor], the same pivot native focus scrolling uses. */
internal class ChannelsListViewport(
    private val state: LazyListState,
    private val offsetFor: (Int) -> Int,
) : ChannelsLayoutViewport {
    override fun isVisible(channelId: ChannelId): Boolean =
        state.layoutInfo.visibleItemsInfo.any { channelLazyItemMatches(it.key, channelId) }

    override fun pageSize(): Int = (state.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)

    override suspend fun scrollToChannel(index: Int, animate: Boolean) {
        if (animate) state.animateScrollToItem(index, offsetFor(index))
        else state.scrollToItem(index, offsetFor(index))
    }

    override fun requestScrollToChannel(index: Int) = state.requestScrollToItem(index, offsetFor(index))
}

/**
 * A grid puts the target's row just below the peeking half of the row above it, where focus
 * scrolling holds it; paging keeps the column.
 */
internal class ChannelsGridViewport(
    private val state: LazyGridState,
    private val columns: Int,
) : ChannelsLayoutViewport {
    override fun isVisible(channelId: ChannelId): Boolean =
        state.layoutInfo.visibleItemsInfo.any { channelLazyItemMatches(it.key, channelId) }

    /** The whole rows the grid fits, the same scrolled or not, so paging down and back up match. */
    override fun pageSize(): Int {
        val layout = state.layoutInfo
        val row = layout.visibleItemsInfo.firstOrNull()?.size?.height ?: return columns
        val height = layout.viewportSize.height - layout.beforeContentPadding
        val wholeRows = (height + layout.mainAxisItemSpacing) / (row + layout.mainAxisItemSpacing)
        return wholeRows.coerceAtLeast(1) * columns
    }

    override suspend fun scrollToChannel(index: Int, animate: Boolean) {
        val (row, offset) = peekingRowFor(index)
        if (animate) state.animateScrollToItem(row, offset) else state.scrollToItem(row, offset)
    }

    override fun requestScrollToChannel(index: Int) {
        val (row, offset) = peekingRowFor(index)
        state.requestScrollToItem(row, offset)
    }

    /** The row above the target, scrolled so only its peeking part shows; the first row stays put. */
    private fun peekingRowFor(index: Int): Pair<Int, Int> {
        val rowStart = index - index % columns
        if (rowStart == 0) return 0 to 0
        val rowHeight = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
        return rowStart - columns to (rowHeight * (1f - ChannelsGridPeekFraction)).toInt()
    }
}

/**
 * A card row whose viewport overflows both screen edges by [offscreenPx] and keeps the focused
 * card on the start keyline, where its content padding ends (item offset 0). Paging moves by the
 * cards that fit on screen after the keyline.
 */
internal class ChannelsRowViewport(
    private val state: LazyListState,
    private val offscreenPx: () -> Int,
) : ChannelsLayoutViewport {
    override fun isVisible(channelId: ChannelId): Boolean =
        state.layoutInfo.visibleItemsInfo.any { channelLazyItemMatches(it.key, channelId) }

    override fun pageSize(): Int {
        val layout = state.layoutInfo
        val end = layout.viewportEndOffset - offscreenPx()
        return layout.visibleItemsInfo.count { it.offset >= 0 && it.offset + it.size <= end }.coerceAtLeast(1)
    }

    override suspend fun scrollToChannel(index: Int, animate: Boolean) {
        if (animate) state.animateScrollToItem(index) else state.scrollToItem(index)
    }

    override fun requestScrollToChannel(index: Int) = state.requestScrollToItem(index)
}

private const val CHANNEL_RESTORE_LAYOUT_FRAMES = 4

internal suspend fun ChannelsLayoutViewport.awaitVisibleChannel(channelId: ChannelId): Boolean {
    repeat(CHANNEL_RESTORE_LAYOUT_FRAMES) {
        if (isVisible(channelId)) return true
        withFrameNanos { }
    }
    return isVisible(channelId)
}
