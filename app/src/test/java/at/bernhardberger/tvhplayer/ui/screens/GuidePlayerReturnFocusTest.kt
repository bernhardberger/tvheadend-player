package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyPress
import org.junit.Assert.assertEquals
import org.robolectric.annotation.GraphicsMode
import java.io.File
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
    private lateinit var back: OnBackPressedDispatcher
    private lateinit var view: View
    private var playRequests = 0
    private lateinit var settingsApplication: org.koin.core.KoinApplication

    @Test
    @Config(qualifiers = "en-w960dp-h540dp-land-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun contextMenuHoldReleaseBackMenuAndWatchRunningProgramme() {
        guide(PlayerReturnFocus(ChannelId(1), ChannelId(1)))
        focused("Channel 1 hour 0")
        val row = compose.onNodeWithText("Channel 1 hour 0")
        fun event(action: Int, repeat: Int = 0) = KeyEvent(android.view.KeyEvent(0, 0, action, android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat))
        row.performKeyPress(event(android.view.KeyEvent.ACTION_DOWN))
        row.performKeyPress(event(android.view.KeyEvent.ACTION_DOWN, 1))
        compose.onNodeWithText("Details").assertIsFocused()
        compose.onRoot().performKeyPress(event(android.view.KeyEvent.ACTION_UP))
        compose.onNodeWithText("Details").assertIsFocused()
        assertEquals(0, playRequests)
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("build/outputs/u15-context").apply { mkdirs() }
        File(directory, "guide.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithTag("browse-context-menu").assertDoesNotExist()
        focused("Channel 1 hour 0")
        key(Key.Menu)
        compose.onNodeWithText("Details").assertIsFocused()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        compose.onNodeWithText("Watch").assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals(1, playRequests)
    }

    @Before fun before() {
        zone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        settingsApplication = org.koin.dsl.koinApplication { modules(org.koin.dsl.module {
            single { at.bernhardberger.tvhplayer.settings.UiSettingsStore(preferences()) }
        }) }
    }
    @After fun after() { models.clear(); scope.cancel(); if (::player.isInitialized) player.release(); TimeZone.setDefault(zone); settingsApplication.close() }

    @Test fun timeshiftedEntryReturnsToNowOnChangedPlayingChannel() {
        // Entered from a past programme on channel 1, then zapped to channel 2.
        guide(PlayerReturnFocus(playingChannelId = ChannelId(2), originChannelId = ChannelId(1)))
        focused("Channel 2 hour 0")
    }

    @Test fun programmeRequestFromChannelsLandsOnFutureProgramme() {
        val programme = event(2, 3)
        guide(PlayerReturnFocus(ChannelId(2), ChannelId(2), programme.id, programme.start.epochSeconds))
        focused("Channel 2 hour 3")
    }

    @Test fun missingProgrammeRequestFallsBackToChannelAtNow() {
        guide(PlayerReturnFocus(ChannelId(2), ChannelId(2), EventId(999), hour + 3 * 3600))
        focused("Channel 2 hour 0")
    }

    @Test fun historicalContextMenuCanWatchTheChannelWithoutRequiringALiveEvent() {
        guide(PlayerReturnFocus(ChannelId(99), ChannelId(1)))
        focused("Channel 1 hour -2")
        key(Key.Menu)
        compose.onNodeWithText("Details").assertIsFocused()
        key(Key.DirectionDown)
        compose.onNodeWithText("Watch channel").assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals(1, playRequests)
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

    @Test fun offlineGuideOkOpensDetailsWhoseWatchPlaysTheChannel() {
        guide(PlayerReturnFocus(playingChannelId = ChannelId(1), originChannelId = ChannelId(1)), offline = true)
        focused("Channel 1 hour 0")
        key(Key.DirectionCenter)
        compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("details-watch") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        key(Key.DirectionCenter)
        assertEquals(1, playRequests)
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

    private fun observation(loaded: Boolean, offline: Boolean = false): SessionObservation {
        val channels = if (loaded) (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) } else emptyList()
        // Channel 3 has no EPG.
        val withEpg = listOf(1L, 2L)
        val catalog = ChannelCatalog.create(channels)
        val epg = EpgSnapshot.create(
                events = withEpg.flatMap { channel -> (0..3).map { event(channel, it) } },
                historicalEvents = withEpg.flatMap { channel -> (-4..-1).map { event(channel, it) } },
                coverages = channels.map {
                    EpgCoverage.create(channelId = it.id, coveredFrom = Instant.fromEpochSeconds(hour),
                        coveredTo = Instant.fromEpochSeconds(hour + 7 * 86400))
                },
            )
        // Offline: saved metadata before the session is ready.
        return SessionObservation.create(
            sessionState = if (offline) SessionState.Connecting
                else SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = if (offline) ChannelRepositoryState.Stale(catalog) else ChannelRepositoryState.Current(catalog),
            epgState = if (offline) EpgRepositoryState.Stale(epg) else EpgRepositoryState.Current(epg),
            dvrState = if (offline) DvrRepositoryState.Stale(DvrSnapshot.create()) else DvrRepositoryState.Current(DvrSnapshot.create()),
        )
    }

    private fun guide(request: PlayerReturnFocus, channelsLoaded: Boolean = true, offline: Boolean = false): FakeTvheadendSession {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val session = FakeTvheadendSession(observation(channelsLoaded, offline))
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
            at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider(context),
            at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus.None,
            at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy.fromPlayerSettings())
        // The guide position saved when the historical programme on channel 1 was opened.
        val position = GuidePositionStore().apply {
            save(GuidePosition(ChannelId(1), event(1, -2).id, hour - 2 * 3600, hour - 2 * 3600, 0))
        }
        playerReturn = request
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        compose.setContent {
            org.koin.compose.KoinIsolatedContext(settingsApplication) {
            TVHeadendPlayerTheme {
                back = requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                view = LocalView.current
                Box(Modifier.fillMaxSize().background(androidx.tv.material3.MaterialTheme.colorScheme.background)) {
                EpgGridScreen(
                    initialFocusEnabled = focusEnabled,
                    channelViewModel = model, session = session, playerSession = runtime,
                    selection = ChannelSelectionStore(), guidePositionStore = position,
                    lastPlayedStore = LastPlayedChannelStore(context),
                    playerReturn = playerReturn, onPlay = { _, _ -> playRequests++ },
                    notices = notices,
                )
                }
            }
        }
        }
        compose.waitForIdle()
        return session
    }
}
