package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

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
