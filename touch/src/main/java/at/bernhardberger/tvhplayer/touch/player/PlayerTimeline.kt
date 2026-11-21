@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import at.bernhardberger.tvhplayer.touch.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun PlayerTimeline(
    timeline: LiveTimeline,
    status: LivePlayerStatus,
    layout: PlayerLayout,
    visibleControls: Boolean,
    preview: Int?,
    onPreviewChange: (Int?) -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = status == LivePlayerStatus.Playing
    val seekable = playing && timeline.bufferStart != null && timeline.bufferStart < timeline.live
    val allowDrag = visibleControls && seekable
    val position = if (status == LivePlayerStatus.Tuning) timeline.live else preview ?: timeline.position
    val bottomOffset = if (layout.upright) 2.dp else 24.dp
    val gesture = if (allowDrag) timelineDrag(timeline, position, onPreviewChange, onSeek) else Modifier
    BoxWithConstraints(modifier.testTag("player-timeline").then(gesture)) {
        val trackY = maxHeight - bottomOffset
        val markerX = maxWidth * timeline.fraction(position)
        Track(timeline, position, trackY, status, layout, visibleControls, preview != null, Modifier.fillMaxSize())
        if (visibleControls && (playing || status == LivePlayerStatus.Tuning)) {
            PositionThumb(markerX, trackY, layout, preview != null)
        }
        if (preview != null) {
            DragLabel(
                playerClock(timeline, position), position == timeline.live, layout.upright,
                markerX, trackY, maxWidth,
            )
        }
    }
}

@Composable
private fun timelineDrag(
    timeline: LiveTimeline,
    position: Int,
    onPreviewChange: (Int?) -> Unit,
    onSeek: (Int) -> Unit,
): Modifier {
    val first = checkNotNull(timeline.bufferStart)
    val label = stringResource(R.string.player_timeline)
    val positionLabel = behindLive(timeline.live - position)
    val latestTimeline by rememberUpdatedState(timeline)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestPreview by rememberUpdatedState(onPreviewChange)
    val snapPx = with(LocalDensity.current) { 12.dp.toPx() }
    return Modifier.excludeBarEnds()
        .semantics {
            contentDescription = label
            stateDescription = positionLabel
            progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), first.toFloat()..timeline.live.toFloat())
            setProgress { target ->
                val seconds = target.roundToInt().coerceIn(first, timeline.live)
                if (seconds == timeline.position) false else {
                    onSeek(seconds)
                    true
                }
            }
        }
        .pointerInput(snapPx) {
            var target: Int? = null
            detectHorizontalDragGestures(
                onDragStart = { target = null },
                onHorizontalDrag = { change, _ ->
                    target = latestTimeline.dragTarget(change.position.x, size.width, snapPx)
                    latestPreview(target)
                },
                onDragCancel = { latestPreview(null) },
                onDragEnd = {
                    latestPreview(null)
                    target?.takeUnless { it == latestTimeline.position }?.let(latestSeek)
                },
            )
        }
}

private fun LiveTimeline.dragTarget(x: Float, width: Int, snapPx: Float): Int? {
    val first = bufferStart ?: return null
    return when {
        width <= 0 -> null
        abs(x - width * fraction(live)) <= snapPx -> live
        abs(x - width * fraction(position)) <= snapPx -> position
        else -> (x / width * duration).roundToInt().coerceIn(first, live)
    }
}

@Composable
private fun Track(
    timeline: LiveTimeline,
    position: Int,
    y: Dp,
    status: LivePlayerStatus,
    layout: PlayerLayout,
    visibleControls: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val orange = MaterialTheme.colorScheme.tertiary
    val light = MaterialTheme.colorScheme.onSurface
    val buffer = timeline.bufferStart
    val playing = status == LivePlayerStatus.Playing
    Canvas(modifier) {
        val center = y.toPx()
        val mark = size.width * timeline.fraction(position)
        val edge = size.width * timeline.fraction(timeline.live)
        val thin = when {
            layout.upright || !visibleControls -> 2.dp
            layout == PlayerLayout.Tablet -> 4.dp
            else -> 3.dp
        }.toPx()
        fun segment(start: Float, end: Float, ink: Color, thickness: Float) {
            if (end > start) drawLine(ink, Offset(start, center), Offset(end, center), thickness, StrokeCap.Round)
        }
        segment(0f, size.width, light.copy(alpha = 0.34f), thin)
        if (status != LivePlayerStatus.Tuning) {
            val first = if (playing && buffer != null) size.width * timeline.fraction(buffer) else edge
            segment(0f, first, light, thin)
            if (playing && buffer != null) {
                val thick = if (!visibleControls) thin else layout.trackThickness.toPx()
                segment(first, mark, orange, thick)
                segment(mark, edge, light.copy(alpha = 0.42f), thick)
            }
        }
        if (dragging) {
            val current = size.width * timeline.fraction(timeline.position)
            drawLine(light, Offset(current, center - 7.dp.toPx()), Offset(current, center + 7.dp.toPx()), 2.dp.toPx())
        }
    }
}

@Composable
private fun PositionThumb(x: Dp, y: Dp, layout: PlayerLayout, dragging: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val width = if (dragging) 3.dp else if (layout.upright) 5.dp else 6.dp
    val height = when (layout) {
        PlayerLayout.Tablet -> 36.dp
        PlayerLayout.PhoneLandscape -> 28.dp
        else -> 24.dp
    }
    SliderDefaults.Thumb(
        interactionSource = interaction,
        modifier = Modifier.absoluteOffset(x - width / 2, y - height / 2),
        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.tertiary),
        thumbSize = DpSize(width, height),
    )
}

@Composable
private fun DragLabel(time: String, live: Boolean, compact: Boolean, markerX: Dp, trackY: Dp, trackWidth: Dp) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val labelWidth = 108.dp.coerceAtMost(trackWidth)
    val labelRise = when {
        compact -> 74.dp
        live -> 85.dp
        else -> 69.dp
    }
    val horizontalPadding = if (compact) 12.dp else 16.dp
    Box(
        Modifier.absoluteOffset(
            x = (markerX - labelWidth / 2).coerceIn(0.dp, trackWidth - labelWidth),
            y = trackY - labelRise,
        ).width(labelWidth).wrapContentHeight(Alignment.Top, unbounded = true),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp), color = colors.inverseSurface, contentColor = colors.inverseOnSurface,
        ) {
            Column(
                Modifier.padding(
                    start = horizontalPadding, end = horizontalPadding,
                    top = if (compact) 6.dp else 8.dp,
                    bottom = if (compact) 6.dp else 9.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (live) Text(stringResource(R.string.player_live), style = type.labelMedium)
                Text(time, style = if (compact) type.labelLarge else type.titleMedium.copy(fontSize = 19.sp))
            }
        }
    }
}

@Composable
internal fun playerClock(timeline: LiveTimeline, seconds: Int): String {
    val config = LocalConfiguration.current
    val locale = checkNotNull(ConfigurationCompat.getLocales(config)[0])
    return timeline.programStart.plusSeconds(seconds.toLong())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

@Composable
internal fun behindLive(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.player_live)
    else stringResource(R.string.player_behind_live, "%d:%02d".format(seconds / 60, seconds % 60))

private fun LiveTimeline.fraction(seconds: Int): Float = seconds.toFloat() / duration

private fun Modifier.excludeBarEnds(): Modifier =
    systemGestureExclusion { bounds ->
        val end = bounds.size.height.toFloat().coerceAtMost(bounds.size.width / 2f)
        Rect(0f, 0f, end, bounds.size.height.toFloat())
    }.systemGestureExclusion { bounds ->
        val end = bounds.size.height.toFloat().coerceAtMost(bounds.size.width / 2f)
        Rect(bounds.size.width - end, 0f, bounds.size.width.toFloat(), bounds.size.height.toFloat())
    }
