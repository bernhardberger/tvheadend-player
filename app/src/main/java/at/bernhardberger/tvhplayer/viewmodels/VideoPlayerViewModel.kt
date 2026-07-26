package at.bernhardberger.tvhplayer.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import at.bernhardberger.tvhplayer.htsp.HtspService
import at.bernhardberger.tvhplayer.player.PlayerSession
import at.bernhardberger.tvhplayer.repositories.TvhRepository

class VideoPlayerViewModel(
    private val playerSession: PlayerSession,
    private val repo: TvhRepository,
    htspService: HtspService
) : ViewModel() {
    val connectionState = htspService.state
    val playbackState = playerSession.state
    val timeshiftState = playerSession.timeshiftState
    val diagnostics = playerSession.diagnostics

    fun getPlayerInstance(context: Context) =
        playerSession.getOrCreatePlayer(context)

    suspend fun playService(context: Context, serviceId: Int) {
        playerSession.playService(context, serviceId)
    }

    suspend fun stop() {
        playerSession.stop()
    }

    suspend fun pauseTimeshift() = playerSession.pauseTimeshift()

    suspend fun resumeTimeshift() = playerSession.resumeTimeshift()

    suspend fun seekTimeshift(deltaMs: Long) = playerSession.seekTimeshift(deltaMs)

    suspend fun goLive() = playerSession.goLive()

    fun setDiagnosticsEnabled(enabled: Boolean) = playerSession.setDiagnosticsEnabled(enabled)

    fun epgForChannel(channelId: Int) = repo.epgForChannel(channelId)
}
