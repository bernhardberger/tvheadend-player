package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/** Draws a decorative logical-leading veil without adding layout, input, or semantics nodes. */
internal fun Modifier.navigationEdgeVeil(
    width: Dp,
    startAlpha: Float,
    availableWidthPx: DrawScope.() -> Float = { width.toPx() },
    protectedBoundsPx: DrawScope.() -> Rect? = { null },
): Modifier = drawWithContent {
    drawContent()

    val veilWidth = availableWidthPx().coerceIn(0f, width.toPx())
    if (veilWidth <= 0f) return@drawWithContent

    val protectedBounds = protectedBoundsPx()
    val protectedTop = protectedBounds?.top?.coerceIn(0f, size.height) ?: size.height
    val protectedBottom = protectedBounds?.bottom?.coerceIn(0f, size.height) ?: size.height
    drawNavigationVeil(veilWidth, startAlpha, top = 0f, bottom = protectedTop)

    if (protectedBounds != null && protectedBottom > protectedTop) {
        val widthBeforeProtected = if (layoutDirection == LayoutDirection.Ltr) {
            protectedBounds.left
        } else {
            size.width - protectedBounds.right
        }.coerceIn(0f, veilWidth)
        drawNavigationVeil(
            width = widthBeforeProtected,
            startAlpha = startAlpha,
            top = protectedTop,
            bottom = protectedBottom,
        )
    }
    drawNavigationVeil(veilWidth, startAlpha, top = protectedBottom, bottom = size.height)
}

private fun DrawScope.drawNavigationVeil(
    width: Float,
    startAlpha: Float,
    top: Float,
    bottom: Float,
) {
    if (width <= 0f || bottom <= top) return

    val leadingLeft = if (layoutDirection == LayoutDirection.Ltr) {
        0f
    } else {
        size.width - width
    }
    val colors = if (layoutDirection == LayoutDirection.Ltr) {
        listOf(Color.Black.copy(alpha = startAlpha), Color.Transparent)
    } else {
        listOf(Color.Transparent, Color.Black.copy(alpha = startAlpha))
    }
    drawRect(
        brush = Brush.horizontalGradient(
            colors = colors,
            startX = leadingLeft,
            endX = leadingLeft + width,
        ),
        topLeft = Offset(leadingLeft, top),
        size = Size(width, bottom - top),
    )
}

internal val LocalNavigationVeilFocusProtection =
    staticCompositionLocalOf<((Rect?) -> Unit)?> { null }
