package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/** Space between compact zap cards in a row or grid. */
internal val CompactZapCardGap = 24.dp

/**
 * A card row's viewport reaches this far past both screen edges so the next cards of a held
 * D-pad are already composed and placed; otherwise each step past the edge runs a
 * synchronous beyond-bounds search inside key dispatch and the row stalls.
 */
internal val CompactZapRowOffscreen = 440.dp

/** Measures the row [offscreenPx] wider on both sides while its slot keeps the given width. */
internal fun Modifier.overflowingRowViewport(offscreenPx: Int): Modifier = layout { measurable, constraints ->
    check(constraints.hasBoundedWidth) { "A card row needs a bounded width" }
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + 2 * offscreenPx,
            maxWidth = constraints.maxWidth + 2 * offscreenPx,
        ),
    )
    layout((placeable.width - 2 * offscreenPx).coerceAtLeast(0), placeable.height) { placeable.place(-offscreenPx, 0) }
}

/** Keeps the focused card on the start keyline: the row scrolls, and at its end focus moves on. */
@OptIn(ExperimentalFoundationApi::class)
internal class StartKeylineBringIntoViewSpec(private val keylinePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - keylinePx
}
