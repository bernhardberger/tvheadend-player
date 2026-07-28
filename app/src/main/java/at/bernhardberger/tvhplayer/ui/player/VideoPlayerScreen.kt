package at.bernhardberger.tvhplayer.ui.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.ImageLoader
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ChannelNavigation
import at.bernhardberger.tvhplayer.core.ChannelKeyAction
import at.bernhardberger.tvhplayer.core.ChannelPickAction
import at.bernhardberger.tvhplayer.core.DvrActionFailure
import at.bernhardberger.tvhplayer.core.DvrActionResult
import at.bernhardberger.tvhplayer.core.browsingFocusChannelId
import at.bernhardberger.tvhplayer.core.MediaPlaybackAction
import at.bernhardberger.tvhplayer.core.PlaybackStatusPresentation
import at.bernhardberger.tvhplayer.core.PlaybackAuxiliaryBackAction
import at.bernhardberger.tvhplayer.core.PlaybackOptionsPage
import at.bernhardberger.tvhplayer.core.channelPickAction
import at.bernhardberger.tvhplayer.core.coalesceTimeshiftSeekDelta
import at.bernhardberger.tvhplayer.core.mediaPlaybackAction
import at.bernhardberger.tvhplayer.core.playbackStatusPresentation
import at.bernhardberger.tvhplayer.core.playbackAuxiliaryBackAction
import at.bernhardberger.tvhplayer.core.playbackChannelKeyAction
import at.bernhardberger.tvhplayer.core.playbackSuppressesRevealingKey
import at.bernhardberger.tvhplayer.core.playerParentConsumesRecoveryKey
import at.bernhardberger.tvhplayer.core.PlayerKeyAction
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.core.playerKeyAction
import at.bernhardberger.tvhplayer.core.seekStepMs
import at.bernhardberger.tvhplayer.core.SimpleTvCapability
import at.bernhardberger.tvhplayer.core.SimpleTvProfile
import at.bernhardberger.tvhplayer.core.SimpleTvSettings
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.htsp.ChannelUi
import at.bernhardberger.tvhplayer.htsp.ConnectionState
import at.bernhardberger.tvhplayer.player.PlaybackSessionState
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.common.nextAfter
import at.bernhardberger.tvhplayer.ui.common.nowEvent
import at.bernhardberger.tvhplayer.ui.components.KeepScreenOn
import at.bernhardberger.tvhplayer.ui.components.PiconBox
import at.bernhardberger.tvhplayer.ui.components.TvRecoveryOverlay
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val CHANNEL_NUMBER_TIMEOUT_MS = 1_500L
private const val COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS = 250L
private const val TIMESHIFT_SEEK_DEBOUNCE_MS = 400L

internal suspend fun stopPlaybackAndClose(
    stopPlayback: suspend () -> Unit,
    closePlayer: () -> Unit,
) {
    stopPlayback()
    closePlayer()
}

val bottomGradient = Brush.verticalGradient(
    0f to Color.Transparent,
    0.35f to Color.Black.copy(alpha = 0.35f),
    0.70f to Color.Black.copy(alpha = 0.75f),
    1f to Color.Black.copy(alpha = 0.92f)
)

val topGradient = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.78f),
    0.48f to Color.Black.copy(alpha = 0.48f),
    1f to Color.Transparent,
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoPlayerViewModel: VideoPlayerViewModel = koinViewModel(),
    selection: ChannelSelectionStore = koinInject(),
    lastPlayedChannelStore: LastPlayedChannelStore = koinInject(),
    settingsStore: PlayerSettingsStore = koinInject(),
    channelsVm: ChannelsViewModel = koinViewModel(),
    imageLoader: ImageLoader = koinInject(),
    dvrRepository: DvrRepository = koinInject(),
    channelId: Int,
    channelName: String,
    serviceId: Int,
    simpleTvProfile: SimpleTvProfile = SimpleTvProfile(SimpleTvSettings(), false),
    onUnlock: () -> Unit = {},
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val settings by settingsStore.playerSettings.collectAsStateWithLifecycle(
        initialValue = PlayerSettings(profile = "", audioLanguage = null, subtitleLanguage = null)
    )

    val connState by videoPlayerViewModel.connectionState.collectAsStateWithLifecycle()
    val playbackState by videoPlayerViewModel.playbackState.collectAsStateWithLifecycle()
    val timeshiftState by videoPlayerViewModel.timeshiftState.collectAsStateWithLifecycle()
    val diagnostics by videoPlayerViewModel.diagnostics.collectAsStateWithLifecycle()
    val effectiveTimeshiftState = if (
        simpleTvProfile.allows(SimpleTvCapability.TIMESHIFT)
    ) {
        timeshiftState
    } else {
        TimeshiftState()
    }
    val channels by channelsVm.channels.collectAsStateWithLifecycle()
    val allChannels by channelsVm.allChannels.collectAsStateWithLifecycle()
    val dvrEntries by dvrRepository.entries.collectAsStateWithLifecycle()
    val canModifyRecordings by dvrRepository.canModifyRecordings.collectAsStateWithLifecycle()
    val orderedChannelIds = remember(channels) { channels.map { it.id } }
    val channelNumbers = remember(channels) { channels.associate { it.id to it.number } }
    val selectedInitId by selection.selectedId.collectAsStateWithLifecycle()
    var selectedId by remember { mutableIntStateOf(selectedInitId) }

    var connectionLost by remember { mutableStateOf(false) }
    var screenActive by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var channelNumberInput by remember { mutableStateOf("") }
    var timeshiftFeedback by remember { mutableStateOf<String?>(null) }
    var pendingTimeshiftSeekMs by remember { mutableLongStateOf(0L) }
    var timeshiftSeekJob by remember { mutableStateOf<Job?>(null) }
    var restoreToLiveAfterReconnect by remember { mutableStateOf(false) }
    var optionsPage by remember { mutableStateOf<PlaybackOptionsPage?>(null) }
    var statsVisible by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var infoDvrActionResult by remember { mutableStateOf<DvrActionResult?>(null) }
    var revealingKeyCode by remember { mutableStateOf<Int?>(null) }

    val showDrawer = drawerOpen && !controlsVisible && !infoOpen

    var currentChannelId by remember { mutableIntStateOf(channelId) }
    var currentServiceId by remember { mutableIntStateOf(serviceId) }
    var currentChannelName by remember { mutableStateOf(channelName) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val ctx = LocalContext.current
    val timeshiftUnavailableText = stringResource(R.string.timeshift_unavailable)
    val timeshiftReconnectLiveText = stringResource(R.string.timeshift_reconnect_live)
    val timeshiftSeekClampedText = stringResource(R.string.timeshift_seek_clamped)
    val player = remember { videoPlayerViewModel.getPlayerInstance(ctx) }
    var aspectRatio by remember { mutableStateOf(settings.aspectRatio) }

    LaunchedEffect(settings.aspectRatio) {
        aspectRatio = settings.aspectRatio
    }

    DisposableEffect(statsVisible) {
        videoPlayerViewModel.setDiagnosticsEnabled(statsVisible)
        onDispose {
            if (statsVisible) videoPlayerViewModel.setDiagnosticsEnabled(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> screenActive = true
                Lifecycle.Event.ON_STOP -> screenActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var lastPlayedServiceId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(screenActive, currentServiceId) {
        if (!screenActive) {
            lastPlayedServiceId = -1
            return@LaunchedEffect
        }

        if (lastPlayedServiceId == currentServiceId) return@LaunchedEffect

        if (lastPlayedServiceId != -1) {
            videoPlayerViewModel.stop()
        }
        videoPlayerViewModel.playService(ctx, currentServiceId)
        lastPlayedServiceId = currentServiceId
    }

    LaunchedEffect(playbackState, currentChannelId) {
        if (playbackState is PlaybackSessionState.Playing) {
            lastPlayedChannelStore.setChannelId(currentChannelId)
        }
    }

    KeepScreenOn(enabled = true)

    var interactionToken by remember { mutableIntStateOf(0) }
    val autoHideMs = 5000L

    fun showControls() {
        controlsVisible = true
        interactionToken++
    }

    fun hideControls() {
        controlsVisible = false
    }

    fun queueTimeshiftSeek(deltaMs: Long) {
        pendingTimeshiftSeekMs = coalesceTimeshiftSeekDelta(
            state = effectiveTimeshiftState,
            pendingDeltaMs = pendingTimeshiftSeekMs,
            requestedDeltaMs = deltaMs,
        )
        timeshiftSeekJob?.cancel()
        timeshiftSeekJob = scope.launch {
            delay(TIMESHIFT_SEEK_DEBOUNCE_MS)
            val coalescedDeltaMs = pendingTimeshiftSeekMs
            pendingTimeshiftSeekMs = 0L
            val decision = videoPlayerViewModel.seekTimeshift(coalescedDeltaMs)
            timeshiftFeedback = if (decision?.clamped == true) {
                timeshiftSeekClampedText
            } else if (decision == null) {
                timeshiftUnavailableText
            } else {
                null
            }
        }
    }

    fun tuneChannel(channel: ChannelUi): Boolean {
        timeshiftSeekJob?.cancel()
        pendingTimeshiftSeekMs = 0L
        channelNumberInput = ""
        selection.setSelected(channel.id)
        selectedId = channel.id

        if (channelPickAction(currentChannelId, channel.id) == ChannelPickAction.CLOSE_DRAWER) {
            drawerOpen = false
            return true
        }

        currentChannelId = channel.id
        currentServiceId = channel.id
        currentChannelName = channel.name
        timeshiftFeedback = null

        drawerOpen = false
        showControls()
        return true
    }

    fun tuneAdjacentChannel(direction: Int): Boolean {
        val adjacentId = ChannelNavigation.adjacentId(
            orderedIds = orderedChannelIds,
            currentId = currentChannelId,
            direction = direction,
        ) ?: return false

        val channel = channels.firstOrNull { it.id == adjacentId } ?: return false
        return tuneChannel(channel)
    }

    fun tuneEnteredChannel(): Boolean {
        if (channelNumberInput.isEmpty()) return false

        val channelId = ChannelNavigation.idForNumber(
            orderedIds = orderedChannelIds,
            channelNumbers = channelNumbers,
            enteredNumber = channelNumberInput,
        )
        channelNumberInput = ""

        val channel = channels.firstOrNull { it.id == channelId }
        return channel?.let(::tuneChannel) ?: true
    }

    LaunchedEffect(channelNumberInput) {
        if (channelNumberInput.isEmpty()) return@LaunchedEffect
        delay(
            if (channelNumberInput.length == 3) {
                COMPLETE_CHANNEL_NUMBER_TIMEOUT_MS
            } else {
                CHANNEL_NUMBER_TIMEOUT_MS
            }
        )
        tuneEnteredChannel()
    }

    LaunchedEffect(controlsVisible, interactionToken, optionsPage) {
        if (!controlsVisible || optionsPage != null) return@LaunchedEffect
        delay(autoHideMs)
        hideControls()
    }

    val epg by videoPlayerViewModel.epgForChannel(currentChannelId).collectAsStateWithLifecycle()

    var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000L
            delay(1000L)
        }
    }

    val nowEvent = remember(epg, nowSec) { epg.nowEvent(nowSec) }
    val nextEvent = remember(epg, nowEvent) { epg.nextAfter(nowEvent) }
    val currentChannel = remember(allChannels, currentChannelId) {
        allChannels.firstOrNull { it.id == currentChannelId }
    }
    val currentChannelNumber = remember(channels, currentChannelId) {
        ChannelNavigation.numberForId(
            orderedChannelIds,
            channelNumbers,
            currentChannelId,
        )
    }
    val currentRecording = remember(dvrEntries, nowEvent?.eventId) {
        nowEvent?.let { event -> dvrEntries.firstOrNull { it.eventId == event.eventId } }
    }
    val statusPresentation = playbackStatusPresentation(
        connectionAvailable = connState is ConnectionState.Connected,
        playbackStarting = playbackState is PlaybackSessionState.Starting,
        playbackRecovering = playbackState is PlaybackSessionState.Recovering,
        playbackPlaying = playbackState is PlaybackSessionState.Playing,
    )
    val recoveryVisible = screenActive &&
        statusPresentation == PlaybackStatusPresentation.FULL_RECOVERY
    var compactTuningVisible by remember { mutableStateOf(false) }

    LaunchedEffect(screenActive, statusPresentation) {
        compactTuningVisible = false
        if (screenActive && statusPresentation == PlaybackStatusPresentation.COMPACT_TUNING) {
            delay(500L)
            compactTuningVisible = true
        }
    }

    LaunchedEffect(nowEvent?.eventId) {
        infoDvrActionResult = null
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) drawerOpen = false
    }

    LaunchedEffect(connState, screenActive) {
        if (!screenActive) return@LaunchedEffect

        when (connState) {
            is ConnectionState.Connected -> {
                if (connectionLost) {
                    connectionLost = false
                    showControls()

                    videoPlayerViewModel.playService(ctx, currentServiceId)
                    lastPlayedServiceId = currentServiceId
                    if (restoreToLiveAfterReconnect) {
                        timeshiftFeedback = timeshiftReconnectLiveText
                        restoreToLiveAfterReconnect = false
                    }
                }
            }

            is ConnectionState.Connecting,
            is ConnectionState.Disconnected,
            is ConnectionState.Error -> {
                if (!connectionLost) {
                    connectionLost = true
                    restoreToLiveAfterReconnect =
                        effectiveTimeshiftState.available &&
                            effectiveTimeshiftState.positionMs < -1_000L
                    showControls()
                    videoPlayerViewModel.stop()
                    lastPlayedServiceId = -1
                }
            }
        }
    }

    val handlePlaybackBack: () -> Unit = playbackBack@{
        if (recoveryVisible) {
            val recoveryBackAction = playerKeyAction(
                PlayerKeyContext(
                    surface = PlayerSurface.LIVE,
                    controlsVisible = false,
                    seekbarFocused = false,
                    timeshiftAvailable = effectiveTimeshiftState.available,
                    simpleTvActive = simpleTvProfile.active,
                ),
                keyCode = AndroidKeyEvent.KEYCODE_BACK,
            )
            if (recoveryBackAction == PlayerKeyAction.CLOSE_PLAYER) onClose()
            return@playbackBack
        }
        when (playbackAuxiliaryBackAction(optionsPage, statsVisible, infoOpen)) {
            PlaybackAuxiliaryBackAction.CLOSE_INFO -> infoOpen = false
            PlaybackAuxiliaryBackAction.RETURN_TO_OPTIONS_ROOT -> {
                optionsPage = PlaybackOptionsPage.ROOT
            }
            PlaybackAuxiliaryBackAction.CLOSE_OPTIONS -> {
                optionsPage = null
                interactionToken++
            }
            PlaybackAuxiliaryBackAction.HIDE_STATS -> statsVisible = false
            PlaybackAuxiliaryBackAction.PASS_THROUGH -> when {
                channelNumberInput.isNotEmpty() -> channelNumberInput = ""
                showDrawer -> drawerOpen = false
                else -> when (
                    playerKeyAction(
                        PlayerKeyContext(
                            surface = PlayerSurface.LIVE,
                            controlsVisible = controlsVisible,
                            seekbarFocused = false,
                            timeshiftAvailable = effectiveTimeshiftState.available,
                            simpleTvActive = simpleTvProfile.active,
                            optionsOpen = false,
                            statsOpen = false,
                            infoOpen = false,
                            drawerOpen = false,
                        ),
                        keyCode = AndroidKeyEvent.KEYCODE_BACK,
                    )
                ) {
                    PlayerKeyAction.HIDE_CONTROLS -> hideControls()
                    PlayerKeyAction.CLOSE_PLAYER -> onClose()
                    else -> Unit
                }
            }
        }
    }
    BackHandler(onBack = handlePlaybackBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) handlePlaybackBack()
                    return@onPreviewKeyEvent true
                }
                if (playbackSuppressesRevealingKey(revealingKeyCode, keyCode)) {
                    if (event.type == KeyEventType.KeyUp) revealingKeyCode = null
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (recoveryVisible) {
                    return@onPreviewKeyEvent playerParentConsumesRecoveryKey(keyCode)
                }
                if (infoOpen) return@onPreviewKeyEvent false
                if (optionsPage != null) return@onPreviewKeyEvent false

                val mediaAction = mediaPlaybackAction(
                    keyCode = event.nativeKeyEvent.keyCode,
                    playKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    pauseKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    toggleKeyCode = AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                )
                if (
                    mediaAction != MediaPlaybackAction.NONE &&
                    effectiveTimeshiftState.available
                ) {
                    when (mediaAction) {
                        MediaPlaybackAction.PLAY -> {
                            player.play()
                            scope.launch {
                                if (!videoPlayerViewModel.resumeTimeshift()) {
                                    timeshiftFeedback =
                                        timeshiftUnavailableText
                                }
                            }
                        }
                        MediaPlaybackAction.PAUSE -> {
                            player.pause()
                            scope.launch {
                                if (!videoPlayerViewModel.pauseTimeshift()) {
                                    player.play()
                                    timeshiftFeedback =
                                        timeshiftUnavailableText
                                }
                            }
                        }
                        MediaPlaybackAction.TOGGLE -> {
                            if (effectiveTimeshiftState.paused || !player.playWhenReady) {
                                player.play()
                                scope.launch {
                                    if (!videoPlayerViewModel.resumeTimeshift()) {
                                        timeshiftFeedback =
                                            timeshiftUnavailableText
                                    }
                                }
                            } else {
                                player.pause()
                                scope.launch {
                                    if (!videoPlayerViewModel.pauseTimeshift()) {
                                        player.play()
                                        timeshiftFeedback =
                                            timeshiftUnavailableText
                                    }
                                }
                            }
                        }
                        MediaPlaybackAction.NONE -> Unit
                    }
                    showControls()
                    return@onPreviewKeyEvent true
                }

                ChannelNavigation.digitForKeyCode(event.nativeKeyEvent.keyCode)?.let { digit ->
                    channelNumberInput = ChannelNavigation.appendDigit(channelNumberInput, digit)
                    return@onPreviewKeyEvent true
                }

                ChannelNavigation.directionForKeyCode(event.nativeKeyEvent.keyCode)?.let { direction ->
                    channelNumberInput = ""
                    when (playbackChannelKeyAction(browserVisible = showDrawer)) {
                        ChannelKeyAction.TUNE ->
                            return@onPreviewKeyEvent tuneAdjacentChannel(direction)
                        ChannelKeyAction.PAGE_LIST -> Unit
                    }
                }

                if (channelNumberInput.isNotEmpty()) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Enter,
                        Key.NumPadEnter,
                        Key.DirectionCenter -> tuneEnteredChannel()

                        Key.Back -> {
                            channelNumberInput = ""
                            true
                        }

                        else -> false
                    }
                }

                if (showDrawer) {
                    // List drawer: Right dismisses (edge-of-list exit). Large-card
                    // Simple TV grid needs Right for horizontal navigation — only
                    // Back (or a pick) closes there.
                    val largeCardDrawer = simpleTvProfile.active
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Back -> {
                            drawerOpen = false
                            true
                        }
                        Key.DirectionRight -> {
                            if (largeCardDrawer) {
                                false
                            } else {
                                drawerOpen = false
                                true
                            }
                        }
                        else -> false
                    }
                }

                val keyAction = playerKeyAction(
                    PlayerKeyContext(
                        surface = PlayerSurface.LIVE,
                        controlsVisible = controlsVisible,
                        seekbarFocused = false,
                        timeshiftAvailable = effectiveTimeshiftState.available,
                        simpleTvActive = simpleTvProfile.active,
                        optionsOpen = optionsPage != null,
                        statsOpen = statsVisible,
                        infoOpen = infoOpen,
                        drawerOpen = showDrawer,
                    ),
                    keyCode = keyCode,
                )
                when (keyAction) {
                    PlayerKeyAction.DISMISS_OVERLAY_ONLY -> {
                        when {
                            infoOpen -> {
                                infoOpen = false
                                return@onPreviewKeyEvent true
                            }
                            else -> return@onPreviewKeyEvent true
                        }
                    }
                    PlayerKeyAction.REVEAL_CONTROLS -> {
                        revealingKeyCode = keyCode
                        infoOpen = false
                        showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.REVEAL_AND_TOGGLE_PAUSE -> {
                        revealingKeyCode = keyCode
                        if (effectiveTimeshiftState.paused || !player.playWhenReady) {
                            player.play()
                            scope.launch { videoPlayerViewModel.resumeTimeshift() }
                        } else {
                            player.pause()
                            scope.launch {
                                if (!videoPlayerViewModel.pauseTimeshift()) {
                                    player.play()
                                }
                            }
                        }
                        showControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.OPEN_CHANNELS -> {
                        selectedId = browsingFocusChannelId(
                            visibleChannels = channels,
                            currentFocusId = currentChannelId,
                        ) ?: -1
                        drawerOpen = true
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.OPEN_INFO -> {
                        infoOpen = true
                        hideControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SEEK_BACK -> {
                        queueTimeshiftSeek(-seekStepMs(event.nativeKeyEvent.repeatCount))
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.SEEK_FORWARD -> {
                        queueTimeshiftSeek(seekStepMs(event.nativeKeyEvent.repeatCount))
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.HIDE_CONTROLS -> {
                        hideControls()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.CLOSE_PLAYER -> {
                        onClose()
                        return@onPreviewKeyEvent true
                    }
                    PlayerKeyAction.PASS_THROUGH -> Unit
                }
                false
            }
    ) {
        PlayerVideoSurface(
            player = player,
            aspectRatio = aspectRatio,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = showDrawer,
            enter = slideInHorizontally(tween(180)) { -it },
            exit = slideOutHorizontally(tween(180)) { -it },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
        ) {
            ChannelDrawer(
                channels = channels,
                selectedId = selectedId,
                playingChannelId = currentChannelId,
                nowSec = nowSec,
                channelsVm = channelsVm,
                imageLoader = imageLoader,
                onFocusChannel = { selectedId = it },
                onPickChannel = { tuneChannel(it) },
                onCloseDrawer = { drawerOpen = false },
                // Simple TV uses the shared large-card grid for quick select.
                largeCards = simpleTvProfile.active,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            OverlayControlsTv(
                imageLoader = imageLoader,
                channelNumber = currentChannelNumber,
                channelName = currentChannelName,
                piconPath = currentChannel?.icon,
                nowEvent = nowEvent,
                nextEvent = nextEvent,
                nowSec = nowSec,
                controlsVisible = controlsVisible,
                optionsOpen = optionsPage != null,
                onOpenChannels = {
                    selectedId = browsingFocusChannelId(
                        visibleChannels = channels,
                        currentFocusId = currentChannelId,
                    ) ?: -1
                    hideControls()
                    drawerOpen = true
                },
                onOpenInfo = {
                    infoOpen = true
                    hideControls()
                },
                onStopPlayback = {
                    scope.launch {
                        stopPlaybackAndClose(
                            stopPlayback = videoPlayerViewModel::stop,
                            closePlayer = onClose,
                        )
                    }
                },
                onUserInteraction = { interactionToken++ },
                onOpenOptions = {
                    optionsPage = PlaybackOptionsPage.ROOT
                    controlsVisible = true
                },
                timeshiftState = effectiveTimeshiftState,
                timeshiftFeedback = timeshiftFeedback,
                onToggleTimeshiftPause = {
                    if (effectiveTimeshiftState.paused) {
                        player.play()
                        scope.launch {
                            if (!videoPlayerViewModel.resumeTimeshift()) {
                                timeshiftFeedback =
                                    timeshiftUnavailableText
                            }
                        }
                    } else {
                        player.pause()
                        scope.launch {
                            if (!videoPlayerViewModel.pauseTimeshift()) {
                                player.play()
                                timeshiftFeedback =
                                    timeshiftUnavailableText
                            }
                        }
                    }
                },
                onSeekTimeshift = { deltaMs ->
                    pendingTimeshiftSeekMs = coalesceTimeshiftSeekDelta(
                        state = effectiveTimeshiftState,
                        pendingDeltaMs = pendingTimeshiftSeekMs,
                        requestedDeltaMs = deltaMs,
                    )
                    timeshiftSeekJob?.cancel()
                    timeshiftSeekJob = scope.launch {
                        delay(TIMESHIFT_SEEK_DEBOUNCE_MS)
                        val coalescedDeltaMs = pendingTimeshiftSeekMs
                        pendingTimeshiftSeekMs = 0L
                        val decision = videoPlayerViewModel.seekTimeshift(coalescedDeltaMs)
                        timeshiftFeedback = if (decision?.clamped == true) {
                            timeshiftSeekClampedText
                        } else if (decision == null) {
                            timeshiftUnavailableText
                        } else {
                            null
                        }
                    }
                },
                onGoLive = {
                    scope.launch {
                        val decision = videoPlayerViewModel.goLive()
                        if (decision == null || !videoPlayerViewModel.resumeTimeshift()) {
                            timeshiftFeedback = timeshiftUnavailableText
                        } else {
                            player.play()
                            timeshiftFeedback = null
                        }
                    }
                },
                showStop = simpleTvProfile.allows(SimpleTvCapability.STOP),
            )
        }

        if (infoOpen && nowEvent != null) {
            val infoFocus = remember { FocusRequester() }
            LaunchedEffect(infoOpen, nowEvent.eventId) {
                runCatching { infoFocus.requestFocus() }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        colors = SurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(28.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            PiconBox(
                                imageLoader = imageLoader,
                                piconPath = currentChannel?.icon,
                                modifier = Modifier.width(96.dp).height(54.dp),
                            )
                            at.bernhardberger.tvhplayer.ui.components.ProgrammeContentDetails(
                                event = nowEvent,
                                subtitle = buildString {
                                    append(currentChannelName)
                                    append(" • ")
                                    append(
                                        at.bernhardberger.tvhplayer.ui.common.formatClock(
                                            nowEvent.start,
                                        )
                                    )
                                    append("–")
                                    append(
                                        at.bernhardberger.tvhplayer.ui.common.formatClock(
                                            nowEvent.stop,
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(max = 420.dp),
                                footer = {
                                    currentRecording?.let {
                                        Text(
                                            text = stringResource(R.string.recording_already_scheduled),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    infoDvrActionResult?.let {
                                        Text(
                                            text = infoDvrActionResultLabel(it),
                                            color = if (it is DvrActionResult.Failed) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            10.dp,
                                            Alignment.End,
                                        ),
                                    ) {
                                        if (
                                            currentRecording == null &&
                                            !simpleTvProfile.active &&
                                            canModifyRecordings
                                        ) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        infoDvrActionResult =
                                                            dvrRepository.scheduleEvent(
                                                                nowEvent.eventId,
                                                            )
                                                    }
                                                },
                                            ) {
                                                Text(stringResource(R.string.record))
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { infoOpen = false },
                                            modifier = Modifier.focusRequester(infoFocus),
                                        ) {
                                            Text(stringResource(R.string.player_info_close))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else if (infoOpen && nowEvent == null) {
            // No EPG: close immediately rather than trapping the user.
            LaunchedEffect(Unit) { infoOpen = false }
        }

        if (
            statsVisible &&
            !controlsVisible &&
            optionsPage == null &&
            channelNumberInput.isEmpty() &&
            !showDrawer &&
            !infoOpen
        ) {
            PlaybackStatsOverlay(
                diagnostics = diagnostics,
                aspectRatio = aspectRatio,
                timeshiftState = effectiveTimeshiftState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 48.dp),
            )
        }

        optionsPage?.let { page ->
            PlaybackOptionsSheet(
                page = page,
                player = player,
                aspectRatio = aspectRatio,
                statsVisible = statsVisible,
                showSimpleTvExit = simpleTvProfile.active,
                simpleTvActive = simpleTvProfile.active,
                onPageChange = { optionsPage = it },
                onAspectRatioChange = { mode ->
                    aspectRatio = mode
                    scope.launch { settingsStore.setAspectRatio(mode) }
                },
                onStatsVisibleChange = { statsVisible = it },
                onSimpleTvExit = {
                    optionsPage = null
                    onUnlock()
                },
            )
        }

        AnimatedVisibility(
            visible = channelNumberInput.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(48.dp)
        ) {
            Surface(
                colors = SurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.78f),
                    contentColor = Color.White,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = channelNumberInput,
                    fontSize = 56.sp,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = compactTuningVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .testTag("player-tuning-status"),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = stringResource(R.string.player_tuning_channel, currentChannelName),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        shadow = Shadow(color = Color.Black, blurRadius = 12f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        TvRecoveryOverlay(
            visible = recoveryVisible,
            message = stringResource(
                when {
                    simpleTvProfile.active && connState !is ConnectionState.Connected ->
                        R.string.simple_tv_recovery_connection
                    simpleTvProfile.active -> R.string.simple_tv_recovery_playback
                    connState !is ConnectionState.Connected -> R.string.player_connection_recovering
                    else -> R.string.player_playback_recovering
                }
            ),
            hint = if (simpleTvProfile.active) {
                stringResource(R.string.simple_tv_recovery_hint)
            } else {
                null
            },
            opaque = simpleTvProfile.active,
            retryLabel = if (simpleTvProfile.active) {
                stringResource(R.string.retry)
            } else {
                null
            },
            onRetry = if (simpleTvProfile.active) {
                {
                    scope.launch {
                        // Retry through the existing serialized playback owner.
                        videoPlayerViewModel.playService(ctx, currentServiceId)
                    }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun infoDvrActionResultLabel(result: DvrActionResult): String = stringResource(
    when (result) {
        is DvrActionResult.Accepted -> R.string.recording_action_accepted
        is DvrActionResult.Failed -> when (result.reason) {
            DvrActionFailure.PERMISSION_DENIED -> R.string.recording_action_permission
            DvrActionFailure.CONNECTION_LIMIT -> R.string.recording_action_conn_limit
            DvrActionFailure.CONFLICT -> R.string.recording_action_conflict
            DvrActionFailure.REJECTED -> R.string.recording_action_rejected
            DvrActionFailure.CONNECTION -> R.string.recording_action_connection
        }
    },
)
