@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

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
import at.bernhardberger.tvheadend.sdk.core.DvrProgressPolicy
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
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import kotlin.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.job

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
    private val _state = MutableStateFlow<AppPlaybackState>(AppPlaybackState.Idle)
    private val _activeTarget = MutableStateFlow<AppPlaybackTarget?>(null)
    private val livePauseController = LivePauseController(
        player = player,
        coordinator = coordinator,
        scope = scope,
        targetCommands = targetCommands,
        livePlaybackObservation = coordinator.livePlaybackObservation,
        _state = _state,
        _activeTarget = _activeTarget,
        activeTargetEpoch = { activeTargetEpoch },
        foreground = { foreground },
        interruption = { audioInterruptions.interruption },
        stopPausedRetune = ::stopPausedRetune,
        playStartedTarget = { audioInterruptions.playWithAudioFocusLocked() },
        resumeOwedRecovery = { resumeInterruptedLiveRecoveryLocked() },
        pendingLiveSelectionPlayWhenReady = ::pendingLiveSelectionPlayWhenReady,
    )
    val livePause = livePauseController.livePause
    val livePauseNotice = livePauseController.livePauseNotice
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
    /**
     * The epoch of the target the active one continues: a live recovery retune of the same
     * channel keeps it, every other commit begins its own. Written after [activeTargetEpoch], so a
     * reader that sees a new value also sees that commit's epoch.
     */
    @Volatile
    private var targetContinuity: Long? = null
    @Volatile
    private var targetInstallationInProgress = false
    /** Playlist replacements on the player: the SDK installs every target's source as one. */
    @Volatile
    private var playerSources = 0L
    /** The live install in progress, for the escalations its new source already reports. */
    @Volatile
    private var liveInstall: LiveInstallAttribution? = null

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
    // The recovery seek owed to the installed target until its timeline can take it; under
    // the target access lock, because player callbacks apply it outside target commands.
    private var pendingRecordingRecovery: PendingRecordingRecovery? = null
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
        _state = _state,
        _activeTarget = _activeTarget,
        activeTargetEpoch = { activeTargetEpoch },
        targetInstallationInProgress = { targetInstallationInProgress },
        publishPlayerErrorFromPlayer = ::publishPlayerErrorFromPlayer,
    )
    /** Written under the command serializer, read at arrival by the transport commands. */
    @Volatile
    private var foreground = true
    private val audioInterruptions: AudioInterruptionController = AudioInterruptionController(
        player = player,
        coordinator = coordinator,
        scope = scope,
        targetCommands = targetCommands,
        audioFocus = audioFocus,
        audioSelection = audioSelection,
        livePauseController = livePauseController,
        livePlaybackObservation = coordinator.livePlaybackObservation,
        _activeTarget = _activeTarget,
        foreground = { foreground },
        activeTargetEpoch = { activeTargetEpoch },
        targetInstallationInProgress = { targetInstallationInProgress },
        cancelRecoveryForInterruptionLocked = liveRecovery::cancelOnInterruptionLocked,
        resumeInterruptedRecoveryLocked = ::resumeInterruptedLiveRecoveryLocked,
    )
    val isInterruptionMuted: Boolean get() = audioInterruptions.interruptionMuted
    val hasAudioInterruption: Boolean get() = audioInterruptions.interruption != null
    val state = _state.asStateFlow()
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
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            audioTracks.onMediaItemTransition()
        }

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
            audioTracks.onTrackSelectionParametersChanged(parameters)
        }

        override fun onTracksChanged(tracks: Tracks) {
            audioTracks.onTracksChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) {
                if (policy.trace.enabled && playbackState == Player.STATE_READY) {
                    policy.trace.ready(activeTargetEpoch)
                }
                presentation.publishPlayerState()
                if (playbackState == Player.STATE_READY) livePauseController.onTargetReady(activeTargetEpoch)
            }
            applyPendingRecordingRecovery()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) playerSources++
            applyPendingRecordingRecovery()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // Any other seek (the viewer's, a marker's) replaces the owed recovery seek.
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                targetCommands.runIfOpen { pendingRecordingRecovery = null }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) presentation.publishPlayerState()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!targetInstallationInProgress && targetCommands.isOpen()) publishPlayerError()
        }
    }

    init {
        targetCommands.runIfOpen {
            player.addListener(listener)
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

    private suspend fun startLiveSelection(
        selection: LivePlaybackSelection,
        intent: Long?,
        generation: Long,
    ): PlaybackTargetResult? =
        targetCommands.serialize(onClosed = { PlaybackTargetResult.SHUT_DOWN }) {
            if (intent != null && isPlaybackIntentStopped(intent)) {
                endLiveSelection(generation)
                return@serialize null
            }
            if (policy.trace.enabled) policy.trace.tuneAdmitted()
            lastLiveChannelId = selection.channelId
            forgetRecordingRecoveryLocked()
            liveRecovery.resetBackoffLocked()
            val result = playLive(
                channelId = selection.channelId,
                recovering = false,
                servedGeneration = generation,
            )
            // A commit ended the selection. Otherwise the start failed while the previous live
            // target stayed: a Pause the viewer gave meanwhile is still the latest transport
            // intent, so it applies to that retained target.
            if (endLiveSelection(generation)) pauseRetainedLiveTargetLocked()
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
    ): PlaybackTargetResult? {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        presentationEpoch.publishIfCurrent(expectedPresentationEpoch) {
            if (!recovering && _activeTarget.value == null || viewerRetry && healthyActiveTarget() == null) {
                _state.value = AppPlaybackState.Starting
            }
        }
        val playerSettings = settings.playerSettings.first()
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        audioTracks.configureAudioOutputBeforeFirstTargetLocked(playerSettings)
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
        audioTracks.loadStoredAudioLocked(channelId)?.let { return it }
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
                        audioInterruptions.retainInterruptionMuteLocked()
                    } else audioInterruptions.clearAudioInterruptionLocked()
                    // A recovery retune replaces the very target its fence validated in this command.
                    val continuesTarget = recoverySelection != null &&
                        activeTargetEpoch == expectedPresentationEpoch &&
                        _activeTarget.value == AppPlaybackTarget.Live(selection.channelId)
                    activeTargetEpoch = epoch
                    if (!continuesTarget) targetContinuity = epoch
                    servedGeneration?.let(servedIntent::set)
                    livePauseController.dropPendingLivePauseLocked()
                    // From here on Pause and Play address this target itself.
                    heldPause = servedGeneration != null && endLiveSelection(servedGeneration)
                    livePauseController.timeshiftRequestedEpoch = epoch.takeIf { timeshiftPeriod > Duration.ZERO }
                    _activeTarget.value = AppPlaybackTarget.Live(selection.channelId)
                    install.committedEpoch = epoch
                    lastLiveChannelId = selection.channelId
                    _recordingSelection.value = null
                    _recordingAdmission.value = null
                    recordingMarkers.clearRecordingMarkers()
                    _state.value = AppPlaybackState.Starting
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
            if (retainInterruptionMute && foreground) player.play()
            else applyPlayIntentToStartedTarget(result, playWhenReady, heldPause = heldPause)
            // Only a start the viewer is waiting for opens a start-up buffer window.
            startupBuffer?.liveStartApplied()
        }
        return result
    }

    suspend fun playRecording(
        selection: RecordingPlaybackSelection,
        start: RecordingPlaybackStart,
    ): PlaybackTargetResult? {
        playbackRequests.incrementAndGet()
        return targetCommands.serialize(onClosed = { PlaybackTargetResult.SHUT_DOWN }) {
            // The viewer's start choice applies to a new play; no earlier position carries over.
            forgetRecordingRecoveryLocked()
            playRecordingLocked(selection.recordingId, start)
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
    private fun noteRecordingInterruptionLocked() {
        val target = _activeTarget.value as? AppPlaybackTarget.Recording ?: return
        recordingRecovery = targetCommands.readIfOpen {
            pendingRecordingRecovery
                ?.takeIf { it.epoch == activeTargetEpoch && it.position.recordingId == target.recordingId }
                ?.position
                ?: RecordingRecoveryPosition(
                    recordingId = target.recordingId,
                    positionMs = player.currentPosition,
                    durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 },
                )
        } ?: recordingRecovery
    }

    /** A new play, live, or Stop: nothing from an earlier recording target carries over. */
    private fun forgetRecordingRecoveryLocked() {
        recordingRecovery = null
        targetCommands.runIfOpen { pendingRecordingRecovery = null }
    }

    /**
     * Seeks the installed recording to its owed recovery position once its timeline can take
     * the seek, by the rule the SDK's own resume follows: a known duration, a position before
     * its end, a seekable item. An earlier seek into a progressive item not yet seekable is
     * lost to its start. A replaced, failed, or stopped target drops the seek.
     */
    private fun applyPendingRecordingRecovery() {
        targetCommands.runIfOpen {
            val pending = pendingRecordingRecovery ?: return@runIfOpen
            if (targetInstallationInProgress) return@runIfOpen
            val target = _activeTarget.value as? AppPlaybackTarget.Recording
            if (pending.epoch != activeTargetEpoch || target?.recordingId != pending.position.recordingId) {
                pendingRecordingRecovery = null
                return@runIfOpen
            }
            // A failed target keeps the seek owed, so its own recovery continues there.
            if (player.playerError != null) return@runIfOpen
            val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: return@runIfOpen
            if (pending.position.positionMs >= durationMs) {
                pendingRecordingRecovery = null
                return@runIfOpen
            }
            if (!player.isCurrentMediaItemSeekable) return@runIfOpen
            pendingRecordingRecovery = null
            player.seekTo(pending.position.positionMs)
        }
    }

    private suspend fun playRecordingLocked(
        recordingId: DvrEntryId,
        start: RecordingPlaybackStart,
        recoverAt: RecordingRecoveryPosition? = null,
    ): PlaybackTargetResult? {
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        val playerSettings = settings.playerSettings.first()
        if (!targetCommands.isOpen()) return PlaybackTargetResult.SHUT_DOWN
        audioTracks.configureAudioOutputBeforeFirstTargetLocked(playerSettings)
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
        var recoverySeekMs: Long? = null
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
                        recoverySeekMs = recordingRecoverySeekMs(
                            recovery = recoverAt,
                            recordingId = recordingId,
                            completedRecording = admission is RecordingPlaybackAdmission.Completed,
                        )
                        if (!targetCommands.isOpen()) {
                            PlaybackTargetResult.SHUT_DOWN
                        } else {
                            // Recovery owns the position: the SDK schedules no bookmark seek.
                            coordinator.setRecordingTarget(
                                binding.binding,
                                if (recoverySeekMs != null) RecordingPlaybackStart.START_OVER else start,
                            )
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
                    activeTargetEpoch = epoch
                    targetContinuity = epoch
                    livePauseController.dropPendingLivePauseLocked()
                    _activeTarget.value = AppPlaybackTarget.Recording(selection.recordingId)
                    _recordingSelection.value = selection
                    _recordingAdmission.value = admission
                    recordingMarkers.observeRecordingMarkersLocked(requireNotNull(installedBinding))
                    _state.value = AppPlaybackState.Starting
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
            val recovery = recoverAt
            val positionMs = recoverySeekMs
            recordingRecovery = null
            targetCommands.runIfOpen {
                pendingRecordingRecovery = if (positionMs != null && recovery != null) {
                    PendingRecordingRecovery(
                        epoch = activeTargetEpoch,
                        position = recovery.copy(positionMs = positionMs),
                    )
                } else {
                    null
                }
            }
            applyPendingRecordingRecovery()
            applyPlayIntentToStartedTarget(result)
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
            onClosed = { PlaybackStopResult.ShutDown },
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
        onClosed = { PlaybackStopResult.ShutDown },
    ) {
        noteRecordingInterruptionLocked()
        targetCommands.runIfOpen { pendingRecordingRecovery = null }
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
        livePauseController.dropPendingLivePauseLocked()
        audioInterruptions.clearAudioInterruptionLocked()
        recordingMarkers.clearRecordingMarkers()
        val epoch = presentationEpoch.begin()
        presentation.endTargetPresentationLocked(epoch)
        val currentJob = currentCoroutineContext().job
        liveRecovery.cancelOnStopLocked(currentJob)
        val result = coordinator.stop()
        if (!targetCommands.isOpen()) return PlaybackStopResult.ShutDown
        presentationEpoch.publishIfCurrent(epoch) {
            _activeTarget.value = null
            _recordingSelection.value = null
            _recordingAdmission.value = null
            activeTargetEpoch = null
            livePauseController.publishLivePause()
            _state.value = AppPlaybackState.Idle
            presentation.publishDiagnostics()
        }
        return result
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
            playLive(
                channelId = channelId,
                recovering = true,
                servedGeneration = generation,
                viewerRetry = viewerRetry,
            )
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
        audioInterruptions.resumeAfterInterruption = false
        player.pause()
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
    suspend fun seekTimeshift(target: at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget) =
        targetCommands.serialize(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) {
            serverSkip({ it.startedServerSkip() }) { coordinator.seekTimeshift(target) }
        }

    suspend fun seekTimeshift(selection: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekSelection) =
        targetCommands.serialize(
            onClosed = { at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Replaced },
        ) {
            presentation.diagnoseSeekLocked(selection) {
                serverSkip({ it.startedServerSkip() }) { coordinator.seekTimeshift(selection) }
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
        if (audioInterruptions.interruptionMuted) {
            audioInterruptions.playWithAudioFocusLocked()
            return false
        }
        audioInterruptions.resumeAfterInterruption = false
        player.pause()
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
                if (live && !timeshift && !livePauseController.currentLivePauseAvailability().acceptsPause()) return@serialize
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
    fun setRefreshRateMatchingEnabled(enabled: Boolean) {
        audioTracks.setRefreshRateMatchingEnabled(enabled)
    }
    fun onRecoveryRequired(reason: PlaybackRecoveryReason) {
        // The escalation belongs to the target playing when the SDK reports it, not to a
        // replacement that commits before it is admitted.
        val origin = currentLiveRecoveryFence(
            reason = reason,
            observation = session.observation.value,
            activeTarget = _activeTarget.value,
            activeTargetEpoch = activeTargetEpoch,
        )
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
                val installed = install?.committedEpoch?.let { LiveRecoveryFence(reason, install.selection, it) }
                val requestedEpoch = installed?.targetEpoch ?: reportedEpoch
                if (foregroundPlaybackLifecycle.isKeeping(activeTargetEpoch)) {
                    if (requestedEpoch != activeTargetEpoch) return@admit null
                    applyForegroundPlaybackAction(foregroundPlaybackLifecycle.releaseKept(requestedEpoch, BackgroundPlaybackNotice.TUNER_LOST))
                    return@admit null
                }
                if (!foreground) return@admit null
                // Checked before the budget: a replacement that committed meanwhile owns its own.
                val fence = (installed ?: origin)?.takeIf(::liveRecoveryFenceMatches) ?: return@admit null
                if (audioInterruptions.interruptionPaused || viewerPausedLocked(fence.targetEpoch)) {
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
                    _state.value = AppPlaybackState.Recovering(
                        reason = fence.reason,
                        retryDelayMillis = attempt.delayMillis,
                    )
                    presentation.publishDiagnostics()
                }
                fence to attempt
            },
            retryLocked = ::retryResumedLiveRecoveryLocked,
        )
    }

    private suspend fun retryLiveRecoveryLocked(fence: LiveRecoveryFence): PlaybackTargetResult? {
        if (audioInterruptions.interruptionPaused || !foreground) return null
        if (!fence.matches(
                activeTarget = _activeTarget.value,
                activeTargetEpoch = activeTargetEpoch,
                observation = session.observation.value,
            )
        ) {
            return null
        }
        return playLive(
            channelId = fence.selection.channelId,
            recovering = true,
            expectedPresentationEpoch = fence.targetEpoch,
            recoverySelection = fence.selection,
        )
    }

    /**
     * Every attempt (after its backoff, or resumed) revalidates the viewer's transport intent
     * where it runs: a Pause accepted meanwhile wins, and the retune's startup Play would
     * override it. The attempt, already budgeted, then stays owed until the viewer plays again.
     */
    private suspend fun retryResumedLiveRecoveryLocked(fence: LiveRecoveryFence): PlaybackTargetResult? {
        if (foreground && !audioInterruptions.interruptionPaused && viewerPausedLocked(fence.targetEpoch) &&
            fence.matches(
                activeTarget = _activeTarget.value,
                activeTargetEpoch = activeTargetEpoch,
                observation = session.observation.value,
            )
        ) {
            liveRecovery.oweAgainLocked(fence)
            return null
        }
        return retryLiveRecoveryLocked(fence)
    }

    /** The viewer's own Pause holds [epoch]: paused, or a pause still pending for it. */
    private fun viewerPausedLocked(epoch: Long): Boolean =
        !player.playWhenReady || livePauseController.pendingLivePauseEpoch == epoch

    /**
     * Playback may continue after an audio interruption (focus returned, a play acquired it, or a
     * rejected hold went on muted) or the viewer's Pause: the admitted recovery the interruption
     * or Pause stopped retunes its target now, and a recovery requested meanwhile is admitted
     * now, if that target (channel, epoch and session) is still the one playing. Either stays
     * owed while the interruption or the viewer's own Pause holds playback.
     */
    private fun resumeInterruptedLiveRecoveryLocked() {
        val interrupted = liveRecovery.interruptedRecovery
        if (interrupted != null && !liveRecoveryFenceMatches(interrupted)) liveRecovery.dropInterruptedLocked()
        val deferred = liveRecovery.deferredRecovery
        if (deferred != null && !liveRecoveryFenceMatches(deferred)) liveRecovery.dropDeferredLocked()
        val epoch = activeTargetEpoch ?: return
        if (liveRecovery.interruptedRecovery == null && liveRecovery.deferredRecovery == null) return
        if (!foreground || audioInterruptions.interruptionPaused || viewerPausedLocked(epoch)) return
        if (liveRecovery.interruptedRecovery != null) {
            liveRecovery.resumeInterruptedLocked(::retryResumedLiveRecoveryLocked) { fence ->
                presentationEpoch.publishIfCurrent(fence.targetEpoch) {
                    _state.value = AppPlaybackState.Recovering(reason = fence.reason, retryDelayMillis = 0L)
                    presentation.publishDiagnostics()
                }
            }
            return
        }
        // Admission claims its attempt (and backoff) now, as for any other escalation, for the
        // target the request was deferred for.
        liveRecovery.takeDeferredLocked()?.let { dispatchLiveRecovery(it.reason, origin = it) }
    }

    private fun liveRecoveryFenceMatches(fence: LiveRecoveryFence): Boolean = fence.matches(
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
        _state.value = recoveryExhaustedState(stopResult, recoveryReason)
        presentation.publishDiagnostics()
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
        pendingRecovery?.join()
        livePlaybackObservationJob.join()
        settingsJob.join()
        targetCommands.awaitIdle {
            recordingRecovery = null
            pendingRecordingRecovery = null
            livePauseController.dropPendingLivePauseLocked()
            dropHeldLiveSelectionPause()
            audioInterruptions.clearAudioInterruptionLocked()
            presentation.removeTargetFrameListenerLocked()
            player.removeListener(listener)
            presentation.seekDiagnosticsListener?.let(player::removeAnalyticsListener)
            audioSelection.clear(player)
        }
        audioTracks.joinAudioWrites()
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
        presentation.coverVideoForTargetInstallLocked()
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
            // Also on failure or cancellation, so the load control follows the target that stays.
            startupBuffer?.targetInstallFinished(
                committed = activeTargetEpoch != previousTargetEpoch,
                activeIsLive = _activeTarget.value is AppPlaybackTarget.Live,
            )
            val previousTargetStays = targetCommands.isOpen() &&
                presentationEpoch.isCurrent(expectedPresentationEpoch) &&
                previousTarget != null && healthyActiveTarget() == previousTarget &&
                activeTargetEpoch == previousTargetEpoch && player.currentMediaItem == previousMediaItem
            if (previousTargetStays) presentation.restoreVideoAfterFailedInstallLocked()
            if (foreground && previousTargetStays) {
                player.playWhenReady = previousPlayWhenReady
                presentation.publishPlayerState()
            }
            audioTracks.activateAudioAfterInstallLocked()
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
            presentation.endTargetPresentationLocked(epoch)
            activeTargetEpoch = null
            livePauseController.dropPendingLivePauseLocked()
            _activeTarget.value = null
            livePauseController.publishLivePause()
            _recordingSelection.value = null
            _recordingAdmission.value = recordingAdmission
            recordingMarkers.clearRecordingMarkers()
            _state.value = AppPlaybackState.Failed(reason, targetResult)
            presentation.publishDiagnostics()
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
                if (_state.value is AppPlaybackState.Failed) {
                    stopPausedRetune()
                } else if (livePauseController.readyReachedEpoch != targetEpoch) {
                    // The server pause waits for the first picture (and the grant): a hold before
                    // it leaves the viewer on the tuning screen.
                    livePauseController.startPendingLivePauseLocked(targetEpoch, wasPlaying = false, failClosed = true)
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
                        val resumed = if (restored && kept.resume) audioInterruptions.playWithAudioFocusLocked(resumeTimeshift = true)
                            else TimeshiftCommandResult.ACCEPTED
                        // Focus denial retains interruption ownership; it is not a server failure.
                        val resumeFailed = resumed != TimeshiftCommandResult.ACCEPTED &&
                            resumed != TimeshiftCommandResult.SHUT_DOWN && audioInterruptions.interruption == null
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

    // A committed UI choice outlives the options sheet, including while another command holds the lock.
    fun useAutomaticAudio() = scope.launch {
        targetCommands.serialize(onClosed = {}) {
            audioTracks.applyAutomaticAudioLocked()
        }
    }

    /** Installed and presented target, including same-channel retunes; never pending/stale tracks. */
    fun isQuickListTargetCurrent(epoch: Long): Boolean =
        targetCommands.isOpen() && !targetInstallationInProgress && activeTargetEpoch == epoch &&
            _state.value.presented

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
        _state.value = AppPlaybackState.Failed(
            reason = if (_activeTarget.value is AppPlaybackTarget.Recording) {
                AppPlaybackFailureReason.RECORDING_READ_FAILED
            } else {
                AppPlaybackFailureReason.OTHER
            },
            playerErrorCode = player.playerError?.errorCodeName,
            subscriptionIssue = lastSubscriptionIssue(),
        )
        livePauseController.publishLivePause()
        presentation.publishDiagnosticsFromPlayer()
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

/** A recovery seek owed to the recording target installed under [epoch]. */
private data class PendingRecordingRecovery(
    val epoch: Long?,
    val position: RecordingRecoveryPosition,
)

/**
 * The position recovery of [recordingId] continues at, or null to keep the viewer's start.
 * Only the same completed recording carries over. A growing recording keeps its start: its
 * extent moves and a remembered offset is not proven seekable. A position at or past the
 * point where an orderly exit counts as finished starts per the viewer's choice instead.
 */
internal fun recordingRecoverySeekMs(
    recovery: RecordingRecoveryPosition?,
    recordingId: DvrEntryId,
    completedRecording: Boolean,
    orderlyCompletionFraction: Double = DvrProgressPolicy().orderlyCompletionFraction,
): Long? {
    if (recovery == null || recovery.recordingId != recordingId || !completedRecording) return null
    if (recovery.positionMs <= 0) return null
    val durationMs = recovery.durationMs
    if (durationMs != null && recovery.positionMs >= durationMs * orderlyCompletionFraction) return null
    return recovery.positionMs
}

/** A seek skipped on the server unless it never ran or the server refused it. */
private fun at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.startedServerSkip(): Boolean =
    this is at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentSeekResult.Completed &&
        seekCommand.disposition != TimeshiftCommandDisposition.NOT_ACCEPTED
