package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.SeekbarDomain
import at.bernhardberger.tvhplayer.core.SeekbarRange
import at.bernhardberger.tvhplayer.core.TIMESHIFT_LIVE_EDGE_TOLERANCE_MS
import at.bernhardberger.tvhplayer.core.seekbarScrub
import java.util.Locale
import kotlin.math.abs

/**
 * Focusable seekbar for timeshift and recording playback.
 *
 * Left/Right scrub with repeat acceleration. Up/Down are not consumed so focus
 * can leave the bar toward adjacent controls.
 */
@Composable
fun PlaybackSeekbar(
    range: SeekbarRange,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    liveLabelVisible: Boolean = range.domain == SeekbarDomain.TIMESHIFT,
    epgBoundaryFractions: List<Float> = emptyList(),
    programmePositionMs: Long? = null,
    programmeDurationMs: Long? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val programmeDuration = programmeDurationMs?.takeIf { it > 0L }
    val programmePosition = programmePositionMs?.coerceIn(0L, programmeDuration ?: 0L)
    val showingProgramme = !focused &&
        range.domain == SeekbarDomain.TIMESHIFT &&
        programmePosition != null &&
        programmeDuration != null
    val displayedProgress = if (showingProgramme) {
        programmePosition.toFloat() / programmeDuration.toFloat()
    } else {
        range.progress
    }
    val description = when {
        showingProgramme -> stringResource(
            R.string.player_programme_progress_description,
            formatPlaybackClock(programmePosition),
            formatPlaybackClock(programmeDuration),
        )
        range.domain == SeekbarDomain.RECORDING -> stringResource(
            R.string.player_seekbar_recording_description,
            formatPlaybackClock(range.positionMs),
            formatPlaybackClock(range.endMs),
        )
        else -> stringResource(
            R.string.player_seekbar_timeshift_description,
            formatPlaybackClock(abs(range.positionMs)),
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeekTo(
                            seekbarScrub(
                                range,
                                direction = -1,
                                repeatCount = event.nativeKeyEvent.repeatCount,
                            )
                        )
                        true
                    }
                    Key.DirectionRight -> {
                        onSeekTo(
                            seekbarScrub(
                                range,
                                direction = 1,
                                repeatCount = event.nativeKeyEvent.repeatCount,
                            )
                        )
                        true
                    }
                    else -> false
                }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = displayedProgress,
                    range = 0f..1f,
                )
            }
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showingProgramme) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${formatPlaybackClock(programmePosition)} / " +
                        formatPlaybackClock(programmeDuration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("player-programme-progress"),
                )
                Spacer(Modifier.weight(1f))
            } else {
                when {
                    range.domain == SeekbarDomain.RECORDING -> Text(
                        text = formatPlaybackClock(range.positionMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    range.endMs - range.positionMs > TIMESHIFT_LIVE_EDGE_TOLERANCE_MS -> Text(
                        text = "-${formatPlaybackClock(abs(range.positionMs))}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (liveLabelVisible && range.domain == SeekbarDomain.TIMESHIFT) {
                    Text(
                        text = stringResource(R.string.timeshift_live),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (range.domain == SeekbarDomain.RECORDING) {
                    Text(
                        text = formatPlaybackClock(range.endMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(if (focused) 6.dp else 4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayedProgress.coerceIn(0f, 1f))
                        .height(if (focused) 6.dp else 4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
                if (!showingProgramme) epgBoundaryFractions.forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(if (focused) 6.dp else 4.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(if (focused) 6.dp else 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)),
                        )
                    }
                }
            }
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(range.progress.coerceIn(0f, 1f)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .testTag("player-seekbar-thumb"),
                    )
                }
            }
        }
    }
}

/** Thin non-focusable programme progress for non-seekable live TV. */
@Composable
fun ProgrammeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress.coerceIn(0f, 1f),
                    range = 0f..1f,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
        )
    }
}

internal fun formatPlaybackClock(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
