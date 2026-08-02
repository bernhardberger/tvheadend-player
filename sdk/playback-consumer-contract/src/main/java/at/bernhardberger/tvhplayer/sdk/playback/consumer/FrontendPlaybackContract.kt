package at.bernhardberger.tvhplayer.sdk.playback.consumer

import android.content.Context
import androidx.media3.common.Player
import at.bernhardberger.tvhplayer.htsp.ChannelEpgRuntime
import at.bernhardberger.tvhplayer.htsp.DvrRuntime
import at.bernhardberger.tvhplayer.htsp.TvheadendClient
import at.bernhardberger.tvhplayer.player.PlaybackPreferencesProvider
import at.bernhardberger.tvhplayer.player.PlaybackRuntime
import at.bernhardberger.tvhplayer.player.createMedia3PlaybackRuntime

/** Compile contract for an Android frontend that consumes only the playback SDK. */
class FrontendPlaybackContract(
    val client: TvheadendClient,
    val channels: ChannelEpgRuntime,
    val dvr: DvrRuntime,
    val playback: PlaybackRuntime,
) {
    val player: Player = playback.player

    fun createPlaybackRuntime(
        context: Context,
        preferences: PlaybackPreferencesProvider,
    ): PlaybackRuntime = createMedia3PlaybackRuntime(
        context = context,
        client = client,
        preferencesProvider = preferences,
    )
}
