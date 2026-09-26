@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class)

package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
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
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.playback.PlaybackTrace
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import coil3.ImageLoader
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The production live screen owns each live start once: a key's own start is not repeated by
 * the entry/resume effect, a superseded or stopped start never installs, and a number typed
 * before leaving never tunes. Counts the fake session's live bindings, one per installed start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LiveZapStartOwnershipTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsData = GatedPreferences()
    private lateinit var player: ExoPlayer
    private lateinit var session: FakeTvheadendSession
    private lateinit var runtime: AppPlaybackRuntime
    private lateinit var lifecycle: LifecycleRegistry
    private val admissions = AtomicInteger()

    @After fun after() {
        models.clear()
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun keyStartIsNotRepeatedByTheEntryEffect() {
        screen()
        // The key's start is still waiting when the key's recomposition runs the entry effect.
        settingsData.open.value = false
        key(Key.ChannelUp)
        settle()
        settingsData.open.value = true
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
        settle()
        assertEquals(2, admissions.get())
        assertEquals(2, binds())
    }

    @Test fun startsSupersededWhileAdmissionIsHeldNeverInstall() {
        screen()
        // The runtime admits the next start and holds it before installation.
        settingsData.open.value = false
        key(Key.ChannelUp)
        key(Key.Three)
        key(Key.DirectionCenter)
        settingsData.open.value = true
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(3)) }
        settle()
        assertEquals(2, binds())
    }

    @Test fun startsSupersededWhileAdmissionIsHeldNeverInstallAfterTheScreenStops() {
        screen()
        // Each key's start supersedes the one before; leaving cancels the last one.
        settingsData.open.value = false
        key(Key.ChannelUp)
        key(Key.Three)
        key(Key.DirectionCenter)
        key(Key.Two)
        key(Key.DirectionCenter)
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.CREATED }
        settle()
        settingsData.open.value = true
        settle()
        assertEquals(1, binds())
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun startAdmittedBeforeTheScreenStopsNeverInstallsBeforeAnyRecomposition() {
        screen()
        stopWhileTheKeyStartIsAdmitted()
        assertEquals(1, binds())
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun startCancelledByStoppingIsRestartedOnceOnReturn() {
        screen()
        stopWhileTheKeyStartIsAdmitted()
        // A stopped window composes nothing: the return is its first composition since the key.
        compose.runOnUiThread { lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeBy(16)
            runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2))
        }
        settle()
        // Entry, the cancelled key start and its one restart.
        assertEquals(3, admissions.get())
        assertEquals(2, binds())
    }

    @Test fun entryStartCancelledByStoppingIsRestartedOnceOnReturn() {
        screen(entryHeld = true)
        val entryAdmissions = admissions.get()
        compose.runOnUiThread { lifecycle.currentState = Lifecycle.State.CREATED }
        settingsData.open.value = true
        drainWithoutFrames()
        assertEquals(0, binds())
        compose.runOnUiThread { lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeBy(16)
            runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1))
        }
        settle()
        assertEquals(entryAdmissions + 1, admissions.get())
        assertEquals(1, binds())
    }

    @Test fun numberTypedBeforeLeavingNeverTunes() {
        screen()
        key(Key.Two)
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.CREATED }
        compose.mainClock.advanceTimeBy(CHANNEL_NUMBER_READY_BOUND_MS + 2_000)
        settle()
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.RESUMED }
        settle()
        assertEquals(1, binds())
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    /**
     * CH+ is admitted and held; the screen stops; then the runtime could proceed. Nothing
     * composes in between, as in a stopped window whose frame clock is paused.
     */
    private fun stopWhileTheKeyStartIsAdmitted() {
        settingsData.open.value = false
        val admitted = admissions.get()
        compose.onRoot().performKeyInput { pressKey(Key.ChannelUp) }
        compose.runOnUiThread { lifecycle.currentState = Lifecycle.State.CREATED }
        assertEquals(admitted + 1, admissions.get())
        settingsData.open.value = true
        drainWithoutFrames()
    }

    /** Lets the runtime run on without advancing the frame clock: nothing recomposes. */
    private fun drainWithoutFrames() = repeat(50) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(10)
    }

    private fun binds() = session.calls.count { it == FakeSessionCall.BIND_LIVE_PLAYBACK }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun settle() {
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun screen(entryHeld: Boolean = false) {
        session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) },
            )),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).apply { scriptLivePlaybackSuccess() }
        val settings = PlayerSettingsStore(settingsData)
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryData()))
        models.put("channels", channels)
        compose.waitUntil(10_000) { channels.channels.value.size == 3 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(object : PlaybackTrace {
                override val enabled = true
                override fun tuneAdmitted() { admissions.incrementAndGet() }
            }))
        val video = VideoPlayerViewModel(runtime, session)
        models.put("video", video)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        lifecycle = owner.lifecycle
        lifecycle.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()
        if (entryHeld) settingsData.open.value = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TVHeadendPlayerTheme {
                    VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), settings,
                        channels, ImageLoader.Builder(context).build(), session, ChannelId(1), "Name 1", {}, {}, runtime)
                }
            }
        }
        if (entryHeld) {
            // The entry effect's own start is admitted and held. (A held entry start is
            // restarted once while the session settles, so the count is taken afterwards.)
            compose.waitUntil(10_000) { admissions.get() >= 1 }
            settle()
            compose.mainClock.autoAdvance = false
            return
        }
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        settle()
        assertEquals(1, binds())
        // Number and settle timers run only when a test advances the clock.
        compose.mainClock.autoAdvance = false
    }

    private open class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }

    /** Preferences whose readers wait while [open] is false: the runtime reads them after admission. */
    private class GatedPreferences : InMemoryData() {
        val open = MutableStateFlow(true)
        override val data: Flow<Preferences> = flow {
            open.first { it }
            emitAll(state)
        }
    }
}
