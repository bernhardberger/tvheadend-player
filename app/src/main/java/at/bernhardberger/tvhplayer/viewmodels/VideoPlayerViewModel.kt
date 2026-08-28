package at.bernhardberger.tvhplayer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.core.toConnectionState
import at.bernhardberger.tvhplayer.playback.LivePlaybackSelection
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import kotlinx.coroutines.flow.SharingStarted
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
    val timeshiftState = playbackRuntime.timeshiftState
    val liveSubscriptionFailure = playbackRuntime.livePlaybackIssue
    val diagnostics = playbackRuntime.diagnostics

    fun getPlayerInstance() = playbackRuntime.player

    fun play() = playbackRuntime.play()

    fun pause() = playbackRuntime.pause()

    suspend fun playChannel(selection: LivePlaybackSelection) = playbackRuntime.playLive(selection)

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

    fun epgForChannel(channelId: ChannelId) = session.observation.map { observation ->
        when (val state = observation.epgState) {
            is at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState.Current -> state.snapshot
            is at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState.Stale -> state.snapshot
            is at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState.Synchronizing -> state.staleSnapshot
            at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState.Empty -> null
        }?.events?.filter { it.channelId == channelId }.orEmpty()
    }
}
