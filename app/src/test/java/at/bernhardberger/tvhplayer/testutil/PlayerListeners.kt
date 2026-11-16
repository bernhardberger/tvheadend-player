package at.bernhardberger.tvhplayer.testutil

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Keeps production listeners available for deterministic frame callbacks in UI fixtures. */
fun observePlayerListeners(player: ExoPlayer, listeners: MutableList<Player.Listener>): ExoPlayer =
    object : ExoPlayer by player {
        override fun addListener(listener: Player.Listener) {
            listeners += listener
            player.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            listeners -= listener
            player.removeListener(listener)
        }
    }
