package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardContainerDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.HomeCardItem
import at.bernhardberger.tvhplayer.core.channelInitials
import at.bernhardberger.tvhplayer.ui.HomeCardMediaHeight
import at.bernhardberger.tvhplayer.ui.HomeCardWidth
import at.bernhardberger.tvhplayer.ui.TvRecordingColor
import at.bernhardberger.tvhplayer.ui.TvSpacing4
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.common.formatClock
import coil3.ImageLoader

/**
 * 16:9 Material for TV card used on Home content rows.
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
    val timeLabel = programmeTimeLabel(item)
    val accessibilityLabel = listOfNotNull(
        item.channelNumber?.let { "$it ${item.channelName}" } ?: item.channelName,
        item.title,
        timeLabel,
    ).joinToString(". ")
    StandardCardContainer(
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                scale = CardDefaults.scale(focusedScale = 1.05f),
                glow = CardDefaults.glow(
                    focusedGlow = Glow(
                        elevationColor = MaterialTheme.colorScheme.primary.copy(
                            alpha = TvTextDisabledAlpha,
                        ),
                        elevation = 8.dp,
                    ),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeCardMediaHeight)
                    .semantics { contentDescription = accessibilityLabel }
                    .testTag(testTag),
            ) {
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
                                .padding(TvSpacing8)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.extraSmall,
                                )
                                .padding(horizontal = TvSpacing8, vertical = TvSpacing4),
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
            }
        },
        title = {
            Column(modifier = Modifier.padding(top = TvSpacing8)) {
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
                timeLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        contentColor = CardContainerDefaults.contentColor(
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.width(HomeCardWidth),
    )
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
