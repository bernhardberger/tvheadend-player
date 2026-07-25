package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.components.ChannelRow
import at.bernhardberger.tvhplayer.ui.TvPlaybackPadding
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ChannelDrawer(
    channels: List<ChannelUi>,
    selectedId: Int,
    nowSec: Long,
    channelsVm: ChannelsViewModel,
    imageLoader: ImageLoader,
    onFocusChannel: (Int) -> Unit,
    onPickChannel: (ChannelUi) -> Unit,
    focusRequester: FocusRequester,
    onCloseDrawer: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val orderedChannelIds = remember(channels) { channels.map { it.id } }
    val channelNumbers = remember(channels) { channels.associate { it.id to it.number } }

    var didInitialRestore by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var pageGeneration by remember { mutableIntStateOf(0) }

    fun pageChannels(direction: Int): Boolean {
        val currentIndex = channels.indexOfFirst { it.id == selectedId }
        val targetIndex = ChannelNavigation.pageTargetIndex(
            itemCount = channels.size,
            currentIndex = currentIndex,
            visibleItemCount = listState.layoutInfo.visibleItemsInfo.size,
            direction = direction,
        ) ?: return true
        if (targetIndex == currentIndex) return true

        val targetId = channels[targetIndex].id
        val generation = ++pageGeneration
        isRestoring = true
        onFocusChannel(targetId)
        coroutineScope.launch {
            try {
                listState.animateScrollToItem(targetIndex)
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.any { it.key == targetId }
                }.filter { it }.first()
                withFrameNanos { }
                focusRequester.requestFocus()
                withFrameNanos { }
            } finally {
                if (pageGeneration == generation) isRestoring = false
            }
        }
        return true
    }

    LaunchedEffect(channels, selectedId) {
        if (didInitialRestore) return@LaunchedEffect
        if (channels.isEmpty()) return@LaunchedEffect

        val id = if (selectedId == -1) channels.first().id else selectedId
        val idx = channels.indexOfFirst { it.id == id }
        if (idx < 0) return@LaunchedEffect

        isRestoring = true

        listState.scrollToItem(idx)

        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.key == id }
        }.filter { it }.first()

        withFrameNanos { }
        focusRequester.requestFocus()
        withFrameNanos { }

        didInitialRestore = true
        isRestoring = false
    }

    Box(
        modifier = Modifier
            .width(460.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.96f),
                    0.82f to Color.Black.copy(alpha = 0.92f),
                    1f to Color.Transparent,
                )
            )
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                ChannelNavigation.pageDirectionForKeyCode(
                    ev.nativeKeyEvent.keyCode
                )?.let(::pageChannels)?.let { return@onPreviewKeyEvent it }
                when (ev.key) {
                    Key.Back, Key.DirectionRight -> {
                        onCloseDrawer(); true
                    }

                    else -> false
                }
            }
    ) {
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxSize()
                    .padding(TvPlaybackPadding),
                contentAlignment = Alignment.Center,
            ) {
                androidx.tv.material3.Text(stringResource(R.string.empty_channel_tag))
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = TvPlaybackPadding,
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight()
                    .focusGroup()
                    .focusRestorer()
            ) {
                items(channels, key = { ch -> ch.id }) { ch ->
                    val isSelected = ch.id == selectedId

                    val now = remember(ch.id, nowSec) { channelsVm.nowEvent(ch.id, nowSec) }
                    val prog = remember(now, nowSec) { now?.progress(nowSec) ?: 0f }

                    ChannelRow(
                        modifier = if (isSelected) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                        number = ChannelNavigation.numberForId(
                            orderedChannelIds,
                            channelNumbers,
                            ch.id,
                        ),
                        name = ch.name,
                        programTitle = now?.title ?: stringResource(R.string.no_epg),
                        progress = if (now != null) prog else null,
                        imageLoader = imageLoader,
                        piconPath = ch.icon,
                        focused = isSelected,
                        onFocus = { if (!isRestoring) onFocusChannel(ch.id) },
                        onConfirm = { onPickChannel(ch) }
                    )

                }
            }
        }
    }
}
