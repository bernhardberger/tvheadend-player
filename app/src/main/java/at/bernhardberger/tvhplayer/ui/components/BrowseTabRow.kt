package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.TabColors
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowScope
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTextSecondaryAlpha

/** Native TV tabs with one travelling focus shape, shared by the three browse destinations. */
@Composable
internal fun BrowseTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable TabRowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier
            // TabRow measures its content at natural width. Don't let a caller's minimum
            // width implicitly centre a short row inside the title's leading anchor.
            .wrapContentWidth(Alignment.Start)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        indicator = { positions, hasFocus ->
            val position = positions.getOrNull(selectedTabIndex)
            if (position != null) {
                val target = with(density) {
                    Rect(position.left.toPx(), position.top.toPx(), position.right.toPx(), position.bottom.toPx())
                }
                val bounds = animateRectAsState(target, tween(150), label = "Browse tab focus")
                Canvas(Modifier.fillMaxSize().zIndex(if (hasFocus) 1f else -1f)) {
                    val rect = bounds.value
                    val left = if (layoutDirection == LayoutDirection.Ltr) rect.left else size.width - rect.right
                    // XOR leaves dark backdrop-shaped glyphs inside the light pill, exactly
                    // where it covers the text. Unlike Difference it also works on API 28.
                    // There is one text/semantics tree and motion only invalidates drawing.
                    drawRoundRect(
                        color = if (hasFocus) scheme.onSurface else scheme.secondaryContainer.copy(alpha = 0.4f),
                        topLeft = Offset(left, rect.top),
                        size = rect.size,
                        cornerRadius = CornerRadius(rect.height / 2),
                        blendMode = if (hasFocus) BlendMode.Xor else BlendMode.SrcOver,
                    )
                }
            }
        },
        tabs = tabs,
    )
}

@Composable
internal fun browseTabColors(): TabColors {
    val foreground = MaterialTheme.colorScheme.onSurface
    return TabDefaults.pillIndicatorTabColors(
        contentColor = foreground.copy(alpha = TvTextSecondaryAlpha),
        inactiveContentColor = foreground.copy(alpha = TvTextSecondaryAlpha),
        selectedContentColor = foreground,
        focusedContentColor = foreground,
        focusedSelectedContentColor = foreground,
        disabledContentColor = foreground.copy(alpha = TvTextDisabledAlpha),
        disabledInactiveContentColor = foreground.copy(alpha = TvTextDisabledAlpha),
        disabledSelectedContentColor = foreground.copy(alpha = TvTextDisabledAlpha),
    )
}
