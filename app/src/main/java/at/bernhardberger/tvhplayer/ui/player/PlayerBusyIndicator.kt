package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.R

/** One unboxed ring over video. Its caller centres it independently of the chrome. */
@Composable
internal fun PlayerBusyIndicator(status: PlayerBusyStatus?, modifier: Modifier = Modifier) {
    val description = status?.let {
        stringResource(if (it == PlayerBusyStatus.TUNING) R.string.player_tuning else R.string.player_buffering)
    }
    AnimatedVisibility(
        visible = status != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier,
    ) {
        // Only this stable node speaks. The primitive's progress semantics are cleared, so neither
        // its animation nor chrome changes publish a second buffering/tuning description.
        Box(Modifier.testTag("player-busy-indicator").clearAndSetSemantics {
            if (description != null) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
        }) {
            PlayerBusyRing()
        }
    }
}

/** Shared visual only; visibility, timing and announcements remain caller-owned. */
@Composable
internal fun PlayerBusyRing(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(44.dp).drawWithCache {
            val outline = 1.dp.toPx()
            val paint = Paint().apply { colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.65f)) }
            onDrawWithContent {
                // Dilate only the moving arc, not its bounding circle: no disk or track
                // behind the ring, even on bright video. One indeterminate animation.
                drawContext.canvas.saveLayer(Rect(-outline, -outline, size.width + outline, size.height + outline), paint)
                translate(left = -outline) { this@onDrawWithContent.drawContent() }
                translate(left = outline) { this@onDrawWithContent.drawContent() }
                translate(top = -outline) { this@onDrawWithContent.drawContent() }
                translate(top = outline) { this@onDrawWithContent.drawContent() }
                drawContext.canvas.restore()
                drawContent()
            }
        },
        color = Color.White,
        trackColor = Color.Transparent,
        strokeWidth = 3.dp,
    )
}
