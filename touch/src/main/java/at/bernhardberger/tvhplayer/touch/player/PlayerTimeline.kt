@file:Suppress("MagicNumber")

package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.touch.R
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.presentation.R as SharedR
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun PlayerTimeline(
    source: State<LiveTimeline?>,
    status: LivePlayerStatus,
    layout: PlayerLayout,
    visibleControls: Boolean,
    preview: Int?,
    onPreviewChange: (Int?) -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The bar alone reads the sampled values, so a sample redraws it and nothing around it.
    val timeline = source.value ?: return
    val playing = status == LivePlayerStatus.Playing
    val seekable = playing && timeline.seekable
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
                playerClock(timeline.programStart, position),
                timeline.liveEdge && position == timeline.live,
                layout.upright,
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
    val positionLabel = if (timeline.liveEdge && position == timeline.live) {
        stringResource(
            SharedR.string.player_seekbar_timeshift_live_description,
            stringResource(
                SharedR.string.timeshift_buffer_start_description,
                formatPlaybackDuration((timeline.live - first) * 1_000L),
            ),
        )
    } else {
        behindLive(timeline.behindAt(position))
    }
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
        .dragOnBar(
            snapPx, { x, width -> latestTimeline.dragTarget(x, width, snapPx) },
            { latestTimeline.position }, { latestPreview(it) }, { latestSeek(it) },
            startValidation = {
                val original = latestTimeline
                { original.canContinueDrag(latestTimeline) }
            },
        )
}

@Composable
internal fun <T : Any> Modifier.dragOnBar(
    snapPx: Float,
    targetAt: (Float, Int) -> T?,
    current: () -> T,
    onPreview: (T?) -> Unit,
    onSeek: (T) -> Unit,
    onTargetChanged: (T?) -> Unit = {},
    startValidation: () -> (() -> Boolean) = { { true } },
): Modifier {
    val latestTarget by rememberUpdatedState(targetAt)
    val latestCurrent by rememberUpdatedState(current)
    val latestPreview by rememberUpdatedState(onPreview)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestChanged by rememberUpdatedState(onTargetChanged)
    val latestValidation by rememberUpdatedState(startValidation)
    return pointerInput(snapPx) {
        var target: T? = null
        var valid: (() -> Boolean)? = null
        detectHorizontalDragGestures(
            onDragStart = { target = null; valid = latestValidation() },
            onHorizontalDrag = { change, _ ->
                val previous = target
                if (valid?.invoke() == false) valid = null
                target = if (valid != null) latestTarget(change.position.x, size.width) else null
                if (target != previous) latestChanged(target)
                latestPreview(target)
            },
            onDragCancel = { latestPreview(null) },
            onDragEnd = {
                latestPreview(null)
                if (valid?.invoke() == true) target?.takeUnless { it == latestCurrent() }?.let(latestSeek)
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
internal fun PositionThumb(x: Dp, y: Dp, layout: PlayerLayout, dragging: Boolean) {
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

/**
 * The slider's value label (Material: label container 44dp high, as wide as its text, inverse
 * surface, labelLarge), centered on the handle and kept over the bar. At the live edge "Live"
 * stands above the time as a marker's name would (labelMedium).
 */
@Composable
internal fun DragLabel(
    time: String,
    live: Boolean,
    compact: Boolean,
    markerX: Dp,
    trackY: Dp,
    trackWidth: Dp,
    markerName: String? = null,
    markerSymbol: (@Composable () -> Unit)? = null,
    minimumGap: Dp = 0.dp,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val gap = maxOf(if (compact) 42.dp else 28.dp, minimumGap)
    Surface(
        modifier = Modifier.layout { measurable, _ ->
            val label = measurable.measure(Constraints())
            val x = (markerX.roundToPx() - label.width / 2).coerceIn(0, max(0, trackWidth.roundToPx() - label.width))
            layout(0, 0) { label.place(x, (trackY - gap).roundToPx() - label.height) }
        }.heightIn(min = 44.dp).then(if (markerName != null) Modifier.testTag("recording-drag-label") else Modifier),
        shape = RoundedCornerShape(14.dp), color = colors.inverseSurface, contentColor = colors.inverseOnSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (live) Text(stringResource(R.string.player_live), style = type.labelMedium)
            markerName?.let { Text(it, style = type.labelMedium) }
            if (markerSymbol == null) {
                Text(time, style = type.labelLarge)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    markerSymbol()
                    Text(time, style = type.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun playerClock(programStart: LocalTime, seconds: Int): String {
    val config = LocalConfiguration.current
    val locale = checkNotNull(ConfigurationCompat.getLocales(config)[0])
    // The shared clock, so the bar's times follow the 24-hour setting like every other time.
    return formatClock(programStart.plusSeconds(seconds.toLong()), locale)
}

@Composable
internal fun behindLive(seconds: Int): String =
    if (seconds == 0) stringResource(R.string.player_live)
    else stringResource(R.string.player_behind_live, behindLiveOffset(seconds))

private fun LiveTimeline.fraction(seconds: Int): Float = seconds.toFloat() / duration

internal fun Modifier.excludeBarEnds(): Modifier =
    systemGestureExclusion { bounds ->
        val end = bounds.size.height.toFloat().coerceAtMost(bounds.size.width / 2f)
        Rect(0f, 0f, end, bounds.size.height.toFloat())
    }.systemGestureExclusion { bounds ->
        val end = bounds.size.height.toFloat().coerceAtMost(bounds.size.width / 2f)
        Rect(bounds.size.width - end, 0f, bounds.size.width.toFloat(), bounds.size.height.toFloat())
    }
