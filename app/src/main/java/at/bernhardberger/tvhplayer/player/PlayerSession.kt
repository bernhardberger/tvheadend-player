package at.bernhardberger.tvhplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import at.bernhardberger.tvhplayer.core.PlaybackRecoveryPolicy
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.player.htsp.HtspSubscriptionDataSource
import at.bernhardberger.tvhplayer.player.htsp.LegacyRenderer
import at.bernhardberger.tvhplayer.player.htsp.TvheadendExtractorsFactory
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class PlayerCommandGate {
    private val mutex = Mutex()

    suspend fun <T> run(command: suspend () -> T): T = mutex.withLock { command() }
}

internal fun shouldStartPlayback(activeServiceId: Int?, requestedServiceId: Int): Boolean =
    activeServiceId != requestedServiceId

sealed interface PlaybackSessionState {
    data object Idle : PlaybackSessionState
    data object Starting : PlaybackSessionState
    data object Playing : PlaybackSessionState
    data class Recovering(val retryDelayMillis: Long) : PlaybackSessionState
}

class PlayerSession(
    private val htsp: HtspService,
    private val playerSettingsStore: PlayerSettingsStore,
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = PlayerCommandGate()
    private var player: ExoPlayer? = null
    private var dataSourceFactory: HtspSubscriptionDataSource.Factory? = null

    private data class ActivePlayback(
        val context: Context,
        val serviceId: Int,
        val generation: Long,
    )

    @Volatile
    private var activePlayback: ActivePlayback? = null
    private var playbackGeneration = 0L
    private var consecutiveFailures = 0
    private var retryJob: Job? = null
    private var recoveryEventsEnabled = false

    private val _state = MutableStateFlow<PlaybackSessionState>(PlaybackSessionState.Idle)
    val state: StateFlow<PlaybackSessionState> = _state

    private val _activeServiceId = MutableStateFlow<Int?>(null)
    val activeServiceId: StateFlow<Int?> = _activeServiceId

    private var playWhenReadyState = true
    private var currentItem = 0
    private var playbackPosition = 0L

    private var watchdogJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback failed")
            if (recoveryEventsEnabled) scheduleRecovery()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (activePlayback == null || !recoveryEventsEnabled) return
            when (playbackState) {
                Player.STATE_READY -> _state.value = PlaybackSessionState.Playing
                Player.STATE_ENDED -> scheduleRecovery()
                Player.STATE_BUFFERING -> {
                    if (_state.value !is PlaybackSessionState.Recovering) {
                        _state.value = PlaybackSessionState.Starting
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && activePlayback != null) {
                retryJob?.cancel()
                retryJob = null
                consecutiveFailures = 0
                _state.value = PlaybackSessionState.Playing
            }
        }
    }

    private companion object {
        // If playback is still BUFFERING with the position not advancing this long
        // after start, a renderer (typically an audio track the hardware decoder can't
        // actually play) is blocking; drop audio so the video plays instead of freezing.
        const val STUCK_TIMEOUT_MS = 6_000L
        const val STUCK_POS_MS = 1_000L
    }

    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        // Hardware decoders everywhere (MODE_ON) so AAC keeps its 5.1 channels and
        // AC3/EAC3 can still pass through to an AVR. The one exception is MPEG-1/2 audio
        // (MP1/MP2): the Amlogic platform decoder advertises support but fails valid
        // DVB/IPTV frames with "Invalid data frame", and never falls back. LegacyRenderer
        // hides that decoder via a MediaCodecSelector so only MP1/MP2 drop to the bundled
        // FFmpeg software decoder; everything else stays on hardware.
        val renderersFactory = LegacyRenderer(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return player ?: ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(appContext))
            .build()
            .also { p ->
                p.addAnalyticsListener(EventLogger())
                p.addListener(playerListener)
                player = p

                p.playWhenReady = playWhenReadyState
                p.seekTo(currentItem, playbackPosition)
            }
    }

    @OptIn(UnstableApi::class)
    suspend fun playService(context: Context, serviceId: Int) {
        commands.run {
            if (!shouldStartPlayback(activePlayback?.serviceId, serviceId)) return@run

            val appContext = context.applicationContext
            val target = withContext(Dispatchers.Main.immediate) {
                retryJob?.cancel()
                retryJob = null
                watchdogJob?.cancel()
                recoveryEventsEnabled = false
                consecutiveFailures = 0
                ActivePlayback(appContext, serviceId, ++playbackGeneration).also {
                    activePlayback = it
                    _activeServiceId.value = serviceId
                    _state.value = PlaybackSessionState.Starting
                }
            }
            startPlaybackLocked(target)
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun startPlaybackLocked(target: ActivePlayback) {
        if (activePlayback != target) return

        try {
            val settings = playerSettingsStore.playerSettings.first()
            if (activePlayback != target) return

            val p = withContext(Dispatchers.Main.immediate) {
                getOrCreatePlayer(target.context).also { player ->
                    watchdogJob?.cancel()
                    recoveryEventsEnabled = false
                    _state.value = PlaybackSessionState.Starting
                    if (dataSourceFactory != null) {
                        updateState(player)
                        player.stop()
                        player.clearMediaItems()
                    }
                }
            }
            releaseCurrentDataSource()
            if (activePlayback != target) return

            // Apply audio/subtitle language preferences. Subtitles default to OFF
            // unless a subtitle language is configured. Audio is re-enabled here so a
            // previous channel's stuck-audio recovery doesn't keep audio off.
            withContext(Dispatchers.Main.immediate) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)

                    settings.audioLanguage?.takeIf { it.isNotBlank() }
                        ?.let { setPreferredAudioLanguage(it) }

                    val sub = settings.subtitleLanguage
                    if (sub.isNullOrBlank()) {
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        setPreferredTextLanguage(sub)
                    }
                }.build()

                val factory = HtspSubscriptionDataSource.Factory(
                    target.context,
                    htsp,
                    settings.profile,
                )
                dataSourceFactory = factory
                val mediaSource = ProgressiveMediaSource.Factory(
                    factory,
                    TvheadendExtractorsFactory(),
                ).createMediaSource(MediaItem.fromUri("htsp://service/${target.serviceId}"))

                p.setMediaSource(mediaSource)
                recoveryEventsEnabled = true
                p.prepare()
                p.playWhenReady = true

                startStuckAudioWatchdog(p, target)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Timber.e(error, "Unable to start service %d", target.serviceId)
            withContext(Dispatchers.Main.immediate) {
                scheduleRecovery(target)
            }
        }
    }

    private fun scheduleRecovery() {
        activePlayback?.let(::scheduleRecovery)
    }

    private fun scheduleRecovery(target: ActivePlayback) {
        if (activePlayback != target || retryJob?.isActive == true) return

        recoveryEventsEnabled = false
        watchdogJob?.cancel()
        val delayMillis = PlaybackRecoveryPolicy.retryDelayMillis(++consecutiveFailures)
        _state.value = PlaybackSessionState.Recovering(delayMillis)
        retryJob = mainScope.launch {
            delay(delayMillis)
            if (activePlayback != target) return@launch

            retryJob = null
            Timber.i(
                "Retrying service %d after playback failure %d",
                target.serviceId,
                consecutiveFailures,
            )
            commands.run {
                if (activePlayback == target) startPlaybackLocked(target)
            }
        }
    }

    /**
     * Safety net for a stream whose audio track the hardware decoder can't actually
     * play (e.g. some IPTV ADTS AAC the platform decoder rejects with 0x1001): the
     * player then never reaches READY and the first video frame stays frozen. If we're
     * still stuck buffering with the position barely moved after [STUCK_TIMEOUT_MS],
     * disable the audio track so the video renderer can drive playback on its own.
     *
     * This only fires on a genuine stall — when the hardware decoder plays the audio
     * fine (the normal case) the watchdog returns without touching anything.
     */
    private fun startStuckAudioWatchdog(p: ExoPlayer, target: ActivePlayback) {
        watchdogJob?.cancel()
        watchdogJob = mainScope.launch {
            delay(STUCK_TIMEOUT_MS)
            val stuck = p.playWhenReady &&
                    p.playbackState == Player.STATE_BUFFERING &&
                    p.currentPosition < STUCK_POS_MS
            if (stuck) {
                Timber.w(
                    "Playback stuck buffering after %d ms (pos=%d); disabling audio to recover video",
                    STUCK_TIMEOUT_MS, p.currentPosition
                )
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()

                delay(STUCK_TIMEOUT_MS)
                val stillStuck = activePlayback == target &&
                    p.playWhenReady &&
                    p.playbackState == Player.STATE_BUFFERING &&
                    p.currentPosition < STUCK_POS_MS
                if (stillStuck) scheduleRecovery(target)
            }
        }
    }

    suspend fun stop() {
        commands.run {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main.immediate) {
                    activePlayback = null
                    _activeServiceId.value = null
                    playbackGeneration++
                    consecutiveFailures = 0
                    retryJob?.cancel()
                    retryJob = null
                    watchdogJob?.cancel()
                    recoveryEventsEnabled = false
                    _state.value = PlaybackSessionState.Idle
                    player?.let { p ->
                        updateState(p)
                        p.stop()
                        p.clearMediaItems()
                    }
                }
                releaseCurrentDataSource()
            }
        }
    }

    suspend fun release() {
        commands.run {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main.immediate) {
                    activePlayback = null
                    _activeServiceId.value = null
                    playbackGeneration++
                    retryJob?.cancel()
                    retryJob = null
                    watchdogJob?.cancel()
                    recoveryEventsEnabled = false
                    _state.value = PlaybackSessionState.Idle
                    player?.let { p ->
                        updateState(p)
                        p.removeListener(playerListener)
                        p.release()
                    }
                    player = null
                }
                releaseCurrentDataSource()
            }
        }
    }

    private suspend fun releaseCurrentDataSource() {
        val factory = dataSourceFactory ?: return
        dataSourceFactory = null
        withContext(Dispatchers.IO) { factory.releaseCurrentDataSource() }
    }

    private fun updateState(p: ExoPlayer) {
        playWhenReadyState = p.playWhenReady
        currentItem = p.currentMediaItemIndex
        playbackPosition = p.currentPosition
    }
}
