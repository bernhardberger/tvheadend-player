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
 * Fades drawn content to transparent at logical-leading without adding a UI node.
 */
internal fun Modifier.navigationEdgeFadeMask(
    width: Dp,
    maskEnabled: () -> Boolean = { true },
    availableWidthPx: DrawScope.() -> Float = { width.toPx() },
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
    if (fadeWidth <= 0f) return@drawWithContent

    drawNavigationFadeMask(fadeWidth)
}

private fun DrawScope.drawNavigationFadeMask(
    width: Float,
) {
    if (width <= 0f) return

    val ltr = layoutDirection == LayoutDirection.Ltr
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
