package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.StepDirection
import at.bernhardberger.tvhplayer.ui.TvSurfaceColors
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
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

/**
 * Clear space between the step readout's bottom and the thumb's top, so the chip never reads as
 * touching the focused bar's thumb (AOSP Live TV keeps its time label about 5dp clear of a 12dp one).
 */
internal val ReadoutThumbGap = 8.dp

/**
 * Where the step readout's chip and the distance's drawn text lie in the root, written when they are
 * positioned and read only by the distance's layer: while the chip lies over any of the text, the
 * text is not drawn, so none of it shows as a fragment beside the chip.
 */
@Stable
internal class ReadoutCover {
    var chip by mutableStateOf<Rect?>(null)
    var distance by mutableStateOf<Rect?>(null)
    val coversDistance: Boolean get() = chip?.let { distance?.overlaps(it) } == true
}

private fun LayoutCoordinates.unclippedBoundsInRoot(): Rect = findRootCoordinates().localBoundingBoxOf(this, clipBounds = false)

/**
 * The step readout at its place on the bar, on an opaque chip so nothing beneath shows through. Over
 * a drawn thumb ([thumbDrawn]) its bottom sits [ReadoutThumbGap] above the thumb's top; without one
 * (the Banner's preview) it rests on the bar row's top. It takes the bar row's width and no height:
 * it paints above the row, over whatever sits there.
 * [direction] leads it with ⏪ or ⏩: where the target lies from the playback position. At the live
 * edge ([atLive]) it leads with ▶ instead.
 */
@Composable
private fun TimelineTargetLabel(
    label: String,
    progress: Float,
    available: Boolean = true,
    direction: StepDirection? = null,
    atLive: Boolean = false,
    thumbDrawn: Boolean = false,
    cover: ReadoutCover,
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (available) 1f else TvOverlayTextTertiaryAlpha)
    DisposableEffect(cover) { onDispose { cover.chip = null } }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .layout { measurable, constraints ->
                val label = measurable.measure(constraints.copy(minWidth = 0))
                val thumbTop = (TvOverlayTimelineRowHeight - TvOverlayTimelineThumbSize) / 2
                layout(constraints.maxWidth, 0) {
                    label.placeRelative(
                        (constraints.maxWidth * progress - label.width / 2f).roundToInt()
                            .coerceIn(0, constraints.maxWidth - label.width),
                        (if (thumbDrawn) (thumbTop - ReadoutThumbGap).roundToPx() else 0) - label.height,
                    )
                }
            }
            .onGloballyPositioned { cover.chip = it.unclippedBoundsInRoot() }
            .background(TvSurfaceColors.containerHigh, PlayerChromeTokens.chipShape)
            .heightIn(min = PlayerChromeTokens.chipHeight)
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) { }
            .testTag("timeshift-preview-target"),
    ) {
        if (atLive) Icon(
            painterResource(R.drawable.ic_play_arrow),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(playerStateCellSize()).testTag("timeshift-preview-live"),
        ) else if (direction != null) Icon(
            painterResource(if (direction == StepDirection.BEHIND) R.drawable.ic_fast_rewind else R.drawable.ic_fast_forward),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(playerStateCellSize())
                .testTag(if (direction == StepDirection.BEHIND) "timeshift-preview-behind" else "timeshift-preview-ahead"),
        )
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
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
    // The axis is part of the identity: schedule, buffer, programme window. A fraction on another axis
    // is another quantity, so a change of axis snaps to it instead of sliding through a value between.
    val animatedProgress = key(motionKey, programmeWindow, rewindableStartFraction != null, currentProgress != null, programmeTargetAvailable) {
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
    /** False when the focusable track already speaks both clocks. */
    endpointSemantics: Boolean = true,
    reserveLabelSpace: Boolean = false,
    previewLabel: String? = null,
    /** Where [previewLabel]'s target lies from the playback position; null shows no direction. */
    previewDirection: StepDirection? = null,
    /** The step readout's target is the live edge: it leads with ▶ instead of a direction. */
    previewAtLive: Boolean = false,
    fillColor: Color = MaterialTheme.colorScheme.tertiary,
    showTrack: Boolean = true,
    timelineModifier: Modifier = Modifier,
    feedback: String? = null,
    feedbackIsError: Boolean = false,
    feedbackTestTag: String = "player-window-title",
    /** The state cell before the bar, the end after it (replacing [trailingLabel]) and a live end's distance behind live. */
    status: PlayerBarStatus? = null,
    /** Whose label widths the bar row keeps. */
    kind: TimelineKind = TimelineKind.LIVE,
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
    val showStatus = !collapsed && feedback != null
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
        (reserveLabelSpace || leadingLabel != null || trailingLabel != null || status != null)
    val widths = rememberTimelineEndpointWidths(kind)
    val stateCell = LocalStateCellInset.current
    val labelLineHeight = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
    val readoutCover = remember { ReadoutCover() }
    @Composable
    fun Track() {
        Box(timelineModifier.fillMaxWidth()) {
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
                motionKey = motionKey to programmeWindow?.let { Triple(it.event?.id, it.start, it.stop) },
            )
            trackOverlay?.invoke(this)
            if (previewLabel != null && !collapsed) {
                TimelineTargetLabel(previewLabel, previewProgress, previewAvailable, previewDirection, previewAtLive,
                    thumbDrawn = tone == PlayerTimelineTone.ACTIVE && progress != null, cover = readoutCover)
            }
        }
    }
    // Live TV or a growing recording's distance behind live, above and flush with the end label.
    val liveEnd = status?.end?.takeIf { inlineLabels && it.liveEnd }
    Box(modifier.fillMaxWidth()) {
        // It takes no room: it paints above the block, on the info block's last line, at one offset
        // above the bar row whatever that line is. Composed before the row, it lies beneath the step
        // readout's opaque chip, and is not drawn while the chip lies over any of its text: the chip
        // moves in steps, so the text cuts with it instead of fading beside it.
        if (liveEnd != null) PlayerLiveDistance(
            liveEnd, status.distance,
            Modifier.align(Alignment.TopEnd).paintAbove().size(widths.distance, labelLineHeight),
            textModifier = remember(readoutCover) {
                Modifier
                    .onGloballyPositioned { readoutCover.distance = it.unclippedBoundsInRoot() }
                    .graphicsLayer { alpha = if (readoutCover.coversDistance) 0f else 1f }
            },
        )
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (inlineLabels) {
                TimelineEndpointRow(
                    widths = widths,
                    modifier = Modifier
                        .height(maxOf(TvOverlayTimelineRowHeight, labelLineHeight))
                        .testTag("player-timeline-labels"),
                    state = status?.let { { PlayerStateCellView(it.state, announces = stateCell.announces) } },
                    leading = {
                        Box(if (endpointSemantics) Modifier else Modifier.clearAndSetSemantics { }) {
                            leadingLabel?.let { TimelineEndpointLabel(it, leadingColor, leadingLabelTestTag, endpointEmphasis) }
                        }
                    },
                    trailing = {
                        Box(if (endpointSemantics) Modifier else Modifier.clearAndSetSemantics { }) {
                            if (status?.end != null) {
                                PlayerBarEndClock(status.end, endpointEmphasis)
                            } else trailingLabel?.let {
                                TimelineEndpointLabel(it, trailingColor, trailingLabelTestTag, endpointEmphasis)
                            }
                        }
                    },
                ) { Track() }
            } else {
                Track()
            }
        }
        if (showStatus) {
            // Measured height stays with the track. The slot paints into the run-out above.
            TimelineStatusSlot(
                statusHeight = statusHeight,
                hidden = previewLabel != null,
                // The feedback ends before the distance drawn at the end of its band.
                endReserve = if (liveEnd != null) widths.distance + DistanceTextGap else 0.dp,
                feedback = feedback,
                feedbackIsError = feedbackIsError,
                feedbackTestTag = feedbackTestTag,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .paintAbove()
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * The feedback line above the bar. While the step readout shows [hidden], it keeps its place but
 * stays out of sight and of accessibility.
 */
@Composable
private fun TimelineStatusSlot(
    statusHeight: Dp,
    hidden: Boolean,
    endReserve: Dp,
    feedback: String?,
    feedbackIsError: Boolean,
    feedbackTestTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().height(statusHeight).testTag("player-timeline-status"),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth()
            .graphicsLayer { alpha = if (hidden) 0f else 1f }
            .then(if (hidden) Modifier.clearAndSetSemantics { } else Modifier),
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
                            .padding(end = maxOf(24.dp, endReserve))
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
        }
    }
}
