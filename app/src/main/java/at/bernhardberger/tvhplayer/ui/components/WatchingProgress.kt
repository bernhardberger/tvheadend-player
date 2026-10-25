package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.RecordingPlaybackSelection

internal val LocalWatchingChannel = compositionLocalOf<ChannelId?> { null }
internal val LocalActivePlaybackTarget = compositionLocalOf<AppPlaybackTarget?> { null }
internal val LocalRecordingPlaybackSelection = compositionLocalOf<RecordingPlaybackSelection?> { null }

/** Identity only: no playback-position sampling or per-row collection. */
@Composable
internal fun ProvideWatchingProgress(runtime: AppPlaybackRuntime, content: @Composable () -> Unit) {
    val target by runtime.activeTarget.collectAsStateWithLifecycle()
    val recordingSelection by runtime.recordingSelection.collectAsStateWithLifecycle()
    val playback by runtime.state.collectAsStateWithLifecycle()
    val channel = (target as? AppPlaybackTarget.Live)?.channelId
        ?.takeIf { playback == AppPlaybackState.Playing || playback == AppPlaybackState.Buffering }
    CompositionLocalProvider(LocalWatchingChannel provides channel, LocalActivePlaybackTarget provides target,
        LocalRecordingPlaybackSelection provides recordingSelection, content = content)
}

/** Single colour decision for all viewer-position and ambient progress strips. */
internal fun progressColor(watching: Boolean, onAir: Color, viewer: Color): Color = if (watching) viewer else onAir

@Composable
internal fun progressColor(watching: Boolean): Color =
    progressColor(watching, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
