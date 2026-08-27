package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.data.ChannelEpgRuntime
import at.bernhardberger.tvhplayer.data.TvheadendDataRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VideoPlayerViewModel(
    private val playbackRuntime: AppPlaybackRuntime,
    private val channelRuntime: ChannelEpgRuntime,
    runtime: TvheadendDataRuntime,
) : ViewModel() {
    val connectionState = runtime.connectionState
    val playbackState = playbackRuntime.state
    val playingLiveChannelId = playbackRuntime.activeTarget
        .map { (it as? AppPlaybackTarget.Live)?.channelId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timeshiftState = playbackRuntime.timeshiftState
    val liveSubscriptionFailure = playbackRuntime.livePlaybackIssue
    val diagnostics = playbackRuntime.diagnostics

    fun getPlayerInstance() = playbackRuntime.player

    fun play() = playbackRuntime.play()

    fun pause() = playbackRuntime.pause()

    suspend fun playChannel(channelId: Int) = playbackRuntime.playLive(channelId)

    suspend fun stop() {
        playbackRuntime.stop()
    }

    fun retryLiveNow() {
        viewModelScope.launch { playbackRuntime.retryLive() }
    }

    suspend fun pauseTimeshift() = playbackRuntime.pauseTimeshift()

    suspend fun resumeTimeshift() = playbackRuntime.resumeTimeshift()

    suspend fun seekTimeshift(deltaMs: Long) = playbackRuntime.seekTimeshift(deltaMs)

    suspend fun goLive() = playbackRuntime.goLive()

    fun setDiagnosticsEnabled(enabled: Boolean) = playbackRuntime.setDiagnosticsEnabled(enabled)

    fun epgForChannel(channelId: Int) = channelRuntime.epgForChannel(channelId)
}
