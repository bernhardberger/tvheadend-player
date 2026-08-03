package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.client.ChannelEpgRuntime
import at.bernhardberger.tvheadend.client.TvheadendClient
import at.bernhardberger.tvheadend.playback.ExperimentalPlaybackDiagnosticsApi
import at.bernhardberger.tvheadend.playback.PlaybackRuntime
import kotlinx.coroutines.launch

@OptIn(ExperimentalPlaybackDiagnosticsApi::class)
class VideoPlayerViewModel(
    private val playbackRuntime: PlaybackRuntime,
    private val channelRuntime: ChannelEpgRuntime,
    client: TvheadendClient,
) : ViewModel() {
    val connectionState = client.connectionState
    val playbackState = playbackRuntime.state
    val activeChannelId = playbackRuntime.activeChannelId
    val playingLiveChannelId = playbackRuntime.playingLiveChannelId
    val timeshiftState = playbackRuntime.timeshiftState
    val liveSubscriptionFailure = playbackRuntime.liveSubscriptionFailure
    val diagnostics = playbackRuntime.diagnostics

    fun getPlayerInstance() = playbackRuntime.player

    suspend fun playService(serviceId: Int): Boolean = playbackRuntime.playLive(serviceId)

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
