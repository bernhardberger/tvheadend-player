package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.ui.TvOverlayGhostFillAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTextTertiaryAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBarFocusedHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineBarHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineLabelGap
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineRowHeight
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineThumbSize
import at.bernhardberger.tvhplayer.ui.TvOverlayTimelineTickAlpha
import at.bernhardberger.tvhplayer.ui.TvOverlayTrackAlpha

private val PlaybackPositionColor = Color(0xFFFA7F00)

enum class PlayerTimelineTone { AMBIENT, INTERACTIVE, ACTIVE, PREVIEW }

@Composable
fun PlayerTimelineBar(
    progress: Float,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    ghostProgress: Float? = null,
    boundaryFractions: List<Float> = emptyList(),
    rewindableStartFraction: Float? = null,
    liveEdgeFraction: Float? = null,
    thumbTestTag: String? = null,
    progressSemantics: Boolean = true,
) {
    val currentProgress = progress.coerceIn(0f, 1f)
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
                if (progressSemantics) {
                    Modifier.semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(currentProgress, 0f..1f)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(barHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TvOverlayTrackAlpha)),
        ) {
            ghostProgress?.coerceIn(0f, 1f)?.let { ghost ->
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
            if (rewindableStartFraction != null && liveEdgeFraction != null) {
                val start = rewindableStartFraction.coerceIn(0f, 1f)
                val end = liveEdgeFraction.coerceIn(start, 1f)
                Box(
                    Modifier
                        .offset(x = maxWidth * start)
                        .width(maxWidth * (end - start))
                        .height(barHeight)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = TvOverlayGhostFillAlpha,
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth(currentProgress)
                    .height(barHeight)
                    .background(PlaybackPositionColor),
            )
            boundaryFractions.forEach { fraction ->
                Box(
                    Modifier
                        .offset(x = maxWidth * fraction.coerceIn(0f, 1f) - 1.dp)
                        .width(2.dp)
                        .height(barHeight)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = TvOverlayTimelineTickAlpha,
                            ),
                        ),
                )
            }
            liveEdgeFraction?.let { fraction ->
                Box(
                    Modifier
                        .offset(x = maxWidth * fraction.coerceIn(0f, 1f) - 1.dp)
                        .width(2.dp)
                        .height(barHeight)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
        if (tone == PlayerTimelineTone.ACTIVE) {
            BoxWithConstraints(Modifier.fillMaxWidth().align(Alignment.Center)) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * currentProgress - TvOverlayTimelineThumbSize / 2)
                        .size(TvOverlayTimelineThumbSize)
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
    progress: Float,
    tone: PlayerTimelineTone,
    modifier: Modifier = Modifier,
    leadingLabel: String? = null,
    trailingLabel: String? = null,
    leadingLabelColor: Color? = null,
    trailingLabelColor: Color? = null,
    leadingLabelTestTag: String? = null,
    trailingLabelTestTag: String? = null,
    ghostProgress: Float? = null,
    boundaryFractions: List<Float> = emptyList(),
    rewindableStartFraction: Float? = null,
    liveEdgeFraction: Float? = null,
    thumbTestTag: String? = null,
    progressSemantics: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        if (leadingLabel != null || trailingLabel != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                leadingLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = leadingLabelColor
                            ?: MaterialTheme.colorScheme.onSurface.copy(
                                alpha = TvOverlayTextTertiaryAlpha,
                            ),
                        modifier = leadingLabelTestTag?.let(Modifier::testTag) ?: Modifier,
                    )
                }
                Spacer(Modifier.weight(1f))
                trailingLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = trailingLabelColor
                            ?: MaterialTheme.colorScheme.onSurface.copy(
                                alpha = TvOverlayTextTertiaryAlpha,
                            ),
                        modifier = trailingLabelTestTag?.let(Modifier::testTag) ?: Modifier,
                    )
                }
            }
            Spacer(Modifier.height(TvOverlayTimelineLabelGap))
        }
        PlayerTimelineBar(
            progress = progress,
            tone = tone,
            ghostProgress = ghostProgress,
            boundaryFractions = boundaryFractions,
            rewindableStartFraction = rewindableStartFraction,
            liveEdgeFraction = liveEdgeFraction,
            thumbTestTag = thumbTestTag,
            progressSemantics = progressSemantics,
        )
    }
}
