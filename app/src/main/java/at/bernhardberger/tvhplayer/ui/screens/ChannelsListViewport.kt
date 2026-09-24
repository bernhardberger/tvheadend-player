package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.ui.TvSpacing24
import at.bernhardberger.tvhplayer.ui.components.ChannelRowEdgeInset
import at.bernhardberger.tvhplayer.ui.components.ChannelRowWidth

// Channels geometry, expressed relative to the shell's safe-area inset.
// The shell passes 24dp leading and 32dp top; the tags and rows sit 12dp further in
// (x116 on the 960x540 canvas) so the 1.05 focus scale has room inside the list.

/** Tag row band above the list: tags at the top inset, rows start 48dp below it. */
internal val ChannelsTagSectionHeight = 44.dp

/** Top content reserve inside the list so the first focused row's scale is not clipped. */
internal val ChannelsListTopReserve = 4.dp

/** Height of the transparent fade where scrolling rows run out at the screen bottom. */
internal val ChannelsListFadeHeight = 48.dp

/** Extra room above the fade so a focused row's scale never touches it. */
internal val ChannelsListFocusBreathingRoom = 8.dp

/** Bottom band kept clear of focused rows: fade plus breathing room. */
internal val ChannelsListBottomReserve = ChannelsListFadeHeight + ChannelsListFocusBreathingRoom

/** List column including the horizontal focus-scale reserve on both sides of the rows. */
internal val ChannelsListColumnWidth = ChannelRowWidth + ChannelRowEdgeInset * 2

/** Gap between the list column and the focused-programme details. */
internal val ChannelsListToDetailsGap = TvSpacing24

/**
 * Bring-into-view policy for the list: the delegate (the platform's TV pivot
 * spec or default) sees a viewport shortened by [topInsetPx] at the start and
 * [bottomInsetPx] at the end, so native focus scrolling keeps a focused row inside
 * the readable band above the bottom fade and below the top reserve, while the
 * list itself still draws to the screen edge beneath the fade.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class InsetBringIntoViewSpec(
    private val delegate: BringIntoViewSpec,
    private val topInsetPx: Float,
    private val bottomInsetPx: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val insetContainer = (containerSize - topInsetPx - bottomInsetPx).coerceAtLeast(0f)
        return delegate.calculateScrollDistance(offset - topInsetPx, size, insetContainer)
    }
}

/** Fades drawn content to transparent across the bottom [height] without adding a node. */
internal fun Modifier.bottomEdgeFadeMask(height: Dp): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val fade = height.toPx().coerceIn(0f, size.height)
    if (fade <= 0f) return@drawWithContent
    val top = size.height - fade
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = top,
            endY = size.height,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, fade),
        blendMode = BlendMode.DstIn,
    )
}
