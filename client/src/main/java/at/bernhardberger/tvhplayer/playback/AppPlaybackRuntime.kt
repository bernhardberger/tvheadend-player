@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
import at.bernhardberger.tvheadend.sdk.media3.PausedSeekConfirmation
import at.bernhardberger.tvheadend.sdk.media3.PlaybackRecoveryReason
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopOutcome
import at.bernhardberger.tvheadend.sdk.media3.PlaybackStopResult
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.RecordingPlaybackStart
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.TvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi
import at.bernhardberger.tvheadend.sdk.playback.LiveSubscriptionPriority
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.FrameRateSwitching
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

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
    private val startupBuffer: StartupBufferController? = null,
) {
    private val targetCommands = PlaybackTargetCommandSerialization()
    private val foregroundPlaybackLifecycle = ForegroundPlaybackLifecycle()
    private var keepTimer: Job? = null
    /**
     * The last server stop or issue seen for a live target, recorded as it is observed. A queued
     * check releases a kept target on it even if the flag has cleared meanwhile: a restart
     * discards the kept timeshift, and an ended subscription clears the flag as well.
     */
    @Volatile
    private var observedLiveLoss: ObservedLiveLoss? = null
    private val _backgroundNotice = MutableStateFlow<BackgroundPlaybackNotice?>(null)
    val backgroundNotice = _backgroundNotice.asStateFlow()
    /**
     * The viewing-intent generation, bumped synchronously before any command waits for the
     * serializer. Intent is: a live or recording start ([playLive] without an intent of its
     * own, [playRecording]), a retry ([retryLive], including the live screen's automatic
     * retry after a reconnect, and [retryRecording]), a channel the live screen accepted
     * ([notePlaybackIntent], carried to its delayed [playLive]), a launch request (appliance
     * entry or startup autoplay, [notePlaybackIntent]), a warm opening action that starts
     * nothing (a click on the channel already playing, noted at the click and carried to
     * [playLive]; a warm return; both [notePlaybackIntent]), and opening a player screen
     * unless a user stop is the latest playback event ([enterPlayerScreen]). Not intent:
     * recovery, [restoreRecordingRoute], and automatic stops ([stopAfterLoss]).
     */
    private val playbackRequests = AtomicLong()

    /**
     * The newest intent generation the installed live target serves: the one it was started
     * or retried under (recorded when that target commits, not when its start is admitted: a
     * start cancelled before it commits leaves the previous target with its own intent), or a
     * later warm entry that found it already playing
     * ([notePlaybackIntentServed]). Returning from the background resumes a channel only
     * while no newer intent exists; a channel the viewer zapped away from before leaving
     * is not tuned again.
     */
    private val servedIntent = AtomicLong()

    /**
     * Guards [userStopIntent] and the pending session Stops against [enterPlayerScreen] and
     * the session Stop's check.
     */
    private val intentLock = Any()

    /**
     * The intent generation under which the latest user stop was asked for ([stop],
     * [stopFromSession]); -1 before any. A user stop is the latest playback event while this
     * equals [playbackRequests].
     */
    private var userStopIntent = -1L

    /**
     * Session Stops that arrived but have not run their check yet: the generation of the
     * latest arrivals and how many arrived under it. Arrivals under an older generation
     * are already stale (newer intent wins over them), so only the latest one counts.
     */
    private var pendingStopIntent = -1L
    private var pendingStops = 0

    /**
     * The live channel the viewer accepted last, until its start commits or ends
     * ([noteLiveSelection], [playLive]): its intent generation (-1 for none) and whether the
     * viewer paused it meanwhile. It counts only while it is the latest intent and no user stop
     * came under it or later, so a newer selection or Stop replaces it. While it counts, Pause
     * and Play address it and never the target still installed. Guarded by [intentLock].
     */
    private var pendingLiveSelection = -1L
    private var pendingLiveSelectionPaused = false

    /** The generation the latest completed session Stop arrived under; -1 before any. */
    private val lastSessionStop = MutableStateFlow(-1L)

    /**
     * The latest media-session Stop that tore down its target, delivered while no user
     * viewing intent was noted after that Stop arrived: later intent wins over an earlier
     * Stop. The runtime has already stopped, so a showing player screen only closes. The
     * latest Stop is kept, so a screen whose entry the Stop was pending against still closes
     * when it starts collecting after the Stop ran; any screen entered or tuned later has
     * noted newer intent and ignores it. Browse screens do not collect.
     */
    val sessionStops: Flow<Unit> = lastSessionStop.filter { it >= 0 && it == playbackRequests.get() }.map { }

    fun consumeBackgroundNotice(notice: BackgroundPlaybackNotice) {
        _backgroundNotice.compareAndSet(notice, null)
    }

    fun consumeLivePauseNotice(notice: LivePauseUnavailableNotice) {
        livePauseController.consumeLivePauseNotice(notice)
    }
    private val audioSelection = SessionAudioSelection()
    private val presentationEpoch = PlaybackPresentationEpoch()
    private val _activeTarget = MutableStateFlow<AppPlaybackTarget?>(null)
    // The presentation publisher is the only writer of [state]; it (and the recovery attempts it
    // reads) is built before the collaborators that read that state.
    private val liveRecovery: LiveRecoveryController = LiveRecoveryController(
        scope = scope,
        targetCommands = targetCommands,
        publishResolvedRecoveryPlayerState = ::publishResolvedRecoveryPlayerState,
    )
    private val presentation = PlaybackPresentationPublisher(
        player = player,
        session = session,
        policy = policy,
        startupBuffer = startupBuffer,
        targetCommands = targetCommands,
        recoveryAttempts = liveRecovery.recoveryAttempts,
        livePlaybackObservation = coordinator.livePlaybackObservation,
        _activeTarget = _activeTarget,
        activeTargetEpoch = { activeTargetEpoch },
        targetInstallationInProgress = { targetInstallationInProgress },
        publishPlayerErrorFromPlayer = ::publishPlayerErrorFromPlayer,
        elapsedRealtime = elapsedRealtime,
        stallRecoveries = { stallRecoveries },
        freshDecoderPerZap = { freshDecoderPerZap },
    )
    private val livePauseController = LivePauseController(
        player = player,
        coordinator = coordinator,
        scope = scope,
        targetCommands = targetCommands,
        livePlaybackObservation = coordinator.livePlaybackObservation,
        _state = presentation.state,
        _activeTarget = _activeTarget,
        activeTargetEpoch = { activeTargetEpoch },
        foreground = { foreground },
        interruption = { audioInterruptions.interruption },
        stopPausedRetune = ::stopPausedRetune,
        playStartedTarget = { audioInterruptions.playWithAudioFocusLocked() },
        resumeOwedRecovery = { resumeInterruptedLiveRecoveryLocked() },
        pendingLiveSelectionPlayWhenReady = ::pendingLiveSelectionPlayWhenReady,
        pausedSeekRefresh = { pausedSeekNudging },
    )
    val livePause = livePauseController.livePause
    val livePauseNotice = livePauseController.livePauseNotice
    val frameRate = PlaybackFrameRate()
    private val audioTracks = PlaybackAudioTracks(
        player = player,
        settings = settings,
        profileOwner = profileOwner,
        scope = scope,
        audioOutput = audioOutput,
        targetCommands = targetCommands,
        audioSelection = audioSelection,
        _activeTarget = _activeTarget,
        targetInstallationInProgress = { targetInstallationInProgress },
        interruptionMuted = { audioInterruptions.interruptionMuted },
        preserveInterruptionMute = { audioInterruptions.preserveInterruptionMute() },
        frameRate = frameRate,
    )
    val audioPassthroughChangeFailed = audioTracks.audioPassthroughChangeFailed
    val audioAutomatic = audioTracks.audioAutomatic
    private val _recordingSelection = MutableStateFlow<RecordingPlaybackSelection?>(null)
    private val _recordingAdmission = MutableStateFlow<RecordingPlaybackAdmission?>(null)
    private val recordingMarkers = RecordingMarkers(
        player = player,
        session = session,
        scope = scope,
        targetCommands = targetCommands,
        targetInstallationInProgress = { targetInstallationInProgress },
    )
    val recordingCutpoints = recordingMarkers.recordingCutpoints
    val recordingMarkerRevision = recordingMarkers.recordingMarkerRevision
    @Volatile
    private var activeTargetEpoch: Long? = null
    @Volatile
    private var activeLiveSelection: LivePlaybackSelection? = null
    /** The profile configuration [activeLiveSelection] was tuned under; a reconnect retunes only within it. */
    private var activeLiveProfileGeneration = -1L
    /** The target whose reconnect retune failed: its recovery attempts retry until one starts or the budget runs out. */
    private var reconnectRecoveryEpoch: Long? = null
    /**
     * The epoch of the target the active one continues: a live recovery retune of the same
     * channel keeps it, every other commit begins its own. Written after [activeTargetEpoch], so a
     * reader that sees a new value also sees that commit's epoch.
     */
    @Volatile
    private var targetContinuity: Long? = null
    @Volatile
    private var targetInstallationInProgress = false
    /** Nonempty current-item replacement witnesses; removing the predecessor is not a new source. */
    @Volatile
    private var playerSources = 0L
    /** The live install in progress, for the escalations its new source already reports. */
    @Volatile
    private var liveInstall: LiveInstallAttribution? = null

    /** Exact installed item, never a media-id match against an application replacement. */
    @Volatile
    private var ownedMediaItem: MediaItem? = null
    private var departedEpoch: Long? = null
    private var bufferRecovery: LiveRecoveryFence? = null
    @Volatile
    private var exhaustedRecoveryEpoch: Long? = null
    @Volatile
    private var exhaustedRecoveryInstall: LiveInstallAttribution? = null
    /** Presentation fence for a coordinator stop retry, never Player cleanup identity. */
    private data class LiveRetirementRetry(
        val epoch: Long?,
        val install: LiveInstallAttribution?,
    )
    @Volatile
    private var liveRetirement: LiveRetirementRetry? = null

    /**
     * [sourceGeneration]: [playerSources] when the install began; [selection]: the session-bound
     * selection it installs; [committedEpoch]: its commit, if it commits.
     */
    private class LiveInstallAttribution(val sourceGeneration: Long, val selection: LivePlaybackSelection) {
        @Volatile
        var committedEpoch: Long? = null
    }
    private var lastLiveChannelId: ChannelId? = null
    private var lastRecordingRequest: Pair<DvrEntryId, RecordingPlaybackStart>? = null
    // Where the failed or lost recording target last stood; only Retry and route restoration read it.
    private var recordingRecovery: RecordingRecoveryPosition? = null
    /** Written under the command serializer, read at arrival by the transport commands. */
    @Volatile
    private var foreground = true
    // The stall watchdog samples on the player's thread; its recovery runs as a serialized command.
    private val stallHandler = Handler(player.applicationLooper)
    private val stallWatch = PlaybackStallWatch()
    private var stallPolling = false
    @Volatile
    private var stallRecoveryQueued = false
    /** The last automatic stall recovery, its budget. Read and written under the serializer. */
    private var lastStallRecovery: StallRecovery? = null
    /** The stall recovery whose install retires the stuck target first, so its decoders are fresh. */
    private var freshDecoderRecovery: Any? = null
    /** The target epoch whose source [stopForFreshDecoder] retired during the install in progress. */
    private var retiredTargetEpoch: Long? = null
    /** Whether a recovery, not a viewer's zap or retry, retired [retiredTargetEpoch]. */
    private var retiredForRecovery = false
    @Volatile
    private var stallRecoveries = 0
    /**
     * A live stall recovered once: this device's decoders do not survive a live source switch,
     * so for the rest of the process every live zap stops the old target first. Memory only and
     * off by default, so devices that never stall keep the fast zap.
     */
    @Volatile
    private var freshDecoderPerZap = false
    /** A paused seek's picture refresh plays the player ([nudgePausedSeekLocked]); written under the serializer. */
    @Volatile
    private var pausedSeekNudging = false
    private val audioInterruptions: AudioInterruptionController = AudioInterruptionController(
        player = player,
        coordinator = coordinator,
        scope = scope,
        targetCommands = targetCommands,
        audioFocus = audioFocus,
        audioTracks = audioTracks,
        livePauseController = livePauseController,
        livePlaybackObservation = coordinator.livePlaybackObservation,
        _activeTarget = _activeTarget,
        foreground = { foreground },
        activeTargetEpoch = { activeTargetEpoch },
        cancelRecoveryForInterruptionLocked = liveRecovery::cancelOnInterruptionLocked,
        resumeInterruptedRecoveryLocked = ::resumeInterruptedLiveRecoveryLocked,
        holdPlayingPicture = { presentation.holdPlayingPictureLocked(it) },
    )
    val isInterruptionMuted: Boolean get() = audioInterruptions.interruptionMuted
    val hasAudioInterruption: Boolean get() = audioInterruptions.interruption != null
    val state = presentation.state
    val activeTarget = _activeTarget.asStateFlow()
    val recordingSelection = _recordingSelection.asStateFlow()
    val recordingAdmission = _recordingAdmission.asStateFlow()
    val livePlaybackObservation = coordinator.livePlaybackObservation
    val diagnostics = presentation.diagnostics
    val videoPresentation = presentation.videoPresentation

    private val settingsJob = scope.launch {
        settings.playerSettings.distinctUntilChanged().collect {
            targetCommands.serialize(onClosed = {}) {
                val latest = settings.playerSettings.first()
                audioTracks.applyPlayerSettingsLocked(latest)
                audioTracks.applyAudioPassthroughLocked(latest.audioPassthroughEnabled)
            }
        }
    }

    @OptIn(ExperimentalTvheadendApi::class)
    private val declaredVideoFrameDuration = coordinator.declaredVideoFrameDuration

    /** The server's declared frame duration is the display rate request's only source. */
    private val declaredFrameDurationJob = scope.launch {
        declaredVideoFrameDuration.collect(frameRate::onDeclared)
    }

    private val livePlaybackObservationJob = scope.launch {
        livePlaybackObservation.collect { observation ->
            val observedEpoch = activeTargetEpoch
            if (observation is LivePlaybackObservation.Active && observedEpoch != null) {
                observation.keptLiveLossNotice()?.let { observedLiveLoss = ObservedLiveLoss(observedEpoch, it) }
            }
            // Observations arrive several times a second: they must not end a paused seek's refresh.
            targetCommands.serialize(onClosed = {}, countsAsArrival = false) {
                if (livePlaybackObservation.value == LivePlaybackObservation.Departed) {
                    withdrawDepartedTargetLocked()
                    return@serialize
                }
                // The recorded stop releases the keep even if the server restarted the stream since.
                if (observedEpoch == activeTargetEpoch && foregroundPlaybackLifecycle.isKeeping(observedEpoch)) {
                    keptLiveLoss(observedEpoch)?.let { notice ->
                        applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(observedEpoch, notice))
                        return@serialize
                    }
                }
                // Before the play intent: an unresolved local pause is not a server resume, and a
                // just-resolved one keeps its own outcome (an ambiguous result stays paused).
                val resolvedPause = livePauseController.resolvePendingLivePauseLocked()
                livePauseController.publishLivePause()
                val active = livePlaybackObservation.value as? LivePlaybackObservation.Active
                val timeshift = active?.timeshiftState as? LiveTimeshiftState.Available
                if (foreground && !targetInstallationInProgress && !audioInterruptions.interruptionPaused && !audioInterruptions.interruptionMuted &&
                    !resolvedPause && livePauseController.pendingLivePauseEpoch != activeTargetEpoch
                ) {
                    observedLivePlayIntent(
                        activeTarget = _activeTarget.value,
                        serverPaused = timeshift?.playbackPaused,
                    )?.let { play ->
                        val resumed = play && !player.playWhenReady
                        player.playWhenReady = play
                        // Playing again without the viewer's Play: a recovery its pause held goes on.
                        if (resumed) resumeInterruptedLiveRecoveryLocked()
                    }
                }
                presentation.publishDiagnostics()
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            stallWatch.reset()
            watchForStall()
            resumeRecoveryOnPlayerChange()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            stallWatch.reset()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            resumeRecoveryOnPlayerChange()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (livePlaybackObservation.value != LivePlaybackObservation.Departed) audioTracks.onMediaItemTransition()
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            if (livePlaybackObservation.value != LivePlaybackObservation.Departed) audioTracks.onTrackSelectionParametersChanged(parameters)
        }

        override fun onTracksChanged(tracks: Tracks) {
            // Known tracks without video (radio): nothing to request. Empty tracks during a zap keep the rate.
            if (tracks.groups.isNotEmpty() && !tracks.containsType(androidx.media3.common.C.TRACK_TYPE_VIDEO)) {
                frameRate.reset()
            } else if (tracks.containsType(androidx.media3.common.C.TRACK_TYPE_VIDEO)) {
                // Video again after a reset: the declaration may be unchanged, so the flow says nothing.
                frameRate.onDeclared(declaredVideoFrameDuration.value)
            }
            if (livePlaybackObservation.value != LivePlaybackObservation.Departed) audioTracks.onTracksChanged()
            stallWatch.reset()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            watchForStall()
            if (!targetInstallationInProgress && !coordinator.playerCleanupPending.value && targetCommands.isOpen()) {
                if (policy.trace.enabled && playbackState == Player.STATE_READY) {
                    policy.trace.ready(activeTargetEpoch)
                }
                presentation.publishPlayerState()
                if (playbackState == Player.STATE_READY) livePauseController.onTargetReady(activeTargetEpoch)
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                val currentItem = player.currentMediaItem
                if (currentItem != null && currentItem !== ownedMediaItem) playerSources++
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            watchForStall()
            if (!targetInstallationInProgress && !coordinator.playerCleanupPending.value && targetCommands.isOpen()) {
                presentation.publishPlayerState()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) publishPlayerError()
        }
    }

    /** Samples while Media3 has a target to play; player events start it again after a pause. */
    private val stallPoll = object : Runnable {
        override fun run() {
            stallPolling = false
            val sample = targetCommands.readIfOpen { stallSampleLocked() }
            if (sample == null) stallWatch.reset()
            else stallWatch.onSample(sample, SystemClock.uptimeMillis())?.let { recoverStall(it, sample.epoch) }
            watchForStall()
        }
    }

    init {
        targetCommands.runIfOpen {
            player.addListener(listener)
            // The app votes on the video surface itself (PlayerVideoSurface); Media3 never does.
            player.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            player.setVideoFrameMetadataListener(frameRate.listener)
            presentation.seekDiagnosticsListener?.let(player::addAnalyticsListener)
        }
    }

    /**
     * Starts [selection]. Without [intent] this call is new viewing intent. With the intent
     * the live screen noted when it accepted the channel ([notePlaybackIntent]) it adds none,
     * and returns null without touching playback when a user stop was asked for under that
     * intent or later (see [isPlaybackIntentStopped]): the viewer's Stop came last.
     * While it is the latest intent the selection is pending from this call on (unless
     * [noteLiveSelection] registered it earlier): a Pause meanwhile belongs to it. A call
     * cancelled or shut down before its start ended the selection abandons it
     * ([abandonLiveSelection]); a restart registers it again.
     */
    suspend fun playLive(selection: LivePlaybackSelection, intent: Long? = null): PlaybackTargetResult? {
        val generation = intent ?: playbackRequests.incrementAndGet()
        noteLiveSelection(generation)
        try {
            return startLiveSelection(selection, intent, generation)
        } finally {
            // Nothing left if the start committed, failed or was withdrawn: they ended it.
            abandonLiveSelection(generation)
        }
    }

    /** The installed live tune's presentation, not independently consumed UI state/track snapshots. */
    fun liveTargetPresentation(selection: LivePlaybackSelection, intent: Long? = null): AppLiveTargetPresentation? =
        targetCommands.readIfOpen {
            val frame = videoPresentation.value
            if (installedLiveTargetEpochLocked(selection, intent) != frame.epoch ||
                session.observation.value.currentSession !== selection.currentSession ||
                intent != null && (intent != playbackRequests.get() || isPlaybackIntentStopped(intent))
            ) return@readIfOpen null
            val tracks = player.currentTracks
            AppLiveTargetPresentation(
                epoch = frame.epoch,
                visible = frame.visible,
                playing = state.value is AppPlaybackState.Playing && player.isPlaying && player.playerError == null,
                audioOnly = tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected } &&
                    tracks.groups.none { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO },
            )
        }

    /** Installation receipt for cleanup, even if the profile or a newer intent withdrew visibility. */
    fun installedLiveTargetEpoch(selection: LivePlaybackSelection, intent: Long): Long? =
        targetCommands.readIfOpen { installedLiveTargetEpochLocked(selection, intent) }

    private fun installedLiveTargetEpochLocked(selection: LivePlaybackSelection, intent: Long?): Long? =
        activeTargetEpoch?.takeIf {
            !targetInstallationInProgress &&
                _activeTarget.value == AppPlaybackTarget.Live(selection.channelId) &&
                activeLiveSelection?.channelId == selection.channelId &&
                activeLiveSelection?.currentSession === selection.currentSession &&
                (intent == null || intent == servedIntent.get())
        }

    private suspend fun startLiveSelection(
        selection: LivePlaybackSelection,
        intent: Long?,
        generation: Long,
    ): PlaybackTargetResult? =
        serializeViewerStart(generation, onClosed = { PlaybackTargetResult.SHUT_DOWN }) {
            if (intent != null && isPlaybackIntentStopped(intent)) {
                endLiveSelection(generation)
                return@serializeViewerStart null
            }
            if (policy.trace.enabled) policy.trace.tuneAdmitted()
            lastLiveChannelId = selection.channelId
            forgetRecordingRecoveryLocked()
            liveRecovery.resetBackoffLocked()
            val startEpoch = presentationEpoch.snapshot()
            val result = try {
                playLive(
                    channelId = selection.channelId,
                    recovering = false,
                    expectedPresentationEpoch = startEpoch,
                    servedGeneration = generation,
                )
            } finally {
                settleCancelledStartLocked(startEpoch, generation)
            }
            // A commit ended the selection. Otherwise the start failed while the previous live
            // target stayed: a Pause the viewer gave meanwhile is still the latest transport
            // intent, so it applies to that retained target.
            if (endLiveSelection(generation)) pauseRetainedLiveTargetLocked()
            if (result?.isStarted != true && result != PlaybackTargetResult.DEPARTED &&
                generation == playbackRequests.get() && !isPlaybackIntentStopped(generation) &&
                healthyActiveTarget() is AppPlaybackTarget.Live &&
                livePlaybackObservation.value is LivePlaybackObservation.Active
            ) {
                // The completed selection fell back to the retained target. Old queued reports
                // keep their old intent; fresh reports now belong to this completed selection.
                servedIntent.set(generation)
            }
            result
        }

    /**
     * Applies a Pause held for a selection that ended without installing a target of its own
     * to the live target that plays instead. A rejection restores its play state and shows
     * [livePauseNotice], as a dropped pending pause does; an ambiguous result stays paused.
     */
    private suspend fun pauseRetainedLiveTargetLocked() {
        if (!foreground || activeTargetEpoch == null || _activeTarget.value !is AppPlaybackTarget.Live) return
        val result = pauseTimeshiftPlaybackLocked()
        if (result?.disposition == TimeshiftCommandDisposition.NOT_ACCEPTED && foreground &&
            targetCommands.isOpen()
        ) {
            livePauseController.noticeLivePauseUnavailable()
        }
    }

    private suspend fun playLive(
        channelId: ChannelId,
        recovering: Boolean,
        expectedPresentationEpoch: Long = presentationEpoch.snapshot(),
        recoverySelection: LivePlaybackSelection? = null,
        playWhenReady: Boolean = true,
        // The intent a start or retry installs for; the committed target serves it (servedIntent).
        servedGeneration: Long? = null,
        // The viewer's Retry (see retryLive): a fresh start, not recovery, for presentation and audio.
        viewerRetry: Boolean = false,
        recoveryFence: LiveRecoveryFence? = null,
        // retuneAfterReconnect: the profile generation it admitted, which must hold until install.
        reconnectGeneration: Long? = null,
    ): PlaybackTargetResult? {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        val recoveryIntent = playbackRequests.get()
        val reconnectAdmitted = { reconnectGeneration == null || profileStillCurrent(reconnectGeneration) }
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (!recovering && _activeTarget.value == null || viewerRetry && healthyActiveTarget() == null) {
                presentation.publishStarting()
            }
        }
        val playerSettings = settings.playerSettings.first()
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        if (recoveryFence != null && !canContinueRecoveryLocked(recoveryFence)) return null
        // An explicit first request configures persisted output even when its target is
        // unavailable. Recovery defers all Player configuration until its reads are rechecked.
        if (recoveryFence == null) audioTracks.configureAudioOutputBeforeFirstTargetLocked(playerSettings)
        // A server save counts its generation before it replaces the session: until the profile is
        // applied again, the session read below may still be the previous server's.
        val profileGeneration = profileOwner.configurationGeneration.value
            .takeIf { profileOwner.serverProfile.value != null } ?: -1L
        if (!reconnectAdmitted()) return null
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
        val audioResult = audioTracks.loadStoredAudioLocked(channelId) {
            recoveryFence == null || canContinueRecoveryLocked(recoveryFence)
        }
        if (recoveryFence != null && !canContinueRecoveryLocked(recoveryFence)) return null
        audioResult?.let { return it }
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
        val retainInterruptionMute = recovering && !viewerRetry && audioInterruptions.interruptionMuted
        if (recoveryFence != null) audioTracks.configureAudioOutputBeforeFirstTargetLocked(playerSettings)
        val timeshiftPeriod = policy.liveTimeshiftPeriod(playerSettings)
        var committed = false
        // The viewer paused this channel before it started: that Pause replaces the startup Play.
        var heldPause = false
        // The load control must know the target kind before the source is prepared.
        startupBuffer?.targetInstalling(live = true)
        // Read before the SDK can replace the player's source for this install.
        val install = LiveInstallAttribution(sourceGeneration = playerSources, selection = selection)
        liveInstall = install
        val result = try { installTargetForPresentation(
            expectedPresentationEpoch = expectedPresentationEpoch,
            canInstall = {
                reconnectAdmitted() && (recoveryFence == null || canContinueRecoveryLocked(recoveryFence))
            },
            installTarget = {
                if (!targetCommands.isOpen()) {
                    PlaybackTargetResult.SHUT_DOWN
                } else if (!reconnectAdmitted()) {
                    PlaybackTargetResult.NOT_READY
                } else if (recoverySelection != null &&
                    (recoveryIntent != playbackRequests.get() || isPlaybackIntentStopped(recoveryIntent) ||
                        livePlaybackObservation.value == LivePlaybackObservation.Departed)
                ) {
                    PlaybackTargetResult.NOT_READY
                } else {
                    stopOtherKindBeforeInstall(live = true, expectedPresentationEpoch)
                    stopForFreshDecoder(recoveryFence, liveZap = true)
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
                        audioInterruptions.retainInterruptionMuteLocked()
                    } else audioInterruptions.clearAudioInterruptionLocked()
                    // A recovery retune replaces the very target its fence validated in this
                    // command, a reconnect retune the very target retuneAfterReconnect validated.
                    val continuesTarget = (recoverySelection != null || reconnectGeneration != null) &&
                        activeTargetEpoch == expectedPresentationEpoch &&
                        _activeTarget.value == AppPlaybackTarget.Live(selection.channelId)
                    activeTargetEpoch = epoch
                    activeLiveSelection = selection
                    activeLiveProfileGeneration = profileGeneration
                    // Whichever target this is, its epoch is new: no reconnect retry owns it.
                    reconnectRecoveryEpoch = null
                    if (!continuesTarget) targetContinuity = epoch
                    servedGeneration?.let(servedIntent::set)
                    livePauseController.dropPendingLivePauseLocked()
                    // From here on Pause and Play address this target itself.
                    heldPause = servedGeneration != null && endLiveSelection(servedGeneration)
                    livePauseController.noteTimeshiftRequestedLocked(epoch.takeIf { timeshiftPeriod > Duration.ZERO })
                    _activeTarget.value = AppPlaybackTarget.Live(selection.channelId)
                    install.committedEpoch = epoch
                    lastLiveChannelId = selection.channelId
                    _recordingSelection.value = null
                    _recordingAdmission.value = null
                    recordingMarkers.clearRecordingMarkers()
                    presentation.publishStarting()
                    if (policy.trace.enabled) policy.trace.tuneBound(epoch)
                    presentation.beginTargetPresentationLocked(epoch)
                    presentation.publishInstalledPlayerStateLocked()
                    livePauseController.publishLivePause()
                }
            },
            onFailed = { targetResult ->
                publishTargetFailure(
                    expectedPresentationEpoch = expectedPresentationEpoch,
                    reason = AppPlaybackFailureReason.OTHER,
                    targetResult = targetResult,
                )
            }
        ) } finally {
            if (liveInstall === install) liveInstall = null
        }
        if (committed) {
            // STATE_READY reported while installation was in progress was not observed.
            if (player.playbackState == Player.STATE_READY) livePauseController.onTargetReady(activeTargetEpoch)
            // A start cut short leaves the install's stopped player: only a start meant to play
            // still owes its play.
            val plays = foreground && (retainInterruptionMute || (playWhenReady && !heldPause))
            var applied = false
            try {
                if (retainInterruptionMute && foreground) player.play()
                else applyPlayIntentToStartedTarget(result, playWhenReady, heldPause = heldPause)
                applied = true
            } finally {
                presentation.presentStartedTargetLocked(install.committedEpoch, playStillPending = plays && !applied)
            }
            // Only a start the viewer is waiting for opens a start-up buffer window.
            startupBuffer?.liveStartApplied()
        }
        return result
    }

    suspend fun playRecording(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? {
        val request = playbackRequests.incrementAndGet()
        return serializeViewerStart(request, onClosed = { PlaybackTargetResult.SHUT_DOWN }) {
            // The viewer's start choice applies to a new play; no earlier position carries over.
            forgetRecordingRecoveryLocked()
            val startEpoch = presentationEpoch.snapshot()
            try {
                playRecordingLocked(selection.recordingId, start)
            } finally {
                settleCancelledStartLocked(startEpoch, request)
            }
        }
    }

    /**
     * Settles a viewer start cancelled before it committed, so neither its Starting and cover
     * nor a source it or the stopped other kind left stays behind: it ends as [stopPlayback]
     * does, Idle without a target, which is no user stop. A start superseded by a newer request
     * or presentation leaves the presentation to that one and shows no failure, but the SDK
     * still retires the source no app target owns, so the newer install gets fresh decoders.
     */
    private suspend fun settleCancelledStartLocked(startEpoch: Long, request: Long) {
        if (currentCoroutineContext().isActive || !targetCommands.isOpen() || _activeTarget.value != null) return
        withContext(NonCancellable) {
            if (presentationEpoch.isCurrent(startEpoch) && request == playbackRequests.get()) {
                stopPlayback()
            } else {
                // As during an install, the retired source's IDLE is no state of the newer request.
                targetInstallationInProgress = true
                try {
                    coordinator.stop()
                } finally {
                    targetInstallationInProgress = false
                }
            }
        }
    }

    /**
     * Runs viewer start [request]'s [command] under the serializer. A start cancelled while it
     * still waited there is owed nothing itself, but as the latest request it inherits the
     * Starting an earlier superseded start left without a target, and settles that as
     * [settleCancelledStartLocked] would have. That runs later in the runtime's scope, so the
     * cancelled caller never waits for the serializer.
     */
    private suspend fun <T> serializeViewerStart(request: Long, onClosed: () -> T, command: suspend () -> T): T {
        var admitted = false
        try {
            return targetCommands.serialize(onClosed) {
                admitted = true
                command()
            }
        } catch (cancelled: CancellationException) {
            if (!admitted) scope.launch {
                targetCommands.serialize(onClosed = {}) {
                    if (_activeTarget.value == null && state.value == AppPlaybackState.Starting &&
                        request == playbackRequests.get()
                    ) stopPlayback()
                }
            }
            throw cancelled
        }
    }

    /**
     * Notes viewing intent that has no command yet (a channel the live screen accepted
     * before its zap settles, a launch request whose live player starts the
     * channel itself) and returns its generation for [playLive]. A
     * media-session Stop that arrived earlier then loses: it neither stops nor closes.
     */
    fun notePlaybackIntent(): Long = playbackRequests.incrementAndGet()

    private fun pendingLiveSelectionLocked(): Long? =
        pendingLiveSelection.takeIf { it >= 0 && it == playbackRequests.get() && userStopIntent < it }

    /** Null without a pending live selection, else whether the viewer wants it to play. */
    private fun pendingLiveSelectionPlayWhenReady(): Boolean? = synchronized(intentLock) {
        pendingLiveSelectionLocked()?.let { !pendingLiveSelectionPaused }
    }

    /**
     * Registers the live channel the screen accepted under [generation], the intent it just
     * noted ([notePlaybackIntent]) and will start with [playLive], at once or once a CH+/-
     * burst settles. While [generation] is the latest intent and no user stop came, the channel
     * is the pending selection: a Pause meanwhile is held for it, never sent to the target still
     * installed, and replaces its startup Play.
     */
    fun noteLiveSelection(generation: Long) {
        synchronized(intentLock) {
            if (pendingLiveSelection == generation || generation != playbackRequests.get() ||
                userStopIntent >= generation
            ) return
            pendingLiveSelection = generation
            pendingLiveSelectionPaused = false
        }
        livePauseController.publishLivePause()
    }

    /** Records Pause or Play for the pending live selection; false when there is none. */
    private fun setPendingLiveSelectionPaused(paused: Boolean): Boolean {
        if (!targetCommands.isOpen()) return false
        synchronized(intentLock) {
            pendingLiveSelectionLocked() ?: return false
            pendingLiveSelectionPaused = paused
        }
        livePauseController.publishLivePause()
        return true
    }

    /** Drops a Pause held for the pending live selection; the selection itself stays. */
    private fun dropHeldLiveSelectionPause() {
        synchronized(intentLock) {
            if (!pendingLiveSelectionPaused) return
            pendingLiveSelectionPaused = false
        }
        livePauseController.publishLivePause()
    }

    /**
     * The live screen gave up [generation]'s start before it ended: its settling or start was
     * cancelled because the screen stopped or went away, or the session no longer authorizes
     * the channel. The selection stops counting and a Pause held for it goes with it, so later
     * Pause and Play address what actually plays; a restarted start registers it again. No-op
     * for any other generation.
     */
    fun abandonLiveSelection(generation: Long) {
        endLiveSelection(generation)
    }

    /** Ends [generation]'s pending selection; returns whether it still counted and was paused. */
    private fun endLiveSelection(generation: Long): Boolean {
        val paused = synchronized(intentLock) {
            if (pendingLiveSelection != generation) return false
            val paused = pendingLiveSelectionPaused && pendingLiveSelectionLocked() != null
            pendingLiveSelection = -1L
            pendingLiveSelectionPaused = false
            paused
        }
        livePauseController.publishLivePause()
        return paused
    }

    /**
     * The live screen found the channel of [intent] already playing and adopted it without a
     * command: the installed target now serves that intent too, so returning from the
     * background still resumes it. That completes the intent's pending selection: a Pause the
     * viewer gave for it meanwhile applies to the adopted target.
     */
    fun notePlaybackIntentServed(intent: Long) {
        servedIntent.accumulateAndGet(intent, ::maxOf)
        if (!endLiveSelection(intent)) return
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (intent == playbackRequests.get() && !isPlaybackIntentStopped(intent)) pauseRetainedLiveTargetLocked()
            }
        }
    }

    /**
     * A player screen's first step, before any of its playback, retune or restore work.
     * Returns null when a user stop is the latest playback event since the last intent (the
     * viewer stopped after asking for this screen's target, so the screen closes and starts
     * nothing). While a session Stop that arrived under the current generation is still
     * pending, returns that generation without noting intent: the Stop is newer than
     * whatever opened the screen, so it still runs, withdraws this entry's start or restore
     * and closes the screen; if it is skipped instead (detached, backgrounded, nothing
     * active), the entry plays. Otherwise notes opening the screen as intent and returns
     * its generation.
     */
    fun enterPlayerScreen(): Long? = synchronized(intentLock) {
        val current = playbackRequests.get()
        when {
            userStopIntent == current -> null
            pendingStops > 0 && pendingStopIntent == current -> current
            else -> playbackRequests.incrementAndGet()
        }
    }

    /** Whether a user stop was asked for under [intent] or a later generation. */
    fun isPlaybackIntentStopped(intent: Long): Boolean = synchronized(intentLock) { userStopIntent >= intent }

    private fun userStopIsLatest(): Boolean = synchronized(intentLock) {
        userStopIntent == playbackRequests.get()
    }

    /**
     * Automatic: restores a recording route's target (first showing, new server session).
     * It is not viewing intent, and it does nothing while a user stop is the latest playback
     * event since the last intent, so it never resurrects a target the viewer stopped.
     * Automatic stops ([stopAfterLoss]) do not block it.
     */
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
            if (userStopIsLatest()) {
                null
            } else {
                noteRecordingInterruptionLocked()
                playRecordingLocked(selection.recordingId, start, recoverAt = recordingRecovery)
            }
        },
    )

    /**
     * Remembers where the recording target being replaced by recovery stands, read from the
     * player before the replacement retires it. A target still owed its recovery seek stands
     * where that seek would take it, not where the player waits. Without a recording target
     * (a failed retry already removed it, or an automatic stop did) the earlier note stays.
     */
    @OptIn(ExperimentalTvheadendApi::class)
    private fun noteRecordingInterruptionLocked() {
        val target = _activeTarget.value as? AppPlaybackTarget.Recording ?: return
        recordingRecovery = targetCommands.readIfOpen {
            val pendingPosition = coordinator.pendingRecordingRecoveryPosition.value
            val previous = recordingRecovery?.takeIf { it.recordingId == target.recordingId }
            RecordingRecoveryPosition(
                recordingId = target.recordingId,
                positionMs = pendingPosition?.inWholeMilliseconds
                    ?: player.currentPosition.coerceAtLeast(0L),
                // A growing recording's extent is no final duration to judge a finish by.
                // An owed seek keeps the original duration, including an unknown one.
                durationMs = if (pendingPosition != null && previous != null) {
                    previous.durationMs
                } else {
                    player.duration.takeIf {
                        _recordingAdmission.value is RecordingPlaybackAdmission.Completed &&
                            it != C.TIME_UNSET && it > 0
                    }
                },
            )
        } ?: recordingRecovery
    }

    /** A new play, live, or Stop: nothing from an earlier recording target carries over. */
    private fun forgetRecordingRecoveryLocked() {
        recordingRecovery = null
    }

    @OptIn(ExperimentalTvheadendApi::class)
    private suspend fun playRecordingLocked(
        recordingId: DvrEntryId,
        start: RecordingPlaybackStart,
        recoverAt: RecordingRecoveryPosition? = null,
        // A stall recovery replaces the very target it validated: Pause and Play still address it.
        continuesTarget: Boolean = false,
    ): PlaybackTargetResult? {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        val playerSettings = settings.playerSettings.first()
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        audioTracks.configureAudioOutputBeforeFirstTargetLocked(playerSettings)
        val expectedPresentationEpoch = presentationEpoch.snapshot()
        lastRecordingRequest = recordingId to start
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (_activeTarget.value == null) presentation.publishStarting()
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
        startupBuffer?.targetInstalling(live = false)
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
                        val recovery = recoverAt?.takeIf { it.recordingId == recordingId }
                        if (targetCommands.isOpen()) stopOtherKindBeforeInstall(live = false, expectedPresentationEpoch)
                        if (targetCommands.isOpen()) stopForFreshDecoder(recovery)
                        if (!targetCommands.isOpen()) {
                            PlaybackTargetResult.SHUT_DOWN
                        } else if (recovery != null) {
                            coordinator.recoverRecordingTarget(
                                binding = binding.binding,
                                position = recovery.positionMs.milliseconds,
                                previousDuration = recovery.durationMs?.milliseconds,
                                fallbackStart = start,
                            )
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
                    audioInterruptions.clearAudioInterruptionLocked()
                    val continues = continuesTarget && activeTargetEpoch == expectedPresentationEpoch &&
                        _activeTarget.value == AppPlaybackTarget.Recording(selection.recordingId)
                    activeTargetEpoch = epoch
                    if (!continues) targetContinuity = epoch
                    livePauseController.dropPendingLivePauseLocked()
                    _activeTarget.value = AppPlaybackTarget.Recording(selection.recordingId)
                    _recordingSelection.value = selection
                    _recordingAdmission.value = admission
                    recordingMarkers.observeRecordingMarkersLocked(requireNotNull(installedBinding))
                    presentation.publishStarting()
                    presentation.beginTargetPresentationLocked(epoch)
                    presentation.publishInstalledPlayerStateLocked()
                    livePauseController.publishLivePause()
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
        if (committed) {
            // Recovery continues where the lost target stood; the viewer's start applied once.
            recordingRecovery = recoverAt?.takeIf { it.recordingId == recordingId }
            val epoch = activeTargetEpoch
            var applied = false
            try {
                applyPlayIntentToStartedTarget(result)
                applied = true
            } finally {
                presentation.presentStartedTargetLocked(epoch, playStillPending = foreground && !applied)
            }
        }
        return result
    }

    /**
     * The user's (or a terminal) stop: Stop buttons, the no-target Stop key, a finished
     * recording, the root exit. Recorded under the intent current when it was asked for.
     */
    suspend fun stop(): PlaybackStopResult {
        val intent = playbackRequests.get()
        return targetCommands.serialize(
            onClosed = { PlaybackStopResult.SHUT_DOWN },
        ) {
            synchronized(intentLock) { userStopIntent = maxOf(userStopIntent, intent) }
            forgetRecordingRecoveryLocked()
            explicitStopLocked()
        }
    }

    /**
     * The same teardown as [stop] for automatic stops (connection lost, a rejected start):
     * no user stop is recorded, so it neither blocks a later restore nor closes a player
     * screen entered afterwards.
     */
    suspend fun stopAfterLoss(): PlaybackStopResult = targetCommands.serialize(
        onClosed = { PlaybackStopResult.SHUT_DOWN },
    ) {
        noteRecordingInterruptionLocked()
        explicitStopLocked()
    }

    /** A cancelled startup can retire only its own still-unpresented install, never a replacement. */
    suspend fun stopAfterLoss(
        selection: LivePlaybackSelection,
        epoch: Long,
        intent: Long,
    ): PlaybackStopResult? = targetCommands.serialize(onClosed = { PlaybackStopResult.SHUT_DOWN }) {
        val ownsInstall = targetCommands.readIfOpen { installedLiveTargetEpochLocked(selection, intent) == epoch } == true
        if (!ownsInstall) return@serialize null
        val frame = videoPresentation.value
        val tracks = player.currentTracks
        val audioOnlyPlaying = state.value is AppPlaybackState.Playing && player.isPlaying && player.playerError == null &&
            tracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected } &&
            tracks.groups.none { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
        if (frame.epoch == epoch && frame.visible || audioOnlyPlaying) return@serialize null
        noteRecordingInterruptionLocked()
        explicitStopLocked()
    }

    private suspend fun explicitStopLocked(): PlaybackStopResult {
        foregroundPlaybackLifecycle.onExplicitStop()
        cancelKeepTimer()
        _backgroundNotice.value = null
        liveRecovery.resetBackoffLocked()
        return stopPlayback()
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
                val recoveryPending = liveRecovery.cancelOnBackgroundLocked(currentJob, activeTargetEpoch)
                val recordingPlayWhenReady = targetCommands.readIfOpen { player.playWhenReady }
                    ?: return@serialize
                player.pause()
                // A pending pause is already a local pause: the paused intent below carries it.
                livePauseController.dropPendingLivePauseLocked()
                dropHeldLiveSelectionPause()
                livePauseController.publishLivePause()
                audioInterruptions.clearAudioInterruptionLocked()
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
                        targetIntent = servedIntent.get(),
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
                        latestIntent = playbackRequests.get(),
                    ),
                )
                resumeBufferRecoveryLocked()
            }
        }
    }

    /**
     * Retunes a live target whose session ended (the server restarted or the connection
     * dropped) once a session of the same profile is current again; until then it shows the
     * ended stream's last picture. For warm playback behind browse, whose owner calls this
     * when a session becomes current: a showing live screen stops its own target at the loss
     * and retunes it itself, and startup retires its own. Recovery, not new intent: the retune
     * serves the intent the target served. Null when nothing was owed.
     *
     * With [stopIfPaused] a target the viewer paused is stopped instead: the restart ended its
     * timeshift buffer, so a retune could neither resume where it paused nor stay paused
     * truthfully. A retune that fails retries as a live recovery, with its backoff, and stops
     * the target once that budget runs out, rather than leaving the ended stream on screen.
     */
    suspend fun retuneAfterReconnect(stopIfPaused: Boolean = false): PlaybackTargetResult? = targetCommands.serialize(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
    ) {
        val selection = activeLiveSelection ?: return@serialize null
        val current = session.observation.value.currentSession ?: return@serialize null
        val epoch = activeTargetEpoch
        if (!foreground || selection.currentSession === current ||
            epoch == null || _activeTarget.value != AppPlaybackTarget.Live(selection.channelId) ||
            activeLiveProfileGeneration != profileOwner.configurationGeneration.value
        ) return@serialize null
        if (stopIfPaused && viewerPausedLocked(epoch)) {
            explicitStopLocked()
            return@serialize null
        }
        val startEpoch = presentationEpoch.snapshot()
        val generation = playbackRequests.get()
        val result = try {
            playLive(
                channelId = selection.channelId,
                recovering = true,
                expectedPresentationEpoch = startEpoch,
                reconnectGeneration = activeLiveProfileGeneration,
            )
        } finally {
            settleCancelledStartLocked(startEpoch, generation)
        }
        // Another server saved meanwhile: the current session is not this channel's to retry on.
        if (reconnectRetuneFailed(result) && activeTargetEpoch == epoch &&
            profileStillCurrent(activeLiveProfileGeneration)
        ) {
            val fence = currentLiveRecoveryFence(
                reason = PlaybackRecoveryReason.LIVE_ENDED,
                observation = session.observation.value,
                activeTarget = _activeTarget.value,
                activeTargetEpoch = epoch,
            )
            if (fence == null) {
                publishRecoveryExhausted(PlaybackRecoveryReason.LIVE_ENDED)
            } else {
                // A new session: the budget spent on the ended one does not shorten this one.
                liveRecovery.resetBackoffLocked()
                reconnectRecoveryEpoch = epoch
                dispatchLiveRecovery(fence.reason, origin = fence)
            }
        }
        result
    }

    /** The server profile of [generation] is still the applied one, with no save since. */
    private fun profileStillCurrent(generation: Long): Boolean =
        profileOwner.serverProfile.value != null && profileOwner.configurationGeneration.value == generation

    /** Failed before it could start, and the target (its ended stream) is still there. */
    private fun reconnectRetuneFailed(result: PlaybackTargetResult?): Boolean =
        result != null && !result.isStarted &&
            result != PlaybackTargetResult.SHUT_DOWN && result != PlaybackTargetResult.DEPARTED

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
        if (!targetCommands.isOpen()) return PlaybackStopResult.SHUT_DOWN
        // A timer executing this stop must not cancel its own serialized cleanup.
        val timer = keepTimer
        keepTimer = null
        if (timer !== currentCoroutineContext().job) timer?.cancel()
        bufferRecovery = null
        reconnectRecoveryEpoch = null
        if (ownedMediaItem != null && player.currentMediaItem === ownedMediaItem &&
            livePlaybackObservation.value != LivePlaybackObservation.Departed
        ) player.pause()
        livePauseController.dropPendingLivePauseLocked()
        if (livePlaybackObservation.value == LivePlaybackObservation.Departed) {
            audioInterruptions.withdrawAudioInterruptionLocked()
        } else audioInterruptions.clearAudioInterruptionLocked()
        recordingMarkers.clearRecordingMarkers()
        val epoch = presentationEpoch.begin()
        presentation.endTargetPresentationLocked(epoch)
        val currentJob = currentCoroutineContext().job
        liveRecovery.cancelOnStopLocked(currentJob)
        val result = coordinator.stop()
        if (!targetCommands.isOpen()) return PlaybackStopResult.SHUT_DOWN
        presentationEpoch.publishIfCurrent(epoch) {
            _activeTarget.value = null
            frameRate.reset()
            _recordingSelection.value = null
            _recordingAdmission.value = null
            activeTargetEpoch = null
            livePauseController.publishLivePause()
            presentation.publishIdle()
            presentation.publishDiagnostics()
        }
        return result
    }

    /**
     * Stops a target of the other kind before the SDK installs [live]'s replacement, which it
     * would otherwise set over the old source and play through the old target's decoders. Not a
     * user stop: no stop intent, no recording interruption, and the install keeps its cover and
     * presentation epoch, so the old target's IDLE never shows. A failed install then publishes
     * its failure with no target left behind.
     */
    private suspend fun stopOtherKindBeforeInstall(live: Boolean, expectedPresentationEpoch: Long) {
        val previous = _activeTarget.value ?: return
        if ((previous is AppPlaybackTarget.Live) == live) return
        bufferRecovery = null
        livePauseController.dropPendingLivePauseLocked()
        if (livePlaybackObservation.value == LivePlaybackObservation.Departed) {
            audioInterruptions.withdrawAudioInterruptionLocked()
        } else audioInterruptions.clearAudioInterruptionLocked()
        recordingMarkers.clearRecordingMarkers()
        liveRecovery.cancelOnStopLocked(currentCoroutineContext().job)
        // Cancellation must not split the SDK's stop from the app's release of the target.
        withContext(NonCancellable) {
            // PLAYER_UNAVAILABLE is let go as in stopPlayback: the SDK keeps that cleanup, and the
            // replacement's installation is the retry it documents.
            coordinator.stop()
            if (!targetCommands.isOpen()) return@withContext
            _activeTarget.value = null
            _recordingSelection.value = null
            _recordingAdmission.value = null
            activeTargetEpoch = null
            livePauseController.publishLivePause()
            presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
                presentation.publishStarting()
                presentation.publishDiagnostics()
            }
        }
    }

    /**
     * Retunes the last live channel. [viewerRetry]: the viewer pressed Retry, which is a fresh
     * start: without a playable target it shows Starting at once, and once the new target
     * commits it drops the old interruption (mute included) and asks for audio focus like any
     * start. Otherwise (the live screen's automatic retry after a reconnect) it is recovery,
     * which keeps an interruption mute.
     */
    suspend fun retryLive(viewerRetry: Boolean = false): PlaybackTargetResult? {
        return retryLiveCommand(playbackRequests.incrementAndGet(), viewerRetry)
    }

    private suspend fun retryLiveCommand(
        generation: Long,
        viewerRetry: Boolean,
    ): PlaybackTargetResult? = targetCommands.serialize(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
    ) {
        // An explicit retry is a user decision, so it refills the budget an exhausted target used.
        liveRecovery.resetBackoffLocked()
        lastLiveChannelId?.let { channelId ->
            val startEpoch = presentationEpoch.snapshot()
            try {
                playLive(
                    channelId = channelId,
                    recovering = true,
                    expectedPresentationEpoch = startEpoch,
                    servedGeneration = generation,
                    viewerRetry = viewerRetry,
                )
            } finally {
                settleCancelledStartLocked(startEpoch, generation)
            }
        }
    }
    suspend fun retryRecording(): PlaybackTargetResult? {
        playbackRequests.incrementAndGet()
        return retryRecordingCommand()
    }

    private suspend fun retryRecordingCommand(): PlaybackTargetResult? = targetCommands.retryRecording(
        onClosed = { PlaybackTargetResult.SHUT_DOWN },
        currentRequest = { lastRecordingRequest },
    ) { (recordingId, start) ->
        noteRecordingInterruptionLocked()
        playRecordingLocked(recordingId, start, recoverAt = recordingRecovery)
    }
    suspend fun pauseTimeshift(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        if (audioInterruptions.interruptionMuted) TimeshiftCommandResult.UNAVAILABLE else coordinator.pauseTimeshift()
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
     * While a live selection is pending ([noteLiveSelection]) the Pause is held for that channel
     * at once, before any queued command, and returns null: the target still installed gets
     * nothing, and the held Pause replaces the new channel's startup Play.
     */
    suspend fun pauseTimeshiftPlayback(): TimeshiftCommandResult? {
        val arrival = playbackRequests.get()
        if (foreground && setPendingLiveSelectionPaused(true)) return null
        return targetCommands.serialize(
            onClosed = { TimeshiftCommandResult.SHUT_DOWN },
        ) {
            // A channel change (or other viewing intent) noted while this Pause waited supersedes
            // it: it touches neither the target it was meant for nor the new one.
            if (arrival != playbackRequests.get()) null else pauseTimeshiftPlaybackLocked()
        }
    }

    private suspend fun pauseTimeshiftPlaybackLocked(): TimeshiftCommandResult? {
        if (!foreground) return TimeshiftCommandResult.UNAVAILABLE
        if (audioInterruptions.interruptionMuted) {
            audioInterruptions.playWithAudioFocusLocked(restoreSoundOnly = true)
            return null
        }
        val wasPlaying = player.playWhenReady
        audioInterruptions.forgetInterruptionResumeLocked()
        player.pause()
        presentation.settlePausedPictureLocked()
        // The interruption already holds the server; a second pause adds nothing.
        if (audioInterruptions.interruptionPaused) return null
        val epoch = activeTargetEpoch
        if (epoch != null && audioInterruptions.interruption == null && targetCommands.isOpen() &&
            livePauseController.awaitsFirstPicture(epoch, livePauseController.currentLivePauseAvailability())
        ) {
            if (livePauseController.pendingLivePauseEpoch != epoch) livePauseController.startPendingLivePauseLocked(epoch, wasPlaying = wasPlaying, failClosed = false)
            return null
        }
        return coordinator.pauseTimeshift().also { result ->
            if (result.disposition == TimeshiftCommandDisposition.NOT_ACCEPTED &&
                foreground && audioInterruptions.interruption == null && targetCommands.isOpen()
            ) {
                player.playWhenReady = wasPlaying
                if (wasPlaying) resumeInterruptedLiveRecoveryLocked()
            }
        }
    }

    /**
     * While a live selection is pending, Play only withdraws a Pause held for it: ACCEPTED.
     * Viewing intent noted while it waited supersedes it: it resumes nothing and returns
     * ACCEPTED, as the newer target starts playing on its own and there is nothing to roll back.
     */
    suspend fun resumeTimeshift(): TimeshiftCommandResult {
        val arrival = playbackRequests.get()
        if (foreground && setPendingLiveSelectionPaused(false)) return TimeshiftCommandResult.ACCEPTED
        return targetCommands.serialize(
            onClosed = { TimeshiftCommandResult.SHUT_DOWN },
        ) {
            if (arrival != playbackRequests.get()) TimeshiftCommandResult.ACCEPTED
            else audioInterruptions.playWithAudioFocusLocked(resumeTimeshift = true)
        }
    }
    suspend fun goLive(): TimeshiftCommandResult = targetCommands.serialize(
        onClosed = { TimeshiftCommandResult.SHUT_DOWN },
    ) {
        serverSkip({ it.disposition != TimeshiftCommandDisposition.NOT_ACCEPTED }) { coordinator.returnToLive() }
    }
    fun play() {
        if (foreground && setPendingLiveSelectionPaused(false)) return
        val epoch = activeTargetEpoch
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (epoch != null && epoch == activeTargetEpoch) audioInterruptions.playWithAudioFocusLocked()
            }
        }
    }
    @OptIn(ExperimentalTvheadendApi::class)
    suspend fun seekTimeshift(target: at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget) =
        targetCommands.serializeArrival(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) { arrival ->
            serverSkip({ it.startedServerSkip() }) { coordinator.seekTimeshift(target) }
                .also { if (it.startedServerSkip()) nudgePausedSeek(arrival) }
        }

    @OptIn(ExperimentalTvheadendApi::class)
    suspend fun seekTimeshift(selection: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekSelection) =
        targetCommands.serializeArrival(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) { arrival ->
            presentation.diagnoseSeekLocked(selection) {
                serverSkip({ it.startedServerSkip() }) { coordinator.seekTimeshift(selection) }
            }.also { if (it.startedServerSkip()) nudgePausedSeek(arrival) }
        }

    /**
     * A tunnelled decoder can hold the first frame of a paused seek until playback starts
     * ([PausedSeekConfirmation.KEY_FRAME_QUEUED], or a confirmation this app does not know): the
     * old picture would stay. A brief silent local play shows the new one while the server stays
     * paused. It queues behind the seek, so the seek returns at once, and runs only while no
     * command arrived after the seek's [arrival]: of rapid seeks only the last refreshes.
     * Internal so JVM tests can stand in for a confirmation their fake stream never produces.
     */
    @OptIn(ExperimentalTvheadendApi::class)
    internal fun nudgePausedSeek(
        arrival: Long = targetCommands.arrivalCount,
        confirmation: PausedSeekConfirmation? = coordinator.pausedSeekConfirmation.value,
    ) {
        // Read right after the seek returns: the SDK clears it on the next command or once playing.
        if (confirmation == null || confirmation == PausedSeekConfirmation.FRAME_RENDERED) return
        if (!player.trackSelectionParameters.tunnelingRequested || player.playWhenReady) return
        val epoch = activeTargetEpoch ?: return
        val intent = playbackRequests.get()
        scope.launch {
            targetCommands.serialize(onClosed = {}, countsAsArrival = false) {
                if (!pausedSeekSuperseded(arrival, intent, epoch) && !player.playWhenReady) {
                    refreshPausedSeekPictureLocked(arrival, intent, epoch)
                }
            }
        }
    }

    private fun pausedSeekSuperseded(arrival: Long, intent: Long, epoch: Long): Boolean =
        !targetCommands.isOpen() || !foreground || arrival != targetCommands.arrivalCount ||
            intent != playbackRequests.get() || epoch != activeTargetEpoch ||
            audioInterruptions.interruption != null || player.playerError != null

    /**
     * Plays muted until the video decoder renders a further frame, the SDK confirms the rendered
     * picture, [PAUSED_SEEK_NUDGE_MILLIS] pass, or a newer command arrives; then pauses again and
     * restores the volume as it was. It holds the command lock throughout, so a newer command runs
     * only after the player is paused and audible again: no stale pause or volume reaches it.
     */
    @OptIn(ExperimentalTvheadendApi::class)
    private suspend fun refreshPausedSeekPictureLocked(arrival: Long, intent: Long, epoch: Long) {
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val renderedBefore = counters?.renderedOutputBufferCount ?: 0
        val volume = player.volume
        val deadline = elapsedRealtime() + PAUSED_SEEK_NUDGE_MILLIS
        // Published before the play: the viewer's paused controls must not show it.
        pausedSeekNudging = true
        livePauseController.publishLivePause()
        if (policy.trace.enabled) policy.trace.pausedSeekNudge(PausedSeekNudgePhase.START)
        var phase = PausedSeekNudgePhase.CANCELLED
        try {
            player.volume = 0f
            player.play()
            while (true) {
                if (pausedSeekSuperseded(arrival, intent, epoch) || !player.playWhenReady) break
                val rendered = player.videoDecoderCounters?.also { it.ensureUpdated() }?.renderedOutputBufferCount ?: 0
                if (rendered > renderedBefore ||
                    coordinator.pausedSeekConfirmation.value == PausedSeekConfirmation.FRAME_RENDERED
                ) {
                    phase = PausedSeekNudgePhase.SHOWN
                    break
                }
                if (elapsedRealtime() >= deadline) {
                    phase = PausedSeekNudgePhase.TIMEOUT
                    break
                }
                delay(MOTION_POLL_MILLIS)
            }
        } finally {
            player.pause()
            player.volume = volume
            // Cleared after the pause, so the paused controls never see a play in between.
            pausedSeekNudging = false
            livePauseController.publishLivePause()
            if (policy.trace.enabled) policy.trace.pausedSeekNudge(phase)
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
    /** Pauses the target it arrived for, or the recovery replacement that continues it. */
    fun pause() {
        // Continuity first: a commit writes it after the epoch (see targetContinuity).
        val continuity = targetContinuity
        val epoch = activeTargetEpoch
        scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (epoch == null || activeTargetEpoch == null ||
                    epoch != activeTargetEpoch && continuity != targetContinuity
                ) return@serialize
                pauseLocallyOrRestoreSound()
            }
        }
    }

    /** Returns true only when a local pause was applied, not when restoring muted sound. */
    private suspend fun pauseLocallyOrRestoreSound(): Boolean {
        if (audioInterruptions.interruptionMuted) {
            audioInterruptions.playWithAudioFocusLocked()
            return false
        }
        audioInterruptions.forgetInterruptionResumeLocked()
        player.pause()
        presentation.settlePausedPictureLocked()
        return true
    }

    /**
     * System controls use the same local/server intent as the player keys, atomically: while a
     * live selection is pending they hold or withdraw its Pause at once. A live target pauses
     * through the pending live pause while its grant or first picture is outstanding; without
     * timeshift (OFF, UNAVAILABLE) it neither pauses nor plays. Viewing intent noted while it
     * waited supersedes it: it does nothing. A Retry already under way when it arrived is not
     * newer: the retried target it commits is the one it addresses. Nor is a recovery retune of
     * the target it arrived for: the recovered target is the same channel for the same intent.
     */
    fun setSessionPlayWhenReady(playWhenReady: Boolean, isAttached: () -> Boolean): Job {
        val arrival = playbackRequests.get()
        if (isAttached() && foreground && setPendingLiveSelectionPaused(!playWhenReady)) return Job().apply { complete() }
        // Continuity first: a commit writes it after the epoch (see targetContinuity).
        val continuity = targetContinuity
        val epoch = activeTargetEpoch
        val servedAtArrival = servedIntent.get()
        return scope.launch {
            targetCommands.serialize(onClosed = {}) {
                if (!isAttached() || !foreground || activeTargetEpoch == null) return@serialize
                if (arrival != playbackRequests.get()) return@serialize
                // A start of the arrival intent that was still under way (a Retry, the automatic
                // reconnect) and committed while this waited serves that intent, and a recovery
                // retune continues the arrival target: the command addresses the target either
                // committed. Any other replacement drops it.
                val continued = epoch != null && continuity == targetContinuity
                val servesArrival = servedAtArrival != arrival && servedIntent.get() == arrival
                if (epoch != activeTargetEpoch && !continued && !servesArrival) return@serialize
                val timeshift = audioInterruptions.currentInterruptionContent() == AudioInterruptionContent.LIVE_TIMESHIFT
                val live = _activeTarget.value is AppPlaybackTarget.Live
                if (live && !timeshift && !livePauseController.currentLivePauseAvailability().acceptsPause() &&
                    !(playWhenReady && hasExhaustedRecovery())
                ) return@serialize
                if (playWhenReady) {
                    val result = audioInterruptions.playWithAudioFocusLocked(resumeTimeshift = timeshift)
                    // A denied focus request retains its interruption hold/mute. Only a
                    // granted request follows the player-key server-rejection rollback.
                    if (timeshift && audioInterruptions.interruption == null && result != TimeshiftCommandResult.ACCEPTED) {
                        player.pause()
                    }
                } else if (live) {
                    pauseTimeshiftPlaybackLocked()
                } else {
                    pauseLocallyOrRestoreSound()
                }
            }
        }
    }

    /**
     * The player's Stop for system controls, fenced on viewing intent: user intent noted
     * after it arrived cancels it outright. While it is still the viewer's latest action it
     * stops whatever target is active when it runs (a channel or recording still starting
     * when it arrived included), provided the session is attached and the app is in the
     * foreground. Registered as pending from arrival until its check (see
     * [enterPlayerScreen]). Announces itself on [sessionStops] together with the intent
     * generation current when it arrived.
     */
    fun stopFromSession(isAttached: () -> Boolean): Job {
        val request = synchronized(intentLock) {
            playbackRequests.get().also { request ->
                if (request != pendingStopIntent) {
                    pendingStopIntent = request
                    pendingStops = 0
                }
                pendingStops++
            }
        }
        // Guarded by intentLock. Every path (run, skipped, runtime closed, cancelled before
        // it ran) ends the registration exactly once: at the check, or on completion.
        var pending = true
        fun endPendingLocked() {
            if (!pending) return
            pending = false
            if (pendingStopIntent == request && pendingStops > 0) pendingStops--
        }
        return scope.launch {
            targetCommands.serialize(onClosed = {}) {
                val current = synchronized(intentLock) {
                    endPendingLocked()
                    (isAttached() && foreground && activeTargetEpoch != null &&
                        request == playbackRequests.get())
                        .also { if (it) userStopIntent = maxOf(userStopIntent, request) }
                }
                if (!current) return@serialize
                explicitStopLocked()
                if (targetCommands.isOpen()) lastSessionStop.value = request
            }
        }.also { job -> job.invokeOnCompletion { synchronized(intentLock) { endPendingLocked() } } }
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
        recordingMarkers.seekRecordingMarker(positionMs, expectedRevision)
    }
    fun setDiagnosticsEnabled(enabled: Boolean) {
        presentation.setDiagnosticsEnabled(enabled)
    }
    fun setFrameRateSwitching(switching: FrameRateSwitching) {
        audioTracks.setFrameRateSwitching(switching)
    }
    fun onRecoveryRequired(reason: PlaybackRecoveryReason) {
        // The escalation belongs to the target playing when the SDK reports it, not to a
        // replacement that commits before it is admitted.
        val origin = activeTargetEpoch?.let { epoch ->
            activeLiveSelection?.let { LiveRecoveryFence(reason, it, epoch, servedIntent.get()) }
        }
        val reportedInstall = liveInstall?.takeIf { it.sourceGeneration != playerSources }
        if (reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED ||
            reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED ||
            reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED
        ) {
            // The source being installed owns its callback even while the predecessor's epoch
            // is still active. Deduplicate that receipt, not the predecessor's exhaustion.
            val duplicate = if (reportedInstall != null) {
                exhaustedRecoveryInstall === reportedInstall
            } else {
                activeTargetEpoch != null && (exhaustedRecoveryEpoch == activeTargetEpoch ||
                    exhaustedRecoveryInstall?.committedEpoch == activeTargetEpoch)
            }
            if (duplicate) {
                resumeRecoveryOnPlayerChange()
                return
            }
            if (reportedInstall != null) exhaustedRecoveryInstall = reportedInstall
            else exhaustedRecoveryEpoch = activeTargetEpoch
            // Record installation work at the callback boundary, before a newer viewing intent
            // can enter the command lock. Its cleanup is not a permission to retune that intent.
            val retirement = LiveRetirementRetry(
                activeTargetEpoch,
                reportedInstall,
            ).takeIf { reason != PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED }
            if (retirement != null) liveRetirement = retirement
            scope.launch {
                targetCommands.serialize(onClosed = {}) {
                    val installed = reportedInstall?.committedEpoch?.let {
                        LiveRecoveryFence(reason, reportedInstall.selection, it, servedIntent.get())
                    }
                    val fence = (installed ?: origin)?.takeIf(::liveRecoveryFenceMatches)
                    if (retirement != null && liveRetirement === retirement && !retryLiveRetirementLocked()) {
                        // Keep this report owed. A later callback or Player event retries cleanup,
                        // not a second installation, and does not spend a recovery attempt.
                        bufferRecovery = fence
                        return@serialize
                    }
                    if (fence == null) return@serialize
                    if (bufferRecovery != null) return@serialize
                    exhaustedRecoveryEpoch = fence.targetEpoch
                    bufferRecovery = fence
                    resumeBufferRecoveryLocked()
                }
            }
            return
        }
        // The SDK reports a target's escalations only while that target owns the player's
        // source, and may report the new source's before the install that set it commits here.
        // Once the install replaced the source, the report is the install's if it commits;
        // if it fails, the SDK restored the previous target, whose report it then is.
        val install = liveInstall?.takeIf { it.sourceGeneration != playerSources }
        dispatchLiveRecovery(reason, origin, install)
    }

    /**
     * Dispatches a recovery the SDK requested, or a deferred one once playback may continue.
     * Either belongs to the target it was requested for ([origin]): it is admitted only while
     * that target plays, and otherwise dropped before it touches the budget or the state. It
     * waits (still unadmitted) while an interruption or the viewer's own Pause holds that
     * target, and once admitted it revalidates that Pause where it retunes, after its backoff.
     * One reported during a live [install] belongs to the target that install commits, or to
     * [origin] (the target that stays) if it commits none; either fence is validated the same way.
     */
    @OptIn(ExperimentalTvheadendApi::class)
    private fun dispatchLiveRecovery(
        reason: PlaybackRecoveryReason,
        origin: LiveRecoveryFence?,
        install: LiveInstallAttribution? = null,
    ) {
        val reportedEpoch = activeTargetEpoch
        liveRecovery.dispatch(
            reason = reason,
            admitLocked = admit@{ _ ->
                // Admission waits for the install, so its outcome is known here: a commit owns
                // the report (with the selection it installed, never the current session's).
                val installed = install?.committedEpoch?.let { LiveRecoveryFence(reason, install.selection, it, servedIntent.get()) }
                val requestedEpoch = installed?.targetEpoch ?: reportedEpoch
                if (foregroundPlaybackLifecycle.isKeeping(activeTargetEpoch)) {
                    if (requestedEpoch != activeTargetEpoch) return@admit null
                    applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(requestedEpoch, BackgroundPlaybackNotice.TUNER_LOST))
                    return@admit null
                }
                if (!foreground) return@admit null
                // Checked before the budget: a replacement that committed meanwhile owns its own.
                val fence = (installed ?: origin)?.takeIf(::liveRecoveryFenceMatches) ?: return@admit null
                if (recoveryPlaybackBlocked(fence.targetEpoch)) {
                    // The SDK does not escalate this target again: keep the request, without
                    // claiming an attempt, until playback may continue (the interruption ends,
                    // or the viewer plays again after a Pause).
                    liveRecovery.deferLocked(fence)
                    return@admit null
                }
                val attempt = liveRecovery.nextAttemptLocked()
                if (attempt == null) {
                    publishRecoveryExhausted(fence.reason)
                    return@admit null
                }
                liveRecovery.admitLocked(fence)
                presentationEpoch.publishIfCurrent(fence.targetEpoch) {
                    presentation.publishRecovering(
                        reason = fence.reason,
                        retryDelayMillis = attempt.delay.inWholeMilliseconds,
                    )
                    presentation.publishDiagnostics()
                }
                fence to attempt
            },
            retryLocked = ::retryResumedLiveRecoveryLocked,
        )
    }

    private suspend fun retryLiveRecoveryLocked(fence: LiveRecoveryFence): PlaybackTargetResult? {
        if (recoveryPlaybackBlocked(fence.targetEpoch) || !liveRecoveryFenceMatches(fence)) return null
        return playLive(
            channelId = fence.selection.channelId,
            recovering = true,
            expectedPresentationEpoch = fence.targetEpoch,
            recoverySelection = fence.selection,
            recoveryFence = fence,
            // A reconnect retry stays within the profile its retune was admitted for.
            reconnectGeneration = activeLiveProfileGeneration.takeIf { reconnectRecoveryEpoch == fence.targetEpoch },
        )
    }

    /**
     * Every attempt (after its backoff, or resumed) revalidates the viewer's transport intent
     * where it runs: a Pause accepted meanwhile wins, and the retune's startup Play would
     * override it. The attempt, already budgeted, then stays owed until the viewer plays again.
     */
    private suspend fun retryResumedLiveRecoveryLocked(fence: LiveRecoveryFence): PlaybackTargetResult? {
        if (foreground && recoveryPlaybackBlocked(fence.targetEpoch) && liveRecoveryFenceMatches(fence)) {
            liveRecovery.oweAgainLocked(fence)
            return null
        }
        return retryLiveRecoveryLocked(fence)
    }

    /** The viewer's own Pause holds [epoch]: paused, or a pause still pending for it. */
    private fun viewerPausedLocked(epoch: Long): Boolean =
        !player.playWhenReady || livePauseController.pendingLivePauseEpoch == epoch

    private fun recoveryPlaybackBlocked(epoch: Long): Boolean =
        !foreground || audioInterruptions.interruptionPaused || viewerPausedLocked(epoch) ||
            player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE

    private fun hasExhaustedRecovery(): Boolean =
        listOfNotNull(bufferRecovery, liveRecovery.deferredRecovery, liveRecovery.interruptedRecovery).any {
            it.reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED ||
                it.reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED ||
                it.reason == PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_PLAYER_CLEANUP_FAILED
        }

    private fun canContinueRecoveryLocked(fence: LiveRecoveryFence): Boolean {
        if (!liveRecoveryFenceMatches(fence)) return false
        if (player.currentMediaItem?.let { it !== ownedMediaItem } == true) {
            withdrawDepartedTargetLocked()
            return false
        }
        if (recoveryPlaybackBlocked(fence.targetEpoch)) {
            liveRecovery.oweAgainLocked(fence)
            return false
        }
        return true
    }

    private fun resumeRecoveryOnPlayerChange() {
        scope.launch {
            // A paused seek's refresh changes the play intent itself: this reaction waits for its end.
            targetCommands.serialize(onClosed = {}, countsAsArrival = false) {
                resumeBufferRecoveryLocked()
                resumeInterruptedLiveRecoveryLocked()
            }
        }
    }

    private suspend fun resumeBufferRecoveryLocked() {
        if (!retryLiveRetirementLocked()) return
        val fence = bufferRecovery ?: return
        if (!liveRecoveryFenceMatches(fence)) {
            bufferRecovery = null
            return
        }
        if (player.currentMediaItem?.let { it !== ownedMediaItem } == true) {
            withdrawDepartedTargetLocked()
            return
        }
        if (!foreground) return
        // Admission, blocked transport, backoff and the attempt budget have one owner.
        bufferRecovery = null
        dispatchLiveRecovery(fence.reason, fence)
    }

    private suspend fun retryLiveRetirementLocked(): Boolean {
        val obligation = liveRetirement ?: return true
        val epoch = obligation.install?.committedEpoch ?: obligation.epoch
        // A later explicit installation already replaced/retired this SDK target. Never
        // stop that newer installation merely because the older callback arrived late.
        if (activeTargetEpoch == epoch ||
            activeTargetEpoch == null && departedEpoch == epoch
        ) {
            val result = try {
                coordinator.stop()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                presentation.publishFailed(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER))
                return false
            }
            when (result.outcome) {
                PlaybackStopOutcome.STOPPED, PlaybackStopOutcome.RETIRED_AFTER_DEPARTURE,
                PlaybackStopOutcome.ALREADY_STOPPED -> Unit
                PlaybackStopOutcome.PLAYER_UNAVAILABLE -> {
                    if (epoch != null && activeTargetEpoch == epoch &&
                        player.currentMediaItem === ownedMediaItem
                    ) presentationEpoch.publishIfCurrent(epoch) {
                        presentation.publishFailed(AppPlaybackState.Failed(AppPlaybackFailureReason.OTHER))
                    }
                    return false
                }
                else -> {
                    presentation.publishFailed(recoveryExhaustedState(result,
                        PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED_RETIREMENT_FAILED))
                    return false
                }
            }
        }
        if (liveRetirement === obligation) liveRetirement = null
        return true
    }

    /** Withdraw attribution without pausing, preparing or changing replacement playback. */
    private fun withdrawDepartedTargetLocked() {
        departedEpoch = activeTargetEpoch
        bufferRecovery = null
        liveRecovery.dropDeferredLocked()
        liveRecovery.dropInterruptedLocked()
        val epoch = presentationEpoch.begin()
        presentation.endTargetPresentationLocked(epoch)
        activeTargetEpoch = null
        activeLiveSelection = null
        _activeTarget.value = null
        frameRate.reset()
        _recordingSelection.value = null
        _recordingAdmission.value = null
        livePauseController.dropPendingLivePauseLocked()
        audioInterruptions.withdrawAudioInterruptionLocked()
        recordingMarkers.clearRecordingMarkers()
        presentation.publishIdle()
        presentation.publishDiagnostics()
    }

    /**
     * Playback may continue after an audio interruption (focus returned, a play acquired it, or a
     * rejected hold went on muted) or the viewer's Pause: the admitted recovery the interruption
     * or Pause stopped retunes its target now, and a recovery requested meanwhile is admitted
     * now, if that target (channel, epoch and session) is still the one playing. Either stays
     * owed while the interruption or the viewer's own Pause holds playback.
     */
    private fun resumeInterruptedLiveRecoveryLocked() {
        if (bufferRecovery != null) scope.launch {
            targetCommands.serialize(onClosed = {}) { resumeBufferRecoveryLocked() }
        }
        val interrupted = liveRecovery.interruptedRecovery
        if (interrupted != null && !liveRecoveryFenceMatches(interrupted)) liveRecovery.dropInterruptedLocked()
        val deferred = liveRecovery.deferredRecovery
        if (deferred != null && !liveRecoveryFenceMatches(deferred)) liveRecovery.dropDeferredLocked()
        val epoch = activeTargetEpoch ?: return
        if (liveRecovery.interruptedRecovery == null && liveRecovery.deferredRecovery == null) return
        if (recoveryPlaybackBlocked(epoch)) return
        if (liveRecovery.interruptedRecovery != null) {
            liveRecovery.resumeInterruptedLocked(::retryResumedLiveRecoveryLocked) { fence ->
                presentationEpoch.publishIfCurrent(fence.targetEpoch) {
                    presentation.publishRecovering(reason = fence.reason, retryDelayMillis = 0L)
                    presentation.publishDiagnostics()
                }
            }
            return
        }
        // Admission claims its attempt (and backoff) now, as for any other escalation, for the
        // target the request was deferred for.
        liveRecovery.takeDeferredLocked()?.let { dispatchLiveRecovery(it.reason, origin = it) }
    }

    private fun liveRecoveryFenceMatches(fence: LiveRecoveryFence): Boolean =
        livePlaybackObservation.value != LivePlaybackObservation.Departed && departedEpoch != fence.targetEpoch &&
        (fence.viewingIntent == null || fence.viewingIntent == playbackRequests.get()) &&
        !isPlaybackIntentStopped(fence.viewingIntent ?: servedIntent.get()) && fence.matches(
        activeTarget = _activeTarget.value,
        activeTargetEpoch = activeTargetEpoch,
        observation = session.observation.value,
    )

    /**
     * Retires a target the SDK keeps reporting as stuck.
     *
     * Recovery cannot succeed indefinitely, and a still-running subscription keeps consuming a
     * tuner. Surfacing a failure lets the user choose, instead of retuning forever.
     */
    private suspend fun publishRecoveryExhausted(recoveryReason: PlaybackRecoveryReason) {
        val stopResult = stopPlayback()
        if (!targetCommands.isOpen()) return
        presentation.publishFailed(recoveryExhaustedState(stopResult, recoveryReason))
        presentation.publishDiagnostics()
    }

    private fun watchForStall() {
        val state = player.playbackState
        if (stallPolling || !targetCommands.isOpen() || !player.playWhenReady ||
            state != Player.STATE_BUFFERING && state != Player.STATE_READY
        ) return
        stallPolling = true
        stallHandler.postDelayed(stallPoll, STALL_POLL_MILLIS)
    }

    /**
     * The installed target the viewer wants playing, or null while nothing may count as a stall:
     * an install, cleanup or recovery in progress, the viewer's Pause or Stop, the background, an
     * audio interruption that pauses it. An interruption mutes only live TV, which plays on and is
     * watched; its recovery keeps the mute. The picture is watched with the counters of this
     * target's decoder, also while its cover still waits for a moving picture, and not when
     * tunnelling or without a selected video track (radio).
     */
    private fun stallSampleLocked(): StallSample? {
        val epoch = activeTargetEpoch ?: return null
        if (_activeTarget.value == null) return null
        val state = player.playbackState
        if (targetInstallationInProgress || coordinator.playerCleanupPending.value ||
            state != Player.STATE_BUFFERING && state != Player.STATE_READY ||
            recoveryPlaybackBlocked(epoch) || player.currentMediaItem.let { it == null || it !== ownedMediaItem } ||
            bufferRecovery != null || liveRecovery.deferredRecovery != null || liveRecovery.interruptedRecovery != null ||
            presentation.state.value.let { it is AppPlaybackState.Recovering || it is AppPlaybackState.Failed }
        ) return null
        val video = presentation.videoPresentation.value
        val counters = player.videoDecoderCounters?.takeIf {
            video.epoch == epoch && !player.trackSelectionParameters.tunnelingRequested &&
                player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        }
        counters?.ensureUpdated()
        return StallSample(
            epoch = epoch,
            buffering = state == Player.STATE_BUFFERING,
            bufferedAheadMillis = player.totalBufferedDuration,
            playing = player.isPlaying,
            positionMillis = player.currentPosition,
            videoCounters = counters,
            renderedFrames = counters?.renderedOutputBufferCount ?: 0,
            pictureShown = video.epoch == epoch && video.visible,
        )
    }

    /** Queues one recovery at a time; the watch keeps sampling, so progress meanwhile supersedes it. */
    private fun recoverStall(stall: PlaybackStall, epoch: Long) {
        if (stallRecoveryQueued) return
        if (policy.trace.enabled) policy.trace.stall(stall, epoch)
        val intent = playbackRequests.get()
        val evidence = stallWatch.evidence
        stallRecoveryQueued = true
        scope.launch {
            targetCommands.serialize(onClosed = {}) { recoverStallLocked(epoch, intent, evidence) }
        }.invokeOnCompletion { stallRecoveryQueued = false }
    }

    /**
     * Restarts the stalled target of [epoch] once with fresh decoders: live retunes the channel
     * (the live edge, as any recovery retune), a recording resumes where it stood. A second stall
     * of the same target and [intent] within [STALL_RECOVERY_WINDOW_MILLIS] fails it instead.
     * Dropped, with its budget untouched, when the target progressed or was reset since the watch
     * reported it ([evidence]), when a newer command replaced the target, or when it may no longer
     * count as a stall at all: the viewer paused or stopped it, the app went to the background,
     * an audio interruption holds it.
     */
    private suspend fun recoverStallLocked(epoch: Long, intent: Long, evidence: Long) {
        val target = _activeTarget.value ?: return
        val sample = stallSampleLocked()?.takeIf { it.epoch == epoch } ?: return
        if (playbackRequests.get() != intent || isPlaybackIntentStopped(intent)) return
        // Progress since the last poll supersedes the report just as progress at a poll does.
        stallWatch.onSample(sample, SystemClock.uptimeMillis())
        if (stallWatch.evidence != evidence) return
        val now = elapsedRealtime()
        if (!lastStallRecovery.allowsRecovery(target, intent, now)) {
            failStalledTargetLocked(target)
            return
        }
        when (target) {
            is AppPlaybackTarget.Live -> {
                val fence = activeLiveSelection?.let {
                    LiveRecoveryFence(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED, it, epoch, servedIntent.get())
                }?.takeIf(::liveRecoveryFenceMatches) ?: return
                lastStallRecovery = StallRecovery(target, intent, now)
                stallRecoveries++
                if (!freshDecoderPerZap) {
                    freshDecoderPerZap = true
                    if (policy.trace.enabled) policy.trace.freshDecoderPerZap()
                }
                // Kept for a recovery its fence owes until the viewer plays again (see stopForFreshDecoder).
                freshDecoderRecovery = fence
                retryLiveRecoveryLocked(fence)
            }
            is AppPlaybackTarget.Recording -> {
                val (recordingId, start) = lastRecordingRequest?.takeIf { it.first == target.recordingId } ?: return
                lastStallRecovery = StallRecovery(target, intent, now)
                stallRecoveries++
                noteRecordingInterruptionLocked()
                val recoverAt = recordingRecovery
                freshDecoderRecovery = recoverAt
                try {
                    playRecordingLocked(recordingId, start, recoverAt = recoverAt, continuesTarget = true)
                } finally {
                    freshDecoderRecovery = null
                }
            }
        }
        presentation.publishDiagnostics()
    }

    /** The second stall in the window: live gives up as exhausted recovery, a recording fails to read. */
    private suspend fun failStalledTargetLocked(target: AppPlaybackTarget) {
        if (target is AppPlaybackTarget.Live) {
            publishRecoveryExhausted(PlaybackRecoveryReason.LIVE_BUFFER_EXHAUSTED)
            return
        }
        // Retry continues where the recording stood.
        noteRecordingInterruptionLocked()
        stopPlayback()
        if (!targetCommands.isOpen()) return
        presentation.publishFailed(AppPlaybackState.Failed(AppPlaybackFailureReason.RECORDING_READ_FAILED))
        presentation.publishDiagnostics()
    }

    /**
     * Retires the stalled target before [recovery]'s replacement installs: set over the old source,
     * the replacement would play through the stuck decoders. Unlike [stopOtherKindBeforeInstall] the
     * app keeps its target and interruption state, as any recovery does; the retired source is IDLE,
     * so a failed install still publishes its failure, and a viewer's cancelled one lets the target
     * go ([settleRetiredTargetLocked]). A [liveZap] over a live target stops it the same way once
     * [freshDecoderPerZap] is on.
     */
    private suspend fun stopForFreshDecoder(recovery: Any?, liveZap: Boolean = false) {
        val recovers = recovery != null && recovery == freshDecoderRecovery
        if (!recovers && !(liveZap && freshDecoderPerZap && _activeTarget.value is AppPlaybackTarget.Live)) return
        if (recovers) freshDecoderRecovery = null
        // Cancellation must not split the SDK's stop from the note that the target is gone.
        withContext(NonCancellable) {
            retiredTargetEpoch = activeTargetEpoch
            retiredForRecovery = recovery != null
            coordinator.stop()
        }
    }

    /**
     * An install cancelled after [stopForFreshDecoder] retired its predecessor leaves no source
     * behind that target. A viewer's zap or retry lets it go as [stopOtherKindBeforeInstall] does,
     * the install's presentation keeping its cover, and then settles as any cancelled start
     * ([settleCancelledStartLocked]). A newer recovery no longer cancels a recovery install (it
     * waits for the command lock), so a recovery install is cancelled here only when its scope
     * ends without a detach. It then leaves the target with the viewer's [previousPlayWhenReady]
     * back, so the install's pause does not hold a next attempt as the viewer's Pause. A source
     * the SDK installed for the cancelled install is not the app's: it stays paused and the target
     * departs, as [canContinueRecoveryLocked] would withdraw it.
     * An install that ran to its end published its own outcome.
     */
    private fun settleRetiredTargetLocked(
        expectedPresentationEpoch: Long,
        cancelled: Boolean,
        previousPlayWhenReady: Boolean,
    ) {
        val retired = retiredTargetEpoch ?: return
        retiredTargetEpoch = null
        if (!cancelled || !targetCommands.isOpen() || activeTargetEpoch != retired) return
        if (retiredForRecovery) {
            if (player.currentMediaItem.let { it != null && it !== ownedMediaItem }) withdrawDepartedTargetLocked()
            else if (foreground) player.playWhenReady = previousPlayWhenReady
            return
        }
        _activeTarget.value = null
        _recordingSelection.value = null
        _recordingAdmission.value = null
        activeTargetEpoch = null
        livePauseController.publishLivePause()
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            presentation.publishStarting()
            presentation.publishDiagnostics()
        }
    }

    private fun lastSubscriptionIssue(): SubscriptionIssue? =
        (livePlaybackObservation.value as? LivePlaybackObservation.Active)?.subscriptionIssue

    suspend fun detach() {
        if (!targetCommands.close()) return
        cancelKeepTimer()
        recordingMarkers.clearRecordingMarkersAndJoin()
        val pendingRecovery = liveRecovery.cancelOnDetach()
        livePlaybackObservationJob.cancel()
        settingsJob.cancel()
        declaredFrameDurationJob.cancel()
        pendingRecovery?.join()
        livePlaybackObservationJob.join()
        settingsJob.join()
        declaredFrameDurationJob.join()
        targetCommands.awaitIdle {
            recordingRecovery = null
            livePauseController.dropPendingLivePauseLocked()
            dropHeldLiveSelectionPause()
            audioInterruptions.clearAudioInterruptionLocked()
            presentation.removeTargetFrameListenerLocked()
            player.removeListener(listener)
            stallHandler.removeCallbacks(stallPoll)
            player.clearVideoFrameMetadataListener(frameRate.listener)
            frameRate.close()
            presentation.seekDiagnosticsListener?.let(player::removeAnalyticsListener)
            audioTracks.clearSessionAudioLocked()
        }
        audioTracks.joinAudioWrites()
    }

    private suspend fun installTargetForPresentation(
        expectedPresentationEpoch: Long,
        canInstall: () -> Boolean = { true },
        installTarget: suspend () -> PlaybackTargetResult,
        onStarted: () -> Unit,
        onFailed: (PlaybackTargetResult) -> Unit,
    ): PlaybackTargetResult {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        if (!canInstall()) return PlaybackTargetResult.NOT_READY
        val previousPlayWhenReady = player.playWhenReady
        val previousTarget = healthyActiveTarget()
        val previousTargetEpoch = activeTargetEpoch
        val previousMediaItem = player.currentMediaItem
        presentation.coverVideoForTargetInstallLocked()
        // A pending SDK source has no app ownership receipt; let the coordinator retry its
        // exact retained sources without pausing an unrelated current replacement.
        if (!coordinator.playerCleanupPending.value) player.pause()
        targetInstallationInProgress = true
        return try {
            val result = completePlaybackTargetInstallation(
                installTarget = installTarget,
                presentationStillCurrent = {
                    targetCommands.isOpen() &&
                        presentationEpoch.isCurrent(expectedPresentationEpoch)
                },
                activeTarget = ::healthyActiveTarget,
                onStarted = {
                    ownedMediaItem = player.currentMediaItem
                    audioInterruptions.restoreWithdrawnMuteOnCommitLocked()
                    departedEpoch = null
                    bufferRecovery = null
                    onStarted()
                },
                onFailed = { result ->
                    if (result == PlaybackTargetResult.DEPARTED) withdrawDepartedTargetLocked()
                    else onFailed(result)
                },
            )
            if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
            result
        } finally {
            targetInstallationInProgress = false
            // Also on failure or cancellation, so the load control follows the target that stays.
            startupBuffer?.targetInstallFinished(
                committed = activeTargetEpoch != previousTargetEpoch,
                activeIsLive = _activeTarget.value is AppPlaybackTarget.Live,
            )
            settleRetiredTargetLocked(expectedPresentationEpoch, !currentCoroutineContext().isActive, previousPlayWhenReady)
            val previousTargetStays = targetCommands.isOpen() &&
                presentationEpoch.isCurrent(expectedPresentationEpoch) &&
                previousTarget != null && healthyActiveTarget() == previousTarget &&
                activeTargetEpoch == previousTargetEpoch && player.currentMediaItem == previousMediaItem
            if (previousTargetStays) {
                presentation.restoreVideoAfterFailedInstallLocked(resumesPlaying = foreground && previousPlayWhenReady)
            }
            if (foreground && previousTargetStays) {
                player.playWhenReady = previousPlayWhenReady
                presentation.publishPlayerState()
            }
            if (_activeTarget.value != null && livePlaybackObservation.value != LivePlaybackObservation.Departed) {
                audioTracks.activateAudioAfterInstallLocked()
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
        if (coordinator.playerCleanupPending.value && activeTargetEpoch != null) {
            // A failed stop can leave Media3 non-IDLE, but that source is no longer playable.
            // Keep the target epoch so the serialized retirement retry still owns its fence.
            presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
                presentation.publishFailed(AppPlaybackState.Failed(reason, targetResult))
                presentation.publishDiagnostics()
            }
            return
        }
        val epoch = presentationEpoch.beginIfCurrent(expectedPresentationEpoch) ?: return
        presentationEpoch.publishIfCurrent(epoch) {
            presentation.endTargetPresentationLocked(epoch)
            activeTargetEpoch = null
            livePauseController.dropPendingLivePauseLocked()
            _activeTarget.value = null
            frameRate.reset()
            livePauseController.publishLivePause()
            _recordingSelection.value = null
            _recordingAdmission.value = recordingAdmission
            recordingMarkers.clearRecordingMarkers()
            presentation.publishFailed(AppPlaybackState.Failed(reason, targetResult))
            presentation.publishDiagnostics()
        }
    }

    private fun healthyActiveTarget(): AppPlaybackTarget? = targetCommands.readIfOpen {
        _activeTarget.value.takeIf {
            !coordinator.playerCleanupPending.value &&
            livePlaybackObservation.value != LivePlaybackObservation.Departed && departedEpoch != activeTargetEpoch &&
            activePlayerTargetIsHealthy(
                playerErrorPresent = player.playerError != null,
                playbackState = player.playbackState,
            )
        }
    }

    /**
     * [heldPause]: the viewer paused this channel before it started. It replaces the startup Play
     * as an ordinary pending live pause: one server pause once the grant and the first picture
     * are there, else it is dropped with its notice and playback starts.
     */
    private suspend fun applyPlayIntentToStartedTarget(
        result: PlaybackTargetResult?,
        playWhenReady: Boolean = true,
        heldPause: Boolean = false,
    ) {
        if (result?.isStarted != true) return
        val target = _activeTarget.value ?: return
        val targetEpoch = activeTargetEpoch ?: return
        cancelKeepTimer()
        _backgroundNotice.value = null
        val action = foregroundPlaybackLifecycle.onTargetStarted(target, targetEpoch, servedIntent.get())
        if (foreground) {
            if (playWhenReady && heldPause) {
                player.pause()
                livePauseController.startPendingLivePauseLocked(
                    targetEpoch, wasPlaying = true, failClosed = false, startupPlay = true,
                )
                val availability = livePauseController.currentLivePauseAvailability()
                if (!livePauseController.awaitsFirstPicture(targetEpoch, availability)) {
                    livePauseController.resolvePendingLivePauseLocked()
                }
            } else if (playWhenReady) audioInterruptions.playWithAudioFocusLocked()
            else {
                player.pause()
                // A player that failed during installation never reaches READY to resolve a pause.
                if (state.value is AppPlaybackState.Failed) {
                    stopPausedRetune()
                } else if (livePauseController.readyReachedEpoch != targetEpoch) {
                    // The server pause waits for the first picture (and the grant): a hold before
                    // it leaves the viewer on the tuning screen.
                    livePauseController.startPendingLivePauseLocked(targetEpoch, wasPlaying = false, failClosed = true)
                } else if (coordinator.pauseTimeshift() != TimeshiftCommandResult.ACCEPTED ||
                    state.value is AppPlaybackState.Failed
                ) {
                    stopPausedRetune()
                }
            }
            return
        }
        applyForegroundPlaybackAction(action)
    }

    /**
     * A paused kept channel never plays without its server pause: stop with the tuner-loss notice,
     * or the neutral one while the server has stopped the stream without an issue.
     */
    private suspend fun stopPausedRetune() {
        val notice = currentLiveLossNotice() ?: BackgroundPlaybackNotice.TUNER_LOST
        stopPlayback()
        _backgroundNotice.value = notice
    }

    /**
     * The loss that ends the kept target [epoch]: the current observation's stop or issue, or a
     * stop observed for [epoch] since the keep began. The latter holds even if the server has
     * restarted the stream meanwhile, because the restart discards the kept timeshift.
     */
    private fun keptLiveLoss(epoch: Long?): BackgroundPlaybackNotice? =
        livePlaybackObservation.value.keptLiveLossNotice()
            ?: observedLiveLoss?.takeIf { it.epoch == epoch }?.notice

    /** The current live target's stop or issue; null without a live target. */
    private fun currentLiveLossNotice(): BackgroundPlaybackNotice? =
        (livePlaybackObservation.value as? LivePlaybackObservation.Active)?.keptLiveLossNotice()

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
            resumeRecording = { audioInterruptions.playWithAudioFocusLocked() },
            keepLive = { keep ->
                // Evidence from before this keep is superseded by the current observation below.
                observedLiveLoss = null
                player.pause()
                val paused = coordinator.pauseTimeshift() == TimeshiftCommandResult.ACCEPTED
                val yielded = paused && coordinator.setLivePriority(LiveSubscriptionPriority.YIELD) == TimeshiftCommandResult.ACCEPTED
                if (targetCommands.isOpen() && activeTargetEpoch == keep.epoch) {
                    // A stop or issue that arrived before the keep was admitted releases it now.
                    val lossNotice = if (livePlaybackObservation.value is LivePlaybackObservation.Active) keptLiveLoss(keep.epoch) else null
                    if (!yielded) {
                        applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(keep.epoch))
                    } else if (lossNotice != null) {
                        applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(keep.epoch, lossNotice))
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
                        val resumed = if (restored && kept.resume) audioInterruptions.playWithAudioFocusLocked(resumeTimeshift = true)
                            else TimeshiftCommandResult.ACCEPTED
                        // Focus denial retains interruption ownership; it is not a server failure.
                        val resumeFailed = resumed != TimeshiftCommandResult.ACCEPTED &&
                            resumed != TimeshiftCommandResult.SHUT_DOWN && audioInterruptions.interruption == null
                        if (!restored || resumeFailed) {
                            val channel = (_activeTarget.value as? AppPlaybackTarget.Live)?.channelId
                            // A stop the queued loss check has not handled yet is not a lost tuner.
                            val notice = currentLiveLossNotice()
                                ?: observedLiveLoss?.takeIf { it.epoch == kept.epoch }?.notice
                                ?: BackgroundPlaybackNotice.TUNER_LOST
                            stopPlayback()
                            channel?.let { playLive(it, recovering = false, playWhenReady = kept.resume) }
                            if (resumeFailed) _backgroundNotice.value = notice
                        }
                    }
                }
            },
            notice = { _backgroundNotice.value = it },
        )
    }

    // A committed UI choice outlives the options sheet, including while another command holds the lock.
    fun useAutomaticAudio() = scope.launch {
        targetCommands.serialize(onClosed = {}) {
            audioTracks.applyAutomaticAudioLocked()
        }
    }

    /** Installed and presented target, including same-channel retunes; never pending/stale tracks. */
    fun isQuickListTargetCurrent(epoch: Long): Boolean =
        targetCommands.isOpen() && !targetInstallationInProgress && activeTargetEpoch == epoch &&
            state.value.presented

    /** Preview and Back use the same queue. A retired target cannot change its successor's choice. */
    fun selectQuickListAudio(epoch: Long, override: androidx.media3.common.TrackSelectionOverride?) = scope.launch {
        targetCommands.serialize(onClosed = {}) {
            if (!isQuickListTargetCurrent(epoch)) return@serialize
            audioTracks.selectQuickListAudioLocked(override)
        }
    }

    private fun publishResolvedRecoveryPlayerState(
        fence: LiveRecoveryFence,
        result: PlaybackTargetResult?,
    ) {
        if (reconnectRecoveryEpoch == fence.targetEpoch) {
            if (reconnectRetuneFailed(result)) {
                // A fresh identity, so this resolving attempt cannot claim the next one.
                dispatchLiveRecovery(fence.reason, origin = fence.copy())
                return
            }
            // An attempt that did not run (a Pause owes it again) keeps the target retrying.
            if (result != null) reconnectRecoveryEpoch = null
        }
        if (coordinator.playerCleanupPending.value) return
        if (shouldRepublishPlayerStateAfterRecovery(
                result = result,
                fence = fence,
                activeTarget = _activeTarget.value,
                activeTargetEpoch = activeTargetEpoch,
                observation = session.observation.value,
                healthyActiveTarget = healthyActiveTarget(),
            )
        ) {
            presentation.publishPlayerState(recoveryResolved = true)
        }
    }

    private fun publishPlayerError() {
        targetCommands.runIfOpen { publishPlayerErrorFromPlayer() }
    }

    private fun publishPlayerErrorFromPlayer() {
        // A failed target never completes a pause pressed before its grant. A paused kept-channel
        // retune still has to fail closed, so its pending pause resolves (and stops) serialized.
        val failClosedPauseEpoch = livePauseController.onPlayerErrorUnderAccessLock()
        presentation.publishFailed(
            AppPlaybackState.Failed(
                reason = if (_activeTarget.value is AppPlaybackTarget.Recording) {
                    AppPlaybackFailureReason.RECORDING_READ_FAILED
                } else {
                    AppPlaybackFailureReason.OTHER
                },
                playerErrorCode = player.playerError?.errorCodeName,
                playerErrorNumber = player.playerError?.errorCode,
                playerErrorClassification = classifyPlaybackError(player.playerError),
                subscriptionIssue = lastSubscriptionIssue(),
                deviceFault = isDevicePlaybackFault(player.playerError),
            ),
        )
        livePauseController.publishLivePause()
        presentation.publishDiagnosticsUnderAccessLock()
        if (failClosedPauseEpoch != null) livePauseController.launchPendingLivePauseResolution(failClosedPauseEpoch)
    }

    /** Reports a server skip to the start-up buffer; a cancelled or failed request counts as not accepted. */
    private inline fun <T> serverSkip(accepted: (T) -> Boolean, request: () -> T): T {
        startupBuffer?.timeshiftSeeking()
        var started = false
        try {
            return request().also { started = accepted(it) }
        } finally {
            startupBuffer?.timeshiftSeekFinished(accepted = started)
        }
    }
}

/** Where a recording target stood when recovery (Retry, route restoration) replaced it. */
internal data class RecordingRecoveryPosition(
    val recordingId: DvrEntryId,
    val positionMs: Long,
    val durationMs: Long?,
)

/**
 * Why a kept live target no longer receives the stream, or null while it does. A server stop
 * without an issue is only an interruption; one with an issue, or no target, lost the tuner.
 */
private fun LivePlaybackObservation.keptLiveLossNotice(): BackgroundPlaybackNotice? = when {
    this !is LivePlaybackObservation.Active || subscriptionIssue != null -> BackgroundPlaybackNotice.TUNER_LOST
    serverStopped -> BackgroundPlaybackNotice.INTERRUPTED
    else -> null
}

/** A server stop or issue observed for the live target [epoch]. */
private class ObservedLiveLoss(val epoch: Long, val notice: BackgroundPlaybackNotice)

/** A seek skipped on the server unless it never ran or the server refused it. */
private fun at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.startedServerSkip(): Boolean =
    this is at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Completed &&
        seekCommand.disposition != TimeshiftCommandDisposition.NOT_ACCEPTED
