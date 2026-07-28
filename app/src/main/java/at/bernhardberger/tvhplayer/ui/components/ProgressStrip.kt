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
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.ui.TvProgressStripHeight
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha

@Composable
fun ProgressStrip(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = TvTrackAlpha),
) {
    val boundedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(TvProgressStripHeight)
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
