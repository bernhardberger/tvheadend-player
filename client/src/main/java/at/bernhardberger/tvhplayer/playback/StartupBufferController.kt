@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.STARTUP_BUFFER_AUTOMATIC
import at.bernhardberger.tvhplayer.settings.StartupBufferLearningState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Feeds [StartupBufferLoadControl] and learns the Automatic start-up buffer.
 *
 * The playback runtime reports which kind of target it is about to install
 * ([targetInstalling], [targetInstallFinished]), when a live start's play
 * intent is applied ([liveStartApplied]), and when it asks the server for a
 * timeshift skip ([timeshiftSeeking], [timeshiftSeekFinished]); everything
 * else comes from player and analytics callbacks on the application thread.
 * Each live start the viewer waits for arms one [StartupBufferWindow]. The
 * window belongs to the setting, level and server identity it was armed with:
 * a change to the setting or identity cuts it, and its verdict is persisted
 * only when it was armed in Automatic at the level still learned. Nothing is
 * armed while audio is disabled, because a window could not see underruns.
 *
 * The controller keeps the active target's kind for its windows; the load
 * control classifies each start from the period's own media item.
 */
class StartupBufferController(
    private val loadControl: StartupBufferLoadControl,
    private val settings: PlayerSettingsStore,
    serverIdentity: Flow<String?>,
    private val scope: CoroutineScope,
) : Player.Listener, AnalyticsListener {
    private val window = StartupBufferWindow()
    private var windowTimer: Job? = null
    private var skipGrace: Job? = null
    private var targetLive = false
    private var player: Player? = null
    private var settingMillis = STARTUP_BUFFER_AUTOMATIC
    private var profile: String? = null
    private var windowEffect: StartupBufferInEffect? = null
    private var windowProfile: String? = null
    private var audioDisabled = false

    private val mutableInEffect = MutableStateFlow(
        StartupBufferPolicy.inEffect(STARTUP_BUFFER_AUTOMATIC, StartupBufferLearningState(), null),
    )

    /** The buffer the next live start waits for, and whether it is learned. */
    val inEffect: StateFlow<StartupBufferInEffect> = mutableInEffect.asStateFlow()

    init {
        scope.launch {
            var bound = false
            combine(
                settings.playerSettings.map { it.startupBufferMillis }.distinctUntilChanged(),
                settings.startupBufferLearning,
                serverIdentity.distinctUntilChanged(),
            ) { millis, learning, identity ->
                // A window measures the setting and server it started with.
                if (bound && (millis != settingMillis || identity != profile)) cut()
                bound = true
                settingMillis = millis
                profile = identity
                StartupBufferPolicy.inEffect(millis, learning, identity)
            }.distinctUntilChanged().collect { effect ->
                loadControl.setLiveStartBufferMillis(effect.millis)
                mutableInEffect.value = effect
            }
        }
    }

    /** The kind of the target being installed or active, which decides whether a window is armed. */
    internal val isLiveTarget: Boolean get() = targetLive

    fun attach(player: ExoPlayer) {
        this.player = player
        audioDisabled = C.TRACK_TYPE_AUDIO in player.trackSelectionParameters.disabledTrackTypes
        player.addListener(this)
        player.addAnalyticsListener(this)
    }

    /** Called before the runtime prepares a target, so the load control knows its kind. */
    fun targetInstalling(live: Boolean) {
        targetLive = live
        loadControl.clearLiveSeekStart()
        cut()
    }

    /**
     * Called when an install ends, also on failure or cancellation. The target
     * that stays, of kind [activeIsLive], keeps the load control; the window
     * waits for [liveStartApplied].
     */
    fun targetInstallFinished(committed: Boolean, activeIsLive: Boolean) {
        targetLive = activeIsLive
        loadControl.clearLiveSeekStart()
        cut()
    }

    /**
     * Called after the runtime applied the play intent of a committed live
     * start. A paused start, or one denied audio focus, arms nothing.
     */
    fun liveStartApplied() {
        armIfPlayIntended()
        // Playback that already began while the intent was applied starts the window now.
        if (player?.isPlaying == true && window.startIfArmed()) startTimer()
    }

    /**
     * Called before the runtime asks the server to skip within the timeshift
     * buffer or back to live. The skip resets the stream and plays on without a
     * player seek: the buffering that follows is not trouble, and the start
     * after it is a live start with its own window.
     */
    fun timeshiftSeeking() {
        loadControl.liveSeekStarting(alreadyBuffering = player?.playbackState == Player.STATE_BUFFERING)
        cut()
        armIfPlayIntended()
    }

    /**
     * Called with the outcome of [timeshiftSeeking]'s request, also when it was
     * cancelled or failed; a skip that never ran arms nothing. An accepted skip
     * that does not rebuffer within [StartupBufferLoadControl.SKIP_REBUFFER_GRACE_MS]
     * was absorbed: the start after it is watched from then, and a later
     * rebuffer is graded normally.
     */
    fun timeshiftSeekFinished(accepted: Boolean) {
        if (!accepted) {
            loadControl.clearLiveSeekStart()
            cut()
            return
        }
        loadControl.liveSeekFinished()
        stopSkipGrace()
        skipGrace = scope.launch {
            delay(StartupBufferLoadControl.SKIP_REBUFFER_GRACE_MS)
            skipGrace = null
            if (player?.isPlaying != true) return@launch
            loadControl.clearLiveSeekStart()
            if (window.startIfArmed()) startTimer()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) cut()
    }

    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) {
        // Muting disables the audio renderer; underruns before and after do not compare.
        val disabled = C.TRACK_TYPE_AUDIO in parameters.disabledTrackTypes
        if (disabled != audioDisabled) cut()
        audioDisabled = disabled
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            if (window.startIfArmed()) startTimer()
        } else if (!rebufferedWhilePlaying()) {
            cut()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> rebufferedWhilePlaying()
            Player.STATE_IDLE, Player.STATE_ENDED -> cut()
        }
    }

    override fun onPlayerError(error: PlaybackException) = cut()

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        window.underrun()?.let(::record)
    }

    /** Buffering while the user still wants playback is a rebuffer; skips re-arm first. */
    private fun rebufferedWhilePlaying(): Boolean {
        val player = player ?: return false
        if (!player.playWhenReady || player.playbackState != Player.STATE_BUFFERING) return false
        window.rebuffered()?.let(::record)
        return true
    }

    private fun armIfPlayIntended() {
        if (!targetLive || audioDisabled || player?.playWhenReady != true) return
        stopTimer()
        window.arm()
        windowEffect = mutableInEffect.value
        windowProfile = profile
    }

    private fun cut() {
        stopTimer()
        stopSkipGrace()
        window.cut()
    }

    private fun stopSkipGrace() {
        skipGrace?.cancel()
        skipGrace = null
    }

    private fun startTimer() {
        stopTimer()
        windowTimer = scope.launch {
            delay(StartupBufferPolicy.WINDOW_MILLIS)
            windowTimer = null
            window.elapsed()?.let(::record)
        }
    }

    private fun stopTimer() {
        windowTimer?.cancel()
        windowTimer = null
    }

    private fun record(verdict: StartupBufferVerdict) {
        stopTimer()
        val effect = windowEffect ?: return
        if (!effect.automatic) return
        val identity = windowProfile ?: return
        scope.launch {
            try {
                settings.updateStartupBufferLearning { stored ->
                    // Only a window measured at the level still learned moves it.
                    if (StartupBufferPolicy.learningFor(stored, identity).levelMillis != effect.millis) stored
                    else StartupBufferPolicy.after(stored, verdict, identity)
                }
            } catch (_: java.io.IOException) {
                // A storage failure must not interrupt playback; the level stays as it was.
            }
        }
    }
}
