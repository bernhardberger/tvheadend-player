package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Tab geometry shared by the overflow peek and its protected edge masks. */
internal class NavigationTabOverflowState {
    private val viewportBounds = mutableStateOf(Rect.Zero)
    private val tabBounds = mutableStateMapOf<Int, Rect>()

    fun updateViewportBounds(coordinates: LayoutCoordinates) {
        viewportBounds.value = coordinates.unclippedBoundsInRoot()
    }

    fun updateTabBounds(index: Int, coordinates: LayoutCoordinates) {
        tabBounds[index] = coordinates.unclippedBoundsInRoot()
    }

    /**
     * Leave a cut tab rather than a gap at the unscrolled trailing edge. If the
     * overflowing tab has too little visible, cut the preceding unprotected tab
     * halfway instead. Measure from the first tab so scrolling cannot resize the row.
     */
    fun trailingHintWidthPx(
        fullWidthPx: Float,
        protectedIndices: Set<Int>,
        minimumPeekPx: Float,
        layoutDirection: LayoutDirection,
    ): Float {
        val viewport = viewportBounds.value
        val first = tabBounds[0]
        if (viewport.width <= 0f || first == null) return fullWidthPx
        val origin = first.spaceFromLeadingEdge(viewport, layoutDirection)
        val spans = tabBounds.toSortedMap().map { (index, bounds) ->
            val start = bounds.spaceFromLeadingEdge(viewport, layoutDirection) - origin
            Triple(index, start, start + bounds.width)
        }
        val cut = spans.indexOfFirst { (_, _, end) -> end > fullWidthPx }
        if (cut < 0 || fullWidthPx - spans[cut].second >= minimumPeekPx) return fullWidthPx
        val (index, start, end) = spans.getOrNull(cut - 1) ?: return fullWidthPx
        if (index in protectedIndices || start <= 0f) return fullWidthPx
        return (start + (end - start) / 2f).coerceAtMost(fullWidthPx)
    }

    fun availableFadeWidthPx(
        activeIndex: Int,
        focusedIndex: Int,
        layoutDirection: LayoutDirection,
        maximumWidthPx: Float,
        trailing: Boolean = false,
    ): Float {
        val viewport = viewportBounds.value
        val active = tabBounds[activeIndex] ?: return 0f
        val focused = tabBounds[focusedIndex]
        val edgeDirection = if (!trailing) layoutDirection else when (layoutDirection) {
            LayoutDirection.Ltr -> LayoutDirection.Rtl
            LayoutDirection.Rtl -> LayoutDirection.Ltr
        }
        val hasHiddenInactiveTab = tabBounds.any { (index, bounds) ->
            index != activeIndex && index != focusedIndex && when (edgeDirection) {
                LayoutDirection.Ltr -> bounds.left < viewport.left
                LayoutDirection.Rtl -> bounds.right > viewport.right
            }
        }
        if (!hasHiddenInactiveTab) return 0f

        val activeSpace = active.spaceFromLeadingEdge(viewport, edgeDirection)
        val focusedSpace = focused?.spaceFromLeadingEdge(viewport, edgeDirection)
        return minOf(activeSpace, focusedSpace ?: activeSpace).coerceIn(0f, maximumWidthPx)
    }
}

private fun LayoutCoordinates.unclippedBoundsInRoot(): Rect =
    Rect(offset = positionInRoot(), size = Size(size.width.toFloat(), size.height.toFloat()))

private fun Rect.spaceFromLeadingEdge(viewport: Rect, layoutDirection: LayoutDirection): Float = when (layoutDirection) {
    LayoutDirection.Ltr -> left - viewport.left
    LayoutDirection.Rtl -> viewport.right - right
}

/**
 * Fades drawn content at logical edges without adding a UI node. Trailing is opt-in.
 */
internal fun Modifier.navigationEdgeFadeMask(
    width: Dp,
    maskEnabled: () -> Boolean = { true },
    availableWidthPx: DrawScope.() -> Float = { width.toPx() },
    availableTrailingWidthPx: DrawScope.() -> Float = { 0f },
): Modifier = graphicsLayer {
    compositingStrategy = if (maskEnabled()) {
        CompositingStrategy.Offscreen
    } else {
        CompositingStrategy.Auto
    }
}.drawWithContent {
    drawContent()
    if (!maskEnabled()) return@drawWithContent

    val fadeWidth = availableWidthPx().coerceIn(0f, width.toPx())
    drawNavigationFadeMask(fadeWidth)
    drawNavigationFadeMask(availableTrailingWidthPx().coerceIn(0f, width.toPx()), trailing = true)
}

private fun DrawScope.drawNavigationFadeMask(
    width: Float,
    trailing: Boolean = false,
) {
    if (width <= 0f) return

    val ltr = (layoutDirection == LayoutDirection.Ltr) != trailing
    val leadingLeft = if (ltr) 0f else size.width - width
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = if (ltr) 0f else size.width,
            endX = if (ltr) width else size.width - width,
        ),
        topLeft = Offset(leadingLeft, 0f),
        size = Size(width, size.height),
        blendMode = BlendMode.DstIn,
    )
}
