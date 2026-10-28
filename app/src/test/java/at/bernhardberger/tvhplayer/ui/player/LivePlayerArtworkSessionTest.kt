@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
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
import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.ChannelIcon
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
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.core.AppArtworkSource
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
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
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The live player loads its logos with the session of its own observation, not with whichever
 * session the app root provides: the root and the screen collect the same flow independently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LivePlayerArtworkSessionTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val images = ImageLoaderRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = Collections.synchronizedList(mutableListOf<Any>())
    private lateinit var player: ExoPlayer

    @After fun after() {
        models.clear()
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun theLivePlayerLoadsItsLogosWithItsOwnSessionWhateverTheRootProvides() {
        val playerSession = session()
        val rootSession = session()
        val own = requireNotNull(playerSession.observation.value.currentSession)
        val root = requireNotNull(rootSession.observation.value.currentSession)
        assertNotSame(own, root)

        show(playerSession, rootSession)

        val logos = synchronized(requests) { requests.filterIsInstance<AppArtworkSource>().filter { it.id == LOGO } }
        assertTrue("the player drew its channel logo", logos.isNotEmpty())
        assertEquals(emptyList<AppArtworkSource>(), logos.filter { it.currentSession === root })
        assertTrue(logos.all { it.currentSession === own })
    }

    private fun session() = FakeTvheadendSession(SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
            listOf(Channel.create(ChannelId(1), name = "Name 1", number = 1, icon = ChannelIcon.ImageCache(LOGO))),
        )),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )).apply { scriptLivePlaybackSuccess() }

    private fun show(playerSession: FakeTvheadendSession, rootSession: FakeTvheadendSession) {
        images.install(ImageLoader.Builder(context)
            .components {
                add(Interceptor { chain ->
                    requests += chain.request.data
                    SuccessResult(Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888).asImage(), chain.request, DataSource.MEMORY)
                })
            }
            .coroutineContext(Dispatchers.Main.immediate)
            .diskCache(null)
            .build())
        val settingsData = InMemoryData()
        val settings = PlayerSettingsStore(settingsData)
        val profiles = AppProfileOwner(playerSession, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(playerSession, ChannelTagSettingsStore(InMemoryData()))
        models.put("channels", channels)
        compose.waitUntil(10_000) { channels.scope.value.scope.visibleChannels.size == 1 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val runtime = AppPlaybackRuntime(player, playerSession, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings())
        val video = VideoPlayerViewModel(runtime, playerSession)
        models.put("video", video)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, playerSession.observation.value.currentSession?.generationIdentity)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TVHeadendPlayerTheme {
                    ProvideAppArtwork(rootSession) {
                        VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), TargetAspect(AspectRatioMode.FIT) {},
                            channels, playerSession, ChannelId(1), "Name 1", {}, {}, runtime,
                            notices = notices, uiSettingsStore = at.bernhardberger.tvhplayer.settings.UiSettingsStore(settingsData))
                    }
                }
            }
        }
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        compose.waitForIdle()
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        val LOGO = ArtworkId(1)
    }
}
