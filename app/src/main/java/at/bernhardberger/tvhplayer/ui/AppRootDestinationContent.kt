package at.bernhardberger.tvhplayer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.ui.components.ChannelSettingsNotice
import androidx.compose.ui.Modifier
import at.bernhardberger.tvhplayer.profiling.profileLayout
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection
import at.bernhardberger.tvhplayer.ui.components.ContentContainer
import at.bernhardberger.tvhplayer.ui.player.RecordingPlayerScreen
import at.bernhardberger.tvhplayer.ui.player.VideoPlayerScreen
import at.bernhardberger.tvhplayer.ui.screens.ChannelsScreen
import at.bernhardberger.tvhplayer.ui.screens.EpgGridScreen
import at.bernhardberger.tvhplayer.ui.screens.RecordingsScreen
import at.bernhardberger.tvhplayer.ui.screens.RecordingsScreenState
import at.bernhardberger.tvhplayer.ui.screens.SettingsScreen

@Composable
internal fun StartupGatedChannelsContent(
    contentAllowed: Boolean,
    channelsContent: @Composable () -> Unit,
) {
    if (contentAllowed) channelsContent()
}

@Composable
internal fun StartupGatedPlayerContent(
    contentAllowed: Boolean,
    playerContent: @Composable () -> Unit,
) {
    if (contentAllowed) playerContent()
}

@Composable
internal fun ChannelsRouteContent(
    channelsVm: ChannelsViewModel,
    contentAllowed: Boolean,
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    playingChannelId: ChannelId?,
    connectionUiState: ConnectionUiState,
    onRetryConnection: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlay: (LivePlaybackSelection, String) -> Unit,
) {
    StartupGatedChannelsContent(
        contentAllowed = contentAllowed,
    ) {
        ContentContainer(modifier = Modifier.profileLayout("channels")) {
            BrowsingScopeContent(channelsVm, initialFocusEnabled) { ChannelsScreen(
                channelViewModel = channelsVm,
                contentPadding = contentPadding,
                initialFocusEnabled = initialFocusEnabled,
                playingChannelId = playingChannelId,
                connectionUiState = connectionUiState,
                onRetryConnection = onRetryConnection,
                onOpenConnectionSettings = onOpenConnectionSettings,
                onPlay = onPlay,
            ) }
        }
    }
}

@Composable
internal fun GuideRouteContent(
    channelsVm: ChannelsViewModel,
    contentAllowed: Boolean,
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    onPlayRecording: (RecordingPlaybackSelection) -> Unit,
    onPlay: (LivePlaybackSelection, String) -> Unit,
) {
    if (contentAllowed) {
        ContentContainer(modifier = Modifier.profileLayout("guide")) {
            BrowsingScopeContent(channelsVm, initialFocusEnabled) { EpgGridScreen(
                channelViewModel = channelsVm,
                contentPadding = contentPadding,
                initialFocusEnabled = initialFocusEnabled,
                connectionUiState = connectionUiState,
                onRetry = onRetry,
                onOpenConnectionSettings = onOpenConnectionSettings,
                onClearCategory = {},
                onPlayRecording = onPlayRecording,
                onPlay = onPlay,
            ) }
        }
    }
}

@Composable
internal fun RecordingsRouteContent(
    contentAllowed: Boolean,
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    backEnabled: Boolean,
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit,
    state: RecordingsScreenState,
) {
    if (contentAllowed) {
        ContentContainer(modifier = Modifier.profileLayout("recordings")) {
            RecordingsScreen(
                contentPadding = contentPadding,
                initialFocusEnabled = initialFocusEnabled,
                backEnabled = backEnabled,
                connectionUiState = connectionUiState,
                onRetry = onRetry,
                onPlayRecording = onPlayRecording,
                state = state,
            )
        }
    }
}

@Composable
internal fun SettingsRouteContent(
    channelsVm: ChannelsViewModel,
    contentAllowed: Boolean,
    section: SettingsSection,
    initialFocusEnabled: Boolean,
    contentPadding: PaddingValues,
    backEnabled: Boolean,
    onNavigate: (SettingsSection) -> Unit,
) {
    if (contentAllowed) {
        ContentContainer {
            SettingsScreen(
                channelsVm = channelsVm,
                section = section,
                initialFocusEnabled = initialFocusEnabled,
                contentPadding = contentPadding,
                backEnabled = backEnabled,
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
internal fun LivePlayerRouteContent(
    channelsVm: ChannelsViewModel,
    contentAllowed: Boolean,
    channelId: ChannelId,
    channelName: String,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
) {
    StartupGatedPlayerContent(contentAllowed = contentAllowed) {
        VideoPlayerScreen(
            channelsVm = channelsVm,
            channelId = channelId,
            channelName = channelName,
            onReconnect = onReconnect,
            onClose = onClose,
        )
    }
}

@Composable
internal fun BrowsingScopeContent(
    channelsVm: ChannelsViewModel,
    initialFocusEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val state by channelsVm.scope.collectAsStateWithLifecycle()
    val failed by channelsVm.settingsFailure.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ChannelSettingsNotice(
            loaded = state.settingsLoaded,
            failed = failed,
            onRetry = channelsVm::retrySettings,
            initialFocusEnabled = initialFocusEnabled,
        )
        if (state.settingsLoaded) Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun RecordingPlayerRouteContent(
    contentAllowed: Boolean,
    recordingId: DvrEntryId,
    playbackStart: RecordingPlaybackStart,
    connectionState: ConnectionState,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
) {
    if (contentAllowed) {
        RecordingPlayerScreen(
            recordingId = recordingId,
            playbackStart = playbackStart,
            connectionState = connectionState,
            onReconnect = onReconnect,
            onClose = onClose,
        )
    }
}
