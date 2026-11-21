package at.bernhardberger.tvhplayer.touch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.images.ChannelAccents
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.touch.player.PlayerActivity
import at.bernhardberger.tvhplayer.touch.player.playingChannelId
import at.bernhardberger.tvhplayer.touch.player.playingRecordingId
import at.bernhardberger.tvhplayer.touch.player.watchTunes
import at.bernhardberger.tvhplayer.touch.player.RecordingRoute
import at.bernhardberger.tvhplayer.touch.player.recordingStart
import at.bernhardberger.tvhplayer.touch.player.startsRecording
import at.bernhardberger.tvhplayer.playback.currentRecordingPlaybackSelection
import at.bernhardberger.tvhplayer.touch.di.Application
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.screens.onboarding.discoverServers
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.touch.guide.GuideViewModel
import at.bernhardberger.tvhplayer.touch.recordings.RecordingsViewModel
import at.bernhardberger.tvhplayer.touch.ui.LocalChannelAccents
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val session: TvheadendSession by inject()
    private val owner: AppProfileOwner by inject()
    private val sdk: SdkRuntimeOwner by inject()
    private val notices: NoticeCenter by inject()
    private val connection: AppConnectionViewModel by viewModel()
    private val channels: ChannelsViewModel by inject()
    private val recordings: RecordingsViewModel by viewModel()
    private val guide: GuideViewModel by viewModel()
    private val applicationScope: CoroutineScope by inject(Application)
    private var watchRequest = 0

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
                CompositionLocalProvider(LocalChannelAccents provides get<ChannelAccents>()) {
                    TouchApp(
                        session, owner, connection, channels, recordings, guide, notices,
                        playingId = playingChannelId(target, playback)?.value?.toString(),
                        playingRecordingId = playingRecordingId(target, playback),
                        onWatch = ::watch,
                        onPlayRecording = ::watchRecording,
                        discovery = { discoverServers(context) },
                    )
                }
            }
        }
    }

    private fun watch(channelId: String) {
        watchRequest++
        val id = ChannelId(channelId.toLong())
        val runtime = sdk.playbackRuntime
        // A tap that tunes is the viewing intent: a stop from before it must not close the player it opens.
        // Watch on the playing channel only brings its player back.
        val playing = playingChannelId(runtime.activeTarget.value, runtime.state.value)
        if (watchTunes(id, playing)) runtime.notePlaybackIntent()
        startActivity(PlayerActivity.intent(this, id))
    }

    @OptIn(UnstableApi::class)
    private fun watchRecording(recordingId: String, startOver: Boolean) {
        val id = DvrEntryId(recordingId.toLong())
        val route = RecordingRoute(id, recordingStart(startOver))
        val runtime = sdk.playbackRuntime
        val request = ++watchRequest
        if (startsRecording(id, runtime.activeTarget.value)) {
            val selection = currentRecordingPlaybackSelection(session.observation.value, id) ?: return
            // Register the fresh playback request before the player's Stop guard runs. The
            // application owns the install even when this activity is covered by the player.
            applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                runtime.playRecording(selection, route.start)
                // Like TV Browse, hand the route over after the result has been published.
                if (request == watchRequest && !isDestroyed && !isFinishing) {
                    startActivity(route.intent(this@MainActivity))
                }
            }
        } else {
            startActivity(route.intent(this))
        }
    }
}
