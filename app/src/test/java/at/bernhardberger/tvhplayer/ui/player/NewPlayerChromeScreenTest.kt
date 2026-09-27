@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
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
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOpener
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.playback.PlaybackTrace
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerChromeDesign
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import coil3.ImageLoader
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The production live screen with the player-design setting: New swaps the header, status and
 * centre slots and adds the Banner, Stream & signal page; Current composes the old slots only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class NewPlayerChromeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var view: View

    @After fun after() {
        models.clear()
        scope.cancel()
        if (::player.isInitialized) player.release()
    }

    @Test fun currentComposesTheOldSlotsOnly() {
        screen(PlayerChromeDesign.CURRENT)
        assertTrue("controls on entry", exists("player-actions"))
        assertTrue(exists("player-channel-identity"))
        assertTrue(exists("player-clock"))
        for (tag in TRIAL_TAGS) assertFalse("$tag in Current", exists(tag))

        key(Key.Info)
        assertTrue(exists("live-info-panel"))
        assertFalse("no Stream & signal in Current", exists("live-info-stream-signal"))
    }

    @Test fun newEntersWithAPassiveBannerAndOkLandsOnTheControls() {
        screen(PlayerChromeDesign.NEW)
        assertTrue("Banner on entry", exists("trial-banner"))
        assertTrue(exists("trial-info-bar"))
        assertTrue(exists("trial-top-cluster"))
        assertFalse("no actions in the Banner", exists("player-actions"))
        for (tag in OLD_TAGS) assertFalse("$tag in New", exists(tag))
        assertEquals("nothing in the Banner is focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("trial-banner")) and isFocusable()).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("trial-banner") and isFocusable()).fetchSemanticsNodes().size)

        val playWhenReady = player.playWhenReady
        key(Key.DirectionCenter)
        assertFalse("OK replaces the Banner", exists("trial-banner"))
        assertTrue(exists("player-actions"))
        assertTrue("controls keep the New slots", exists("trial-info-bar"))
        assertTrue(exists("trial-top-cluster"))
        for (tag in OLD_TAGS) assertFalse("$tag in New controls", exists(tag))
        val initial = if (exists("player-pause")) "player-pause" else "player-info"
        assertEquals(listOf(initial), focused())
        assertEquals("OK is consumed: the pause was not toggled", playWhenReady, player.playWhenReady)
        assertEquals("the info bar is never focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("trial-info-bar")) and isFocusable()).fetchSemanticsNodes().size +
                compose.onAllNodes(hasTestTag("trial-info-bar") and isFocusable()).fetchSemanticsNodes().size)
        assertEquals("the status cluster is never focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("trial-top-cluster")) and isFocusable()).fetchSemanticsNodes().size)
    }

    @Test fun newBackHidesTheBannerWithoutClosingThePlayer() {
        var closed = false
        screen(PlayerChromeDesign.NEW, onClose = { closed = true })
        assertTrue(exists("trial-banner"))
        key(Key.Back)
        assertFalse(exists("trial-banner"))
        assertFalse(exists("player-actions"))
        assertFalse("Back hid the Banner only", closed)
    }

    @Test fun newBannerStaysWhileTheChannelIsTuning() {
        screen(PlayerChromeDesign.NEW)
        // Well past the Banner's 5 s: no frame was presented, so it stays while tuning.
        repeat(8) { settle() }
        assertTrue(exists("trial-banner"))
        assertFalse("the Banner replaces the standalone chip", exists("trial-standalone-status"))
        assertFalse("New keeps the centre for unavailable and recovery only", exists("player-tuning-status"))
    }

    @Test fun newInfoOpensStreamAndSignalAndBackRestoresItsFocus() {
        screen(PlayerChromeDesign.NEW)
        key(Key.Info)
        assertTrue(exists("live-info-panel"))
        assertTrue(exists("live-info-stream-signal"))
        var presses = 0
        while (focused() != listOf("live-info-stream-signal") && presses < 8) {
            key(Key.DirectionDown)
            presses++
        }
        assertEquals("Stream & signal is reachable by D-pad Down", listOf("live-info-stream-signal"), focused())

        key(Key.DirectionCenter)
        assertTrue(exists("live-info-stream-signal-details"))
        assertFalse(exists("live-info-stream-signal"))
        assertTrue("details take focus",
            compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())

        key(Key.Back)
        assertTrue("Back returns to Info", exists("live-info-panel"))
        assertFalse(exists("live-info-stream-signal-details"))
        assertEquals(listOf("live-info-stream-signal"), focused())

        key(Key.Back)
        assertFalse(exists("live-info-panel"))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun newUnavailableShowsOnlyTheCentreMessage() = unavailable("en", 1f)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun newUnavailableShowsOnlyTheCentreMessageAtLargeText() = unavailable("en", 1.3f)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun newUnavailableShowsOnlyTheCentreMessageInGerman() = unavailable("de", 1f)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun newUnavailableShowsOnlyTheCentreMessageInGermanAtLargeText() = unavailable("de", 1.3f)

    private fun unavailable(locale: String, fontScale: Float) {
        screen(PlayerChromeDesign.NEW, failing = true, fontScale = fontScale)
        repeat(4) { settle() }
        assertTrue("centre message", exists("player-channel-unavailable"))
        assertFalse("the Banner gives way to the centre message", exists("trial-banner"))
        assertFalse(exists("trial-standalone-status"))
        assertFalse(exists("player-tuning-status"))
        assertFalse(exists("player-buffering-status"))
        // Evidence capture of the production screen, 960×540, into the ignored artifacts directory.
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("../artifacts/ui-trial/stage2").apply { mkdirs() }
        File(directory, "unavailable-$locale-font$fontScale.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun focused(): List<String> =
        compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun settle() {
        repeat(20) {
            compose.mainClock.advanceTimeBy(100)
            compose.waitForIdle()
        }
    }

    private fun screen(design: PlayerChromeDesign, onClose: () -> Unit = {}, failing: Boolean = false, fontScale: Float = 1f) {
        val now = System.currentTimeMillis() / 1_000L
        val session = FakeTvheadendSession(SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) },
            )),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = listOf(
                EpgEvent.create(EventId(11L), ChannelId(1L), Instant.fromEpochSeconds(now - 1_800L),
                    Instant.fromEpochSeconds(now + 3_600L), title = "Now showing", summary = "Summary"),
                EpgEvent.create(EventId(12L), ChannelId(1L), Instant.fromEpochSeconds(now + 3_600L),
                    Instant.fromEpochSeconds(now + 7_200L), title = "Later"),
            ))),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
        )).apply {
            // The subscription never becomes playable: the channel stays tuning, as on a slow tune.
            // Failing: the scripted stream cannot be decoded, so the channel becomes unavailable.
            if (failing) scriptLivePlaybackSuccess()
            else scriptLivePlaybackSuccess(SubscriptionOpener { _, _, _ -> awaitCancellation() })
        }
        val settings = PlayerSettingsStore(InMemoryData())
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryData()))
        models.put("channels", channels)
        compose.waitUntil(10_000) { channels.channels.value.size == 3 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val runtime = AppPlaybackRuntime(player, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(object : PlaybackTrace {}))
        val video = VideoPlayerViewModel(runtime, session)
        models.put("video", video)
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        compose.waitForIdle()
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                TVHeadendPlayerTheme {
                    view = LocalView.current
                    VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), settings,
                        channels, ImageLoader.Builder(context).build(), session, ChannelId(1), "Name 1", {}, onClose, runtime,
                        chromeDesign = design)
                }
            }
        }
        compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        settle()
        compose.mainClock.autoAdvance = false
        settle()
    }

    private class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }

    private companion object {
        val TRIAL_TAGS = listOf("trial-banner", "trial-info-bar", "trial-top-cluster", "trial-standalone-status")
        val OLD_TAGS = listOf("player-channel-identity", "player-picon", "player-clock", "player-clock-status")
    }
}
