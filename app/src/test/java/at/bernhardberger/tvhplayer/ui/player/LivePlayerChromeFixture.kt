@file:androidx.media3.common.util.UnstableApi
@file:OptIn(
    at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class,
)


package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.testutil.focusedTags
import at.bernhardberger.tvhplayer.testutil.tagExists
import at.bernhardberger.tvhplayer.testutil.pressKey
import at.bernhardberger.tvhplayer.testutil.settleFrames
import android.app.Application
import android.view.KeyEvent as AndroidKeyEvent
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
import at.bernhardberger.tvhplayer.testutil.assertMainSettled
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
import at.bernhardberger.tvhplayer.testutil.holdBack
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
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
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequest
import at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget
import at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.ui.components.ProvideWatchingProgress
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.PlaybackAudioFocus
import at.bernhardberger.tvhplayer.playback.PlaybackRuntimePolicy
import at.bernhardberger.tvhplayer.playback.PlaybackTrace
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.ui.TVHeadendPlayerTheme
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.robolectric.Shadows.shadowOf

abstract class LivePlayerChromeFixture {
    @get:Rule val compose = createComposeRule()

    protected val context = ApplicationProvider.getApplicationContext<Application>()

    protected val models = ViewModelStore()

    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected val subscriptionDispatcher = StandardTestDispatcher()

    protected lateinit var player: ExoPlayer

    protected lateinit var view: View

    protected lateinit var runtime: AppPlaybackRuntime

    protected lateinit var session: FakeTvheadendSession

    protected val scheduleRequests = mutableListOf<DvrScheduleRequest>()

    protected val notices = NoticeCenter({ 0L }) { NoticeContext(0, null) }

    protected lateinit var video: VideoPlayerViewModel

    protected val owner = ScreenLifecycleOwner()
    protected class ScreenLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    /** Listeners on the runtime's player, so tests can drive states the fake stream never reaches. */
    protected val playerListeners = mutableListOf<Player.Listener>()

    /** The player reports READY and playing, as when the tune's stream has started. */
    protected var forcedReady = false

    /** Tracks the player reports, for services the fake stream never describes. */
    protected var forcedTracks: Tracks? = null

    protected var forcedVideoFormat: Format? = null

    protected var forcedAudioFormat: Format? = null

    protected val screenVisible = mutableStateOf(true)

    // The preferencesDataStore delegate is a sandbox singleton, not a per-Application fixture.
    // Snapshot the real store rather than replacing production persistence or leaving channel 2
    // selected for the next screen test. There is deliberately no production reset API.
    // These persistence calls await DataStore's real Dispatchers.IO file worker.
    @Suppress("UNCHECKED_CAST")
    protected val lastPlayedPreferences by lazy {
        Class.forName("at.bernhardberger.tvhplayer.settings.ServerSettingsKt")
            .getDeclaredMethod("getDataStore", android.content.Context::class.java)
            .apply { isAccessible = true }.invoke(null, context) as DataStore<Preferences>
    }

    protected lateinit var originalLastPlayed: Preferences

    @Before fun before() {
        originalLastPlayed = runBlocking { lastPlayedPreferences.data.first() }
        runBlocking {
            lastPlayedPreferences.edit { it[stringPreferencesKey("activeServerProfileId")] =
                at.bernhardberger.tvheadend.sdk.core.ServerProfileId.generate().value }
        }
    }

    @After fun after() {
        closeScreen()
        runBlocking { lastPlayedPreferences.updateData { originalLastPlayed } }
    }

    protected var screenClosed = false

    protected fun closeScreen() {
        if (screenClosed) return
        screenClosed = true
        compose.runOnIdle { screenVisible.value = false }
        compose.waitForIdle()
        models.clear()
        scope.cancel()
        if (::player.isInitialized) player.release()
        assertSubscriptionSettled("the screen's Main scope finishes cancellation") { scope.coroutineContext[Job]!!.isCompleted }
    }

    protected val liveTvSurface get() = context.getString(at.bernhardberger.tvhplayer.R.string.player_live_tv_surface)

    protected fun focusableNodes() = compose.onAllNodes(isFocusable(), useUnmergedTree = true).fetchSemanticsNodes().size

    /** The live chrome's controls [behindLiveMs] behind the live edge with known timing. */
    protected fun liveChrome(behindLiveMs: () -> Long, state: () -> PlayerStateCell = { PlayerStateCell.PLAYING },
        numberEntryShown: () -> Boolean = { false }) {
        compose.setContent {
            TVHeadendPlayerTheme {
                PlayerChrome(
                    mode = PlayerChromeMode.CONTROLS,
                    content = PlayerChromeContent("20:15", liveInfoBarData(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources, 1, "One", null, null, false, 1_800, "Programme"), state = state()),
                    timeline = PlayerChromeTimeline.Live(
                        at.bernhardberger.tvhplayer.playback.AppTimeshiftState(available = true, bufferStartMs = -600_000,
                            positionMs = -behindLiveMs(), liveEdgeMs = 0, timingKnown = true,
                            paused = state() == PlayerStateCell.PAUSED),
                        nowSec = 1_800,
                    ),
                    actions = PlayerChromeActions(active = !numberEntryShown(), paused = state() == PlayerStateCell.PAUSED),
                    onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {},
                    onInteraction = {},
                    numberEntryShown = numberEntryShown(),
                )
            }
        }
        compose.mainClock.autoAdvance = false
        settle()
    }

    /** A timeshift channel playing at the live edge with Pause ready; [block] sees the play-when-ready changes. */
    protected fun timeshiftPlaying(onClose: () -> Unit = {}, mapPosition: Boolean = false,
        block: (pauses: List<Boolean>) -> Unit) {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, subscriptionDispatcher).apply { startAdmission() }
        try {
            screen(opener = manager, onClose = onClose)
            startLiveSubscription(connection, height = 1080L, signal = 53739L, timeshift = true)
            playerReady()
            assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
            playerListeners.toList().forEach { it.onRenderedFirstFrame() }
            assertMainSettled("the driven frame is presented") { runtime.videoPresentation.value.visible }
            // Media3's ExoPlayer playback thread binds the grant after collection registration.
            compose.waitUntil(5_000) { idleMainLooper(); runtime.livePause.value.availability == LivePauseAvailability.READY }
            if (mapPosition) {
                runTest(subscriptionDispatcher) {
                    // A displayed-content mapping is required to select a seek target.
                    connection.emit(SubscriptionEvent.Packet(
                        at.bernhardberger.tvheadend.sdk.playback.MuxFrameType.I, StreamIndex(1),
                        60_000_000L, 60_000_000L, 40_000L,
                        at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture(byteArrayOf(0, 0, 1, 9, 0x10)),
                    ))
                }
                settle()
                assertNotNull(sampleTimeshift().playbackTarget)
            }
            val pauses = mutableListOf<Boolean>()
            compose.runOnIdle {
                player.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { pauses += playWhenReady }
                })
            }
            block(pauses)
        } finally {
            runTest(subscriptionDispatcher) { manager.closeAndJoin() }
        }
    }

    protected fun removedConfirmation(held: Boolean) {
        var closes = 0
        screen(onClose = { closes++ })
        publishInfoRecording(DvrEntryState.RECORDING)
        key(Key.Info)
        compose.onNodeWithTag("details-stop").performSemanticsAction(SemanticsActions.RequestFocus)
        key(Key.DirectionCenter)
        compose.onNodeWithTag("two-column-first").assertIsFocused()
        compose.runOnUiThread {
            view.dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BACK))
            if (held) view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
                AndroidKeyEvent.KEYCODE_BACK, 1, 0, 0, 0, AndroidKeyEvent.FLAG_LONG_PRESS))
        }
        publishInfoRecording(DvrEntryState.COMPLETED)
        assertFalse("the DVR update removes the confirmation", exists("two-column-first"))
        assertTrue(exists("programme-details-page"))
        compose.runOnUiThread {
            if (held) view.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
                AndroidKeyEvent.KEYCODE_BACK, 2))
            view.dispatchKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_BACK))
        }
        settle()
        assertTrue("the vanished confirmation's release must not close Info", exists("programme-details-page"))
        assertEquals(0, closes)
        assertTrue(runtime.activeTarget.value != null)
        assertEquals(0, session.calls.count { it == FakeSessionCall.DVR_STOP_ENTRY })
    }

    protected fun holdStops(prepare: () -> Unit) {
        var closes = 0
        screen(onClose = { closes++ })
        prepare()
        assertEquals(0, closes)
        assertTrue(runtime.activeTarget.value != null)
        holdBack()
        assertEquals(1, closes)
        assertEquals(null, runtime.activeTarget.value)
    }

    protected fun holdGoesBack(prepare: () -> Unit, after: () -> Unit) {
        var closes = 0
        screen(onClose = { closes++ })
        prepare()
        val target = runtime.activeTarget.value
        val before = focused()
        holdBack {
            assertEquals(before, focused())
            assertEquals(0, closes)
            assertEquals(target, runtime.activeTarget.value)
        }
        assertEquals(0, closes)
        assertEquals(target, runtime.activeTarget.value)
        after()
    }

    protected fun holdBack(beforeRelease: () -> Unit = {}) {
        holdBack(::hardwareBack) {
            settleFrames()
            beforeRelease()
        }
        settle()
    }

    protected fun hardwareBack(down: Boolean, repeat: Int, flags: Int) = compose.runOnUiThread {
        val action = if (down) AndroidKeyEvent.ACTION_DOWN else AndroidKeyEvent.ACTION_UP
        view.dispatchKeyEvent(if (repeat == 0 && flags == 0) AndroidKeyEvent(action, AndroidKeyEvent.KEYCODE_BACK)
            else AndroidKeyEvent(0, 0, action, AndroidKeyEvent.KEYCODE_BACK, repeat, 0, 0, 0, flags))
    }

    protected fun startLiveSubscription(
        connection: ScriptedSubscriptionConnection,
        height: Long,
        signal: Long,
        timeshift: Boolean = false,
    ) {
        // Media3's real ExoPlayer playback thread registers the media period's collection.
        val registered = scope.async(start = CoroutineStart.UNDISPATCHED) { connection.awaitCollectionRegistered() }
        compose.waitUntil(5_000) { idleMainLooper(); registered.isCompleted }
        runTest(subscriptionDispatcher) {
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    protected fun liveStartFrames(zap: Boolean, pauseBeforeZap: Boolean = false) {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, subscriptionDispatcher).apply { startAdmission() }
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
                    assertTrue("the previous channel's server is paused", sampleTimeshift().paused)
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
            assertFalse("confirmed without timing", sampleTimeshift().timingKnown)
            runTest(subscriptionDispatcher) {
                connection.emit(SubscriptionEvent.Timeshift(60_000_000L, 0L, 0L, 60_000_000L, null, null))
                connection.emit(SubscriptionEvent.Packet(
                    at.bernhardberger.tvheadend.sdk.playback.MuxFrameType.I, StreamIndex(1),
                    60_000_000L, 60_000_000L, 40_000L,
                    at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture(byteArrayOf(0, 0, 1, 9, 0x10)),
                ))
            }
            frames(32)
            assertTrue("timing became known", sampleTimeshift().timingKnown)
        } finally {
            try { closeScreen() } finally { runTest(subscriptionDispatcher) { manager.closeAndJoin() } }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    protected fun screenZap(pauseDuringTune: Boolean = false, returnToSameChannel: Boolean = false,
        pauseBeforeZap: Boolean = false) {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, subscriptionDispatcher).apply { startAdmission() }
        val grantDispatcher = StandardTestDispatcher()
        val opener = SubscriptionOpener { channel, consumer, period ->
            val subscription = manager.open(channel, consumer, period)
            // Hold the new subscription's bind handoff independently of the forced READY callback.
            if (connection.subscribeCount > 1) withContext(grantDispatcher) { subscription } else subscription
        }
        val settingsData = GatedPreferences()
        try {
            screen(opener = opener, settingsData = settingsData, workDispatcher = subscriptionDispatcher)
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
            assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
            settle()
            if (pauseBeforeZap) {
                // Media3's ExoPlayer playback thread binds the grant after collection registration.
                compose.waitUntil(5_000) { idleMainLooper(); runtime.livePause.value.availability == LivePauseAvailability.READY }
                key(Key.MediaPause)
                assertTrue("A's server timeshift is paused", sampleTimeshift().paused)
                settingsData.open.value = false
            }
            if (returnToSameChannel) {
                // Media3's ExoPlayer playback thread binds the grant after collection registration.
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
                assertTrue(sampleTimeshift().paused)
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
            assertMainSettled("the key installs the selected channel") { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(channel)) }
            startLiveSubscription(connection, 720, 0)
            playerReady()
            assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
            // The source worker resumes after the gated opener on subscriptionDispatcher.
            // Media3 can still bind when its real playback thread creates the period, so wait
            // for the grant signal without advancing Compose or coroutine virtual time.
            compose.waitUntil(5_000) {
                grantDispatcher.scheduler.runCurrent()
                idleMainLooper()
                (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
                    ?.timeshiftState is LiveTimeshiftState.Available
            }
            settle()
            val unknown = sampleTimeshift()
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
                runTest(subscriptionDispatcher) {
                    connection.emit(SubscriptionEvent.Timeshift(60_000_000L, 0L, 0L, 60_000_000L, null, null))
                    // Establish the SDK's displayed-content mapping, not only its reader bounds.
                    connection.emit(SubscriptionEvent.Packet(
                        at.bernhardberger.tvheadend.sdk.playback.MuxFrameType.I, StreamIndex(1),
                        60_000_000L, 60_000_000L, 40_000L,
                        at.bernhardberger.tvheadend.sdk.testing.SubscriptionBinaryFixture(byteArrayOf(0, 0, 1, 9, 0x10)),
                    ))
                }
                settle()
                val known = sampleTimeshift()
                assertTrue("status supplies timing", known.timingKnown)
                assertTrue("real known timing has a target", known.playbackTarget != null)
                assertScreenProgramme(channel, "timing known")
                // The first status had no wall clock. A subsequent status gives the SDK the
                // mapping needed to resolve the committed programme window.
                runTest(subscriptionDispatcher) {
                    connection.emit(SubscriptionEvent.Timeshift(61_000_000L, 0L, 0L, 61_000_000L,
                        null, Instant.fromEpochMilliseconds(System.currentTimeMillis())))
                }
                compose.mainClock.advanceTimeBy(300)
                compose.waitForIdle()
                val mapped = sampleTimeshift()
                val window = programmeWindow(mapped) { time -> events.firstOrNull {
                    it.channelId == ChannelId(channel) && time >= it.start && time < it.stop
                } }
                assertEquals("committed window resolved", "Current $channel", window?.event?.title)
                assertScreenProgramme(channel, "window")
            }
        } finally {
            // Dispose request effects and release the media period before closing its manager,
            // including when an assertion interrupts a pending admission/subscription.
            try { closeScreen() } finally { runTest(subscriptionDispatcher) { manager.closeAndJoin() } }
        }
    }

    protected fun assertUnknownProgramme() {
        val info = compose.onNodeWithTag("player-info-bar").fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.joinToString(" ")
        assertTrue("unknown playback does not claim the airing programme: $info", info.contains("No program information"))
        assertFalse("unknown playback has no Next: $info", info.contains("Next 1") || info.contains("Next 2"))
    }

    protected fun assertScreenProgramme(channel: Long, phase: String) {
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

    protected fun description(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()

    /** The state live TV announces, live or behind live; null while unknown. */
    protected fun liveState(): String? = compose.onNodeWithTag("player-live-state", useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    protected fun texts(tag: String): String =
        compose.onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
            .joinToString(" | ")

    protected fun publishMatchingFutureProgramme() {
        val now = System.currentTimeMillis() / 1_000L
        val events = (session.observation.value.epgState as EpgRepositoryState.Current).snapshot.events
        session.publish(observation(now, listOf(events.first(), EpgEvent.create(EventId(12), ChannelId(1),
            Instant.fromEpochSeconds(now + 3_600), Instant.fromEpochSeconds(now + 7_200), title = "Now showing"))))
        settle()
    }

    protected fun openRows() {
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        assertEquals(listOf("player-channel-card-1"), focused())
    }

    protected fun confirmInfoAction(destructive: Boolean = false) {
        compose.onNodeWithTag("two-column-first").assertIsFocused()
        compose.onNode(isDialog()).performKeyInput {
            if (destructive) pressKey(Key.DirectionRight)
            pressKey(Key.DirectionCenter)
        }
        settle()
    }

    protected fun publishInfoRecording(state: DvrEntryState, eventId: EventId = EventId(11)) {
        val current = session.observation.value.epgState as EpgRepositoryState.Current
        session.publish(observation(System.currentTimeMillis() / 1_000, current.snapshot.events,
            recordings = listOf(DvrEntry.create(DvrEntryId(7), eventId = eventId, state = state))))
        settle()
    }

    protected fun publishRailProgrammes() {
        val now = System.currentTimeMillis() / 1_000L
        session.publish(observation(now, listOf(
            EpgEvent.create(EventId(21), ChannelId(2), Instant.fromEpochSeconds(now - 600),
                Instant.fromEpochSeconds(now + 600), title = "Second channel now"),
            EpgEvent.create(EventId(22), ChannelId(2), Instant.fromEpochSeconds(now + 600),
                Instant.fromEpochSeconds(now + 1800), title = "Second channel later"),
        )))
        settle()
    }

    protected fun idleMainLooper() {
        subscriptionDispatcher.scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        subscriptionDispatcher.scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
    }

    protected fun assertSubscriptionSettled(message: String, condition: () -> Boolean) {
        idleMainLooper()
        assertTrue(message, condition())
    }

    protected fun sampleTimeshift(): AppTimeshiftState {
        var sample: AppTimeshiftState? = null
        // The coordinator samples Media3's real playback thread; subscription commands use our scheduler.
        runTest(subscriptionDispatcher) { sample = runtime.sampleTimeshiftPresentation() }
        return requireNotNull(sample)
    }

    /** Live's controls name the channel and its badges in the top identity's description. */
    protected fun identityDescription(): String = compose.onNodeWithTag(PlayerTopIdentityTag, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" | ")

    protected fun badgeDescription(tag: String): String =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            .joinToString(" | ")

    protected fun playerReady() {
        forcedReady = true
        compose.runOnIdle {
            playerListeners.toList().forEach {
                it.onPlaybackStateChanged(Player.STATE_READY)
                it.onIsPlayingChanged(true)
            }
        }
        compose.waitForIdle()
    }

    protected fun audioTrack(id: String, language: String, selected: Boolean) = Tracks.Group(TrackGroup(Format.Builder()
        .setId(id).setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage(language).setChannelCount(2).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))

    protected fun subtitleTrack(id: String, language: String) = Tracks.Group(TrackGroup(Format.Builder()
        .setId(id).setSampleMimeType(MimeTypes.APPLICATION_DVBSUBS).setLanguage(language).build()),
        false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))

    protected fun publishTracks(tracks: Tracks) {
        forcedTracks = tracks
        compose.runOnIdle { playerListeners.toList().forEach { it.onTracksChanged(tracks) } }
        // A few frames only: the controls' hide timer must not run out in between.
        settleFrames()
    }

    protected fun exists(tag: String) =
        compose.tagExists(tag)

    /** The closed rail stays composed to peek, cleared from what accessibility reads; the merged tree respects that. */
    protected fun railOpen() = compose.onAllNodes(hasTestTag("player-channel-browser")).fetchSemanticsNodes().isNotEmpty()

    /** Tags of the nodes an accessibility service can activate, adjust or run custom actions on. */
    protected fun actionable(): List<String> = compose.onAllNodes(SemanticsMatcher("actionable") { node ->
        listOf(SemanticsActions.OnClick, SemanticsActions.CustomActions, SemanticsActions.SetProgress).any { it in node.config }
    }).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) ?: "untagged ${it.id}" }

    protected fun focused(): List<String> =
        compose.focusedTags()

    protected fun key(key: Key) {
        compose.pressKey(key, settleFrames = 0)
        settle()
    }

    /** Presses [key] and lets a few frames pass, short of any entry timer. */
    protected fun press(key: Key) {
        compose.pressKey(key, settleFrames = 0)
        settleFrames()
    }

    protected fun settleFrames() = repeat(3) { idleMainLooper(); compose.settleFrames(1, frameMillis = 16) }

    protected fun settle() {
        repeat(20) {
            idleMainLooper()
            compose.settleFrames(1)
        }
    }

    protected fun observation(
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

    protected fun screen(
        onClose: () -> Unit = {},
        failing: Boolean = false,
        fontScale: Float = 1f,
        epg: Boolean = true,
        opener: SubscriptionOpener? = null,
        recordingScheduled: Boolean = false,
        recordingProfile: DvrConfigId? = null,
        settingsData: DataStore<Preferences> = InMemoryPreferencesDataStore(),
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
        workDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
        val uiSettings = at.bernhardberger.tvhplayer.settings.UiSettingsStore(InMemoryPreferencesDataStore())
        recordingProfile?.let { runTest { uiSettings.setRecordingProfile(it) } }
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
        val profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.Main.immediate,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryPreferencesDataStore()), profiles.liveProfile)
        models.put("channels", channels)
        assertMainSettled("the fake channel catalog is collected") { channels.scope.value.scope.visibleChannels.size == if (missingRequestedChannel) 2 else 3 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player, workDispatcher = workDispatcher).also { it.launchIn(scope) }
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
            PlaybackRuntimePolicy.fromPlayerSettings(object : PlaybackTrace {}),
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
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
                        VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), TargetAspect(AspectRatioMode.FIT) {},
                            channels, screenSession, ChannelId(1), "Name 1", {}, onClose, runtime,
                            contentAllowed = contentAllowed(), notices = notices, uiSettingsStore = uiSettings,
                            startupTarget = if (startupEnabled()) ApplianceLaunchTarget(ApplianceLaunchRequest(startupRequestId()), ChannelId(1), "Name 1") else null,
                            onStartupOutcome = { _, outcome -> onStartupOutcome(outcome); acceptStartupOutcome() })
                    } }
                }
            }
        }
        if (settleOnEntry) {
            compose.mainClock.advanceTimeUntil {
                idleMainLooper()
                runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1))
            }
            settle()
            compose.mainClock.autoAdvance = false
            settle()
        }
    }

    protected class GatedPreferences(
        private val store: DataStore<Preferences> = InMemoryPreferencesDataStore(),
    ) : DataStore<Preferences> by store {
        val open = MutableStateFlow(true)
        override val data: Flow<Preferences> = flow {
            open.first { it }
            emitAll(store.data)
        }
    }
}
