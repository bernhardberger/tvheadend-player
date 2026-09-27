package at.bernhardberger.tvhplayer.viewmodels

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.toConnectionState
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.liveDiagnosticsForTarget
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvhplayer.core.GlanceSnr
import at.bernhardberger.tvhplayer.core.glanceSnr
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VideoPlayerViewModel(
    private val playbackRuntime: AppPlaybackRuntime,
    private val session: TvheadendSession,
) : ViewModel() {
    val observation = session.observation
    val activeTarget = playbackRuntime.activeTarget
    val connectionState = session.observation
        .map { it.sessionState.toConnectionState() }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            at.bernhardberger.tvhplayer.data.ConnectionState.Disconnected,
        )
    val playbackState = playbackRuntime.state
    val playingLiveChannelId = playbackRuntime.activeTarget
        .map { (it as? AppPlaybackTarget.Live)?.channelId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val livePlaybackObservation = playbackRuntime.livePlaybackObservation
    val diagnostics = playbackRuntime.diagnostics

    /** The live frontend SNR as a glance badge shows it; emits only when the displayed value changes. */
    val liveSnr: StateFlow<GlanceSnr?> = combine(activeTarget, livePlaybackObservation, ::liveSnrOf)
        .distinctUntilChanged()
        // Only the New design's badges collect it; the Current design never starts it.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Reuses the client's (Media3-unstable-marked) live diagnostics target rule.
    @OptIn(UnstableApi::class)
    private fun liveSnrOf(target: AppPlaybackTarget?, observation: LivePlaybackObservation?): GlanceSnr? =
        liveDiagnosticsForTarget(target, (observation as? LivePlaybackObservation.Active)?.diagnostics)
            ?.frontend
            ?.let { glanceSnr(it.relativeSnrPercent, it.absoluteSnrDecibels) }

    fun getPlayerInstance() = playbackRuntime.player

    fun play() = playbackRuntime.play()

    fun pause() = playbackRuntime.pause()

    val hasAudioInterruption: Boolean get() = playbackRuntime.hasAudioInterruption

    suspend fun playChannel(selection: LivePlaybackSelection, intent: Long? = null) =
        playbackRuntime.playLive(selection, intent)

    suspend fun stop() {
        playbackRuntime.stop()
    }

    /** Automatic stop (connection lost, rejected start); records no user stop. */
    suspend fun stopAfterLoss() {
        playbackRuntime.stopAfterLoss()
    }

    /** [viewerRetry]: the viewer pressed Retry (a fresh start); otherwise automatic reconnect recovery. */
    fun retryLiveNow(viewerRetry: Boolean) {
        viewModelScope.launch { playbackRuntime.retryLive(viewerRetry) }
    }

    suspend fun pauseTimeshift() = playbackRuntime.pauseTimeshift()
    suspend fun pauseTimeshiftPlayback() = playbackRuntime.pauseTimeshiftPlayback()

    suspend fun resumeTimeshift() = playbackRuntime.resumeTimeshift()

    suspend fun seekTimeshift(target: at.bernhardberger.tvheadend.sdk.media3.TimeshiftContentTarget) =
        playbackRuntime.seekTimeshift(target)

    suspend fun seekTimeshift(selection: at.bernhardberger.tvheadend.sdk.media3.TimeshiftSeekSelection) =
        playbackRuntime.seekTimeshift(selection)

    suspend fun sampleTimeshiftPresentation() = playbackRuntime.sampleTimeshiftPresentation()

    suspend fun goLive() = playbackRuntime.goLive()

    fun setDiagnosticsEnabled(enabled: Boolean) = playbackRuntime.setDiagnosticsEnabled(enabled)
}
