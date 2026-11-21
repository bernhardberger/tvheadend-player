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
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.touch.player.PlayerActivity
import at.bernhardberger.tvhplayer.touch.player.playingChannelId
import at.bernhardberger.tvhplayer.touch.player.watchTunes
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discoverServers
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val session: TvheadendSession by inject()
    private val owner: AppProfileOwner by inject()
    private val sdk: SdkRuntimeOwner by inject()
    private val notices: NoticeCenter by inject()
    private val connection: AppConnectionViewModel by viewModel()
    private val channels: ChannelsViewModel by viewModel()
    private val recordings: RecordingsViewModel by viewModel()
    private val guide: GuideViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val context = applicationContext
        setContent {
            MaterialTheme(colorScheme = MobileDarkColors) {
                val target by sdk.playbackRuntime.activeTarget.collectAsStateWithLifecycle()
                val playback by sdk.playbackRuntime.state.collectAsStateWithLifecycle()
                TouchApp(
                    session, owner, connection, channels, recordings, guide, notices,
                    playingId = playingChannelId(target, playback)?.value?.toString(),
                    onWatch = ::watch,
                    discovery = { discoverServers(context) },
                )
            }
        }
    }

    private fun watch(channelId: String) {
        val id = ChannelId(channelId.toLong())
        val runtime = sdk.playbackRuntime
        // A tap that tunes is the viewing intent: a stop from before it must not close the player it opens.
        // Watch on the playing channel only brings its player back.
        val playing = playingChannelId(runtime.activeTarget.value, runtime.state.value)
        if (watchTunes(id, playing)) runtime.notePlaybackIntent()
        startActivity(PlayerActivity.intent(this, id))
    }
}
