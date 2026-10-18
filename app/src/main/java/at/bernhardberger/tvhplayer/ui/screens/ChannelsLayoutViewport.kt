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

/** A grid scrolls by whole rows, the target's row first; paging keeps the column. */
internal class ChannelsGridViewport(
    private val state: LazyGridState,
    private val columns: Int,
) : ChannelsLayoutViewport {
    override fun isVisible(channelId: ChannelId): Boolean =
        state.layoutInfo.visibleItemsInfo.any { channelLazyItemMatches(it.key, channelId) }

    override fun pageSize(): Int {
        val visibleRows = (state.layoutInfo.visibleItemsInfo.size + columns - 1) / columns
        return (visibleRows - 1).coerceAtLeast(1) * columns
    }

    override suspend fun scrollToChannel(index: Int, animate: Boolean) {
        val rowStart = index - index % columns
        if (animate) state.animateScrollToItem(rowStart) else state.scrollToItem(rowStart)
    }

    override fun requestScrollToChannel(index: Int) = state.requestScrollToItem(index - index % columns)
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
