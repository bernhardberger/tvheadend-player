package at.bernhardberger.tvhplayer.touch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.touch.player.PlayerActivity
import at.bernhardberger.tvhplayer.touch.player.playingChannelId
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discoverServers
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val session: TvheadendSession by inject()
    private val owner: AppProfileOwner by inject()
    private val sdk: SdkRuntimeOwner by inject()
    private val connection: AppConnectionViewModel by viewModel()
    private val channels: ChannelsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val target by sdk.playbackRuntime.activeTarget.collectAsStateWithLifecycle()
                val playback by sdk.playbackRuntime.state.collectAsStateWithLifecycle()
                TouchApp(
                    session, owner, connection, channels, discovery = { discoverServers(applicationContext) },
                    playingId = playingChannelId(target, playback)?.value?.toString(),
                    onWatch = ::watch,
                )
            }
        }
    }

    private fun watch(channelId: String) {
        // The tap is the viewing intent: a stop from before it must not close the player it opens.
        sdk.playbackRuntime.notePlaybackIntent()
        startActivity(PlayerActivity.intent(this, ChannelId(channelId.toLong())))
    }
}
