package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.RecordingPlaybackAvailability
import at.bernhardberger.tvhplayer.core.RecordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.recordingPlaybackAvailability
import at.bernhardberger.tvhplayer.core.recordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.recordingSeekFeedbackSettled
import at.bernhardberger.tvhplayer.core.recordingStackedSeekTarget
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.player.PlaybackFailureReason
import at.bernhardberger.tvhplayer.player.PlaybackSessionState
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.repositories.TvhRepository
import at.bernhardberger.tvhplayer.ui.common.formatHms
import at.bernhardberger.tvhplayer.ui.components.KeepScreenOn
import coil3.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val RECORDING_SHORT_SEEK_MS = 30_000L
private const val RECORDING_LONG_SEEK_MS = 10 * 60_000L
private const val RECORDING_CONTROLS_AUTO_HIDE_MS = 5_000L
private const val RECORDING_SEEK_DEBOUNCE_MS = 400L
private const val RECORDING_SEEK_FEEDBACK_MIN_MS = 600L
private const val RECORDING_SEEK_FEEDBACK_POLL_MS = 100L
private const val RECORDING_SEEK_FEEDBACK_SETTLED_GRACE_MS = 350L

@Composable
fun RecordingPlayerScreen(
    recordingId: Int,
    repository: DvrRepository = koinInject(),
    channelRepository: TvhRepository = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    session: PlayerSession = koinInject(),
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    onUnlock: () -> Unit = {},
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playbackState by session.state.collectAsStateWithLifecycle()
    val entries by repository.entries.collectAsStateWithLifecycle()
    val channels by channelRepository.channelsUi.collectAsStateWithLifecycle()
    val channelsById = remember(channels) { channels.associateBy(ChannelUi::id) }
    val entry = entries.firstOrNull { it.id == recordingId }
    val availability = entry?.let(::recordingPlaybackAvailability)
        ?: RecordingPlaybackAvailability.FileUnavailable
    val ready = availability as? RecordingPlaybackAvailability.Ready
    val player = remember { session.getOrCreatePlayer(context) }
    val rootFocus = remember { FocusRequester() }
    val unavailableFocus = remember { FocusRequester() }
    val showStop = simpleTvProfile.allows(SimpleTvCapability.STOP)
    val showUnlock = simpleTvProfile.active
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(C.TIME_UNSET) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var seekFeedbackToken by remember { mutableIntStateOf(0) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekOriginMs by remember { mutableStateOf<Long?>(null) }

    fun stopAndClose() {
        scope.launch {
            stopPlaybackAndClose(
                stopPlayback = session::stop,
                closePlayer = onClose,
            )
        }
    }

    fun showControls() {
        controlsVisible = true
        interactionToken++
    }

    fun hideControls() {
        controlsVisible = false
    }

    fun seekBy(deltaMs: Long) {
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        if (pendingSeekTargetMs == null) pendingSeekOriginMs = currentPosition
        val target = recordingStackedSeekTarget(
            currentMs = currentPosition,
            pendingTargetMs = pendingSeekTargetMs,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET },
            deltaMs = deltaMs,
        )
        pendingSeekTargetMs = target
        positionMs = target
        seekFeedbackToken++
    }

    fun applyKeyAction(action: RecordingPlaybackKeyAction): Boolean = when (action) {
        RecordingPlaybackKeyAction.PASS_THROUGH -> false
        RecordingPlaybackKeyAction.REVEAL_CONTROLS -> {
            showControls()
            true
        }
        RecordingPlaybackKeyAction.HIDE_CONTROLS -> {
            hideControls()
            true
        }
        RecordingPlaybackKeyAction.CLOSE -> {
            onClose()
            true
        }
        RecordingPlaybackKeyAction.SEEK_BACK_SHORT -> {
            seekBy(-RECORDING_SHORT_SEEK_MS)
            true
        }
        RecordingPlaybackKeyAction.SEEK_FORWARD_SHORT -> {
            seekBy(RECORDING_SHORT_SEEK_MS)
            true
        }
        RecordingPlaybackKeyAction.SEEK_BACK_LONG -> {
            seekBy(-RECORDING_LONG_SEEK_MS)
            true
        }
        RecordingPlaybackKeyAction.SEEK_FORWARD_LONG -> {
            seekBy(RECORDING_LONG_SEEK_MS)
            true
        }
    }

    LaunchedEffect(recordingId, ready?.path) {
        ready ?: return@LaunchedEffect
        if (
            session.activeRecordingId.value == recordingId &&
            playbackState !is PlaybackSessionState.Idle &&
            playbackState !is PlaybackSessionState.Failed
        ) {
            return@LaunchedEffect
        }
        session.playRecording(
            context = context,
            recordingId = recordingId,
            path = ready.path,
            knownSize = ready.size,
        )
    }

    LaunchedEffect(player, availability) {
        if (availability !is RecordingPlaybackAvailability.Ready) return@LaunchedEffect
        while (true) {
            positionMs = pendingSeekTargetMs ?: player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration
            isPlaying = player.isPlaying
            delay(250L)
        }
    }

    LaunchedEffect(controlsVisible, interactionToken) {
        if (!controlsVisible || availability !is RecordingPlaybackAvailability.Ready) {
            return@LaunchedEffect
        }
        delay(RECORDING_CONTROLS_AUTO_HIDE_MS)
        hideControls()
    }

    LaunchedEffect(controlsVisible, availability) {
        if (!controlsVisible && availability is RecordingPlaybackAvailability.Ready) {
            rootFocus.requestFocus()
        } else if (availability !is RecordingPlaybackAvailability.Ready) {
            unavailableFocus.requestFocus()
        }
    }

    LaunchedEffect(seekFeedbackToken) {
        val targetMs = pendingSeekTargetMs ?: return@LaunchedEffect
        delay(RECORDING_SEEK_DEBOUNCE_MS)
        player.seekTo(targetMs)
        delay(RECORDING_SEEK_FEEDBACK_MIN_MS)
        while (
            !recordingSeekFeedbackSettled(
                playerReady = player.playbackState == Player.STATE_READY,
                playerEnded = player.playbackState == Player.STATE_ENDED,
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying,
                playbackFailed = session.state.value is PlaybackSessionState.Failed,
            )
        ) {
            delay(RECORDING_SEEK_FEEDBACK_POLL_MS)
        }
        delay(RECORDING_SEEK_FEEDBACK_SETTLED_GRACE_MS)
        pendingSeekTargetMs = null
        pendingSeekOriginMs = null
    }

    BackHandler {
        applyKeyAction(
            recordingPlaybackKeyAction(
                controlsVisible = controlsVisible,
                keyCode = AndroidKeyEvent.KEYCODE_BACK,
            )
        )
    }
    KeepScreenOn(enabled = availability is RecordingPlaybackAvailability.Ready)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
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
                    showControls()
                    return@onPreviewKeyEvent true
                }

                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-RECORDING_SHORT_SEEK_MS)
                        return@onPreviewKeyEvent true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(RECORDING_SHORT_SEEK_MS)
                        return@onPreviewKeyEvent true
                    }
                }

                applyKeyAction(
                    recordingPlaybackKeyAction(
                        controlsVisible = controlsVisible,
                        keyCode = event.nativeKeyEvent.keyCode,
                    )
                )
            },
    ) {
        if (availability is RecordingPlaybackAvailability.Ready) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RecordingOverlayControls(
                    player = player,
                    imageLoader = imageLoader,
                    piconPath = entry?.let { channelsById[it.channelId]?.icon },
                    title = entry?.title ?: stringResource(R.string.recording_unavailable_title),
                    subtitle = entry?.subtitle,
                    channelName = entry?.channelName,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    controlsVisible = controlsVisible,
                    onTogglePlayPause = {
                        if (playbackState is PlaybackSessionState.Finished) {
                            player.seekTo(0L)
                            player.play()
                        } else {
                            player.togglePlayPause()
                        }
                    },
                    onSeek = ::seekBy,
                    onStopPlayback = ::stopAndClose,
                    onUserInteraction = { interactionToken++ },
                    showStop = showStop,
                    showUnlock = showUnlock,
                    onUnlock = onUnlock,
                )
            }

            if (!controlsVisible && pendingSeekTargetMs != null) {
                RecordingSeekProgress(
                    positionMs = requireNotNull(pendingSeekTargetMs),
                    durationMs = durationMs,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (pendingSeekTargetMs != null && pendingSeekOriginMs != null) {
                val seekDeltaMs = requireNotNull(pendingSeekTargetMs) -
                    requireNotNull(pendingSeekOriginMs)
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.78f),
                        contentColor = Color.White,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = buildString {
                            append(if (seekDeltaMs >= 0L) "+" else "−")
                            append(formatHms(kotlin.math.abs(seekDeltaMs) / 1_000L))
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            val statusText = when {
                playbackState is PlaybackSessionState.Failed -> stringResource(
                    when ((playbackState as PlaybackSessionState.Failed).reason) {
                        PlaybackFailureReason.RECORDING_UNAVAILABLE ->
                            R.string.recording_file_unavailable
                        PlaybackFailureReason.RECORDING_READ_FAILED ->
                            R.string.recording_read_failed
                    }
                )
                availability.growing && durationMs == C.TIME_UNSET ->
                    stringResource(R.string.recording_growing_playback)
                else -> null
            }
            statusText?.let {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.78f),
                        contentColor = Color.White,
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(horizontal = 56.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = entry?.title ?: stringResource(R.string.recording_unavailable_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Text(
                    text = stringResource(
                        if (availability == RecordingPlaybackAvailability.NotReady) {
                            R.string.recording_not_ready
                        } else {
                            R.string.recording_file_unavailable
                        }
                    ),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onClose,
                        modifier = Modifier.focusRequester(unavailableFocus),
                    ) {
                        Text(stringResource(R.string.close))
                    }
                    if (showUnlock) {
                        Button(onClick = onUnlock) {
                            Text(stringResource(R.string.simple_tv_unlock))
                        }
                    }
                }
            }
        }
    }
}

private fun Player.togglePlayPause() {
    if (isPlaying) pause() else play()
}
