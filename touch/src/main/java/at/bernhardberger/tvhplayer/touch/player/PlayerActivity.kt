package at.bernhardberger.tvhplayer.touch.player

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelTagId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvhplayer.data.ConnectionState
import at.bernhardberger.tvhplayer.di.SdkRuntimeOwner
import at.bernhardberger.tvhplayer.core.toConnectionState
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.BackgroundPlaybackNotice
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.touch.channels.ChannelDetails
import at.bernhardberger.tvhplayer.touch.channels.ChannelProgram
import at.bernhardberger.tvhplayer.touch.channels.ChannelRow
import at.bernhardberger.tvhplayer.ui.MobileDarkColors
import at.bernhardberger.tvhplayer.ui.common.formatClock
import at.bernhardberger.tvhplayer.ui.common.formatTimeInterval
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.common.rememberCurrentEpochSeconds
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import java.time.ZoneId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.time.Instant

/**
 * The live player. It plays through the app's one playback runtime: it never creates or
 * releases the player. Leaving it (Back, or the activity stopping outside a rotation) stops
 * playback; a tap on another channel while it is open tunes that one here.
 */
class PlayerActivity : ComponentActivity() {
    private val sdk: SdkRuntimeOwner by inject()
    private val session: TvheadendSession by inject()
    private val connection: AppConnectionViewModel by viewModel()
    private val channels: ChannelsViewModel by viewModel()
    private val runtime get() = sdk.playbackRuntime

    /** The channel asked for; a new [Tune.token] tunes again (Watch live after the pause ended). */
    private data class Tune(val channelId: ChannelId, val token: Int = 0)

    private lateinit var requested: MutableStateFlow<Tune>
    private val requestFailed = MutableStateFlow(false)
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channelId = intent.channelId()
        // First, before any playback: a user stop since the tap that opened this closes it.
        if (channelId == null || runtime.enterPlayerScreen(this) == null) {
            leaving = true
            finish()
            return
        }
        requested = MutableStateFlow(Tune(channelId))
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        onBackPressedDispatcher.addCallback(this) { leave() }
        lifecycleScope.launch { requested.collectLatest(::tune) }
        lifecycleScope.launch { retuneOnNewSession() }
        lifecycleScope.launch { followConnection() }
        lifecycleScope.launch {
            runtime.sessionStops.first()
            leaving = true
            finish()
        }
        setContent {
            MaterialTheme(colorScheme = MobileDarkColors) { Player() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val channelId = intent.channelId() ?: return
        // A newer tap replaces one still starting; the same channel again changes nothing.
        if (::requested.isInitialized) requested.update { if (it.channelId == channelId) it else Tune(channelId) }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) leave()
    }

    override fun onDestroy() {
        runtime.onLivePlayerClosed(this)
        super.onDestroy()
    }

    private fun leave() {
        if (leaving) return
        leaving = true
        lifecycleScope.launch {
            withContext(NonCancellable) { runtime.stop() }
            finish()
        }
    }

    private fun watchLive() {
        runtime.consumeBackgroundNotice(BackgroundPlaybackNotice.LIMIT_EXPIRED)
        requested.update { it.copy(token = it.token + 1) }
    }

    @OptIn(UnstableApi::class)
    private suspend fun tune(tune: Tune) {
        requestFailed.value = false
        val intent = runtime.notePlaybackIntent()
        val state = runtime.state.value
        if (tune.token == 0 && playingChannelId(runtime.activeTarget.value, state) == tune.channelId &&
            state !is AppPlaybackState.Idle
        ) {
            // Already playing (or starting) this channel: adopt it instead of tuning it again.
            runtime.notePlaybackIntentServed(intent)
            withContext(NonCancellable) { runtime.retuneAfterReconnect() }
            return
        }
        val selection = session.observation.mapNotNull { currentLivePlaybackSelection(it, tune.channelId) }.first()
        if (runtime.isPlaybackIntentStopped(intent)) return
        val result = runtime.playLive(selection, intent)
        if (result?.isStarted != true && !runtime.isPlaybackIntentStopped(intent)) {
            requestFailed.value = true
            runtime.stopAfterLoss()
        }
    }

    /** A session that replaced the one the channel started on retunes it, as the TV's player does. */
    private suspend fun retuneOnNewSession() {
        session.observation.map { it.currentSession }
            .distinctUntilChanged { a, b -> a === b }
            .filterNotNull()
            .drop(1)
            .collect { runtime.retuneAfterReconnect() }
    }

    /** The TV's loss handling: a lost connection stops the channel, its return retries it. */
    private suspend fun followConnection() {
        var lost = false
        combine(
            session.observation.map { it.sessionState.toConnectionState() is ConnectionState.Connected },
            runtime.videoPresentation.map { it.channelStartAtMillis != null },
        ) { connected, starting -> connected to starting }
            .distinctUntilChanged()
            .collect { (connected, starting) ->
                when {
                    starting -> lost = false
                    connected && lost -> {
                        lost = false
                        runtime.retryLive(viewerRetry = false)
                    }
                    !connected && !lost -> {
                        lost = true
                        if (runtime.stopAfterLoss(unlessChannelStarting = true) == null) lost = false
                    }
                }
            }
    }

    @Composable
    private fun Player() {
        val presentation by connection.uiState.collectAsStateWithLifecycle()
        val scope by channels.scope.collectAsStateWithLifecycle()
        val observation by channels.observation.collectAsStateWithLifecycle()
        val playback by runtime.state.collectAsStateWithLifecycle()
        val target by runtime.activeTarget.collectAsStateWithLifecycle()
        val live by runtime.livePlaybackObservation.collectAsStateWithLifecycle()
        val notice by runtime.backgroundNotice.collectAsStateWithLifecycle()
        val video by runtime.videoPresentation.collectAsStateWithLifecycle()
        val tune by requested.collectAsStateWithLifecycle()
        val failed by requestFailed.collectAsStateWithLifecycle()
        val nowSec = rememberCurrentEpochSeconds()()
        var brightness by remember { mutableFloatStateOf(systemBrightness()) }
        val shown = tune.channelId
        val active = live as? LivePlaybackObservation.Active
        val status = liveStatus(
            connected = observation.sessionState.toConnectionState() is ConnectionState.Connected,
            state = playback,
            issue = active?.subscriptionIssue,
            serverStopped = active?.serverStopped == true,
            playingId = playingChannelId(target, playback),
            shownId = shown,
            requestFailed = failed,
            notice = notice,
        )
        val now = Instant.fromEpochSeconds(nowSec)
        val state = livePlayerState(
            shown, scope, observation, presentation.connection, presentation.degraded, status, now,
            ZoneId.systemDefault(),
        )
        val resources = LocalResources.current
        PlayerScreen(
            state = state.copy(
                channel = state.channel.withProgramTexts(observation, shown, now, resources),
                brightness = brightness,
            ),
            onAction = { action ->
                when (action) {
                    PlayerAction.Back -> leave()
                    PlayerAction.WatchLive -> watchLive()
                    else -> Unit
                }
            },
            onSeek = {},
            onBrightness = { value ->
                brightness = value
                window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(MIN_BRIGHTNESS, 1f) }
            },
            onChannel = { id ->
                val channelId = ChannelId(id.toLong())
                requested.update { if (it.channelId == channelId) it else Tune(channelId) }
            },
            onChoice = { choice ->
                if (choice is PlayerChoice.ChannelTag) channels.selectTag(choice.id?.let { ChannelTagId(it.toLong()) })
            },
            video = { modifier -> TouchVideoSurface(runtime.player, video.visible, modifier) },
        )
    }

    private fun systemBrightness(): Float =
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_BRIGHTNESS) / MAX_BRIGHTNESS

    companion object {
        private const val EXTRA_CHANNEL = "channel"
        private const val DEFAULT_BRIGHTNESS = 128
        private const val MAX_BRIGHTNESS = 255f
        // Zero would turn the backlight off on some devices.
        private const val MIN_BRIGHTNESS = 0.01f

        internal fun intent(context: Context, channelId: ChannelId): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_CHANNEL, channelId.value)

        private fun Intent.channelId(): ChannelId? =
            getLongExtra(EXTRA_CHANNEL, -1L).takeIf { it >= 0 }?.let(::ChannelId)
    }
}

/** The running and next program's clock, episode and genre texts, by the shared formatters. */
private fun ChannelRow.withProgramTexts(
    observation: SessionObservation,
    channelId: ChannelId,
    now: Instant,
    resources: Resources,
): ChannelRow {
    val running = observation.eventAt(channelId, now)
    val next = observation.nextEvent(channelId, now)
    val nowSec = now.epochSeconds
    return copy(
        now = running?.let {
            ChannelProgram(
                title = it.title.orEmpty(), start = null,
                timeRange = formatTimeInterval(it.start.epochSeconds, it.stop.epochSeconds, nowSec = nowSec),
                progress = it.progress(nowSec),
            )
        },
        next = next?.let { ChannelProgram(it.title.orEmpty(), start = formatClock(it.start.epochSeconds)) },
        details = (details ?: ChannelDetails()).copy(
            episode = running?.episodeLine(resources),
            metadata = running?.genre?.takeIf(String::isNotBlank),
        ),
    )
}

private fun EpgEvent.episodeLine(resources: Resources): String? =
    programmeEpisodeLine(episode, subtitle, resources).ifEmpty { null }
