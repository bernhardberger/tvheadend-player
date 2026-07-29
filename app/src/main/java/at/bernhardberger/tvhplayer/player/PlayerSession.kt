package at.bernhardberger.tvhplayer.player

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import at.bernhardberger.tvhplayer.core.PlaybackRecoveryPolicy
import at.bernhardberger.tvhplayer.core.RecordingCheckpointTrigger
import at.bernhardberger.tvhplayer.core.RecordingPlaybackIntent
import at.bernhardberger.tvhplayer.core.RECORDING_SEEK_CHECKPOINT_DEBOUNCE_MS
import at.bernhardberger.tvhplayer.core.RecordingStartDecision
import at.bernhardberger.tvhplayer.core.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.player.htsp.HtspSubscriptionDataSource
import at.bernhardberger.tvhplayer.player.htsp.HtspRecordingDataSource
import at.bernhardberger.tvhplayer.player.htsp.LegacyRenderer
import at.bernhardberger.tvhplayer.player.htsp.TvheadendExtractorsFactory
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.repositories.DvrRepository
import at.bernhardberger.tvhplayer.repositories.RecordingProgressCapability
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class PlayerCommandGate {
    private val mutex = Mutex()

    suspend fun <T> run(command: suspend () -> T): T = mutex.withLock {
        withContext(NonCancellable) { command() }
    }
}

internal fun shouldStartPlayback(activeServiceId: Int?, requestedServiceId: Int): Boolean =
    activeServiceId != requestedServiceId

@C.VideoChangeFrameRateStrategy
@OptIn(UnstableApi::class)
internal fun videoChangeFrameRateStrategy(enabled: Boolean): Int =
    if (enabled) {
        C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
    } else {
        C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
    }

sealed interface PlaybackSessionState {
    data object Idle : PlaybackSessionState
    data object Starting : PlaybackSessionState
    data object Playing : PlaybackSessionState
    data object Finished : PlaybackSessionState
    data class Recovering(val retryDelayMillis: Long) : PlaybackSessionState
    data class Failed(val reason: PlaybackFailureReason) : PlaybackSessionState
}

enum class PlaybackFailureReason {
    RECORDING_UNAVAILABLE,
    RECORDING_READ_FAILED,
}

class PlayerSession(
    private val htsp: HtspService,
    private val playerSettingsStore: PlayerSettingsStore,
    private val dvrRepository: DvrRepository,
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = PlayerCommandGate()
    private var player: ExoPlayer? = null
    private var applicationContext: Context? = null
    private var dataSourceFactory: HtspSubscriptionDataSource.Factory? = null
    private var recordingDataSourceFactory: HtspRecordingDataSource.Factory? = null

    private data class ActiveRecording(
        val entryId: Int,
        val generation: Long,
        val intent: RecordingPlaybackIntent,
        val startCoordinator: RecordingStartCoordinator,
        val progressCoordinator: RecordingProgressCoordinator,
        var dvrState: at.bernhardberger.tvhplayer.htsp.DvrState,
        var preparationApplied: Boolean = false,
        var progressStarted: Boolean = false,
        var clearProgressWhenPlaying: Boolean = false,
    )

    private data class ActivePlayback(
        val context: Context,
        val serviceId: Int,
        val generation: Long,
    )

    @Volatile
    private var activePlayback: ActivePlayback? = null
    @Volatile
    private var activeRecording: ActiveRecording? = null
    private var playbackGeneration = 0L
    private var consecutiveFailures = 0
    private var retryJob: Job? = null
    private var timeshiftStateJob: Job? = null
    private var recoveryEventsEnabled = false
    private var diagnosticsJob: Job? = null
    private var recordingPreparationJob: Job? = null
    private var recordingProgressJob: Job? = null
    private var recordingRemoteJob: Job? = null
    private var recordingSeekJob: Job? = null
    private var videoDecoder: String? = null
    private var audioDecoder: String? = null
    private var audioUnderruns = 0
    private var renderedFramesBaseline = 0
    private var droppedFramesBaseline = 0

    private val _state = MutableStateFlow<PlaybackSessionState>(PlaybackSessionState.Idle)
    val state: StateFlow<PlaybackSessionState> = _state

    private val _activeServiceId = MutableStateFlow<Int?>(null)
    val activeServiceId: StateFlow<Int?> = _activeServiceId
    private val _activeRecordingId = MutableStateFlow<Int?>(null)
    val activeRecordingId: StateFlow<Int?> = _activeRecordingId
    private val _recordingProgressSyncState =
        MutableStateFlow(RecordingProgressSyncState.Inactive)
    val recordingProgressSyncState: StateFlow<RecordingProgressSyncState> =
        _recordingProgressSyncState
    private val _timeshiftState = MutableStateFlow(TimeshiftState())
    val timeshiftState: StateFlow<TimeshiftState> = _timeshiftState
    private val _diagnostics = MutableStateFlow(PlaybackDiagnosticsSnapshot())
    val diagnostics: StateFlow<PlaybackDiagnosticsSnapshot> = _diagnostics

    private var playWhenReadyState = true
    private var currentItem = 0
    private var playbackPosition = 0L

    private var watchdogJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback failed")
            val recording = activeRecording
            if (recording != null) {
                mainScope.launch {
                    commands.run {
                        if (activeRecording?.generation != recording.generation) return@run
                        finishActiveRecordingLocked(terminalError = true)
                        if (activeRecording?.generation == recording.generation) {
                            _state.value = PlaybackSessionState.Failed(
                                PlaybackFailureReason.RECORDING_READ_FAILED
                            )
                        }
                    }
                }
            } else if (recoveryEventsEnabled) {
                scheduleRecovery()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val liveActive = activePlayback != null && recoveryEventsEnabled
            val recordingActive = _activeRecordingId.value != null
            if (!liveActive && !recordingActive) return
            when (playbackState) {
                Player.STATE_READY -> {
                    if (!recordingActive || activeRecording?.preparationApplied == true) {
                        _state.value = PlaybackSessionState.Playing
                    }
                }
                Player.STATE_ENDED -> {
                    if (recordingActive) {
                        val recording = activeRecording ?: return
                        mainScope.launch {
                            commands.run {
                                if (activeRecording?.generation != recording.generation) return@run
                                finishActiveRecordingLocked(naturalEnd = true)
                                if (activeRecording?.generation == recording.generation) {
                                    _state.value = PlaybackSessionState.Finished
                                }
                            }
                        }
                    } else {
                        scheduleRecovery()
                    }
                }
                Player.STATE_BUFFERING -> {
                    if (_state.value !is PlaybackSessionState.Recovering) {
                        _state.value = PlaybackSessionState.Starting
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && (activePlayback != null || _activeRecordingId.value != null)) {
                retryJob?.cancel()
                retryJob = null
                consecutiveFailures = 0
                _state.value = PlaybackSessionState.Playing
            }
            val recording = activeRecording
            if (
                isPlaying &&
                recording?.preparationApplied == true &&
                !recording.progressStarted
            ) {
                recording.progressStarted = true
                val clearProgress = recording.clearProgressWhenPlaying
                recording.clearProgressWhenPlaying = false
                mainScope.launch {
                    val nowMs = System.currentTimeMillis()
                    recording.progressCoordinator.playbackStarted(
                        positionMs = player?.currentPosition ?: 0L,
                        nowMs = nowMs,
                    )
                    if (clearProgress) {
                        recording.progressCoordinator.startOverStarted(nowMs)
                    }
                    publishRecordingProgressState(recording)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private val diagnosticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoder = decoderName
            player?.videoDecoderCounters?.let { counters ->
                renderedFramesBaseline = counters.renderedOutputBufferCount
                droppedFramesBaseline = counters.droppedBufferCount
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            audioDecoder = decoderName
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) {
            audioUnderruns++
        }
    }

    private companion object {
        // If playback is still BUFFERING with the position not advancing this long
        // after start, a renderer (typically an audio track the hardware decoder can't
        // actually play) is blocking; drop audio so the video plays instead of freezing.
        const val STUCK_TIMEOUT_MS = 6_000L
        const val STUCK_POS_MS = 1_000L
        const val RECORDING_PREPARATION_TIMEOUT_MS = 5_000L
        const val RECORDING_PREPARATION_POLL_MS = 50L
        const val RECORDING_PROGRESS_SAMPLE_MS = 1_000L
    }

    @OptIn(UnstableApi::class)
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        applicationContext = appContext
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
            .setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy(enabled = true))
            .build()
            .also { p ->
                p.addAnalyticsListener(EventLogger())
                p.addAnalyticsListener(diagnosticsListener)
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

            finishActiveRecordingLocked()

            val appContext = context.applicationContext
            val target = withContext(Dispatchers.Main.immediate) {
                cancelRecordingJobs()
                activeRecording = null
                retryJob?.cancel()
                retryJob = null
                watchdogJob?.cancel()
                recoveryEventsEnabled = false
                consecutiveFailures = 0
                ActivePlayback(appContext, serviceId, ++playbackGeneration).also {
                    resetDiagnosticsCounters()
                    activePlayback = it
                    _activeServiceId.value = serviceId
                    _activeRecordingId.value = null
                    _recordingProgressSyncState.value = RecordingProgressSyncState.Inactive
                    _state.value = PlaybackSessionState.Starting
                }
            }
            startPlaybackLocked(target)
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun playRecording(
        context: Context,
        entry: DvrEntry,
        path: String,
        knownSize: Long?,
        intent: RecordingPlaybackIntent = RecordingPlaybackIntent.DefaultPolicy,
    ) {
        commands.run {
            finishActiveRecordingLocked()
            val appContext = context.applicationContext
            val recording = withContext(Dispatchers.Main.immediate) {
                cancelRecordingJobs()
                activePlayback = null
                _activeServiceId.value = null
                val generation = ++playbackGeneration
                val progressCapability = dvrRepository.progressCapability.value
                val effectiveIntent = recordingIntentForResumeSupport(
                    intent = intent,
                    resumeSupported = progressCapability != RecordingProgressCapability.Unsupported,
                )
                val progress = RecordingProgressCoordinator(
                    initialServerPositionSeconds = entry.playPosition,
                    capability = progressCapability,
                    enabled = entry.state == at.bernhardberger.tvhplayer.htsp.DvrState.COMPLETED,
                    writer = RecordingProgressWriter { write ->
                        withContext(Dispatchers.IO) {
                            dvrRepository.updateRecordingProgress(
                                entryId = entry.id,
                                playPositionSeconds = write.positionSeconds,
                                setWatched = write.setWatched,
                            )
                        }
                    },
                )
                ActiveRecording(
                    entryId = entry.id,
                    generation = generation,
                    intent = effectiveIntent,
                    startCoordinator = RecordingStartCoordinator(
                        generation = generation,
                        intent = effectiveIntent,
                        state = entry.state,
                        serverPositionSeconds = entry.playPosition,
                        playCount = entry.playCount,
                    ),
                    progressCoordinator = progress,
                    dvrState = entry.state,
                ).also {
                    activeRecording = it
                    _activeRecordingId.value = entry.id
                }
                resetDiagnosticsCounters()
                retryJob?.cancel()
                retryJob = null
                watchdogJob?.cancel()
                recoveryEventsEnabled = false
                consecutiveFailures = 0
                _state.value = PlaybackSessionState.Starting
                player?.let {
                    updateState(it)
                    it.stop()
                    it.clearMediaItems()
                }
                activeRecording ?: error("Recording context was not installed")
            }
            publishRecordingProgressState(recording)
            releaseCurrentDataSource()

            try {
                val settings = playerSettingsStore.playerSettings.first()
                withContext(Dispatchers.Main.immediate) {
                    val p = getOrCreatePlayer(appContext)
                    p.setVideoChangeFrameRateStrategy(
                        videoChangeFrameRateStrategy(settings.refreshRateMatchingEnabled)
                    )
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
                        setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        settings.audioLanguage?.takeIf { it.isNotBlank() }
                            ?.let { setPreferredAudioLanguage(it) }
                        val subtitle = settings.subtitleLanguage
                        if (subtitle.isNullOrBlank()) {
                            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        } else {
                            setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            setPreferredTextLanguage(subtitle)
                        }
                    }.build()
                    val factory = HtspRecordingDataSource.Factory(
                        htsp = htsp,
                        path = path,
                        knownSize = knownSize,
                    )
                    recordingDataSourceFactory = factory
                    val mediaSource = ProgressiveMediaSource.Factory(factory)
                        .createMediaSource(
                            MediaItem.fromUri("htsp-file://recording/${entry.id}")
                        )
                    p.setMediaSource(mediaSource)
                    p.playWhenReady = false
                    p.prepare()
                    startRecordingPreparation(p, recording)
                    startRecordingRemoteUpdates(recording)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Timber.e(error, "Unable to start recording %d", entry.id)
                withContext(Dispatchers.Main.immediate) {
                    _state.value = PlaybackSessionState.Failed(
                        PlaybackFailureReason.RECORDING_UNAVAILABLE
                    )
                }
            }
        }
    }

    private fun startRecordingPreparation(p: ExoPlayer, recording: ActiveRecording) {
        recordingPreparationJob?.cancel()
        recordingPreparationJob = mainScope.launch {
            val startedAt = System.currentTimeMillis()
            while (activeRecording?.generation == recording.generation) {
                val elapsed = System.currentTimeMillis() - startedAt
                val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                val ready = p.playbackState == Player.STATE_READY
                val durationRequired = recording.startCoordinator.requiresDuration()
                if (ready && (!durationRequired || duration != null)) break
                if (elapsed >= RECORDING_PREPARATION_TIMEOUT_MS) break
                if (_state.value is PlaybackSessionState.Failed) return@launch
                delay(RECORDING_PREPARATION_POLL_MS)
            }
            val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            when (
                val decision = recording.startCoordinator.decide(
                    currentGeneration = playbackGeneration,
                    durationMs = duration,
                    waitExpired = true,
                    preparationFailed = _state.value is PlaybackSessionState.Failed,
                )
            ) {
                RecordingPreparationDecision.Wait,
                RecordingPreparationDecision.Cancel -> return@launch
                is RecordingPreparationDecision.Start -> {
                    if (activeRecording?.generation != recording.generation) return@launch
                    when (val start = decision.decision) {
                        RecordingStartDecision.FromBeginning -> p.seekTo(0L)
                        is RecordingStartDecision.ResumeAt -> p.seekTo(start.positionMs)
                    }
                    recording.preparationApplied = true
                    recording.clearProgressWhenPlaying =
                        recording.intent == RecordingPlaybackIntent.FromBeginning
                    p.playWhenReady = true
                    startRecordingProgressSampler(recording)
                }
            }
        }
    }

    private fun startRecordingProgressSampler(recording: ActiveRecording) {
        recordingProgressJob?.cancel()
        recordingProgressJob = mainScope.launch {
            while (activeRecording?.generation == recording.generation) {
                delay(RECORDING_PROGRESS_SAMPLE_MS)
                val p = player ?: continue
                recording.progressCoordinator.tick(
                    positionMs = p.currentPosition,
                    playing = p.isPlaying,
                    nowMs = System.currentTimeMillis(),
                )
                publishRecordingProgressState(recording)
            }
        }
    }

    private fun startRecordingRemoteUpdates(recording: ActiveRecording) {
        recordingRemoteJob?.cancel()
        recordingRemoteJob = mainScope.launch {
            dvrRepository.entries.collectLatest { entries ->
                if (activeRecording?.generation != recording.generation) return@collectLatest
                val entry = entries.firstOrNull { it.id == recording.entryId } ?: return@collectLatest
                recording.dvrState = entry.state
                entry.playPosition?.let { recording.progressCoordinator.remoteUpdate(it) }
                publishRecordingProgressState(recording)
            }
        }
    }

    fun onRecordingSeekSettled() {
        val recording = activeRecording ?: return
        recordingSeekJob?.cancel()
        recordingSeekJob = mainScope.launch {
            delay(RECORDING_SEEK_CHECKPOINT_DEBOUNCE_MS)
            if (activeRecording?.generation != recording.generation) return@launch
            recordingSeekJob = null
            val position = player?.currentPosition ?: return@launch
            recording.progressCoordinator.checkpoint(
                trigger = RecordingCheckpointTrigger.Seek,
                positionMs = position,
                nowMs = System.currentTimeMillis(),
            )
            publishRecordingProgressState(recording)
        }
    }

    fun onRecordingPaused() {
        val recording = activeRecording ?: return
        if (!recording.preparationApplied) return
        val position = player?.currentPosition ?: return
        mainScope.launch {
            if (activeRecording?.generation != recording.generation) return@launch
            recording.progressCoordinator.checkpoint(
                trigger = RecordingCheckpointTrigger.Pause,
                positionMs = position,
                nowMs = System.currentTimeMillis(),
            )
            publishRecordingProgressState(recording)
        }
    }

    private suspend fun finishActiveRecordingLocked(
        naturalEnd: Boolean = false,
        terminalError: Boolean = false,
    ): Boolean {
        val recording = activeRecording ?: return false
        recordingPreparationJob?.cancel()
        recordingProgressJob?.cancel()
        recordingSeekJob?.cancel()
        val playback = withContext(Dispatchers.Main.immediate) {
            val p = player
            (p?.currentPosition ?: 0L) to p?.duration?.takeIf {
                it != C.TIME_UNSET && it > 0L
            }
        }
        val completed = recording.progressCoordinator.finish(
            positionMs = playback.first,
            durationMs = playback.second,
            dvrState = recording.dvrState,
            naturalEnd = naturalEnd,
            terminalError = terminalError,
            nowMs = System.currentTimeMillis(),
        )
        publishRecordingProgressState(recording)
        return completed
    }

    private suspend fun publishRecordingProgressState(recording: ActiveRecording) {
        val progressState = recording.progressCoordinator.snapshot().syncState
        if (activeRecording?.generation == recording.generation) {
            _recordingProgressSyncState.value = progressState
        }
    }

    private fun cancelRecordingJobs() {
        recordingPreparationJob?.cancel()
        recordingPreparationJob = null
        recordingProgressJob?.cancel()
        recordingProgressJob = null
        recordingRemoteJob?.cancel()
        recordingRemoteJob = null
        recordingSeekJob?.cancel()
        recordingSeekJob = null
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
                    if (dataSourceFactory != null || recordingDataSourceFactory != null) {
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
                p.setVideoChangeFrameRateStrategy(
                    videoChangeFrameRateStrategy(settings.refreshRateMatchingEnabled)
                )
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
                    settings.timeshiftEnabled,
                )
                dataSourceFactory = factory
                timeshiftStateJob?.cancel()
                timeshiftStateJob = mainScope.launch {
                    factory.timeshiftState.collectLatest { _timeshiftState.value = it }
                }
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
                finishActiveRecordingLocked()
                withContext(Dispatchers.Main.immediate) {
                    cancelRecordingJobs()
                    activePlayback = null
                    activeRecording = null
                    _activeServiceId.value = null
                    _activeRecordingId.value = null
                    _recordingProgressSyncState.value = RecordingProgressSyncState.Inactive
                    playbackGeneration++
                    consecutiveFailures = 0
                    retryJob?.cancel()
                    retryJob = null
                    watchdogJob?.cancel()
                    timeshiftStateJob?.cancel()
                    timeshiftStateJob = null
                    _timeshiftState.value = TimeshiftState()
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
                finishActiveRecordingLocked()
                withContext(Dispatchers.Main.immediate) {
                    cancelRecordingJobs()
                    activePlayback = null
                    activeRecording = null
                    _activeServiceId.value = null
                    _activeRecordingId.value = null
                    _recordingProgressSyncState.value = RecordingProgressSyncState.Inactive
                    playbackGeneration++
                    setDiagnosticsEnabled(false)
                    retryJob?.cancel()
                    retryJob = null
                    watchdogJob?.cancel()
                    timeshiftStateJob?.cancel()
                    timeshiftStateJob = null
                    _timeshiftState.value = TimeshiftState()
                    recoveryEventsEnabled = false
                    _state.value = PlaybackSessionState.Idle
                    player?.let { p ->
                        updateState(p)
                        p.removeListener(playerListener)
                        p.removeAnalyticsListener(diagnosticsListener)
                        p.release()
                    }
                    player = null
                }
                releaseCurrentDataSource()
            }
        }
    }

    private suspend fun releaseCurrentDataSource() {
        val liveFactory = dataSourceFactory
        val recordingFactory = recordingDataSourceFactory
        dataSourceFactory = null
        recordingDataSourceFactory = null
        timeshiftStateJob?.cancel()
        timeshiftStateJob = null
        _timeshiftState.value = TimeshiftState()
        withContext(Dispatchers.IO) {
            liveFactory?.releaseCurrentDataSource()
            recordingFactory?.releaseCurrentDataSource()
        }
    }

    suspend fun pauseTimeshift(): Boolean = withContext(Dispatchers.IO) {
        val source = dataSourceFactory?.currentDataSource ?: return@withContext false
        if (!source.timeshiftState.value.available) return@withContext false
        runCatching { source.pause() }.isSuccess.also { success ->
            if (!success) disableTimeshiftForCurrentSubscription()
        }
    }

    suspend fun resumeTimeshift(): Boolean = withContext(Dispatchers.IO) {
        val source = dataSourceFactory?.currentDataSource ?: return@withContext false
        if (!source.timeshiftState.value.available) return@withContext false
        runCatching { source.resume() }.isSuccess.also { success ->
            if (!success) disableTimeshiftForCurrentSubscription()
        }
    }

    suspend fun seekTimeshift(deltaMs: Long): TimeshiftSeekDecision? =
        withContext(Dispatchers.IO) {
            val source = dataSourceFactory?.currentDataSource ?: return@withContext null
            if (!source.timeshiftState.value.available) return@withContext null
            runCatching { source.seekTimeshift(deltaMs) }.getOrNull().also {
                if (it == null) disableTimeshiftForCurrentSubscription()
            }
        }

    suspend fun goLive(): TimeshiftSeekDecision? = withContext(Dispatchers.IO) {
        val source = dataSourceFactory?.currentDataSource ?: return@withContext null
        if (!source.timeshiftState.value.available) return@withContext null
        runCatching { source.goLive() }.getOrNull().also {
            if (it == null) disableTimeshiftForCurrentSubscription()
        }
    }

    private fun disableTimeshiftForCurrentSubscription() {
        timeshiftStateJob?.cancel()
        timeshiftStateJob = null
        _timeshiftState.value = TimeshiftState()
    }

    private fun updateState(p: ExoPlayer) {
        playWhenReadyState = p.playWhenReady
        currentItem = p.currentMediaItemIndex
        playbackPosition = p.currentPosition
    }

    @OptIn(UnstableApi::class)
    suspend fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        commands.run {
            withContext(Dispatchers.Main.immediate) {
                player?.setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy(enabled))
            }
        }
    }

    fun setDiagnosticsEnabled(enabled: Boolean) {
        diagnosticsJob?.cancel()
        diagnosticsJob = null
        if (!enabled) {
            _diagnostics.value = PlaybackDiagnosticsSnapshot()
            return
        }

        diagnosticsJob = mainScope.launch {
            var previousBytes = currentReadBytes()
            var previousSampleTime = System.currentTimeMillis()
            while (true) {
                delay(1_000L)
                val currentBytes = currentReadBytes()
                val currentSampleTime = System.currentTimeMillis()
                publishDiagnostics(
                    previousBytes,
                    previousSampleTime,
                    currentBytes,
                    currentSampleTime,
                )
                previousBytes = currentBytes
                previousSampleTime = currentSampleTime
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun publishDiagnostics(
        previousBytes: Long,
        previousSampleTime: Long,
        currentBytes: Long = currentReadBytes(),
        currentSampleTime: Long = System.currentTimeMillis(),
    ) {
        val p = player ?: return
        val videoCounters = p.videoDecoderCounters
        val rendered = decoderCounterDelta(
            videoCounters?.renderedOutputBufferCount ?: 0,
            renderedFramesBaseline,
        )
        val dropped = decoderCounterDelta(
            videoCounters?.droppedBufferCount ?: 0,
            droppedFramesBaseline,
        )
        _diagnostics.value = PlaybackDiagnosticsSnapshot(
            source = when {
                _activeRecordingId.value != null -> PlaybackDiagnosticsSource.RECORDING
                _activeServiceId.value != null -> PlaybackDiagnosticsSource.LIVE_TV
                else -> PlaybackDiagnosticsSource.NONE
            },
            state = _state.value,
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0L),
            durationMs = p.duration.takeIf { it != C.TIME_UNSET && it >= 0L },
            bufferedMs = (p.bufferedPosition - p.currentPosition).coerceAtLeast(0L),
            video = p.videoFormat?.toPlaybackFormatDiagnostics(),
            videoDecoder = videoDecoder,
            renderedFrames = rendered,
            droppedFrames = dropped,
            audio = p.audioFormat?.toPlaybackFormatDiagnostics(),
            audioDecoder = audioDecoder,
            audioUnderruns = audioUnderruns,
            readRateBitsPerSecond = readRateBitsPerSecond(
                previousBytes,
                currentBytes,
                currentSampleTime - previousSampleTime,
            ),
            transport = dataSourceFactory?.transportDiagnostics?.value,
            system = collectSystemDiagnostics(),
        )
    }

    @OptIn(UnstableApi::class)
    private fun resetDiagnosticsCounters() {
        val counters = player?.videoDecoderCounters
        renderedFramesBaseline = counters?.renderedOutputBufferCount ?: 0
        droppedFramesBaseline = counters?.droppedBufferCount ?: 0
        videoDecoder = null
        audioDecoder = null
        audioUnderruns = 0
        _diagnostics.value = PlaybackDiagnosticsSnapshot()
    }

    private fun currentReadBytes(): Long =
        dataSourceFactory?.readMetrics?.totalBytesRead()
            ?: recordingDataSourceFactory?.readMetrics?.totalBytesRead()
            ?: 0L

    private fun collectSystemDiagnostics(): PlaybackSystemDiagnostics? {
        val context = applicationContext ?: return null
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val appPssBytes = activityManager
            ?.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            ?.firstOrNull()
            ?.totalPss
            ?.toLong()
            ?.times(1_024L)

        val mode = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.mode
        val thermalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java)
                ?.currentThermalStatus
                ?.let(::playbackThermalLevel)
        } else {
            null
        }

        return PlaybackSystemDiagnostics(
            outputMode = mode?.let {
                PlaybackOutputMode(
                    width = it.physicalWidth,
                    height = it.physicalHeight,
                    refreshRateHz = it.refreshRate,
                )
            },
            thermalLevel = thermalLevel,
            appPssBytes = appPssBytes,
            lowMemory = activityManager?.let { memoryInfo.lowMemory },
        )
    }
}
