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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import android.graphics.Canvas
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
import at.bernhardberger.tvhplayer.core.PlayerForegroundLayer
import at.bernhardberger.tvhplayer.core.PlayerKeyContext
import at.bernhardberger.tvhplayer.core.PlayerSurface
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
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
        assertEquals("a stall after a presented tune is not another tune", "Buffering…", description("player-busy-indicator"))
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
            DvrEntry.create(DvrEntryId(2L), eventId = nextProgramme.id),
        )))
        repeat(2) { settle() }
        assertFalse(exists("live-info-record"))
        assertEquals(listOf("live-info-close"), focused())
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
        assertFalse(exists("live-info-stream-signal"))
        key(Key.Back)
        assertFalse(exists("live-info-panel"))
        assertTrue(exists("player-actions"))
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
        assertTrue("unknown playback does not claim the airing programme: $info", info.contains("Programme information unavailable"))
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
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-record"), focused())
        key(Key.DirectionRight)
        assertEquals(listOf("live-info-close"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-close"), focused())
        key(Key.DirectionUp)
        assertEquals("the description stays reachable with Up", listOf("player-info-reading"), focused())
    }

    @Test fun playerInfoStartsOnCloseWithoutRecording() {
        // The airing programme is already scheduled, so Record is not offered.
        screen(recordingScheduled = true)
        key(Key.Info)
        assertEquals(listOf("live-info-close"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-close"), focused())
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
        openRail()
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

    private fun openRail() {
        var presses = 0
        while (!exists("player-channel-shelf") && presses < 3) {
            key(Key.DirectionDown)
            presses++
        }
        assertTrue("Down opens the rail", exists("player-channel-shelf"))
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
    ) =
        SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) },
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
    ) {
        val now = System.currentTimeMillis() / 1_000L
        session = FakeTvheadendSession(observation(now, if (!epg) emptyList() else listOf(
            EpgEvent.create(EventId(11L), ChannelId(1L), Instant.fromEpochSeconds(now - 1_800L),
                Instant.fromEpochSeconds(now + 3_600L), title = "Now showing", summary = "Summary"),
            EpgEvent.create(EventId(12L), ChannelId(1L), Instant.fromEpochSeconds(now + 3_600L),
                Instant.fromEpochSeconds(now + 7_200L), title = "Later"),
        ), recordings = if (recordingScheduled) listOf(DvrEntry.create(DvrEntryId(1L), eventId = EventId(11L)))
            else emptyList())).apply {
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
        compose.waitUntil(10_000) { channels.channels.value.size == 3 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val base = player
        val controlled = object : ExoPlayer by base {
            override fun getPlaybackState(): Int = if (forcedReady) Player.STATE_READY else base.playbackState
            override fun isPlaying(): Boolean = forcedReady || base.isPlaying
            override fun getCurrentTracks(): Tracks = forcedTracks ?: base.currentTracks
            override fun getVideoFormat(): Format? = forcedVideoFormat ?: base.videoFormat
            override fun getAudioFormat(): Format? = forcedAudioFormat ?: base.audioFormat
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
        compose.waitForIdle()
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
                            channels, ImageLoader.Builder(context).build(), session, ChannelId(1), "Name 1", {}, onClose, runtime)
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
