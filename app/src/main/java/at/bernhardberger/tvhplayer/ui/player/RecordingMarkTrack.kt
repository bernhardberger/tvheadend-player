package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkKind

/** One mark on the bar, as fractions of the bar's length; a chapter's [end] equals its [start]. */
@Immutable
data class TimelineMarkSpan(val kind: RecordingMarkKind, val start: Float, val end: Float)

/**
 * A recording's marks drawn on its bar. [selected] indexes [spans]: the mark the open marks row is on,
 * at its end with [selectedEnd]. While the marks row is [open], cuts show their whole span.
 */
@Immutable
data class TimelineMarks(
    val spans: List<TimelineMarkSpan>,
    val selected: Int? = null,
    val open: Boolean = false,
    val selectedEnd: Boolean = false,
)

/** [marks] on a bar [displayEndMs] long, [selected] among them (at its end with [selectedEnd]); null without marks or length. */
internal fun timelineMarks(
    marks: List<RecordingMark>,
    displayEndMs: Long,
    selected: RecordingMark? = null,
    open: Boolean = false,
    selectedEnd: Boolean = false,
): TimelineMarks? {
    if (marks.isEmpty() || displayEndMs <= 0) return null
    val spans = marks.map {
        TimelineMarkSpan(it.kind, it.startMs.toFloat() / displayEndMs, it.endMs.toFloat() / displayEndMs)
    }
    val index = marks.indexOf(selected).takeIf { it >= 0 }
    return TimelineMarks(spans, index, open, selectedEnd = index != null && selectedEnd)
}

private const val AdTintAlpha = 0.26f
private const val AdTintSelectedAlpha = 0.80f
private const val TickAlpha = 0.55f

/**
 * The marks layer inside the clipped bar, over its fills: ad breaks tint their span, chapters and
 * cuts tick their start (a cut's tick dashed). With the marks row open a cut's span is striped in
 * the bar's own colour where it lies, played or not, so it reads as the same bar set apart.
 */
@Composable
internal fun TimelineMarkLayer(
    marks: TimelineMarks,
    /** The fill's end, read only while drawing. */
    progress: () -> Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val tint = MaterialTheme.colorScheme.secondary
    val tick = with(LocalDensity.current) { 1.dp.toPx() }
    val dash = remember(tick) { PathEffect.dashPathEffect(floatArrayOf(tick * 2, tick * 2)) }
    // One progress for the whole row opening or closing; only the draw phase reads it.
    val opening = animateFloatAsState(
        targetValue = if (marks.open) 1f else 0f,
        animationSpec = if (marks.open) tween(PlayerMotion.MediumMs, easing = PlayerMotion.StandardDecelerate)
            else tween(PlayerMotion.ShortMs, easing = PlayerMotion.StandardAccelerate),
        label = "recording marks open",
    )
    Canvas(modifier.testTag("recording-marks-track")) {
        val open = opening.value
        val played = progress().coerceIn(0f, 1f) * size.width
        marks.spans.forEachIndexed { index, span ->
            val selected = index == marks.selected
            val left = span.start.coerceIn(0f, 1f) * size.width
            val right = span.end.coerceIn(0f, 1f) * size.width
            when (span.kind) {
                RecordingMarkKind.AD_BREAK -> drawRect(
                    tint.copy(alpha = if (selected) lerp(AdTintAlpha, AdTintSelectedAlpha, open) else AdTintAlpha),
                    Offset(left, 0f), Size(right - left, size.height),
                )
                RecordingMarkKind.CUT -> {
                    // The span grows out of the start tick, its stripes fading in as it goes.
                    val reach = left + (right - left) * open
                    if (open > 0f && reach > left) {
                        cutStripes(left, minOf(reach, played), fillColor, selected, playedPart = true, fade = open)
                        cutStripes(maxOf(left, played), reach, ink, selected, playedPart = false, fade = open)
                    }
                    if (span.start > 0f && span.start < 1f) {
                        if (selected && !marks.selectedEnd) drawLine(ink, Offset(left, 0f), Offset(left, size.height), tick * 2)
                        else drawLine(
                            ink.copy(alpha = TickAlpha), Offset(left, 0f), Offset(left, size.height), tick,
                            pathEffect = dash,
                        )
                    }
                    // The open row on the cut's end stop marks that end instead.
                    if (selected && marks.selectedEnd && span.end < 1f) {
                        drawLine(ink, Offset(right, 0f), Offset(right, size.height), tick * 2)
                    }
                }
                RecordingMarkKind.CHAPTER -> if (span.start > 0f && span.start < 1f) {
                    drawLine(ink.copy(alpha = TickAlpha), Offset(left, 0f), Offset(left, size.height), tick)
                }
            }
        }
    }
}

/**
 * Diagonal stripes between [left] and [right]: over the played fill a darker and a brighter shade of
 * [base] that keep its hue; over the unplayed track the track's grey a step darker and brighter. Never black or white.
 */
private fun DrawScope.cutStripes(
    left: Float, right: Float, base: Color, selected: Boolean, playedPart: Boolean, fade: Float = 1f,
) {
    if (right <= left) return
    val ground = (if (playedPart) base.shade(value = if (selected) 0.62f else 0.78f)
        else Color.Black.copy(alpha = if (selected) 0.45f else 0.25f)).let { it.copy(alpha = it.alpha * fade) }
    val stripe = (if (playedPart) base.shade(saturation = if (selected) 0.5f else 0.7f)
        else base.copy(alpha = if (selected) 0.75f else 0.42f)).let { it.copy(alpha = it.alpha * fade) }
    val period = 4.dp.toPx()
    val stroke = 1.5.dp.toPx()
    clipRect(left, 0f, right, size.height) {
        drawRect(ground, Offset(left, 0f), Size(right - left, size.height))
        // Aligned to the bar, not the span, so a span split at the fill continues its stripes.
        var x = (left / period).toInt() * period - size.height
        while (x < right) {
            drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
            x += period
        }
    }
}

/** The same hue with its saturation and value scaled: a darker or paler [this], never grey. */
private fun Color.shade(saturation: Float = 1f, value: Float = 1f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return Color.hsv(hsv[0], hsv[1] * saturation, hsv[2] * value, alpha)
}
