package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Adapts the platform focus policy to the boundary and scrolled viewports. Insets of zero
 * leave the delegate's viewport unchanged. Clamp backwards movement at the actual first-row
 * boundary and read scroll position when calculating, not in composition.
 */
@OptIn(ExperimentalFoundationApi::class)
class InsetBringIntoViewSpec(
    private val delegate: BringIntoViewSpec,
    private val topInsetPx: Float,
    private val bottomInsetPx: Float,
    private val scrolledTopInsetPx: Float = topInsetPx,
    private val scrolledBottomInsetPx: Float = bottomInsetPx,
    private val scrollBackAvailablePx: () -> Float = { Float.POSITIVE_INFINITY },
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        calculateScrollDistance(offset, size, containerSize, scrollBackAvailablePx())

    /** Explicit item restoration supplies the destination's boundary, not the old viewport's. */
    fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float, scrollBackAvailablePx: Float): Float {
        val available = scrollBackAvailablePx.coerceAtLeast(0f)
        val topInset = if (available > 0f) scrolledTopInsetPx else topInsetPx
        val bottomInset = if (available > 0f) scrolledBottomInsetPx else bottomInsetPx
        val insetContainer = (containerSize - topInset - bottomInset).coerceAtLeast(0f)
        val distance = delegate.calculateScrollDistance(offset - topInset, size, insetContainer)
        if (available > 0f) return distance.coerceAtLeast(-available)

        // Leaving the boundary enables the top fade and changes the delegate's pivot.
        // Only move forward as far as both regimes agree, otherwise the next frame can
        // request the inverse movement and oscillate back across the same boundary.
        val scrolledContainer = (containerSize - scrolledTopInsetPx - scrolledBottomInsetPx).coerceAtLeast(0f)
        val scrolledDistance = delegate.calculateScrollDistance(offset - scrolledTopInsetPx, size, scrolledContainer)
        val forward = minOf(distance, scrolledDistance).coerceAtLeast(0f)
        // Browse lists use the agreement above. Only lists that keep extra scrolled top room
        // (the player schedule) also hold smaller moves while the item clears the bottom reserve.
        val clearsBottom = offset + size <= containerSize - bottomInsetPx
        return if (clearsBottom && forward < scrolledTopInsetPx - topInsetPx) 0f else forward
    }
}
