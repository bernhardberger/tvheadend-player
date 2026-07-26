package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.HomeNowPlaying
import at.bernhardberger.tvhplayer.core.HomeRecentChannel
import at.bernhardberger.tvhplayer.core.HomeRecordingItem
import at.bernhardberger.tvhplayer.core.buildHomeDashboard
import at.bernhardberger.tvhplayer.player.PlayerSession
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
    channelsVm: ChannelsViewModel = koinViewModel(),
    playerSession: PlayerSession = koinInject(),
    lastPlayedStore: LastPlayedChannelStore = koinInject(),
    dvrRepository: DvrRepository = koinInject(),
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
    LaunchedEffect(model.nowPlaying, model.recentChannels, model.onNow) {
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
                        modifier = Modifier.focusRequester(initialFocus),
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
                item { HomeSectionTitle(stringResource(R.string.home_recent)) }
                items(model.recentChannels, key = { "recent-${it.channelId}" }) { item ->
                    HomeChannelRow(item) {
                        val channel = channelsById[item.channelId] ?: return@HomeChannelRow
                        onPlayChannel(channel.id, channel.id, channel.name)
                    }
                }
            }

            if (model.onNow.isNotEmpty()) {
                item { HomeSectionTitle(stringResource(R.string.home_on_now)) }
                items(model.onNow, key = { "onnow-${it.channelId}" }) { item ->
                    HomeChannelRow(item) {
                        val channel = channelsById[item.channelId] ?: return@HomeChannelRow
                        onPlayChannel(channel.id, channel.id, channel.name)
                    }
                }
            }

            if (model.recordingNow.isNotEmpty()) {
                item { HomeSectionTitle(stringResource(R.string.home_recording_now)) }
                items(model.recordingNow, key = { "recnow-${it.id}" }) { item ->
                    HomeRecordingRow(item) { onPlayRecording(item.id) }
                }
            }

            if (model.latestRecordings.isNotEmpty()) {
                item { HomeSectionTitle(stringResource(R.string.home_latest_recordings)) }
                items(model.latestRecordings, key = { "latest-${it.id}" }) { item ->
                    HomeRecordingRow(item) { onPlayRecording(item.id) }
                }
            }

            if (model.upcomingRecordings.isNotEmpty()) {
                item { HomeSectionTitle(stringResource(R.string.home_upcoming_recordings)) }
                items(model.upcomingRecordings, key = { "up-${it.id}" }) { item ->
                    HomeRecordingRow(item) { onOpenRecordings() }
                }
            }

            if (
                model.nowPlaying == null &&
                model.recentChannels.isEmpty() &&
                model.onNow.isEmpty() &&
                model.latestRecordings.isEmpty()
            ) {
                item {
                    Text(
                        text = when (connectionUiState) {
                            ConnectionUiState.Connecting,
                            ConnectionUiState.SyncingChannels ->
                                stringResource(R.string.loading)
                            is ConnectionUiState.Error ->
                                stringResource(R.string.epg_server_failure)
                            else -> stringResource(R.string.home_empty)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .focusRequester(initialFocus),
                    )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = {
            Text(
                text = item.programmeTitle ?: item.channelName,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append(item.channelName)
                    if (item.isRecording) {
                        append(" • ")
                        append(stringResource(R.string.recordings_recording_now))
                    }
                },
            )
        },
        scale = ListItemDefaults.scale(focusedScale = 1.02f, focusedSelectedScale = 1.02f),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeChannelRow(
    item: HomeRecentChannel,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(item.channelName) },
        supportingContent = {
            item.programmeTitle?.let { Text(it) }
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HomeRecordingRow(
    item: HomeRecordingItem,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(item.title) },
        supportingContent = {
            item.subtitle?.let { Text(it) }
        },
        scale = ListItemDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    )
}
