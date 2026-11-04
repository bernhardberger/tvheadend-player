package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Bring-into-view policy for a list between fades: the delegate (the platform's TV pivot
 * spec or default) sees a viewport shortened by the current top inset and
 * [bottomInsetPx] at the end, so native focus scrolling keeps a focused row inside
 * the readable band between the fades. Read scroll position when calculating, not
 * in composition. Clamp at the actual first-row boundary so returning there removes
 * the top fade without a residual backwards request from the native focus scale.
 */
@OptIn(ExperimentalFoundationApi::class)
class InsetBringIntoViewSpec(
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
