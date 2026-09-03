package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.PlaybackRecoveryInitialAction
import at.bernhardberger.tvhplayer.core.PlaybackRecoverySecondaryAction
import at.bernhardberger.tvhplayer.core.PlaybackRecoverySurface
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerAutoHideContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.RecordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.seekStepMs
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerControlsAutoHideEligible
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.core.playerParentConsumesRecoveryKey
import at.bernhardberger.tvhplayer.core.playbackRecoveryUiModel
import at.bernhardberger.tvhplayer.core.recordingKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.recordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.recordingPlaybackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.core.formatPlaybackDelta
import at.bernhardberger.tvhplayer.ui.components.RecordingContentDetails
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import coil3.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val RECORDING_SHORT_SEEK_MS = 30_000L
private const val RECORDING_LONG_SEEK_MS = 10 * 60_000L
private const val RECORDING_CONTROLS_AUTO_HIDE_MS = 5_000L

@Composable
fun RecordingPlayerScreen(
    recordingId: DvrEntryId,
    playbackStart: RecordingPlaybackStart = RecordingPlaybackStart.RESUME,
    tvheadendSession: TvheadendSession = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    session: AppPlaybackRuntime = koinInject(),
    settingsStore: PlayerSettingsStore = koinInject(),
    showStop: Boolean = true,
    showSimpleTvExit: Boolean = false,
    playerCloseAllowed: Boolean = true,
    fullPlaybackOptionsAvailable: Boolean = true,
    connectionAvailable: Boolean,
    onReconnect: () -> Unit,
    onUnlock: () -> Unit = {},
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val playbackState by session.state.collectAsStateWithLifecycle()
    val recordingSelection by session.recordingSelection.collectAsStateWithLifecycle()
    val recordingAdmission by session.recordingAdmission.collectAsStateWithLifecycle()
    val observation by tvheadendSession.observation.collectAsStateWithLifecycle()
    val currentSession = observation.currentSession
    val routeSelection = currentRecordingPlaybackSelection(observation, recordingId)
    if (routeSelection != null) {
        RecordingPlaybackRouteRestorationEffect(
            recordingId = recordingId,
            playbackStart = playbackStart,
            generationAuthority = routeSelection.currentSession,
            restorePlayback = {
                session.restoreRecordingRoute(routeSelection, playbackStart)
            },
        )
    }
    val diagnostics by session.diagnostics.collectAsStateWithLifecycle()
    val settings by settingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(audioLanguage = null, subtitleLanguage = null)
    )
    val retainedSelection = recordingSelection?.takeIf { it.recordingId == recordingId }
    var targetObservation by remember(recordingId, retainedSelection?.currentSession) {
        mutableStateOf(
            observation.takeIf {
                retainedSelection != null &&
                    it.currentSession === retainedSelection.currentSession
            }
        )
    }
    LaunchedEffect(observation, retainedSelection) {
        if (
            targetObservation == null &&
            retainedSelection != null &&
            observation.currentSession === retainedSelection.currentSession
        ) {
            targetObservation = observation
        }
    }
    val entry = targetObservation?.dvrEntry(recordingId)
    val admission = recordingAdmission.takeIf { retainedSelection != null }
    val playbackAvailable = entry != null && when (admission) {
        is RecordingPlaybackAdmission.Completed,
        is RecordingPlaybackAdmission.GrowingStartOverOnly -> true
        RecordingPlaybackAdmission.GrowingDeferred,
        RecordingPlaybackAdmission.ObservationExpired,
        RecordingPlaybackAdmission.TargetUnavailable,
        null -> false
    }
    val growing = admission is RecordingPlaybackAdmission.GrowingStartOverOnly
    val recordingResolved = entry != null ||
        admission != null ||
        playbackState is AppPlaybackState.Failed
    val recordingLoading = !recordingResolved && connectionAvailable
    val initialConnectionFailure = !recordingResolved && !connectionAvailable
    val player = remember { session.player }
    val timelineState = rememberRecordingTimelinePresentationState(
        player = player,
        session = session,
        playbackAvailable = playbackAvailable,
    )
    val positionMs = timelineState.positionMs
    val durationMs = timelineState.durationMs
    val nowSec = timelineState.nowEpochSec
    val isPlaying = timelineState.isPlaying
    val rootFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var optionsPage by remember { mutableStateOf<PlaybackOptionsPage?>(null) }
    var restoreOptionsFocus by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf(settings.aspectRatio) }
    var revealingKeyCode by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(settings.aspectRatio) {
        aspectRatio = settings.aspectRatio
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
        } else {
            session.play()
        }
    }

    fun pausePlayback() {
        session.pause()
    }

    fun seekBy(deltaMs: Long) {
        timelineState.queueSeek(deltaMs)
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

    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = controlsVisible,
            playbackProgressing = isPlaying,
            playbackStable = playbackAvailable &&
                playbackState is AppPlaybackState.Playing,
            seekPending = timelineState.seekPending,
            modalVisible = optionsPage != null || infoOpen,
            recoveryVisible = playbackState is AppPlaybackState.Recovering,
            actionableErrorVisible = initialConnectionFailure ||
                (recordingResolved &&
                    (!playbackAvailable ||
                        playbackState is AppPlaybackState.Failed)),
        )
    )
    PlayerControlsAutoHideEffect(
        eligible = autoHideEligible,
        interactionToken = interactionToken,
        timeoutMillis = RECORDING_CONTROLS_AUTO_HIDE_MS,
        onHide = ::hideControls,
    )

    fun currentPlayerForegroundContext() =
        PlayerForegroundContext(
            confirmationVisible = false,
            infoVisible = infoOpen && entry != null && playbackAvailable,
            optionsPage = optionsPage,
            numberEntryVisible = false,
            channelDrawerVisible = false,
            recoveryVisible = playbackState is AppPlaybackState.Recovering,
            terminalErrorVisible = initialConnectionFailure ||
                (recordingResolved &&
                    (!playbackAvailable ||
                        playbackState is AppPlaybackState.Failed)),
            seekPreviewPhase = timelineState.seekPreviewPhase(controlsVisible),
            controlsVisible = controlsVisible && playbackAvailable,
            statsEnabled = statsVisible,
        )
    val foregroundContext = currentPlayerForegroundContext()
    val seekPreviewPhase = foregroundContext.seekPreviewPhase
    val foregroundLayer = playerForegroundLayer(foregroundContext)
    val failureReason = (playbackState as? AppPlaybackState.Failed)?.reason
    val retryTargetAvailable =
        initialConnectionFailure ||
            (playbackAvailable && retainedSelection != null &&
                failureReason == AppPlaybackFailureReason.RECORDING_READ_FAILED)
    val recoveryUiModel = playbackRecoveryUiModel(
        surface = PlaybackRecoverySurface.RECORDING,
        connectionAvailable = connectionAvailable,
        retryTargetAvailable = retryTargetAvailable,
        secondaryAction = PlaybackRecoverySecondaryAction.CLOSE,
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
                playerCloseAllowed = playerCloseAllowed,
                foregroundLayer = playerForegroundLayer(currentPlayerForegroundContext()),
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
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            PlayerBackAction.HIDE_CONTROLS -> hideControls()
            PlayerBackAction.HIDE_STATS -> statsVisible = false
            PlayerBackAction.CONSUME_WITHOUT_CHANGE -> Unit
        }
    }

    LaunchedEffect(controlsVisible, playbackAvailable, playbackState) {
        if (
            !controlsVisible &&
            playbackAvailable &&
            playbackState !is AppPlaybackState.Failed
        ) {
            rootFocus.requestFocus()
        }
    }

    LaunchedEffect(infoOpen) {
        if (infoOpen) runCatching { infoFocus.requestFocus() }
    }

    PlayerBackHandler(handlePlaybackBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
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
                    playerCloseAllowed = playerCloseAllowed,
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
        if (playbackAvailable) {
            val entry = requireNotNull(entry)
            PlayerControlsLayer(
                visible = foregroundLayer == PlayerForegroundLayer.CONTROLS,
                modalVisible = optionsPage != null,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RecordingOverlayControls(
                    imageLoader = imageLoader,
                    currentSession = retainedSelection?.currentSession,
                    piconPath = entry.channelId?.let { targetObservation?.channel(it)?.icon },
                    title = entry.title.orEmpty(),
                    subtitle = entry.subtitle,
                    channelName = entry.channelName,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    growing = growing,
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
                    targetMs = requireNotNull(timelineState.pendingTargetMs),
                    originMs = timelineState.pendingOriginMs,
                    durationMs = durationMs,
                    growing = growing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (
                controlsVisible &&
                timelineState.pendingTargetMs != null &&
                timelineState.pendingOriginMs != null
            ) {
                val seekDeltaMs = requireNotNull(timelineState.pendingTargetMs) -
                    requireNotNull(timelineState.pendingOriginMs)
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
                    admission == RecordingPlaybackAdmission.GrowingDeferred ->
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
                showSimpleTvExit = showSimpleTvExit,
                fullOptionsAvailable = fullPlaybackOptionsAvailable,
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

@Composable
internal fun RecordingPlaybackRouteRestorationEffect(
    recordingId: DvrEntryId,
    playbackStart: RecordingPlaybackStart,
    generationAuthority: CurrentSessionObservation,
    restorePlayback: suspend () -> Unit,
) {
    val latestRestorePlayback by rememberUpdatedState(restorePlayback)
    LaunchedEffect(recordingId, playbackStart, generationAuthority) {
        latestRestorePlayback()
    }
}

private fun Player.togglePlayPause() {
    if (isPlaying) pause() else play()
}
