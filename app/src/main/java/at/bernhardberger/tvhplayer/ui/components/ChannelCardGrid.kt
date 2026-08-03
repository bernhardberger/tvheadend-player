package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardContainerDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.channelInitials
import at.bernhardberger.tvheadend.core.Channel
import at.bernhardberger.tvhplayer.ui.ChannelCardWidth
import at.bernhardberger.tvhplayer.ui.CompactChannelCardWidth
import at.bernhardberger.tvhplayer.ui.TvCardSpacing
import at.bernhardberger.tvhplayer.ui.TvSpacing4
import at.bernhardberger.tvhplayer.ui.TvSpacing8
import at.bernhardberger.tvhplayer.ui.TvTextDisabledAlpha
import at.bernhardberger.tvhplayer.ui.TvTrackAlpha
import coil3.ImageLoader

data class ChannelCardModel(
    val channel: Channel,
    val number: Int?,
    val programmeTitle: String,
    val playingNow: Boolean = false,
    val recordingNow: Boolean = false,
    /** 0–1 programme progress when EPG is known; null omits the bar. */
    val progress: Float? = null,
)

/**
 * Shared 3-column large-card channel grid for Channels browse and Simple TV
 * quick select. Spacing leaves room for bounded focus scale without clipping.
 */
@Composable
fun ChannelCardGrid(
    items: List<ChannelCardModel>,
    imageLoader: ImageLoader,
    onFocusChannel: (Int) -> Unit,
    onConfirmChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    focusRequesters: Map<Int, FocusRequester> = emptyMap(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layoutDirection = LocalLayoutDirection.current
        val cardWidth = guidanceCardWidth(
            availableWidth = maxWidth -
                contentPadding.calculateStartPadding(layoutDirection) -
                contentPadding.calculateEndPadding(layoutDirection),
        )
        LazyVerticalGrid(
            columns = GridCells.FixedSize(cardWidth),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(TvCardSpacing),
            verticalArrangement = Arrangement.spacedBy(TvCardSpacing),
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .focusRestorer(),
        ) {
            items(items, key = { it.channel.channelId }) { item ->
                var focused by remember(item.channel.channelId) { mutableStateOf(false) }
                ChannelCard(
                    item = item,
                    focused = focused,
                    imageLoader = imageLoader,
                    modifier = Modifier.width(cardWidth),
                    interactiveModifier = Modifier
                        .testTag("channel-card-${item.channel.channelId}")
                        .then(
                            focusRequesters[item.channel.channelId]?.let {
                                Modifier.focusRequester(it)
                            } ?: Modifier,
                        )
                        .onFocusChanged { focusState ->
                            focused = focusState.isFocused
                            if (focusState.isFocused) onFocusChannel(item.channel.channelId)
                        },
                    onClick = { onConfirmChannel(item.channel) },
                )
            }
        }
    }
}

private fun guidanceCardWidth(availableWidth: Dp): Dp {
    val requiredSpacing = TvCardSpacing * 2
    return if (ChannelCardWidth * 3 + requiredSpacing <= availableWidth) {
        ChannelCardWidth
    } else {
        CompactChannelCardWidth
    }
}

@Composable
private fun ChannelCard(
    item: ChannelCardModel,
    focused: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactiveModifier: Modifier = Modifier,
) {
    val initials = remember(item.channel.name) { channelInitials(item.channel.name) }
    val playingNowDescription = stringResource(R.string.player_on_now)
    val recordingNowDescription = stringResource(R.string.recording_state_recording)
    val accessibilityLabel = buildString {
        item.number?.let {
            append(it)
            append(" ")
        }
        append(item.channel.name)
        append(". ")
        append(item.programmeTitle)
        if (item.playingNow) {
            append(". ")
            append(playingNowDescription)
        }
        if (item.recordingNow) {
            append(". ")
            append(recordingNowDescription)
        }
    }
    StandardCardContainer(
        imageCard = { interactionSource ->
            Card(
                onClick = onClick,
                interactionSource = interactionSource,
                modifier = interactiveModifier
                    .fillMaxWidth()
                    .aspectRatio(CardDefaults.HorizontalImageAspectRatio)
                    .semantics {
                        contentDescription = accessibilityLabel
                        selected = item.playingNow
                    },
                scale = CardDefaults.scale(focusedScale = 1.05f),
                glow = CardDefaults.glow(
                    focusedGlow = Glow(
                        elevationColor = MaterialTheme.colorScheme.primary.copy(
                            alpha = TvTextDisabledAlpha,
                        ),
                        elevation = 8.dp,
                    ),
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CardDefaults.HorizontalImageAspectRatio)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.channel.icon.isNullOrBlank()) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        PiconBox(
                            imageLoader = imageLoader,
                            piconPath = item.channel.icon,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(56.dp),
                        )
                    }
                    if (item.playingNow || item.recordingNow) {
                        ChannelNowIndicators(
                            playingNow = item.playingNow,
                            recordingNow = item.recordingNow,
                            playingTint = MaterialTheme.colorScheme.primary,
                            announceState = false,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(TvSpacing8)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                )
                                .padding(TvSpacing4),
                        )
                    }
                }
            }
        },
        title = {
            Column(modifier = Modifier.padding(top = TvSpacing8)) {
                ChannelTitle(
                    number = item.number,
                    name = item.channel.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.programmeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (focused) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                item.progress?.let { progress ->
                    ProgressStrip(
                        progress = progress,
                        trackColor = if (focused) {
                            MaterialTheme.colorScheme.onSurface.copy(
                                alpha = TvTextDisabledAlpha,
                            )
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = TvTrackAlpha)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel-card-progress-${item.channel.channelId}"),
                    )
                }
            }
        },
        contentColor = CardContainerDefaults.contentColor(
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}
