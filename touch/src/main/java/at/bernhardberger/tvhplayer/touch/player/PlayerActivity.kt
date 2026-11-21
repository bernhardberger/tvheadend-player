package at.bernhardberger.tvhplayer.touch.player

import android.app.PictureInPictureUiState
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel as lifecycleViewModel
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.core.livePlayWhenReady
import at.bernhardberger.tvhplayer.core.toConnectionState
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.touch.channels.ChannelDetails
import at.bernhardberger.tvhplayer.touch.channels.ChannelProgram
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.touch.channels.rememberChannelsTexts
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.android.ext.android.get
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.touch.ui.ArtworkModels
import at.bernhardberger.tvhplayer.touch.guide.GuideClock
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import java.time.ZoneId
import at.bernhardberger.tvhplayer.touch.ui.LocalChannelAccents
import at.bernhardberger.tvhplayer.touch.ui.FrameNotices
import at.bernhardberger.tvhplayer.touch.ui.LocalFrameNotices
import at.bernhardberger.tvhplayer.touch.ui.DvrControls
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.time.Instant

/**
 * The live player. It plays through the app's one playback runtime: it never creates or
 * releases the player. Back and leaving the app put the picture into the picture-in-picture
 * window; without the window, and when the window is closed, playback stops. A tap on another
 * channel while it is open tunes that one here.
 */
class PlayerActivity : ComponentActivity() {
    private val sdk: SdkRuntimeOwner by inject()
    private val session: TvheadendSession by inject()
    private val artwork by lazy { ArtworkModels(session) }
    private val settings: PlayerSettingsStore by inject()
    private val notices: NoticeCenter by inject()
    private val owner: AppProfileOwner by inject()
    private val connection: AppConnectionViewModel by viewModel()
    private val guide: GuideViewModel by viewModel()
    private val channels: ChannelsViewModel by inject()
    private val runtime get() = sdk.playbackRuntime

    private lateinit var requested: MutableStateFlow<PlayerRoute>
    private lateinit var pictureWindow: PlayerWindow
    private val requestFailed = MutableStateFlow(false)
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = intent.playerRoute()
        // First, before any playback: a user stop since the tap that opened this closes it.
        if (route == null || runtime.enterPlayerScreen(this) == null) {
            leaving = true
            finish()
            return
        }
        requested = MutableStateFlow(route)
        pictureWindow = PlayerWindow(this, runtime.player).also { it.open() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this) { back() }
        // The window's previous/next step through the channel order Channels shows, as the TV's player.
        lifecycle.addObserver(
            PlayerMediaSession(this, runtime, session.observation) { direction ->
                val current = requested.value as? Tune ?: return@PlayerMediaSession
                val order = channels.scope.value.scope.visibleChannels.map { it.id }
                adjacentChannel(order, current.channelId, direction)
                    ?.takeUnless { leaving }?.let { next -> requested.update { tuneTo(it, next) } }
            },
        )
        lifecycleScope.launch {
            requested.collectLatest {
                when (it) {
                    is Tune -> tune(runtime, session, it, requestFailed)
                    is RecordingRoute -> restoreRecording(runtime, session, it, pictureWindow.inWindow)
                }
            }
        }
        lifecycleScope.launch {
            requested.map { it is Tune }.distinctUntilChanged().collectLatest { live ->
                if (live) {
                    launch { retuneOnNewSession(runtime, session) }
                    followConnection(runtime, session)
                }
            }
        }
        finishRecordingAtEof(lifecycleScope, runtime, requested, { lifecycle.currentState }, ::leave)
        lifecycleScope.launch {
            runtime.sessionStops.first()
            leaving = true
            finish()
        }
        setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                CompositionLocalProvider(LocalChannelAccents provides get<ChannelAccents>()) {
                    // The window's one notice host, for the connection in use, as browsing builds it.
                    FrameNotices(notices, owner, session) {
                        when (val shown = requested.collectAsStateWithLifecycle().value) {
                            is Tune -> Player(shown)
                            is RecordingRoute -> RecordingPlayback(
                                shown, runtime, session, artwork, pictureWindow, window, ::back,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val route = intent.playerRoute() ?: return
        if (!::requested.isInitialized || leaving) return
        if (route is RecordingRoute && runtime.enterPlayerScreen(this) == null) {
            leaving = true
            finish()
        } else {
            setIntent(intent)
            requested.update { if (route is Tune) tuneTo(it, route.channelId) else route }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12 and later enter the window by themselves (auto-enter); before that, here.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ::pictureWindow.isInitialized &&
            leavePlayer(LeaveTrigger.UserLeave, canUseWindow(), isInPictureInPictureMode, false) == Leave.EnterWindow
        ) {
            pictureWindow.enter()
        }
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        // Android 15 and later say early, so the controls go before the window's animation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && pipState.isTransitioningToPip &&
            ::pictureWindow.isInitialized
        ) {
            pictureWindow.inWindow.value = true
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!::pictureWindow.isInitialized) return
        if (isInPictureInPictureMode) pictureWindow.inWindow.value = true else pictureWindow.left()
    }

    override fun onStop() {
        super.onStop()
        // Outside a rotation: the window closed, or the player hidden without one.
        if (leavePlayer(LeaveTrigger.Hidden, false, isInPictureInPictureMode, isChangingConfigurations) == Leave.Stop) {
            leave()
        }
    }

    override fun onDestroy() {
        if (::pictureWindow.isInitialized) pictureWindow.close()
        runtime.onLivePlayerClosed(this)
        super.onDestroy()
    }

    private fun back() {
        when (leavePlayer(LeaveTrigger.Back, canUseWindow(), isInPictureInPictureMode, false)) {
            Leave.EnterWindow -> if (!pictureWindow.enter()) leave()
            Leave.Stop -> leave()
            Leave.Stay -> Unit
        }
    }

    private fun leave() {
        if (leaving) return
        leaving = true
        lifecycleScope.launch {
            withContext(NonCancellable) { runtime.stop() }
            finish()
        }
    }

    private fun act(action: PlayerAction, timeshift: LiveTimeshift, paused: Boolean) {
        when (action) {
            PlayerAction.Back -> back()
            PlayerAction.PictureInPicture -> pictureWindow.enter()
            PlayerAction.WatchLive -> {
                runtime.consumeBackgroundNotice(BackgroundPlaybackNotice.LIMIT_EXPIRED)
                requested.update { if (it is Tune) it.copy(token = it.token + 1) else it }
            }
            PlayerAction.TogglePlay -> timeshift.toggle(paused)
            PlayerAction.BackTen -> timeshift.skip(-BACK_SKIP_MS)
            PlayerAction.ForwardThirty -> timeshift.skip(FORWARD_SKIP_MS)
            PlayerAction.GoLive -> timeshift.goLive()
            PlayerAction.RetryChannels -> connection.reconnectNow()
            else -> Unit
        }
    }

    @Composable
    private fun Player(tune: Tune) {
        val presentation by connection.uiState.collectAsStateWithLifecycle()
        val scope by channels.scope.collectAsStateWithLifecycle()
        val observation by channels.observation.collectAsStateWithLifecycle()
        val video by runtime.videoPresentation.collectAsStateWithLifecycle()
        val failed by requestFailed.collectAsStateWithLifecycle()
        val inWindow by pictureWindow.inWindow.collectAsStateWithLifecycle()
        val windowAvailable by pictureWindow.available.collectAsStateWithLifecycle()
        val liveStart by runtime.liveStartUndisturbed.collectAsStateWithLifecycle()
        // The texts and the channel list move on the minute, not on every tick of the clock.
        val clock = rememberCurrentEpochSeconds()
        val minute by remember { derivedStateOf { clock() / SECONDS_PER_MINUTE } }
        val pause by runtime.livePause.collectAsStateWithLifecycle()
        val playWhenReady by rememberPlayWhenReady(runtime.player)
        val commands = rememberCoroutineScope()
        val timeshift = remember { LiveTimeshift(runtime, commands) }
        var controlsShown by remember { mutableStateOf(true) }
        var brightness by remember { mutableFloatStateOf(brightnessSlider(systemBrightness(this))) }
        val shown = tune.channelId
        val status = currentStatus(runtime, observation, shown, failed)
        val paused = !livePlayWhenReady(pause, playWhenReady)
        val options = rememberPlayerOptions(runtime, settings, shown, status == LivePlayerStatus.Playing)
        SideEffect { pictureWindow.mode = options.aspect }
        PlayerEffects(
            timeshift, shown, controlsShown && !paused && !inWindow, liveStart && !paused,
            session, runtime, notices, window,
        )
        val now = Instant.fromEpochSeconds(minute * SECONDS_PER_MINUTE)
        val programs = timeshift.programs.value
        val state = livePlayerState(
            shown, scope, observation, presentation.connection, presentation.degraded, status,
            timeshift.timeline, pause.availability, now, window = windowAvailable, artwork = artwork,
            texts = rememberChannelsTexts(),
        )
        val info = rememberPlayerInfo(
            !inWindow && PlayerControl.Info in state.offered, guide, shown, scope.scope.allChannels, observation,
            programs, GuideClock(now.epochSeconds, ZoneId.systemDefault()), artwork, presentation,
        )
        val dvr = lifecycleViewModel { DvrControls(session, notices) }
        PlayerScreen(
            state = state.withControls(
                state.channel.withProgramTexts(programs, now, LocalResources.current), brightness, paused,
                options, info.schedule, liveRecordOffer(shown, programs, observation, now.epochSeconds),
            ),
            onAction = { act(it, timeshift, paused) },
            onSeek = timeshift::seek,
            onBrightness = { brightness = it; applyBrightness(window, it) },
            onChannel = { id -> requested.update { tuneTo(it, ChannelId(id.toLong())) } },
            onChoice = playerChoices(dvr, info, options, channels) {
                session.currentLiveRecord((requested.value as? Tune)?.channelId, timeshift.programs.value, clock())
            },
            onInfoShown = info.onShown,
            video = { TouchVideoSurface(runtime.player, video.visible, it, pictureWindow::attach, options.aspect) },
            onControlsShown = { controlsShown = it },
            inWindow = inWindow,
            hideAfterMs = rememberControlsTimeout(), confirmationOpen = dvr.pending != null,
            progressing = rememberPlayerProgressing(runtime.player).value, notices = LocalFrameNotices.current,
        )
        PlayerDvrConfirmation(dvr, inWindow)
    }

    companion object {
        private const val EXTRA_CHANNEL = "channel"
        private const val SECONDS_PER_MINUTE = 60L
        private const val BACK_SKIP_MS = 10_000L
        private const val FORWARD_SKIP_MS = 30_000L

        internal fun intent(context: Context, channelId: ChannelId): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_CHANNEL, channelId.value)

        private fun Intent.channelId(): ChannelId? =
            getLongExtra(EXTRA_CHANNEL, -1L).takeIf { it >= 0 }?.let(::ChannelId)

        private fun Intent.playerRoute(): PlayerRoute? = recordingRoute() ?: channelId()?.let { Tune(it) }
    }
}

/** The channel asked for; a new [token] tunes again (Watch live after the pause ended). */
private data class Tune(val channelId: ChannelId, val token: Int = 0) : PlayerRoute

/** A newer tap replaces one still starting; the same channel again changes nothing. */
private fun tuneTo(current: PlayerRoute, channelId: ChannelId): Tune =
    if (current is Tune && current.channelId == channelId) current else Tune(channelId)

@OptIn(UnstableApi::class)
private suspend fun tune(
    runtime: AppPlaybackRuntime,
    session: TvheadendSession,
    tune: Tune,
    requestFailed: MutableStateFlow<Boolean>,
) {
    requestFailed.value = false
    val intent = runtime.notePlaybackIntent()
    val state = runtime.state.value
    if (tune.token == 0 && playingChannelId(runtime.activeTarget.value, state) == tune.channelId &&
        state !is AppPlaybackState.Idle
    ) {
        // Already playing (or starting) this channel: adopt it instead of tuning it again.
        runtime.notePlaybackIntentServed(intent)
        withContext(NonCancellable) { runtime.retuneAfterReconnect() }
        return
    }
    val selection = session.observation.mapNotNull { currentLivePlaybackSelection(it, tune.channelId) }.first()
    if (runtime.isPlaybackIntentStopped(intent)) return
    val result = runtime.playLive(selection, intent)
    if (result?.isStarted != true && !runtime.isPlaybackIntentStopped(intent)) {
        requestFailed.value = true
        runtime.stopAfterLoss()
    }
}

/** A session that replaced the one the channel started on retunes it, as the TV's player does. */
private suspend fun retuneOnNewSession(runtime: AppPlaybackRuntime, session: TvheadendSession) {
    session.observation.map { it.currentSession }
        .distinctUntilChanged { a, b -> a === b }
        .filterNotNull()
        .drop(1)
        .collect { runtime.retuneAfterReconnect() }
}

/** The TV's loss handling: a lost connection stops the channel, its return retries it. */
private suspend fun followConnection(runtime: AppPlaybackRuntime, session: TvheadendSession) {
    var lost = false
    combine(
        session.observation.map { it.sessionState.toConnectionState() is ConnectionState.Connected },
        runtime.videoPresentation.map { it.channelStartAtMillis != null },
    ) { connected, starting -> connected to starting }
        .distinctUntilChanged()
        .collect { (connected, starting) ->
            when {
                starting -> lost = false
                connected && lost -> {
                    lost = false
                    runtime.retryLive(viewerRetry = false)
                }
                !connected && !lost -> {
                    lost = true
                    if (runtime.stopAfterLoss(unlessChannelStarting = true) == null) lost = false
                }
            }
        }
}

/** The system's backlight level (0–1), which the slider shows on its own curve ([brightnessSlider]). */
internal fun systemBrightness(context: Context): Float = Settings.System.getInt(
    context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_BRIGHTNESS,
) / MAX_BRIGHTNESS

/** The slider sets this window's backlight only; the system setting stays as it was. */
internal fun applyBrightness(window: Window, slider: Float) {
    window.attributes = window.attributes.apply {
        screenBrightness = brightnessLevel(slider).coerceIn(MIN_BRIGHTNESS, 1f)
    }
}

private const val DEFAULT_BRIGHTNESS = 128
private const val MAX_BRIGHTNESS = 255f
// Zero would turn the backlight off on some devices.
private const val MIN_BRIGHTNESS = 0.01f

/** The face's status from the runtime, the session and this activity's own failed request, as the TV derives it. */
@OptIn(UnstableApi::class)
@Composable
private fun currentStatus(
    runtime: AppPlaybackRuntime,
    observation: SessionObservation,
    shown: ChannelId,
    failed: Boolean,
): LivePlayerStatus {
    val playback by runtime.state.collectAsStateWithLifecycle()
    val target by runtime.activeTarget.collectAsStateWithLifecycle()
    val live by runtime.livePlaybackObservation.collectAsStateWithLifecycle()
    val notice by runtime.backgroundNotice.collectAsStateWithLifecycle()
    val active = live as? LivePlaybackObservation.Active
    return liveStatus(
        connected = observation.sessionState.toConnectionState() is ConnectionState.Connected,
        state = playback,
        issue = active?.subscriptionIssue,
        serverStopped = active?.serverStopped == true,
        playingId = playingChannelId(target, playback),
        shownId = shown,
        requestFailed = failed,
        notice = notice,
    )
}

/**
 * The clock, episode and genre texts of the program the viewer is in and the one after it, by the
 * shared formatters.
 */
private fun ChannelRow.withProgramTexts(programs: ShownPrograms, now: Instant, resources: Resources): ChannelRow {
    val running = programs.now
    val nowSec = now.epochSeconds
    return copy(
        now = running?.let {
            ChannelProgram(
                title = it.title.orEmpty(), start = null,
                timeRange = formatTimeInterval(it.start.epochSeconds, it.stop.epochSeconds, nowSec = nowSec),
                progress = it.progress(nowSec),
            )
        },
        next = programs.next?.let { ChannelProgram(it.title.orEmpty(), start = formatClock(it.start.epochSeconds)) },
        details = (details ?: ChannelDetails()).copy(
            episode = running?.episodeLine(resources),
            metadata = running?.genre?.takeIf(String::isNotBlank),
        ),
    )
}

private fun EpgEvent.episodeLine(resources: Resources): String? =
    programmeEpisodeLine(episode, subtitle, resources).ifEmpty { null }
