package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.adjacentTagId
import at.bernhardberger.tvhplayer.htsp.ChannelTagUi

@Composable
fun ChannelTagSelector(
    tags: List<ChannelTagUi>,
    activeTagId: Int?,
    onSelectTag: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showChooser by remember { mutableStateOf(false) }
    val activeIndex = tags.indexOfFirst { it.id == activeTagId }
    val activeName = if (activeTagId == null || activeIndex < 0) {
        stringResource(R.string.all_channels)
    } else {
        tags[activeIndex].name
    }
    val canMoveLeft = activeTagId != null
    val canMoveRight = activeIndex < tags.lastIndex

    Button(
        onClick = { showChooser = true },
        scale = ButtonDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                val targetTagId = adjacentTagId(tags, activeTagId, direction)
                if (targetTagId != activeTagId) onSelectTag(targetTagId)
                true
            },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_channel_tag),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(26.dp)
                    .alpha(if (canMoveLeft) 1f else 0.3f),
            )
            Text(
                text = activeName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_channel_tag),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(26.dp)
                    .alpha(if (canMoveRight) 1f else 0.3f),
            )
        }
    }

    if (showChooser) {
        ChannelTagChooser(
            tags = tags,
            activeTagId = activeTagId,
            onSelectTag = {
                onSelectTag(it)
                showChooser = false
            },
            onDismiss = { showChooser = false },
        )
    }
}

@Composable
private fun ChannelTagChooser(
    tags: List<ChannelTagUi>,
    activeTagId: Int?,
    onSelectTag: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = if (activeTagId == null) {
        0
    } else {
        tags.indexOfFirst { it.id == activeTagId }.coerceAtLeast(0) + 1
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(activeTagId, tags) {
        withFrameNanos { }
        selectedFocus.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f))
                .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.width(620.dp),
                shape = MaterialTheme.shapes.large,
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.choose_channel_tag),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .focusGroup(),
                    ) {
                        item(key = "all-channels") {
                            TagChoice(
                                text = stringResource(R.string.all_channels),
                                selected = activeTagId == null,
                                selectedFocus = selectedFocus,
                                onClick = { onSelectTag(null) },
                            )
                        }
                        items(tags, key = { it.id }) { tag ->
                            TagChoice(
                                text = tag.name,
                                selected = activeTagId == tag.id,
                                selectedFocus = selectedFocus,
                                onClick = { onSelectTag(tag.id) },
                            )
                        }
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChoice(
    text: String,
    selected: Boolean,
    selectedFocus: FocusRequester,
    onClick: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        scale = ListItemDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier),
    )
}

@Composable
fun UnavailableTagNotice(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.active_tag_unavailable),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }
}
