package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

// Browse lists run to the bottom edge of the screen and fade out where rows continue.
// The fade keeps to the last row's height or so, so the row cut by the edge still reads
// as the list going on, as Google TV's lists do. Rows that scrolled away above fade
// under a shorter band, shown only while there are any.

/** Fade where rows continue past the bottom edge of the screen. */
internal val ListBottomFadeHeight = 80.dp

/** Fade where rows have scrolled away above the list. */
internal val ListTopFadeHeight = 48.dp

/** Extra room between the top fade and the focused row's native scale. */
internal val ListFocusBreathingRoom = 8.dp

/**
 * Bottom band kept clear of focused rows, and the list's bottom content padding: deeper than
 * the fade, so the rows after the focused one stay in view above it.
 */
internal val ListBottomReserve = 148.dp

/** Top band kept clear of focused rows while earlier rows exist. */
internal val ListScrolledTopReserve = ListTopFadeHeight + ListFocusBreathingRoom

/**
 * Bring-into-view policy for a list between fades: the delegate (the platform's TV pivot
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
        val forward = minOf(distance, scrolledDistance).coerceAtLeast(0f)
        // A move shorter than the extra top room the scrolled regime reserves would only
        // tuck the first row under the fade; hold the boundary until the pivot wants more,
        // as long as the item already clears the bottom reserve.
        val clearsBottom = offset + size <= containerSize - bottomInsetPx
        return if (clearsBottom && forward < scrolledTopInsetPx - topInsetPx) 0f else forward
    }
}

/**
 * Focus scrolling for a list drawn with [listEdgeFades]: [topReserve] is the list's own top
 * content padding, kept until earlier rows exist; its bottom content padding is
 * [ListBottomReserve]. A viewport reaching [topOverflow] above the list's band, padded by that
 * much more at the top, keeps both top reserves measured from the band.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun rememberListEdgeBringIntoView(
    listState: LazyListState,
    topReserve: Dp,
    topOverflow: Dp = 0.dp,
): InsetBringIntoViewSpec {
    val platform = LocalBringIntoViewSpec.current
    val density = LocalDensity.current
    return remember(platform, density, listState, topReserve, topOverflow) {
        with(density) {
            InsetBringIntoViewSpec(
                delegate = platform,
                topInsetPx = (topOverflow + topReserve).toPx(),
                scrolledTopInsetPx = (topOverflow + ListScrolledTopReserve).toPx(),
                bottomInsetPx = ListBottomReserve.toPx(),
                scrollBackAvailablePx = {
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else Float.POSITIVE_INFINITY
                },
            )
        }
    }
}

/** Room beside a faded list for its focused row's native scale, which the mask's layer would clip. */
private val ListFocusOverhang = 24.dp

/**
 * Fades a list's edges only while rows continue past them; read during drawing. The mask's
 * offscreen layer reaches [ListFocusOverhang] past both sides of the list without changing its
 * layout, so a scaled focused row keeps its rounded ends. Without [topFade], a list that starts
 * at a whole row shows it unfaded.
 */
internal fun Modifier.listEdgeFades(listState: LazyListState, topFade: Boolean = true): Modifier = this
    .layout { measurable, constraints ->
        val overhang = ListFocusOverhang.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = 2 * overhang))
        layout(placeable.width - 2 * overhang, placeable.height) { placeable.placeRelative(-overhang, 0) }
    }
    .listEdgeFadeMask(ListTopFadeHeight, ListBottomFadeHeight, { topFade && listState.canScrollBackward }) {
        listState.canScrollForward
    }
    .layout { measurable, constraints ->
        val overhang = ListFocusOverhang.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = -2 * overhang))
        layout(placeable.width + 2 * overhang, placeable.height) { placeable.placeRelative(overhang, 0) }
    }

/** Local transparent edge masks; the scroll-dependent edges are read only during drawing. */
internal fun Modifier.listEdgeFadeMask(
    topHeight: Dp,
    bottomHeight: Dp,
    showTopFade: () -> Boolean,
    showBottomFade: () -> Boolean,
): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val top = topHeight.toPx().coerceIn(0f, size.height)
    if (top > 0f && showTopFade()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = top,
            ),
            size = Size(size.width, top),
            blendMode = BlendMode.DstIn,
        )
    }
    val bottom = bottomHeight.toPx().coerceIn(0f, size.height)
    if (bottom > 0f && showBottomFade()) {
        val start = size.height - bottom
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = start,
                endY = size.height,
            ),
            topLeft = Offset(0f, start),
            size = Size(size.width, bottom),
            blendMode = BlendMode.DstIn,
        )
    }
}
