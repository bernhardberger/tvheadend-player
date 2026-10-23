@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
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
import at.bernhardberger.tvheadend.sdk.core.DvrRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.ServerProfileReadResult
import at.bernhardberger.tvheadend.sdk.core.SessionFailure
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.MainStartupState
import at.bernhardberger.tvhplayer.notices.ConnectionNoticeSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
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
import at.bernhardberger.tvhplayer.ui.player.LivePlayerServerWaitTag
import at.bernhardberger.tvhplayer.viewmodels.AppConnectionViewModel
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsPlayerViewModel
import at.bernhardberger.tvhplayer.viewmodels.SettingsStorageViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import at.bernhardberger.tvhplayer.ui.startup.LocalStartupBrandIntro
import at.bernhardberger.tvhplayer.ui.startup.StartupBrandClock
import at.bernhardberger.tvhplayer.ui.startup.StartupBrandIntro

/**
 * Startup and play before the session is ready. OK on a saved channel row opens the player for
 * that channel at once; it waits without an action, tunes that channel on Ready, offers the catalog's
 * recovery when connecting fails, and Back returns to the row without tuning anything later.
 * Without saved channels a cold start shows the startup screen, readable, until channels arrive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class StartupOfflineTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = ChannelCatalog.create((1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) })
    private lateinit var player: ExoPlayer
    private lateinit var session: FakeTvheadendSession
    private lateinit var runtime: AppPlaybackRuntime
    private lateinit var playerData: InMemoryData

    @After fun after() {
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun okBeforeReadyOpensTheWaitingPlayerAndTunesThatChannelOnReady() {
        app()
        confirmRow(1)
        val wait = compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true)
        val description = wait.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description, description.contains("1 Name 1") && description.contains("Connecting to your Tvheadend server"))
        // The opening OK activated nothing: no action exists or holds focus in the wait.
        assertEquals(0, compose.onAllNodes(hasClickAction() and isFocused()).fetchSemanticsNodes().size)
        key(Key.DirectionCenter)
        assertEquals(0, binds())

        session.publish(ready(catalog))
        settle()
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        settle()
        assertEquals(1, binds())
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun failedConnectingThatRetriesByItselfOffersConnectionSettingsFocusedAndNoRetry() {
        app()
        confirmRow(1)
        session.publish(SessionObservation.create(
            sessionState = SessionState.Unavailable(SessionFailure.TRANSPORT_UNAVAILABLE),
            channelState = ChannelRepositoryState.Stale(catalog),
        ))
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true), useUnmergedTree = false)
            .assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasText("Retry", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
        // Automatic attempts keep the recovery and its focus in place.
        session.publish(connecting(catalog))
        settle()
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun connectionSettingsFromTheFailedWaitLeavesThePlayerForConnectionSettings() {
        app()
        confirmRow(1)
        // A failure that waits for the viewer: Retry first, Connection settings beside it.
        session.publish(SessionObservation.create(
            sessionState = SessionState.Unavailable(SessionFailure.synchronizationFailed(TvheadendFailure.MALFORMED_RESPONSE)),
            channelState = ChannelRepositoryState.Stale(catalog),
        ))
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        key(Key.DirectionRight)
        compose.onNode(hasTestTag("tv-recovery-secondary") and hasText("Connection settings", substring = true)).assertIsFocused()
        key(Key.DirectionCenter)
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Edit connection"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-overlay")).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, binds())
    }

    @Test fun backLeavesTheWaitToItsRowAndCancelsItsTune() {
        app()
        confirmRow(2)
        key(Key.Back)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("channel-row-2").assertIsFocused()
        session.publish(ready(catalog))
        settle()
        assertEquals(0, binds())
        assertEquals(null, runtime.activeTarget.value)
    }

    @Test fun aReplacedPendingTargetTunesOnlyTheSecondChannel() {
        app()
        confirmRow(1)
        key(Key.Back)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-1") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        confirmRow(3)
        session.publish(ready(catalog))
        settle()
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(3)) }
        settle()
        assertEquals(1, binds())
    }

    @Test fun aChannelGoneAfterSyncShowsTheUnavailableState() {
        app()
        confirmRow(3)
        session.publish(ready(ChannelCatalog.create(catalog.channels.take(2))))
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("player-channel-unavailable")).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, binds())
    }

    @Test fun savedChannelsShowTheShellWithoutTheStartupScreen() {
        app()
        assertEquals(0, compose.onAllNodes(hasTestTag("main-startup-root")).fetchSemanticsNodes().size)
    }

    @Test fun withoutSavedChannelsTheBrandAssemblesAfterTheOneGraceAndStaysReadable() {
        val intro = coldApp(SessionObservation.create(sessionState = SessionState.Connecting))
        advance(200)
        // The one grace: the startup screen's background, no brand and no stage text yet.
        assertEquals(1, startupScreens())
        assertEquals(0, statusTexts())
        assertFalse(intro.running)
        // The brand assembles from its start after the grace; from then the screen stays readable.
        repeat(20) { if (!intro.running) advance(50) }
        assertTrue(intro.running)
        assertTrue("the brand assembles from its start", intro.millis < 100f)
        session.publish(ready(catalog))
        advance(250)
        assertEquals("shown screens stay readable", 1, startupScreens())
        assertEquals("the stage text follows the brand", 1, statusTexts())
        advance(400)
        assertEquals(0, startupScreens())
        compose.onNodeWithTag("channel-row-1").fetchSemanticsNode()
    }

    @Test fun savedChannelsWithinTheGraceLeadStraightToTheShell() {
        coldApp(SessionObservation.create(sessionState = SessionState.Connecting))
        advance(200)
        session.publish(connecting(catalog))
        advance(100)
        assertEquals(0, startupScreens())
        compose.onNodeWithTag("channel-row-1").fetchSemanticsNode()
        advance(1_000)
        assertEquals(0, startupScreens() + statusTexts())
    }

    @Test fun aFailedColdStartKeepsConnectionSettingsFocusedThroughAutomaticAttempts() {
        coldApp(SessionObservation.create(sessionState = SessionState.Unavailable(SessionFailure.TRANSPORT_UNAVAILABLE)))
        advance(1_000)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        // 110: the session retries by itself, so the startup alert offers Connection settings and no Retry.
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-secondary")).fetchSemanticsNodes().size)
        assertEquals(1, startupScreens())
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting))
        advance(500)
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
    }

    @Test fun unavailableProfileOffersConnectionSettingsInsteadOfConnecting() =
        profileRecovery(ServerProfileReadResult.Unavailable)

    @Test fun missingProfileOffersConnectionSettingsInsteadOfConnecting() =
        profileRecovery(ServerProfileReadResult.Missing)

    private fun profileRecovery(profile: ServerProfileReadResult) {
        app(profile = profile)
        confirmRow(1, awaitWait = false)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("tv-recovery-primary") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("tv-recovery-primary") and hasText("Connection settings", substring = true)).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun okWithAReadySessionStartsThatChannelAndBackLeavesItPlaying() {
        app(ready(catalog))
        confirmRow(2, awaitWait = false)
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        assertEquals(1, binds())
        assertEquals(0, compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size)
        key(Key.Back)
        settle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("playback stays warm behind browse", AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun aServerRestartWhileBrowsingRetunesTheWarmChannelOnce() {
        app(ready(catalog))
        confirmRow(2, awaitWait = false)
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        key(Key.Back)
        settle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        session.publish(connecting(catalog))
        settle()
        session.publish(ready(catalog))
        settle()
        compose.waitUntil(10_000) { binds() == 2 }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        // Returning to the player adopts the retuned channel.
        key(Key.DirectionCenter)
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("channel-row-2")).fetchSemanticsNodes().isEmpty() }
        settle()
        assertEquals(2, binds())
    }

    @Test fun reopeningThePlayerAsTheServerReturnsStillRetunesTheWarmChannelOnce() {
        app(ready(catalog))
        confirmRow(2, awaitWait = false)
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        key(Key.Back)
        settle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        session.publish(connecting(catalog))
        settle()
        // The retune waits for its settings while OK opens the player: browse leaves before it is done.
        val gate = CompletableDeferred<Unit>().also { playerData.readGate = it }
        session.publish(ready(catalog))
        settle()
        assertEquals(1, binds())
        key(Key.DirectionCenter)
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("channel-row-2")).fetchSemanticsNodes().isEmpty() }
        playerData.readGate = null
        gate.complete(Unit)
        settle()
        compose.waitUntil(10_000) { binds() == 2 }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertEquals(2, binds())
    }

    @Test fun twoOksInOneBurstOpenOnlyTheSecondChannel() {
        app()
        // Both confirms run before either request's coroutine pushes its player.
        val confirms = listOf(1L, 2L).map { id ->
            requireNotNull(compose.onNodeWithTag("channel-row-$id").fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        }
        compose.runOnUiThread { confirms.forEach { it() } }
        settle()
        val wait = compose.onNodeWithTag(LivePlayerServerWaitTag, useUnmergedTree = true)
        val description = wait.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description, description.contains("2 Name 2"))
        session.publish(ready(catalog))
        settle()
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        assertEquals(1, binds())
        // One Back leaves the one player.
        key(Key.Back)
        settle()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("channel-row-2") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun startupScreens() = compose.onAllNodes(hasTestTag("main-startup-root")).fetchSemanticsNodes().size

    private fun statusTexts() =
        compose.onAllNodes(hasTestTag("main-startup-status"), useUnmergedTree = true).fetchSemanticsNodes().size

    /** Both clocks: frames, and the Handler clock startup's deadlines use. */
    private fun advance(millis: Long) {
        repeat((millis / 50).toInt()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            compose.mainClock.advanceTimeBy(50)
        }
        compose.waitForIdle()
    }

    /** A cold start with the production brand intro, as MainActivity provides it. */
    private fun coldApp(initial: SessionObservation): StartupBrandIntro {
        val intro = StartupBrandIntro(eligible = true).apply {
            entranceReady()
            resumed(true)
            focused(true)
        }
        app(initial, awaitRows = false, intro = intro)
        return intro
    }

    private fun confirmRow(id: Long, awaitWait: Boolean = true, confirm: Boolean = true) {
        repeat(10) {
            val focusedRow = (1..3L).firstOrNull { row ->
                compose.onAllNodes(hasTestTag("channel-row-$row") and isFocused()).fetchSemanticsNodes().isNotEmpty()
            }
            when {
                focusedRow == id -> return@repeat
                focusedRow != null && focusedRow > id -> key(Key.DirectionUp)
                else -> key(Key.DirectionDown)
            }
        }
        compose.onNodeWithTag("channel-row-$id").assertIsFocused()
        if (!confirm) return
        key(Key.DirectionCenter)
        settle()
        if (!awaitWait) return
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        settle()
    }

    private fun binds() = session.calls.count { it == FakeSessionCall.BIND_LIVE_PLAYBACK }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun settle() {
        repeat(10) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun connecting(channels: ChannelCatalog) = SessionObservation.create(
        sessionState = SessionState.Connecting,
        channelState = ChannelRepositoryState.Stale(channels),
    )

    private fun ready(channels: ChannelCatalog) = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(channels),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    /** By default saved channels before the session is ready: the warm start of D26. */
    private fun app(
        initial: SessionObservation = connecting(catalog),
        awaitRows: Boolean = true,
        intro: StartupBrandIntro? = null,
        profile: ServerProfileReadResult = ServerProfileReadResult.anonymous("offline.invalid", 9982),
    ) {
        session = FakeTvheadendSession(initial).apply { scriptLivePlaybackSuccess() }
        val data = InMemoryData().also { playerData = it }
        val settings = PlayerSettingsStore(data)
        val profiles = AppProfileOwner(session, FakeServerProfileStore(profile),
            settings, Dispatchers.IO, readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings())
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val dependencies = module {
            single<TvheadendSession> { session }
            single { runtime }
            single { profiles }
            single { settings }
            single { ChannelTagSettingsStore(InMemoryData()) }
            // These journeys walk the list's rows.
            single { UiSettingsStore(InMemoryData()).apply { runBlocking { setChannelsView(ChannelsView(ChannelsLayout.LIST)) } } }
            single { LastPlayedChannelStore(context) }
            single { ChannelSelectionStore() }
            single { GuidePositionStore() }
            single { notices }
            single { ConnectionNoticeSource(session.observation, profiles.configurationGeneration, notices) }
            viewModel { AppConnectionViewModel(get(), get(), get()) }
            viewModel { ChannelsViewModel(get(), get()) }
            viewModel { VideoPlayerViewModel(get(), get()) }
            viewModel { SettingsStorageViewModel(session.cache, get(), ChannelAccentStore()) }
            viewModel { SettingsPlayerViewModel(get(), get(), get(), get(), get()) }
        }
        val server = ServerSettings(host = "offline.invalid")
        // Startup timing is checked against the frame and Handler clocks.
        if (!awaitRows) compose.mainClock.autoAdvance = false
        compose.setContent {
            KoinIsolatedContext(koinApplication { modules(dependencies) }) {
                TVHeadendPlayerTheme {
                    CompositionLocalProvider(LocalStartupBrandIntro provides intro) {
                        if (intro != null) StartupBrandClock(intro)
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
        }
        if (!awaitRows) return
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("channel-row-1")).fetchSemanticsNodes().isNotEmpty()
        }
        settle()
        compose.waitUntil(10_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodes(hasTestTag("nav-channels") and isFocused()).fetchSemanticsNodes().isNotEmpty()) {
            key(Key.DirectionRight)
        }
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        /** Holds every read that starts while set, as a slow store would. */
        var readGate: CompletableDeferred<Unit>? = null
        override val data: Flow<Preferences> get() = flow {
            readGate?.await()
            emitAll(state)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }
}
