package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.TvPanelBrowseAlpha

/** Host-sized layer. Coordinates are measured from the host top/end, including safe-area insets. */
@Composable
fun PlayerTopEndScrim(clusterWidthFromEnd: Dp, clusterBottom: Dp, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.scrim
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val start = (size.width - clusterWidthFromEnd.toPx() - 200.dp.toPx()).coerceAtLeast(0f)
        val peak = (size.width - clusterWidthFromEnd.toPx()).coerceAtLeast(start + 1f)
        drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = TvPanelBrowseAlpha)), start, peak))
        val bottom = clusterBottom.toPx().coerceAtLeast(0f)
        val end = bottom + 24.dp.toPx()
        // Start fading above the lower chips; their own fill retains contrast without a dark shelf.
        drawRect(Brush.verticalGradient(0f to color, (bottom / 3f / end) to color, 1f to color.copy(alpha = 0f),
            startY = 0f, endY = end), blendMode = BlendMode.DstIn)
    }
}
