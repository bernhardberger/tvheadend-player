package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlaybackRecoveryInitialAction
import at.bernhardberger.tvhplayer.core.PlaybackRecoverySurface
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerAutoHideContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerSeekPreviewPhase
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.data.RecordingPlaybackAvailability
import at.bernhardberger.tvhplayer.data.RecordingPlaybackIntent
import at.bernhardberger.tvhplayer.core.RecordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.seekStepMs
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerControlsAutoHideEligible
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.core.playerParentConsumesRecoveryKey
import at.bernhardberger.tvhplayer.core.playbackRecoveryUiModel
import at.bernhardberger.tvhplayer.core.recordingKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.data.recordingPlaybackAvailability
import at.bernhardberger.tvhplayer.core.recordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.recordingPlaybackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.recordingSeekFeedbackSettled
import at.bernhardberger.tvhplayer.core.recordingStackedSeekTarget
import at.bernhardberger.tvhplayer.data.ChannelEpgRuntime
import at.bernhardberger.tvhplayer.data.DvrRuntime
import at.bernhardberger.tvhplayer.data.Channel
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppRecordingProgressState
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.ui.components.KeepScreenOn
import at.bernhardberger.tvhplayer.ui.components.RecordingContentDetails
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
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

internal fun recordingDegradedEpisodeActive(
    currentlyActive: Boolean,
    syncState: AppRecordingProgressState,
): Boolean = when (syncState) {
    AppRecordingProgressState.DEGRADED -> true
    AppRecordingProgressState.SAVING -> currentlyActive
    AppRecordingProgressState.INACTIVE,
    AppRecordingProgressState.AVAILABLE,
    AppRecordingProgressState.READ_ONLY,
    AppRecordingProgressState.UNSUPPORTED -> false
}

@Composable
fun RecordingPlayerScreen(
    recordingId: Int,
    playbackIntent: RecordingPlaybackIntent = RecordingPlaybackIntent.DefaultPolicy,
    repository: DvrRuntime = koinInject(),
    channelRepository: ChannelEpgRuntime = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    session: AppPlaybackRuntime = koinInject(),
    settingsStore: PlayerSettingsStore = koinInject(),
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    connectionAvailable: Boolean,
    onReconnect: () -> Unit,
    onUnlock: () -> Unit = {},
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playbackState by session.state.collectAsStateWithLifecycle()
    val progressSyncState by session.recordingProgressState.collectAsStateWithLifecycle()
    val diagnostics by session.diagnostics.collectAsStateWithLifecycle()
    val settings by settingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(profile = "", audioLanguage = null, subtitleLanguage = null)
    )
    val entries by repository.entries.collectAsStateWithLifecycle()
    val entriesReady by repository.entriesReady.collectAsStateWithLifecycle()
    val channels by channelRepository.channels.collectAsStateWithLifecycle()
    val channelsById = remember(channels) { channels.associateBy(Channel::channelId) }
    val entry = entries.firstOrNull { it.id == recordingId }
    val recordingResolved = entry != null || entriesReady
    val recordingLoading = !recordingResolved && connectionAvailable
    val initialConnectionFailure = !recordingResolved && !connectionAvailable
    val availability = entry?.let(::recordingPlaybackAvailability)
    val ready = availability as? RecordingPlaybackAvailability.Ready
    val player = remember { session.player }
    val rootFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    val showStop = simpleTvProfile.allows(SimpleTvCapability.STOP)
    val showUnlock = simpleTvProfile.active
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(C.TIME_UNSET) }
    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1_000L) }
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var seekFeedbackToken by remember { mutableIntStateOf(0) }
    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekOriginMs by remember { mutableStateOf<Long?>(null) }
    var pendingSeekDispatched by remember { mutableStateOf(false) }
    var optionsPage by remember { mutableStateOf<PlaybackOptionsPage?>(null) }
    var restoreOptionsFocus by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf(settings.aspectRatio) }
    var revealingKeyCode by remember { mutableStateOf<Int?>(null) }
    var degradedEpisodeActive by remember { mutableStateOf(false) }

    LaunchedEffect(settings.aspectRatio) {
        aspectRatio = settings.aspectRatio
    }

    LaunchedEffect(progressSyncState) {
        degradedEpisodeActive = recordingDegradedEpisodeActive(
            currentlyActive = degradedEpisodeActive,
            syncState = progressSyncState,
        )
    }

    DisposableEffect(statsVisible) {
        session.setDiagnosticsEnabled(statsVisible)
        onDispose {
            if (statsVisible) session.setDiagnosticsEnabled(false)
        }
    }

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

    fun togglePlayPause() {
        if (player.isPlaying) {
            session.pause()
            session.recordingPaused()
        } else {
            session.play()
        }
    }

    fun pausePlayback() {
        session.pause()
        session.recordingPaused()
    }

    fun seekBy(deltaMs: Long) {
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        if (pendingSeekTargetMs == null) pendingSeekOriginMs = currentPosition
        pendingSeekDispatched = false
        val target = recordingStackedSeekTarget(
            currentMs = currentPosition,
            pendingTargetMs = pendingSeekTargetMs,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L },
            deltaMs = deltaMs,
        )
        pendingSeekTargetMs = target
        positionMs = target
        seekFeedbackToken++
    }

    fun applyKeyAction(
        action: RecordingPlaybackKeyAction,
        repeatCount: Int = 0,
    ): Boolean = when (action) {
        RecordingPlaybackKeyAction.PASS_THROUGH -> false
        RecordingPlaybackKeyAction.REVEAL_CONTROLS -> {
            showControls()
            true
        }
        RecordingPlaybackKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
            togglePlayPause()
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
        RecordingPlaybackKeyAction.OPEN_INFO -> {
            controlsVisible = false
            infoOpen = true
            true
        }
        RecordingPlaybackKeyAction.SEEK_BACK -> {
            seekBy(-seekStepMs(repeatCount))
            true
        }
        RecordingPlaybackKeyAction.SEEK_FORWARD -> {
            seekBy(seekStepMs(repeatCount))
            true
        }
    }

    LaunchedEffect(recordingId, ready?.path, playbackIntent) {
        val playableEntry = entry ?: return@LaunchedEffect
        ready ?: return@LaunchedEffect
        if (
            session.activeRecordingId.value == recordingId &&
            playbackIntent == RecordingPlaybackIntent.DefaultPolicy &&
            playbackState !is AppPlaybackState.Idle &&
            playbackState !is AppPlaybackState.Failed
        ) {
            return@LaunchedEffect
        }
        session.playRecording(
            entry = playableEntry,
            intent = playbackIntent,
        )
    }

    LaunchedEffect(player, availability) {
        if (availability !is RecordingPlaybackAvailability.Ready) return@LaunchedEffect
        while (true) {
            positionMs = pendingSeekTargetMs ?: player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration
            isPlaying = player.isPlaying
            nowSec = System.currentTimeMillis() / 1_000L
            delay(250L)
        }
    }

    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = controlsVisible,
            playbackProgressing = isPlaying,
            playbackStable = availability is RecordingPlaybackAvailability.Ready &&
                playbackState is AppPlaybackState.Playing,
            seekPending = pendingSeekTargetMs != null,
            modalVisible = optionsPage != null || infoOpen,
            recoveryVisible = playbackState is AppPlaybackState.Recovering,
            actionableErrorVisible = initialConnectionFailure ||
                (recordingResolved &&
                    (availability !is RecordingPlaybackAvailability.Ready ||
                        playbackState is AppPlaybackState.Failed)),
        )
    )
    PlayerControlsAutoHideEffect(
        eligible = autoHideEligible,
        interactionToken = interactionToken,
        timeoutMillis = RECORDING_CONTROLS_AUTO_HIDE_MS,
        onHide = ::hideControls,
    )

    val seekPreviewPhase = when {
        controlsVisible || pendingSeekTargetMs == null -> PlayerSeekPreviewPhase.NONE
        pendingSeekDispatched -> PlayerSeekPreviewPhase.DISPATCHED
        else -> PlayerSeekPreviewPhase.PENDING
    }
    val foregroundLayer = playerForegroundLayer(
        PlayerForegroundContext(
            confirmationVisible = false,
            infoVisible = infoOpen && entry != null &&
                availability is RecordingPlaybackAvailability.Ready,
            optionsPage = optionsPage,
            numberEntryVisible = false,
            channelDrawerVisible = false,
            recoveryVisible = playbackState is AppPlaybackState.Recovering,
            terminalErrorVisible = initialConnectionFailure ||
                (recordingResolved &&
                    (availability !is RecordingPlaybackAvailability.Ready ||
                        playbackState is AppPlaybackState.Failed)),
            seekPreviewPhase = seekPreviewPhase,
            controlsVisible = controlsVisible &&
                availability is RecordingPlaybackAvailability.Ready,
            statsEnabled = statsVisible,
        )
    )
    val failureReason = (playbackState as? AppPlaybackState.Failed)?.reason
    val retryTargetAvailable =
        initialConnectionFailure ||
            (availability is RecordingPlaybackAvailability.Ready &&
                failureReason == AppPlaybackFailureReason.RECORDING_READ_FAILED)
    val recoveryUiModel = playbackRecoveryUiModel(
        surface = PlaybackRecoverySurface.RECORDING,
        connectionAvailable = connectionAvailable,
        retryTargetAvailable = retryTargetAvailable,
        simpleTvActive = false,
    )

    fun dispatchRecoveryRetry() {
        when (recoveryUiModel.retryCommand) {
            PlaybackRetryCommand.RECONNECT -> onReconnect()
            PlaybackRetryCommand.RESUME_RECORDING -> scope.launch { session.retryRecording() }
            PlaybackRetryCommand.RETRY_LIVE,
            PlaybackRetryCommand.NONE -> Unit
        }
    }

    val handlePlaybackBack: () -> Unit = {
        when (
            playerBackAction(
                surface = PlayerSurface.RECORDING,
                simpleTvActive = simpleTvProfile.active,
                foregroundLayer = foregroundLayer,
            )
        ) {
            PlayerBackAction.DISMISS_CONFIRMATION -> Unit
            PlayerBackAction.CLOSE_INFO -> infoOpen = false
            PlayerBackAction.RETURN_TO_OPTIONS_ROOT -> optionsPage = PlaybackOptionsPage.ROOT
            PlayerBackAction.CLOSE_OPTIONS -> {
                optionsPage = null
                restoreOptionsFocus = true
                interactionToken++
            }
            PlayerBackAction.CLEAR_NUMBER_ENTRY,
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> Unit
            PlayerBackAction.CLOSE_PLAYER -> onClose()
            PlayerBackAction.CANCEL_PENDING_SEEK,
            PlayerBackAction.DISMISS_SEEK_FEEDBACK -> {
                seekFeedbackToken++
                pendingSeekTargetMs = null
                pendingSeekOriginMs = null
                pendingSeekDispatched = false
            }
            PlayerBackAction.HIDE_CONTROLS -> hideControls()
            PlayerBackAction.HIDE_STATS -> statsVisible = false
            PlayerBackAction.CONSUME_WITHOUT_CHANGE -> Unit
        }
    }

    LaunchedEffect(controlsVisible, availability, playbackState) {
        if (
            !controlsVisible &&
            availability is RecordingPlaybackAvailability.Ready &&
            playbackState !is AppPlaybackState.Failed
        ) {
            rootFocus.requestFocus()
        }
    }

    LaunchedEffect(infoOpen) {
        if (infoOpen) runCatching { infoFocus.requestFocus() }
    }

    LaunchedEffect(seekFeedbackToken) {
        val targetMs = pendingSeekTargetMs ?: return@LaunchedEffect
        delay(RECORDING_SEEK_DEBOUNCE_MS)
        pendingSeekDispatched = true
        session.seekTo(targetMs)
        session.recordingSeekSettled()
        delay(RECORDING_SEEK_FEEDBACK_MIN_MS)
        while (
            !recordingSeekFeedbackSettled(
                playerReady = player.playbackState == Player.STATE_READY,
                playerEnded = player.playbackState == Player.STATE_ENDED,
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying,
                playbackFailed = session.state.value is AppPlaybackState.Failed,
            )
        ) {
            delay(RECORDING_SEEK_FEEDBACK_POLL_MS)
        }
        delay(RECORDING_SEEK_FEEDBACK_SETTLED_GRACE_MS)
        pendingSeekTargetMs = null
        pendingSeekOriginMs = null
        pendingSeekDispatched = false
    }

    val dispatchBack = rememberPlayerBackDispatcher(handlePlaybackBack)
    KeepScreenOn(
        enabled = availability is RecordingPlaybackAvailability.Ready &&
            playbackState !is AppPlaybackState.Failed,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (dispatchBack(event)) return@onPreviewKeyEvent true
                val keyCode = event.nativeKeyEvent.keyCode
                if (recordingPlaybackSuppressesRevealingKey(revealingKeyCode, keyCode)) {
                    if (event.type == KeyEventType.KeyUp) revealingKeyCode = null
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (
                    foregroundLayer == PlayerForegroundLayer.RECOVERY ||
                    foregroundLayer == PlayerForegroundLayer.TERMINAL_ERROR
                ) {
                    return@onPreviewKeyEvent playerParentConsumesRecoveryKey(keyCode)
                }

                if (infoOpen) return@onPreviewKeyEvent false
                if (optionsPage != null) return@onPreviewKeyEvent false

                val mediaAction = mediaPlaybackAction(
                    keyCode = keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                )
                if (mediaAction != MediaPlaybackAction.NONE) {
                    when (mediaAction) {
                        MediaPlaybackAction.PLAY -> session.play()
                        MediaPlaybackAction.PAUSE -> pausePlayback()
                        MediaPlaybackAction.TOGGLE -> togglePlayPause()
                        MediaPlaybackAction.NONE -> Unit
                    }
                    showControls()
                    return@onPreviewKeyEvent true
                }

                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-RECORDING_SHORT_SEEK_MS)
                        return@onPreviewKeyEvent true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(RECORDING_SHORT_SEEK_MS)
                        return@onPreviewKeyEvent true
                    }
                }

                val keyAction = recordingPlaybackKeyAction(
                    controlsVisible = controlsVisible,
                    keyCode = keyCode,
                    simpleTvActive = simpleTvProfile.active,
                )
                if (recordingKeyActionStartsOpeningCycle(keyAction)) {
                    revealingKeyCode = keyCode
                }
                applyKeyAction(
                    action = keyAction,
                    repeatCount = event.nativeKeyEvent.repeatCount,
                )
            }
            .focusRequester(rootFocus)
            .playerRootSemantics(stringResource(R.string.player_recording_surface))
            .focusable(),
    ) {
        if (availability is RecordingPlaybackAvailability.Ready) {
            PlayerControlsLayer(
                visible = foregroundLayer == PlayerForegroundLayer.CONTROLS,
                modalVisible = optionsPage != null,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RecordingOverlayControls(
                    imageLoader = imageLoader,
                    piconPath = channelsById[entry.channelId]?.icon,
                    title = entry.title,
                    subtitle = entry.subtitle,
                    channelName = entry.channelName,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    growing = availability.growing,
                    nowSec = nowSec,
                    isPlaying = isPlaying,
                    controlsVisible = controlsVisible,
                    optionsOpen = optionsPage != null,
                    onTogglePlayPause = ::togglePlayPause,
                    onSeek = ::seekBy,
                    onStopPlayback = ::stopAndClose,
                    onUserInteraction = { interactionToken++ },
                    showStop = showStop,
                    onOpenOptions = {
                        restoreOptionsFocus = false
                        optionsPage = PlaybackOptionsPage.ROOT
                        controlsVisible = true
                    },
                    onOpenInfo = {
                        controlsVisible = false
                        infoOpen = true
                    },
                    restoreOptionsFocus = restoreOptionsFocus,
                    onOptionsFocusRestored = { restoreOptionsFocus = false },
                )
            }

            if (foregroundLayer == PlayerForegroundLayer.INFO) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    colors = SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    shape = MaterialTheme.shapes.large,
                ) {
                    RecordingContentDetails(
                        entry = entry,
                        modifier = Modifier.padding(28.dp),
                        actions = {
                            Button(
                                onClick = { infoOpen = false },
                                modifier = Modifier.focusRequester(infoFocus),
                            ) {
                                Text(stringResource(R.string.player_info_close))
                            }
                        },
                    )
                }
            }

            if (foregroundLayer == PlayerForegroundLayer.STATS) {
                PlaybackStatsOverlay(
                    diagnostics = diagnostics,
                    aspectRatio = aspectRatio,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 36.dp, end = 48.dp),
                )
            }

            if (
                foregroundLayer == PlayerForegroundLayer.PENDING_SEEK_PREVIEW ||
                foregroundLayer == PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
            ) {
                RecordingSeekPreview(
                    targetMs = requireNotNull(pendingSeekTargetMs),
                    originMs = pendingSeekOriginMs,
                    durationMs = durationMs,
                    growing = availability.growing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (controlsVisible && pendingSeekTargetMs != null && pendingSeekOriginMs != null) {
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
                        text = formatPlaybackDelta(seekDeltaMs),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            if (
                degradedEpisodeActive &&
                playbackState !is AppPlaybackState.Failed
            ) {
                Text(
                    text = stringResource(R.string.recording_progress_save_failed),
                    color = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            val statusText = when {
                controlsVisible && progressSyncState == AppRecordingProgressState.DEGRADED ->
                    stringResource(R.string.recording_progress_save_failed)
                controlsVisible && progressSyncState == AppRecordingProgressState.READ_ONLY ->
                    stringResource(R.string.recording_progress_read_only)
                controlsVisible && progressSyncState == AppRecordingProgressState.UNSUPPORTED ->
                    stringResource(R.string.recording_progress_unsupported)
                else -> null
            }
            statusText?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .semantics {
                            if (
                                controlsVisible &&
                                progressSyncState == AppRecordingProgressState.DEGRADED
                            ) {
                                hideFromAccessibility()
                            }
                        },
                    colors = SurfaceDefaults.colors(
                        containerColor = Color.Black.copy(alpha = 0.78f),
                        contentColor = Color.White,
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
            }
        }
        TvRecoveryOverlay(
            visible = recordingLoading,
            message = stringResource(R.string.recording_loading),
            opaque = false,
        )
        TvRecoveryOverlay(
            visible = foregroundLayer == PlayerForegroundLayer.RECOVERY,
            message = stringResource(R.string.recording_recovering),
            detail = entry?.title,
            opaque = false,
            primaryActionLabel = stringResource(R.string.close),
            onPrimaryAction = onClose,
        )
        TvRecoveryOverlay(
            visible = foregroundLayer == PlayerForegroundLayer.TERMINAL_ERROR,
            message = stringResource(
                if (failureReason == AppPlaybackFailureReason.RECORDING_READ_FAILED) {
                    R.string.recording_playback_interrupted_title
                } else {
                    R.string.recording_unavailable_title
                }
            ),
            detail = entry?.title,
            hint = stringResource(
                when {
                    failureReason == AppPlaybackFailureReason.RECORDING_READ_FAILED ->
                        R.string.recording_read_failed
                    initialConnectionFailure -> R.string.recording_connection_unavailable
                    availability == RecordingPlaybackAvailability.NotReady ->
                        R.string.recording_not_ready
                    else -> R.string.recording_file_unavailable
                }
            ),
            opaque = false,
            primaryActionLabel = stringResource(
                if (
                    recoveryUiModel.initialAction == PlaybackRecoveryInitialAction.RETRY
                ) {
                    R.string.retry
                } else {
                    R.string.close
                }
            ),
            onPrimaryAction = if (
                recoveryUiModel.initialAction == PlaybackRecoveryInitialAction.RETRY
            ) {
                ::dispatchRecoveryRetry
            } else {
                onClose
            },
            secondaryActionLabel = if (
                recoveryUiModel.initialAction == PlaybackRecoveryInitialAction.RETRY
            ) {
                stringResource(R.string.close)
            } else {
                null
            },
            onSecondaryAction = if (
                recoveryUiModel.initialAction == PlaybackRecoveryInitialAction.RETRY
            ) {
                onClose
            } else {
                null
            },
            liveRegionMode = LiveRegionMode.Assertive,
        )
        optionsPage?.let { page ->
            PlaybackOptionsSheet(
                page = page,
                player = player,
                tracksResolving =
                    playbackState is AppPlaybackState.Starting ||
                        playbackState is AppPlaybackState.Recovering,
                aspectRatio = aspectRatio,
                statsVisible = statsVisible,
                showSimpleTvExit = showUnlock,
                simpleTvActive = simpleTvProfile.active,
                onPageChange = { optionsPage = it },
                onAspectRatioChange = { mode ->
                    aspectRatio = mode
                    scope.launch { settingsStore.setAspectRatio(mode) }
                },
                onStatsVisibleChange = { statsVisible = it },
                onSimpleTvExit = {
                    optionsPage = null
                    restoreOptionsFocus = true
                    onUnlock()
                },
            )
        }
    }
}

private fun Player.togglePlayPause() {
    if (isPlaying) pause() else play()
}
