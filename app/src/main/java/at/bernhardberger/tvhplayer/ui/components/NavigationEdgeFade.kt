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
 * Fades drawn content to transparent at logical-leading without adding a UI node. The fade starts
 * [leadingInsetPx] in from the leading edge, where the screen's edge lies when the content reaches
 * beyond it; anything before that is cleared.
 */
internal fun Modifier.navigationEdgeFadeMask(
    width: Dp,
    maskEnabled: () -> Boolean = { true },
    availableWidthPx: DrawScope.() -> Float = { width.toPx() },
    leadingInsetPx: () -> Float = { 0f },
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

    drawNavigationFadeMask(fadeWidth, leadingInsetPx().coerceIn(0f, fadeWidth))
}

private fun DrawScope.drawNavigationFadeMask(
    width: Float,
    inset: Float,
) {
    if (width <= 0f) return

    val ltr = layoutDirection == LayoutDirection.Ltr
    val leadingLeft = if (ltr) 0f else size.width - width
    // Clamped, so whatever lies before the inset stays transparent.
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = if (ltr) inset else size.width - inset,
            endX = if (ltr) width else size.width - width,
        ),
        topLeft = Offset(leadingLeft, 0f),
        size = Size(width, size.height),
        blendMode = BlendMode.DstIn,
    )
}
