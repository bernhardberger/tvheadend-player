package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextOverflow
import at.bernhardberger.tvhplayer.ui.TvOverlayStatusRowHeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvOverlayGhostFillAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBarFocusedHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBarHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineRowHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineThumbSize
import at.bernhardberger.tvhplayer.ui.TvOverlayTrackAlpha
import kotlin.math.roundToInt

/** Plain status text sits in the light end of the footer gradient, so it needs its own backing. */
@Composable
internal fun Modifier.playerStatusScrim(): Modifier = background(
    Color.Black.copy(alpha = 0.78f),
    MaterialTheme.shapes.small,
).padding(horizontal = 8.dp)

/** Draws into the run-out above without adding to the track's measured height. */
private fun Modifier.paintAbove(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) {
        placeable.placeRelative(0, -placeable.height)
    }
}

/** Gap between an inline endpoint label and the track. Matches on the preview row. */
private val InlineEndpointGap = 8.dp

/**
 * Caps a sentence stuffed into an endpoint slot so the track, and the seek label
 * aligned to it, keep a usable width. Clock and duration labels fit inside this.
 */
private val InlineEndpointLabelMaxWidth = 240.dp

/** Both ends use the same pixel rounding; animation is read only during layout. */
private fun Modifier.timelineSpan(
    trackWidth: Dp,
    start: () -> Float = { 0f },
    end: () -> Float,
): Modifier = offset { IntOffset((trackWidth.toPx() * start()).roundToInt(), 0) }
    .layout { measurable, constraints ->
        val left = (trackWidth.toPx() * start()).roundToInt()
        val right = (trackWidth.toPx() * end()).roundToInt()
        val width = (right - left).coerceIn(0, constraints.maxWidth)
        val child = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(child.width, child.height) { child.placeRelative(0, 0) }
    }

@Composable
private fun TimelineTargetLabel(
    label: String,
    progress: Float,
    available: Boolean = true,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (available) 1f else TvOverlayTextTertiaryAlpha),
        maxLines = 1,
        modifier = Modifier
            .layout { measurable, constraints ->
                val label = measurable.measure(constraints.copy(minWidth = 0))
                layout(constraints.maxWidth, label.height) {
                    label.placeRelative(
                        (constraints.maxWidth * progress - label.width / 2f).roundToInt()
                            .coerceIn(0, constraints.maxWidth - label.width),
                        0,
                    )
                }
            }
            .playerStatusScrim()
            .testTag("timeshift-preview-target"),
    )
}

@Composable
private fun TimelineEndpointLabel(
    text: String,
    color: Color,
    testTag: String?,
    visible: Boolean,
    emphasis: State<Float>,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (visible) color else Color.Transparent,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(max = InlineEndpointLabelMaxWidth)
            .graphicsLayer { alpha = emphasis.value }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { })
            .then(if (visible && testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

/**
 * Same label widths and gaps as the inline track row, so a seek label measured in
 * [center] uses the track's coordinates and is not clipped by the inset.
 */
@Composable
private fun TimelineEndpointRow(
    leading: String?,
    trailing: String?,
    leadingColor: Color,
    trailingColor: Color,
    leadingTag: String?,
    trailingTag: String?,
    labelsVisible: Boolean,
    emphasis: State<Float>,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            TimelineEndpointLabel(leading, leadingColor, leadingTag, labelsVisible, emphasis)
            Spacer(Modifier.width(InlineEndpointGap))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { center() }
        if (trailing != null) {
            Spacer(Modifier.width(InlineEndpointGap))
            TimelineEndpointLabel(trailing, trailingColor, trailingTag, labelsVisible, emphasis)
        }
    }
}

enum class PlayerTimelineTone { AMBIENT, INTERACTIVE, ACTIVE, PREVIEW }

@Composable
fun PlayerTimelineBar(
    progress: Float?,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    ghostProgress: Float? = null,
    selectedMarkerFraction: Float? = null,
    rewindableStartFraction: Float? = null,
    rewindableStartOverflow: Boolean = false,
    liveEdgeFraction: Float? = null,
    thumbTestTag: String? = null,
    rewindableBoundaryTestTag: String? = null,
    rewindableOverflowTestTag: String? = null,
    progressSemantics: Boolean = true,
    availableEndFraction: Float? = liveEdgeFraction,
    programmeWindow: Boolean = false,
    programmeTargetAvailable: Boolean? = null,
    fillColor: Color = MaterialTheme.colorScheme.tertiary,
    showTrack: Boolean = true,
    markerFractions: List<Float> = emptyList(),
    motionKey: Any? = null,
) {
    val currentProgress = progress?.coerceIn(0f, 1f)
    val animatedProgress = key(motionKey, programmeWindow, currentProgress != null, programmeTargetAvailable) {
        animateFloatAsState(currentProgress ?: 0f,
            animationSpec = tween(durationMillis = PlayerMotion.EmphasisMs, easing = LinearOutSlowInEasing),
            label = "player-timeline-position")
    }
    val barHeight = if (tone == PlayerTimelineTone.ACTIVE) {
        TvOverlayTimelineBarFocusedHeight
    } else {
        TvOverlayTimelineBarHeight
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TvOverlayTimelineRowHeight)
            .then(
                if (progressSemantics && currentProgress != null) {
                    Modifier.semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(currentProgress, 0f..1f)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (showTrack) BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(barHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (rewindableStartFraction != null) 0.10f else TvOverlayTrackAlpha)),
        ) {
            ghostProgress?.takeUnless { programmeWindow }?.coerceIn(0f, 1f)?.let { ghost ->
                Box(
                    Modifier
                        .fillMaxWidth(ghost)
                        .height(barHeight)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = TvOverlayGhostFillAlpha,
                            ),
                        ),
                )
            }
            if (rewindableStartFraction != null && availableEndFraction != null) {
                val start = rewindableStartFraction.coerceIn(0f, 1f)
                val end = availableEndFraction.coerceIn(start, 1f)
                Box(
                    Modifier
                        .offset(x = maxWidth * start)
                        .width(maxWidth * (end - start))
                        .height(barHeight)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = TvOverlayGhostFillAlpha),
                        ),
                )
            }
            if ((programmeWindow || rewindableStartFraction == null) && currentProgress != null) {
                // White belongs only to elapsed content before the available history. Drawing
                // it underneath the orange span exposes a rounding fringe at the moving end.
                Box(
                    Modifier
                        .timelineSpan(maxWidth) {
                            if (programmeWindow && rewindableStartFraction != null) {
                                minOf(animatedProgress.value, rewindableStartFraction.coerceIn(0f, 1f))
                            } else animatedProgress.value
                        }
                        .height(barHeight)
                        .testTag("player-timeline-fill")
                        .background(if (programmeWindow) MaterialTheme.colorScheme.onSurface else fillColor),
                )
                if (programmeWindow && rewindableStartFraction != null && availableEndFraction != null) {
                    val start = rewindableStartFraction.coerceIn(0f, 1f)
                    // A valid sampled position can precede the next history-status update.
                    // Paint that actual playback position without extending any seek grant.
                    Box(Modifier.timelineSpan(maxWidth, start = { start }) {
                            if (programmeTargetAvailable == true) animatedProgress.value.coerceAtLeast(start)
                            else animatedProgress.value.coerceIn(start, availableEndFraction.coerceIn(start, 1f))
                        }.height(barHeight)
                        .testTag("player-timeline-interactive-fill")
                        .background(fillColor))
                }
            }
            markerFractions.filter { it > 0f && it < 1f }.forEach { fraction ->
                Box(Modifier.offset(x = maxWidth * fraction - 0.5.dp).width(1.dp).height(barHeight)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)).testTag("recording-marker-tick"))
            }
            rewindableStartFraction?.takeIf { !programmeWindow && it > 0f && it < 1f }?.let { fraction ->
                val start = fraction.coerceIn(0f, 1f)
                if (rewindableStartOverflow) {
                    val markerColor = MaterialTheme.colorScheme.onSurface
                    Box(
                        Modifier
                            .offset(x = maxWidth * start)
                            .width(8.dp)
                            .height(barHeight)
                            .then(
                                rewindableBoundaryTestTag
                                    ?.let(Modifier::testTag)
                                    ?: Modifier,
                            ),
                    ) {
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    rewindableOverflowTestTag
                                        ?.let(Modifier::testTag)
                                        ?: Modifier,
                                ),
                        ) {
                            val strokeWidth = 2.dp.toPx()
                            drawLine(
                                color = markerColor,
                                start = Offset(size.width, 0f),
                                end = Offset(0f, size.height / 2f),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Square,
                            )
                            drawLine(
                                color = markerColor,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width, size.height),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Square,
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .offset(x = maxWidth * start)
                            .width(2.dp)
                            .height(barHeight)
                            .background(MaterialTheme.colorScheme.onSurface)
                            .then(
                                rewindableBoundaryTestTag
                                    ?.let(Modifier::testTag)
                                    ?: Modifier,
                            ),
                    )
                }
            }
            if (!programmeWindow && rewindableStartFraction != null && currentProgress != null && programmeTargetAvailable != false) {
                val start = rewindableStartFraction.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .timelineSpan(maxWidth, start = { start }) { animatedProgress.value.coerceAtLeast(start) }
                        .height(barHeight)
                        .testTag("player-timeline-fill")
                        .background(MaterialTheme.colorScheme.tertiary),
                )
            }
        }
        selectedMarkerFraction?.let { fraction ->
            BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.Center)) {
                Box(Modifier
                    .offset(x = (maxWidth * fraction.coerceIn(0f, 1f) - 0.5.dp).coerceIn(0.dp, maxWidth - 1.dp))
                    .width(1.dp).height(18.dp)
                    .background(MaterialTheme.colorScheme.onSurface)
                    .testTag("recording-selected-marker"))
            }
        }
        if (tone == PlayerTimelineTone.ACTIVE && currentProgress != null) {
            val thumbSize = TvOverlayTimelineThumbSize
            BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.Center)) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset((maxWidth.toPx() * animatedProgress.value - thumbSize.toPx() / 2).roundToInt(), 0) }
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface)
                        .then(thumbTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                )
            }
        }
    }
}

@Composable
fun PlayerTimelineBlock(
    progress: Float?,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    trailingLabel: String? = null,
    leadingLabelColor: Color? = null,
    trailingLabelColor: Color? = null,
    leadingLabelTestTag: String? = null,
    trailingLabelTestTag: String? = null,
    ghostProgress: Float? = null,
    selectedMarkerFraction: Float? = null,
    rewindableStartFraction: Float? = null,
    rewindableStartOverflow: Boolean = false,
    liveEdgeFraction: Float? = null,
    thumbTestTag: String? = null,
    rewindableBoundaryTestTag: String? = null,
    rewindableOverflowTestTag: String? = null,
    progressSemantics: Boolean = true,
    programmeWindow: ProgrammeWindow? = null,
    reserveLabelSpace: Boolean = false,
    previewLabel: String? = null,
    fillColor: Color = MaterialTheme.colorScheme.tertiary,
    showTrack: Boolean = true,
    timelineModifier: Modifier = Modifier,
    feedback: String? = null,
    feedbackIsError: Boolean = false,
    feedbackTestTag: String = "player-window-title",
    statusAction: (@Composable () -> Unit)? = null,
    collapsed: Boolean = false,
    markerFractions: List<Float> = emptyList(),
    trackOverlay: (@Composable BoxScope.() -> Unit)? = null,
    /**
     * Identity of what the bar measures, such as the playing channel and programme. A
     * change jumps the fill to the new position instead of sliding across programmes.
     */
    motionKey: Any? = null,
) {
    val statusHeight = TvOverlayStatusRowHeight
    val showStatus = !collapsed && (
        feedback != null || statusAction != null || previewLabel != null
        )
    val previewProgress = programmeWindow?.positionFraction ?: progress ?: 0f
    val previewAvailable = programmeWindow?.targetAvailable != false
    val endpointColor = MaterialTheme.colorScheme.onSurface
    val endpointEmphasis = animateFloatAsState(
        if (tone == PlayerTimelineTone.ACTIVE || tone == PlayerTimelineTone.PREVIEW) 1f else TvOverlayTextTertiaryAlpha,
        animationSpec = tween(PlayerMotion.EmphasisMs), label = "player-endpoint-emphasis",
    )
    val leadingColor = leadingLabelColor ?: endpointColor
    val trailingColor = trailingLabelColor ?: endpointColor
    val inlineLabels = !collapsed &&
        (reserveLabelSpace || leadingLabel != null || trailingLabel != null)
    val labelLineHeight = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
    @Composable
    fun Track() {
        Box(Modifier.fillMaxWidth()) {
            PlayerTimelineBar(
                progress = programmeWindow?.positionFraction ?: progress,
                tone = tone,
                modifier = Modifier.testTag("player-timeline-track"),
                ghostProgress = ghostProgress,
                selectedMarkerFraction = selectedMarkerFraction,
                rewindableStartFraction = programmeWindow?.availableStartFraction ?: rewindableStartFraction,
                rewindableStartOverflow = rewindableStartOverflow,
                liveEdgeFraction = if (programmeWindow != null) programmeWindow.liveFraction else liveEdgeFraction,
                availableEndFraction = programmeWindow?.availableEndFraction ?: liveEdgeFraction,
                programmeWindow = programmeWindow != null,
                programmeTargetAvailable = programmeWindow?.targetAvailable,
                thumbTestTag = thumbTestTag,
                rewindableBoundaryTestTag = rewindableBoundaryTestTag,
                rewindableOverflowTestTag = rewindableOverflowTestTag,
                progressSemantics = progressSemantics,
                fillColor = fillColor,
                showTrack = showTrack,
                markerFractions = markerFractions,
                motionKey = motionKey to programmeWindow?.event?.let { Triple(it.id, it.start, it.stop) },
            )
            trackOverlay?.invoke(this)
        }
    }
    Box(modifier.fillMaxWidth()) {
        Column(timelineModifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (inlineLabels) {
                TimelineEndpointRow(
                    leading = leadingLabel,
                    trailing = trailingLabel,
                    leadingColor = leadingColor,
                    trailingColor = trailingColor,
                    leadingTag = leadingLabelTestTag,
                    trailingTag = trailingLabelTestTag,
                    labelsVisible = true,
                    emphasis = endpointEmphasis,
                    modifier = Modifier
                        .height(maxOf(TvOverlayTimelineRowHeight, labelLineHeight))
                        .testTag("player-timeline-labels"),
                ) { Track() }
            } else {
                Track()
            }
        }
        if (showStatus) {
            // Measured height stays with the track. The slot paints into the run-out above.
            TimelineStatusSlot(
                statusHeight = statusHeight,
                previewLabel = previewLabel,
                previewProgress = previewProgress,
                previewAvailable = previewAvailable,
                feedback = feedback,
                feedbackIsError = feedbackIsError,
                feedbackTestTag = feedbackTestTag,
                statusAction = statusAction,
                inlinePreview = inlineLabels,
                leadingLabel = leadingLabel,
                trailingLabel = trailingLabel,
                leadingColor = leadingColor,
                trailingColor = trailingColor,
                endpointEmphasis = endpointEmphasis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .paintAbove()
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimelineStatusSlot(
    statusHeight: Dp,
    previewLabel: String?,
    previewProgress: Float,
    previewAvailable: Boolean,
    feedback: String?,
    feedbackIsError: Boolean,
    feedbackTestTag: String,
    statusAction: (@Composable () -> Unit)?,
    inlinePreview: Boolean,
    leadingLabel: String?,
    trailingLabel: String?,
    leadingColor: Color,
    trailingColor: Color,
    endpointEmphasis: State<Float>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().height(statusHeight).testTag("player-timeline-status"),
        contentAlignment = Alignment.Center,
    ) {
        if (previewLabel != null) {
            if (inlinePreview) {
                TimelineEndpointRow(
                    leading = leadingLabel,
                    trailing = trailingLabel,
                    leadingColor = leadingColor,
                    trailingColor = trailingColor,
                    leadingTag = null,
                    trailingTag = null,
                    labelsVisible = false,
                    emphasis = endpointEmphasis,
                ) {
                    TimelineTargetLabel(previewLabel, previewProgress, previewAvailable)
                }
            } else {
                TimelineTargetLabel(previewLabel, previewProgress, previewAvailable)
            }
        }
        // Keep the explicit Up destination mounted while the target label is shown.
        // Its focus immediately restores the status row, including during a pending seek.
        Row(Modifier.fillMaxWidth()
            .graphicsLayer { alpha = if (previewLabel == null) 1f else 0f }
            .then(if (previewLabel != null) Modifier.clearAndSetSemantics { } else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
                if (feedback != null) {
                    Text(
                        feedback,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (feedbackIsError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 24.dp)
                            .wrapContentWidth(Alignment.Start)
                            .then(
                                if (feedbackIsError) {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer,
                                            MaterialTheme.shapes.small,
                                        )
                                        .padding(horizontal = 8.dp)
                                } else {
                                    Modifier.playerStatusScrim()
                                },
                            )
                            .testTag(feedbackTestTag),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                statusAction?.invoke()
        }
    }
}
