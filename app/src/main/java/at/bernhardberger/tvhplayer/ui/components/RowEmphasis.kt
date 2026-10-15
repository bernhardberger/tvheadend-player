package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TvPassiveAlpha
import at.bernhardberger.tvhplayer.ui.player.PlayerMotion
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

internal const val RowChangeMs = 300
internal val RowChangeMotion = tween<Float>(RowChangeMs, easing = PlayerMotion.Standard)
internal const val RowDimAlpha = 0.4f
internal const val RowHeaderQuietAlpha = TvPassiveAlpha
internal const val RowHeaderQuietScale = 14f / 24f

/** A row's emphasis at [distance] rows from the shared position. */
internal fun rowEmphasis(distance: Float): Float = (1f - abs(distance)).coerceIn(0f, 1f)

/** Keep quiet headings adjacent to cards which no longer grow into [focusOverflow]. */
internal fun Modifier.rowHeaderEmphasis(
    quietScale: Float = RowHeaderQuietScale,
    focusOverflow: Dp = 0.dp,
    emphasis: () -> Float,
): Modifier = layout { measurable, constraints ->
    val heading = measurable.measure(constraints)
    layout(heading.width, heading.height) {
        // Placement, not translation inside Text's layer: ellipsized Text clips that layer.
        // Keep the reserved section height fixed so the scroll anchors don't change mid-motion.
        heading.placeRelative(0, ((1f - emphasis()) * focusOverflow.toPx()).toInt())
    }
}.graphicsLayer {
    val value = emphasis()
    scaleX = lerp(quietScale, 1f, value)
    scaleY = scaleX
    transformOrigin = TransformOrigin(0f, 1f)
    alpha = lerp(RowHeaderQuietAlpha, 1f, value)
}

internal fun Modifier.rowContentEmphasis(emphasis: () -> Float): Modifier = graphicsLayer {
    val value = emphasis()
    // Fade the composed row, not each overlapping draw (art, plate, picon). Rows reserve
    // focus overflow inside their bounds, including during the native card scale transition.
    // At full emphasis no offscreen buffer is needed and overflow remains unrestricted.
    compositingStrategy = if (value < 1f) CompositingStrategy.Offscreen else CompositingStrategy.ModulateAlpha
    alpha = lerp(RowDimAlpha, 1f, value)
}

/** The scroll at fractional section [position], moving linearly between the sections' scroll anchors. */
internal fun scrollAtSectionPosition(position: Float, anchors: List<Float>): Float {
    if (anchors.isEmpty()) return 0f
    val clamped = position.coerceIn(0f, anchors.lastIndex.toFloat())
    val below = floor(clamped).toInt()
    val above = ceil(clamped).toInt()
    return lerp(anchors[below], anchors[above], clamped - below)
}
