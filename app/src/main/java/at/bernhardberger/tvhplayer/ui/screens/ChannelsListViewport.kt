package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.components.ChannelRowEdgeInset
import at.bernhardberger.tvhplayer.ui.components.ChannelRowWidth
import at.bernhardberger.tvhplayer.ui.TvBrowseColumnGap

// Channels geometry, expressed relative to the shell's safe-area inset.
// The shell passes 24dp leading and 32dp top; nominal rows sit 26dp further in
// (x130 on the 960x540 canvas), including the list's 12dp native focus reserve.

/** Minimum scope band; larger native tabs grow it rather than clipping their text. */
internal val ChannelsTagSectionHeight = 44.dp

/** Top content reserve inside the list so the first focused row's scale is not clipped. */
internal val ChannelsListTopReserve = 4.dp

/** Height of each transparent fade where scrolling rows leave the list viewport. */
internal val ChannelsListFadeHeight = 48.dp

/** Extra room between either fade and the focused row's native scale. */
internal val ChannelsListFocusBreathingRoom = 8.dp

/** Bottom band kept clear of focused rows: fade plus breathing room. */
internal val ChannelsListBottomReserve = ChannelsListFadeHeight + ChannelsListFocusBreathingRoom

/** Top band kept clear while earlier rows exist; the first row retains its 4dp reserve. */
internal val ChannelsListScrolledTopReserve = ChannelsListFadeHeight + ChannelsListFocusBreathingRoom

/** List column including the horizontal focus-scale reserve on both sides of the rows. */
internal val ChannelsListColumnWidth = ChannelRowWidth + ChannelRowEdgeInset * 2

/** The trailing focus reserve is part of the nominal column separation, not extra. */
internal val ChannelsListToDetailsGap = TvBrowseColumnGap - ChannelRowEdgeInset

/**
 * Bring-into-view policy for the list: the delegate (the platform's TV pivot
 * spec or default) sees a viewport shortened by the current top inset and
 * [bottomInsetPx] at the end, so native focus scrolling keeps a focused row inside
 * the readable band between the fades. Read scroll position when calculating, not
 * in composition. Clamp at the actual first-row boundary so returning there removes
 * the top fade without a residual backwards request from the native focus scale.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class InsetBringIntoViewSpec(
    private val delegate: BringIntoViewSpec,
    private val topInsetPx: Float,
    private val bottomInsetPx: Float,
    private val scrolledTopInsetPx: Float = topInsetPx,
    private val scrollBackAvailablePx: () -> Float = { Float.POSITIVE_INFINITY },
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        calculateScrollDistance(offset, size, containerSize, scrollBackAvailablePx())

    /** Explicit item restoration supplies the destination's boundary, not the old viewport's. */
    fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float, scrollBackAvailablePx: Float): Float {
        val available = scrollBackAvailablePx.coerceAtLeast(0f)
        val topInset = if (available > 0f) scrolledTopInsetPx else topInsetPx
        val insetContainer = (containerSize - topInset - bottomInsetPx).coerceAtLeast(0f)
        val distance = delegate.calculateScrollDistance(offset - topInset, size, insetContainer)
        if (available > 0f) return distance.coerceAtLeast(-available)

        // Leaving the boundary enables the top fade and changes the delegate's pivot.
        // Only move forward as far as both regimes agree, otherwise the next frame can
        // request the inverse movement and oscillate back across the same boundary.
        val scrolledContainer = (containerSize - scrolledTopInsetPx - bottomInsetPx).coerceAtLeast(0f)
        val scrolledDistance = delegate.calculateScrollDistance(offset - scrolledTopInsetPx, size, scrolledContainer)
        return minOf(distance, scrolledDistance).coerceAtLeast(0f)
    }
}

/** Local transparent edge masks; the scroll-dependent top edge is read only during drawing. */
internal fun Modifier.listEdgeFadeMask(height: Dp, showTopFade: () -> Boolean): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val fade = height.toPx().coerceIn(0f, size.height)
    if (fade <= 0f) return@drawWithContent
    if (showTopFade()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            size = Size(size.width, fade),
            blendMode = BlendMode.DstIn,
        )
    }
    val top = size.height - fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = top,
            endY = size.height,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, fade),
        blendMode = BlendMode.DstIn,
    )
}
