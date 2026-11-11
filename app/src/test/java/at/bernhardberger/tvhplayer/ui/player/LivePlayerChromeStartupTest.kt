@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)


package at.bernhardberger.tvhplayer.ui.player

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import at.bernhardberger.tvhplayer.testutil.assertMainSettled
import androidx.compose.ui.test.hasContentDescription
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ChannelCatalog
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.ChannelRepositoryState
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.SessionState
import at.bernhardberger.tvheadend.sdk.media3.PlaybackTargetResult
import at.bernhardberger.tvhplayer.core.ApplianceLaunchRequests
import at.bernhardberger.tvhplayer.core.ApplianceLaunchState
import at.bernhardberger.tvhplayer.core.CurrentChannelReadiness
import at.bernhardberger.tvhplayer.core.WarmReturnOpportunity
import at.bernhardberger.tvhplayer.core.MainStartupPlaybackOutcome
import at.bernhardberger.tvhplayer.playback.AppPlaybackState
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.currentLivePlaybackSelection
import at.bernhardberger.tvhplayer.ui.AppRootPlaybackOrchestrator
import at.bernhardberger.tvhplayer.ui.performMainStartupBack
import at.bernhardberger.tvhplayer.ui.HomeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "en-w960dp-h540dp-land-mdpi")
class LivePlayerChromeStartupTest : LivePlayerChromeFixture() {
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
        assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
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
        // Media3's real ExoPlayer playback thread reports the undecodable stream's failure.
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            outcomes.contains(MainStartupPlaybackOutcome.RECOVERY)
        }
        assertFalse(exists("player-banner"))
    }

    @Test fun startupWarmEntryAdoptsPlayingMatchingTuneWithoutAnotherFrameOrRetune() {
        val requestId = mutableStateOf(1L)
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(contentAllowed = { false }, startup = true, startupRequestId = { requestId.value },
            onStartupOutcome = { outcomes += it })
        playerReady()
        settle()
        assertMainSettled("the driven player reports Playing") { runtime.state.value is AppPlaybackState.Playing }
        compose.runOnIdle { playerListeners.toList().forEach { it.onRenderedFirstFrame() } }
        settle()
        assertEquals(listOf(MainStartupPlaybackOutcome.PRESENTED), outcomes)
        for (freshIntent in listOf(false, true)) {
            outcomes.clear()
            val epoch = runtime.videoPresentation.value.epoch
            assertTrue(runtime.videoPresentation.value.visible)
            assertTrue(outcomes.isEmpty())
            if (freshIntent) {
                compose.runOnIdle {
                    runtime.notePlaybackIntent() // The launch owner notes new intent before resolving this route.
                    requestId.value = 2L
                }
            } else {
                compose.runOnIdle { screenVisible.value = false }
                settle()
                compose.runOnIdle { screenVisible.value = true }
            }
            settle()
            compose.waitUntil(5_000) { outcomes.contains(MainStartupPlaybackOutcome.ALREADY_PLAYING) }
            assertEquals("warm adoption does not install another tune", epoch, runtime.videoPresentation.value.epoch)
            assertTrue("the already-presented frame is retained", runtime.videoPresentation.value.visible)
        }
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
                assertEquals(HomeKey, destination)
                screenVisible.value = false
            }
            assertEquals(ApplianceLaunchState.Idle, requests.state.value)
        }
        settle()
        assertMainSettled("startup cancellation retires its tune") { runtime.state.value is AppPlaybackState.Idle }
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
        assertEquals(AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
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
        assertMainSettled("the retry returns its result") { results.isNotEmpty() }
        assertEquals(listOf(PlaybackTargetResult.NOT_READY), results)
    }

    @Test fun backgroundCancellationAtCommitBeforeReceiptRetiresTheUndeliveredStartupTune() {
        val outcomes = mutableListOf<MainStartupPlaybackOutcome>()
        screen(settleOnEntry = false, contentAllowed = { false }, startup = true,
            afterTargetPlay = { owner.lifecycle.currentState = Lifecycle.State.CREATED },
            onStartupOutcome = { outcomes += it })
        settle()
        assertMainSettled("startup cancellation retires its tune") { runtime.state.value is AppPlaybackState.Idle }
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
        assertMainSettled("startup cancellation retires its tune") { runtime.state.value is AppPlaybackState.Idle }
        assertEquals(null, runtime.activeTarget.value)
        assertFalse(player.playWhenReady)
    }
}
