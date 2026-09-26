package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
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
import at.bernhardberger.tvhplayer.core.PlaybackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.playbackOptionsKeyOutcome
import at.bernhardberger.tvhplayer.core.playbackOptionsKeyRequest
import at.bernhardberger.tvhplayer.core.recordingKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.recordingPlaybackKeyAction
import at.bernhardberger.tvhplayer.core.recordingPlaybackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.ui.components.RecordingContentDetails
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.ui.components.rememberPlaybackIntent
import coil3.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val RECORDING_SHORT_SEEK_MS = 30_000L
private const val RECORDING_LONG_SEEK_MS = 10 * 60_000L
private const val RECORDING_CONTROLS_AUTO_HIDE_MS = 5_000L

@OptIn(UnstableApi::class)
@Composable
fun RecordingPlayerScreen(
    recordingId: DvrEntryId,
    playbackStart: RecordingPlaybackStart = RecordingPlaybackStart.RESUME,
    tvheadendSession: TvheadendSession = koinInject(),
    imageLoader: ImageLoader = koinInject(),
    session: AppPlaybackRuntime = koinInject(),
    settingsStore: PlayerSettingsStore = koinInject(),
    connectionState: ConnectionState,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
) {
    val playerClose = rememberPlayerClose(onClose)
    // First, before the route restore: a user stop since the last intent closes the screen.
    val screenEntry = rememberPlayerEntry(session::enterPlayerScreen, playerClose)
    val connectionAvailable = connectionState is ConnectionState.Connected
    val scope = rememberCoroutineScope()
    val playbackState by session.state.collectAsStateWithLifecycle()
    val activeTarget by session.activeTarget.collectAsStateWithLifecycle()
    val videoPresentation by session.videoPresentation.collectAsStateWithLifecycle()
    val recordingSelection by session.recordingSelection.collectAsStateWithLifecycle()
    val recordingAdmission by session.recordingAdmission.collectAsStateWithLifecycle()
    val cutpoints by session.recordingCutpoints.collectAsStateWithLifecycle()
    val markerRevision by session.recordingMarkerRevision.collectAsStateWithLifecycle()
    val observation by tvheadendSession.observation.collectAsStateWithLifecycle()
    val currentSession = observation.currentSession
    val routeSelection = currentRecordingPlaybackSelection(observation, recordingId)
    if (screenEntry != null && routeSelection != null) {
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
        initialValue = PlayerSettings()
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
    val growing = admission is RecordingPlaybackAdmission.GrowingStartOverOnly &&
        currentRecordingIsGrowing(observation, retainedSelection)
    val recordingResolved = entry != null ||
        admission != null ||
        playbackState is AppPlaybackState.Failed
    val recordingLoading = !recordingResolved && connectionAvailable
    val initialConnectionFailure = !recordingResolved && !connectionAvailable
    val player = remember { session.player }
    val playWhenReady by rememberPlaybackIntent(player)
    val timelineState = androidx.compose.runtime.key(recordingId, retainedSelection?.currentSession) {
        rememberRecordingTimelinePresentationState(
            player = player,
            session = session,
            playbackAvailable = playbackAvailable,
            growing = growing,
        )
    }
    val positionMs = timelineState.positionMs
    val durationMs = timelineState.durationMs
    val displayDurationMs = timelineState.displayDurationMs
    val markers = remember(cutpoints, durationMs, timelineState.canSeek, retainedSelection, currentSession) {
        if (timelineState.canSeek && retainedSelection != null && retainedSelection.currentSession === currentSession) {
            at.bernhardberger.tvhplayer.core.recordingMarkerPositions(cutpoints, durationMs)
        } else emptyList()
    }
    val markerNavigation = remember { RecordingMarkerNavigation() }
    LaunchedEffect(markerRevision, recordingId, currentSession) { markerNavigation.dismiss() }
    fun seekMarker(targetMs: Long) {
        timelineState.cancelPendingSeek()
        session.seekRecordingMarker(targetMs, markerNavigation.ownerRevision)
    }
    val nowSec = timelineState.nowEpochSec
    val isPlaying = timelineState.isPlaying
    val rootFocus = remember { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionToken by remember { mutableIntStateOf(0) }
    var optionsPage by remember { mutableStateOf<PlaybackOptionsPage?>(null) }
    var optionsQuickList by remember { mutableStateOf(false) }
    val quickList = remember { PlaybackQuickListSignals() }
    var restoreOptionsFocus by remember { mutableStateOf(false) }
    var lastFocusedControl by remember { mutableStateOf("player-pause") }
    var quickListReturnControl by remember { mutableStateOf<String?>(null) }
    var restoreQuickListControl by remember { mutableStateOf<String?>(null) }
    var restoreInfoActionFocus by remember { mutableStateOf(false) }
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
                closePlayer = playerClose::close,
            )
        }
    }
    CloseOnSessionStop(session.sessionStops, playerClose)

    fun showControls() {
        controlsVisible = true
        interactionToken++
    }

    fun hideControls() {
        controlsVisible = false
    }

    fun togglePlayPause() {
        if (player.playWhenReady) {
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

    fun openOptionsFromKey(keyCode: Int): Boolean {
        val outcome = playbackOptionsKeyOutcome(
            keyCode = keyCode,
            quickListPage = optionsPage.takeIf { optionsQuickList },
        ) ?: return false
        if (timelineState.seekPending) timelineState.commitPendingSeek()
        infoOpen = false
        restoreOptionsFocus = false
        when (outcome) {
            PlaybackOptionsKeyOutcome.OpenMenu -> {
                optionsQuickList = false
                optionsPage = PlaybackOptionsPage.ROOT
                controlsVisible = true
            }
            PlaybackOptionsKeyOutcome.MoveDown -> quickList.moveDown()
            // The short list leaves the controls as they are underneath.
            is PlaybackOptionsKeyOutcome.OpenQuickList -> {
                if (!optionsQuickList) quickListReturnControl = lastFocusedControl.takeIf { controlsVisible }
                optionsQuickList = true
                optionsPage = outcome.page
            }
        }
        return true
    }

    fun closeQuickList() {
        restoreQuickListControl = quickListReturnControl.takeIf { controlsVisible }
        quickListReturnControl = null
        optionsPage = null
        optionsQuickList = false
        interactionToken++
    }

    fun applyKeyAction(
        action: RecordingPlaybackKeyAction,
        keyCode: Int,
        repeatCount: Int = 0,
    ): Boolean = when (action) {
        RecordingPlaybackKeyAction.PASS_THROUGH -> false
        RecordingPlaybackKeyAction.REVEAL_CONTROLS -> {
            if (timelineState.seekPending) {
                timelineState.commitPendingSeek()
                restoreInfoActionFocus = true
            }
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
            playerClose.close()
            true
        }
        RecordingPlaybackKeyAction.OPEN_INFO -> {
            controlsVisible = false
            infoOpen = true
            true
        }
        RecordingPlaybackKeyAction.OPEN_OPTIONS -> openOptionsFromKey(keyCode)
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
            modalVisible = optionsPage != null || infoOpen || markerNavigation.open,
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
            optionsQuickList = optionsQuickList && optionsPage != null,
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
    LaunchedEffect(foregroundLayer) {
        if (foregroundLayer != PlayerForegroundLayer.CONTROLS) markerNavigation.dismiss()
    }
    val failureReason = (playbackState as? AppPlaybackState.Failed)?.reason
    val retryTargetAvailable =
        initialConnectionFailure ||
            (playbackAvailable && retainedSelection != null &&
                failureReason == AppPlaybackFailureReason.RECORDING_READ_FAILED)
    val recoveryUiModel = playbackRecoveryUiModel(
        surface = PlaybackRecoverySurface.RECORDING,
        connectionState = connectionState,
        retryTargetAvailable = retryTargetAvailable,
    )

    fun dispatchRecoveryRetry() {
        when (recoveryUiModel.retryCommand) {
            PlaybackRetryCommand.RECONNECT -> onReconnect()
            PlaybackRetryCommand.RESUME_RECORDING -> scope.launch { session.retryRecording() }
            PlaybackRetryCommand.RETRY_LIVE,
            PlaybackRetryCommand.NONE -> Unit
        }
    }

    fun closeInfo() {
        infoOpen = false
        restoreInfoActionFocus = true
        showControls()
    }

    val handlePlaybackBack: () -> Unit = {
        if (markerNavigation.open) {
            markerNavigation.dismiss()
            interactionToken++
        } else when (
            playerBackAction(
                seekPreviewPhase = timelineState.seekPreviewPhase(controlsVisible),
                surface = PlayerSurface.RECORDING,
                foregroundLayer = playerForegroundLayer(currentPlayerForegroundContext()),
            )
        ) {
            PlayerBackAction.DISMISS_CONFIRMATION -> Unit
            PlayerBackAction.CLOSE_INFO -> closeInfo()
            PlayerBackAction.RESTORE_AND_CLOSE_QUICK_LIST -> {
                quickList.restoreStart()
                closeQuickList()
            }
            PlayerBackAction.RETURN_TO_OPTIONS_ROOT -> optionsPage = PlaybackOptionsPage.ROOT
            PlayerBackAction.CLOSE_OPTIONS -> {
                optionsPage = null
                optionsQuickList = false
                restoreOptionsFocus = true
                interactionToken++
            }
            PlayerBackAction.CLEAR_NUMBER_ENTRY,
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> Unit
            PlayerBackAction.CLOSE_PLAYER -> playerClose.close()
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            PlayerBackAction.HIDE_CONTROLS -> hideControls()
            PlayerBackAction.HIDE_STATS -> statsVisible = false
        }
    }

    PlayerRootFocusEffect(foregroundLayer, rootFocus)

    LaunchedEffect(infoOpen) {
        if (infoOpen) runCatching { infoFocus.requestFocus() }
    }

    PlayerBackHandler(handlePlaybackBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (event.type == KeyEventType.KeyDown && optionsQuickList && optionsPage != null) {
                    quickList.onKeyDown()
                }
                if (markerNavigation.handle(event, markers, ::seekMarker)) {
                    interactionToken++
                    return@onPreviewKeyEvent true
                }
                if (recordingPlaybackSuppressesRevealingKey(revealingKeyCode, keyCode)) {
                    if (event.type == KeyEventType.KeyUp) revealingKeyCode = null
                    return@onPreviewKeyEvent true
                }
                if (handlePlayerStopKeyWithoutTarget(
                        event = event,
                        hasActiveTarget = session.activeTarget.value != null,
                        beginKeyCycle = { revealingKeyCode = it },
                        stopAndClose = ::stopAndClose,
                    )
                ) {
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                    // See VideoPlayerScreen: a focused Compose target swallows the Back cycle on
                    // the TV before the BackHandler can fire, so Back is decided here.
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        revealingKeyCode = keyCode
                        handlePlaybackBack()
                    }
                    return@onPreviewKeyEvent true
                }
                if (
                    foregroundLayer == PlayerForegroundLayer.RECOVERY ||
                    foregroundLayer == PlayerForegroundLayer.TERMINAL_ERROR
                ) {
                    return@onPreviewKeyEvent playerParentConsumesRecoveryKey(keyCode)
                }

                if (playbackOptionsKeyRequest(keyCode) != null) {
                    // Menu, audio-track and captions keys reach the options from Info or
                    // another options page as well as from plain playback.
                    val optionsKeyAction = recordingPlaybackKeyAction(
                        controlsVisible = controlsVisible,
                        keyCode = keyCode,
                    )
                    if (recordingKeyActionStartsOpeningCycle(optionsKeyAction)) {
                        revealingKeyCode = keyCode
                    }
                    return@onPreviewKeyEvent applyKeyAction(optionsKeyAction, keyCode)
                }

                if (infoOpen || optionsPage != null) {
                    if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT && foregroundLayer != PlayerForegroundLayer.CONFIRMATION) {
                        revealingKeyCode = keyCode
                        handlePlaybackBack()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                val mediaAction = mediaPlaybackAction(
                    keyCode = keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    repeatCount = event.nativeKeyEvent.repeatCount,
                )
                if (mediaAction != MediaPlaybackAction.NONE) {
                    revealingKeyCode = keyCode
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
                )
                if (recordingKeyActionStartsOpeningCycle(keyAction)) {
                    revealingKeyCode = keyCode
                }
                applyKeyAction(
                    action = keyAction,
                    keyCode = keyCode,
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
                modalVisible = optionsPage != null || infoOpen,
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
                    displayDurationMs = displayDurationMs,
                    markers = markers,
                    markerPositionMs = timelineState.pendingTargetMs ?: positionMs,
                    markerNavigation = markerNavigation,
                    markerRevision = markerRevision,
                    onSeekMarker = ::seekMarker,
                    growing = growing,
                    nowSec = nowSec,
                    canSeek = timelineState.canSeek,
                    paused = !playWhenReady,
                    playbackPresented = playbackState is AppPlaybackState.Playing || playbackState is AppPlaybackState.Buffering,
                    previewing = timelineState.pendingTargetMs != null,
                    controlsVisible = controlsVisible,
                    optionsOpen = optionsPage != null,
                    onTogglePlayPause = ::togglePlayPause,
                    onSeek = ::seekBy,
                    onStopPlayback = ::stopAndClose,
                    onUserInteraction = { interactionToken++ },
                    onOpenOptions = {
                        restoreOptionsFocus = false
                        optionsQuickList = false
                        optionsPage = PlaybackOptionsPage.ROOT
                        controlsVisible = true
                    },
                    onOpenInfo = {
                        controlsVisible = false
                        infoOpen = true
                    },
                    restoreOptionsFocus = restoreOptionsFocus,
                    restoreQuickListControl = restoreQuickListControl,
                    onQuickListFocusRestored = { restoreQuickListControl = null },
                    onControlFocused = { lastFocusedControl = it },
                    restoreInfoFocus = restoreInfoActionFocus,
                    onInfoFocusRestored = { restoreInfoActionFocus = false },
                    onCommitSeek = timelineState::commitPendingSeek,
                    onOptionsFocusRestored = { restoreOptionsFocus = false },
                )
            }

            PlayerPanelVisibility(Unit.takeIf { foregroundLayer == PlayerForegroundLayer.INFO }) {
                PlaybackOptionsOverlayFrame(
                    paneTitle = stringResource(R.string.player_info),
                    panelTag = "recording-info-panel",
                    panelWidth = PlaybackInfoPanelWidth,
                ) {
                    PlayerInfoReadingContent(
                        title = entry.title.orEmpty(),
                        subtitle = listOfNotNull(entry.subtitle, entry.channelName).joinToString(" / "),
                        body = entry.summary?.takeIf(String::isNotBlank) ?: entry.description,
                        readingFocus = infoFocus,
                        modifier = Modifier.padding(PlaybackInfoPanelPadding),
                        footer = {
                            androidx.tv.material3.OutlinedButton(
                                onClick = ::closeInfo,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(R.string.player_info_close))
                            }
                        },
                    )
                }
            }

            PlaybackStatsVisibility(
                visible = foregroundLayer == PlayerForegroundLayer.STATS,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 48.dp),
            ) {
                PlaybackStatsOverlay(
                    diagnostics = diagnostics,
                    aspectRatio = aspectRatio,
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
                    displayDurationMs = displayDurationMs,
                    growing = growing,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

        }
        CompactBufferingStatus(
            state = playbackState,
            playWhenReady = playWhenReady,
            target = activeTarget,
            expectedTarget = AppPlaybackTarget.Recording(recordingId),
            generation = retainedSelection,
            screenActive = playbackAvailable && retainedSelection != null &&
                retainedSelection.currentSession === currentSession,
            foregroundBlocked = recordingLoading || foregroundContext.recoveryVisible ||
                foregroundContext.terminalErrorVisible,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        )
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
            onPrimaryAction = playerClose::close,
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
                playerClose::close
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
                playerClose::close
            } else {
                null
            },
            liveRegionMode = LiveRegionMode.Assertive,
        )
        PlayerPanelVisibility(
            value = optionsPage?.let { it to optionsQuickList },
            closesAtOnce = { (_, quick) -> quick },
        ) { (page, _) ->
            val audioAutomatic by session.audioAutomatic.collectAsStateWithLifecycle()
            PlaybackOptionsSheet(
                page = page,
                player = player,
                audioAutomatic = audioAutomatic,
                onAutomaticAudio = { session.useAutomaticAudio() },
                tracksResolving =
                    playbackState is AppPlaybackState.Starting ||
                        playbackState is AppPlaybackState.Recovering,
                aspectRatio = aspectRatio,
                statsVisible = statsVisible,
                onPageChange = {
                    optionsQuickList = false
                    optionsPage = it
                },
                quickList = quickList.takeIf { optionsQuickList },
                onQuickListClose = ::closeQuickList,
                quickListTargetEpoch = videoPresentation.epoch,
                quickListAvailable = playbackAvailable && activeTarget == AppPlaybackTarget.Recording(recordingId),
                isQuickListTargetCurrent = session::isQuickListTargetCurrent,
                onQuickAudioSelection = { epoch, override -> session.selectQuickListAudio(epoch, override) },
                recording = true,
                onAspectRatioChange = { mode ->
                    aspectRatio = mode
                    scope.launch { settingsStore.setAspectRatio(mode) }
                },
                onStatsVisibleChange = { statsVisible = it },
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
