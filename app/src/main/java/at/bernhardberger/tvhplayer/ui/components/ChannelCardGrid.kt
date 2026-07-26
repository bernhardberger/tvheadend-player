package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.channelInitials
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import coil3.ImageLoader

data class ChannelCardModel(
    val channel: ChannelUi,
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
    selectedId: Int,
    imageLoader: ImageLoader,
    onFocusChannel: (Int) -> Unit,
    onConfirmChannel: (ChannelUi) -> Unit,
    modifier: Modifier = Modifier,
    focusRequesters: Map<Int, FocusRequester> = emptyMap(),
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(12.dp),
    columns: Int = 3,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .focusGroup(),
    ) {
        items(items, key = { it.channel.id }) { item ->
            val selected = item.channel.id == selectedId
            ChannelCard(
                item = item,
                selected = selected,
                imageLoader = imageLoader,
                modifier = Modifier
                    .then(
                        focusRequesters[item.channel.id]?.let { Modifier.focusRequester(it) }
                            ?: Modifier,
                    )
                    .onFocusChanged {
                        if (it.isFocused) onFocusChannel(item.channel.id)
                    },
                onClick = { onConfirmChannel(item.channel) },
            )
        }
    }
}

@Composable
fun ChannelCard(
    item: ChannelCardModel,
    selected: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initials = remember(item.channel.name) { channelInitials(item.channel.name) }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
    ) {
        Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small,
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
                // Match ChannelRow: play glyph for the live channel; text only for REC.
                if (item.playingNow) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_on_now),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .padding(2.dp),
                    )
                } else if (item.recordingNow) {
                    Text(
                        text = stringResource(R.string.recordings_recording_now),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Text(
                text = buildString {
                    item.number?.let {
                        append(it)
                        append("  ")
                    }
                    append(item.channel.name)
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.programmeTitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) {
                    MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.86f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            item.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}
