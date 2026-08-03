@file:OptIn(at.bernhardberger.tvheadend.playback.ExperimentalPlaybackDiagnosticsApi::class)

package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import at.bernhardberger.tvheadend.core.TimeshiftState
import at.bernhardberger.tvhplayer.settings.AspectRatioMode

@Composable
internal fun PlaybackStatsOverlay(
    diagnostics: at.bernhardberger.tvhplayer.player.PlaybackDiagnosticsSnapshot,
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier,
    timeshiftState: TimeshiftState? = null,
) {
    PlaybackStatsOverlay(
        diagnostics = diagnostics.toSdkPlaybackDiagnosticsSnapshot(),
        aspectRatio = aspectRatio,
        modifier = modifier,
        timeshiftState = timeshiftState,
    )
}
