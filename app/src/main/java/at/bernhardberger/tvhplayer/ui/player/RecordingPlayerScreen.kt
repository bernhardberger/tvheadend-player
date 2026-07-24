package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.RecordingPlaybackAvailability
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.recordingPlaybackAvailability
import at.bernhardberger.tvhplayer.core.recordingSeekTarget
import at.bernhardberger.tvhplayer.player.PlaybackFailureReason
import at.bernhardberger.tvhplayer.player.PlaybackSessionState
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.ui.common.formatHms
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val RECORDING_SEEK_STEP_MS = 30_000L

@Composable
fun RecordingPlayerScreen(
    recordingId: Int,
    repository: DvrRepository = koinInject(),
    session: PlayerSession = koinInject(),
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackState by session.state.collectAsStateWithLifecycle()
    val entries by repository.entries.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == recordingId }
    val availability = entry?.let(::recordingPlaybackAvailability)
        ?: RecordingPlaybackAvailability.FileUnavailable
    val ready = availability as? RecordingPlaybackAvailability.Ready
    val player = remember { session.getOrCreatePlayer(context) }
    val initialFocus = remember { FocusRequester() }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(C.TIME_UNSET) }
    var isPlaying by remember { androidx.compose.runtime.mutableStateOf(false) }

    fun close() {
        scope.launch {
            session.stop()
            onClose()
        }
    }

    LaunchedEffect(recordingId, ready?.path) {
        ready ?: return@LaunchedEffect
        session.playRecording(
            context = context,
            recordingId = recordingId,
            path = ready.path,
            knownSize = ready.size,
        )
    }

    LaunchedEffect(playbackState) {
        if (availability is RecordingPlaybackAvailability.Ready) {
            while (true) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration
                isPlaying = player.isPlaying
                delay(500L)
            }
        }
    }

    LaunchedEffect(Unit) { initialFocus.requestFocus() }
    DisposableEffect(recordingId) {
        onDispose { scope.launch { session.stop() } }
    }
    BackHandler(onBack = ::close)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val mediaAction = mediaPlaybackAction(
                    keyCode = event.nativeKeyEvent.keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                )
                if (mediaAction != MediaPlaybackAction.NONE) {
                    when (mediaAction) {
                        MediaPlaybackAction.PLAY -> player.play()
                        MediaPlaybackAction.PAUSE -> player.pause()
                        MediaPlaybackAction.TOGGLE -> player.togglePlayPause()
                        MediaPlaybackAction.NONE -> Unit
                    }
                    return@onPreviewKeyEvent true
                }
                when {
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        player.seekBy(-RECORDING_SEEK_STEP_MS)
                        true
                    }
                    event.nativeKeyEvent.keyCode ==
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        player.seekBy(RECORDING_SEEK_STEP_MS)
                        true
                    }
                    event.key == Key.Back -> {
                        close()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(bottomGradient)
                .padding(start = 56.dp, end = 56.dp, top = 120.dp, bottom = 38.dp),
        ) {
            Text(
                text = entry?.title ?: stringResource(R.string.recording_unavailable_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry?.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            Spacer(Modifier.height(12.dp))
            when {
                availability !is RecordingPlaybackAvailability.Ready -> {
                    Text(
                        text = stringResource(
                            if (availability == RecordingPlaybackAvailability.NotReady) {
                                R.string.recording_not_ready
                            } else {
                                R.string.recording_file_unavailable
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                playbackState is PlaybackSessionState.Failed -> {
                    val failure = (playbackState as PlaybackSessionState.Failed).reason
                    Text(
                        text = stringResource(
                            when (failure) {
                                PlaybackFailureReason.RECORDING_UNAVAILABLE ->
                                    R.string.recording_file_unavailable
                                PlaybackFailureReason.RECORDING_READ_FAILED ->
                                    R.string.recording_read_failed
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                availability.growing && durationMs == C.TIME_UNSET -> {
                    Text(
                        stringResource(R.string.recording_growing_playback),
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
                else -> {
                    Text(
                        text = "${formatHms(positionMs / 1000L)} / ${
                            if (durationMs == C.TIME_UNSET) {
                                stringResource(R.string.recording_duration_unknown)
                            } else {
                                formatHms(durationMs.coerceAtLeast(0L) / 1000L)
                            }
                        }",
                        color = Color.White.copy(alpha = 0.82f),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (availability is RecordingPlaybackAvailability.Ready) {
                    Button(
                        onClick = {
                            if (playbackState is PlaybackSessionState.Finished) {
                                player.seekTo(0L)
                                player.play()
                            } else {
                                player.togglePlayPause()
                            }
                        },
                        modifier = Modifier.focusRequester(initialFocus),
                    ) {
                        Text(
                            stringResource(
                                if (isPlaying) R.string.pause else R.string.play
                            )
                        )
                    }
                    OutlinedButton(onClick = { player.seekBy(-RECORDING_SEEK_STEP_MS) }) {
                        Text(stringResource(R.string.seek_back_30))
                    }
                    OutlinedButton(onClick = { player.seekBy(RECORDING_SEEK_STEP_MS) }) {
                        Text(stringResource(R.string.seek_forward_30))
                    }
                }
                OutlinedButton(
                    onClick = ::close,
                    modifier = if (availability !is RecordingPlaybackAvailability.Ready) {
                        Modifier.focusRequester(initialFocus)
                    } else {
                        Modifier
                    },
                ) {
                    Text(
                        stringResource(
                            if (availability is RecordingPlaybackAvailability.Ready) {
                                R.string.stop_playback
                            } else {
                                R.string.close
                            }
                        )
                    )
                }
            }
        }
    }
}

private fun Player.togglePlayPause() {
    if (isPlaying) pause() else play()
}

private fun Player.seekBy(deltaMs: Long) {
    seekTo(
        recordingSeekTarget(
            currentMs = currentPosition,
            durationMs = duration.takeIf { it != C.TIME_UNSET },
            deltaMs = deltaMs,
        )
    )
}
