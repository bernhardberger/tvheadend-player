package at.bernhardberger.tvhplayer.ui.components

import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt

/**
 * Fills the node with a gradient [shader] drawn without dithering, over its content or under it.
 *
 * Compose dithers every gradient brush. On the G10 that cost about 20ms a frame across a grid of
 * card tints. Use this for short or steep ramps and for scrims over pictures or video, where an
 * 8-bit ramp shows no bands; keep a dithered brush for a large, slow, dark ramp over flat colour.
 * Snapshot state read in [shader] rebuilds it when it changes; [alpha] is read each frame without
 * rebuilding it.
 */
internal fun Modifier.unditheredGradient(
    over: Boolean = false,
    alpha: () -> Float = { 1f },
    shader: (Size) -> Shader,
): Modifier =
    drawWithCache {
        val paint = Paint().apply {
            isDither = false
            this.shader = shader(size)
        }
        onDrawWithContent {
            if (over) drawContent()
            paint.alpha = (alpha().coerceIn(0f, 1f) * 255).roundToInt()
            if (paint.alpha > 0) drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            if (!over) drawContent()
        }
    }
