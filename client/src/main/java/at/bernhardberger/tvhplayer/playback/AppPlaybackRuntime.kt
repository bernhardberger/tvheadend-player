@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.PlaybackBinding
import at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult
import at.bernhardberger.tvheadend.sdk.core.RecordingPlaybackAdmission
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackOptions
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopResult
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionPriority
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.job

/**
 * How long a pending live pause waits for the first picture before holding the server anyway:
 * below the SDK's preparation budget and well before the SDK sample queues fill.
 */
internal const val FIRST_PICTURE_BUDGET_MILLIS = 10_000L

/** App presentation adapter over the released coordinator and app-owned player. */
class AppPlaybackRuntime(
    val player: ExoPlayer,
    private val session: TvheadendSession,
    private val coordinator: TvheadendPlaybackCoordinator,
    private val settings: PlayerSettingsStore,
    private val profileOwner: AppProfileOwner,
    private val scope: CoroutineScope,
    private val audioOutput: TvheadendAudioOutputProvider,
    private val audioFocus: PlaybackAudioFocus,
    private val policy: PlaybackRuntimePolicy,
    private val elapsedRealtime: () -> Long = android.os.SystemClock::elapsedRealtime,
) {
    private val targetCommands = PlaybackTargetCommandSerialization()
    private val foregroundPlaybackLifecycle = ForegroundPlaybackLifecycle()
    private var keepTimer: Job? = null
    private val _backgroundNotice = MutableStateFlow<BackgroundPlaybackNotice?>(null)
    val backgroundNotice = _backgroundNotice.asStateFlow()

    fun consumeBackgroundNotice(notice: BackgroundPlaybackNotice) {
        _backgroundNotice.compareAndSet(notice, null)
    }

    /**
     * A Pause pressed before the timeshift grant is known, for one live target epoch.
     * [failClosed] marks the paused retune of a kept channel: without a server pause it stops.
     * [pictureWaitExpired]: the first picture did not arrive within [FIRST_PICTURE_BUDGET_MILLIS],
     * so the grant alone resolves it; [pictureWait] is that budget.
     */
    private class PendingLivePause(val epoch: Long, val wasPlaying: Boolean, val failClosed: Boolean) {
        var pictureWaitExpired = false
        var pictureWait: Job? = null
    }
    private var pendingLivePause: PendingLivePause? = null
        set(value) {
            // Every way a pending pause ends (resolution, resume, retarget, stop, detach,
            // background, failure) also ends its first-picture budget.
            if (field !== value) field?.pictureWait?.cancel()
            field = value
        }
    private var timeshiftRequestedEpoch: Long? = null
    private var readyReachedEpoch: Long? = null
    private val _livePause = MutableStateFlow(LivePauseState())
    val livePause = _livePause.asStateFlow()
    private val _livePauseNotice = MutableStateFlow<LivePauseUnavailableNotice?>(null)
    val livePauseNotice = _livePauseNotice.asStateFlow()

    fun consumeLivePauseNotice(notice: LivePauseUnavailableNotice) {
        _livePauseNotice.compareAndSet(notice, null)
    }
    private val audioSelection = SessionAudioSelection()
    private var audioOutputConfigured = false
    private var audioOutputChanging = false
    private val _audioPassthroughChangeFailed = MutableStateFlow(false)
    val audioPassthroughChangeFailed = _audioPassthroughChangeFailed.asStateFlow()
    private var audioWriteJob: Job? = null
    private val _audioAutomatic = MutableStateFlow(player.trackSelectionParameters.overrides.values.none { it.type == C.TRACK_TYPE_AUDIO })
    val audioAutomatic = _audioAutomatic.asStateFlow()
    private val presentationEpoch = PlaybackPresentationEpoch()
    private val _state = MutableStateFlow<AppPlaybackState>(AppPlaybackState.Idle)
    private val _activeTarget = MutableStateFlow<AppPlaybackTarget?>(null)
    private val _recordingSelection = MutableStateFlow<RecordingPlaybackSelection?>(null)
    private val _recordingAdmission = MutableStateFlow<RecordingPlaybackAdmission?>(null)
    private val markerQuery = RecordingMarkerQuery()
    private var markerJob: Job? = null
    val recordingCutpoints = markerQuery.cutpoints
    val recordingMarkerRevision = markerQuery.revision
    private val _diagnostics = MutableStateFlow(AppPlaybackDiagnostics())
    private val _videoPresentation = MutableStateFlow(AppVideoPresentation())
    private var diagnosticsEnabled = false
    @Volatile
    private var activeTargetEpoch: Long? = null
    @Volatile
    private var targetInstallationInProgress = false
    private var lastLiveChannelId: ChannelId? = null
    private var lastRecordingRequest: Pair<DvrEntryId, RecordingPlaybackStart>? = null
    private var recoveryJob: Job? = null
    private var admittedRecoveryEpoch: Long? = null
    private val recoveryBackoff = LiveRecoveryBackoff()
    private val recoveryAttempts = LiveRecoveryAttemptRunner(::publishResolvedRecoveryPlayerState)
    private var targetFrameListener: Player.Listener? = null
    private var foreground = true
    private var focusGeneration = 0L
    private var interruption: AudioInterruption? = null
    private var interruptionContent = AudioInterruptionContent.NONE
    private var interruptionHoldUnconfirmed = false
    private var resumeAfterInterruption = false
    private var interruptionPaused = false
    private var interruptionMuted = false
    val isInterruptionMuted: Boolean get() = interruptionMuted
    val hasAudioInterruption: Boolean get() = interruption != null
    val state = _state.asStateFlow()
    val activeTarget = _activeTarget.asStateFlow()
    val recordingSelection = _recordingSelection.asStateFlow()
    val recordingAdmission = _recordingAdmission.asStateFlow()
    val livePlaybackObservation = coordinator.livePlaybackObservation
    val diagnostics = _diagnostics.asStateFlow()
    val videoPresentation = _videoPresentation.asStateFlow()

    private val settingsJob = scope.launch {
        settings.playerSettings.distinctUntilChanged().collect {
            targetCommands.serialize(onClosed = {}) {
                val latest = settings.playerSettings.first()
                applyPlayerSettings(latest)
                applyAudioPassthrough(latest.audioPassthroughEnabled)
            }
        }
    }

    private val livePlaybackObservationJob = scope.launch {
        livePlaybackObservation.collect { observation ->
            val observedEpoch = activeTargetEpoch
            val lost = observation !is LivePlaybackObservation.Active || observation.subscriptionIssue != null
            targetCommands.serialize(onClosed = {}) {
                if (lost && observedEpoch == activeTargetEpoch && foregroundPlaybackLifecycle.isKeeping(observedEpoch)) {
                    applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(observedEpoch, BackgroundPlaybackNotice.TUNER_LOST))
                    return@serialize
                }
                // Before the play intent: an unresolved local pause is not a server resume, and a
                // just-resolved one keeps its own outcome (an ambiguous result stays paused).
                val resolvedPause = resolvePendingLivePause()
                publishLivePause()
                val active = livePlaybackObservation.value as? LivePlaybackObservation.Active
                val timeshift = active?.timeshiftState as? LiveTimeshiftState.Available
                if (foreground && !targetInstallationInProgress && !interruptionPaused && !interruptionMuted &&
                    !resolvedPause && pendingLivePause?.epoch != activeTargetEpoch
                ) {
                    observedLivePlayIntent(
                        activeTarget = _activeTarget.value,
                        serverPaused = timeshift?.playbackPaused,
                    )?.let { player.playWhenReady = it }
                }
                publishDiagnostics()
            }
        }
    }

    private var diagnosticDecoderName = "unknown"
    private var diagnosticDecoderGeneration = 0
    private val seekDiagnosticsListener = if (policy.seekDiagnostics) {
        object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                diagnosticDecoderName = decoderName.take(80).replace(Regex("[^A-Za-z0-9._-]"), "_")
                diagnosticDecoderGeneration++
            }
        }
    } else null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!targetCommands.isOpen()) return
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            audioSelection.onMediaItemTransition(player)
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            if (!targetCommands.isOpen()) return
            _audioAutomatic.value = parameters.overrides.values.none { it.type == C.TRACK_TYPE_AUDIO }
            if (interruptionMuted) {
                preserveInterruptionMute()
                return
            }
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            val profileId = profileOwner.audioProfileId ?: return
            val selected = audioSelection.rememberExplicitChoice(player) ?: return
            val previous = audioWriteJob
            audioWriteJob = scope.launch {
                previous?.join()
                try {
                    settings.audioChoices.write(profileId, selected.first, selected.second)
                } catch (_: java.io.IOException) {
                    // A storage failure must not interrupt otherwise valid playback.
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (targetInstallationInProgress || audioOutputChanging || interruptionMuted || !targetCommands.isOpen()) return
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            audioSelection.restore(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) {
                if (policy.trace.enabled && playbackState == Player.STATE_READY) {
                    policy.trace.ready(activeTargetEpoch)
                }
                publishPlayerState()
                if (playbackState == Player.STATE_READY) onTargetReady(activeTargetEpoch)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) publishPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) publishPlayerError()
        }
    }

    init {
        targetCommands.runIfOpen {
            player.addListener(listener)
            seekDiagnosticsListener?.let(player::addAnalyticsListener)
        }
    }

    suspend fun playLive(selection: LivePlaybackSelection): PlaybackTargetResult? =
        targetCommands.serialize(onClosed = { PlaybackTargetResult.SHUT_DOWN }) {
            if (policy.trace.enabled) policy.trace.tuneAdmitted()
            lastLiveChannelId = selection.channelId
            recoveryBackoff.reset()
            val result = playLive(
                channelId = selection.channelId,
                recovering = false,
            )
            result
        }

    private suspend fun playLive(
        channelId: ChannelId,
        recovering: Boolean,
        expectedPresentationEpoch: Long = presentationEpoch.snapshot(),
        recoverySelection: LivePlaybackSelection? = null,
        playWhenReady: Boolean = true,
    ): PlaybackTargetResult? {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (!recovering && _activeTarget.value == null) {
                _state.value = AppPlaybackState.Starting
            }
        }
        val playerSettings = settings.playerSettings.first()
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        configureAudioOutputBeforeFirstTarget(playerSettings)
        val profileSelection = currentLivePlaybackSelection(session.observation.value, channelId)
        if (profileSelection == null) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = unavailableLiveTargetResult(channelId),
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        if (
            recoverySelection != null &&
            profileSelection.currentSession !== recoverySelection.currentSession
        ) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = PlaybackTargetResult.NOT_READY,
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        val streamProfileId = profileOwner.selectedStreamProfileIdFor(profileSelection.currentSession)
        val audioProfile = profileOwner.serverProfile.value
        val audioProfileId = profileOwner.audioProfileId
        audioWriteJob?.join()
        val storedAudio = try {
            audioProfileId?.let { settings.audioChoices.read(it, channelId) }
        } catch (_: java.io.IOException) {
            null
        }
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        if (profileOwner.serverProfile.value !== audioProfile || profileOwner.audioProfileId != audioProfileId) {
            return PlaybackTargetResult.NOT_READY
        }
        audioSelection.useProfile(audioProfile, player)
        audioSelection.load(channelId, storedAudio)
        val selection = currentLivePlaybackSelection(session.observation.value, channelId)
        if (selection == null) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = unavailableLiveTargetResult(channelId),
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        if (
            selection.currentSession !== profileSelection.currentSession ||
            recoverySelection != null &&
            selection.currentSession !== recoverySelection.currentSession
        ) {
            return completeUnavailableTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = PlaybackTargetResult.NOT_READY,
                failureReason = AppPlaybackFailureReason.OTHER,
            )
        }
        val retainInterruptionMute = recovering && interruptionMuted
        val timeshiftPeriod = policy.liveTimeshiftPeriod(playerSettings)
        var committed = false
        val result = installTargetForPresentation(
            expectedPresentationEpoch = expectedPresentationEpoch,
            installTarget = {
                if (!targetCommands.isOpen()) {
                    PlaybackTargetResult.SHUT_DOWN
                } else {
                    when (
                        val target = coordinator.setLiveTarget(
                            session = session,
                            currentSession = selection.currentSession,
                            channelId = selection.channelId,
                            options = LivePlaybackOptions(
                                streamProfileId = streamProfileId,
                                timeshiftPeriod = timeshiftPeriod,
                            ),
                        )
                    ) {
                        is LivePlaybackTargetResult.Bound -> target.result
                        LivePlaybackTargetResult.ObservationExpired -> PlaybackTargetResult.NOT_READY
                        LivePlaybackTargetResult.TargetUnavailable ->
                            PlaybackTargetResult.TARGET_UNAVAILABLE
                    }
                }
            },
            onStarted = started@{
                val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch)
                    ?: return@started
                presentationEpoch.publishIfCurrent(epoch) {
                    committed = true
                    if (retainInterruptionMute) {
                        // Recovery is not viewer consent to resume audio. Retire the old epoch's
                        // callback and keep consuming silently until an explicit focus request.
                        focusGeneration++
                        audioFocus.abandon()
                        resumeAfterInterruption = false
                    } else clearAudioInterruption()
                    activeTargetEpoch = epoch
                    pendingLivePause = null
                    timeshiftRequestedEpoch = epoch.takeIf { timeshiftPeriod > Duration.ZERO }
                    _activeTarget.value = AppPlaybackTarget.Live(selection.channelId)
                    lastLiveChannelId = selection.channelId
                    _recordingSelection.value = null
                    _recordingAdmission.value = null
                    clearRecordingMarkers()
                    _state.value = AppPlaybackState.Starting
                    if (policy.trace.enabled) policy.trace.tuneBound(epoch)
                    beginTargetPresentation(epoch)
                    publishInstalledPlayerState()
                    publishLivePause()
                }
            },
            onFailed = { targetResult ->
                publishTargetFailure(
                    expectedPresentationEpoch = expectedPresentationEpoch,
                    reason = AppPlaybackFailureReason.OTHER,
                    targetResult = targetResult,
                )
            }
        )
        if (committed) {
            // STATE_READY reported while installation was in progress was not observed.
            if (player.playbackState == Player.STATE_READY) onTargetReady(activeTargetEpoch)
            if (retainInterruptionMute && foreground) player.play()
            else applyPlayIntentToStartedTarget(result, playWhenReady)
        }
        return result
    }

    suspend fun playRecording(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? = targetCommands.serialize(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
    ) {
        playRecordingLocked(selection.recordingId, start)
    }

    suspend fun restoreRecordingRoute(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? = targetCommands.restoreRecordingIfNeeded(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
        targetMatches = {
            currentRecordingPlaybackSelection(
                observation = session.observation.value,
                recordingId = selection.recordingId,
            )?.let { currentSelection ->
                !recordingRouteNeedsRestoration(
                    routeSelection = currentSelection,
                    activeTarget = _activeTarget.value,
                    selectedRecording = _recordingSelection.value,
                )
            } == true
        },
        restore = {
            playRecordingLocked(selection.recordingId, start)
        },
    )

    private suspend fun playRecordingLocked(
        recordingId: DvrEntryId,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        val playerSettings = settings.playerSettings.first()
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        configureAudioOutputBeforeFirstTarget(playerSettings)
        val expectedPresentationEpoch = presentationEpoch.snapshot()
        lastRecordingRequest = recordingId to start
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (_activeTarget.value == null) _state.value = AppPlaybackState.Starting
        }
        val selection = currentRecordingPlaybackSelection(session.observation.value, recordingId)
        if (selection == null) {
            val observation = session.observation.value
            return completeUnavailableRecordingTarget(
                expectedPresentationEpoch = expectedPresentationEpoch,
                result = if (observation.currentSession == null) {
                    PlaybackTargetResult.NOT_READY
                } else {
                    PlaybackTargetResult.TARGET_UNAVAILABLE
                },
                admission = if (observation.currentSession == null) {
                    RecordingPlaybackAdmission.ObservationExpired
                } else {
                    RecordingPlaybackAdmission.TargetUnavailable
                },
            )
        }
        var admission: RecordingPlaybackAdmission? = null
        var installedBinding: PlaybackBinding.Recording? = null
        var committed = false
        val result = installTargetForPresentation(
            expectedPresentationEpoch = expectedPresentationEpoch,
            installTarget = {
                if (!targetCommands.isOpen()) return@installTargetForPresentation PlaybackTargetResult.SHUT_DOWN
                when (
                    val binding = session.bindRecordingPlayback(
                        selection.currentSession,
                        selection.recordingId,
                    )
                ) {
                    is PlaybackBindingResult.Bound -> {
                        admission = binding.binding.admission
                        installedBinding = binding.binding
                        if (!targetCommands.isOpen()) {
                            PlaybackTargetResult.SHUT_DOWN
                        } else {
                            coordinator.setRecordingTarget(binding.binding, start)
                        }
                    }
                    PlaybackBindingResult.ObservationExpired -> {
                        admission = RecordingPlaybackAdmission.ObservationExpired
                        PlaybackTargetResult.NOT_READY
                    }
                    PlaybackBindingResult.TargetUnavailable -> {
                        admission = RecordingPlaybackAdmission.TargetUnavailable
                        PlaybackTargetResult.TARGET_UNAVAILABLE
                    }
                }
            },
            onStarted = started@{
                val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch)
                    ?: return@started
                presentationEpoch.publishIfCurrent(epoch) {
                    committed = true
                    clearAudioInterruption()
                    activeTargetEpoch = epoch
                    pendingLivePause = null
                    _activeTarget.value = AppPlaybackTarget.Recording(selection.recordingId)
                    _recordingSelection.value = selection
                    _recordingAdmission.value = admission
                    observeRecordingMarkers(requireNotNull(installedBinding))
                    _state.value = AppPlaybackState.Starting
                    beginTargetPresentation(epoch)
                    publishInstalledPlayerState()
                    publishLivePause()
                }
            },
            onFailed = { targetResult ->
                publishTargetFailure(
                    expectedPresentationEpoch = expectedPresentationEpoch,
                    reason = AppPlaybackFailureReason.RECORDING_READ_FAILED,
                    targetResult = targetResult,
                    recordingAdmission = admission,
                )
            }
        )
        if (committed) applyPlayIntentToStartedTarget(result)
        return result
    }

    suspend fun stop(): PlaybackStopResult = targetCommands.serialize(
        onClosed = { PlaybackStopResult.ShutDown },
    ) {
        foregroundPlaybackLifecycle.onExplicitStop()
        cancelKeepTimer()
        _backgroundNotice.value = null
        recoveryBackoff.reset()
        stopPlayback()
    }

    fun onAppBackgrounded(interactive: Boolean = true) {
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (!foreground) {
                    if (!interactive) applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(activeTargetEpoch))
                    return@serialize
                }
                foreground = false
                val currentJob = currentCoroutineContext().job
                val recoveryPending = recoveryJob?.isActive == true && activeTargetEpoch != null &&
                    admittedRecoveryEpoch == activeTargetEpoch
                // A queued, un-admitted escalation must still reach admission and release
                // any target kept below. The SDK will not escalate that target again.
                if (admittedRecoveryEpoch != null) {
                    recoveryJob?.takeUnless { it === currentJob }?.cancel()
                    if (recoveryJob !== currentJob) recoveryJob = null
                    admittedRecoveryEpoch = null
                }
                val recordingPlayWhenReady = targetCommands.readIfOpen { player.playWhenReady }
                    ?: return@serialize
                player.pause()
                // A pending pause is already a local pause: the paused intent below carries it.
                pendingLivePause = null
                publishLivePause()
                clearAudioInterruption()
                val playerSettings = settings.playerSettings.first()
                val timeshift = (livePlaybackObservation.value as? LivePlaybackObservation.Active)?.timeshiftState as? LiveTimeshiftState.Available
                applyForegroundPlaybackAction(
                    foregroundPlaybackLifecycle.onBackgrounded(
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                        recordingPlayWhenReady = recordingPlayWhenReady,
                        timeshiftAvailable = policy.liveTimeshiftPeriod(playerSettings) > Duration.ZERO && timeshift != null,
                        serverPaused = timeshift?.serverPaused == true || timeshift?.playbackPaused == true,
                        keepLimit = policy.keepTunedLimit(playerSettings),
                        interactive = interactive,
                        nowMillis = elapsedRealtime(),
                        recoveryPending = recoveryPending,
                    ),
                )
            }
        }
    }

    fun onAppForegrounded() {
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                foreground = true
                cancelKeepTimer()
                applyForegroundPlaybackAction(
                    foregroundPlaybackLifecycle.onForegrounded(
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                        nowMillis = elapsedRealtime(),
                    ),
                )
            }
        }
    }

    fun onDeviceStandby() {
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(activeTargetEpoch))
            }
        }
    }

    private fun cancelKeepTimer() {
        keepTimer?.cancel()
        keepTimer = null
    }

    private suspend fun stopPlayback(): PlaybackStopResult {
        if (!targetCommands.isOpen()) return PlaybackStopResult.ShutDown
        // A timer executing this stop must not cancel its own serialized cleanup.
        val timer = keepTimer
        keepTimer = null
        if (timer !== currentCoroutineContext().job) timer?.cancel()
        player.pause()
        pendingLivePause = null
        clearAudioInterruption()
        clearRecordingMarkers()
        val epoch = presentationEpoch.begin()
        endTargetPresentation(epoch)
        val currentJob = currentCoroutineContext().job
        recoveryJob?.takeUnless { it === currentJob }?.cancel()
        recoveryJob = null
        admittedRecoveryEpoch = null
        val result = coordinator.stop()
        if (!targetCommands.isOpen()) return PlaybackStopResult.ShutDown
        presentationEpoch.publishIfCurrent(epoch) {
            _activeTarget.value = null
            _recordingSelection.value = null
            _recordingAdmission.value = null
            activeTargetEpoch = null
            publishLivePause()
            _state.value = AppPlaybackState.Idle
            publishDiagnostics()
        }
        return result
    }

    suspend fun retryLive(): PlaybackTargetResult? = targetCommands.serialize(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
    ) {
        // An explicit retry is a user decision, so it refills the budget an exhausted target used.
        recoveryBackoff.reset()
        lastLiveChannelId?.let { channelId ->
            playLive(
                channelId = channelId,
                recovering = true,
            )
        }
    }
    suspend fun retryRecording(): PlaybackTargetResult? = targetCommands.retryRecording(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
        currentRequest = { lastRecordingRequest },
    ) { (recordingId, start) ->
        playRecordingLocked(recordingId, start)
    }
    suspend fun pauseTimeshift(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        if (interruptionMuted) TimeshiftCommandResult.UNAVAILABLE else coordinator.pauseTimeshift()
    }

    /**
     * Atomically restores interruption-muted sound or pauses local and server playback.
     * Null means the request was handled without a server result: no timeshift feedback or
     * rollback. That is a sound restoration, or a live pause before the target first reached
     * STATE_READY (grant undecided, or granted before the first picture): playback pauses locally
     * at once and the server pause follows once the first picture is ready, or the pause is
     * dropped with [livePauseNotice] when there is no grant. A server hold before the first
     * picture would leave the viewer on the tuning screen.
     * Otherwise returns the server result; rejected pauses restore local intent here without
     * requesting focus. Ambiguous results (such as TIMEOUT) leave playback locally paused.
     * Only for live timeshift targets; other targets pause locally.
     */
    suspend fun pauseTimeshiftPlayback(): TimeshiftCommandResult? = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) { pauseTimeshiftPlaybackLocked() }

    private suspend fun pauseTimeshiftPlaybackLocked(): TimeshiftCommandResult? {
        if (!foreground) return TimeshiftCommandResult.UNAVAILABLE
        if (interruptionMuted) {
            playWithAudioFocus(restoreSoundOnly = true)
            return null
        }
        val wasPlaying = player.playWhenReady
        resumeAfterInterruption = false
        player.pause()
        // The interruption already holds the server; a second pause adds nothing.
        if (interruptionPaused) return null
        val epoch = activeTargetEpoch
        if (epoch != null && interruption == null && targetCommands.isOpen() &&
            awaitsFirstPicture(epoch, currentLivePauseAvailability())
        ) {
            if (pendingLivePause?.epoch != epoch) startPendingLivePause(epoch, wasPlaying = wasPlaying, failClosed = false)
            return null
        }
        return coordinator.pauseTimeshift().also { result ->
            if (result.disposition == TimeshiftCommandDisposition.NOT_ACCEPTED &&
                foreground && interruption == null && targetCommands.isOpen()
            ) {
                player.playWhenReady = wasPlaying
            }
        }
    }
    suspend fun resumeTimeshift(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        playWithAudioFocus(resumeTimeshift = true)
    }
    suspend fun goLive(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        coordinator.returnToLive()
    }
    fun play() {
        val epoch = activeTargetEpoch
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (epoch != null && epoch == activeTargetEpoch) playWithAudioFocus()
            }
        }
    }
    suspend fun seekTimeshift(target: at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget) =
        targetCommands.serialize(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) { coordinator.seekTimeshift(target) }

    suspend fun seekTimeshift(selection: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekSelection) =
        targetCommands.serialize(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) {
            val diagnose = policy.seekDiagnostics && !player.playWhenReady
            fun counters(): String {
                val value = player.videoDecoderCounters ?: return "none"
                value.ensureUpdated()
                return "${value.queuedInputBufferCount}/${value.renderedOutputBufferCount}/" +
                    "${value.skippedOutputBufferCount}/${value.droppedBufferCount}"
            }
            val before = if (diagnose) counters() else ""
            val generation = diagnosticDecoderGeneration
            val started = android.os.SystemClock.elapsedRealtime()
            coordinator.seekTimeshift(selection).also { result ->
                if (diagnose) {
                    val completed = result as? at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Completed
                    val format = player.videoFormat
                    val videoSelected = player.currentTracks.groups.any {
                        it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected
                    }
                    // One bounded line per paused UI seek. No channel, profile, endpoint,
                    // raw error, subscription identity or credential-bearing payload.
                    android.util.Log.i("TvhSeek", "outcome=${completed?.command ?: result.javaClass.simpleName} " +
                        "seek=${completed?.seekCommand} buffering=${completed?.buffering} pause=${completed?.pauseRestoration} " +
                        "elapsedMs=${android.os.SystemClock.elapsedRealtime() - started} " +
                        "countsQueuedRenderedSkippedDropped=$before->${counters()} " +
                        "decoder=$diagnosticDecoderName generation=$generation->$diagnosticDecoderGeneration " +
                        "videoSelected=$videoSelected size=${format?.width}x${format?.height} state=${player.playbackState} " +
                        "playWhenReady=${player.playWhenReady} bufferedMs=${player.totalBufferedDuration} " +
                        "requestedDeltaMs=${selection.displacement.inWholeMilliseconds}")
                }
            }
        }

    /**
     * Samples the presented timeshift position.
     *
     * Segment replacement (including same-subscription restart) invalidates a sample.
     * [toAppPresentation] detects that from the history the sample carries. Rejecting every
     * observation that changed during the
     * round trip also rejected ordinary subscription diagnostics, which arrive several times a
     * second and blanked the timeline that often.
     */
    suspend fun sampleTimeshiftPresentation(): AppTimeshiftState {
        val sample = coordinator.timeshiftPlaybackPosition()
        return (livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState?.toAppPresentation(sample) ?: AppTimeshiftState()
    }
    fun pause() {
        val epoch = activeTargetEpoch
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (epoch == null || epoch != activeTargetEpoch) return@serialize
                pauseLocallyOrRestoreSound()
            }
        }
    }

    /** Returns true only when a local pause was applied, not when restoring muted sound. */
    private suspend fun pauseLocallyOrRestoreSound(): Boolean {
        if (interruptionMuted) {
            playWithAudioFocus()
            return false
        }
        resumeAfterInterruption = false
        player.pause()
        return true
    }

    /** System controls use the same local/server intent as the player keys, atomically. */
    fun setSessionPlayWhenReady(playWhenReady: Boolean, isAttached: () -> Boolean): Job {
        val epoch = activeTargetEpoch
        return scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (!isAttached() || !foreground || epoch == null || epoch != activeTargetEpoch) return@serialize
                val timeshift = currentInterruptionContent() == AudioInterruptionContent.LIVE_TIMESHIFT
                if (_activeTarget.value is AppPlaybackTarget.Live && !timeshift) return@serialize
                if (playWhenReady) {
                    val result = playWithAudioFocus(resumeTimeshift = timeshift)
                    // A denied focus request retains its interruption hold/mute. Only a
                    // granted request follows the player-key server-rejection rollback.
                    if (timeshift && interruption == null && result != TimeshiftCommandResult.ACCEPTED) {
                        player.pause()
                    }
                } else if (timeshift) {
                    pauseTimeshiftPlaybackLocked()
                } else {
                    pauseLocallyOrRestoreSound()
                }
            }
        }
    }

    fun seekRecordingFromSession(positionMs: Long, isAttached: () -> Boolean): Job {
        val epoch = activeTargetEpoch
        return scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (!isAttached() || !foreground || epoch == null || epoch != activeTargetEpoch ||
                    _activeTarget.value !is AppPlaybackTarget.Recording) return@serialize
                if (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) &&
                    player.isCurrentMediaItemSeekable) player.seekTo(positionMs)
            }
        }
    }

    fun seekTo(positionMs: Long) { targetCommands.runIfOpen { player.seekTo(positionMs) } }
    fun seekRecordingMarker(positionMs: Long, expectedRevision: Long) {
        targetCommands.runIfOpen {
            if (expectedRevision != recordingMarkerRevision.value ||
                !markerQuery.isCurrent() || targetInstallationInProgress) return@runIfOpen
            if (!player.isCurrentMediaItemSeekable ||
                !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return@runIfOpen
            if (positionMs in at.bernhardberger.tvhplayer.core.recordingMarkerPositions(
                    recordingCutpoints.value, player.duration,
                )) {
                // seekTo does not change playWhenReady: marker navigation preserves pause.
                player.seekTo(positionMs)
            }
        }
    }

    private fun clearRecordingMarkers() {
        markerQuery.use(null)
        markerJob?.cancel()
        markerJob = null
    }

    private fun observeRecordingMarkers(binding: PlaybackBinding.Recording) {
        clearRecordingMarkers()
        markerQuery.use(binding)
        markerJob = scope.launch {
            // Admission changes, not position samples: at most initial + growing completion.
            var completionFetched = false
            var initialFetched = false
            session.observation.map { binding.admission::class }.distinctUntilChanged().collectLatest {
                when (binding.admission) {
                    is RecordingPlaybackAdmission.GrowingStartOverOnly -> if (!initialFetched) {
                        initialFetched = true
                        markerQuery.refresh(binding)
                    }
                    is RecordingPlaybackAdmission.Completed -> if (!completionFetched) {
                        completionFetched = true
                        markerQuery.refresh(binding)
                    }
                    else -> markerQuery.retire(binding)
                }
            }
        }
    }
    fun setDiagnosticsEnabled(enabled: Boolean) {
        if (!targetCommands.isOpen()) return
        diagnosticsEnabled = enabled
        publishDiagnostics()
    }
    fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        targetCommands.runIfOpen {
            player.setVideoChangeFrameRateStrategy(
                if (enabled) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
            )
        }
    }
    fun onRecoveryRequired(reason: PlaybackRecoveryReason) {
        val requestedEpoch = activeTargetEpoch
        dispatchPlaybackRecovery(scope, reason) { dispatchedReason ->
            val currentJob = currentCoroutineContext().job
            recoveryJob?.takeUnless { it === currentJob }?.cancel()
            recoveryJob = currentJob
            admittedRecoveryEpoch = null
            try {
                // Admit the attempt under the command lock, then wait for the backoff delay
                // without holding it so a channel change or Stop stays responsive meanwhile.
                val admitted = targetCommands.serialize(onClosed = { null }) {
                    if (foregroundPlaybackLifecycle.isKeeping(activeTargetEpoch)) {
                        if (requestedEpoch != activeTargetEpoch) return@serialize null
                        applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(requestedEpoch, BackgroundPlaybackNotice.TUNER_LOST))
                        return@serialize null
                    }
                    if (interruptionPaused || !foreground) return@serialize null
                    val fence = currentLiveRecoveryFence(
                        reason = dispatchedReason,
                        observation = session.observation.value,
                        activeTarget = _activeTarget.value,
                        activeTargetEpoch = activeTargetEpoch,
                    ) ?: return@serialize null
                    if (!fence.matches(
                            activeTarget = _activeTarget.value,
                            activeTargetEpoch = activeTargetEpoch,
                            observation = session.observation.value,
                        )
                    ) {
                        return@serialize null
                    }
                    val attempt = recoveryBackoff.nextAttempt()
                    if (attempt == null) {
                        publishRecoveryExhausted(fence.reason)
                        return@serialize null
                    }
                    admittedRecoveryEpoch = fence.targetEpoch
                    presentationEpoch.publishIfCurrent(fence.targetEpoch) {
                        _state.value = AppPlaybackState.Recovering(
                            reason = fence.reason,
                            retryDelayMillis = attempt.delayMillis,
                        )
                        publishDiagnostics()
                    }
                    fence to attempt
                }
                if (admitted != null) {
                    val (fence, attempt) = admitted
                    recoveryAttempts.run(fence) {
                        if (attempt.delayMillis > 0L) delay(attempt.delayMillis)
                        targetCommands.serialize(onClosed = { null }) {
                            if (interruptionPaused || !foreground) return@serialize null
                            if (!fence.matches(
                                    activeTarget = _activeTarget.value,
                                    activeTargetEpoch = activeTargetEpoch,
                                    observation = session.observation.value,
                                )
                            ) {
                                return@serialize null
                            }
                            playLive(
                                channelId = fence.selection.channelId,
                                recovering = true,
                                expectedPresentationEpoch = fence.targetEpoch,
                                recoverySelection = fence.selection,
                            )
                        }
                    }
                }
            } finally {
                if (recoveryJob === currentJob) {
                    recoveryJob = null
                    admittedRecoveryEpoch = null
                }
            }
        }
    }

    /**
     * Retires a target the SDK keeps reporting as stuck.
     *
     * Recovery cannot succeed indefinitely, and a still-running subscription keeps consuming a
     * tuner. Surfacing a failure lets the user choose, instead of retuning forever.
     */
    private suspend fun publishRecoveryExhausted(recoveryReason: PlaybackRecoveryReason) {
        val stopResult = stopPlayback()
        if (!targetCommands.isOpen()) return
        _state.value = recoveryExhaustedState(stopResult, recoveryReason)
        publishDiagnostics()
    }

    private fun lastSubscriptionIssue(): SubscriptionIssue? =
        (livePlaybackObservation.value as? LivePlaybackObservation.Active)?.subscriptionIssue

    suspend fun detach() {
        if (!targetCommands.close()) return
        cancelKeepTimer()
        val pendingMarkers = markerJob
        clearRecordingMarkers()
        pendingMarkers?.join()
        val pendingRecovery = recoveryJob
        recoveryJob = null
        admittedRecoveryEpoch = null
        pendingRecovery?.cancel()
        livePlaybackObservationJob.cancel()
        settingsJob.cancel()
        pendingRecovery?.join()
        livePlaybackObservationJob.join()
        settingsJob.join()
        targetCommands.awaitIdle {
            pendingLivePause = null
            clearAudioInterruption()
            targetFrameListener?.let(player::removeListener)
            targetFrameListener = null
            player.removeListener(listener)
            seekDiagnosticsListener?.let(player::removeAnalyticsListener)
            audioSelection.clear(player)
        }
        audioWriteJob?.join()
    }

    private suspend fun installTargetForPresentation(
        expectedPresentationEpoch: Long,
        installTarget: suspend () -> PlaybackTargetResult,
        onStarted: () -> Unit,
        onFailed: (PlaybackTargetResult) -> Unit,
    ): PlaybackTargetResult {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        val previousPlayWhenReady = player.playWhenReady
        val previousTarget = healthyActiveTarget()
        val previousTargetEpoch = activeTargetEpoch
        val previousMediaItem = player.currentMediaItem
        player.pause()
        targetInstallationInProgress = true
        return try {
            val result = completePlaybackTargetInstallation(
                installTarget = installTarget,
                presentationStillCurrent = {
                    targetCommands.isOpen() &&
                        presentationEpoch.isCurrent(expectedPresentationEpoch)
                },
                activeTarget = ::healthyActiveTarget,
                onStarted = onStarted,
                onFailed = onFailed,
            )
            if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
            result
        } finally {
            targetInstallationInProgress = false
            if (foreground && targetCommands.isOpen() && presentationEpoch.isCurrent(expectedPresentationEpoch) &&
                previousTarget != null && healthyActiveTarget() == previousTarget &&
                activeTargetEpoch == previousTargetEpoch && player.currentMediaItem == previousMediaItem
            ) {
                player.playWhenReady = previousPlayWhenReady
                publishPlayerState()
            }
            if (targetCommands.isOpen()) {
                audioSelection.useProfile(profileOwner.serverProfile.value, player)
                (_activeTarget.value as? AppPlaybackTarget.Live)?.takeUnless { interruptionMuted }?.let {
                    audioSelection.activate(it.channelId, player)
                }
            }
        }
    }

    private suspend fun completeUnavailableTarget(
        expectedPresentationEpoch: Long,
        result: PlaybackTargetResult,
        failureReason: AppPlaybackFailureReason,
    ): PlaybackTargetResult = installTargetForPresentation(
        expectedPresentationEpoch = expectedPresentationEpoch,
        installTarget = { result },
        onStarted = {},
        onFailed = { targetResult ->
            publishTargetFailure(
                expectedPresentationEpoch = expectedPresentationEpoch,
                reason = failureReason,
                targetResult = targetResult,
            )
        },
    )

    private suspend fun completeUnavailableRecordingTarget(
        expectedPresentationEpoch: Long,
        result: PlaybackTargetResult,
        admission: RecordingPlaybackAdmission,
    ): PlaybackTargetResult = installTargetForPresentation(
        expectedPresentationEpoch = expectedPresentationEpoch,
        installTarget = { result },
        onStarted = {},
        onFailed = { targetResult ->
            publishTargetFailure(
                expectedPresentationEpoch = expectedPresentationEpoch,
                reason = AppPlaybackFailureReason.RECORDING_READ_FAILED,
                targetResult = targetResult,
                recordingAdmission = admission,
            )
        },
    )

    private fun unavailableLiveTargetResult(channelId: ChannelId): PlaybackTargetResult {
        val observation = session.observation.value
        return if (observation.currentSession == null) {
            PlaybackTargetResult.NOT_READY
        } else if (observation.channel(channelId) == null) {
            PlaybackTargetResult.TARGET_UNAVAILABLE
        } else {
            PlaybackTargetResult.NOT_READY
        }
    }

    private fun publishTargetFailure(
        expectedPresentationEpoch: Long,
        reason: AppPlaybackFailureReason,
        targetResult: PlaybackTargetResult,
        recordingAdmission: RecordingPlaybackAdmission? = null,
    ) {
        if (!targetCommands.isOpen()) return
        if (healthyActiveTarget() != null) return
        val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch) ?: return
        presentationEpoch.publishIfCurrent(epoch) {
            endTargetPresentation(epoch)
            activeTargetEpoch = null
            pendingLivePause = null
            _activeTarget.value = null
            publishLivePause()
            _recordingSelection.value = null
            _recordingAdmission.value = recordingAdmission
            clearRecordingMarkers()
            _state.value = AppPlaybackState.Failed(reason, targetResult)
            publishDiagnostics()
        }
    }

    private fun healthyActiveTarget(): AppPlaybackTarget? = targetCommands.readIfOpen {
        _activeTarget.value.takeIf {
            activePlayerTargetIsHealthy(
                playerErrorPresent = player.playerError != null,
                playbackState = player.playbackState,
            )
        }
    }

    private suspend fun applyPlayIntentToStartedTarget(result: PlaybackTargetResult?, playWhenReady: Boolean = true) {
        if (result?.isStarted != true) return
        val target = _activeTarget.value ?: return
        val targetEpoch = activeTargetEpoch ?: return
        cancelKeepTimer()
        _backgroundNotice.value = null
        val action = foregroundPlaybackLifecycle.onTargetStarted(target, targetEpoch)
        if (foreground) {
            if (playWhenReady) playWithAudioFocus()
            else {
                player.pause()
                // A player that failed during installation never reaches READY to resolve a pause.
                if (_state.value is AppPlaybackState.Failed) {
                    stopPausedRetune()
                } else if (readyReachedEpoch != targetEpoch) {
                    // The server pause waits for the first picture (and the grant): a hold before
                    // it leaves the viewer on the tuning screen.
                    startPendingLivePause(targetEpoch, wasPlaying = false, failClosed = true)
                } else if (coordinator.pauseTimeshift() != TimeshiftCommandResult.ACCEPTED ||
                    _state.value is AppPlaybackState.Failed
                ) {
                    stopPausedRetune()
                }
            }
            return
        }
        applyForegroundPlaybackAction(action)
    }

    /** A paused kept channel never plays without its server pause: stop with the tuner-loss notice. */
    private suspend fun stopPausedRetune() {
        stopPlayback()
        _backgroundNotice.value = BackgroundPlaybackNotice.TUNER_LOST
    }

    private fun currentLivePauseAvailability(): LivePauseAvailability {
        val epoch = activeTargetEpoch
        return livePauseAvailability(
            liveTarget = epoch != null && _activeTarget.value is AppPlaybackTarget.Live,
            timeshiftRequested = epoch != null && timeshiftRequestedEpoch == epoch,
            timeshiftAvailable = (livePlaybackObservation.value as? LivePlaybackObservation.Active)
                ?.timeshiftState is LiveTimeshiftState.Available,
            readyReached = epoch != null && readyReachedEpoch == epoch,
        )
    }

    private fun publishLivePause() {
        val epoch = activeTargetEpoch
        _livePause.value = LivePauseState(
            availability = currentLivePauseAvailability(),
            pending = epoch != null && pendingLivePause?.epoch == epoch,
        )
    }

    /** Serialized. The pending pause waits for the first picture at most [FIRST_PICTURE_BUDGET_MILLIS]. */
    private fun startPendingLivePause(epoch: Long, wasPlaying: Boolean, failClosed: Boolean) {
        val pending = PendingLivePause(epoch, wasPlaying = wasPlaying, failClosed = failClosed)
        pendingLivePause = pending
        publishLivePause()
        pending.pictureWait = scope.launch {
            delay(FIRST_PICTURE_BUDGET_MILLIS)
            targetCommands.serialize(onClosed = {}) {
                if (pendingLivePause !== pending || pending.epoch != activeTargetEpoch) return@serialize
                // Resolving clears this pending pause; that must not cancel the resolution itself.
                pending.pictureWait = null
                pending.pictureWaitExpired = true
                resolvePendingLivePause()
                Unit
            }
        }
    }

    private fun clearPendingLivePause() {
        if (pendingLivePause == null) return
        pendingLivePause = null
        publishLivePause()
    }

    /**
     * A live pause for [epoch] stays local and pending until the target first reached STATE_READY:
     * the grant is still undecided, or granted but a server hold now would stop the stream before
     * the first picture. The wait for the picture is bounded ([pictureWaitExpired]): past the
     * budget a granted pause holds the server, so its queues do not fill behind a paused player.
     *
     * Known limitation: [readyReachedEpoch] stays set for the whole epoch, so while the SDK
     * re-prepares the same target (no new epoch) a Pause sends the server pause at once, before
     * the new first picture.
     */
    private fun awaitsFirstPicture(
        epoch: Long,
        availability: LivePauseAvailability,
        pictureWaitExpired: Boolean = false,
    ): Boolean =
        availability == LivePauseAvailability.STARTING ||
            availability == LivePauseAvailability.READY && readyReachedEpoch != epoch && !pictureWaitExpired

    /** The current target reached STATE_READY; without a grant by now it has none. */
    private fun onTargetReady(epoch: Long?) {
        if (epoch == null || epoch != activeTargetEpoch || readyReachedEpoch == epoch) return
        readyReachedEpoch = epoch
        publishLivePause()
        if (pendingLivePause?.epoch != epoch) return
        launchPendingLivePauseResolution(epoch)
    }

    private fun launchPendingLivePauseResolution(epoch: Long) {
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (epoch == activeTargetEpoch) resolvePendingLivePause()
                Unit
            }
        }
    }

    /**
     * Serialized. Completes a pending pause once the grant is decided: one server pause when
     * timeshift became available, otherwise the pause is dropped without any server command.
     * A failed target drops it too; a paused kept-channel retune then stops (fail closed).
     */
    private suspend fun resolvePendingLivePause(): Boolean {
        val pending = pendingLivePause ?: return false
        if (pending.epoch != activeTargetEpoch || !foreground || !targetCommands.isOpen()) {
            if (pending.epoch != activeTargetEpoch) clearPendingLivePause()
            return false
        }
        if (_state.value is AppPlaybackState.Failed) {
            clearPendingLivePause()
            if (pending.failClosed) stopPausedRetune()
            return true
        }
        val availability = currentLivePauseAvailability()
        // The grant alone does not resolve it: STATE_READY does (see onTargetReady).
        if (awaitsFirstPicture(pending.epoch, availability, pending.pictureWaitExpired)) return false
        clearPendingLivePause()
        val rejected = if (availability == LivePauseAvailability.READY) {
            val result = coordinator.pauseTimeshift()
            // A player error while the pause was in flight found no pending pause to fail closed.
            if (pending.failClosed) result != TimeshiftCommandResult.ACCEPTED || _state.value is AppPlaybackState.Failed
            else result.disposition == TimeshiftCommandDisposition.NOT_ACCEPTED
        } else true
        if (!rejected || !targetCommands.isOpen() || pending.epoch != activeTargetEpoch) return true
        if (pending.failClosed) {
            stopPausedRetune()
            return true
        }
        if (foreground && interruption == null) player.playWhenReady = pending.wasPlaying
        _livePauseNotice.value = LivePauseUnavailableNotice()
        return true
    }

    private suspend fun applyForegroundPlaybackAction(action: ForegroundPlaybackAction) {
        executeForegroundPlaybackAction(
            action = action,
            stopLive = { stopPlayback() },
            pauseRecording = { targetCommands.runIfOpen(player::pause) },
            resumeLive = { channelId ->
                if (_activeTarget.value != null) stopPlayback()
                lastLiveChannelId = channelId
                playLive(channelId = channelId, recovering = false)
            },
            resumeRecording = { playWithAudioFocus() },
            keepLive = { keep ->
                player.pause()
                val paused = coordinator.pauseTimeshift() == TimeshiftCommandResult.ACCEPTED
                val yielded = paused && coordinator.setLivePriority(LiveSubscriptionPriority.YIELD) == TimeshiftCommandResult.ACCEPTED
                if (targetCommands.isOpen() && activeTargetEpoch == keep.epoch) {
                    if (!yielded) {
                        applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(keep.epoch))
                    } else if ((livePlaybackObservation.value as? LivePlaybackObservation.Active)?.subscriptionIssue != null) {
                        applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(keep.epoch, BackgroundPlaybackNotice.TUNER_LOST))
                    } else {
                        keepTimer = scope.launch {
                            delay((keep.deadlineMillis - elapsedRealtime()).coerceAtLeast(0))
                            targetCommands.serialize(onClosed = {}) {
                                applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(keep.epoch, BackgroundPlaybackNotice.LIMIT_EXPIRED))
                            }
                        }
                    }
                }
            },
            resumeKeptLive = { kept ->
                if (activeTargetEpoch == kept.epoch) {
                    val restored = coordinator.setLivePriority(LiveSubscriptionPriority.NORMAL) == TimeshiftCommandResult.ACCEPTED
                    if (targetCommands.isOpen() && activeTargetEpoch == kept.epoch) {
                        val resumed = if (restored && kept.resume) playWithAudioFocus(resumeTimeshift = true)
                            else TimeshiftCommandResult.ACCEPTED
                        // Focus denial retains interruption ownership; it is not a server failure.
                        val resumeFailed = resumed != TimeshiftCommandResult.ACCEPTED &&
                            resumed != TimeshiftCommandResult.SHUT_DOWN && interruption == null
                        if (!restored || resumeFailed) {
                            val channel = (_activeTarget.value as? AppPlaybackTarget.Live)?.channelId
                            stopPlayback()
                            channel?.let { playLive(it, recovering = false, playWhenReady = kept.resume) }
                            if (resumeFailed) _backgroundNotice.value = BackgroundPlaybackNotice.TUNER_LOST
                        }
                    }
                }
            },
            notice = { _backgroundNotice.value = it },
        )
    }

    private fun currentInterruptionContent(): AudioInterruptionContent = when (_activeTarget.value) {
        is AppPlaybackTarget.Live -> if (
            (livePlaybackObservation.value as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available
        ) AudioInterruptionContent.LIVE_TIMESHIFT else AudioInterruptionContent.LIVE
        is AppPlaybackTarget.Recording -> AudioInterruptionContent.RECORDING
        null -> AudioInterruptionContent.NONE
    }

    private fun clearAudioInterruption() {
        focusGeneration++
        audioFocus.abandon()
        interruption = null
        interruptionHoldUnconfirmed = false
        interruptionContent = AudioInterruptionContent.NONE
        resumeAfterInterruption = false
        interruptionPaused = false
        setInterruptionMuted(false)
    }

    private fun setInterruptionMuted(muted: Boolean) {
        if (interruptionMuted == muted) return
        interruptionMuted = muted
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, muted).build()
        if (!muted && !targetInstallationInProgress) audioSelection.restore(player)
    }

    private fun preserveInterruptionMute() {
        if (interruptionMuted && C.TRACK_TYPE_AUDIO !in player.trackSelectionParameters.disabledTrackTypes) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        }
    }

    private suspend fun playWithAudioFocus(
        resumeTimeshift: Boolean = false,
        restoreSoundOnly: Boolean = false,
    ): TimeshiftCommandResult {
        if (!targetCommands.isOpen()) return TimeshiftCommandResult.SHUT_DOWN
        if (!foreground) return TimeshiftCommandResult.UNAVAILABLE
        val epoch = activeTargetEpoch ?: return TimeshiftCommandResult.UNAVAILABLE
        // Every explicit play retires a pause still pending for this target: it never reached
        // the server, so the play resumes locally only.
        val retiredPendingPause = !restoreSoundOnly && pendingLivePause?.epoch == epoch
        if (retiredPendingPause) clearPendingLivePause()
        val generation = ++focusGeneration
        val granted = audioFocus.request { event ->
            scope.launch {
                targetCommands.serialize(onClosed = {}) {
                    if (foreground && epoch == activeTargetEpoch && generation == focusGeneration) {
                        handleAudioInterruption(event)
                    }
                }
            }
        }
        if (!granted) {
            // Preserve a previous server hold even if timeshift availability has disappeared.
            if (!restoreSoundOnly) handleAudioInterruption(AudioInterruption.TRANSIENT_LOSS, wasPlaying = true)
            // Delayed gain is disabled: only another explicit request can resolve denial.
            resumeAfterInterruption = false
            return TimeshiftCommandResult.UNAVAILABLE
        }
        // Restoring sound only still releases a server hold whose acknowledgement was lost.
        val releaseServer = if (restoreSoundOnly) interruption != null && interruptionHoldUnconfirmed
        else resumeTimeshift && !retiredPendingPause ||
            interruption != null && interruptionContent == AudioInterruptionContent.LIVE_TIMESHIFT
        val result = if (releaseServer) coordinator.resumeTimeshift() else TimeshiftCommandResult.ACCEPTED
        interruption = null
        interruptionHoldUnconfirmed = false
        interruptionPaused = false
        resumeAfterInterruption = false
        setInterruptionMuted(false)
        player.play()
        return result
    }

    private suspend fun handleAudioInterruption(event: AudioInterruption, wasPlaying: Boolean = player.playWhenReady) {
        val content = if (interruption != null) interruptionContent else currentInterruptionContent()
        val action = audioInterruptionAction(content, event, wasPlaying, resumeAfterInterruption)
        if (event == AudioInterruption.TRANSIENT_LOSS_CAN_DUCK) return
        if (event == AudioInterruption.GAIN) {
            when (action) {
                AudioInterruptionAction.RESUME -> {
                    if (content == AudioInterruptionContent.LIVE_TIMESHIFT &&
                        coordinator.resumeTimeshift() != TimeshiftCommandResult.ACCEPTED && interruptionPaused) return
                    interruptionPaused = false
                    setInterruptionMuted(false)
                    player.play()
                }
                AudioInterruptionAction.UNMUTE -> setInterruptionMuted(false)
                else -> return
            }
            interruption = null
            interruptionHoldUnconfirmed = false
            resumeAfterInterruption = false
            return
        }
        val persistent = interruption == AudioInterruption.PERMANENT_LOSS || interruption == AudioInterruption.NOISY
        if (event != AudioInterruption.TRANSIENT_LOSS) resumeAfterInterruption = false
        else if (!persistent && interruption == null) resumeAfterInterruption = wasPlaying
        if (!persistent || event != AudioInterruption.TRANSIENT_LOSS) interruption = event
        interruptionContent = content
        when (action) {
            AudioInterruptionAction.PAUSE -> if (!interruptionPaused && !interruptionMuted) {
                val currentJob = currentCoroutineContext().job
                recoveryJob?.takeUnless { it === currentJob }?.cancel()
                if (recoveryJob !== currentJob) {
                    recoveryJob = null
                    admittedRecoveryEpoch = null
                }
                interruptionPaused = true
                player.pause()
                val hold = if (content == AudioInterruptionContent.LIVE_TIMESHIFT) coordinator.pauseTimeshift() else null
                if (hold != null && hold != TimeshiftCommandResult.ACCEPTED) {
                    // Do not leave an unconfirmed server hold filling the pushed source queues.
                    // Keep the timeshift content kind so a subsequent resume also releases a
                    // server hold whose acknowledgement may have been lost.
                    interruptionHoldUnconfirmed = hold.disposition == TimeshiftCommandDisposition.UNCONFIRMED
                    setInterruptionMuted(true)
                    interruptionPaused = false
                    player.play()
                }
            }
            AudioInterruptionAction.MUTE -> {
                setInterruptionMuted(true)
                // A denied initial request also needs to start video consumption.
                player.play()
            }
            else -> Unit
        }
    }

    private fun beginTargetPresentation(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
            targetFrameListener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (targetInstallationInProgress || !targetCommands.isOpen()) return
                    val notYetVisible = policy.trace.enabled && !_videoPresentation.value.visible
                    _videoPresentation.value = _videoPresentation.value.onFirstFrame(
                        frameEpoch = epoch,
                        activeTargetEpoch = activeTargetEpoch,
                    )
                    if (notYetVisible && epoch == activeTargetEpoch && _videoPresentation.value.visible) {
                        val live = livePlaybackObservation.value as? LivePlaybackObservation.Active
                        policy.trace.firstVideoFrame(epoch, player.videoFormat, live?.diagnostics?.source?.adapterName)
                    }
                }
            }.also(player::addListener)
        }
    }

    private fun endTargetPresentation(epoch: Long) {
        targetCommands.runIfOpen {
            targetFrameListener?.let(player::removeListener)
            targetFrameListener = null
            _videoPresentation.value = _videoPresentation.value.beginTarget(epoch)
        }
    }

    private fun configureAudioOutputBeforeFirstTarget(value: PlayerSettings) {
        targetCommands.runIfOpen {
            if (!audioOutputConfigured) {
                audioOutput.configurePassthrough(value.audioPassthroughEnabled)
                audioOutputConfigured = true
            }
        }
    }

    private suspend fun applyAudioPassthrough(enabled: Boolean) {
        if (!targetCommands.isOpen() || !audioOutputConfigured) return
        if (audioOutput.isPassthroughEnabled == enabled) {
            _audioPassthroughChangeFailed.value = false
            return
        }
        audioOutputChanging = true
        try {
            val applied = audioOutput.setPassthroughEnabled(player, enabled)
            if (targetCommands.isOpen()) _audioPassthroughChangeFailed.value = !applied
        } finally {
            audioOutputChanging = false
            // onTracksChanged restores the remembered choice using NEW mode capabilities.
            // currentTracks here can still describe the old mode and must not reapply an override.
        }
    }

    // A committed UI choice outlives the options sheet, including while another command holds the lock.
    fun useAutomaticAudio() = scope.launch {
        targetCommands.serialize(onClosed = {}) {
            audioSelection.useProfile(profileOwner.serverProfile.value, player)
            val channel = audioSelection.useAutomatic(player)
            val profileId = profileOwner.audioProfileId
            if (channel != null && profileId != null) {
                // A pending explicit-choice write must finish before its removal is persisted.
                val previous = audioWriteJob
                audioWriteJob = scope.launch {
                    previous?.join()
                    try {
                        settings.audioChoices.remove(profileId, channel)
                    } catch (_: java.io.IOException) {
                        // Storage failure must not interrupt playback; the session choice is still forgotten.
                    }
                }
            }
        }
    }

    private fun applyPlayerSettings(value: PlayerSettings) {
        targetCommands.runIfOpen {
            player.trackSelectionParameters = trackPreferences(
                player.trackSelectionParameters, value,
            )
            player.setVideoChangeFrameRateStrategy(
                if (value.refreshRateMatchingEnabled) {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                } else {
                    C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                },
            )
        }
    }

    private fun publishPlayerState(recoveryResolved: Boolean = false) {
        targetCommands.runIfOpen {
            if (player.playerError == null) {
                _state.value = if (recoveryResolved) {
                    playerStateAfterRecoveryResolution(
                        currentState = _state.value,
                        playbackState = player.playbackState,
                        isPlaying = player.isPlaying,
                    )
                } else {
                    playerReportedPlaybackState(
                        currentState = _state.value,
                        recoveryAttemptInProgress = recoveryAttempts.ownsPlayerState(
                            activeTarget = _activeTarget.value,
                            activeTargetEpoch = activeTargetEpoch,
                            observation = session.observation.value,
                        ),
                        playbackState = player.playbackState,
                        isPlaying = player.isPlaying,
                    )
                }
            }
            publishDiagnosticsFromPlayer()
        }
    }

    private fun publishResolvedRecoveryPlayerState(
        fence: LiveRecoveryFence,
        result: PlaybackTargetResult?,
    ) {
        if (shouldRepublishPlayerStateAfterRecovery(
                result = result,
                fence = fence,
                activeTarget = _activeTarget.value,
                activeTargetEpoch = activeTargetEpoch,
                observation = session.observation.value,
                healthyActiveTarget = healthyActiveTarget(),
            )
        ) {
            publishPlayerState(recoveryResolved = true)
        }
    }

    private fun publishInstalledPlayerState() {
        targetCommands.runIfOpen {
            if (player.playerError != null) {
                publishPlayerErrorFromPlayer()
                return@runIfOpen
            }
            _state.value = playerReportedPlaybackState(
                currentState = AppPlaybackState.Starting,
                recoveryAttemptInProgress = false,
                playbackState = player.playbackState,
                isPlaying = player.isPlaying,
            )
            publishDiagnosticsFromPlayer()
        }
    }

    private fun publishPlayerError() {
        targetCommands.runIfOpen { publishPlayerErrorFromPlayer() }
    }

    private fun publishPlayerErrorFromPlayer() {
        // A failed target never completes a pause pressed before its grant. A paused kept-channel
        // retune still has to fail closed, so its pending pause resolves (and stops) serialized.
        val pending = pendingLivePause
        if (pending?.failClosed == false) pendingLivePause = null
        _state.value = AppPlaybackState.Failed(
            reason = if (_activeTarget.value is AppPlaybackTarget.Recording) {
                AppPlaybackFailureReason.RECORDING_READ_FAILED
            } else {
                AppPlaybackFailureReason.OTHER
            },
            playerErrorCode = player.playerError?.errorCodeName,
            subscriptionIssue = lastSubscriptionIssue(),
        )
        publishLivePause()
        publishDiagnosticsFromPlayer()
        if (pending?.failClosed == true) launchPendingLivePauseResolution(pending.epoch)
    }

    private fun publishDiagnostics() {
        if (!diagnosticsEnabled) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
            return
        }
        if (!targetCommands.runIfOpen { publishDiagnosticsFromPlayer() }) {
            _diagnostics.value = AppPlaybackDiagnostics(source = source(), state = _state.value)
        }
    }

    private fun publishDiagnosticsFromPlayer() {
        val activeTarget = _activeTarget.value
        val activeLiveObservation =
            livePlaybackObservation.value as? LivePlaybackObservation.Active
        val video = player.videoFormat
        val audio = player.audioFormat
        _diagnostics.value = AppPlaybackDiagnostics(
            source = source(activeTarget),
            state = _state.value,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it >= 0L },
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
            video = video?.let {
                AppPlaybackFormatDiagnostics(
                    codec = it.codecs,
                    resolution = if (it.width > 0 && it.height > 0) "${it.width}×${it.height}" else null,
                    frameRate = it.frameRate.takeIf { rate -> rate > 0f },
                )
            },
            audio = audio?.let {
                AppPlaybackFormatDiagnostics(
                    codec = it.codecs,
                    language = it.language,
                    channelCount = it.channelCount.takeIf { count -> count > 0 },
                    sampleRateHz = it.sampleRate.takeIf { rate -> rate > 0 },
                )
            },
            live = liveDiagnosticsForTarget(
                activeTarget = activeTarget,
                diagnostics = activeLiveObservation?.diagnostics,
            ),
        )
    }

    private fun source(activeTarget: AppPlaybackTarget? = _activeTarget.value) = when (activeTarget) {
        is AppPlaybackTarget.Live -> AppPlaybackSource.LIVE_TV
        is AppPlaybackTarget.Recording -> AppPlaybackSource.RECORDING
        else -> AppPlaybackSource.NONE
    }
}
