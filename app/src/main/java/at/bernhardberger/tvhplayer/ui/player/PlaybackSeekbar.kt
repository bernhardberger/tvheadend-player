package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
) {
    var focused by remember { mutableStateOf(false) }
    val description = when (range.domain) {
        SeekbarDomain.RECORDING -> stringResource(
            R.string.player_seekbar_recording_description,
            formatPlaybackClock(range.positionMs),
            formatPlaybackClock(range.endMs),
        )
        SeekbarDomain.TIMESHIFT -> stringResource(
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
                        onSeekTo(seekbarScrub(range, direction = -1, repeatCount = 0))
                        true
                    }
                    Key.DirectionRight -> {
                        onSeekTo(seekbarScrub(range, direction = 1, repeatCount = 0))
                        true
                    }
                    else -> false
                }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = range.progress,
                    range = 0f..1f,
                )
            }
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (range.domain) {
                    SeekbarDomain.RECORDING -> formatPlaybackClock(range.positionMs)
                    SeekbarDomain.TIMESHIFT ->
                        if (range.positionMs >= -500L) {
                            stringResource(R.string.timeshift_live)
                        } else {
                            "-${formatPlaybackClock(abs(range.positionMs))}"
                        }
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) 10.dp else 6.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(range.progress.coerceIn(0f, 1f))
                    .height(if (focused) 10.dp else 6.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            epgBoundaryFractions.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 0.dp) // fraction applied via fill width parent measure
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(if (focused) 10.dp else 6.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(if (focused) 10.dp else 6.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)),
                    )
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
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary),
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

private fun formatPlaybackClock(ms: Long): String {
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
