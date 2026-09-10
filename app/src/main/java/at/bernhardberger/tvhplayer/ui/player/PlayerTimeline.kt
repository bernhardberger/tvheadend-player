package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import at.bernhardberger.tvhplayer.ui.TvOverlayActionButtonSize
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

private val PlaybackPositionColor = Color(0xFFFA7F00)

@Composable
private fun TimelineTargetLabel(label: String, progress: Float, available: Boolean = true) {
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
            .testTag("timeshift-preview-target"),
    )
}

enum class PlayerTimelineTone { AMBIENT, INTERACTIVE, ACTIVE, PREVIEW }

@Composable
fun PlayerTimelineBar(
    progress: Float?,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    ghostProgress: Float? = null,
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
    fillColor: Color = PlaybackPositionColor,
    showTrack: Boolean = true,
) {
    val currentProgress = progress?.coerceIn(0f, 1f)
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
                Box(
                    Modifier
                        .fillMaxWidth(currentProgress)
                        .height(barHeight)
                        .background(fillColor),
                )
            }
            rewindableStartFraction?.takeIf { it > 0f && it < 1f }?.let { fraction ->
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
                Box(
                    Modifier
                        .offset(x = (maxWidth * currentProgress - 1.dp).coerceAtLeast(0.dp))
                        .width(2.dp)
                        .height(barHeight)
                        .background(PlaybackPositionColor),
                )
            }
        }
        if (tone == PlayerTimelineTone.ACTIVE && currentProgress != null) {
            val thumbSize = TvOverlayTimelineThumbSize
            BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.Center)) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * currentProgress - thumbSize / 2)
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(if (programmeTargetAvailable == false) Color.Transparent else PlaybackPositionColor)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
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
    fillColor: Color = PlaybackPositionColor,
    showTrack: Boolean = true,
    timelineModifier: Modifier = Modifier,
    feedback: String? = null,
    feedbackIsError: Boolean = false,
    feedbackTestTag: String = "player-window-title",
    reserveStatusSpace: Boolean = false,
    statusAction: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (reserveStatusSpace || feedback != null || statusAction != null) {
            Row(
                Modifier.fillMaxWidth().height(TvOverlayActionButtonSize).testTag("player-timeline-status"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (feedback != null) {
                    Text(
                        feedback,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (feedbackIsError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 24.dp)
                            .wrapContentWidth(Alignment.Start)
                            .then(if (feedbackIsError) Modifier.background(MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.shapes.small).padding(horizontal = 8.dp) else Modifier)
                            .testTag(feedbackTestTag),
                    )
                } else Spacer(Modifier.weight(1f))
                statusAction?.invoke()
            }
        }
        // Live modes reserve the same target clearance, including tuning and missing EPG.
        // The timeline's top padding already contributes 8dp of that clearance.
        if (reserveStatusSpace || previewLabel != null) {
            Spacer(Modifier.height(with(LocalDensity.current) {
                (MaterialTheme.typography.labelLarge.lineHeight.toDp() - 8.dp).coerceAtLeast(0.dp)
            }))
        }
        Column(timelineModifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (previewLabel != null) {
                Box(Modifier.fillMaxWidth().layout { measurable, constraints ->
                    val label = measurable.measure(constraints.copy(minHeight = 0))
                    layout(label.width, 0) { label.placeRelative(0, -label.height) }
                }) {
                    TimelineTargetLabel(previewLabel, programmeWindow?.positionFraction ?: progress ?: 0f,
                        programmeWindow?.targetAvailable != false)
                }
            }
            PlayerTimelineBar(
                progress = programmeWindow?.positionFraction ?: progress,
                tone = tone,
                modifier = Modifier.testTag("player-timeline-track"),
                ghostProgress = ghostProgress,
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
            )
            // Endpoint readouts never shorten or move the track, even at large font scales.
            if (reserveLabelSpace || leadingLabel != null || trailingLabel != null) {
                val labelHeight = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.lineHeight.toDp() }
                Row(Modifier.fillMaxWidth().height(labelHeight).testTag("player-timeline-labels"), verticalAlignment = Alignment.CenterVertically) {
                    leadingLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = leadingLabelColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = TvOverlayTextTertiaryAlpha),
                            modifier = leadingLabelTestTag?.let(Modifier::testTag) ?: Modifier,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    trailingLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = trailingLabelColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = TvOverlayTextTertiaryAlpha),
                            modifier = trailingLabelTestTag?.let(Modifier::testTag) ?: Modifier,
                        )
                    }
                }
            }
        }
    }
}
