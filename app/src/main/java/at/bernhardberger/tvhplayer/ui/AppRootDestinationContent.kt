package at.bernhardberger.tvhplayer.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.core.PlaybackRecoverySecondaryAction
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
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
import at.bernhardberger.tvhplayer.ui.screens.SimpleTvUnlockScreen

@Composable
internal fun StartupGatedChannelsContent(
    contentAllowed: Boolean,
    routeAllowed: Boolean = true,
    channelsContent: @Composable () -> Unit,
) {
    if (contentAllowed && routeAllowed) channelsContent()
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
    contentAllowed: Boolean,
    routeAllowed: Boolean,
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
        routeAllowed = routeAllowed,
    ) {
        ContentContainer {
            ChannelsScreen(
                contentPadding = contentPadding,
                initialFocusEnabled = initialFocusEnabled,
                playingChannelId = playingChannelId,
                connectionUiState = connectionUiState,
                onRetryConnection = onRetryConnection,
                onOpenConnectionSettings = onOpenConnectionSettings,
                onPlay = onPlay,
            )
        }
    }
}

@Composable
internal fun GuideRouteContent(
    contentAllowed: Boolean,
    routeAllowed: Boolean,
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
    onOpenConnectionSettings: () -> Unit,
    timeshiftAllowed: Boolean,
    recordingsAllowed: Boolean,
    onPlayRecording: (RecordingPlaybackSelection) -> Unit,
    onPlay: (LivePlaybackSelection, String) -> Unit,
) {
    if (contentAllowed && routeAllowed) {
        ContentContainer {
            EpgGridScreen(
                contentPadding = contentPadding,
                initialFocusEnabled = initialFocusEnabled,
                connectionUiState = connectionUiState,
                onRetry = onRetry,
                onOpenConnectionSettings = onOpenConnectionSettings,
                onClearCategory = {},
                timeshiftAllowed = timeshiftAllowed,
                recordingsAllowed = recordingsAllowed,
                onPlayRecording = onPlayRecording,
                onPlay = onPlay,
            )
        }
    }
}

@Composable
internal fun RecordingsRouteContent(
    contentAllowed: Boolean,
    routeAllowed: Boolean,
    contentPadding: PaddingValues,
    initialFocusEnabled: Boolean,
    backEnabled: Boolean,
    connectionUiState: ConnectionUiState,
    onRetry: () -> Unit,
    onPlayRecording: (RecordingPlaybackSelection, RecordingPlaybackStart) -> Unit,
    state: RecordingsScreenState,
) {
    if (contentAllowed && routeAllowed) {
        ContentContainer {
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
    contentAllowed: Boolean,
    routeAllowed: Boolean,
    section: SettingsSection,
    initialFocusEnabled: Boolean,
    contentPadding: PaddingValues,
    backEnabled: Boolean,
    onNavigate: (SettingsSection) -> Unit,
    onStartSimpleTv: (SimpleTvSettings) -> Unit,
) {
    if (contentAllowed && routeAllowed) {
        ContentContainer {
            SettingsScreen(
                section = section,
                initialFocusEnabled = initialFocusEnabled,
                contentPadding = contentPadding,
                backEnabled = backEnabled,
                onNavigate = onNavigate,
                onStartSimpleTv = onStartSimpleTv,
            )
        }
    }
}

@Composable
internal fun UnlockRouteContent(
    contentAllowed: Boolean,
    backEnabled: Boolean,
    onExited: () -> Unit,
    onBack: () -> Unit,
) {
    if (contentAllowed) {
        ContentContainer {
            SimpleTvUnlockScreen(
                backEnabled = backEnabled,
                onExited = onExited,
                onBack = onBack,
            )
        }
    }
}

@Composable
internal fun LivePlayerRouteContent(
    contentAllowed: Boolean,
    channelId: ChannelId,
    channelName: String,
    timeshiftAllowed: Boolean,
    showStop: Boolean,
    recordingActionsAllowed: Boolean,
    playerCloseAllowed: Boolean,
    fullPlaybackOptionsAvailable: Boolean,
    recoverySecondaryAction: PlaybackRecoverySecondaryAction,
    onReconnect: () -> Unit,
    onUnlock: () -> Unit,
    onClose: () -> Unit,
) {
    StartupGatedPlayerContent(contentAllowed = contentAllowed) {
        VideoPlayerScreen(
            channelId = channelId,
            channelName = channelName,
            timeshiftAllowed = timeshiftAllowed,
            showStop = showStop,
            recordingActionsAllowed = recordingActionsAllowed,
            playerCloseAllowed = playerCloseAllowed,
            fullPlaybackOptionsAvailable = fullPlaybackOptionsAvailable,
            recoverySecondaryAction = recoverySecondaryAction,
            onReconnect = onReconnect,
            onUnlock = onUnlock,
            onClose = onClose,
        )
    }
}

@Composable
internal fun RecordingPlayerRouteContent(
    contentAllowed: Boolean,
    routeAllowed: Boolean,
    recordingId: DvrEntryId,
    playbackStart: RecordingPlaybackStart,
    showStop: Boolean,
    showSimpleTvExit: Boolean,
    playerCloseAllowed: Boolean,
    fullPlaybackOptionsAvailable: Boolean,
    connectionState: ConnectionState,
    onReconnect: () -> Unit,
    onUnlock: () -> Unit,
    onClose: () -> Unit,
) {
    if (contentAllowed && routeAllowed) {
        RecordingPlayerScreen(
            recordingId = recordingId,
            playbackStart = playbackStart,
            showStop = showStop,
            showSimpleTvExit = showSimpleTvExit,
            playerCloseAllowed = playerCloseAllowed,
            fullPlaybackOptionsAvailable = fullPlaybackOptionsAvailable,
            connectionState = connectionState,
            onReconnect = onReconnect,
            onUnlock = onUnlock,
            onClose = onClose,
        )
    }
}
