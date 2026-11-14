package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import at.bernhardberger.tvhplayer.di.PlayerStatsLevel

@Composable
internal fun LivePlayerDiagnostics(
    viewModel: VideoPlayerViewModel,
    level: PlayerStatsLevel,
    visible: Boolean,
    accessible: Boolean,
    targetKey: Any?,
    aspectRatio: at.bernhardberger.tvhplayer.settings.AspectRatioMode,
    timeshiftState: AppTimeshiftState,
    playbackRuntime: at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime,
    raster: String?,
    modifier: Modifier,
) {
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    PlayerDiagnosticsEffect(playbackRuntime, level.enabled)
    PlaybackStatsVisibility(visible = visible, modifier = modifier) {
        PlaybackStatsOverlay(
            diagnostics = diagnostics,
            aspectRatio = aspectRatio,
            timeshiftState = timeshiftState,
            frameRate = playbackRuntime.frameRate,
            level = level,
            accessible = accessible,
            targetKey = targetKey,
            raster = raster,
        )
    }
}

/** The card's live rows for the channel message, with their own 60 s history while it shows. */
@Composable
internal fun rememberLiveStatsRows(
    viewModel: VideoPlayerViewModel,
    level: PlayerStatsLevel,
    targetKey: Any?,
): List<String> {
    if (!level.enabled) return emptyList()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    return liveStatsRows(diagnostics, rememberStatsWindow(targetKey, diagnostics), level)
}
