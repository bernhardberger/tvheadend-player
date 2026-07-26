package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.HomeNowPlaying
import at.bernhardberger.tvhplayer.core.HomeFocusTarget
import at.bernhardberger.tvhplayer.core.HomeRecentChannel
import at.bernhardberger.tvhplayer.core.HomeRecordingItem
import at.bernhardberger.tvhplayer.core.buildHomeDashboard
import at.bernhardberger.tvhplayer.core.homeInitialFocusTarget
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.TvBrowsePanelAlpha
import at.bernhardberger.tvhplayer.ui.TvScreenPadding
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    connectionUiState: ConnectionUiState,
    onRetryConnection: () -> Unit,
    onPlayChannel: (channelId: Int, serviceId: Int, name: String) -> Unit,
    onPlayRecording: (recordingId: Int) -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenChannels: () -> Unit,
    channelsVm: ChannelsViewModel = koinViewModel(),
    playerSession: PlayerSession = koinInject(),
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
    dvrRepository: DvrRepository = koinInject(),
    imageLoader: ImageLoader = koinInject(),
) {
    val channels by channelsVm.channels.collectAsStateWithLifecycle()
    val allChannels by channelsVm.allChannels.collectAsStateWithLifecycle()
    val activeServiceId by playerSession.activeServiceId.collectAsStateWithLifecycle()
    val activeRecordingId by playerSession.activeRecordingId.collectAsStateWithLifecycle()
    val recentIds by lastPlayedStore.recentChannelIds.collectAsStateWithLifecycle(initialValue = emptyList())
    val recordings by dvrRepository.entries.collectAsStateWithLifecycle()
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(30_000)
        }
    }

    val channelsById = remember(allChannels) { allChannels.associateBy { it.id } }
    val onNowEvents = remember(channels, nowSec) {
        channels.mapNotNull { channel ->
            val event = channelsVm.nowEvent(channel.id, nowSec) ?: return@mapNotNull null
            channel to event
        }
    }
    val activeProgramme = remember(activeServiceId, nowSec) {
        activeServiceId?.let { channelsVm.nowEvent(it, nowSec)?.title }
    }
    val model = remember(
        channelsById,
        activeServiceId,
        activeRecordingId,
        activeProgramme,
        recentIds,
        onNowEvents,
        recordings,
        nowSec,
    ) {
        buildHomeDashboard(
            channelsById = channelsById,
            activeServiceId = activeServiceId,
            activeRecordingId = activeRecordingId,
            activeProgrammeTitle = activeProgramme,
            recentChannelIds = recentIds,
            onNowEvents = onNowEvents,
            recordings = recordings,
            nowSec = nowSec,
        )
    }

    val initialFocus = remember { FocusRequester() }
    val initialFocusTarget = remember(model) { homeInitialFocusTarget(model) }
    LaunchedEffect(initialFocusTarget, connectionUiState) {
        runCatching { initialFocus.requestFocus() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = TvBrowsePanelAlpha),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(TvScreenPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.nav_home),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }

            model.nowPlaying?.let { now ->
                item(key = "now-playing") {
                    HomeSectionTitle(stringResource(R.string.home_now_playing))
                    HomeNowPlayingCard(
                        item = now,
                        imageLoader = imageLoader,
                        piconPath = channelsById[now.channelId]?.icon,
                        modifier = if (initialFocusTarget == HomeFocusTarget.NOW_PLAYING) {
                            Modifier.focusRequester(initialFocus)
                        } else {
                            Modifier
                        },
                        onClick = {
                            if (now.isRecording) {
                                activeRecordingId?.let(onPlayRecording)
                            } else {
                                val channel = channelsById[now.channelId] ?: return@HomeNowPlayingCard
                                onPlayChannel(channel.id, channel.id, channel.name)
                            }
                        },
                    )
                }
            }

            if (model.recentChannels.isNotEmpty()) {
                item(key = "recent-section") {
                    HomeSectionTitle(stringResource(R.string.home_recent))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            model.recentChannels,
                            key = { _, item -> "recent-${item.channelId}" },
                        ) { index, item ->
                            HomeChannelCard(
                                item = item,
                                imageLoader = imageLoader,
                                piconPath = channelsById[item.channelId]?.icon,
                                modifier = if (
                                    index == 0 && initialFocusTarget == HomeFocusTarget.RECENT_CHANNEL
                                ) Modifier.focusRequester(initialFocus) else Modifier,
                                onClick = {
                                    channelsById[item.channelId]?.let { channel ->
                                        onPlayChannel(channel.id, channel.id, channel.name)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (model.onNow.isNotEmpty()) {
                item(key = "on-now-section") {
                    HomeSectionTitle(stringResource(R.string.home_on_now))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            model.onNow,
                            key = { _, item -> "onnow-${item.channelId}" },
                        ) { index, item ->
                            HomeChannelCard(
                                item = item,
                                imageLoader = imageLoader,
                                piconPath = channelsById[item.channelId]?.icon,
                                modifier = if (
                                    index == 0 && initialFocusTarget == HomeFocusTarget.ON_NOW
                                ) Modifier.focusRequester(initialFocus) else Modifier,
                                onClick = {
                                    channelsById[item.channelId]?.let { channel ->
                                        onPlayChannel(channel.id, channel.id, channel.name)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (model.recordingNow.isNotEmpty()) {
                item(key = "recording-now-section") {
                    HomeSectionTitle(stringResource(R.string.home_recording_now))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            model.recordingNow,
                            key = { _, item -> "recnow-${item.id}" },
                        ) { index, item ->
                            HomeRecordingCard(
                                item = item,
                                imageLoader = imageLoader,
                                piconPath = channelsById[item.channelId]?.icon,
                                modifier = if (
                                    index == 0 && initialFocusTarget == HomeFocusTarget.RECORDING_NOW
                                ) Modifier.focusRequester(initialFocus) else Modifier,
                                onClick = { onPlayRecording(item.id) },
                            )
                        }
                    }
                }
            }

            if (model.latestRecordings.isNotEmpty()) {
                item(key = "latest-section") {
                    HomeSectionTitle(stringResource(R.string.home_latest_recordings))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            model.latestRecordings,
                            key = { _, item -> "latest-${item.id}" },
                        ) { index, item ->
                            HomeRecordingCard(
                                item = item,
                                imageLoader = imageLoader,
                                piconPath = channelsById[item.channelId]?.icon,
                                modifier = if (
                                    index == 0 && initialFocusTarget == HomeFocusTarget.LATEST_RECORDING
                                ) Modifier.focusRequester(initialFocus) else Modifier,
                                onClick = { onPlayRecording(item.id) },
                            )
                        }
                    }
                }
            }

            if (model.upcomingRecordings.isNotEmpty()) {
                item(key = "upcoming-section") {
                    HomeSectionTitle(stringResource(R.string.home_upcoming_recordings))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(
                            model.upcomingRecordings,
                            key = { _, item -> "up-${item.id}" },
                        ) { index, item ->
                            HomeRecordingCard(
                                item = item,
                                imageLoader = imageLoader,
                                piconPath = channelsById[item.channelId]?.icon,
                                modifier = if (
                                    index == 0 && initialFocusTarget == HomeFocusTarget.UPCOMING_RECORDING
                                ) Modifier.focusRequester(initialFocus) else Modifier,
                                onClick = onOpenRecordings,
                            )
                        }
                    }
                }
            }

            if (
                model.nowPlaying == null &&
                model.recentChannels.isEmpty() &&
                model.onNow.isEmpty() &&
                model.latestRecordings.isEmpty() &&
                model.recordingNow.isEmpty() &&
                model.upcomingRecordings.isEmpty()
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = when (connectionUiState) {
                                ConnectionUiState.Connecting,
                                ConnectionUiState.SyncingChannels -> stringResource(R.string.loading)
                                is ConnectionUiState.Error -> stringResource(R.string.epg_server_failure)
                                else -> stringResource(R.string.home_empty)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val retry = connectionUiState == ConnectionUiState.Connecting ||
                            connectionUiState == ConnectionUiState.SyncingChannels ||
                            connectionUiState is ConnectionUiState.Error
                        Button(
                            onClick = if (retry) onRetryConnection else onOpenChannels,
                            modifier = Modifier.focusRequester(initialFocus),
                        ) {
                            Text(stringResource(if (retry) R.string.retry else R.string.nav_channels))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun HomeNowPlayingCard(
    item: HomeNowPlaying,
    imageLoader: ImageLoader,
    piconPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PiconBox(
                imageLoader = imageLoader,
                piconPath = piconPath,
                modifier = Modifier.width(88.dp).height(56.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.programmeTitle ?: item.channelName,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(item.channelName)
                        if (item.isRecording) {
                            append(" • ")
                            append(stringResource(R.string.recordings_recording_now))
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun HomeChannelCard(
    item: HomeRecentChannel,
    imageLoader: ImageLoader,
    piconPath: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.width(300.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PiconBox(
                imageLoader = imageLoader,
                piconPath = piconPath,
                modifier = Modifier.width(64.dp).height(44.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.channelName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.programmeTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRecordingCard(
    item: HomeRecordingItem,
    imageLoader: ImageLoader,
    piconPath: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.width(320.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PiconBox(
                imageLoader = imageLoader,
                piconPath = piconPath,
                modifier = Modifier.width(64.dp).height(44.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
