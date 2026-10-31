@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.notices.*
import at.bernhardberger.tvhplayer.playback.*
import at.bernhardberger.tvhplayer.settings.*
import at.bernhardberger.tvhplayer.stores.*
import at.bernhardberger.tvhplayer.viewmodels.*
import at.bernhardberger.tvhplayer.ui.components.awaitBrowsePreparation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.time.Instant

/** Offline AppRoot fixture shared by Guide and Home return journeys. */
internal class NavigationJourneyFixture(
    private val compose: ComposeContentTestRule,
    private val server: ServerSettings,
) {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    lateinit var back: androidx.activity.OnBackPressedDispatcher
        private set
    val guidePosition = GuidePositionStore()

    fun close() {
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    fun focus(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    fun openDestination(from: String, step: Key, to: String) {
        focus(from)
        key(step)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(to) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        key(Key.Enter)
    }

    fun app(withRecording: Boolean = false, emptyEpg: Boolean = false) {
        val now = kotlin.time.Clock.System.now().epochSeconds
        val catalog = ChannelCatalog.create((1..if (withRecording) 8L else 3L).map {
            Channel.create(ChannelId(it), name = "Name $it", number = it)
        })
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
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = if (emptyEpg) emptyList() else events,
                coverages = catalog.channels.map { EpgCoverage.create(it.id, AIRS_FROM, AIRS_UNTIL) })),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(if (withRecording) listOf(
                DvrEntry.create(id = DvrEntryId(7), eventId = EventId(66), channelId = ChannelId(6),
                    state = DvrEntryState.COMPLETED, title = "Recorded programme"),
            ) else emptyList())),
        )).apply { scriptLivePlaybackSuccess(); if (withRecording) scriptRecordingPlaybackSuccess() }
        val settings = PlayerSettingsStore(InMemoryData())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(ServerProfileReadResult.anonymous(server.host, server.htspPort)),
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
            viewModel { SettingsStorageViewModel(session.cache, get(), ChannelAccentStore()) {} }
            viewModel { SettingsPlayerViewModel(get(), get(), get(), get(), get()) }
        }
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    back = requireNotNull(androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    AppRoot(startupState = MainStartupState.Ready(server, autoStartPlayback = false),
                        runtimeServerSettings = server, applianceLaunchRequests = ApplianceLaunchRequests(),
                        onPlayerVisibilityChanged = {}, onRequestExit = {})
                }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("nav-channels")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-channels").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        settle()
    }

    fun settle() {
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            compose.awaitBrowsePreparation()
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
