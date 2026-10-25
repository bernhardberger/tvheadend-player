package at.bernhardberger.tvhplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import kotlinx.coroutines.delay

internal val LocalWatchingChannel = compositionLocalOf<ChannelId?> { null }
internal val LocalWatchingTarget = compositionLocalOf<AppPlaybackTarget?> { null }
internal val LocalWatchingIndicator = compositionLocalOf { ChannelPlaybackIndicator.NONE }
internal val LocalRecordingPosition = compositionLocalOf<State<RecordingViewerPosition?>?> { null }

internal data class RecordingViewerPosition(val recordingId: DvrEntryId, val positionMs: Long, val durationMs: Long) {
    val fraction: Float? get() = durationMs.takeIf { it > 0 }?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
}

@Composable
internal fun watchingIndicator(event: EpgEvent?, nowSec: Long): ChannelPlaybackIndicator =
    if (event != null && event.channelId == LocalWatchingChannel.current &&
        nowSec >= event.start.epochSeconds && nowSec < event.stop.epochSeconds) LocalWatchingIndicator.current
    else ChannelPlaybackIndicator.NONE

@Composable
internal fun watchingIndicator(recordingId: DvrEntryId?): ChannelPlaybackIndicator =
    if (recordingId != null && LocalWatchingTarget.current == AppPlaybackTarget.Recording(recordingId))
        LocalWatchingIndicator.current else ChannelPlaybackIndicator.NONE

@Composable
internal fun recordingViewerPosition(recordingId: DvrEntryId?): RecordingViewerPosition? =
    LocalRecordingPosition.current?.value?.takeIf {
        it.recordingId == recordingId && watchingIndicator(recordingId) != ChannelPlaybackIndicator.NONE
    }

/** One identity/intent source; position changes invalidate only consumers of the sampled state. */
@Composable
internal fun ProvideWatchingProgress(runtime: AppPlaybackRuntime, content: @Composable () -> Unit) {
    val target by runtime.activeTarget.collectAsStateWithLifecycle()
    val playback by runtime.state.collectAsStateWithLifecycle()
    val playWhenReady by rememberPlaybackIntent(runtime.player)
    val indicator = playbackIndicator(target, target, playback, playWhenReady)
    val channel = (target as? AppPlaybackTarget.Live)?.channelId
    val position = remember(runtime, target) { mutableStateOf<RecordingViewerPosition?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(runtime, target, lifecycle) {
        val recording = target as? AppPlaybackTarget.Recording ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                position.value = RecordingViewerPosition(recording.recordingId,
                    runtime.player.currentPosition.coerceAtLeast(0), runtime.player.duration)
                delay(1_000)
            }
        }
    }
    CompositionLocalProvider(LocalWatchingChannel provides channel, LocalWatchingTarget provides target,
        LocalWatchingIndicator provides indicator, LocalRecordingPosition provides position, content = content)
}

/** Single colour decision for all viewer-position and ambient progress strips. */
internal fun progressColor(watching: Boolean, onAir: Color, viewer: Color): Color = if (watching) viewer else onAir

@Composable
internal fun progressColor(watching: Boolean): Color =
    progressColor(watching, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
