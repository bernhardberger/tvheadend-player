package at.bernhardberger.tvhplayer.ui.player

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
import androidx.compose.ui.semantics.LiveRegionMode
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
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.playerKeyActionStartsOpeningCycle
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
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
    // The Banner and the controls; the info panel and the options cover them.
    val chrome = remember(scope) {
        PlayerChromeState(
            scope = scope,
            autoHideTimeoutMillis = RECORDING_CONTROLS_AUTO_HIDE_MS,
            isCovered = { infoOpen || optionsPage != null },
        )
    }
    DisposableEffect(chrome) { onDispose(chrome::dispose) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, chrome) {
        val observer = LifecycleEventObserver { _, event ->
            // A Banner never outlives the stop, nor does its timer.
            if (event == Lifecycle.Event.ON_STOP) chrome.hideBanner()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                chrome.yieldToLayer(controls = true)
            }
            PlaybackOptionsKeyOutcome.MoveDown -> quickList.moveDown()
            // The short list leaves the controls as they are underneath.
            is PlaybackOptionsKeyOutcome.OpenQuickList -> {
                if (!optionsQuickList) quickListReturnControl = lastFocusedControl.takeIf { chrome.controlsVisible }
                optionsQuickList = true
                optionsPage = outcome.page
                chrome.yieldToLayer()
            }
        }
        return true
    }

    fun closeQuickList() {
        restoreQuickListControl = quickListReturnControl.takeIf { chrome.controlsVisible }
        quickListReturnControl = null
        optionsPage = null
        optionsQuickList = false
        chrome.onUserInteraction()
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
                restoreInfoActionFocus = true
            }
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
            chrome.yieldToLayer(controls = false)
            infoOpen = true
            true
        }
        PlayerKeyAction.OPEN_OPTIONS -> openOptionsFromKey(keyCode)
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

    fun recordingKeyAction(keyCode: Int): PlayerKeyAction = playerKeyAction(
        PlayerKeyContext(
            surface = PlayerSurface.RECORDING,
            controlsVisible = chrome.controlsVisible,
            seekbarFocused = false,
            timeshiftAvailable = false,
        ),
        keyCode,
    )

    val autoHideEligible = playerControlsAutoHideEligible(
        PlayerAutoHideContext(
            controlsVisible = chrome.controlsVisible,
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
    SideEffect {
        chrome.updateAutoHideEligibility(autoHideEligible)
    }

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
    val glanceTracks = trackGlance(rememberPlayerTracks(player))
    val chromeBadges = playerGlanceBadges(diagnostics, glanceTracks)
    // The Banner hides after the first frame of the loaded recording; a newly loading one keeps it.
    val bannerFramePresented = at.bernhardberger.tvhplayer.core.bannerFramePresented(
        tuneConfirmed = playbackAvailable &&
            activeTarget == AppPlaybackTarget.Recording(recordingId) &&
            retainedSelection != null && retainedSelection.currentSession === currentSession,
        videoFrameVisible = videoPresentation.visible,
        playing = playbackState is AppPlaybackState.Playing,
        audioOnly = glanceTracks.audioOnly,
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
        chrome.showControls()
    }

    // Back hides a Banner only when one is drawn: not while the recording still loads.
    fun currentBackAction(): PlayerBackAction {
        val layer = playerForegroundLayer(currentPlayerForegroundContext())
        return playerBackAction(
            seekPreviewPhase = timelineState.seekPreviewPhase(chrome.controlsVisible),
            surface = PlayerSurface.RECORDING,
            foregroundLayer = layer,
            bannerVisible = playbackAvailable && chromeMode(layer).isBanner,
        )
    }

    val handlePlaybackBack: () -> Unit = {
        if (markerNavigation.open) {
            markerNavigation.dismiss()
            chrome.onUserInteraction()
        } else when (currentBackAction()) {
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
                chrome.onUserInteraction()
            }
            PlayerBackAction.CLEAR_NUMBER_ENTRY,
            PlayerBackAction.CLOSE_CHANNEL_DRAWER -> Unit
            PlayerBackAction.CLOSE_PLAYER -> playerClose.close()
            PlayerBackAction.HIDE_BANNER -> chrome.hideBanner()
            PlayerBackAction.CANCEL_PENDING_SEEK -> timelineState.cancelPendingSeek()
            PlayerBackAction.DISMISS_SEEK_FEEDBACK ->
                timelineState.dismissDispatchedFeedback()
            PlayerBackAction.HIDE_CONTROLS -> chrome.hideControls()
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
                    chrome.onUserInteraction()
                    return@onPreviewKeyEvent true
                }
                if (playbackSuppressesRevealingKey(revealingKeyCode, keyCode)) {
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
                    val optionsKeyAction = recordingKeyAction(keyCode)
                    if (playerKeyActionStartsOpeningCycle(optionsKeyAction)) {
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
            val channel = entry.channelId?.let { targetObservation?.channel(it) }
            PlayerChrome(
                mode = chromeMode(foregroundLayer),
                content = PlayerChromeContent(
                    clock = formatClock(nowSec),
                    info = recordingInfoBarData(entry, channel?.number, nowSec, timelineState.playbackPositionMs,
                        recordedLengthMs, growing),
                    state = chromeState,
                    recordingNow = growing,
                    badges = chromeBadges,
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
                    markers = markers,
                    markerRevision = markerRevision,
                    motionKey = recordingId,
                ),
                actions = PlayerChromeActions(
                    active = chrome.controlsVisible && optionsPage == null,
                    paused = !playWhenReady,
                    record = false,
                    restoreFocus = when {
                        restoreQuickListControl != null -> restoreQuickListControl
                        restoreInfoActionFocus -> PlayerIdentityCardTag
                        restoreOptionsFocus -> "player-settings"
                        else -> null
                    },
                ),
                imageLoader = imageLoader,
                currentSession = retainedSelection?.currentSession,
                entry = chrome.controlsEntry,
                panelOpen = optionsPage != null || infoOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
                onTogglePause = ::togglePlayPause,
                onSeek = ::seekBy,
                onStop = ::stopAndClose,
                onInfo = {
                    chrome.yieldToLayer(controls = false)
                    infoOpen = true
                },
                onOptions = {
                    restoreOptionsFocus = false
                    optionsQuickList = false
                    optionsPage = PlaybackOptionsPage.ROOT
                    chrome.yieldToLayer(controls = true)
                },
                onInteraction = chrome::onUserInteraction,
                onCommitSeek = timelineState::commitPendingSeek,
                onActionFocused = { lastFocusedControl = it },
                onFocusRestored = {
                    restoreQuickListControl = null
                    restoreInfoActionFocus = false
                    restoreOptionsFocus = false
                },
                markerNavigation = markerNavigation,
                onSeekMarker = ::seekMarker,
            )

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
