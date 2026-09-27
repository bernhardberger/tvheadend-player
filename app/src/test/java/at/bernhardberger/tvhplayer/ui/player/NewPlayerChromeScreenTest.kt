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
import android.os.Looper
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    @Test fun newBannerHidesFiveSecondsAfterThisTunesFirstFrame() {
        screen(PlayerChromeDesign.NEW)
        playerReady()
        compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
        // Playing without a picture: no video diagnostics does not prove audio-only.
        repeat(4) { settle() }
        assertTrue("no frame of this tune yet: the Banner stays", exists("trial-banner"))

        playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        compose.waitUntil(5_000) { runtime.videoPresentation.value.visible }
        compose.mainClock.advanceTimeBy(4_800)
        compose.waitForIdle()
        assertTrue("before 5000 ms after the first frame", exists("trial-banner"))
        settle()
        assertFalse("5000 ms after the first frame the Banner hides", exists("trial-banner"))
        assertFalse(exists("player-actions"))
    }

    @Test fun newAudioOnlyServiceStartsTheBannerTimerWhenPlaying() {
        screen(PlayerChromeDesign.NEW)
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
        assertTrue(exists("trial-banner"))
        repeat(2) { settle() }
        assertFalse("audio-only: playing starts the Banner's 5 s", exists("trial-banner"))
    }

    @Test fun aStoppedAndRestartedScreenShowsNoStaleBanner() {
        screen(PlayerChromeDesign.NEW)
        assertTrue(exists("trial-banner"))
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        settle()
        assertFalse("the Banner does not outlive the stop", exists("trial-banner"))
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        repeat(8) { settle() }
        assertFalse("no stale Banner on return", exists("trial-banner"))
        assertFalse(exists("player-actions"))
    }

    @Test fun detailsSurviveAProgrammeBoundaryAndRecordAvailabilityChange() {
        screen(PlayerChromeDesign.NEW)
        openStreamSignalDetails()
        val now = System.currentTimeMillis() / 1_000L
        // Compose reports a focus request on a detached Info-page requester (which older
        // Compose versions threw) as a "not initialized" warning on stdout; record it.
        val stdout = System.out
        val captured = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(object : java.io.OutputStream() {
            override fun write(b: Int) { captured.write(b); stdout.write(b) }
        }, true))
        try {
            // The airing programme changes while the details page is shown.
            session.publish(observation(now, listOf(
                EpgEvent.create(EventId(21L), ChannelId(1L), Instant.fromEpochSeconds(now - 60L),
                    Instant.fromEpochSeconds(now + 3_600L), title = "Next up", summary = "Summary"),
            )))
            repeat(2) { settle() }
            assertTrue(exists("live-info-stream-signal-details"))
            // Recording becomes unavailable while the details page is shown.
            session.publish(observation(now, listOf(
                EpgEvent.create(EventId(21L), ChannelId(1L), Instant.fromEpochSeconds(now - 60L),
                    Instant.fromEpochSeconds(now + 3_600L), title = "Next up", summary = "Summary"),
            ), dvrWrite = CapabilityAccess.DENIED))
            repeat(2) { settle() }
            assertTrue(exists("live-info-stream-signal-details"))
        } finally {
            System.setOut(stdout)
        }
        assertFalse("no focus request reaches the detached Info page",
            captured.toString().contains("FocusRequester is not initialized"))
        assertTrue("details keep focus",
            compose.onAllNodes(hasAnyAncestor(hasTestTag("live-info-stream-signal-details")) and isFocused(), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty())

        key(Key.Back)
        assertTrue(exists("live-info-panel"))
        assertEquals(listOf("live-info-stream-signal"), focused())
    }

    @Test fun withoutEpgStreamAndSignalIsStillReachable() {
        screen(PlayerChromeDesign.NEW, epg = false)
        key(Key.Info)
        assertTrue(exists("live-info-panel"))
        openStreamSignalDetails(alreadyOpen = true)
        key(Key.Back)
        assertTrue(exists("live-info-panel"))
        assertEquals(listOf("live-info-stream-signal"), focused())
    }

    @Test fun newBadgesComeFromTheSelectedTracksAndTheLiveSnrWithoutDiagnostics() {
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, null)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        try {
            screen(PlayerChromeDesign.NEW, opener = manager)
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
            // The observation arrives from a playback thread; its collectors run on the paused main looper.
            compose.waitUntil(5_000) { idleMainLooper(); video.liveSnr.value != null }
            val diagnostics = runtime.diagnostics.value
            assertTrue("Stats and details are closed: no format or frontend diagnostics",
                diagnostics.video == null && diagnostics.audio == null && diagnostics.live == null)
            // Badges describe only the confirmed playing channel.
            playerReady()
            compose.waitUntil(5_000) { runtime.state.value is AppPlaybackState.Playing }
            settle()
            assertTrue(exists("trial-banner"))
            val badges = badgeDescription("trial-banner")
            for (expected in listOf("1080 lines", "Video H.264", "Audio DD+ 5.1", "Signal-to-noise ratio 82 percent")) {
                assertTrue("Banner badges '$badges' contain '$expected'", badges.contains(expected))
            }

            key(Key.DirectionCenter)
            assertTrue(exists("player-actions"))
            val controls = badgeDescription("trial-info-bar")
            for (expected in listOf("1080 lines", "Video H.264", "Audio DD+ 5.1", "Signal-to-noise ratio 82 percent")) {
                assertTrue("controls badges '$controls' contain '$expected'", controls.contains(expected))
            }

            // The details page needs full diagnostics, exactly like Stats for nerds; closing it ends them.
            key(Key.Info)
            openStreamSignalDetails(alreadyOpen = true)
            compose.waitUntil(5_000) { idleMainLooper(); runtime.diagnostics.value.live != null }
            key(Key.Back)
            assertFalse(exists("live-info-stream-signal-details"))
            compose.waitUntil(5_000) { idleMainLooper(); runtime.diagnostics.value.live == null }
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
            screen(PlayerChromeDesign.NEW, opener = manager)
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
            val badgesA = badgeDescription("trial-banner")
            for (expected in listOf("1080 lines", "Video H.264", "Signal-to-noise ratio 82 percent")) {
                assertTrue("A's badges '$badgesA' contain '$expected'", badgesA.contains(expected))
            }

            // Zap to B: the player still reports A's tracks, A's diagnostics and A's retained SNR.
            forcedReady = false
            key(Key.ChannelUp)
            assertTrue(exists("trial-banner"))
            val staleBanner = badgeDescription("trial-banner")
            for (stale in listOf("lines", "Video", "Audio", "Signal-to-noise")) {
                assertFalse("B's Banner '$staleBanner' shows no badge ('$stale')", staleBanner.contains(stale))
            }
            key(Key.Info)
            assertTrue("Stream & signal stays reachable", exists("live-info-stream-signal"))
            val staleInfo = texts("live-info-stream-signal")
            for (stale in listOf("1080", "H.264", "DD+", "SNR")) {
                assertFalse("B's Info summary '$staleInfo' shows no '$stale'", staleInfo.contains(stale))
            }
            // Opening the details samples the installed player, which still plays A.
            openStreamSignalDetails(alreadyOpen = true)
            compose.waitUntil(5_000) { idleMainLooper(); runtime.diagnostics.value.video?.height == 1080 }
            settle()
            val staleDetails = texts("live-info-stream-signal-details")
            for (stale in listOf("1920", "1080", "H.264", "Dolby", "82")) {
                assertFalse("B's details '$staleDetails' show no '$stale'", staleDetails.contains(stale))
            }
            assertTrue("B's details '$staleDetails' say no details yet", staleDetails.contains("No stream details available"))
            key(Key.Back)
            assertTrue("Back returns to Info", exists("live-info-panel"))
            key(Key.Back)
            assertFalse(exists("live-info-panel"))
            // Closing the details turned diagnostics off again; the waits below read them.
            video.setDiagnosticsEnabled(true)

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
            key(Key.Info)
            val infoB = texts("live-info-stream-signal")
            for (expected in listOf("720", "H.264", "SNR", "50")) {
                assertTrue("B's Info summary '$infoB' contains '$expected'", infoB.contains(expected))
            }
            assertFalse(infoB.contains("1080"))
            openStreamSignalDetails(alreadyOpen = true)
            compose.waitUntil(5_000) { idleMainLooper(); texts("live-info-stream-signal-details").contains("50") }
            val detailsB = texts("live-info-stream-signal-details")
            for (stale in listOf("1920", "Dolby", "82")) {
                assertFalse("B's details '$detailsB' show no '$stale'", detailsB.contains(stale))
            }
            assertTrue("B's details '$detailsB' show B's picture", detailsB.contains("1280"))
            key(Key.Back)
            assertTrue("Back returns to Info", exists("live-info-panel"))
            key(Key.Back)
            assertTrue(exists("trial-info-bar"))
            val badgesB = badgeDescription("trial-info-bar")
            for (expected in listOf("720 lines", "Video H.264", "Signal-to-noise ratio 50 percent")) {
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
        val registered = scope.async(Dispatchers.Default) { connection.awaitCollectionRegistered() }
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

    private fun texts(tag: String): String =
        compose.onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
            .joinToString(" | ")

    @Test fun newInfoStartsOnRecordAndDownReachesStreamAndSignal() {
        screen(PlayerChromeDesign.NEW)
        key(Key.Info)
        assertEquals("New: the first action takes focus", listOf("live-info-record"), focused())
        key(Key.DirectionDown)
        assertEquals("one Down from the actions reaches Stream & signal", listOf("live-info-stream-signal"), focused())
        key(Key.DirectionUp)
        assertEquals(listOf("live-info-record"), focused())
        key(Key.DirectionUp)
        assertEquals("the description stays reachable with Up", listOf("player-info-reading"), focused())
    }

    @Test fun newInfoStartsOnCloseWithoutRecording() {
        // The airing programme is already scheduled, so Record is not offered.
        screen(PlayerChromeDesign.NEW, recordingScheduled = true)
        key(Key.Info)
        assertEquals(listOf("live-info-close"), focused())
        key(Key.DirectionDown)
        assertEquals(listOf("live-info-stream-signal"), focused())
    }

    @Test fun currentInfoStillStartsOnTheDescription() {
        screen(PlayerChromeDesign.CURRENT)
        key(Key.Info)
        assertEquals(listOf("player-info-reading"), focused())
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()

    private fun badgeDescription(tag: String): String =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
            .joinToString(" | ")

    @Test fun backDuringTheOkGraceBehavesAsWithTheBannerHiddenAndEndsTheGrace() = runTest {
        val state = LivePlayerLayerState(this, 5_000L)
        state.enableBanner(true)
        state.onBannerFramePresented(true)
        testScheduler.advanceTimeBy(5_001L)
        testScheduler.runCurrent()
        assertTrue(state.bannerTakesOk)
        var backs = 0
        val consumed = state.handleOverlayKey(
            event = androidx.compose.ui.input.key.KeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK),
            ),
            keyContext = PlayerKeyContext(
                surface = PlayerSurface.LIVE,
                controlsVisible = false, seekbarFocused = false, timeshiftAvailable = false,
            ),
            foregroundLayer = PlayerForegroundLayer.NONE,
            quickListAvailable = true,
            onBack = { backs++ },
            onOptionsOpened = {},
        )
        assertEquals(true, consumed)
        assertEquals("Back is handled as before", 1, backs)
        assertFalse("Back ends the grace", state.bannerTakesOk)
        state.dispose()
    }

    @Test fun aChannelNumberEntryEndsTheBannerOkGrace() {
        // Live Pause is available, so OK with hidden chrome pauses; the Banner's OK does not.
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, 3_600L)))
        }
        val manager = createSubscriptionManager(connection, Dispatchers.Default).apply { startAdmission() }
        try {
            screen(PlayerChromeDesign.NEW, opener = manager)
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
            compose.mainClock.advanceTimeBy(4_800)
            compose.waitForIdle()
            // Stop within 100 ms of the timeout hide, well inside the 500 ms grace.
            var steps = 0
            while (exists("trial-banner") && steps < 10) {
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
                steps++
            }
            assertFalse("the Banner timed out", exists("trial-banner"))
            assertFalse(exists("player-actions"))

            // Within the grace: a number no channel has, committed with OK, tunes nothing.
            compose.onRoot().performKeyInput {
                pressKey(Key.Nine)
                pressKey(Key.DirectionCenter)
            }
            compose.waitForIdle()
            idleMainLooper()
            assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
            assertFalse("the dismissed entry reveals nothing", exists("player-actions"))
            assertTrue("the dismissed entry pauses nothing", pauses.isEmpty())

            // Still inside the Banner's 500 ms, OK now acts as with hidden chrome: it pauses.
            compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
            compose.waitForIdle()
            assertTrue(exists("player-actions"))
            settle()
            val paused = runCatching { compose.waitUntil(3_000) { idleMainLooper(); false in pauses } }.isSuccess
            assertTrue("OK after a number entry is not the Banner's OK: it paused ($pauses)", paused)
        } finally {
            runBlocking { manager.closeAndJoin() }
        }
    }

    @Test fun channelUpBrowsesTheOpenNewRailInsteadOfTuning() {
        screen(PlayerChromeDesign.NEW)
        openRail()
        assertEquals(listOf("player-channel-card-1"), focused())
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        assertTrue("the rail stays open", exists("player-channel-shelf"))
        assertEquals(listOf("player-channel-card-2"), focused())
    }

    @Test fun channelUpTunesWithTheNewRailClosed() {
        screen(PlayerChromeDesign.NEW)
        key(Key.ChannelUp)
        assertEquals(AppPlaybackTarget.Live(ChannelId(2)), runtime.activeTarget.value)
    }

    @Test fun channelUpStillTunesWithTheCurrentRailOpen() {
        screen(PlayerChromeDesign.CURRENT)
        openRail()
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

    private fun openStreamSignalDetails(alreadyOpen: Boolean = false) {
        if (!alreadyOpen) key(Key.Info)
        assertTrue(exists("live-info-stream-signal"))
        var presses = 0
        while (focused() != listOf("live-info-stream-signal") && presses < 8) {
            key(Key.DirectionDown)
            presses++
        }
        assertEquals("Stream & signal is reachable by D-pad Down", listOf("live-info-stream-signal"), focused())
        key(Key.DirectionCenter)
        assertTrue(exists("live-info-stream-signal-details"))
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

    private fun observation(
        now: Long,
        events: List<EpgEvent>,
        dvrWrite: CapabilityAccess = CapabilityAccess.ALLOWED,
        recordings: List<DvrEntry> = emptyList(),
    ) =
        SessionObservation.create(
            sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = dvrWrite)),
            channelState = ChannelRepositoryState.Current(ChannelCatalog.create(
                (1..3L).map { Channel.create(ChannelId(it), name = "Name $it", number = it) },
            )),
            epgState = EpgRepositoryState.Current(EpgSnapshot.create(events = events)),
            dvrState = DvrRepositoryState.Current(DvrSnapshot.create(entries = recordings)),
        )

    private fun screen(
        design: PlayerChromeDesign,
        onClose: () -> Unit = {},
        failing: Boolean = false,
        fontScale: Float = 1f,
        epg: Boolean = true,
        opener: SubscriptionOpener? = null,
        recordingScheduled: Boolean = false,
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
        val settings = PlayerSettingsStore(InMemoryData())
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
