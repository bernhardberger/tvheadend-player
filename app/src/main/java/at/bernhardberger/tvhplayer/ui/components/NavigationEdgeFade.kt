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

/** Fades drawn content to transparent at logical-leading without adding a UI node. */
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

    val leadingLeft = if (layoutDirection == LayoutDirection.Ltr) {
        0f
    } else {
        size.width - width
    }
    val colors = if (layoutDirection == LayoutDirection.Ltr) {
        listOf(Color.Transparent, Color.Black)
    } else {
        listOf(Color.Black, Color.Transparent)
    }
    drawRect(
        brush = Brush.horizontalGradient(
            colors = colors,
            startX = leadingLeft,
            endX = leadingLeft + width,
        ),
        topLeft = Offset(leadingLeft, 0f),
        size = Size(width, size.height),
        blendMode = BlendMode.DstIn,
    )
}
