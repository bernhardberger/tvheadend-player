@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.view.View
import android.os.Looper
import java.io.File
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvheadend.sdk.android.ServerProfileEditReadResult
import at.bernhardberger.tvheadend.sdk.core.CapabilityAccess
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.DvrEntry
import at.bernhardberger.tvheadend.sdk.core.DvrEntryId
import at.bernhardberger.tvheadend.sdk.core.DvrEntryState
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.NoticeContext
import at.bernhardberger.tvhplayer.notices.NoticeSeverity
import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.DvrMutationFeedback
import at.bernhardberger.tvheadend.sdk.core.DvrMutationKind
import at.bernhardberger.tvheadend.sdk.core.DvrMutationResult
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
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionCondition
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOpener
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStream
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvheadend.sdk.playback.createSubscriptionManager
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import kotlinx.coroutines.runBlocking
import at.bernhardberger.tvheadend.sdk.media3.createTvheadendPlaybackCoordinator
import at.bernhardberger.tvheadend.sdk.testing.FakeServerProfileStore
import at.bernhardberger.tvheadend.sdk.testing.FakeTvheadendSession
import at.bernhardberger.tvheadend.sdk.testing.FakeSessionCall
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequest
import at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.WarmReturnOpportunity
import at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.playback.PlaybackTrace
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
import at.bernhardberger.tvhplayer.ui.AppRootPlaybackOrchestrator
import at.bernhardberger.tvhplayer.ui.performMainStartupBack
import at.bernhardberger.tvhplayer.ui.ChannelsKey
import at.bernhardberger.tvhplayer.viewmodels.ChannelsViewModel
import at.bernhardberger.tvhplayer.viewmodels.VideoPlayerViewModel
import coil3.ImageLoader
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The production live screen's chrome: the top cluster, info bar and status chip, the Banner and
 * programme Info and Stats for nerds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LivePlayerChromeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val models = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var view: View
    private lateinit var runtime: AppPlaybackRuntime
    private lateinit var session: FakeTvheadendSession
    private val notices = NoticeCenter({ 0L }) { NoticeContext(0, null) }
    private lateinit var video: VideoPlayerViewModel
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    /** Listeners on the runtime's player, so tests can drive states the fake stream never reaches. */
    private val playerListeners = mutableListOf<Player.Listener>()
    /** The player reports READY and playing, as when the tune's stream has started. */
    private var forcedReady = false
    /** Tracks the player reports, for services the fake stream never describes. */
    private var forcedTracks: Tracks? = null
    private var forcedVideoFormat: Format? = null
    private var forcedAudioFormat: Format? = null
    private val screenVisible = mutableStateOf(true)

    // The preferencesDataStore delegate is a sandbox singleton, not a per-Application fixture.
    // Snapshot the real store rather than replacing production persistence or leaving channel 2
    // selected for the next screen test. There is deliberately no production reset API.
    @Suppress("UNCHECKED_CAST")
    private val lastPlayedPreferences by lazy {
        Class.forName("at.bernhardberger.tvhplayer.stores.LastPlayedChannelStoreKt")
            .getDeclaredMethod("getApplianceDataStore", android.content.Context::class.java)
            .apply { isAccessible = true }.invoke(null, context) as DataStore<Preferences>
    }
    private lateinit var originalLastPlayed: Preferences
    @Before fun before() {
        originalLastPlayed = runBlocking { lastPlayedPreferences.data.first() }
    }

    @After fun after() {
        closeScreen()
        runBlocking { lastPlayedPreferences.updateData { originalLastPlayed } }
    }

    private var screenClosed = false
    private fun closeScreen() {
        if (screenClosed) return
        screenClosed = true
        compose.runOnIdle { screenVisible.value = false }
        compose.waitForIdle()
        models.clear()
        scope.cancel()
        if (::player.isInitialized) player.release()
        compose.waitUntil(5_000) { idleMainLooper(); scope.coroutineContext[Job]!!.isCompleted }
    }

    @Test fun startupPlayerTunesBehindCoverWithoutChromeFocusOrKeysAndWaitsForItsFrame() {
        val allowed = mutableStateOf(false)
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { allowed.value }, startup = true, onStartupOutcome = { outcomes += it })
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(exists("player-root"))
        assertFalse(exists("player-banner"))
        assertEquals(emptyList<String>(), focused())
        assertTrue("route alone is not presentation", outcomes.isEmpty())
        key(Key.DirectionDown)
        key(Key.ChannelUp)
        key(Key.DirectionCenter)
        assertEquals("covered keys cannot zap", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse(exists("player-actions"))
        playerReady()
        compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
        settle()
        assertTrue("Playing alone is not a video frame", outcomes.isEmpty())
        compose.runOnIdle { playerListeners.toList().forEach { it.onRenderedFirstFrame() } }
        settle()
        compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.PRESENTED) }
        assertFalse(exists("player-banner"))
        compose.runOnIdle { allowed.value = true }
        settle()
        key(Key.DirectionDown)
        assertTrue("controls become reachable after reveal", exists("player-actions"))
    }

    @Test fun startupAudioOnlyPlayingHandsOffWithoutAVideoFrame() {
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { false }, startup = true, onStartupOutcome = { outcomes += it })
        val audioOnly = Tracks(listOf(Tracks.Group(
            TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
            false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
        )))
        compose.runOnIdle {
            forcedTracks = audioOnly
            playerListeners.toList().forEach { it.onTracksChanged(audioOnly) }
        }
        playerReady()
        settle()
        compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.PRESENTED) }
        assertFalse(runtime.videoPresentation.value.visible)
    }

    @Test fun startupPlayerReportsItsExistingUnavailableOutcomeSoCoverCanRelease() {
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(failing = true, contentAllowed = { false }, startup = true, onStartupOutcome = { outcomes += it })
        // The real player's failure can arrive after entry settles; let its state recompose.
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.RECOVERY) }
        assertFalse(exists("player-banner"))
    }

    @Test fun startupWarmEntryAdoptsPlayingMatchingTuneWithoutAnotherFrameOrRetune() {
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { false }, startup = true, onStartupOutcome = { outcomes += it })
        playerReady()
        settle()
        compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
        compose.runOnIdle { playerListeners.toList().forEach { it.onRenderedFirstFrame() } }
        settle()
        assertEquals(listOf(MainStartupPlaybackOutcome.PRESENTED), outcomes)
        outcomes.clear()
        val epoch = runtime.videoPresentation.value.epoch
        assertTrue(runtime.videoPresentation.value.visible)
        compose.runOnIdle { screenVisible.value = false }
        settle()
        compose.runOnIdle { screenVisible.value = true }
        settle()
        compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.ALREADY_PLAYING) }
        assertEquals("warm adoption does not install another tune", epoch, runtime.videoPresentation.value.epoch)
        assertTrue("the already-presented frame is retained", runtime.videoPresentation.value.visible)
    }

    @Test fun aNewStartupRequestOnTheSameWarmRouteOwnsFreshIntentWithoutRetuning() {
        val requestId = mutableStateOf(1L)
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { false }, startup = true, startupRequestId = { requestId.value },
            onStartupOutcome = { outcomes += it })
        playerReady()
        settle()
        compose.runOnIdle { playerListeners.toList().forEach { it.onRenderedFirstFrame() } }
        settle()
        assertEquals(listOf(MainStartupPlaybackOutcome.PRESENTED), outcomes)
        outcomes.clear()
        val epoch = runtime.videoPresentation.value.epoch
        assertTrue(outcomes.isEmpty())
        compose.runOnIdle {
            runtime.notePlaybackIntent() // The launch owner notes new intent before resolving this route.
            requestId.value = 2L
        }
        settle()
        compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.ALREADY_PLAYING) }
        assertEquals(epoch, runtime.videoPresentation.value.epoch)
        assertTrue(runtime.videoPresentation.value.visible)
    }

    @Test fun cancellingCommittedStartupBeforeItsFrameStopsOnlyItsTuneAndDisarmsWarmReturn() {
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { false }, startup = true, onStartupOutcome = { outcomes += it })
        playerReady()
        settle()
        assertTrue(outcomes.isEmpty())
        val root = AppRootPlaybackOrchestrator().apply { activePlaybackChanged(ChannelId(1), null) }
        val requests = ApplianceLaunchRequests().apply { request() }
        val pending = requests.state.value as ApplianceLaunchState.Pending
        requests.resolve(pending.request, CurrentChannelReadiness.Ready(listOf(Channel.create(ChannelId(1), name = "Name 1"))), ChannelId(1))
        compose.runOnIdle {
            performMainStartupBack(requests, requests.state.value) { destination ->
                assertEquals(ChannelsKey, destination)
                screenVisible.value = false
            }
            assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        }
        settle()
        compose.waitUntil(5_000) { idleMainLooper(); runtime.state.value is AppPlaybackState.Idle }
        assertEquals(null, runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
        root.activePlaybackChanged(null, null)
        assertEquals(WarmReturnOpportunity(), root.warmReturn)
        assertNotNull("cleanup is not the user's Stop intent", runtime.enterPlayerScreen())
        assertTrue(outcomes.isEmpty())
    }

    @Test fun startupWarmAdoptionWithoutAPresentedFrameWaitsButCancellationPreservesTheWarmTune() {
        val startup = mutableStateOf(false)
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { !startup.value }, startupEnabled = { startup.value }, onStartupOutcome = { outcomes += it })
        playerReady()
        settle()
        val epoch = runtime.videoPresentation.value.epoch
        compose.runOnIdle { screenVisible.value = false }
        settle()
        compose.runOnIdle { startup.value = true; screenVisible.value = true }
        settle()
        assertTrue("Playing without this tune's frame cannot dismiss startup", outcomes.isEmpty())
        assertFalse(exists("player-banner"))
        assertEquals(emptyList<String>(), focused())
        assertEquals(epoch, runtime.videoPresentation.value.epoch)
        compose.runOnIdle { screenVisible.value = false }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue(runtime.state.value is AppPlaybackState.Playing)
        assertEquals("an adopted tune is not owned by cancelled startup", epoch, runtime.videoPresentation.value.epoch)
    }

    @Test fun ordinaryWarmReturnDoesNotRequireStartupSessionProofOrRetune() {
        screen()
        playerReady()
        settle()
        val epoch = runtime.videoPresentation.value.epoch
        compose.runOnIdle { session.replaceGeneration(session.observation.value) }
        settle()
        val selection = requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))
        assertEquals(null, runtime.liveTargetPresentation(selection))
        compose.runOnIdle { screenVisible.value = false }
        settle()
        compose.runOnIdle { screenVisible.value = true }
        settle()
        key(Key.DirectionDown)
        assertTrue(exists("player-actions"))
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals("ordinary adoption keeps its previous contract", epoch, runtime.videoPresentation.value.epoch)
    }

    @Test fun ordinaryUnresolvedChannelKeepsWaitingInsteadOfReportingStartupFailure() {
        screen(missingRequestedChannel = true, settleOnEntry = false)
        settle()
        assertNotNull(session.observation.value.currentSession)
        assertEquals(null, runtime.activeTarget.value)
        assertFalse(exists("player-channel-unavailable"))
        assertTrue(runtime.state.value is AppPlaybackState.Idle)
    }

    @Test fun successfulStartupWithoutAnOwnedReceiptReportsRecoveryInsteadOfWaitingForever() {
        val startup = mutableStateOf(true)
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(settleOnEntry = false, contentAllowed = { !startup.value }, startupEnabled = { startup.value },
            afterTargetPlay = {
                // Supersede attribution at the committed tune, before the initial start returns.
                runtime.notePlaybackIntent().also(runtime::notePlaybackIntentServed)
            }, onStartupOutcome = { outcomes += it; startup.value = false })
        settle()
        compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.RECOVERY) }
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue("existing recovery is actionable after startup releases", exists("player-channel-unavailable"))
    }

    @Test fun backgroundCancellationAtCommitBeforeReceiptRetiresTheUndeliveredStartupTune() {
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(settleOnEntry = false, contentAllowed = { false }, startup = true,
            afterTargetPlay = { owner.lifecycle.currentState = Lifecycle.State.CREATED },
            onStartupOutcome = { outcomes += it })
        settle()
        compose.waitUntil(5_000) { idleMainLooper(); runtime.state.value is AppPlaybackState.Idle }
        assertEquals(null, runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
        assertTrue(outcomes.isEmpty())
        assertNotNull(runtime.enterPlayerScreen())
    }

    @Test fun rejectedStartupRecoveryCannotTransferItsUnpresentedTuneAfterOwnerCancellation() {
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { false }, startup = true, acceptStartupOutcome = { false },
            onStartupOutcome = { outcomes += it; screenVisible.value = false })
        compose.runOnIdle { session.replaceGeneration(session.observation.value) }
        settle()
        assertEquals(listOf(MainStartupPlaybackOutcome.RECOVERY), outcomes)
        compose.waitUntil(5_000) { idleMainLooper(); runtime.state.value is AppPlaybackState.Idle }
        assertEquals(null, runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
    }

    @Test fun playerEntersWithAPassiveBannerAndDownLandsOnTheControls() {
        screen()
        assertTrue("Banner on entry", exists("player-banner"))
        assertTrue(exists("player-info-bar"))
        assertTrue(exists("player-top-cluster"))
        assertFalse("no actions in the Banner", exists("player-actions"))
        assertEquals("nothing in the Banner is focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("player-banner")) and isFocusable()).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("player-banner") and isFocusable()).fetchSemanticsNodes().size)

        val playWhenReady = player.playWhenReady
        key(Key.DirectionDown)
        assertFalse("the controls replace the Banner", exists("player-banner"))
        assertTrue(exists("player-actions"))
        assertTrue("controls keep the player slots", exists("player-info-bar"))
        assertTrue(exists("player-top-cluster"))
        assertEquals("the controls open on Play/Pause", listOf("player-pause"), focused())
        assertEquals("Down is consumed: the pause was not toggled", playWhenReady, player.playWhenReady)
        assertEquals("the card is the one focusable thing in the info bar", listOf("player-identity-card"),
            (compose.onAllNodes(hasAnyAncestor(hasTestTag("player-info-bar")) and isFocusable()).fetchSemanticsNodes() +
                compose.onAllNodes(hasTestTag("player-info-bar") and isFocusable()).fetchSemanticsNodes())
                .map { it.config.getOrNull(SemanticsProperties.TestTag) })
        assertFalse("the action row has no Info", exists("player-info"))
        assertEquals("the header clock is never focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("player-top-cluster")) and isFocusable()).fetchSemanticsNodes().size)
        assertTrue("the header keeps only the clock, no status chip: ${texts("player-header")}",
            texts("player-header").matches(Regex("\\d{1,2}:\\d{2}( [AP]M)?")))
    }

    @Test fun playerBackHidesTheBannerWithoutClosingThePlayer() {
        var closed = false
        screen(onClose = { closed = true })
        assertTrue(exists("player-banner"))
        key(Key.Back)
        assertFalse(exists("player-banner"))
        assertFalse(exists("player-actions"))
        assertFalse("Back hid the Banner only", closed)
    }

    @Test fun aQuickListReturnsFocusToStop() = timeshiftPlaying {
        key(Key.DirectionDown)
        assertTrue(exists("player-stop"))
        compose.onNodeWithTag("player-stop").performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        assertEquals(listOf("player-stop"), focused())
        key(Key.MediaAudioTrack)
        assertFalse("focus moved into the quick list: ${focused()}", focused() == listOf("player-stop"))
        key(Key.Back)
        assertEquals(listOf("player-stop"), focused())
    }

    @Test fun playerBannerStaysWhileTheChannelIsTuning() {
        screen()
        // Well past the Banner's 5 s: no frame was presented, so it stays while tuning.
        repeat(8) { settle() }
        assertTrue(exists("player-banner"))
        assertEquals("tuning never replaces the playback glyph", "Playing", description("player-state"))
        assertEquals("Tuning", description("player-busy-indicator"))
        assertEquals("a tune lands at live: the state is announced", "Live", liveState())
        assertFalse("and no distance is drawn at the live edge", exists("player-distance"))
        assertFalse(exists("player-hidden-slot"))
        assertFalse("the bar shows no animation while tuning", exists("player-timeline-sweep"))
        assertTrue("the bar shows the new channel's schedule at now while tuning", exists("player-timeline-fill"))
        val ring = compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(compose.onRoot().fetchSemanticsNode().boundsInRoot.center, ring.boundsInRoot.center)
        key(Key.DirectionDown)
        assertFalse(exists("player-pause-busy"))
        assertEquals(ring.id, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().id)
        key(Key.Back)
        assertFalse(exists("player-hidden-slot"))
        assertEquals(ring.boundsInRoot, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
    }

    @Test fun aPresentedTunesStallUsesBufferingAndResumingRemovesTheOverlay() = timeshiftPlaying {
        settle()
        assertFalse("a presented tune is not busy", exists("player-busy-indicator"))
        forcedReady = false
        compose.runOnIdle {
            playerListeners.toList().forEach {
                it.onPlaybackStateChanged(Player.STATE_BUFFERING)
                it.onIsPlayingChanged(false)
            }
        }
        compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Buffering }
        settle()
        assertEquals("a stall after a presented tune is not another tune", "Buffering", description("player-busy-indicator"))
        val ring = compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode()
        key(Key.DirectionDown)
        assertTrue(exists("player-actions"))
        assertFalse(exists("player-pause-busy"))
        assertEquals(ring.id, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().id)
        key(Key.Back)
        assertFalse(exists("player-hidden-slot"))
        assertEquals(ring.boundsInRoot, compose.onNodeWithTag("player-busy-indicator", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        playerReady()
        compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
        settle()
        assertFalse("resuming removes the ring", exists("player-busy-indicator"))
    }

    @Test fun backWhilePausedLeavesOnlyTheChipAtTheBottomStart() = timeshiftPlaying { pauses ->
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitUntil(3_000) { idleMainLooper(); false in pauses }
        settle()
        assertEquals("the state cell shows the real state", "Paused", description("player-state"))
        assertFalse("no status slot in the info bar", exists("player-status-slot"))
        key(Key.Back)
        assertFalse(exists("player-banner"))
        assertFalse(exists("player-actions"))
        assertFalse("no header either", exists("player-header"))
        assertTrue("only the chip stays", exists("player-hidden-slot"))
        assertFalse("intentional pause is never busy", exists("player-busy-indicator"))
        assertEquals("Paused", description("player-hidden-slot"))
        val chip = compose.onNodeWithTag("player-hidden-slot", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("at the bottom start: $chip in $root", chip.left < root.right * 0.1f && chip.top > root.bottom * 0.75f)
        assertEquals("nothing hidden is focusable", 0,
            compose.onAllNodes(hasAnyAncestor(hasTestTag("player-hidden-slot")) and isFocusable()).fetchSemanticsNodes().size)
    }

    @Test fun atTheLiveEdgeTheBarRowIsPassiveAndUpReachesOnlyTheCard() {
        liveChrome(behindLiveMs = 0)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertEquals("the card is all there is above the seekbar", listOf("player-identity-card"), focused())
        key(Key.DirectionDown)
        assertEquals("Down returns", listOf("player-seekbar"), focused())
        assertFalse(exists("player-go-live"))
        assertFalse(exists("player-status-slot"))
        assertFalse("the controls have no state cell; the Pause button speaks for playback", exists("player-state"))
        assertEquals("Live", description("player-live-state"))
        assertFalse("nothing is drawn at the live edge", exists("player-distance"))
        assertEquals(0, compose.onAllNodes(hasTestTag("player-live-state") and isFocusable(), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun behindLiveUpFromTheSeekbarReachesOnlyTheCard() {
        liveChrome(behindLiveMs = 203_000)
        assertEquals(listOf("player-pause"), focused())
        assertEquals("Behind live", description("player-live-state"))
        assertEquals("3:23 behind live", texts("player-distance"))
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertEquals("the card is all there is above the seekbar", listOf("player-identity-card"), focused())
        key(Key.DirectionDown)
        assertEquals("Down returns", listOf("player-seekbar"), focused())
        assertFalse("no Go live node", exists("player-go-live"))
        assertFalse(exists("player-go-live-label"))
        key(Key.DirectionDown)
        assertEquals(listOf("player-pause"), focused())
    }

    @Test fun theControlsShowPausedThroughThePlayButtonNotAStateCell() {
        liveChrome(behindLiveMs = 0, state = PlayerStateCell.PAUSED)
        assertFalse(exists("player-state"))
        compose.onNodeWithTag("player-pause").assertContentDescriptionEquals("Play")
    }

    /** The live chrome's controls [behindLiveMs] behind the live edge with known timing. */
    private fun liveChrome(behindLiveMs: Long, state: PlayerStateCell = PlayerStateCell.PLAYING) {
        val loader = ImageLoader(context)
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("20:15", liveInfoBarData(1, "One", null, null, false, 1_800, "Programme"), state = state),
                    timeline = PlayerChromeTimeline.Live(
                        at.bernhardberger.tvhplayer.playback.AppTimeshiftState(available = true, bufferStartMs = -600_000,
                            positionMs = -behindLiveMs, liveEdgeMs = 0, timingKnown = true),
                        nowSec = 1_800,
                    ),
                    actions = PlayerChromeActions(active = true, paused = state == PlayerStateCell.PAUSED),
                    imageLoader = loader, currentSession = null,
                    onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {},
                    onInteraction = {},
                )
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
    }

    /** A timeshift channel playing at the live edge with Pause ready; [block] sees the play-when-ready changes. */
    private fun timeshiftPlaying(block: (pauses: List<Boolean>) -> Unit) {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        try {
            screen(opener = manager)
            startLiveSubscription(connection, height = 1080L, signal = 53739L, timeshift = true)
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            playerListeners.toList().forEach { it.onRenderedFirstFrame() }
            compose.waitUntil(5_000) { runtime.videoPresentation.value.visible }
            compose.waitUntil(5_000) { idleMainLooper(); runtime.livePause.value.availability == LivePauseAvailability.READY }
            val pauses = mutableListOf<Boolean>()
            compose.runOnIdle {
                player.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { pauses += playWhenReady }
                })
            }
            block(pauses)
        } finally {
            runBlocking { manager.closeAndJoin() }
        }
    }

    @Test fun playerInfoContainsProgrammeActionsAndBackReturnsDirectlyToControls() {
        screen()
        key(Key.Info)
        assertTrue(exists("live-info-panel"))
        assertEquals(listOf("live-info-record"), focused())
        assertFalse(exists("live-info-stream-signal"))
        key(Key.Back)
        assertFalse(exists("live-info-panel"))
        assertTrue(exists("player-actions"))
    }

    @Test fun playerBannerHidesFiveSecondsAfterThisTunesFirstFrame() {
        screen()
        playerReady()
        compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
        // Playing without a picture: no video diagnostics does not prove audio-only.
        repeat(4) { settle() }
        assertTrue("no frame of this tune yet: the Banner stays", exists("player-banner"))
        assertEquals("Playing without a frame is still tuning", "Tuning", description("player-busy-indicator"))

        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        compose.waitUntil(5_000) { runtime.videoPresentation.value.visible }
        compose.mainClock.advanceTimeBy(4_800)
        compose.waitForIdle()
        assertTrue("before 5000 ms after the first frame", exists("player-banner"))
        assertFalse("the first frame removes tuning", exists("player-busy-indicator"))
        settle()
        assertFalse("5000 ms after the first frame the Banner hides", exists("player-banner"))
        assertFalse(exists("player-actions"))
    }

    @Test fun playerAudioOnlyServiceStartsTheBannerTimerWhenPlaying() {
        screen()
        val audioOnly = Tracks(listOf(Tracks.Group(
            TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()),
            false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true),
        )))
        assertTrue(trackGlance(audioOnly).audioOnly)
        forcedTracks = audioOnly
        playerListeners.toList().forEach { it.onTracksChanged(audioOnly) }
        playerReady()
        compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertTrue(exists("player-banner"))
        repeat(2) { settle() }
        assertFalse("audio-only: playing starts the Banner's 5 s", exists("player-banner"))
        assertFalse("audio-only presentation removes tuning", exists("player-busy-indicator"))
    }

    @Test fun aStoppedAndRestartedScreenShowsNoStaleBanner() {
        screen()
        assertTrue(exists("player-banner"))
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        settle()
        assertFalse("the Banner does not outlive the stop", exists("player-banner"))
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        repeat(8) { settle() }
        assertFalse("no stale Banner on return", exists("player-banner"))
        assertFalse(exists("player-actions"))
    }

    @Test fun programmeInfoUpdatesAcrossAProgrammeBoundaryAndSchedulingUpdate() {
        screen()
        key(Key.Info)
        val now = System.currentTimeMillis() / 1_000L
        val nextProgramme = EpgEvent.create(EventId(21L), ChannelId(1L), Instant.fromEpochSeconds(now - 60L),
            Instant.fromEpochSeconds(now + 3_600L), title = "Next up", summary = "Summary")
        session.publish(observation(now, listOf(nextProgramme)))
        repeat(2) { settle() }
        assertTrue(texts("live-info-panel").contains("Next up"))
        assertEquals(listOf("live-info-record"), focused())
        session.publish(observation(now, listOf(nextProgramme), recordings = listOf(
            DvrEntry.create(DvrEntryId(2L), eventId = nextProgramme.id, state = DvrEntryState.RECORDING),
        )))
        repeat(2) { settle() }
        assertEquals("Stop recording", texts("live-info-record"))
        assertEquals(listOf("live-info-record"), focused())
        key(Key.Back)
        assertFalse(exists("live-info-panel"))
    }

    @Test fun withoutEpgInfoKeepsCloseFocusedAndBackReturnsToControls() {
        screen(epg = false)
        key(Key.Info)
        assertTrue(exists("live-info-panel"))
        assertEquals(listOf("live-info-close"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-close"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-info-reading"), focused())
        listOf(Key.DirectionLeft, Key.DirectionRight).forEach(::key)
        assertEquals(listOf("player-info-reading"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-close"), focused())
        assertFalse(exists("live-info-stream-signal"))
        key(Key.Back)
        assertFalse(exists("live-info-panel"))
        assertTrue(exists("player-actions"))
        key(Key.Info)
        assertEquals("Back to TV", texts("details-player-hint"))
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        assertFalse(exists("live-info-panel"))
    }

    @Test fun playerBadgesComeFromTheSelectedTracksWithoutDiagnostics() {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, null)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        try {
            screen(opener = manager)
            val tracks = Tracks(listOf(
                Tracks.Group(TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
                    .setWidth(1920).setHeight(1080).build()), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
                Tracks.Group(TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3)
                    .setChannelCount(6).build()), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            ))
            forcedTracks = tracks
            compose.runOnIdle { playerListeners.toList().forEach { it.onTracksChanged(tracks) } }
            // Events reach only a registered collection; the media period registers it off the main thread.
            val registered = scope.async(Dispatchers.Default) { connection.awaitCollectionRegistered() }
            compose.waitUntil(5_000) { registered.isCompleted }
            runBlocking {
                connection.emit(SubscriptionEvent.Started(listOf(
                    SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                        language = null, compositionId = null, ancillaryId = null, width = 1920, height = 1080,
                        frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                        audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null),
                ), null, SubscriptionCondition.NO_DETAIL))
                // 53739 of 65535: 82 %.
                connection.emit(SubscriptionEvent.Signal(53739, 12300, null, null, 0, 0, false))
            }
            val diagnostics = runtime.diagnostics.value
            assertTrue("Stats is closed: no format or frontend diagnostics",
                diagnostics.video == null && diagnostics.audio == null && diagnostics.live == null)
            // Badges describe only the confirmed playing channel.
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            settle()
            assertTrue(exists("player-banner"))
            val badges = badgeDescription("player-banner")
            for (expected in listOf("1080 lines", "Video H.264", "Audio DD+ 5.1")) {
                assertTrue("Banner badges '$badges' contain '$expected'", badges.contains(expected))
            }
            assertFalse("no signal badge", badges.contains("Signal-to-noise"))

            // Without live Pause, Center explains over the Banner; Down opens the controls.
            key(Key.DirectionCenter)
            assertFalse(exists("player-actions"))
            assertTrue(exists("player-banner"))
            assertTrue("the reason shows in the Banner", runtime.livePause.value.availability.let {
                it != LivePauseAvailability.UNAVAILABLE && it != LivePauseAvailability.OFF
            } || compose.onAllNodesWithText("Pause isn't available", substring = true).fetchSemanticsNodes().isNotEmpty())
            key(Key.DirectionDown)
            assertTrue(exists("player-actions"))
            val controls = badgeDescription("player-info-bar")
            for (expected in listOf("1080 lines", "Video H.264", "Audio DD+ 5.1")) {
                assertTrue("controls badges '$controls' contain '$expected'", controls.contains(expected))
            }

            // Programme Info never starts full diagnostics. Stats for nerds still owns them.
            key(Key.Info)
            assertTrue(exists("live-info-panel"))
            assertTrue(runtime.diagnostics.value.let { it.video == null && it.audio == null && it.live == null })
            key(Key.Back)
            compose.onNodeWithTag("player-settings").performSemanticsAction(SemanticsActions.RequestFocus)
            key(Key.DirectionCenter)
            compose.onNodeWithTag("playback-options-stats").performSemanticsAction(SemanticsActions.RequestFocus)
            key(Key.DirectionCenter)
            key(Key.DirectionCenter)
            compose.waitUntil(5_000) { idleMainLooper(); runtime.diagnostics.value.live != null }
            key(Key.Back)
            key(Key.Back)
            assertTrue(exists("player-actions"))
            key(Key.Back)
            assertTrue(exists("playback-stats-overlay"))
            key(Key.Back)
            compose.waitUntil(5_000) { idleMainLooper(); runtime.diagnostics.value.live == null }
            assertFalse(exists("playback-stats-overlay"))
        } finally {
            runBlocking { manager.closeAndJoin() }
        }
    }

    @Test fun afterAZapNoBadgeShowsThePreviousChannelsFactsUntilTheNewChannelPlays() {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, null)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        try {
            screen(opener = manager)
            // Diagnostics on, as with Stats open: A's frontend diagnostics are present too.
            video.setDiagnosticsEnabled(true)
            val videoA = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1920).setHeight(1080).build()
            val audioA = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).setChannelCount(6).build()
            val tracksA = Tracks(listOf(
                Tracks.Group(TrackGroup(videoA), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
                Tracks.Group(TrackGroup(audioA), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            ))
            forcedTracks = tracksA
            forcedVideoFormat = videoA
            forcedAudioFormat = audioA
            compose.runOnIdle { playerListeners.toList().forEach { it.onTracksChanged(tracksA) } }
            startLiveSubscription(connection, height = 1080L, signal = 53739L) // 82 %
            compose.waitUntil(5_000) { idleMainLooper(); runtime.diagnostics.value.live?.frontend != null }
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            settle()
            val badgesA = badgeDescription("player-banner")
            for (expected in listOf("1080 lines", "Video H.264")) {
                assertTrue("A's badges '$badgesA' contain '$expected'", badgesA.contains(expected))
            }

            // Zap to B: the player still reports A's tracks and A's diagnostics.
            forcedReady = false
            key(Key.ChannelUp)
            assertTrue(exists("player-banner"))
            val staleBanner = badgeDescription("player-banner")
            for (stale in listOf("lines", "Video", "Audio", "Signal-to-noise")) {
                assertFalse("B's Banner '$staleBanner' shows no badge ('$stale')", staleBanner.contains(stale))
            }
            key(Key.Info)
            assertTrue(exists("live-info-panel"))
            key(Key.Back)
            assertFalse(exists("live-info-panel"))
            val staleControls = badgeDescription("player-info-bar")
            for (stale in listOf("lines", "Video", "Audio")) {
                assertFalse("B's controls '$staleControls' show no stale '$stale'", staleControls.contains(stale))
            }

            // B plays with its own facts.
            compose.waitUntil(5_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(2)) }
            val videoB = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(1280).setHeight(720).build()
            val tracksB = Tracks(listOf(
                Tracks.Group(TrackGroup(videoB), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            ))
            forcedTracks = tracksB
            forcedVideoFormat = videoB
            forcedAudioFormat = null
            compose.runOnIdle { playerListeners.toList().forEach { it.onTracksChanged(tracksB) } }
            startLiveSubscription(connection, height = 720L, signal = 32768L) // 50 %
            compose.waitUntil(5_000) {
                idleMainLooper()
                runtime.diagnostics.value.live?.frontend?.relativeSnrPercent?.let { it < 60.0 } == true
            }
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            settle()
            assertTrue(exists("player-info-bar"))
            val badgesB = badgeDescription("player-info-bar")
            for (expected in listOf("720 lines", "Video H.264")) {
                assertTrue("B's badges '$badgesB' contain '$expected'", badgesB.contains(expected))
            }
            assertFalse(badgesB.contains("1080"))
            assertFalse(badgesB.contains("82 percent"))
        } finally {
            runBlocking { manager.closeAndJoin() }
        }
    }

    private fun startLiveSubscription(
        connection: ScriptedSubscriptionConnection,
        height: Long,
        signal: Long,
        timeshift: Boolean = false,
    ) {
        // Events reach only a registered collection; the media period registers it off the main thread.
        val registered = scope.async(start = CoroutineStart.UNDISPATCHED) { connection.awaitCollectionRegistered() }
        compose.waitUntil(5_000) { idleMainLooper(); registered.isCompleted }
        runBlocking {
            val registration = registered.await()
            connection.emit(registration, SubscriptionEvent.Started(listOf(
                SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                    language = null, compositionId = null, ancillaryId = null, width = height * 16 / 9, height = height,
                    frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                    audioVersion = null, channelCount = null, rate = null, rdsUecp = null, codecMetadata = null),
            ), null, SubscriptionCondition.NO_DETAIL))
            connection.emit(registration, SubscriptionEvent.Signal(signal, 12300, null, null, 0, 0, false))
            // One minute buffered at the live edge.
            if (timeshift) connection.emit(registration, SubscriptionEvent.Timeshift(60_000_000L, 0L, 0L, 60_000_000L, null, null))
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aScreenZapKeepsTheNewProgrammeFromSelectionThroughKnownTiming() = screenZap()

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aScreenPauseDuringTuneKeepsTheBarAndTagWhenConfirmedWithoutTiming() = screenZap(pauseDuringTune = true)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aScreenReturnToTheSameChannelStartsANewLiveRequest() = screenZap(returnToSameChannel = true)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aZapFromAServerPausedChannelKeepsTheNewProgrammesLiveStart() = screenZap(pauseBeforeZap = true)

    @Test fun firstEntryKeepsTheLiveProgrammeOnEveryFrameUntilTimingIsKnown() = liveStartFrames(zap = false)

    @Test fun aZapKeepsTheNewLiveProgrammeOnEveryFrameUntilTimingIsKnown() = liveStartFrames(zap = true)

    @Test fun aZapFromPauseKeepsTheNewLiveProgrammeOnEveryFrameUntilTimingIsKnown() =
        liveStartFrames(zap = true, pauseBeforeZap = true)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun liveStartFrames(zap: Boolean, pauseBeforeZap: Boolean = false) {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, UnconfinedTestDispatcher()).apply { startAdmission() }
        val settingsData = GatedPreferences()
        try {
            screen(opener = manager, settingsData = settingsData, settleOnEntry = zap)
            val title = if (zap) "New programme" else "Now showing"
            val next = if (zap) "New next" else "Later"
            val fraction = if (zap) 0.75f else 1f / 3f
            var frame = 0
            fun assertFrame() {
                val phase = "${if (zap) "zap" else "entry"} frame ${frame++}"
                assertEquals("$phase: the live-start state", "Live", liveState())
                assertFalse("$phase: no distance at the live edge", exists("player-distance"))
                val info = compose.onNodeWithTag("player-info-bar").fetchSemanticsNode()
                    .config[SemanticsProperties.ContentDescription].joinToString(" ")
                assertTrue("$phase: current programme: $info", info.contains(title))
                assertTrue("$phase: Next: $info", info.contains(next))
                if (zap) assertFalse("$phase: never the previous programme: $info", info.contains("Now showing"))
                val track = compose.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val fill = compose.onNodeWithTag("player-timeline-fill", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals("$phase: only the new schedule's coordinates", fraction, (fill.right - track.left) / track.width, 0.03f)
                assertTrue("$phase: schedule end clock", exists("player-end-clock"))
            }
            fun frames(count: Int) = repeat(count) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                assertFrame()
            }
            if (zap) {
                startLiveSubscription(connection, 1080, 0, timeshift = true)
                playerReady()
                settle()
                if (pauseBeforeZap) {
                    key(Key.MediaPause)
                    assertFalse("the previous channel's player is paused", player.playWhenReady)
                    assertTrue("the previous channel's server is paused", runBlocking { runtime.sampleTimeshiftPresentation() }.paused)
                }
                val now = System.currentTimeMillis() / 1_000L
                session.publish(observation(now, listOf(
                    EpgEvent.create(EventId(20), ChannelId(2), Instant.fromEpochSeconds(now - 2_700),
                        Instant.fromEpochSeconds(now + 900), title = title),
                    EpgEvent.create(EventId(21), ChannelId(2), Instant.fromEpochSeconds(now + 900),
                        Instant.fromEpochSeconds(now + 4_500), title = next),
                )))
                settingsData.open.value = false
                forcedReady = false
                compose.onRoot().performKeyInput { keyDown(Key.ChannelUp) }
                // No settling: the first frame after the accepted key is part of the contract.
                frames(1)
                compose.onRoot().performKeyInput { keyUp(Key.ChannelUp) }
                frames(19)
                settingsData.open.value = true
            } else {
                // setContent has composed the entry, but its effects have not had another frame.
                assertFrame()
            }
            frames(20)
            startLiveSubscription(connection, 720, 0)
            playerReady()
            frames(20)
            assertFalse("confirmed without timing", runBlocking { runtime.sampleTimeshiftPresentation() }.timingKnown)
            runBlocking {
                connection.emit(SubscriptionEvent.Timeshift(60_000_000L, 0L, 0L, 60_000_000L, null, null))
                connection.emit(SubscriptionEvent.Packet(
                    at.bernhardberger.tvheadend.sdk.playback.MuxFrameType.I, StreamIndex(1),
                    60_000_000L, 60_000_000L, 40_000L,
                    at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture(byteArrayOf(0, 0, 1, 9, 0x10)),
                ))
            }
            frames(32)
            assertTrue("timing became known", runBlocking { runtime.sampleTimeshiftPresentation() }.timingKnown)
        } finally {
            try { closeScreen() } finally { runBlocking { manager.closeAndJoin() } }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun screenZap(pauseDuringTune: Boolean = false, returnToSameChannel: Boolean = false,
        pauseBeforeZap: Boolean = false) {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, UnconfinedTestDispatcher()).apply { startAdmission() }
        val settingsData = GatedPreferences()
        try {
            screen(opener = manager, settingsData = settingsData)
            val now = System.currentTimeMillis() / 1_000L
            val events = (1..2L).flatMap { channel -> listOf(
                EpgEvent.create(EventId(channel * 10), ChannelId(channel), Instant.fromEpochSeconds(now - 2_700),
                    Instant.fromEpochSeconds(now + 900), title = "Current $channel"),
                EpgEvent.create(EventId(channel * 10 + 1), ChannelId(channel), Instant.fromEpochSeconds(now + 900),
                    Instant.fromEpochSeconds(now + 4_500), title = "Next $channel"),
            ) }
            session.publish(observation(now, events))
            startLiveSubscription(connection, 1080, 0, timeshift = true)
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            settle()
            if (pauseBeforeZap) {
                compose.waitUntil(5_000) { idleMainLooper(); runtime.livePause.value.availability == LivePauseAvailability.READY }
                key(Key.MediaPause)
                assertTrue("A's server timeshift is paused", runBlocking { runtime.sampleTimeshiftPresentation() }.paused)
                settingsData.open.value = false
            }
            if (returnToSameChannel) {
                compose.waitUntil(5_000) { idleMainLooper(); runtime.livePause.value.availability == LivePauseAvailability.READY }
                // A transport command ends the undisturbed-live-start token without relying
                // on Center's layer-dependent toggle behavior or pausing the old sample.
                key(Key.MediaPlay)
                assertTrue(player.playWhenReady)
                assertEquals("transport command kept the subscription", 1, connection.subscribeCount)
                // Hold before installation (the opener is too late: activeTarget has changed
                // by then). This is the admission gate used by LiveZapStartOwnershipTest.
                settingsData.open.value = false
            }
            forcedReady = false
            compose.onRoot().performKeyInput {
                pressKey(Key.ChannelUp)
                if (returnToSameChannel) pressKey(Key.ChannelDown)
            }
            val channel = if (returnToSameChannel) 1L else 2L
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            if (!pauseBeforeZap) assertScreenProgramme(channel, "selected/masked")
            if (pauseBeforeZap) {
                // Exercise several sampling ticks while the paused A is still installed.
                settle()
                assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
                assertTrue(runBlocking { runtime.sampleTimeshiftPresentation() }.paused)
                settingsData.open.value = true
            }
            if (returnToSameChannel) {
                settle()
                assertEquals("admission gate kept the subscription", 1, connection.subscribeCount)
                assertEquals("A remains installed throughout the A -> B -> A requests",
                    AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
                settingsData.open.value = true
            }
            if (pauseDuringTune) compose.onRoot().performKeyInput { pressKey(Key.MediaPause) }
            settle()
            compose.waitUntil(5_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(channel)) }
            startLiveSubscription(connection, 720, 0)
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            settle()
            val unknown = runBlocking { runtime.sampleTimeshiftPresentation() }
            assertTrue("timeshift granted", unknown.available)
            assertFalse("no status coordinates yet", unknown.timingKnown)
            if (!pauseDuringTune) assertScreenProgramme(channel, "confirmed without timing")
            else {
                // This request already presented a schedule before Pause; the held row
                // may remain, but unknown playback must not claim its programme or Next.
                assertTrue(exists("player-timeline-fill"))
                assertUnknownProgramme()
                assertNotNull("the held row keeps its state", liveState())
            }
            if (!exists("player-actions")) key(Key.DirectionDown)
            assertTrue("controls retain the row", exists("player-timeline-track"))
            assertNotNull("controls retain the live state", liveState())
            if (!pauseDuringTune) {
                assertScreenProgramme(channel, "controls after timing notice delay")
                runBlocking {
                    connection.emit(SubscriptionEvent.Timeshift(60_000_000L, 0L, 0L, 60_000_000L, null, null))
                    // Establish the SDK's displayed-content mapping, not only its reader bounds.
                    connection.emit(SubscriptionEvent.Packet(
                        at.bernhardberger.tvheadend.sdk.playback.MuxFrameType.I, StreamIndex(1),
                        60_000_000L, 60_000_000L, 40_000L,
                        at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture(byteArrayOf(0, 0, 1, 9, 0x10)),
                    ))
                }
                settle()
                val known = runBlocking { runtime.sampleTimeshiftPresentation() }
                assertTrue("status supplies timing", known.timingKnown)
                assertTrue("real known timing has a target", known.playbackTarget != null)
                assertScreenProgramme(channel, "timing known")
                // The first status had no wall clock. A subsequent status gives the SDK the
                // mapping needed to resolve the committed programme window.
                runBlocking {
                    connection.emit(SubscriptionEvent.Timeshift(61_000_000L, 0L, 0L, 61_000_000L,
                        null, Instant.fromEpochMilliseconds(System.currentTimeMillis())))
                }
                compose.mainClock.advanceTimeBy(300)
                compose.waitForIdle()
                val mapped = runBlocking { runtime.sampleTimeshiftPresentation() }
                val window = programmeWindow(mapped) { time -> events.firstOrNull {
                    it.channelId == ChannelId(channel) && time >= it.start && time < it.stop
                } }
                assertEquals("committed window resolved", "Current $channel", window?.event?.title)
                assertScreenProgramme(channel, "window")
            }
        } finally {
            // Dispose request effects and release the media period before closing its manager,
            // including when an assertion interrupts a pending admission/subscription.
            try { closeScreen() } finally { runBlocking { manager.closeAndJoin() } }
        }
    }

    private fun assertUnknownProgramme() {
        val info = compose.onNodeWithTag("player-info-bar").fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ")
        assertTrue("unknown playback does not claim the airing programme: $info", info.contains("Program information unavailable"))
        assertFalse("unknown playback has no Next: $info", info.contains("Next 1") || info.contains("Next 2"))
    }

    private fun assertScreenProgramme(channel: Long, phase: String) {
        val info = compose.onNodeWithTag("player-info-bar").fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ")
        assertTrue("$phase: current programme: $info", info.contains("Current $channel"))
        assertTrue("$phase: next programme: $info", info.contains("Next $channel"))
        assertNotNull("$phase: the live-start state is not unknown", liveState())
        val track = compose.onNodeWithTag("player-timeline-track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val fills = compose.onAllNodes(hasTestTag("player-timeline-fill") or hasTestTag("player-timeline-interactive-fill"),
            useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("$phase: fill never drops", fills.isNotEmpty())
        assertEquals("$phase: no axis slide", 0.75f, (fills.maxOf { it.boundsInRoot.right } - track.left) / track.width, 0.03f)
        val title = compose.onNodeWithTag("player-info-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        var brightest = 0
        for (x in title.left.toInt().coerceAtLeast(0) until title.right.toInt().coerceAtMost(bitmap.width))
            for (y in title.top.toInt().coerceAtLeast(0) until title.bottom.toInt().coerceAtMost(bitmap.height))
                brightest = maxOf(brightest, android.graphics.Color.red(bitmap.getPixel(x, y)))
        bitmap.recycle()
        assertTrue("$phase: title painted, not only in semantics", brightest > 128)
    }

    private fun description(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()

    /** The state live TV announces, live or behind live; null while unknown. */
    private fun liveState(): String? = compose.onNodeWithTag("player-live-state", useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    private fun texts(tag: String): String =
        compose.onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
            .joinToString(" | ")

    @Test fun playerInfoStartsOnRecordAndContainsFocusWithinProgrammeActionsAndReading() {
        screen()
        key(Key.Info)
        assertEquals("The first action takes focus", listOf("live-info-record"), focused())
        assertFalse(exists("details-schedule"))
        assertFalse(exists("live-info-close"))
        key(Key.DirectionLeft)
        assertEquals(listOf("live-info-record"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("live-info-record"), focused())
        repeat(3) { key(Key.DirectionDown) }
        assertEquals(listOf("details-more-info"), focused())
        key(Key.DirectionCenter)
        assertEquals(listOf("player-info-reading"), focused())
        for (index in 0..1) {
            compose.onNodeWithTag("details-tab-$index").performSemanticsAction(SemanticsActions.RequestFocus)
            settle()
            assertEquals("tabs cannot take focus while More info is open", listOf("player-info-reading"), focused())
        }
        key(Key.Back)
        assertEquals("Back returns to the action that opened reading", listOf("details-more-info"), focused())
        repeat(3) { key(Key.DirectionUp) }
        key(Key.DirectionUp)
        assertEquals("Up goes to the selected tab, not the nearest tab", listOf("details-tab-0"), focused())
        key(Key.DirectionLeft)
        assertEquals("tab edges cannot leave the layer", listOf("details-tab-0"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-record"), focused())
        key(Key.Back)
        assertFalse(exists("live-info-panel"))
    }

    @Test fun playerInfoStartsOnRecordingWhenAlreadyScheduled() {
        screen(recordingScheduled = true)
        key(Key.Info)
        assertEquals(listOf("live-info-record"), focused())
        assertEquals("Cancel recording", texts("live-info-record"))
        key(Key.DirectionDown)
        assertEquals(listOf("details-record-series"), focused())
        key(Key.Back)
        assertFalse(exists("live-info-panel"))
    }

    @Test fun playerInfoTabDownStartsAtTheFirstAction() {
        screen()
        key(Key.Info)
        repeat(2) { key(Key.DirectionDown) }
        assertEquals(listOf("details-other-airings"), focused())
        compose.onNodeWithTag("details-tab-0").performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-record"), focused())
    }

    @Test fun playerInfoOmitsMissingProgrammeTitleAndSubtitle() {
        screen()
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, listOf(EpgEvent.create(EventId(31), ChannelId(1),
            Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600)))))
        settle()
        key(Key.Info)
        assertEquals(listOf("live-info-record"), focused())
        assertFalse(exists("details-title"))
        assertFalse(exists("details-tab-1"))
    }

    @Test fun playerInfoConsumesItsOpeningDownAndRestoresTheControlThatOpenedIt() {
        screen()
        key(Key.DirectionDown)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-record"), focused())
        assertFalse(exists("details-schedule"))
        val playing = player.playWhenReady
        listOf(Key.ChannelUp, Key.ChannelDown, Key.MediaStop, Key.MediaPause, Key.Menu, Key.Info).forEach(::key)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(playing, player.playWhenReady)
        assertEquals(listOf("live-info-record"), focused())
        key(Key.Back)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionDown)
        assertEquals("a new visit resets Details and its action", listOf("live-info-record"), focused())
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun detailsScheduleScrollsSmoothlyAndRestoresViewport() = scheduleScroll(false)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun railScheduleScrollsSmoothlyAndRestoresViewport() = scheduleScroll(true)

    private fun scheduleScroll(rail: Boolean) {
        screen()
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, (0..11).map {
            EpgEvent.create(EventId(11L + it), ChannelId(1), Instant.fromEpochSeconds(now - 600 + it * 1800),
                Instant.fromEpochSeconds(now + 1200 + it * 1800), title = "Programme $it", summary = "Summary")
        }))
        settle()
        if (rail) { openInfoRail(); key(Key.DirectionDown) }
        else { key(Key.Info); key(Key.DirectionUp); key(Key.DirectionRight); key(Key.DirectionDown) }
        var node = compose.onNodeWithTag("details-schedule", useUnmergedTree = true).fetchSemanticsNode()
        val list = node.layoutInfo.getModifierInfo()
            .mapNotNull { it.modifier as? androidx.compose.ui.platform.InspectableValue }
            .flatMap { it.inspectableElements.toList() }.map { it.value }
            .filterIsInstance<androidx.compose.foundation.lazy.LazyListState>().single()
        assertEquals("list is bounded by the visible viewport", node.boundsInRoot.height, list.layoutInfo.viewportSize.height.toFloat(), 1f)
        assertTrue("both paths have more rows to scroll", list.canScrollForward)
        assertPageTopBand()
        val thumbnail = compose.onNodeWithTag("details-schedule-11", useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        val thumbnailLeft = thumbnail.localToRoot(androidx.compose.ui.geometry.Offset(thumbnail.size.width / 2f, 0f)).x - thumbnail.size.width / 2f
        assertEquals("only details-tab content is inset", if (rail) 58f else 130f, thumbnailLeft, 0.5f)
        val thumbnailTop = thumbnail.localToRoot(androidx.compose.ui.geometry.Offset(0f, thumbnail.size.height / 2f)).y - thumbnail.size.height / 2f
        assertEquals("Now keeps trial.49's start position", 168f, thumbnailTop, 0.5f)
        assertEquals("peek fractions use the reference canvas", 960, view.width)
        assertEquals("peek fractions use the reference canvas", 540, view.height)
        assertEquals("row gap is fixed", 13, list.layoutInfo.mainAxisItemSpacing)
        assertEquals("viewport bleeds to the screen bottom", 540f, node.boundsInRoot.bottom, 0.5f)
        val heading = compose.onNodeWithTag(if (rail) "details-heading" else "details-tabs", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("peek stays below the headline/tabs", node.boundsInRoot.top >= heading.boundsInRoot.bottom)
        println("SCHEDULE rail=$rail viewport=${list.layoutInfo.viewportSize.height}")
        val failures = mutableListOf<String>()
        fun assertPeeks() {
            val viewport = node.boundsInRoot
            val cards = list.layoutInfo.visibleItemsInfo.map { item ->
                val coordinates = compose.onNodeWithTag("details-schedule-${11 + item.index}", useUnmergedTree = true)
                    .fetchSemanticsNode().layoutInfo.coordinates
                val top = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
                val bottom = coordinates.localToRoot(androidx.compose.ui.geometry.Offset(0f, coordinates.size.height.toFloat())).y
                top to bottom
            }
            fun fraction(top: Boolean): Float {
                val edge = if (top) viewport.top else viewport.bottom
                val card = cards.singleOrNull { (a, b) -> a < edge && b > edge } ?: return 0f
                return (if (top) card.second - edge else edge - card.first) / (card.second - card.first)
            }
            if (list.canScrollBackward && fraction(true) !in 0.3f..0.55f)
                failures += "top peek rail=$rail fraction=${fraction(true)} viewport=$viewport cards=$cards"
            if (list.canScrollForward && fraction(false) !in 0.3f..0.55f)
                failures += "bottom peek rail=$rail fraction=${fraction(false)} viewport=$viewport cards=$cards"
        }
        assertPeeks()
        assertFalse("Now has no previous row", list.canScrollBackward)
        fun scrollPosition(): Int = list.firstVisibleItemIndex *
            (list.layoutInfo.visibleItemsInfo.first().size + list.layoutInfo.mainAxisItemSpacing) + list.firstVisibleItemScrollOffset
        fun step(down: Boolean) {
            val values = mutableListOf(scrollPosition())
            compose.onRoot().performKeyInput { pressKey(if (down) Key.DirectionDown else Key.DirectionUp) }
            repeat(40) {
                compose.mainClock.advanceTimeBy(16)
                compose.waitForIdle()
                values += scrollPosition()
                val focusedRow = compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes().single()
                val coordinates = focusedRow.layoutInfo.coordinates
                val top = coordinates.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
                val bottom = coordinates.localToRoot(androidx.compose.ui.geometry.Offset(0f, coordinates.size.height.toFloat())).y
                if (top < node.boundsInRoot.top - 1 || bottom > node.boundsInRoot.bottom + 1)
                    failures += "focused row clipped rail=$rail down=$down top=$top bottom=$bottom viewport=${node.boundsInRoot}"
                if ((list.canScrollBackward && top < node.boundsInRoot.top + 24f) ||
                    (list.canScrollForward && bottom > node.boundsInRoot.bottom - 32f))
                    failures += "focused row enters fade rail=$rail down=$down top=$top bottom=$bottom viewport=${node.boundsInRoot}"
            }
            println("SCHEDULE rail=$rail down=$down offsets=$values")
            if (values.zipWithNext().any { (a, b) -> if (down) b < a else b > a }) failures += "wrong direction: $values"
            if (values.last() != values.first() && values.distinct().size <= 2) failures += "single-frame jump: $values"
        }
        repeat(3) { step(down = true) }
        assertEquals(listOf("details-schedule-14"), focused())
        assertTrue(list.canScrollBackward && list.canScrollForward)
        assertPeeks()
        val precedingRow = compose.onNodeWithTag("details-schedule-13", useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        assertTrue("one complete row above focus stays below the top fade",
            precedingRow.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y >= node.boundsInRoot.top + 24f)
        val position = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        val fade = node.layoutInfo.getModifierInfo().first {
            (it.modifier as? androidx.compose.ui.platform.InspectableValue)?.nameFallback == "drawWithContent"
        }.modifier
        // Replay the production edge mask over a solid colour; video/artwork cannot hide a missing fade.
        compose.runOnIdle {
            scrimUnderTest.value = Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .then(fade).drawBehind { drawRect(Color.Red) }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(bitmap)) }
        println("FADE rail=$rail back=${list.canScrollBackward} forward=${list.canScrollForward} pixels=" + listOf(2, 16, 32, view.height / 2, view.height - 3).map { bitmap.getPixel(400, it).toUInt().toString(16) })
        assertTrue("top edge mask fades", android.graphics.Color.green(bitmap.getPixel(400, 2)) > 200)
        assertTrue("bottom edge mask fades", android.graphics.Color.green(bitmap.getPixel(400, view.height - 3)) > 200)
        assertEquals("centre stays opaque", 0, android.graphics.Color.green(bitmap.getPixel(400, view.height / 2)))
        bitmap.recycle()
        compose.runOnIdle { scrimUnderTest.value = null }
        key(Key.DirectionCenter)
        key(Key.Back)
        assertEquals(listOf("details-schedule-14"), focused())
        node = compose.onNodeWithTag("details-schedule", useUnmergedTree = true).fetchSemanticsNode()
        val restored = node.layoutInfo.getModifierInfo().mapNotNull { it.modifier as? androidx.compose.ui.platform.InspectableValue }
            .flatMap { it.inspectableElements.toList() }.map { it.value }
            .filterIsInstance<androidx.compose.foundation.lazy.LazyListState>().single()
        assertTrue("the same viewport survives opening details", list === restored)
        assertEquals("Back preserves index and pixel offset", position, restored.firstVisibleItemIndex to restored.firstVisibleItemScrollOffset)
        repeat(3) { step(down = false) }
        assertEquals(listOf("details-schedule-11"), focused())
        assertFalse("return to Now removes the top fade", list.canScrollBackward)
        assertPeeks()
        repeat(2) { key(Key.DirectionDown) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        assertEquals("interrupt the fourth row's scroll", listOf("details-schedule-14"), focused())
        key(Key.DirectionCenter)
        assertEquals("the interrupted row opens details", listOf("details-record"), focused())
        assertEquals("opening a programme does not also activate Record", 0,
            compose.onAllNodes(isDialog()).fetchSemanticsNodes().size)
        val interruptedPosition = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        key(Key.Back)
        assertEquals("Back does not restart an interrupted focus scroll", interruptedPosition,
            list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
        assertEquals("Back restores the interrupted row", listOf("details-schedule-14"), focused())
        repeat(8) { key(Key.DirectionDown) }
        assertEquals(listOf("details-schedule-22"), focused())
        assertFalse("last row has no following peek or fade", list.canScrollForward)
        node = compose.onNodeWithTag("details-schedule", useUnmergedTree = true).fetchSemanticsNode()
        assertPeeks()
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun playerInfoScheduleOpensAProgrammeAndBackRestoresTheRowThenDetails() {
        screen()
        key(Key.Info)
        assertTrue("both known programs expose Schedule", exists("details-tab-1"))
        key(Key.DirectionUp)
        assertEquals(listOf("details-tab-0"), focused())
        key(Key.DirectionRight)
        assertEquals("changing tabs keeps focus on the tab", listOf("details-tab-1"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("details-tab-1"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("details-schedule-11"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("details-tab-1"), focused())
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        assertEquals(listOf("details-schedule-12"), focused())
        key(Key.DirectionCenter)
        assertEquals(listOf("details-record"), focused())
        assertFalse("a schedule selection pushes details without tabs", exists("details-tab-0"))
        assertFalse(exists("details-tab-1"))
        assertTrue(texts("details-title").contains("Later"))
        repeat(4) { key(Key.DirectionDown) }
        assertEquals(listOf("details-more-info"), focused())
        key(Key.DirectionCenter)
        key(Key.Back)
        assertEquals(listOf("details-more-info"), focused())
        key(Key.Back)
        assertEquals(listOf("details-schedule-12"), focused())
        key(Key.Back)
        assertEquals("Back from Schedule returns to Details", listOf("live-info-record"), focused())
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        assertEquals("tab Down starts at Now; opened programme Back still restores its row", listOf("details-schedule-11"), focused())
        key(Key.Back)
        key(Key.DirectionUp)
        key(Key.Back)
        assertEquals("Back from the first tab returns to its first action", listOf("live-info-record"), focused())
        key(Key.Back)
        assertFalse(exists("live-info-panel"))
    }

    @Test fun playerInfoConfirmsCurrentRecordingThenOffersStopFromObservedState() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        key(Key.DirectionCenter)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        compose.onNodeWithText("Back").assertIsFocused()
        confirmInfoAction()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        publishInfoRecording(DvrEntryState.RECORDING)
        assertEquals("Stop recording", texts("live-info-record"))
        assertEquals(listOf("live-info-record"), focused())
        assertTrue("command success never posts a notice", notices.state.value.pending.isEmpty())
        assertFalse("the shared queue replaces the local notice", exists("programme-recording-notice"))
        session.dvrRepository.scriptStopEntry(DvrMutationResult.Confirmed(Unit))
        key(Key.DirectionCenter)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        confirmInfoAction()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        assertEquals(listOf("live-info-record"), focused())
    }

    @Test fun playerInfoConfirmationBackIsSafeAndDoesNotCloseDetails() {
        screen()
        key(Key.Info)
        key(Key.DirectionCenter)
        compose.onNodeWithText("Back").assertIsFocused()
        compose.onNode(isDialog() and !hasTestTag("live-info-panel")).performKeyInput { pressKey(Key.Back) }
        settle()
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertTrue(exists("live-info-panel"))
        assertEquals(listOf("live-info-record"), focused())
        assertTrue(notices.state.value.pending.isEmpty())
    }

    @Test fun playerInfoRecordIgnoresASecondPressBeforeRecomposition() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        val click = compose.onNodeWithTag("live-info-record").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { click(); click() }
        settle()
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        confirmInfoAction()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(listOf("live-info-record"), focused())
    }

    @Test fun playerInfoConfirmsFutureRecordingThenCanCancelTheSchedule() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(8)))
        key(Key.Info)
        key(Key.DirectionUp)
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        key(Key.DirectionCenter)
        confirmInfoAction()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        publishInfoRecording(DvrEntryState.SCHEDULED, EventId(12))
        assertEquals("Cancel recording", texts("details-record"))
        assertEquals(listOf("details-record"), focused())
        session.dvrRepository.scriptCancelEntry(DvrMutationResult.Confirmed(Unit))
        key(Key.DirectionCenter)
        confirmInfoAction()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        key(Key.Back)
        assertEquals(listOf("details-schedule-12"), focused())
    }

    @Test fun playerInfoRecordingFailureKeepsRecordAndShowsANonFocusableNotice() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.AccessDenied)
        key(Key.Info)
        key(Key.DirectionCenter)
        confirmInfoAction()
        assertEquals("Record", texts("live-info-record"))
        assertEquals(listOf("live-info-record"), focused())
        val notice = notices.state.value.pending.single().notice
        assertEquals(NoticeSeverity.FAILURE, notice.severity)
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED), notice)
        assertEquals("dvr-action", notice.key)
        assertFalse(exists("programme-recording-notice"))
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
    }

    @Test fun playerInfoUpOnEitherTabClosesAndRestoresTheInvokingControl() {
        screen()
        key(Key.DirectionDown)
        for (tab in 0..1) {
            assertEquals(listOf("player-pause"), focused())
            key(Key.DirectionDown)
            assertTrue("the shared pill is always visible", exists("details-player-hint"))
            key(Key.DirectionUp)
            if (tab == 1) key(Key.DirectionRight)
            assertTrue(exists("details-player-hint"))
            key(Key.DirectionUp)
            assertFalse(exists("live-info-panel"))
            assertEquals(listOf("player-pause"), focused())
        }
    }

    @Test fun playerInfoMoreInfoKeepsIdentityAndAddsUntruncatedMetadata() {
        screen()
        val now = System.currentTimeMillis() / 1_000L
        val description = "A full description with its own ending. ".repeat(24)
        session.publish(observation(now, listOf(EpgEvent.create(EventId(11), ChannelId(1),
            Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "Now showing",
            description = description, summary = "A separate summary", genre = "Drama",
            categories = listOf("Documentary"), keywords = listOf("Mountains"), copyrightYear = 2024,
            episode = at.bernhardberger.tvheadend.sdk.core.EpgEpisode(null, null, 2, null, 3, 6, null, null, null)))))
        settle()
        key(Key.Info)
        repeat(3) { key(Key.DirectionDown) }
        key(Key.DirectionCenter)
        assertEquals(listOf("player-info-reading"), focused())
        assertTrue(texts("details-title").contains("Now showing"))
        assertTrue("facts use one separator and non-breaking episodes", texts("details-facts").contains("Drama · S2\u00a0E3/6"))
        assertFalse(texts("details-facts").contains("•"))
        val text = texts("details-full-description")
        listOf(description.trim(), "Summary", "A separate summary", "Categories", "Documentary", "Keywords", "Mountains", "Copyright year", "2024")
            .forEach { assertTrue("reading includes $it", text.contains(it)) }
        assertFalse("the visual heading is not repeated inside the reader", text.contains("More info"))
        assertEquals("More info", compose.onNodeWithTag("player-info-reading").fetchSemanticsNode()
            .config[SemanticsProperties.PaneTitle])
        key(Key.DirectionUp)
        key(Key.DirectionLeft)
        key(Key.DirectionRight)
        assertEquals("reading boundaries contain focus", listOf("player-info-reading"), focused())
        repeat(8) { key(Key.DirectionDown) }
        assertEquals(listOf("player-info-reading"), focused())
        key(Key.Back)
        assertEquals(listOf("details-more-info"), focused())
    }

    @Test fun channelCardRailAnimatesOpenAndClosedWithoutReplacingControls() {
        screen()
        key(Key.DirectionUp)
        assertEquals(listOf(PlayerIdentityCardTag), focused())
        val footer = compose.onNodeWithTag("player-footer").fetchSemanticsNode()
        assertEquals(1f, pageAlpha(footer), 0.001f)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput {
            keyDown(Key.DirectionRight)
            advanceEventTime(600)
            keyUp(Key.DirectionRight)
        }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val openingFooter = compose.onNodeWithTag("player-footer", useUnmergedTree = true).fetchSemanticsNode()
        val openingAlpha = pageAlpha(openingFooter)
        assertTrue("rail is partway open, footer alpha=$openingAlpha", openingAlpha > 0f && openingAlpha < 1f)
        assertEquals("opening preserves the controls composition", footer.id, openingFooter.id)
        assertFalse("the schedule peek is not composed during the card reveal", exists("details-heading"))
        settle()
        assertEquals(listOf("player-channel-card-2"), focused())
        assertEquals(0f, pageAlpha(openingFooter), 0.001f)
        assertTrue(exists("details-heading"))
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(96)
        compose.waitForIdle()
        val closingFooter = compose.onNodeWithTag("player-footer", useUnmergedTree = true).fetchSemanticsNode()
        val closingAlpha = pageAlpha(closingFooter)
        assertEquals("the peek leaves before the card collapses", 0f, closingAlpha, 0.001f)
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertFalse("the peek is gone during collapse", exists("details-heading"))
        assertTrue("rail then collapses", pageAlpha(closingFooter) > 0f)
        assertEquals("closing preserves the controls composition", footer.id, closingFooter.id)
        settle()
        assertEquals(1f, pageAlpha(closingFooter), 0.001f)
        assertEquals(listOf(PlayerIdentityCardTag), focused())
        compose.mainClock.autoAdvance = true
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun playerInfoRailDownOpensFocusedChannelsScheduleAndBackRestoresTheRail() {
        screen()
        publishRailProgrammes()
        openInfoRail()
        assertFalse(exists("player-rail-key-hints"))
        assertTrue(texts("details-heading").contains("Name 1"))
        key(Key.DirectionRight)
        assertTrue(texts("details-heading").contains("Name 2"))
        val header = compose.onNodeWithTag("details-heading", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(0.55f, pageAlpha(header), 0.001f)
        assertEquals(0f, pageAlpha(compose.onNodeWithTag("details-player-hint", useUnmergedTree = true).fetchSemanticsNode()), 0f)
        val railCard = compose.onNodeWithTag("player-channel-card-1", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("peek uses the rail's first-column keyline", railCard.layoutInfo.coordinates.positionInRoot().x,
            header.layoutInfo.coordinates.positionInRoot().x, 1f)
        val farCard = compose.onNodeWithTag("player-channel-card-3", useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        assertEquals(listOf("player-channel-card-2"), focused())
        key(Key.DirectionDown)
        assertEquals("the peek is the schedule header, not a replacement", header.id,
            compose.onNodeWithTag("details-heading", useUnmergedTree = true).fetchSemanticsNode().id)
        assertTrue("the rail leaves fully above the viewport",
            farCard.positionInRoot().y + farCard.size.height <= 0f)
        assertEquals(listOf("details-schedule-21"), focused())
        assertEquals("schedule headline stays on the rail keyline", railCard.layoutInfo.coordinates.positionInRoot().x,
            header.layoutInfo.coordinates.positionInRoot().x, 1f)
        val thumbnail = compose.onNodeWithTag("details-schedule-21", useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        // Focus scales the first thumbnail around its centre; compare the underlying layout keyline.
        val thumbnailLeft = thumbnail.localToRoot(androidx.compose.ui.geometry.Offset(thumbnail.size.width / 2f, 0f)).x - thumbnail.size.width / 2f
        assertEquals("schedule thumbnails share the headline keyline", header.layoutInfo.coordinates.positionInRoot().x,
            thumbnailLeft, 1f)
        assertFalse(exists("details-tab-0"))
        assertFalse(exists("details-tab-1"))
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        repeat(8) { settle() }
        assertTrue("the layer cannot time out with the rail", exists("live-info-panel"))
        key(Key.DirectionCenter)
        assertEquals(listOf("details-watch"), focused())
        key(Key.Back)
        assertEquals(listOf("details-schedule-21"), focused())
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(300)
        assertTrue("the same rail cards remain composed", farCard.isAttached)
        val position = farCard.positionInRoot()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        var brightest = 0
        for (x in position.x.toInt().coerceAtLeast(0) until (position.x + farCard.size.width).toInt().coerceAtMost(bitmap.width))
            for (y in position.y.toInt().coerceAtLeast(0) until (position.y + farCard.size.height).toInt().coerceAtMost(bitmap.height))
                brightest = maxOf(brightest, android.graphics.Color.red(bitmap.getPixel(x, y)))
        bitmap.recycle()
        assertTrue("return paints the far card without replaying the sideways reveal: $brightest", brightest > 180)
        settle()
        compose.mainClock.autoAdvance = true
        assertFalse(exists("live-info-panel"))
        assertEquals(listOf("player-channel-card-2"), focused())
    }

    @Test fun programInfoMovesElementsNotSectionsThenRestoresFocusAndDisposesDetails() {
        screen()
        key(Key.DirectionDown)
        val controls = compose.onNodeWithTag("player-pause", useUnmergedTree = true).fetchSemanticsNode().layoutInfo
        val first = compose.onNodeWithTag("player-page-first").fetchSemanticsNode().layoutInfo.coordinates
        val scrim = compose.onNodeWithTag("player-page-scrim").fetchSemanticsNode().layoutInfo.coordinates
        val footerNode = compose.onNodeWithTag("player-footer").fetchSemanticsNode()
        val footer = footerNode.layoutInfo.coordinates
        val footerTop = footer.positionInRoot().y
        val clock = compose.onNodeWithTag("player-top-cluster", useUnmergedTree = true).fetchSemanticsNode()
        val clockTop = clock.boundsInRoot.top
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(250)
        val second = compose.onNodeWithTag("player-page-second").fetchSemanticsNode().layoutInfo.coordinates
        val tabs = compose.onNodeWithTag("details-top-row", useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        assertTrue(pageAlpha(footerNode) < 1f)
        assertTrue(pageAlpha(compose.onNodeWithTag("details-top-row", useUnmergedTree = true).fetchSemanticsNode()) > 0f)
        val strip = compose.onNodeWithTag("player-now-playing", useUnmergedTree = true).fetchSemanticsNode()
        val stripMotion = compose.onNodeWithTag("player-now-playing-motion", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("controls-to-details fades the strip in", pageAlpha(stripMotion) > 0f && pageAlpha(stripMotion) < 1f)
        val stripBounds = strip.boundsInRoot
        val midTabsTop = tabs.positionInRoot().y
        assertTrue("footer rises instead of pulling the viewport", footer.positionInRoot().y < footerTop)
        assertEquals(0f, first.positionInRoot().y, 0f)
        assertEquals(0f, second.positionInRoot().y, 0f)
        assertEquals(clockTop, clock.boundsInRoot.top, 0f)
        assertEquals(0f, scrim.positionInRoot().y, 0f)
        assertTrue("neither moving section exposes an action", focused().none { it == "player-pause" || it == "live-info-record" })
        settle()
        assertTrue("tabs enter from below their rest", midTabsTop > tabs.positionInRoot().y)
        assertTrue("clock ${clock.boundsInRoot} clears the details row at ${tabs.positionInRoot().y}",
            tabs.positionInRoot().y - clock.boundsInRoot.bottom >= 8f)
        assertEquals(listOf("live-info-record"), focused())
        assertTrue("the controls stay composed behind the details", controls.isAttached)
        assertPageTopBand()
        assertEquals("the strip fades without travelling", stripBounds, strip.boundsInRoot)
        assertEquals(58f, tabs.positionInRoot().x, 0.5f)
        assertEquals(130f, compose.onNodeWithTag("details-information", useUnmergedTree = true)
            .fetchSemanticsNode().layoutInfo.coordinates.positionInRoot().x, 0.5f)
        assertEquals(1f, pageAlpha(compose.onNodeWithTag("player-now-playing-motion", useUnmergedTree = true)
            .fetchSemanticsNode()), 0.001f)
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.mainClock.advanceTimeBy(120)
        assertTrue(second.isAttached)
        assertEquals(0f, second.positionInRoot().y, 0f)
        assertEquals(0f, scrim.positionInRoot().y, 0f)
        settle()
        assertFalse(second.isAttached)
        assertEquals(listOf("player-pause"), focused())
        compose.mainClock.autoAdvance = true
        assertTrue("the return reuses the same controls", controls.isAttached)
        assertFalse("controls rest has no now-playing strip", exists("player-now-playing"))
        assertTrue(exists("player-actions"))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun controlsPageScrimFadesMonotonicallyDownAndBack() = pageScrimSeries(rail = false)

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun railPageScrimFadesMonotonicallyDownAndBack() = pageScrimSeries(rail = true)

    private val scrimUnderTest = mutableStateOf<Modifier?>(null)

    private fun pageScrimSeries(rail: Boolean) {
        screen()
        if (rail) {
            publishRailProgrammes()
            openInfoRail()
        } else key(Key.DirectionDown)
        compose.mainClock.autoAdvance = false
        // Mirror the actual viewport draw modifier over white, without chrome/content occluding
        // the sample points. It reads the production screen's live scrim inputs and page clock.
        val drawModifier = compose.onNodeWithTag("player-page-scrim").fetchSemanticsNode()
            .layoutInfo.getModifierInfo().single {
                (it.modifier as? androidx.compose.ui.platform.InspectableValue)?.nameFallback == "drawWithCache"
            }.modifier
        compose.runOnIdle {
            scrimUnderTest.value = drawModifier
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        fun sample(): List<Float> {
            compose.waitForIdle()
            compose.runOnIdle { view.draw(Canvas(bitmap)) }
            return listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).map {
                1f - android.graphics.Color.red(bitmap.getPixel(bitmap.width / 2, (bitmap.height * it).toInt())) / 255f
            }
        }
        val failures = mutableListOf<String>()
        for (down in listOf(true, false)) {
            val frames = mutableListOf(sample())
            compose.onRoot().performKeyInput { pressKey(if (down) Key.DirectionDown else Key.Back) }
            repeat(36) {
                compose.mainClock.advanceTimeBy(16)
                frames += sample()
            }
            println("SCRIM rail=$rail down=$down " + frames.filterIndexed { index, _ -> index % 2 == 0 }
                .mapIndexed { index, values -> "${index * 32}:" + values.joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it) } }.joinToString(";"))
            assertTrue("the sampled scrim actually changes between the two rests",
                if (down) frames.last()[1] > frames.first()[1] + 0.1f else frames.last()[1] < frames.first()[1] - 0.1f)
            frames.zipWithNext().forEachIndexed { index, (from, to) ->
                from.indices.forEach { y ->
                    if (if (down) to[y] < from[y] - 1.01f / 255 else to[y] > from[y] + 1.01f / 255)
                        failures += "rail=$rail down=$down at ${(index + 1) * 16}ms y=${y * 20 + 10}%: ${from[y]} -> ${to[y]}"
                }
            }
        }
        bitmap.recycle()
        compose.mainClock.autoAdvance = true
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Read the actual layer via Compose's inspector, without a production test seam. */
    private fun pageAlpha(node: androidx.compose.ui.semantics.SemanticsNode): Float =
        node.layoutInfo.getModifierInfo().mapNotNull { it.modifier as? androidx.compose.ui.platform.InspectableValue }
            .filter { it.nameFallback == "graphicsLayer" }.fold(1f) { alpha, layer ->
                @Suppress("UNCHECKED_CAST")
                val block = layer.inspectableElements.first { it.name == "block" }.value as androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit
                alpha * androidx.compose.ui.graphics.GraphicsLayerScope().apply(block).alpha
            }

    /** [key] returns from the details: they stay composed until the transition settles. */
    private fun stepBackFromDetails(key: Key) {
        val details = compose.onNodeWithTag("live-info-panel").fetchSemanticsNode().layoutInfo
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(220)
        assertTrue("the details are still stepping out", details.isAttached)
        settle()
        assertFalse("the details leave once the step ends", details.isAttached)
        compose.mainClock.autoAdvance = true
    }

    @Test fun playerInfoRailKeepsWatchHiddenForThePlayingChannel() {
        screen()
        openInfoRail()
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertFalse(exists("details-watch"))
        assertEquals(listOf("live-info-record"), focused())
        key(Key.Back)
        assertEquals(listOf("details-schedule-11"), focused())
    }

    @Test fun playerInfoRailScheduleUpRestoresTheFocusedChannel() {
        screen()
        publishRailProgrammes()
        openInfoRail()
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        key(Key.DirectionUp)
        assertFalse(exists("live-info-panel"))
        assertEquals(listOf("player-channel-card-2"), focused())
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
    }

    @Test fun railScheduleHeadlineNeverOvertakesTheTilesDownOrBack() {
        screen()
        publishRailProgrammes()
        openInfoRail()
        key(Key.DirectionRight)
        assertTrue("strip names the watched channel, not the browsed one", texts("player-now-playing").contains("Name 1"))
        assertFalse(texts("player-now-playing").contains("Name 2"))
        val header = compose.onNodeWithTag("details-heading", useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        val tile = compose.onNodeWithTag("player-channel-card-1", useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
        val strip = compose.onNodeWithTag("player-now-playing", useUnmergedTree = true).fetchSemanticsNode()
        val stripMotion = compose.onNodeWithTag("player-now-playing-motion", useUnmergedTree = true).fetchSemanticsNode()
        val stripBounds = strip.boundsInRoot
        compose.mainClock.autoAdvance = false
        for (key in listOf(Key.DirectionDown, Key.Back)) {
            compose.onRoot().performKeyInput { pressKey(key) }
            repeat(40) { frame ->
                compose.mainClock.advanceTimeBy(16)
                compose.waitForIdle()
                val headingTop = header.localToRoot(androidx.compose.ui.geometry.Offset.Zero).y
                val tileBottom = tile.localToRoot(androidx.compose.ui.geometry.Offset(0f, tile.size.height.toFloat())).y
                assertTrue("$key at ${frame * 16}ms: headline $headingTop overtakes tile $tileBottom", headingTop >= tileBottom)
                assertEquals("strip position stays pinned", stripBounds, strip.boundsInRoot)
                assertEquals("strip never fades between rail and schedule", 1f, pageAlpha(stripMotion), 0.001f)
                assertPageTopBand()
            }
        }
    }

    private fun assertPageTopBand() {
        val strip = compose.onNodeWithTag("player-now-playing", useUnmergedTree = true).fetchSemanticsNode()
        val hint = compose.onNodeWithTag("details-player-hint", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val clock = compose.onNodeWithTag("player-top-cluster", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(58f, strip.boundsInRoot.left, 0.5f)
        assertEquals(clock.center.y, strip.boundsInRoot.center.y, 0.5f)
        assertEquals(clock.center.y, hint.center.y, 0.5f)
        assertEquals(480f, hint.center.x, 0.5f)
        assertTrue("strip clears the centered affordance", strip.boundsInRoot.right < hint.left)
        assertTrue("affordance clears the clock", hint.right < clock.left)
        assertFalse(strip.config.contains(SemanticsProperties.Focused))
    }

    @Test fun playerInfoWatchTunesTheRailChannelAndClosesTheLayers() {
        screen()
        publishRailProgrammes()
        openInfoRail()
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        key(Key.DirectionCenter)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(exists("live-info-panel"))
        assertFalse(exists("player-channel-shelf"))
    }

    @Test fun playerInfoRailWithoutEpgOffersWatchAndReturnsToTheSameChannel() {
        screen()
        openInfoRail()
        key(Key.DirectionRight)
        key(Key.DirectionDown)
        assertEquals(listOf("details-watch"), focused())
        assertTrue(texts("live-info-panel").contains("Name 2"))
        assertEquals("Channels", texts("details-player-hint"))
        key(Key.DirectionUp)
        assertEquals(listOf("player-info-reading"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-channel-card-2"), focused())
        key(Key.DirectionDown)
        key(Key.Back)
        assertEquals(listOf("player-channel-card-2"), focused())
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(exists("live-info-panel"))
    }

    private fun openInfoRail() {
        key(Key.DirectionDown)
        compose.onNodeWithTag(PlayerIdentityCardTag).performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        key(Key.DirectionCenter)
        assertEquals(listOf("player-channel-card-1"), focused())
    }

    private fun confirmInfoAction() {
        compose.onNodeWithText("Back").assertIsFocused()
        compose.onNode(isDialog() and !hasTestTag("live-info-panel")).performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        settle()
    }

    private fun publishInfoRecording(state: DvrEntryState, eventId: EventId = EventId(11)) {
        val current = session.observation.value.epgState as EpgRepositoryState.Current
        session.publish(observation(System.currentTimeMillis() / 1_000, current.snapshot.events,
            recordings = listOf(DvrEntry.create(DvrEntryId(7), eventId = eventId, state = state))))
        settle()
    }

    private fun publishRailProgrammes() {
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, listOf(
            EpgEvent.create(EventId(21), ChannelId(2), Instant.fromEpochSeconds(now - 600),
                Instant.fromEpochSeconds(now + 600), title = "Second channel now"),
            EpgEvent.create(EventId(22), ChannelId(2), Instant.fromEpochSeconds(now + 600),
                Instant.fromEpochSeconds(now + 1800), title = "Second channel later"),
        )))
        settle()
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun badgeDescription(tag: String): String =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            .joinToString(" | ")

    @Test fun okOnTheBannerPausesAndKeepsTheBannerWithoutTheControlsUntilResumed() {
        // Android TV playback guidance (TV-PC): Center pauses and resumes; it shows progress, not the actions.
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        try {
            screen(opener = manager)
            startLiveSubscription(connection, height = 1080L, signal = 53739L, timeshift = true)
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            playerListeners.toList().forEach { it.onRenderedFirstFrame() }
            compose.waitUntil(5_000) { runtime.videoPresentation.value.visible }
            compose.waitUntil(5_000) { idleMainLooper(); runtime.livePause.value.availability == LivePauseAvailability.READY }
            val pauses = mutableListOf<Boolean>()
            compose.runOnIdle {
                player.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { pauses += playWhenReady }
                })
            }
            assertTrue(exists("player-banner"))

            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(3_000) { idleMainLooper(); false in pauses }
            settle()
            assertTrue("pausing keeps the Banner", exists("player-banner"))
            assertFalse("pausing opens no actions", exists("player-actions"))
            // Well past the Banner's timeout: paused, it stays until Back or resume.
            repeat(3) { settle(); compose.mainClock.advanceTimeBy(4_000); compose.waitForIdle() }
            assertTrue("paused: the Banner stays", exists("player-banner"))

            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitUntil(3_000) { idleMainLooper(); pauses.lastOrNull() == true }
            assertFalse("resuming opens no actions either", exists("player-actions"))

        } finally {
            runBlocking { manager.closeAndJoin() }
        }
    }

    @Test fun channelUpBrowsesTheOpenRailInsteadOfTuning() {
        screen()
        openInfoRail()
        assertEquals(listOf("player-channel-card-1"), focused())
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue("the rail stays open", exists("player-channel-shelf"))
        assertEquals(listOf("player-channel-card-2"), focused())
    }

    @Test fun channelUpTunesWithTheRailClosed() {
        screen()
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    private fun playerReady() {
        forcedReady = true
        compose.runOnIdle {
            playerListeners.toList().forEach {
                it.onPlaybackStateChanged(Player.STATE_READY)
                it.onIsPlayingChanged(true)
            }
        }
        compose.waitForIdle()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun playerUnavailableShowsOnlyTheCentreMessage() = unavailable("en", 1f)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun playerUnavailableShowsOnlyTheCentreMessageAtLargeText() = unavailable("en", 1.3f)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun playerUnavailableShowsOnlyTheCentreMessageInGerman() = unavailable("de", 1f)
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "de-w960dp-h540dp-land-mdpi")
    fun playerUnavailableShowsOnlyTheCentreMessageInGermanAtLargeText() = unavailable("de", 1.3f)

    @Test fun playerUnavailableClaimsNeitherPlayingNorLiveInTheControlsNorTheChip() {
        screen(failing = true)
        repeat(4) { settle() }
        assertTrue("centre message", exists("player-channel-unavailable"))
        assertFalse("unavailable replaces busy", exists("player-busy-indicator"))
        key(Key.DirectionDown)
        assertTrue("the controls open over the message", exists("player-actions"))
        assertFalse(exists("player-state"))
        assertFalse(exists("player-live-state"))
        key(Key.Back)
        assertFalse(exists("player-hidden-slot"))
        assertFalse(exists("player-state"))
    }

    private fun unavailable(locale: String, fontScale: Float) {
        screen(failing = true, fontScale = fontScale)
        repeat(4) { settle() }
        assertTrue("centre message", exists("player-channel-unavailable"))
        assertFalse("unavailable replaces busy", exists("player-busy-indicator"))
        assertFalse("the Banner gives way to the centre message", exists("player-banner"))
        assertFalse(exists("player-hidden-slot"))
        assertFalse(exists("player-status-slot"))
        // Evidence capture of the production screen, 960×540, into the ignored artifacts directory.
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(Canvas(bitmap)) }
        val directory = File("../artifacts/player-chrome").apply { mkdirs() }
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

    private fun observation(
        now: Long,
        events: List<EpgEvent>,
        recordings: List<DvrEntry> = emptyList(),
        missingRequestedChannel: Boolean = false,
    ) =
        SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                (if (missingRequestedChannel) 2..3L else 1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) },
            )),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = events)),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = recordings)),
        )

    private fun screen(
        onClose: () -> Unit = {},
        failing: Boolean = false,
        fontScale: Float = 1f,
        epg: Boolean = true,
        opener: SubscriptionOpener? = null,
        recordingScheduled: Boolean = false,
        settingsData: DataStore<Preferences> = InMemoryData(),
        settleOnEntry: Boolean = true,
        contentAllowed: () -> Boolean = { true },
        startup: Boolean = false,
        startupEnabled: () -> Boolean = { startup },
        startupRequestId: () -> Long = { 1L },
        missingRequestedChannel: Boolean = false,
        afterTargetPlay: () -> Unit = {},
        acceptStartupOutcome: () -> Boolean = { true },
        onStartupOutcome: (MainStartupPlaybackOutcome) -> Unit = {},
    ) {
        val now = System.currentTimeMillis() / 1_000L
        session = FakeTvheadendSession(observation(now, if (!epg) emptyList() else listOf(
            EpgEvent.create(EventId(11L), ChannelId(1L), Instant.fromEpochSeconds(now - 1_800L),
                Instant.fromEpochSeconds(now + 3_600L), title = "Now showing", summary = "Summary"),
            EpgEvent.create(EventId(12L), ChannelId(1L), Instant.fromEpochSeconds(now + 3_600L),
                Instant.fromEpochSeconds(now + 7_200L), title = "Later"),
        ), recordings = if (recordingScheduled) listOf(DvrEntry.create(DvrEntryId(1L), eventId = EventId(11L), state = DvrEntryState.SCHEDULED))
            else emptyList(), missingRequestedChannel = missingRequestedChannel)).apply {
            // The subscription never becomes playable: the channel stays tuning, as on a slow tune.
            // Failing: the scripted stream cannot be decoded, so the channel becomes unavailable.
            if (opener != null) scriptLivePlaybackSuccess(opener)
            else if (failing) scriptLivePlaybackSuccess()
            else scriptLivePlaybackSuccess(SubscriptionOpener { _, _, _ -> awaitCancellation() })
        }
        val settings = PlayerSettingsStore(settingsData)
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryData()))
        models.put("channels", channels)
        compose.waitUntil(10_000) { channels.channels.value.size == if (missingRequestedChannel) 2 else 3 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val base = player
        val controlled = object : ExoPlayer by base {
            override fun getPlaybackState(): Int = if (forcedReady) Player.STATE_READY else base.playbackState
            override fun isPlaying(): Boolean = forcedReady || base.isPlaying
            override fun getCurrentTracks(): Tracks = forcedTracks ?: base.currentTracks
            override fun getVideoFormat(): Format? = forcedVideoFormat ?: base.videoFormat
            override fun getAudioFormat(): Format? = forcedAudioFormat ?: base.audioFormat
            override fun play() {
                base.play()
                afterTargetPlay()
            }
            override fun addListener(listener: Player.Listener) {
                playerListeners += listener
                base.addListener(listener)
            }
            override fun removeListener(listener: Player.Listener) {
                playerListeners -= listener
                base.removeListener(listener)
            }
        }
        runtime = AppPlaybackRuntime(controlled, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None,
            PlaybackRuntimePolicy.fromPlayerSettings(object : PlaybackTrace {}))
        video = VideoPlayerViewModel(runtime, session)
        models.put("video", video)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        // setContent synchronizes the first composition. Waiting before a Compose root exists
        // instead incurs the test framework's two-second root-discovery wait on every entry.
        if (!settleOnEntry) compose.mainClock.autoAdvance = false
        compose.setContent {
            if (screenVisible.value) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides owner,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale),
                ) {
                    TVHeadendPlayerTheme {
                        view = LocalView.current
                        VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), settings,
                            channels, ImageLoader.Builder(context).build(), session, ChannelId(1), "Name 1", {}, onClose, runtime,
                            contentAllowed = contentAllowed(), notices = notices,
                            startupTarget = if (startupEnabled()) ApplianceLaunchTarget(ApplianceLaunchRequest(startupRequestId()), ChannelId(1), "Name 1") else null,
                            onStartupOutcome = { _, outcome -> onStartupOutcome(outcome); acceptStartupOutcome() })
                        scrimUnderTest.value?.let { Box(Modifier.fillMaxSize().background(Color.White).then(it)) }
                    }
                }
            }
        }
        if (settleOnEntry) {
            compose.waitUntil(10_000) { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
            settle()
            compose.mainClock.autoAdvance = false
            settle()
        }
    }

    private open class InMemoryData : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(state.value).also { state.value = it }
    }

    private class GatedPreferences : InMemoryData() {
        val open = MutableStateFlow(true)
        override val data: Flow<Preferences> = flow {
            open.first { it }
            emitAll(state)
        }
    }
}
