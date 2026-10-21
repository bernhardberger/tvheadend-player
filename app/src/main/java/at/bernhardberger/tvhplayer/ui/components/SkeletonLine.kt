package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import kotlin.math.PI
import kotlin.math.cos

private const val SkeletonLineWidthFraction = 0.6f
// The bar covers the letters of the line, not its leading.
private const val SkeletonBarShare = 0.8f
private const val SkeletonPulsePeriodMillis = 1_600L
private const val SkeletonPulseLow = 0.5f

/**
 * A line of text still loading: a rounded bar in its place, as tall as the letters of [style] and
 * as high as its line, so the text takes its place without anything moving. It pulses slowly, in
 * step with every other line, and is passive: it takes no focus and says nothing. The shared
 * skeleton of docs/DESIGN.md's destination loading.
 */
@Composable
internal fun SkeletonLine(
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    widthFraction: Float = SkeletonLineWidthFraction,
) {
    val color = LocalContentColor.current.copy(alpha = TvTrackAlpha)
    val pulse = rememberSkeletonPulse()
    val density = LocalDensity.current
    val fontSize = with(density) { style.fontSize.toDp() }
    val lineHeight = if (style.lineHeight.isSpecified) with(density) { style.lineHeight.toDp() } else fontSize * 1.4f
    Box(
        modifier.fillMaxWidth(widthFraction).height(lineHeight).clearAndSetSemantics {}.drawBehind {
            val bar = fontSize.toPx() * SkeletonBarShare
            drawRoundRect(
                color,
                topLeft = Offset(0f, (size.height - bar) / 2f),
                size = Size(size.width, bar),
                cornerRadius = CornerRadius(bar / 2f),
                alpha = pulse.floatValue,
            )
        },
    )
}

/** Read only while drawing, so the pulse redraws the bar without recomposing it. */
@Composable
private fun rememberSkeletonPulse(): FloatState {
    val pulse = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        // With animations off, the bar stays still.
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        while (true) withInfiniteAnimationFrameMillis { pulse.floatValue = skeletonPulse(it) }
    }
    return pulse
}

/** Full strength down to [SkeletonPulseLow] and back, on the frame clock every line shares. */
internal fun skeletonPulse(frameMillis: Long): Float {
    val phase = (frameMillis % SkeletonPulsePeriodMillis).toFloat() / SkeletonPulsePeriodMillis
    return 1f - (1f - SkeletonPulseLow) * (1f - cos(2f * PI.toFloat() * phase)) / 2f
}
