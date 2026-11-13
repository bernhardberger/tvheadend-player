package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel

@Composable
internal fun LivePlayerDiagnostics(
    viewModel: VideoPlayerViewModel,
    enabled: Boolean,
    visible: Boolean,
    aspectRatio: at.bernhardberger.tvhplayer.settings.AspectRatioMode,
    timeshiftState: AppTimeshiftState,
    playbackRuntime: at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime,
    modifier: Modifier,
) {
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    PlayerDiagnosticsEffect(playbackRuntime, enabled)
    PlaybackStatsVisibility(visible = visible, modifier = modifier) {
        PlaybackStatsOverlay(
            diagnostics = diagnostics,
            aspectRatio = aspectRatio,
            timeshiftState = timeshiftState,
            frameRate = playbackRuntime.frameRate,
        )
    }
}
