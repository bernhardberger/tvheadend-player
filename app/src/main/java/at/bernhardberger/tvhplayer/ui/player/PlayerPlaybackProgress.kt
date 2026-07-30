package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import at.bernhardberger.tvhplayer.core.playerPlaybackProgressing

@Composable
internal fun rememberPlayerPlaybackProgressing(player: Player): Boolean {
    fun currentProgressing(): Boolean = playerPlaybackProgressing(
        isPlaying = player.isPlaying,
        playerReady = player.playbackState == Player.STATE_READY,
    )

    var progressing by remember(player) { mutableStateOf(currentProgressing()) }

    DisposableEffect(player) {
        fun update() {
            progressing = currentProgressing()
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = update()

            override fun onPlaybackStateChanged(playbackState: Int) = update()
        }
        player.addListener(listener)
        update()
        onDispose { player.removeListener(listener) }
    }

    return progressing
}
