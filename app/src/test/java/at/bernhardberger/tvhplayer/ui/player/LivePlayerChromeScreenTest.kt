@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)

package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import at.bernhardberger.tvheadend.sdk.playback.TvheadendFailure
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
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
import at.bernhardberger.tvheadend.sdk.core.DvrConfigId
import at.bernhardberger.tvheadend.sdk.core.DvrConfiguration
import at.bernhardberger.tvheadend.sdk.core.DvrConfigurationsState
import at.bernhardberger.tvheadend.sdk.core.DvrRepository
import at.bernhardberger.tvheadend.sdk.core.DvrScheduleRequest
import at.bernhardberger.tvheadend.sdk.core.CurrentSessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvheadend.sdk.core.DvrSnapshot
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.core.EpgRepositoryState
import at.bernhardberger.tvheadend.sdk.core.EpgSnapshot
import at.bernhardberger.tvheadend.sdk.core.EventId
import at.bernhardberger.tvheadend.sdk.core.ServerCapabilities
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvheadend.sdk.media3.TvheadendAudioOutputProvider
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
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
import at.bernhardberger.tvhplayer.ui.components.ProvideWatchingProgress
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
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotEquals
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
    private val scheduleRequests = mutableListOf<DvrScheduleRequest>()
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

    @Test fun startupPlayerTunesBehindCoverWithoutFocusOrKeysAndShowsOnlyThePassiveBannerFromItsFrame() {
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
        settle()
        // The startup screen now fades over the player: the Banner is already there, passive.
        assertTrue("the Banner shows during the reveal", exists("player-banner"))
        assertEquals("nothing takes focus before the reveal ends", emptyList<String>(), focused())
        assertTrue("the player root is no focus target during the reveal", focusableNodes() == 0)
        assertTrue("the player root is out of the semantics tree during the reveal",
            compose.onAllNodes(hasContentDescription(liveTvSurface), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        key(Key.DirectionDown)
        assertFalse("no key opens the controls during the reveal", exists("player-actions"))
        assertEquals(emptyList<String>(), focused())
        compose.runOnIdle { allowed.value = true }
        settle()
        assertTrue("the player root takes focus after the reveal", focusableNodes() > 0)
        key(Key.DirectionDown)
        assertTrue("controls become reachable after reveal", exists("player-actions"))
    }

    private val liveTvSurface get() = context.getString(at.bernhardberger.tvhplayer.R.string.player_live_tv_surface)

    private fun focusableNodes() = compose.onAllNodes(isFocusable(), useUnmergedTree = true).fetchSemanticsNodes().size

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
        // A late player failure posts to Android Main. Advancing Compose's clock alone does not
        // drain Robolectric's looper or deliver the recomposed startup outcome.
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            outcomes.contains(MainStartupPlaybackOutcome.RECOVERY)
        }
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

    @Test fun ordinaryWarmReturnDoesNotRequireStartupSessionProofAndRetunesTheEndedSession() {
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
        // The adopted stream belonged to the ended session: the entry retunes it in the current one.
        assertNotEquals(epoch, runtime.videoPresentation.value.epoch)
        assertNotNull(runtime.liveTargetPresentation(selection))
    }

    @Test fun ordinaryChannelMissingFromTheCurrentSessionShowsUnavailableWithoutTuning() {
        screen(missingRequestedChannel = true, settleOnEntry = false)
        settle()
        assertNotNull(session.observation.value.currentSession)
        assertEquals(null, runtime.activeTarget.value)
        // Gone after sync, or never there: the existing unavailable state, not an endless wait.
        assertTrue(exists("player-channel-unavailable"))
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

    @Test fun theRetuneAfterAReconnectIsNotCoveredByTheFailedRequestsMessage() {
        val startup = mutableStateOf(true)
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        var superseded = false
        screen(settleOnEntry = false, contentAllowed = { !startup.value }, startupEnabled = { startup.value },
            afterTargetPlay = {
                // Only the startup tune loses its receipt; the retune below plays as usual.
                if (!superseded) runtime.notePlaybackIntent().also(runtime::notePlaybackIntentServed)
                superseded = true
            }, onStartupOutcome = { outcomes += it; startup.value = false })
        settle()
        compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.RECOVERY) }
        assertTrue(exists("player-channel-unavailable"))
        val ready = session.observation.value
        compose.runOnIdle {
            session.publish(SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Stale(ChannelCatalog.create(
                    (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) }))))
        }
        settle()
        compose.runOnIdle { session.publish(ready) }
        settle()
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertFalse("the reconnect's retune ends the failed request", exists("player-channel-unavailable"))
    }

    @Test fun aRetryWithoutACurrentSessionReportsToTheScreenThatItDidNotStart() {
        screen()
        compose.runOnIdle {
            session.publish(SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Stale(ChannelCatalog.create(
                    (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) }))))
        }
        settle()
        // The screen gives its request the failure back on this result (liveRetryRequestFailure).
        val results = mutableListOf<PlaybackTargetResult?>()
        compose.runOnIdle { video.retryLiveNow(viewerRetry = false) { results += it } }
        compose.waitUntil(5_000) { idleMainLooper(); results.isNotEmpty() }
        assertEquals(listOf(PlaybackTargetResult.NOT_READY), results)
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
        assertEquals("the programme artwork is passive", emptyList<String?>(),
            (compose.onAllNodes(hasAnyAncestor(hasTestTag("player-info-bar")) and isFocusable()).fetchSemanticsNodes() +
                compose.onAllNodes(hasTestTag("player-info-bar") and isFocusable()).fetchSemanticsNodes())
                .map { it.config.getOrNull(SemanticsProperties.TestTag) })
        assertEquals("the top identity is passive", 0,
            compose.onAllNodes(hasTestTag(PlayerTopIdentityTag) and isFocusable()).fetchSemanticsNodes().size)
        assertTrue("the action row exposes Info", exists("player-info"))
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

    @Test fun trackButtonsStandBesideInfoOnlyWithAChoiceAndReturnFocusFromTheirList() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true))))
        key(Key.DirectionDown)
        assertTrue(exists("player-info"))
        assertFalse("one audio track offers no choice", exists("player-audio"))
        assertFalse("nor do subtitles without a track", exists("player-subtitles"))
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false))))
        assertTrue(exists("player-audio"))
        assertFalse(exists("player-subtitles"))
        val button = compose.onNodeWithTag("player-audio").fetchSemanticsNode()
        assertEquals(listOf("Audio"), button.config.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue("it announces the current choice: ${button.config}",
            button.config.getOrNull(SemanticsProperties.StateDescription).orEmpty().isNotEmpty())
        val audioBounds = button.boundsInRoot
        val info = compose.onNodeWithTag("player-info").fetchSemanticsNode().boundsInRoot
        assertTrue("Audio stands before Info at the end: $audioBounds vs $info", audioBounds.right <= info.left)
        // Left from Info reaches it; it then opens the Audio list, and Back returns to it.
        compose.onNodeWithTag("player-info").performSemanticsAction(SemanticsActions.RequestFocus)
        settle()
        key(Key.DirectionLeft)
        assertEquals(listOf("player-audio"), focused())
        key(Key.DirectionCenter)
        assertTrue("the Audio quick list opens", exists("playback-options-track-list"))
        assertFalse("focus moved into the quick list", focused() == listOf("player-audio"))
        key(Key.Back)
        assertEquals(listOf("player-audio"), focused())
    }

    @Test fun theSubtitlesButtonStandsWithOneTrackSaysOffAndReturnsFocusFromItsList() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true))))
        key(Key.DirectionDown)
        assertFalse(exists("player-subtitles"))
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), subtitleTrack("s1", "de"))))
        assertFalse("one audio track still offers no choice", exists("player-audio"))
        val button = compose.onNodeWithTag("player-subtitles").fetchSemanticsNode()
        assertEquals(listOf("Subtitles"), button.config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals("Off", button.config.getOrNull(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("player-info").performSemanticsAction(SemanticsActions.RequestFocus)
        settleFrames()
        key(Key.DirectionLeft)
        assertEquals(listOf("player-subtitles"), focused())
        key(Key.DirectionCenter)
        assertTrue("the Subtitles quick list opens", exists("playback-options-subtitles-off"))
        assertTrue("with its track", exists("playback-options-track-list"))
        key(Key.Back)
        assertEquals(listOf("player-subtitles"), focused())
    }

    @Test fun aFocusedTrackButtonThatGoesHandsFocusToInfo() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false), subtitleTrack("s1", "de"))))
        key(Key.DirectionDown)
        for (button in listOf("player-audio", "player-subtitles")) {
            compose.onNodeWithTag(button).performSemanticsAction(SemanticsActions.RequestFocus)
            settleFrames()
            assertEquals(listOf(button), focused())
            // The choice shrinks to one audio track and no subtitles.
            publishTracks(Tracks(listOf(audioTrack("a1", "en", true))))
            assertFalse(exists(button))
            assertEquals("$button's neighbour takes focus", listOf("player-info"), focused())
            publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false), subtitleTrack("s1", "de"))))
        }
    }

    @Test fun backFromTheRailReturnsToTheTrackOrInfoButtonItWasEnteredFrom() = timeshiftPlaying {
        publishTracks(Tracks(listOf(audioTrack("a1", "en", true), audioTrack("a2", "de", false), subtitleTrack("s1", "de"))))
        key(Key.DirectionDown)
        for (button in listOf("player-audio", "player-subtitles", "player-info")) {
            compose.onNodeWithTag(button).performSemanticsAction(SemanticsActions.RequestFocus)
            settleFrames()
            key(Key.DirectionDown)
            assertTrue("Down from $button opens the rail", railOpen())
            key(Key.Back)
            assertFalse(railOpen())
            assertEquals(listOf(button), focused())
        }
    }

    @Test fun numberEntryKeepsTheControlsUpWithoutFocusAndReturnsToTheSeekbar() = timeshiftPlaying { pauses ->
        key(Key.DirectionDown)
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        val controls = setOf("player-seekbar", "player-pause", "player-stop", "player-info", "player-settings")
        // Back cancels the entry; an unknown number's hold times out. Both clear it the same way.
        for (cancel in listOf(true, false)) {
            // Channels 1 to 3: one digit is a complete number, and 9 names none.
            press(Key.Nine)
            assertEquals("the digit tuned nothing", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            for (direction in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
                press(direction)
                assertTrue("$direction moved focus into the controls: ${focused()}", focused().none { it in controls })
                assertTrue("the controls stay up under the entry", exists("player-actions"))
                assertFalse("$direction opened the channel rail", exists("player-now-playing"))
            }
            // Nor can an accessibility service move focus into them.
            for (tag in controls) {
                compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().forEach {
                    it.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
                }
                settleFrames()
                assertTrue("$tag took focus under the entry: ${focused()}", focused().none { it in controls })
            }
            assertEquals("an accessibility service finds nothing to act on", emptyList<String>(), actionable())
            assertTrue("the entry is still up", compose.onAllNodesWithText("9").fetchSemanticsNodes().isNotEmpty())
            assertEquals("no control acted", emptyList<Boolean>(), pauses)
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            if (cancel) key(Key.Back) else repeat(2) { settle() }
            assertTrue("the entry left", compose.onAllNodesWithText("9").fetchSemanticsNodes().isEmpty())
            assertEquals("focus returned to the timeline (cancel=$cancel)", listOf("player-seekbar"), focused())
            assertTrue("the actions are back: ${actionable()}", actionable().containsAll(controls - "player-seekbar"))
        }
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

    @Test fun atTheLiveEdgeTheBarRowIsPassiveAndUpStaysOnTheTimeline() {
        liveChrome(behindLiveMs = 0)
        assertEquals(listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertEquals("artwork is passive", listOf("player-seekbar"), focused())
        key(Key.DirectionDown)
        assertEquals("Down returns to the action row", listOf("player-pause"), focused())
        assertFalse(exists("player-go-live"))
        assertFalse(exists("player-status-slot"))
        assertFalse("the controls have no state cell; the Pause button speaks for playback", exists("player-state"))
        assertEquals("Live", description("player-live-state"))
        assertFalse("nothing is drawn at the live edge", exists("player-distance"))
        assertEquals(0, compose.onAllNodes(hasTestTag("player-live-state") and isFocusable(), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun behindLiveUpFromTheSeekbarStaysOnTheTimeline() {
        liveChrome(behindLiveMs = 203_000)
        assertEquals(listOf("player-pause"), focused())
        assertEquals("Behind live", description("player-live-state"))
        assertEquals("3:23 behind live", texts("player-distance"))
        key(Key.DirectionUp)
        assertEquals(listOf("player-seekbar"), focused())
        key(Key.DirectionUp)
        assertEquals("artwork is passive", listOf("player-seekbar"), focused())
        key(Key.DirectionDown)
        assertEquals("Down returns to the action row", listOf("player-pause"), focused())
        assertFalse("no Go live node", exists("player-go-live"))
        assertFalse(exists("player-go-live-label"))
    }

    @Test fun theControlsShowPausedThroughThePlayButtonNotAStateCell() {
        liveChrome(behindLiveMs = 0, state = PlayerStateCell.PAUSED)
        assertFalse(exists("player-state"))
        compose.onNodeWithTag("player-pause").assertContentDescriptionEquals("Play")
    }

    @Test fun numberEntryLeavesTheCoveredControlsNoAccessibilityActions() {
        var entry by mutableStateOf(false)
        val calls = mutableListOf<String>()
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("20:15", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "One", null, null, false, 1_800, "Programme"), state = PlayerStateCell.PAUSED),
                    timeline = PlayerChromeTimeline.Live(
                        at.bernhardberger.tvhplayer.playback.AppTimeshiftState(available = true, bufferStartMs = -600_000,
                            positionMs = -60_000, liveEdgeMs = 0, timingKnown = true, paused = true),
                        nowSec = 1_800,
                    ),
                    actions = PlayerChromeActions(active = !entry, paused = true),
                    onTogglePause = { calls += "pause" }, onSeek = { calls += "seek" }, onStop = { calls += "stop" },
                    onInfo = { calls += "info" }, onOptions = { calls += "options" }, onInteraction = {},
                    numberEntryShown = entry,
                )
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
        val seekActions = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and hasTestTag("player-seekbar")
        assertTrue("seekable: the timeline offers its seek actions", compose.onAllNodes(seekActions).fetchSemanticsNodes().isNotEmpty())
        val offered = actionable()
        compose.runOnIdle { entry = true }
        settle()
        assertEquals("covered, the controls offer nothing to act on", emptyList<String>(), actionable())
        assertFalse("nor can their seek actions be reached", exists("player-seekbar") &&
            compose.onAllNodes(seekActions).fetchSemanticsNodes().isNotEmpty())
        compose.runOnIdle { entry = false }
        settle()
        assertEquals("the same actions return", offered, actionable())
        assertEquals("nothing acted", emptyList<String>(), calls)
    }

    /** The live chrome's controls [behindLiveMs] behind the live edge with known timing. */
    private fun liveChrome(behindLiveMs: Long, state: PlayerStateCell = PlayerStateCell.PLAYING) {
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("20:15", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "One", null, null, false, 1_800, "Programme"), state = state),
                    timeline = PlayerChromeTimeline.Live(
                        at.bernhardberger.tvhplayer.playback.AppTimeshiftState(available = true, bufferStartMs = -600_000,
                            positionMs = -behindLiveMs, liveEdgeMs = 0, timingKnown = true),
                        nowSec = 1_800,
                    ),
                    actions = PlayerChromeActions(active = true, paused = state == PlayerStateCell.PAUSED),
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
        assertTrue(exists("programme-details-page"))
        assertEquals(listOf("details-continue_watching"), focused())
        assertFalse(exists("live-info-stream-signal"))
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
        assertTrue(exists("player-actions"))
        assertEquals(listOf("player-info"), focused())
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
        assertTrue(tracksAudioOnly(audioOnly))
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

    @Test fun programmeInfoRetainsItsLastSnapshotAndWatchFocusAcrossBoundaryAndEviction() {
        screen()
        key(Key.Info)
        val now = System.currentTimeMillis() / 1_000L
        val ended = EpgEvent.create(EventId(11L), ChannelId(1L), Instant.fromEpochSeconds(now - 1_800L),
            Instant.fromEpochSeconds(now - 1L), title = "Ended programme", summary = "Last known summary")
        val nextProgramme = EpgEvent.create(EventId(21L), ChannelId(1L), Instant.fromEpochSeconds(now - 60L),
            Instant.fromEpochSeconds(now + 3_600L), title = "Next up", summary = "Summary")
        session.publish(observation(now, listOf(ended, nextProgramme)))
        repeat(2) { settle() }
        assertEquals("Ended programme", texts("details-title"))
        assertFalse(exists("details-record"))
        // The ended programme is not the one at the viewer position: it offers Watch, which returns to the picture.
        assertEquals(listOf("details-watch"), focused())
        session.publish(observation(now, listOf(nextProgramme)))
        repeat(2) { settle() }
        assertEquals("Ended programme", texts("details-title"))
        assertEquals(listOf("details-watch"), focused())
        session.publish(observation(now, listOf(nextProgramme), recordings = listOf(
            DvrEntry.create(DvrEntryId(2L), eventId = nextProgramme.id, state = DvrEntryState.RECORDING),
        )))
        repeat(2) { settle() }
        assertEquals(listOf("details-watch"), focused())
        assertFalse(exists("details-stop"))
        assertEquals("Ended programme", texts("details-title"))
        session.publish(observation(now, listOf(nextProgramme), missingRequestedChannel = true))
        repeat(2) { settle() }
        assertEquals(listOf("player-info"), focused())
        assertFalse(exists("programme-details-page"))
    }

    @Test fun withoutEpgInfoKeepsContinueWatchingFocusedAndBackReturnsToInfo() {
        screen(epg = false)
        key(Key.Info)
        assertTrue(exists("programme-details-page"))
        assertEquals(listOf("details-continue_watching"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("details-continue_watching"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("details-continue_watching"), focused())
        assertFalse(exists("live-info-stream-signal"))
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
        assertTrue(exists("player-actions"))
        assertEquals(listOf("player-info"), focused())
    }

    @Test fun theLiveChromeShowsNoStreamBadgesAndStartsNoDiagnostics() {
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
                        audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
                ), null))
                // 53739 of 65535: 82 %.
                connection.emit(SubscriptionEvent.Signal(53739, 12300, null, null, 0, 0, false))
            }
            val diagnostics = runtime.diagnostics.value
            assertTrue("Stats is closed: no format or frontend diagnostics",
                diagnostics.video == null && diagnostics.audio == null && diagnostics.live == null)
            // Trial: the stream badges have no place on the live chrome, with the tracks known.
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            settle()
            assertTrue(exists("player-banner"))
            val badges = badgeDescription("player-banner")
            for (absent in listOf("lines", "Video", "Audio", "Signal-to-noise")) {
                assertFalse("Banner '$badges' shows no badge ('$absent')", badges.contains(absent))
            }

            // Without live Pause, Center explains over the Banner; Down opens the controls.
            key(Key.DirectionCenter)
            assertFalse(exists("player-actions"))
            assertTrue(exists("player-banner"))
            assertTrue("the reason shows in the Banner", runtime.livePause.value.availability.let {
                it != LivePauseAvailability.UNAVAILABLE && it != LivePauseAvailability.OFF
            } || compose.onAllNodesWithText("Pause isn't available", substring = true).fetchSemanticsNodes().isNotEmpty())
            key(Key.DirectionDown)
            assertTrue(exists("player-actions"))
            val controls = identityDescription() + " | " + badgeDescription("player-footer")
            for (absent in listOf("lines", "Video", "Audio")) {
                assertFalse("controls '$controls' show no badge ('$absent')", controls.contains(absent))
            }

            // Programme Info never starts full diagnostics. Stats for nerds still owns them.
            key(Key.Info)
            assertTrue(exists("programme-details-page"))
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

    @Test fun noStreamBadgeShowsOnTheLiveChromeBeforeOrAfterAZap() {
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
            for (absent in listOf("lines", "Video", "Audio")) {
                assertFalse("A's Banner '$badgesA' shows no badge ('$absent')", badgesA.contains(absent))
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
            assertTrue(exists("programme-details-page"))
            key(Key.Back)
            assertFalse(exists("programme-details-page"))
            val staleControls = identityDescription()
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
            val badgesB = identityDescription() + " | " + badgeDescription("player-footer")
            for (absent in listOf("lines", "Video", "Audio")) {
                assertFalse("B's controls '$badgesB' show no badge ('$absent')", badgesB.contains(absent))
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
                    audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
            ), null))
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
        assertTrue("unknown playback does not claim the airing programme: $info", info.contains("No program information"))
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

    @Test fun playerInfoStartsOnContinueWatchingAndHasNoRetiredScheduleTabs() {
        screen()
        key(Key.Info)
        assertEquals("The first action takes focus", listOf("details-continue_watching"), focused())
        assertFalse(exists("details-schedule"))
        assertFalse(exists("details-tab-0"))
        assertFalse(exists("details-tab-1"))
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
        assertEquals(listOf("player-info"), focused())
    }

    @Test fun playerInfoOffersCancelAfterContinueWatchingWhenAlreadyScheduled() {
        screen(recordingScheduled = true)
        key(Key.Info)
        assertEquals(listOf("details-continue_watching"), focused())
        compose.onNodeWithTag("details-cancel").assertExists()
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
    }

    @Test fun playerInfoDoesNotInventMissingProgrammeTitleOrSubtitle() {
        screen()
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, listOf(EpgEvent.create(EventId(31), ChannelId(1),
            Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600)))))
        settle()
        key(Key.Info)
        assertEquals(listOf("details-continue_watching"), focused())
        assertEquals("", texts("details-title"))
        assertFalse(exists("details-subtitle"))
        assertFalse(exists("details-tab-1"))
    }

    @Test fun playerInfoConsumesRemoteCommandsAndReturnsToInfo() {
        screen()
        key(Key.DirectionDown)
        assertEquals(listOf("player-pause"), focused())
        key(Key.Info)
        assertEquals(listOf("details-continue_watching"), focused())
        assertFalse(exists("details-schedule"))
        val playing = player.playWhenReady
        listOf(Key.ChannelUp, Key.ChannelDown, Key.MediaStop, Key.MediaPause, Key.Menu, Key.Info).forEach(::key)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertEquals(playing, player.playWhenReady)
        assertEquals(listOf("details-continue_watching"), focused())
        key(Key.Back)
        assertEquals(listOf("player-info"), focused())
        key(Key.Info)
        assertEquals("a new visit resets Details and its action", listOf("details-continue_watching"), focused())
    }

    @Test fun downFromTheActionRowOpensRowsAndUpFromTheTopOrBackReturnsToTheAction() {
        screen()
        key(Key.DirectionDown)
        assertEquals(listOf("player-pause"), focused())
        assertEquals("Channels", texts("player-down-hint"))
        for (close in listOf(Key.DirectionUp, Key.Back)) {
            key(Key.DirectionRight)
            key(Key.DirectionRight)
            key(Key.DirectionRight)
            assertEquals(listOf("player-settings"), focused())
            key(Key.DirectionDown)
            assertEquals("browser opens on the playing channel", listOf("player-channel-card-1"), focused())
            assertTrue("the now-playing strip stands above the rows", exists("player-now-playing"))
            // This screen's channel never leaves tuning.
            compose.onNode(hasTestTag("channel-tuning-indicator") and hasAnyAncestor(hasTestTag("player-now-playing")),
                useUnmergedTree = true).assertExists("the strip carries the playback marker")
            assertFalse("the programme info page does not open", exists("programme-details-page"))
            assertEquals("the opening key tunes nothing", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            if (close == Key.Back) {
                key(Key.DirectionRight)
                assertEquals("cards browse without tuning", listOf("player-channel-card-2"), focused())
                assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            }
            key(close)
            assertFalse(railOpen())
            assertEquals(listOf("player-settings"), focused())
            key(Key.DirectionLeft)
            key(Key.DirectionLeft)
            key(Key.DirectionLeft)
        }
        assertEquals(listOf("player-pause"), focused())
    }

    @Test fun holdOnACardOpensSharedDetailsAndBackRestoresTheRailCard() {
        screen()
        publishRailProgrammes()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("player-channel-card-2"), focused())
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(1_200); keyUp(Key.DirectionCenter) }
        settle()
        assertTrue("the programme's shared details open", exists("programme-details-page"))
        assertFalse("without section tabs or heading", exists("details-top-row"))
        assertFalse("without the page hint", exists("details-player-hint"))
        assertEquals("nothing tunes", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        val action = focused()
        key(Key.DirectionUp)
        assertTrue("Up does not close details", exists("programme-details-page"))
        assertEquals(action, focused())
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
        assertEquals("one Back returns to the invoking card", listOf("player-channel-card-2"), focused())
        assertTrue(railOpen())
        key(Key.DirectionCenter)
        assertEquals("the first short OK after closing tunes channel 2", AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        key(Key.Back)
        assertFalse(railOpen())
        assertEquals("subsequent Back from the rail returns to Pause", listOf("player-pause"), focused())
    }

    @Test fun accessibilityLongClickAfterCompletedOkDoesNotSwallowFirstDetailsOk() {
        screen()
        publishRailProgrammes()
        key(Key.DirectionDown); key(Key.DirectionDown)
        key(Key.DirectionRight); key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(AppPlaybackTarget.Live(ChannelId(3)), runtime.activeTarget.value)
        key(Key.DirectionLeft)
        compose.onNodeWithTag("player-channel-card-2").performSemanticsAction(SemanticsActions.OnLongClick)
        settle()
        assertTrue(exists("programme-details-page"))
        key(Key.DirectionCenter)
        assertEquals("a fresh OK activates Watch", AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun reopenedRailPreviewsItsRememberedCardFromTheFirstFrame() {
        screen()
        publishRailProgrammes()
        openRows()
        key(Key.DirectionRight)
        assertEquals(listOf("player-channel-card-2"), focused())
        key(Key.Back)
        assertFalse(railOpen())
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            val previewText = compose.onAllNodes(hasAnyAncestor(hasTestTag("player-zap-preview")), useUnmergedTree = true)
                .fetchSemanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            assertTrue("frame $it previews the remembered card, not the playing one: $previewText",
                previewText.isEmpty() || "Second channel now" in previewText)
        }
        compose.mainClock.autoAdvance = true
        settle()
        assertEquals(listOf("player-channel-card-2"), focused())
    }

    @Test fun theRailPreviewDescribesTheFocusedCardAfterEveryMove() {
        screen()
        publishRailProgrammes()
        openRows()
        fun previewText() = compose.onAllNodes(hasAnyAncestor(hasTestTag("player-zap-preview")), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertFalse("card 1 is not described by its neighbour: ${previewText()}", "Second channel now" in previewText())
        key(Key.DirectionRight)
        assertEquals(listOf("player-channel-card-2"), focused())
        assertTrue("card 2 is described: ${previewText()}", "Second channel now" in previewText())
        key(Key.DirectionLeft)
        assertEquals(listOf("player-channel-card-1"), focused())
        assertFalse("back on card 1: ${previewText()}", "Second channel now" in previewText())
    }

    @Test fun recentCardTunedElsewhereReopensTheRowOnItsFirstCardFromTheFirstFrame() {
        runBlocking { LastPlayedChannelStore(context).run { setChannelId(ChannelId(3)); setChannelId(ChannelId(2)) } }
        screen()
        publishRailProgrammes()
        openRows()
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-2"), focused())
        key(Key.Back)
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertEquals(listOf("player-pause"), focused())
        compose.mainClock.autoAdvance = false
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        repeat(6) {
            compose.mainClock.advanceTimeByFrame()
            val previewText = compose.onAllNodes(hasAnyAncestor(hasTestTag("player-zap-preview")), useUnmergedTree = true)
                .fetchSemanticsNodes().flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            assertFalse("frame $it previews the tuned channel that left the row: $previewText", "Second channel now" in previewText)
        }
        compose.mainClock.autoAdvance = true
        settle()
        val recentRow = hasAnyAncestor(hasTestTag("player-channel-row-RECENT"))
        compose.onNode(hasTestTag("player-channel-card-2") and recentRow).assertDoesNotExist()
        compose.onNode(isFocused() and recentRow).assertExists()
    }

    @Test fun withoutRecentChannelsTheRailHasOneRowAndDownStaysOnIt() {
        screen()
        key(Key.DirectionDown); key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
        assertFalse(exists("player-channel-row-RECENT"))
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
        key(Key.Back)
        assertFalse(railOpen())
        assertEquals(listOf("player-pause"), focused())
    }

    @Test fun playerInfoRecordsDirectlyThenOffersStopFromObservedState() {
        screen(recordingProfile = DvrConfigId("archive"))
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(DvrConfigId("archive"), scheduleRequests.single().configId)
        publishInfoRecording(DvrEntryState.RECORDING)
        assertEquals("Stop recording", texts("details-stop"))
        assertEquals(listOf("details-stop"), focused())
        assertTrue("command success never posts a notice", notices.state.value.pending.isEmpty())
        assertFalse("the shared queue replaces the local notice", exists("programme-recording-notice"))
        session.dvrRepository.scriptStopEntry(DvrMutationResult.Confirmed(Unit))
        key(Key.DirectionCenter)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        confirmInfoAction(destructive = true)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        assertEquals(listOf("details-stop"), focused())
    }

    @Test fun playerInfoConfirmationBackIsSafeAndDoesNotCloseDetails() {
        screen(recordingScheduled = true)
        key(Key.Info)
        compose.onNodeWithTag("details-cancel").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("two-column-first").assertIsFocused().assertTextEquals("Keep recording")
        compose.onNode(isDialog()).performKeyInput { pressKey(Key.Back) }
        settle()
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        assertTrue(exists("programme-details-page"))
        assertEquals(listOf("details-cancel"), focused())
        assertTrue(notices.state.value.pending.isEmpty())
    }

    @Test fun playerInfoRecordIgnoresASecondPressBeforeRecomposition() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        val click = compose.onNodeWithTag("details-record").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { click(); click() }
        settle()
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        assertEquals(listOf("details-record"), focused())
    }

    @Test fun playerInfoStopKeepReturnsToTheInvokingButtonWithoutSending() {
        screen()
        publishInfoRecording(DvrEntryState.RECORDING)
        key(Key.Info)
        compose.onNodeWithTag("details-stop").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("two-column-first").assertIsFocused().assertTextEquals("Keep recording")
            .performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
        assertEquals(listOf("details-stop"), focused())
    }

    @Test fun playerInfoRecordingFailureKeepsRecordAndShowsANonFocusableNotice() {
        screen()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("centred-dialog-card").assertDoesNotExist()
        assertEquals("Record", texts("details-record"))
        assertEquals(listOf("details-record"), focused())
        val notice = notices.state.value.pending.single().notice
        assertEquals(NoticeSeverity.FAILURE, notice.severity)
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED), notice)
        assertEquals("dvr-action", notice.key)
        assertFalse(exists("programme-recording-notice"))
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
    }

    @Test fun playerInfoRecordFailureAfterContinueWatchingPostsNotice() {
        val completion = CompletableDeferred<Unit>()
        screen(scheduleCompletion = completion)
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        compose.onNodeWithTag("details-continue_watching").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        compose.runOnIdle { completion.complete(Unit) }
        settle()
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED),
            notices.state.value.pending.single().notice)
        compose.onNodeWithTag("programme-details-page").assertDoesNotExist()
        assertEquals(listOf("player-info"), focused())
    }

    @Test fun playerInfoRecordResultAfterCloseDoesNotAffectReopenedProgramme() {
        val completion = CompletableDeferred<Unit>()
        screen(scheduleCompletion = completion)
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        key(Key.Back)
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, listOf(EpgEvent.create(EventId(21), ChannelId(1),
            Instant.fromEpochSeconds(now - 60), Instant.fromEpochSeconds(now + 600), title = "New programme"))))
        settle()
        key(Key.Info)
        assertEquals("New programme", texts("details-title"))
        compose.runOnIdle { completion.complete(Unit) }
        settle()
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED),
            notices.state.value.pending.single().notice)
        assertEquals(listOf("details-continue_watching"), focused())
        compose.onNodeWithTag("details-record").assertExists()
        compose.onNodeWithTag("details-stop").assertDoesNotExist()
    }

    @Test fun playerInfoRecordResultAfterSelectingAnotherAiringDoesNotAffectThatPage() {
        val completion = CompletableDeferred<Unit>()
        screen(scheduleCompletion = completion)
        publishMatchingFutureProgramme()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Failed(TvheadendFailure.ACCESS_DENIED, mayHaveApplied = false))
        key(Key.Info)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        key(Key.DirectionDown)
        compose.onNodeWithTag("details-card-12").assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.runOnIdle { completion.complete(Unit) }
        settle()
        assertEquals(Notice.DvrActionFailed(DvrMutationKind.SCHEDULE, DvrMutationFeedback.PERMISSION_DENIED),
            notices.state.value.pending.single().notice)
        compose.onNodeWithTag("details-watch").assertIsFocused()
        compose.onNodeWithTag("details-record").assertExists()
    }

    @Test fun playerInfoFutureAiringRecordsThenAutomaticallyFocusesCancel() {
        screen()
        publishMatchingFutureProgramme()
        session.dvrRepository.scriptScheduleEntry(DvrMutationResult.Confirmed(DvrEntryId(7)))
        key(Key.Info)
        key(Key.DirectionDown)
        compose.onNodeWithTag("details-card-12").assertIsFocused()
        key(Key.DirectionCenter)
        compose.onNodeWithTag("details-record").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_SCHEDULE_ENTRY })
        publishInfoRecording(DvrEntryState.SCHEDULED, EventId(12))
        compose.onNodeWithTag("details-cancel").assertIsFocused()
        session.dvrRepository.scriptCancelEntry(DvrMutationResult.Confirmed(Unit))
        key(Key.DirectionCenter)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        confirmInfoAction(destructive = true)
        assertEquals(1, session.calls.count { it == FakeSessionCall.DVR_CANCEL_ENTRY })
        compose.onNodeWithTag("details-cancel").assertIsFocused()
    }

    private fun publishMatchingFutureProgramme() {
        val now = System.currentTimeMillis() / 1_000L
        val events = (session.observation.value.epgState as EpgRepositoryState.Current).snapshot.events
        session.publish(observation(now, listOf(events.first(), EpgEvent.create(EventId(12), ChannelId(1),
            Instant.fromEpochSeconds(now + 3_600), Instant.fromEpochSeconds(now + 7_200), title = "Now showing"))))
        settle()
    }

    @Test fun playerInfoUsesSharedDescriptionReaderAndReturnsToItsPanel() {
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
        compose.onNodeWithTag("details-about-description").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        assertTrue(texts("details-reading-heading").contains("Now showing"))
        assertTrue(texts("details-reading-text").contains(description.trim()))
        key(Key.Back)
        assertEquals(listOf("details-about-description"), focused())
    }

    @Test fun upRevealLandsOnPauseAndArtworkRemainsPassive() {
        screen()
        assertFalse("the live controls have no Record action", exists("player-record"))
        key(Key.DirectionUp)
        assertEquals("Up reveals controls onto Pause", listOf("player-pause"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("player-pause"), focused())
        assertFalse(compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true).fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        assertFalse("no browser opens", railOpen())
        assertTrue(identityDescription().contains("Name 1"))
    }

    @Test fun recentRowRemembersItsSelectionAndReopensDirectlyOnCards() {
        // Channel 2 played last, before channel 3.
        runBlocking { LastPlayedChannelStore(context).run { setChannelId(ChannelId(3)); setChannelId(ChannelId(2)) } }
        screen()
        openRows()
        key(Key.DirectionDown)
        assertEquals("newest recent channel", listOf("player-channel-card-2"), focused())
        assertEquals("browsing tunes nothing", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        key(Key.Back)
        assertFalse("Back closes the browser", railOpen())
        assertEquals("and returns to Pause", listOf("player-pause"), focused())
        assertTrue("the controls stay", exists("player-actions"))
        key(Key.DirectionDown)
        assertEquals("remembered tab opens on cards", listOf("player-channel-card-2"), focused())
        key(Key.DirectionCenter)
        assertEquals("OK on a recent channel tunes it", AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertTrue("another channel keeps the browser", railOpen())
    }

    @Test fun sharedDetailsPreserveThePlayerAndDisposeOnBack() {
        screen()
        key(Key.DirectionDown)
        val target = runtime.activeTarget.value
        val playing = player.playWhenReady
        key(Key.Info)
        assertEquals(listOf("details-continue_watching"), focused())
        assertEquals(target, runtime.activeTarget.value)
        assertEquals(playing, player.playWhenReady)
        key(Key.Back)
        assertFalse(exists("programme-details-page"))
        assertEquals(listOf("player-info"), focused())
        assertFalse("controls rest has no now-playing strip", exists("player-now-playing"))
        assertTrue(exists("player-actions"))
    }

    @Test fun shortOkTunesDirectlyAndKeepsBrowsingContext() {
        screen()
        publishRailProgrammes()
        openRows()
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
        assertFalse(exists("programme-details-page"))
        assertTrue(railOpen())
        assertEquals(listOf("player-channel-card-2"), focused())
    }

    private fun openRows() {
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
    }

    private fun confirmInfoAction(destructive: Boolean = false) {
        compose.onNodeWithTag("two-column-first").assertIsFocused()
        compose.onNode(isDialog()).performKeyInput {
            if (destructive) pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
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

    /** Live's controls name the channel and its badges in the top identity's description. */
    private fun identityDescription(): String = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" | ")

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

    @Test fun channelUpDoesNotTuneWhileTheRowsAreOpen() {
        screen()
        openRows()
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue("the browser stays open", railOpen())
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

    private fun audioTrack(id: String, language: String, selected: Boolean) = Tracks.Group(TrackGroup(Format.Builder()
        .setId(id).setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage(language).setChannelCount(2).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))

    private fun subtitleTrack(id: String, language: String) = Tracks.Group(TrackGroup(Format.Builder()
        .setId(id).setSampleMimeType(MimeTypes.APPLICATION_DVBSUBS).setLanguage(language).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))

    private fun publishTracks(tracks: Tracks) {
        forcedTracks = tracks
        compose.runOnIdle { playerListeners.toList().forEach { it.onTracksChanged(tracks) } }
        // A few frames only: the controls' hide timer must not run out in between.
        settleFrames()
    }

    private fun exists(tag: String) =
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The closed rail stays composed to peek, cleared from what accessibility reads; the merged tree respects that. */
    private fun railOpen() = compose.onAllNodes(hasTestTag("player-channel-browser")).fetchSemanticsNodes().isNotEmpty()

    /** Tags of the nodes an accessibility service can activate, adjust or run custom actions on. */
    private fun actionable(): List<String> = compose.onAllNodes(SemanticsMatcher("actionable") { node ->
        listOf(SemanticsActions.OnClick, SemanticsActions.CustomActions, SemanticsActions.SetProgress).any { it in node.config }
    }).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged ${it.id}" }

    private fun focused(): List<String> =
        compose.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun key(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    /** Presses [key] and lets a few frames pass, short of any entry timer. */
    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settleFrames()
    }

    private fun settleFrames() = repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }

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
        recordingProfiles: List<DvrConfiguration> = emptyList(),
    ) =
        SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                (if (missingRequestedChannel) 2..3L else 1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) },
            )),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = events)),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = recordings)),
            dvrConfigurationsState = DvrConfigurationsState.Current.create(recordingProfiles),
        )

    private fun screen(
        onClose: () -> Unit = {},
        failing: Boolean = false,
        fontScale: Float = 1f,
        epg: Boolean = true,
        opener: SubscriptionOpener? = null,
        recordingScheduled: Boolean = false,
        recordingProfile: DvrConfigId? = null,
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
        scheduleCompletion: CompletableDeferred<Unit>? = null,
    ) {
        val now = System.currentTimeMillis() / 1_000L
        session = FakeTvheadendSession(observation(now, if (!epg) emptyList() else listOf(
            EpgEvent.create(EventId(11L), ChannelId(1L), Instant.fromEpochSeconds(now - 1_800L),
                Instant.fromEpochSeconds(now + 3_600L), title = "Now showing", summary = "Summary"),
            EpgEvent.create(EventId(12L), ChannelId(1L), Instant.fromEpochSeconds(now + 3_600L),
                Instant.fromEpochSeconds(now + 7_200L), title = "Later"),
        ), recordings = if (recordingScheduled) listOf(DvrEntry.create(DvrEntryId(1L), eventId = EventId(11L), state = DvrEntryState.SCHEDULED))
            else emptyList(), missingRequestedChannel = missingRequestedChannel,
            recordingProfiles = if (recordingProfile == null) emptyList() else listOf(
                DvrConfiguration(DvrConfigId("default"), "", ""), DvrConfiguration(recordingProfile, "Archive", "")))).apply {
            // The subscription never becomes playable: the channel stays tuning, as on a slow tune.
            // Failing: the scripted stream cannot be decoded, so the channel becomes unavailable.
            if (opener != null) scriptLivePlaybackSuccess(opener)
            else if (failing) scriptLivePlaybackSuccess()
            else scriptLivePlaybackSuccess(SubscriptionOpener { _, _, _ -> awaitCancellation() })
        }
        val settings = PlayerSettingsStore(settingsData)
        val uiSettings = at.bernhardberger.tvhplayer.settings.UiSettingsStore(InMemoryData())
        recordingProfile?.let { runBlocking { uiSettings.setRecordingProfile(it) } }
        val screenSession = object : TvheadendSession by session {
            override val dvrRepository = object : DvrRepository by session.dvrRepository {
                override suspend fun scheduleEntry(currentSession: CurrentSessionObservation, request: DvrScheduleRequest): DvrMutationResult<DvrEntryId> {
                    scheduleRequests += request
                    val result = session.dvrRepository.scheduleEntry(currentSession, request)
                    scheduleCompletion?.await()
                    return result
                }
            }
        }
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.IO,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryData()))
        models.put("channels", channels)
        compose.waitUntil(10_000) { channels.scope.value.scope.visibleChannels.size == if (missingRequestedChannel) 2 else 3 }
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
                    // The app root shows the player inside the runtime's watching state.
                    TVHeadendPlayerTheme { ProvideWatchingProgress(runtime) {
                        view = LocalView.current
                        VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), settings,
                            channels, screenSession, ChannelId(1), "Name 1", {}, onClose, runtime,
                            contentAllowed = contentAllowed(), notices = notices, uiSettingsStore = uiSettings,
                            startupTarget = if (startupEnabled()) ApplianceLaunchTarget(ApplianceLaunchRequest(startupRequestId()), ChannelId(1), "Name 1") else null,
                            onStartupOutcome = { _, outcome -> onStartupOutcome(outcome); acceptStartupOutcome() })
                    } }
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
