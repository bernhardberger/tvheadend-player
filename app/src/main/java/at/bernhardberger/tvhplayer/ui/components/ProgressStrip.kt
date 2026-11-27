package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.TvProgressStripHeight
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import kotlin.math.roundToInt

@Composable
fun ProgressStrip(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTrackAlpha),
    height: Dp = TvProgressStripHeight,
) {
    val boundedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(height)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(trackColor)
            .progressSemantics(boundedProgress),
    ) {
        Box(
            Modifier
                .fillMaxWidth(boundedProgress)
                .fillMaxHeight()
                .background(color),
        )
    }
}

/**
 * Same track and fill, but a ticking value is consumed in layout and accessibility only. A null
 * progress is an expired window: no fill and no range.
 */
@Composable
internal fun ProgressStrip(
    progress: () -> Float?,
    modifier: Modifier,
    color: Color,
    height: Dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TvTrackAlpha))
            .semantics(mergeDescendants = true) {
                progress()?.let { progressBarRangeInfo = ProgressBarRangeInfo(it.coerceIn(0f, 1f), 0f..1f) }
            },
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val size = if (!constraints.hasBoundedWidth) constraints else {
                        val width = (constraints.maxWidth * (progress() ?: 0f).coerceIn(0f, 1f)).roundToInt()
                            .coerceIn(constraints.minWidth, constraints.maxWidth)
                        constraints.copy(minWidth = width, maxWidth = width)
                    }
                    val fill = measurable.measure(size)
                    layout(fill.width, fill.height) { fill.placeRelative(0, 0) }
                }
                .fillMaxHeight()
                .background(color),
        )
    }
}
