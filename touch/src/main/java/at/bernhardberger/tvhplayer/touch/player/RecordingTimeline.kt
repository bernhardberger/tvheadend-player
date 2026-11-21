@file:Suppress("MagicNumber", "LongParameterList")

package at.bernhardberger.tvhplayer.touch.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.bernhardberger.tvhplayer.core.RecordingMark
import at.bernhardberger.tvhplayer.core.RecordingMarkHint
import at.bernhardberger.tvhplayer.core.RecordingMarkKind
import at.bernhardberger.tvhplayer.core.RecordingMarkStop
import at.bernhardberger.tvhplayer.core.formatPlaybackDuration
import at.bernhardberger.tvhplayer.core.leftPx
import at.bernhardberger.tvhplayer.core.visibleRecordingMarkHints
import at.bernhardberger.tvhplayer.touch.R
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun FaceTimeline(
    state: PlayerFaceState,
    layout: PlayerLayout,
    controls: Boolean,
    preview: Long?,
    onPreview: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    onChoice: (PlayerChoice) -> Unit = {},
) {
    when (state) {
        is LivePlayerState -> PlayerTimeline(
            state.timeline, state.status, layout, controls, preview?.let { (it / 1_000).toInt() },
            { onPreview(it?.times(1_000L)) }, { onSeek(it * 1_000L) }, modifier,
        )
        is RecordingPlayerState ->
            RecordingTimeline(state, layout, controls, preview, onPreview, onSeek, modifier, onChoice)
    }
}

@Composable
internal fun RecordingTimeline(
    state: RecordingPlayerState,
    layout: PlayerLayout,
    controls: Boolean,
    preview: Long?,
    onPreview: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
    onChoice: (PlayerChoice) -> Unit,
) {
    if (!state.seekable) return
    val duration = state.displayDurationMs
    val current = state.positionMs.value.coerceIn(0, duration)
    val position = (preview ?: current).coerceIn(0, duration)
    val marks = remember(state.marks, duration) { recordingTrackMarks(state.marks, duration) }
    val canDrag = controls && state.status == LivePlayerStatus.Playing && PlayerControl.Timeshift in state.offered
    val gesture = if (canDrag) {
        recordingBarDrag(state, current, position, onPreview, onSeek)
    } else Modifier
    BoxWithConstraints(modifier.testTag("player-timeline").then(gesture)) {
        val trackY = maxHeight - if (layout.upright) 2.dp else 24.dp
        val markerX = maxWidth * fraction(position, duration)
        val thickness = if (controls) layout.trackThickness else 2.dp
        val hintInset = recordingHintInset(layout, marks)
        RecordingTrack(marks, duration, current, position, trackY, thickness, preview != null, Modifier.fillMaxSize())
        if (controls) {
            if (hintInset > 0.dp) {
                RecordingHints(marks, duration, trackY - layout.recordingHintLift, Modifier.fillMaxSize()) { mark ->
                    recordingMarkerMove(state, mark.startMs)?.let { move -> { onChoice(move) } }
                }
            }
            PositionThumb(markerX, trackY, layout, preview != null)
        }
        if (preview != null) {
            val stop = recordingDrag(position, position, 0L, state.stops, marks)
            val kind = stop.mark?.kind
            DragLabel(
                formatPlaybackDuration(position), false, layout.upright, markerX, trackY, maxWidth,
                markerName = recordingMarkName(stop).takeIf { kind != null },
                markerSymbol = kind?.let { { RecordingSymbol(it) } },
                minimumGap = if (hintInset > 0.dp) hintInset + 4.dp else 0.dp,
            )
        }
    }
}

@Composable
private fun recordingBarDrag(
    state: RecordingPlayerState,
    current: Long,
    position: Long,
    onPreview: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
): Modifier {
    val duration = state.durationMs
    val snapPx = with(LocalDensity.current) { 12.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.player_timeline)
    val positionLabel = formatPlaybackDuration(position)
    return Modifier.excludeBarEnds()
        .semantics {
            contentDescription = label
            stateDescription = positionLabel
            progressBarRangeInfo = ProgressBarRangeInfo(
                position.coerceAtMost(duration).toFloat(), 0f..duration.toFloat(),
            )
            setProgress {
                val target = it.roundToLong().coerceIn(0, duration)
                if (target == current) false else {
                    onSeek(target)
                    true
                }
            }
        }
        .dragOnBar(
            snapPx = snapPx,
            targetAt = { x, width ->
                if (width <= 0) null else {
                    val displayDuration = state.displayDurationMs
                    val distance = (displayDuration * (x / width).toDouble().coerceIn(0.0, 1.0)).roundToLong()
                    val radius = (displayDuration * (snapPx / width).toDouble()).roundToLong()
                    recordingDrag(distance.coerceAtMost(duration), current.coerceAtMost(duration), radius,
                        state.stops, state.marks).positionMs
                }
            },
            current = { current }, onPreview = onPreview, onSeek = onSeek,
            onTargetChanged = { positionMs ->
                if (state.stops.any { it.positionMs == positionMs && it.named }) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            },
        )
}

/** The recording has no thin/unavailable parts; a span can override either side of the thumb. */
@Composable
private fun RecordingTrack(
    marks: List<RecordingMark>,
    duration: Long,
    current: Long,
    position: Long,
    y: Dp,
    thickness: Dp,
    dragging: Boolean,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.onSurface
    Canvas(modifier) {
        val center = y.toPx()
        val thick = thickness.toPx()
        val top = center - thick / 2
        val bottom = center + thick / 2
        fun x(ms: Long) = size.width * fraction(ms, duration)
        fun segment(from: Float, to: Float, color: Color) {
            if (to > from) drawLine(color, Offset(from, center), Offset(to, center), thick, StrokeCap.Round)
        }
        segment(0f, size.width, light.copy(alpha = .42f))
        segment(0f, x(position), colors.tertiary)
        for (mark in marks) {
            val start = x(mark.startMs)
            val end = x(mark.endMs)
            when (mark.kind) {
                RecordingMarkKind.AD_BREAK ->
                    drawRect(colors.secondary.copy(alpha = .75f), Offset(start, top), Size(end - start, thick))
                RecordingMarkKind.CUT -> clipRect(start, top, end, bottom) {
                    var hatch = start - thick
                    while (hatch < end) {
                        drawLine(
                            light.copy(alpha = .72f), Offset(hatch, bottom), Offset(hatch + thick, top), 1.dp.toPx(),
                        )
                        hatch += 4.dp.toPx()
                    }
                }
                else -> drawLine(light, Offset(start, top), Offset(start, bottom), 1.dp.toPx())
            }
        }
        if (dragging) {
            val x = x(current)
            drawLine(light, Offset(x, center - 7.dp.toPx()), Offset(x, center + 7.dp.toPx()), 2.dp.toPx())
        }
    }
}

private val PlayerLayout.recordingHintLift: Dp
    get() = if (this == PlayerLayout.Tablet) 18.dp else 14.dp

/** The space from the track centre to the top of its symbol row. */
@Composable
internal fun recordingHintInset(layout: PlayerLayout, marks: List<RecordingMark>): Dp {
    if (layout.upright || marks.isEmpty()) return 0.dp
    val badgeLine = if (marks.any { it.kind == RecordingMarkKind.AD_BREAK }) {
        with(LocalDensity.current) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
    } else 0.dp
    return layout.recordingHintLift + maxOf(16.dp, badgeLine)
}

/** Measure the actual Material symbols (including localized badges), then apply the TV collision rule. */
@Composable
private fun RecordingHints(
    marks: List<RecordingMark>,
    duration: Long,
    y: Dp,
    modifier: Modifier,
    jump: (RecordingMark) -> (() -> Unit)?,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Layout(
        content = {
            marks.forEach { mark ->
                val onClick = jump(mark)
                val hint = if (onClick == null) {
                    Modifier.clearAndSetSemantics { testTag = "recording-marker-symbol" }
                } else {
                    val stop = RecordingMarkStop(mark.startMs, mark)
                    val label = recordingMarkName(stop) ?: formatPlaybackDuration(stop.positionMs)
                    Modifier.testTag("recording-marker-symbol").semantics { contentDescription = label }
                        .clickable(role = Role.Button, onClick = onClick)
                }
                Box(hint) {
                    RecordingSymbol(mark.kind, ink)
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val items = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth.toFloat()
        val hints = items.mapIndexed { index, item ->
            val mark = marks[index]
            val center = mark.startMs + (mark.endMs - mark.startMs) / 2
            RecordingMarkHint(width * fraction(center, duration), item.width.toFloat(), mark.kind)
        }
        val visible = visibleRecordingMarkHints(hints, 4.dp.toPx(), width)
        layout(constraints.maxWidth, constraints.maxHeight) {
            visible.forEach { index ->
                val item = items[index]
                item.place(hints[index].leftPx(width).roundToInt(), y.roundToPx() - item.height)
            }
        }
    }
}

private fun fraction(position: Long, duration: Long): Float = (position.toDouble() / duration).toFloat()
