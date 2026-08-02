package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvhplayer.htsp.ChannelEpgRuntime
import at.bernhardberger.tvhplayer.htsp.TvheadendClient
import at.bernhardberger.tvhplayer.player.PlaybackRuntime
import kotlinx.coroutines.launch

class VideoPlayerViewModel(
    private val playbackRuntime: PlaybackRuntime,
    private val channelRuntime: ChannelEpgRuntime,
    client: TvheadendClient,
) : ViewModel() {
    val connectionState = client.connectionState
    val playbackState = playbackRuntime.state
    val activeServiceId = playbackRuntime.activeServiceId
    val playingLiveServiceId = playbackRuntime.playingLiveServiceId
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
