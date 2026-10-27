package at.bernhardberger.tvhplayer.ui.player
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key

import at.bernhardberger.tvhplayer.ui.components.PlaybackOptionsOverlayFrame
import at.bernhardberger.tvhplayer.ui.components.ProvideArtworkSession

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.OptIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.testTag
import at.bernhardberger.tvhplayer.core.playerStateCell
import at.bernhardberger.tvhplayer.core.recordingBarEnd
import at.bernhardberger.tvhplayer.ui.common.formatClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
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
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.PlaybackRetryCommand
import at.bernhardberger.tvhplayer.core.PlayerBackAction
import at.bernhardberger.tvhplayer.core.PlayerAutoHideContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundContext
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.playerKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.seekStepMs
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.playerBackAction
import at.bernhardberger.tvhplayer.core.playerControlsAutoHideEligible
import at.bernhardberger.tvhplayer.core.playerForegroundLayer
import at.bernhardberger.tvhplayer.core.recordingRetryCommand
import at.bernhardberger.tvhplayer.core.recordingPlayerAlert
import at.bernhardberger.tvhplayer.core.deviceFaultDetail
import at.bernhardberger.tvhplayer.playback.AppPlaybackFailureReason
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.ui.components.rememberPlaybackIntent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val RECORDING_SHORT_SEEK_MS = 30_000L
private const val RECORDING_LONG_SEEK_MS = 10 * 60_000L
private const val RECORDING_CONTROLS_AUTO_HIDE_MS = 5_000L

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalTvheadendApi::class)
@Composable
fun RecordingPlayerScreen(
    recordingId: DvrEntryId,
    playbackStart: RecordingPlaybackStart = RecordingPlaybackStart.RESUME,
    tvheadendSession: TvheadendSession = koinInject(),
    session: AppPlaybackRuntime = koinInject(),
    targetAspect: TargetAspect,
    connectionState: ConnectionState,
    onReconnect: () -> Unit,
    onClose: () -> Unit,
    onOpenConnectionSettings: () -> Unit = {},
    onShowGuide: (at.bernhardberger.tvheadend.sdk.core.ChannelId, at.bernhardberger.tvheadend.sdk.core.EpgEvent?) -> Unit = { _, _ -> },
) {
    val playerClose = rememberPlayerClose(onClose)
    // First, before the route restore: a user stop since the last intent closes the screen.
    val screenEntry = rememberPlayerEntry(session::enterPlayerScreen, playerClose)
    val connectionAvailable = connectionState is ConnectionState.Connected
    val scope = rememberCoroutineScope()
    val mutations = remember(tvheadendSession.dvrRepository) {
        at.bernhardberger.tvhplayer.ui.screens.DvrMutationActions(tvheadendSession.dvrRepository)
    }
    val notices: at.bernhardberger.tvhplayer.notices.NoticeCenter = koinInject()
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
        is RecordingPlaybackAdmission.Growing -> true
        RecordingPlaybackAdmission.GrowingDeferred,
        RecordingPlaybackAdmission.ObservationExpired,
        RecordingPlaybackAdmission.TargetUnavailable,
        null -> false
    }
    val growing = admission is RecordingPlaybackAdmission.Growing &&
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
    // The recording player has no layers of its own; a quick list before any focus returns to pause.
    val layers = rememberPlayerLayerState(RECORDING_CONTROLS_AUTO_HIDE_MS, initialFocusedControl = "player-pause")
    val chrome = layers.chrome
    val aspectRatio = targetAspect.mode
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, chrome) {
        val observer = LifecycleEventObserver { _, event ->
            // A Banner never outlives the stop, nor does its timer.
            if (event == Lifecycle.Event.ON_STOP) chrome.hideBanner()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val statsVisible = layers.statsVisible
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

    fun applyKeyAction(
        action: PlayerKeyAction,
        keyCode: Int,
        repeatCount: Int = 0,
    ): Boolean = when (action) {
        PlayerKeyAction.PASS_THROUGH,
        PlayerKeyAction.OPEN_CHANNELS -> false
        PlayerKeyAction.REVEAL_CONTROLS -> {
            if (timelineState.seekPending) {
                timelineState.commitPendingSeek()
            }
            layers.restoreInfoFocus = false
            chrome.showControls()
            true
        }
        PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
            togglePlayPause()
            // Pausing shows the Banner, not the controls, so Left/Right keep stepping.
            chrome.peekBanner()
            true
        }
        PlayerKeyAction.PEEK_BANNER -> {
            chrome.peekBanner()
            true
        }
        PlayerKeyAction.HIDE_CONTROLS -> {
            chrome.hideControls()
            true
        }
        PlayerKeyAction.CLOSE_PLAYER,
        PlayerKeyAction.DISMISS_OVERLAY_ONLY -> {
            playerClose.close()
            true
        }
        PlayerKeyAction.OPEN_INFO -> {
            layers.openInfo()
            true
        }
        // The options keys reach the layers before any other key action.
        PlayerKeyAction.OPEN_OPTIONS -> false
        PlayerKeyAction.SEEK_BACK -> {
            seekBy(-seekStepMs(repeatCount))
            // A quick step on the hidden player shows its preview in the Banner.
            chrome.peekBanner()
            true
        }
        PlayerKeyAction.SEEK_FORWARD -> {
            seekBy(seekStepMs(repeatCount))
            chrome.peekBanner()
            true
        }
    }

    fun recordingKeyContext() = PlayerKeyContext(
        surface = PlayerSurface.RECORDING,
        controlsVisible = chrome.controlsVisible,
        seekbarFocused = false,
        timeshiftAvailable = false,
    )

    fun recordingKeyAction(keyCode: Int): PlayerKeyAction = playerKeyAction(recordingKeyContext(), keyCode)

    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = chrome.controlsVisible,
            playbackProgressing = isPlaying,
            playbackStable = playbackAvailable &&
                playbackState is AppPlaybackState.Playing,
            seekPending = timelineState.seekPending,
            modalVisible = layers.optionsPage != null || layers.infoOpen || markerNavigation.open,
            recoveryVisible = playbackState is AppPlaybackState.Recovering,
            actionableErrorVisible = initialConnectionFailure ||
                (recordingResolved &&
                    (!playbackAvailable ||
                        playbackState is AppPlaybackState.Failed)),
        )
    )
    SideEffect {
        chrome.updateAutoHideEligibility(autoHideEligible)
    }

    fun currentPlayerForegroundContext() =
        PlayerForegroundContext(
            confirmationVisible = false,
            infoVisible = layers.infoOpen && entry != null && playbackAvailable,
            optionsPage = layers.optionsPage,
            optionsQuickList = layers.optionsQuickList && layers.optionsPage != null,
            numberEntryVisible = false,
            channelDrawerVisible = false,
            recoveryVisible = playbackState is AppPlaybackState.Recovering,
            terminalErrorVisible = initialConnectionFailure ||
                (recordingResolved &&
                    (!playbackAvailable ||
                        playbackState is AppPlaybackState.Failed)),
            seekPreviewPhase = timelineState.seekPreviewPhase(chrome.controlsVisible),
            controlsVisible = chrome.controlsVisible && playbackAvailable,
            statsEnabled = statsVisible,
        )
    val foregroundContext = currentPlayerForegroundContext()
    val seekPreviewPhase = foregroundContext.seekPreviewPhase
    val foregroundLayer = playerForegroundLayer(foregroundContext)
    // What the chrome draws. A quick step's preview plays inside the Banner and holds it up even
    // after its timer ran out; the Banner is drawn only with playback available.
    fun chromeMode(layer: PlayerForegroundLayer): PlayerChromeMode {
        val bannerStep = layer == PlayerForegroundLayer.PENDING_SEEK_PREVIEW ||
            layer == PlayerForegroundLayer.DISPATCHED_SEEK_PREVIEW
        return playerChromeMode(
            controls = layer == PlayerForegroundLayer.CONTROLS,
            banner = bannerStep || (chrome.bannerVisible && layer == PlayerForegroundLayer.NONE),
            stepPreview = bannerStep,
        )
    }
    // One playback state for the chrome's state cell and, hidden, the chip at its bottom-start place.
    val bufferingEligible = bufferingStatusEligible(
        playbackState, playWhenReady, activeTarget, AppPlaybackTarget.Recording(recordingId),
        playbackAvailable && retainedSelection != null && retainedSelection.currentSession === currentSession,
        foregroundBlocked = recordingLoading || foregroundContext.recoveryVisible || foregroundContext.terminalErrorVisible,
    )
    val bufferingVisible by rememberBusyVisible(
        AppPlaybackTarget.Recording(recordingId) to retainedSelection, bufferingEligible, PlayerBusyStatus.BUFFERING,
    )
    val recordedLengthMs = durationMs.takeIf { it != C.TIME_UNSET && it > 0L }
    val chromeState = playerStateCell(paused = !playWhenReady)
    val audioOnly = tracksAudioOnly(rememberPlayerTracks(player))
    // The Banner hides after the first frame of the loaded recording; a newly loading one keeps it.
    val bannerFramePresented = at.bernhardberger.tvhplayer.core.bannerFramePresented(
        tuneConfirmed = playbackAvailable &&
            activeTarget == AppPlaybackTarget.Recording(recordingId) &&
            retainedSelection != null && retainedSelection.currentSession === currentSession,
        videoFrameVisible = videoPresentation.visible,
        playing = playbackState is AppPlaybackState.Playing,
        audioOnly = audioOnly,
    )
    SideEffect {
        chrome.onBannerFramePresented(bannerFramePresented)
        chrome.holdBanner(!playWhenReady)
    }
    LaunchedEffect(foregroundLayer) {
        if (foregroundLayer != PlayerForegroundLayer.CONTROLS) markerNavigation.dismiss()
    }
    val failureReason = (playbackState as? AppPlaybackState.Failed)?.reason
    val retryTargetAvailable =
        initialConnectionFailure ||
            (playbackAvailable && retainedSelection != null &&
                failureReason == AppPlaybackFailureReason.RECORDING_READ_FAILED)
    val retryCommand = recordingRetryCommand(
        connectionState = connectionState,
        retryTargetAvailable = retryTargetAvailable,
    )

    fun dispatchRecoveryRetry() {
        when (retryCommand) {
            PlaybackRetryCommand.RECONNECT -> onReconnect()
            PlaybackRetryCommand.RESUME_RECORDING -> scope.launch { session.retryRecording() }
            PlaybackRetryCommand.RETRY_LIVE,
            PlaybackRetryCommand.NONE -> Unit
        }
    }

    // Info always closes into the controls, focused on its button.
    fun closeInfo() {
        layers.closeInfo(controls = true)
    }

    val handlePlaybackBack: () -> Unit = handle@{
        if (markerNavigation.open) {
            markerNavigation.dismiss()
            chrome.onUserInteraction()
            return@handle
        }
        val action = playerBackAction(
            surface = PlayerSurface.RECORDING,
            context = currentPlayerForegroundContext(),
            // Back hides a Banner only when one is drawn: not while the recording still loads.
            bannerVisible = playbackAvailable && chrome.bannerVisible,
        )
        // The recording player has no confirmation, schedule, number entry or channel drawer.
        if (!layers.applyBackAction(action)) when (action) {
            PlayerBackAction.CLOSE_INFO -> closeInfo()
            PlayerBackAction.CLOSE_PLAYER -> playerClose.close()
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            else -> Unit
        }
    }

    PlayerRootFocusEffect(foregroundLayer, rootFocus)


    PlayerBackHandler(handlePlaybackBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (event.type == KeyEventType.KeyDown) layers.onKeyDown()
                if (markerNavigation.handle(event, markers, ::seekMarker)) {
                    chrome.onUserInteraction()
                    return@onPreviewKeyEvent true
                }
                if (layers.consumesRevealingKey(event)) return@onPreviewKeyEvent true
                if (layers.infoOpen) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> false
                        else -> true
                    }
                }
                if (handlePlayerStopKeyWithoutTarget(
                        event = event,
                        hasActiveTarget = session.activeTarget.value != null,
                        beginKeyCycle = layers::beginOpeningKeyCycle,
                        stopAndClose = ::stopAndClose,
                    )
                ) {
                    return@onPreviewKeyEvent true
                }
                // A focused Compose target swallows the Back cycle on the TV before the
                // BackHandler can fire, so Back is decided here. Menu, audio-track and captions
                // keys reach the options from any layer; a recording's quick list is always there.
                layers.handleOverlayKey(
                    event = event,
                    keyContext = recordingKeyContext(),
                    foregroundLayer = foregroundLayer,
                    quickListAvailable = true,
                    onBack = handlePlaybackBack,
                    onOptionsOpened = { if (timelineState.seekPending) timelineState.commitPendingSeek() },
                )?.let { return@onPreviewKeyEvent it }

                val mediaAction = mediaPlaybackAction(
                    keyCode = keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    repeatCount = event.nativeKeyEvent.repeatCount,
                )
                if (mediaAction != MediaPlaybackAction.NONE) {
                    layers.beginOpeningKeyCycle(keyCode)
                    when (mediaAction) {
                        MediaPlaybackAction.PLAY -> session.play()
                        MediaPlaybackAction.PAUSE -> pausePlayback()
                        MediaPlaybackAction.TOGGLE -> togglePlayPause()
                        MediaPlaybackAction.NONE -> Unit
                    }
                    chrome.showControls()
                    return@onPreviewKeyEvent true
                }

                when (keyCode) {
                    AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        seekBy(-RECORDING_SHORT_SEEK_MS)
                        chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        seekBy(RECORDING_SHORT_SEEK_MS)
                        chrome.peekBanner()
                        return@onPreviewKeyEvent true
                    }
                }

                val keyAction = recordingKeyAction(keyCode)
                if (playerKeyActionStartsOpeningCycle(keyAction)) {
                    layers.beginOpeningKeyCycle(keyCode)
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
            val channel = entry.channelId?.let { targetObservation?.channel(it) }
            // The same availability as the options sheet's quick lists the buttons open.
            val trackButtons = rememberPlayerTrackButtons(
                player, available = activeTarget == AppPlaybackTarget.Recording(recordingId))
            // The chrome shows its pictures with the session of the recording it plays.
            ProvideArtworkSession(retainedSelection?.currentSession, targetObservation) { PlayerChrome(
                mode = chromeMode(foregroundLayer),
                content = PlayerChromeContent(
                    clock = formatClock(nowSec),
                    info = recordingInfoBarData(entry, channel?.number, nowSec, timelineState.playbackPositionMs,
                        recordedLengthMs, growing),
                    state = chromeState,
                    recordingNow = growing,
                    picon = channel?.icon,
                    artwork = entry.image ?: entry.fanartImage,
                    channelId = entry.channelId,
                ),
                timeline = PlayerChromeTimeline.Recording(
                    positionMs = timelineState.playbackPositionMs,
                    durationMs = recordedLengthMs,
                    displayDurationMs = displayDurationMs.takeIf { it != C.TIME_UNSET && it > 0L },
                    growing = growing,
                    canSeek = timelineState.canSeek,
                    targetMs = timelineState.pendingTargetMs,
                    originMs = timelineState.pendingOriginMs,
                    stepSettled = timelineState.seekDispatched,
                    stepId = timelineState.stepId,
                    markers = markers,
                    markerRevision = markerRevision,
                    motionKey = recordingId,
                ),
                actions = PlayerChromeActions(
                    active = chrome.controlsVisible && layers.optionsPage == null,
                    paused = !playWhenReady,
                    restoreFocus = when {
                        layers.restoreControl != null -> layers.restoreControl
                        layers.restoreInfoFocus -> "player-info"
                        layers.restoreOptionsFocus -> "player-settings"
                        else -> null
                    },
                    tracks = trackButtons,
                ),
                entry = chrome.controlsEntry,
                panelOpen = layers.optionsPage != null || layers.infoOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
                onTogglePause = ::togglePlayPause,
                onSeek = ::seekBy,
                onStop = ::stopAndClose,
                onInfo = layers::openInfo,
                onOptions = layers::openOptions,
                onTrackList = { page ->
                    // Unlike the live player, a track button forgets a pending return to the gear.
                    layers.restoreOptionsFocus = false
                    layers.showQuickList(page)
                },
                onInteraction = chrome::onUserInteraction,
                onCommitSeek = timelineState::commitPendingSeek,
                onActionFocused = layers::onActionFocused,
                onFocusRestored = layers::onFocusRestored,
                markerNavigation = markerNavigation,
                onSeekMarker = ::seekMarker,
            ) }

            if (foregroundLayer == PlayerForegroundLayer.INFO) {
                RecordingPlayerDetails(entry, requireNotNull(targetObservation), observation,
                    { tvheadendSession.observation.value }, nowSec, mutations, notices, scope,
                    onShowGuide = onShowGuide,
                    onPlayRecording = { selection, start ->
                        closeInfo()
                        scope.launch { session.playRecording(selection, start) }
                    }, onClose = ::closeInfo,
                    onBackKeyDown = { layers.beginOpeningKeyCycle(AndroidKeyEvent.KEYCODE_BACK) })
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
                    frameRate = session.frameRate,
                )
            }

        }
        if (!recordingLoading && !foregroundContext.recoveryVisible && !foregroundContext.terminalErrorVisible) {
            PlayerBusyIndicator(PlayerBusyStatus.BUFFERING.takeIf { bufferingVisible }, Modifier.align(Alignment.Center))
        }
        PlayerHiddenStatusChip(
            state = hiddenChipState(
                chromeState, hidden = foregroundLayer == PlayerForegroundLayer.NONE && !chrome.bannerVisible,
                available = playbackAvailable,
            ),
            end = recordingBarEnd(timelineState.playbackPositionMs, recordedLengthMs, growing),
            modifier = Modifier.align(Alignment.BottomStart).padding(playerHiddenChipPadding()),
        )
        TvRecoveryOverlay(
            visible = recordingLoading,
            message = stringResource(R.string.recording_loading),
            opaque = false,
        )
        LivePlayerAlertOverlay(
            visible = foregroundLayer == PlayerForegroundLayer.RECOVERY ||
                foregroundLayer == PlayerForegroundLayer.TERMINAL_ERROR,
            alert = recordingPlayerAlert(
                connectionState, initialConnectionFailure, playbackState, entry, admission, retryCommand,
            ),
            onRetry = { dispatchRecoveryRetry() },
            // Connection settings leaves through Close, so nothing of this screen starts meanwhile.
            onConnectionSettings = {
                playerClose.close()
                onOpenConnectionSettings()
            },
            onClose = playerClose::close,
            deviceFaultDetail = playbackState.deviceFaultDetail(),
        )
        PlayerPanelVisibility(
            value = layers.optionsPage?.let { it to layers.optionsQuickList },
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
                onPageChange = layers::showOptionsPage,
                quickList = layers.quickList.takeIf { layers.optionsQuickList },
                onQuickListClose = layers::closeQuickList,
                quickListTargetEpoch = videoPresentation.epoch,
                quickListAvailable = playbackAvailable && activeTarget == AppPlaybackTarget.Recording(recordingId),
                isQuickListTargetCurrent = session::isQuickListTargetCurrent,
                onQuickAudioSelection = { epoch, override -> session.selectQuickListAudio(epoch, override) },
                recording = true,
                onAspectRatioChange = targetAspect.select,
                onStatsVisibleChange = layers::updateStatsVisibility,
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
