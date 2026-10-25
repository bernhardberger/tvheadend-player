@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvhplayer.R
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
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
import at.bernhardberger.tvheadend.sdk.core.ServerProfile
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionRecoveryDisposition
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.core.ConnectionUiState
import at.bernhardberger.tvhplayer.data.ConnectionFailureKind
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.components.ProvideAppArtwork
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The production live screen opened before its session is ready. The screen stays composed after
 * its close here, as it does through the route's exit transition.
 */
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LivePlayerServerWaitTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = ImageLoaderRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = ChannelCatalog.create((1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) })
    private lateinit var player: ExoPlayer
    private lateinit var session: FakeTvheadendSession
    private lateinit var runtime: AppPlaybackRuntime
    private lateinit var profiles: AppProfileOwner
    private var closes = 0
    /** Key events no player handler consumed. */
    private var unconsumed = 0

    @After fun after() {
        models.clear()
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun backWithdrawsTheWaitSoASessionArrivingDuringTheExitTunesNothing() {
        screen()
        key(Key.Back)
        assertEquals(1, closes)
        session.publish(ready())
        settle()
        assertEquals(0, binds())
        assertEquals(null, runtime.activeTarget.value)
    }

    @Test fun theWaitConsumesWholeKeyCyclesButBack() {
        screen()
        compose.onRoot().performKeyInput {
            listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter,
                Key.ChannelUp, Key.Two).forEach { pressKey(it) }
            keyDown(Key.DirectionDown)
            // Held long enough to repeat.
            advanceEventTime(800)
            keyUp(Key.DirectionDown)
        }
        settle()
        assertEquals(0, unconsumed)
        assertEquals(1, waits())
        assertEquals(0, closes)
        key(Key.Back)
        assertEquals(1, closes)
    }

    @Test fun aConfirmHeldWhenTheSessionArrivesCannotOpenTheControls() {
        screen()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        session.publish(ready())
        settle()
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        assertEquals(0, unconsumed)
        assertEquals(0, compose.onAllNodes(hasTestTag("player-actions"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // Opened before Ready, after a connection save to this profile: its saved logo shows at once.
    @Test fun theWaitShowsTheSavedLogoOfTheProfileItsObservationBelongsTo() {
        val requests = java.util.Collections.synchronizedList(mutableListOf<AppArtworkSource>())
        images.install(ImageLoader.Builder(context)
            .components {
                add(Interceptor { chain ->
                    (chain.request.data as? AppArtworkSource)?.let(requests::add)
                    SuccessResult(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.DISK)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build())

        screen(profile = PROFILE)

        val logos = synchronized(requests) { requests.filter { it.id == LOGO }.distinct() }
        assertEquals(1, logos.size)
        assertNull(logos.single().currentSession)
        assertNotNull("the wait's logo is the saved copy of its observation's profile", logos.single().saved)
    }

    @Test fun aRecreatedScreenWithAnActiveTargetStillHandlesTheLossAndRetunesOnReconnect() {
        screen(initial = ready())
        compose.runOnIdle {
            scope.launch { runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))) }
        }
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        settle()
        val before = binds()
        // The connection is lost while this screen is away; a new screen opens with the target still active.
        compose.runOnIdle { visible.value = false }
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog)))
        settle()
        compose.runOnIdle { visible.value = true }
        settle()
        assertEquals(1, waits())
        session.publish(ready())
        settle()
        compose.waitUntil(10_000) { binds() > before }
    }

    @Test fun aProfileSavedDuringTheOutageRetunesNothingAway() {
        screen(initial = ready())
        compose.runOnIdle {
            scope.launch { runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))) }
        }
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        settle()
        val before = binds()
        compose.runOnIdle { visible.value = false }
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog)))
        settle()
        val generation = profiles.configurationGeneration.value
        compose.runOnIdle { scope.launch { profiles.saveServer("other.test", 9982) } }
        compose.waitUntil(10_000) { profiles.configurationGeneration.value > generation }
        session.publish(ready())
        settle()
        val retunes = mutableListOf<PlaybackTargetResult?>()
        compose.runOnIdle { scope.launch { retunes += runtime.retuneAfterReconnect() } }
        compose.waitUntil(10_000) { retunes.isNotEmpty() }
        assertEquals(listOf(null), retunes)
        assertEquals("channel one of another server is not this one", before, binds())
    }

    // Warm playback behind browse: the whole outage passes while no live screen is shown, and
    // browse's owner asks for the retune when the session is current again.
    @Test fun aLossAndReconnectWhileTheScreenIsAwayRetunesTheWarmTarget() {
        screen(initial = ready())
        compose.runOnIdle {
            scope.launch { runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))) }
        }
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        settle()
        val before = binds()
        compose.runOnIdle { visible.value = false }
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog)))
        settle()
        session.publish(ready())
        settle()
        val retunes = mutableListOf<PlaybackTargetResult?>()
        compose.runOnIdle { scope.launch { retunes += runtime.retuneAfterReconnect() } }
        // Compose's clock does not complete the runtime's asynchronous retune.
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            retunes.isNotEmpty()
        }
        assertEquals(listOf(PlaybackTargetResult.STARTED), retunes)
        compose.waitUntil(10_000) { binds() > before }
        compose.runOnIdle { visible.value = true }
        settle()
        assertEquals("the returning screen adopts the retuned target", before + 1, binds())
    }

    @Test fun aWaitWhoseConnectionRetriesByItselfOffersNoRetry() {
        screen()
        compose.runOnIdle {
            connection.value = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
        }
        settle()
        compose.onNode(hasText(string(R.string.error_110_title))).assertExists()
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Connection settings")).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasText("Retry"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-secondary"), useUnmergedTree = true).fetchSemanticsNodes().size)
        // Connection settings leaves through the screen's close.
        key(Key.DirectionCenter)
        assertEquals(1, closes)
    }

    @Test fun aWaitWhoseConnectionNeedsTheViewerOffersRetryFirst() {
        screen()
        compose.runOnIdle {
            connection.value = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)
        }
        settle()
        compose.onNode(hasText(string(R.string.error_101_title))).assertExists()
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Connection settings"))
    }

    private val visible = MutableStateFlow(true)
    /** AppRoot's connection state; null derives it from the observation. */
    private val connection = mutableStateOf<ConnectionUiState?>(null)

    private companion object {
        val LOGO = ArtworkId(1)
        val PROFILE = ServerProfile("tvheadend.test")
    }

    private fun waits() = compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size

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

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(catalog),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private fun screen(
        initial: SessionObservation = SessionObservation.create(
            sessionState = SessionState.Connecting,
            channelState = ChannelRepositoryState.Stale(catalog),
        ),
        // The profile the observation is data of, with saved logos and channel one's icon.
        profile: ServerProfile? = null,
    ) {
        session = FakeTvheadendSession(initial).apply { scriptLivePlaybackSuccess() }
        if (profile != null) {
            session.artwork.scriptCached(profile)
            session.publish(SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Stale(ChannelCatalog.create((1..3L).map {
                    Channel.create(ChannelId(it), name = "Name $it", number = it, icon = LOGO.takeIf { _ -> it == 1L })
                }))), profile)
        }
        val data = InMemoryData()
        val settings = PlayerSettingsStore(data)
        profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryData()))
        models.put("channels", channels)
        compose.waitUntil(10_000) { channels.scope.value.scope.visibleChannels.size == 3 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings())
        val video = VideoPlayerViewModel(runtime, session)
        models.put("video", video)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TVHeadendPlayerTheme { ProvideAppArtwork(session) {
                    Box(Modifier.onKeyEvent { unconsumed++; false }) {
                        if (visible.collectAsState().value) {
                            VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), settings,
                                channels, session, ChannelId(1), "Name 1", {},
                                { closes++ }, runtime, notices = notices, uiSettingsStore = UiSettingsStore(data),
                                connectionUiState = connection.value)
                        }
                    }
                } }
            }
        }
        settle()
        if (initial.currentSession == null) compose.waitUntil(10_000) { waits() == 1 }
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)
}
