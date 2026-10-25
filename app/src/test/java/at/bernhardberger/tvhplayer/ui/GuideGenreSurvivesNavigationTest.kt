@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgCoverage
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.ChannelsLayout
import at.bernhardberger.tvhplayer.settings.ChannelsView
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.ServerSettings
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelAccentStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.GuidePositionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * The Guide's genre choice lasts while its entry stays in the back stack: choosing Sport, visiting
 * Channels through the navigation rail and returning shows the same filter, on the Filter button
 * and checked in its panel. The choice is not persisted across launches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuideGenreSurvivesNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = ChannelCatalog.create((1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) })
    private lateinit var player: ExoPlayer
    private lateinit var back: androidx.activity.OnBackPressedDispatcher
    private val guidePosition = GuidePositionStore()

    @Test fun recordingPlayerBackRestoresTheGuideProgrammeWindowAndViewport() {
        app(withRecording = true)
        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        focus("guide-date-button")
        repeat(6) { key(Key.DirectionDown) }
        val nowWindow = checkNotNull(guidePosition.position.value).windowStartSec
        key(Key.DirectionLeft)
        val left = checkNotNull(guidePosition.position.value)
        org.junit.Assert.assertEquals(ChannelId(6), left.channelId)
        org.junit.Assert.assertEquals(EventId(66), left.eventId)
        org.junit.Assert.assertTrue(left.windowStartSec < nowWindow)
        org.junit.Assert.assertTrue(left.firstVisibleColumn > 0)
        key(Key.Enter)
        focus("details-play")
        key(Key.Enter)
        compose.onNodeWithTag("guide-date-button").assertDoesNotExist()
        // Back first dismisses player chrome if it is still visible, then closes the player.
        repeat(3) {
            if (compose.onAllNodes(hasTestTag("guide-date-button")).fetchSemanticsNodes().isEmpty()) {
                compose.runOnIdle { back.onBackPressed() }
                settle()
            }
        }
        compose.onNodeWithTag("guide-date-button").assertExists()
        // Returning from the full-screen player, unlike changing destinations, retains the exact spot.
        org.junit.Assert.assertEquals(left, guidePosition.position.value)
        focus("guide-date-button")
        key(Key.DirectionDown)
        compose.onNode(hasText("Recorded programme") and isFocused()).assertExists()
        org.junit.Assert.assertEquals(left, guidePosition.position.value)
    }

    @Test fun leavingForChannelsWithTheRailOpenReentersGuideAtNow() {
        app()
        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        focus("guide-date-button")
        key(Key.DirectionDown)
        val nowWindow = checkNotNull(guidePosition.position.value).windowStartSec
        focus("guide-date-button")
        key(Key.Enter)
        focus("guide-time-day-1")
        key(Key.Enter)
        key(Key.DirectionDown)
        org.junit.Assert.assertTrue(checkNotNull(guidePosition.position.value).windowStartSec > nowWindow)
        openDestination("nav-epg", Key.DirectionUp, "nav-channels")
        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        focus("guide-date-button")
        key(Key.DirectionDown)
        org.junit.Assert.assertEquals(nowWindow, checkNotNull(guidePosition.position.value).windowStartSec)
    }

    @After fun after() {
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun aChosenGenreSurvivesAVisitToChannelsAndBack() {
        app()
        val sportFilter = context.getString(R.string.header_button_value_description, context.getString(R.string.guide_filter), "Sport")

        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("guide-filter-button")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(sportFilter).assertDoesNotExist()

        focus("guide-filter-button")
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertIsDisplayed()
        focus("guide-genre-all")
        repeat(4) { key(Key.DirectionDown) }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("guide-genre-sport") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        key(Key.Enter)
        compose.onNodeWithTag("guide-genre-panel").assertDoesNotExist()
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(sportFilter)
        // The header is named by its pane title; the genre lives on its button.
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, context.getString(R.string.epg_title))).assertExists()

        openDestination("nav-epg", Key.DirectionUp, "nav-channels")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(sportFilter).assertDoesNotExist()

        openDestination("nav-channels", Key.DirectionDown, "nav-epg")
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("guide-filter-button")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("guide-filter-button").assertContentDescriptionEquals(sportFilter)
        focus("guide-filter-button")
        key(Key.Enter)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("guide-genre-sport") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Moves focus to [tag] and settles; the rail's items are reached the way a remote lands on them. */
    private fun focus(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    /** Enters the rail on the current destination's item, steps to its neighbour and opens it. */
    private fun openDestination(from: String, step: Key, to: String) {
        focus(from)
        key(step)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(to) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        key(Key.Enter)
    }

    private fun app(withRecording: Boolean = false) {
        // Most events span every time; the recording journey has a past programme and a scrolled channel.
        val now = kotlin.time.Clock.System.now().epochSeconds
        val catalog = if (withRecording) ChannelCatalog.create((1..8L).map {
            Channel.create(ChannelId(it), name = "Name $it", number = it)
        }) else this.catalog
        fun event(channel: Long, genre: Long) = EpgEvent.create(EventId(channel), ChannelId(channel), AIRS_FROM, AIRS_UNTIL,
            title = "Programme $channel", contentGenre = ContentGenre.fromContentType(genre))
        val events = catalog.channels.map { channel ->
            if (withRecording && channel.id == ChannelId(6)) EpgEvent.create(EventId(6), channel.id,
                Instant.fromEpochSeconds(now - 1800), AIRS_UNTIL, title = "Programme 6")
            else event(channel.id.value, if (channel.id == ChannelId(1)) 0x40L else 0x50L)
        } + if (withRecording) listOf(EpgEvent.create(EventId(66), ChannelId(6), Instant.fromEpochSeconds(now - 7200),
                Instant.fromEpochSeconds(now - 3600), title = "Recorded programme")) else emptyList()
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(catalog),
            // Sport on the first channel, children's programmes after.
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(
                events = events,
                coverages = catalog.channels.map { EpgCoverage.create(it.id, AIRS_FROM, AIRS_UNTIL) },
            )),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(if (withRecording) listOf(
                at.bernhardberger.tvheadend.sdk.core.DvrEntry.create(
                    id = at.bernhardberger.tvheadend.sdk.core.DvrEntryId(7),
                    eventId = EventId(66), channelId = ChannelId(6),
                    state = at.bernhardberger.tvheadend.sdk.core.DvrEntryState.COMPLETED, title = "Recorded programme",
                ),
            ) else emptyList())),
        )).apply { scriptLivePlaybackSuccess(); if (withRecording) scriptRecordingPlaybackSuccess() }
        val settings = PlayerSettingsStore(InMemoryData())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(ServerProfileReadResult.anonymous("offline.invalid", 9982)),
            settings, Dispatchers.IO, readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val dependencies = module {
            single<TvheadendSession> { session }
            single { runtime }
            single { profiles }
            single { settings }
            single { ChannelTagSettingsStore(InMemoryData()) }
            single { UiSettingsStore(InMemoryData()).apply { runBlocking { setChannelsView(ChannelsView(ChannelsLayout.LIST)) } } }
            single { LastPlayedChannelStore(context) }
            single { ChannelSelectionStore() }
            single { guidePosition }
            single { notices }
            single { ConnectionNoticeSource(session.observation, profiles.configurationGeneration, notices) }
            viewModel { AppConnectionViewModel(get(), get(), get()) }
            viewModel { ChannelsViewModel(get(), get()) }
            viewModel { VideoPlayerViewModel(get(), get()) }
            viewModel { SettingsStorageViewModel(session.cache, get(), ChannelAccentStore()) }
            viewModel { SettingsPlayerViewModel(get(), get(), get(), get(), get()) }
        }
        val server = ServerSettings(host = "offline.invalid")
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    back = requireNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    AppRoot(
                        startupState = MainStartupState.Ready(server, autoStartPlayback = false),
                        runtimeServerSettings = server,
                        applianceLaunchRequests = ApplianceLaunchRequests(),
                        onPlayerVisibilityChanged = {},
                        onRequestExit = {},
                    )
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        settle()
    }

    private fun settle() {
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private companion object {
        val AIRS_FROM = Instant.parse("2000-01-01T00:00:00Z")
        val AIRS_UNTIL = Instant.parse("2100-01-01T00:00:00Z")
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }
}
