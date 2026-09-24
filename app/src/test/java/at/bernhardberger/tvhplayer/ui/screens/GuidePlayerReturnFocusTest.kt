package at.bernhardberger.tvhplayer.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import androidx.media3.exoplayer.ExoPlayer
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePosition
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone
import kotlin.time.Instant

/**
 * Back from the live player re-enters the guide on the programme airing now on the
 * playing channel, not the pre-playback (possibly historical) browse position.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class GuidePlayerReturnFocusTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var zone: TimeZone
    private val hour = System.currentTimeMillis() / 3_600_000 * 3600

    @Before fun before() { zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun after() { models.clear(); scope.cancel(); if (::player.isInitialized) player.release(); TimeZone.setDefault(zone) }

    @Test fun timeshiftedEntryReturnsToNowOnChangedPlayingChannel() {
        // Entered from a past programme on channel 1, then zapped to channel 2.
        guide(PlayerReturnFocus(playingChannelId = ChannelId(2), originChannelId = ChannelId(1)))
        focused("Channel 2 hour 0")
    }

    @Test fun timeshiftedEntryOnSameChannelReturnsToNowNotHistoricalProgramme() {
        guide(PlayerReturnFocus(playingChannelId = ChannelId(1), originChannelId = ChannelId(1)))
        focused("Channel 1 hour 0")
    }

    @Test fun playingChannelWithoutEpgUsesNearestNowProgramme() {
        // Guide focus is programme-only; the existing entry search takes the nearest row.
        guide(PlayerReturnFocus(playingChannelId = ChannelId(3), originChannelId = ChannelId(1)))
        focused("Channel 2 hour 0")
    }

    @Test fun secondReturnOnRetainedCompositionIsPositionedAgain() {
        guide(PlayerReturnFocus(playingChannelId = ChannelId(1), originChannelId = ChannelId(1)))
        focused("Channel 1 hour 0")
        key(Key.DirectionDown)
        focused("Channel 2 hour 0")
        // Player on top, then Back again: an equal but distinct return request.
        compose.runOnIdle { focusEnabled = false; playerReturn = null }
        compose.waitForIdle()
        compose.runOnIdle {
            focusEnabled = true
            playerReturn = PlayerReturnFocus(playingChannelId = ChannelId(1), originChannelId = ChannelId(1))
        }
        focused("Channel 1 hour 0")
    }

    @Test fun returnWaitsForChannelsThatArriveAfterComposition() {
        val session = guide(
            PlayerReturnFocus(playingChannelId = ChannelId(2), originChannelId = ChannelId(1)),
            channelsLoaded = false,
        )
        compose.runOnIdle { session.publish(observation(loaded = true)) }
        focused("Channel 2 hour 0")
    }

    @Test fun playingChannelOutsideScopeKeepsBrowsePosition() {
        guide(PlayerReturnFocus(playingChannelId = ChannelId(99), originChannelId = ChannelId(1)))
        focused("Channel 1 hour -2")
    }

    private fun focused(title: String) {
        runCatching { compose.waitUntil(10_000) { compose.onAllNodes(hasText(title) and isFocused()).fetchSemanticsNodes().isNotEmpty() } }
            .onFailure {
                val f = compose.onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.toString() }
                val t = compose.onAllNodes(androidx.compose.ui.test.hasText("Channel", substring = true)).fetchSemanticsNodes().map { it.config.toString().take(80) }
                throw AssertionError("focused=$f texts=$t")
            }
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(data.value).also { data.value = it }
    }

    private var playerReturn by mutableStateOf<PlayerReturnFocus?>(null)
    private var focusEnabled by mutableStateOf(true)

    private fun key(key: Key) { compose.onRoot().performKeyInput { pressKey(key) }; compose.waitForIdle() }

    private fun event(channel: Long, offset: Int) = EpgEvent.create(EventId(channel * 100 + offset + 10),
        channelId = ChannelId(channel), title = "Channel $channel hour $offset",
        start = Instant.fromEpochSeconds(hour + offset * 3600), stop = Instant.fromEpochSeconds(hour + (offset + 1) * 3600))

    private fun observation(loaded: Boolean): SessionObservation {
        val channels = if (loaded) (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) } else emptyList()
        // Channel 3 has no EPG.
        val withEpg = listOf(1L, 2L)
        return SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(channels)),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                events = withEpg.flatMap { channel -> (0..3).map { event(channel, it) } },
                historicalEvents = withEpg.flatMap { channel -> (-4..-1).map { event(channel, it) } },
                coverages = channels.map {
                    EpgCoverage.create(channelId = it.id, coveredFrom = Instant.fromEpochSeconds(hour),
                        coveredTo = Instant.fromEpochSeconds(hour + 7 * 86400))
                },
            )),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )
    }

    private fun guide(request: PlayerReturnFocus, channelsLoaded: Boolean = true): FakeTvheadendSession {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val session = FakeTvheadendSession(observation(channelsLoaded))
        val settings = PlayerSettingsStore(preferences())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        val model = ChannelsViewModel(session, ChannelTagSettingsStore(preferences()))
        models.put("channels", model)
        // Usually warm (shared scope already loaded); a reconnect can compose it empty.
        compose.waitUntil(10_000) {
            model.scope.value.settingsLoaded && model.scope.value.scope.visibleChannels.size == if (channelsLoaded) 3 else 0
        }
        player = ExoPlayer.Builder(context).build()
        val runtime = AppPlaybackRuntime(player, session, createTvheadendPlaybackCoordinator(player), settings, profiles, scope,
            at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider(context))
        // The guide position saved when the historical programme on channel 1 was opened.
        val position = GuidePositionStore().apply {
            save(GuidePosition(ChannelId(1), event(1, -2).id, hour - 2 * 3600, hour - 2 * 3600, 0))
        }
        playerReturn = request
        compose.setContent {
            TVHeadendPlayerTheme {
                EpgGridScreen(
                    initialFocusEnabled = focusEnabled,
                    channelViewModel = model, session = session, playerSession = runtime,
                    selection = ChannelSelectionStore(), guidePositionStore = position,
                    lastPlayedStore = LastPlayedChannelStore(context),
                    imageLoader = ImageLoader.Builder(context).diskCache(null).build(),
                    playerReturn = playerReturn, onPlay = { _, _ -> },
                )
            }
        }
        compose.waitForIdle()
        return session
    }
}
