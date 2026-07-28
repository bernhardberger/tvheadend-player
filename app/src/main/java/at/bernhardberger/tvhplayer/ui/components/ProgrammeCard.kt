package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.HomeCardItem
import at.bernhardberger.tvhplayer.core.channelInitials
import at.bernhardberger.tvhplayer.ui.HomeCardMediaHeight
import at.bernhardberger.tvhplayer.ui.HomeCardWidth
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader

/**
 * 16:9 media-over-text card used on Home content rows (176×99 media area).
 * Shared so Channels can adopt the same shape later.
 */
@Composable
fun ProgrammeCard(
    item: HomeCardItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = item.key,
) {
    val initials = remember(item.channelName) { channelInitials(item.channelName) }
    val accent = rememberChannelAccent(
        imageLoader = imageLoader,
        piconPath = item.piconPath,
        channelId = item.channelId,
    )
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.06f),
        modifier = modifier
            .width(HomeCardWidth)
            .testTag(testTag),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeCardMediaHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.piconPath.isNullOrBlank()) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    PiconBox(
                        imageLoader = imageLoader,
                        piconPath = item.piconPath,
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(56.dp),
                    )
                }
                if (item.recordingNow) {
                    Text(
                        text = stringResource(R.string.home_badge_rec),
                        style = MaterialTheme.typography.labelMedium,
                        color = TvRecordingColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                item.progress?.let { progress ->
                    ProgressStrip(
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = buildString {
                        item.channelNumber?.let {
                            append(it)
                            append(" ")
                        }
                        append(item.channelName)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                programmeTimeLabel(item)?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun programmeTimeLabel(item: HomeCardItem): String? {
    item.remainingMinutes?.let { minutes ->
        return stringResource(R.string.home_minutes_left, minutes)
    }
    val start = item.startSec
    val stop = item.stopSec
    return when {
        // Scheduled — start is the single most useful fact.
        start != null && !item.playable -> {
            stringResource(R.string.home_starts_at, formatClock(start))
        }
        // Completed (or other past) recording — show air window.
        start != null && stop != null && item.recordingId != null -> {
            stringResource(
                R.string.home_time_range,
                formatClock(start),
                formatClock(stop),
            )
        }
        start != null -> stringResource(R.string.home_starts_at, formatClock(start))
        stop != null -> stringResource(R.string.home_ends_at, formatClock(stop))
        else -> null
    }
}
