@file:androidx.media3.common.util.UnstableApi
@file:OptIn(at.bernhardberger.tvheadend.sdk.testing.FakePlaybackApi::class,
    at.bernhardberger.tvheadend.sdk.playback.SubscriptionInfrastructureApi::class)

package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvhplayer.testutil.awaitScheduledWork

import kotlinx.coroutines.flow.onStart
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOpener
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionConfirmation
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionOperationResult
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionEvent
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionCondition
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionIssue
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStream
import at.bernhardberger.tvheadend.sdk.playback.SubscriptionStreamType
import at.bernhardberger.tvheadend.sdk.playback.StreamIndex
import at.bernhardberger.tvheadend.sdk.playback.createSubscriptionManager
import at.bernhardberger.tvheadend.sdk.testing.ScriptedSubscriptionConnection
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.awaitCancellation

import at.bernhardberger.tvhplayer.testutil.settleFrames
import at.bernhardberger.tvhplayer.testutil.pressKey
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
import at.bernhardberger.tvhplayer.settings.InMemoryPreferencesDataStore
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
import at.bernhardberger.tvhplayer.settings.AspectRatioMode
import at.bernhardberger.tvhplayer.settings.AppProfileOwner
import at.bernhardberger.tvhplayer.settings.ChannelTagSettingsStore
import at.bernhardberger.tvhplayer.settings.PlayerSettingsStore
import at.bernhardberger.tvhplayer.settings.UiSettingsStore
import at.bernhardberger.tvhplayer.stores.ChannelSelectionStore
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.testutil.ImageLoaderRule
import at.bernhardberger.tvhplayer.testutil.assertMainSettled
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
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }
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
        compose.pressKey(Key.Back)
        assertEquals(1, closes)
        session.publish(ready())
        compose.settleFrames(10)
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
        compose.settleFrames(10)
        assertEquals(0, unconsumed)
        assertEquals(1, waits())
        assertEquals(0, closes)
        compose.pressKey(Key.Back)
        assertEquals(1, closes)
    }

    @Test fun aConfirmHeldWhenTheSessionArrivesCannotOpenTheControls() {
        screen()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        session.publish(ready())
        compose.settleFrames(10)
        assertMainSettled("the waiting start installs when the session arrives") { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        compose.onRoot().performKeyInput { advanceEventTime(800) }
        compose.settleFrames(2)
        assertEquals("held Confirm repeats must not dismiss Starting", 1, waits())
        compose.runOnIdle { playerListeners.toList().forEach { it.onRenderedFirstFrame() } }
        compose.settleFrames(4)
        assertEquals(0, waits())
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        compose.settleFrames(10)
        assertEquals(0, unconsumed)
        assertEquals(0, compose.onAllNodes(hasTestTag("player-actions"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun channelStartupConsumesKeysExceptSystemAndBack() {
        screen()
        for (connected in listOf(false, true)) {
            if (connected) {
                session.publish(ready())
                compose.settleFrames(10)
                assertMainSettled("the first target is installed") {
                    runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1))
                }
            }
            val target = runtime.activeTarget.value
            val count = binds()
            val playing = player.playWhenReady
            for ((key, passes) in listOf(
                Key.VolumeUp to true, Key.VolumeDown to true, Key.VolumeMute to true,
                Key.Power to true, Key.SystemHome to true, Key.MediaPlayPause to false, Key.MediaPlay to false,
                Key.MediaPause to false, Key.MediaFastForward to false, Key.MediaRewind to false,
                Key.MediaStop to false, Key.Guide to false, Key.ChannelUp to false, Key.ChannelDown to false,
                Key.Two to false, Key.DirectionUp to false, Key.DirectionDown to false,
                Key.DirectionLeft to false, Key.DirectionRight to false, Key.DirectionCenter to false,
                Key.Info to false, Key.Menu to false, Key.Unknown to false,
            )) {
                val before = unconsumed
                compose.onRoot().performKeyInput {
                    keyDown(key)
                    advanceEventTime(800)
                    keyUp(key)
                }
                assertEquals("$key passes only system keys", passes, unconsumed > before)
                assertEquals("$key keeps the channel startup surface", 1, waits())
                assertEquals("$key issues no playback command", playing, player.playWhenReady)
                assertEquals("$key starts no new subscription", count, binds())
                assertEquals(target, runtime.activeTarget.value)
                compose.onNodeWithTag("player-actions").assertDoesNotExist()
                compose.onNodeWithTag("playback-options-title").assertDoesNotExist()
            }
        }
        compose.pressKey(Key.Back)
        assertEquals(1, closes)
    }

    @Test fun aSubscriptionIssueReleasesStartingToTheChannelMessage() {
        val dispatcher = StandardTestDispatcher()
        val connection = ScriptedSubscriptionConnection().apply {
            scriptSubscribe(SubscriptionOperationResult.Ok(SubscriptionConfirmation(null, null, null, null)))
        }
        val manager = createSubscriptionManager(connection, dispatcher).apply { startAdmission() }
        try {
            screen(opener = manager)
            session.publish(ready())
            compose.settleFrames(10)
            assertMainSettled("the target is installed") { runtime.activeTarget.value != null }
            compose.awaitScheduledWork(dispatcher) {
                connection.subscribeCount > 0
            }
            runTest(dispatcher) {
                connection.awaitCollectionRegistered()
                connection.emit(SubscriptionEvent.Started(listOf(
                    SubscriptionStream(index = StreamIndex(1), type = SubscriptionStreamType.H264,
                        language = null, compositionId = null, ancillaryId = null, width = 320, height = 240,
                        frameDuration = null, aspectNumerator = null, aspectDenominator = null, audioType = null,
                        audioVersion = null, channelCount = null, rate = null, rdsUecp = null),
                ), null))
            }
            compose.settleFrames(4)
            runTest(dispatcher) {
                connection.emit(SubscriptionEvent.Status(SubscriptionCondition.STATUS_REPORTED, SubscriptionIssue.NO_FREE_ADAPTER))
            }
            compose.settleFrames(10)
            dispatcher.scheduler.runCurrent()
            compose.settleFrames(4)
            assertEquals("the SDK published the issue", SubscriptionIssue.NO_FREE_ADAPTER,
                (runtime.livePlaybackObservation.value as? at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation.Active)
                    ?.subscriptionIssue)
            assertEquals("a server issue is not masked by Starting", 0, waits())
            compose.onNodeWithTag("player-channel-unavailable", useUnmergedTree = true).assertExists()
            runTest(dispatcher) {
                connection.emit(SubscriptionEvent.Status(SubscriptionCondition.NO_DETAIL, null))
            }
            compose.settleFrames(4)
            assertEquals("a cleared issue returns to the pending picture", 1, waits())
            session.publish(SessionObservation.create(
                sessionState = SessionState.Unavailable(at.bernhardberger.tvheadend.sdk.core.SessionFailure.AUTHENTICATION_REJECTED),
                channelState = ChannelRepositoryState.Stale(catalog),
            ))
            compose.runOnIdle {
                this@LivePlayerServerWaitTest.connection.value = ConnectionUiState.Error(
                    ConnectionFailureKind.AUTHENTICATION, SessionRecoveryDisposition.PROFILE_CHANGE_REQUIRED)
            }
            compose.settleFrames(4)
            compose.onNodeWithTag("tv-recovery-primary").assertExists()
        } finally {
            runTest(dispatcher) { manager.closeAndJoin() }
        }
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
        screen(initial = ready(), appInitiated = false)
        compose.runOnIdle {
            scope.launch { runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))) }
        }
        assertMainSettled("the warm target is installed") { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        compose.settleFrames(10)
        val before = binds()
        // The connection is lost while this screen is away; a new screen opens with the target still active.
        compose.runOnIdle { visible.value = false }
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog)))
        compose.settleFrames(10)
        compose.runOnIdle { visible.value = true }
        compose.settleFrames(10)
        assertEquals(1, waits())
        assertMainSettled("the recreated in-app screen clears the lost target before reconnect") {
            runtime.activeTarget.value == null
        }
        session.publish(ready())
        compose.settleFrames(10)
        assertMainSettled("the reconnect binds a new stream") { binds() > before }
    }

    @Test fun aFailedEntryWaitsForANewSessionInsteadOfRetryingCatalogUpdates() {
        screen()
        session.scriptLivePlaybackFailure(at.bernhardberger.tvheadend.sdk.core.PlaybackBindingResult.TargetUnavailable)
        session.publish(ready())
        compose.waitForIdle()
        assertEquals("the failed entry is attempted once", 1, binds())
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertEquals("re-keying the failed entry does not retry the same selection", 1, binds())
        session.publish(ready())
        compose.waitForIdle()
        assertEquals("catalog updates do not retry a failed entry", 1, binds())
        val retryEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finishRetry = kotlinx.coroutines.CompletableDeferred<Unit>()
        val retryCancelled = kotlinx.coroutines.CompletableDeferred<Unit>()
        beforeSettingsRead = {
            retryEntered.complete(Unit)
            try {
                finishRetry.await()
            } finally {
                retryCancelled.complete(Unit)
            }
        }
        session.replaceGeneration(session.observation.value)
        compose.waitForIdle()
        assertMainSettled("the new-session start reaches settings before installation") { retryEntered.isCompleted }
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertMainSettled("backgrounding cancels the pending start") { retryCancelled.isCompleted }
        assertMainSettled("the cancelled start settles before the next foreground") {
            runtime.state.value is at.bernhardberger.tvhplayer.playback.AppPlaybackState.Idle
        }
        assertNull(runtime.activeTarget.value)
        assertNotNull(runtime.videoPresentation.value.channelStartAtMillis)
        beforeSettingsRead = {}
        session.scriptLivePlaybackSuccess()
        finishRetry.complete(Unit)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitForIdle()
        assertMainSettled("foreground return restarts the same session's cancelled start") {
            runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1))
        }
        assertEquals("a new session retries the channel", 2, binds())
    }

    @Test fun aProfileSavedDuringTheOutageRetunesNothingAway() {
        screen(initial = ready())
        compose.runOnIdle {
            scope.launch { runtime.playLive(requireNotNull(currentLivePlaybackSelection(session.observation.value, ChannelId(1)))) }
        }
        assertMainSettled("the warm target is installed") { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        compose.settleFrames(10)
        val before = binds()
        compose.runOnIdle { visible.value = false }
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog)))
        compose.settleFrames(10)
        val generation = profiles.configurationGeneration.value
        compose.runOnIdle { scope.launch { profiles.saveServer("other.test", 9982) } }
        assertMainSettled("the in-memory profile save completes") { profiles.configurationGeneration.value > generation }
        session.publish(ready())
        compose.settleFrames(10)
        val retunes = mutableListOf<PlaybackTargetResult?>()
        compose.runOnIdle { scope.launch { retunes += runtime.retuneAfterReconnect() } }
        assertMainSettled("the retune returns its result") { retunes.isNotEmpty() }
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
        assertMainSettled("the warm target is installed") { runtime.activeTarget.value == AppPlaybackTarget.Live(ChannelId(1)) }
        compose.settleFrames(10)
        val before = binds()
        compose.runOnIdle { visible.value = false }
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog)))
        compose.settleFrames(10)
        session.publish(ready())
        compose.settleFrames(10)
        val retunes = mutableListOf<PlaybackTargetResult?>()
        compose.runOnIdle { scope.launch { retunes += runtime.retuneAfterReconnect() } }
        assertMainSettled("the retune returns its result") { retunes.isNotEmpty() }
        assertEquals(listOf(PlaybackTargetResult.STARTED), retunes)
        assertMainSettled("the completed retune has bound its stream") { binds() > before }
        compose.runOnIdle { visible.value = true }
        compose.settleFrames(10)
        assertEquals("the returning screen adopts the retuned target", before + 1, binds())
    }

    @Test fun aWaitWhoseConnectionRetriesByItselfOffersNoRetry() {
        screen()
        session.publish(ready())
        compose.settleFrames(10)
        compose.pressKey(Key.DirectionCenter)
        compose.settleFrames(3)
        assertEquals("Starting remains until a frame", 1, waits())
        compose.runOnIdle {
            session.publish(SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Stale(catalog)))
            connection.value = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
        }
        compose.settleFrames(10)
        assertEquals("an automatic retry starts with the channel wait, not error 110", 1, waits())
        compose.pressKey(Key.ChannelUp)
        assertEquals("Connecting consumes channel keys", AppPlaybackTarget.Live(ChannelId(1)), runtime.activeTarget.value)
        compose.onNode(hasText(string(R.string.error_110_title))).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(15_000L)
        compose.settleFrames(2)
        compose.onNode(hasText(string(R.string.error_110_title))).assertExists()
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Connection settings")).assertIsFocused()
        assertEquals(0, compose.onAllNodes(hasText("Retry"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasTestTag("tv-recovery-secondary"), useUnmergedTree = true).fetchSemanticsNodes().size)
        // Connection settings leaves through the screen's close.
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, closes)
    }

    @Test fun aWaitWhoseConnectionNeedsTheViewerOffersRetryFirst() {
        screen()
        compose.runOnIdle {
            connection.value = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.EXPLICIT_RETRY)
        }
        compose.settleFrames(10)
        compose.onNode(hasText(string(R.string.error_101_title))).assertExists()
        compose.onNodeWithTag("tv-recovery-primary").assert(hasText("Retry")).assertIsFocused()
        compose.onNodeWithTag("tv-recovery-secondary").assert(hasText("Connection settings"))
    }

    @Test fun standbyReturnWithNoConnectionWaitsInsteadOfShowingTheLiveAlert() {
        screen(initial = ready())
        assertMainSettled("the initial channel is installed") { runtime.activeTarget.value != null }
        compose.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            runtime.onAppBackgrounded(interactive = false)
        }
        assertMainSettled("standby releases the channel") { runtime.activeTarget.value == null }
        session.publish(SessionObservation.create(sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog)))
        compose.settleFrames(2)
        compose.runOnIdle {
            runtime.onAppForegrounded()
            connection.value = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE, SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        assertMainSettled("foreground begins a new channel start") { runtime.videoPresentation.value.channelStartAtMillis != null }
        compose.settleFrames(2)
        assertEquals(1, waits())
        compose.onNode(hasText(string(R.string.error_110_title))).assertDoesNotExist()
        compose.pressKey(Key.DirectionCenter)
        assertEquals(1, waits())
        val beforeReconnect = binds()
        session.publish(ready())
        compose.runOnIdle { connection.value = null }
        compose.settleFrames(10)
        assertEquals("the resumed channel is retuned exactly once when its session arrives", beforeReconnect + 1, binds())
        assertEquals("session readiness must not uncover a channel before its first frame", 1, waits())
        compose.onNode(hasText("Starting"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player-footer").assertDoesNotExist()
        compose.runOnIdle { playerListeners.toList().forEach { it.onRenderedFirstFrame() } }
        compose.settleFrames(2)
        assertNull(runtime.videoPresentation.value.channelStartAtMillis)
        assertEquals(0, waits())
        compose.pressKey(Key.Back)
        assertEquals(1, closes)
        assertNull(runtime.videoPresentation.value.channelStartAtMillis)
    }

    @Test fun aConnectionLostAfterTheChannelStartEndsKeepsTheExistingAlert() {
        screen(initial = ready())
        assertMainSettled("the live target is installed") { runtime.activeTarget.value != null }
        compose.runOnIdle {
            player.pause()
            playerListeners.toList().forEach { it.onPlaybackStateChanged(androidx.media3.common.Player.STATE_READY) }
            playerListeners.toList().forEach { it.onRenderedFirstFrame() }
        }
        assertNull(runtime.videoPresentation.value.channelStartAtMillis)
        org.junit.Assert.assertTrue(runtime.videoPresentation.value.visible)
        session.publish(SessionObservation.create(
            sessionState = SessionState.Connecting, channelState = ChannelRepositoryState.Stale(catalog),
        ))
        compose.settleFrames(4)
        assertEquals(0, waits())
        compose.onNode(hasText(string(R.string.error_110_title))).assertExists()
    }

    private val visible = MutableStateFlow(true)
    private val playerListeners = mutableListOf<androidx.media3.common.Player.Listener>()
    /** AppRoot's connection state; null derives it from the observation. */
    private val connection = mutableStateOf<ConnectionUiState?>(null)

    private companion object {
        val LOGO = ArtworkId(1)
        val PROFILE = ServerProfile("tvheadend.test")
    }

    private fun waits() = compose.onAllNodes(hasTestTag(LivePlayerServerWaitTag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun binds() = session.calls.count { it == FakeSessionCall.BIND_LIVE_PLAYBACK }

    private fun ready() = SessionObservation.create(
        sessionState = SessionState.Ready(ServerCapabilities.create(streaming = CapabilityAccess.ALLOWED, dvrWrite = CapabilityAccess.ALLOWED)),
        channelState = ChannelRepositoryState.Current(catalog),
        epgState = EpgRepositoryState.Current(EpgSnapshot.create()),
        dvrState = DvrRepositoryState.Current(DvrSnapshot.create()),
    )

    private var beforeSettingsRead: suspend () -> Unit = {}

    @Test fun aConnectedViewerSelectionUsesThePlayersOwnOpeningState() {
        screen(initial = ready(), appInitiated = false)
        assertMainSettled("the viewer's channel is installed") { runtime.activeTarget.value != null }
        compose.settleFrames(4)
        assertEquals("a connected viewer selection must not show channel startup", 0, waits())
        assertNull(runtime.videoPresentation.value.channelStartAtMillis)
        assertEquals("the target still awaits its first frame", false, runtime.videoPresentation.value.visible)
    }

    @Test fun aDisconnectedViewerSelectionWaitsForItsFirstSessionAndBindsOnce() {
        screen(appInitiated = false)
        assertEquals("an in-app selection waits for its first session", 1, waits())
        assertNull(runtime.videoPresentation.value.channelStartAtMillis)
        assertEquals(0, binds())
        compose.onNode(hasText(string(R.string.error_110_title))).assertDoesNotExist()
        session.publish(ready())
        compose.settleFrames(10)
        assertMainSettled("the viewer selection installs once") { runtime.activeTarget.value != null }
        assertEquals(1, binds())
        assertEquals("a viewer selection has no Starting phase", 0, waits())
        compose.runOnIdle {
            session.publish(SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Stale(catalog)))
            connection.value = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE,
                SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
        }
        compose.settleFrames(4)
        compose.onNode(hasText(string(R.string.error_110_title))).assertExists()
    }

    @Test fun aRecoveryHandoffKeepsTheDeadlineAndItsFocusedAction() {
        val origin = mutableStateOf<Long?>(android.os.SystemClock.elapsedRealtime() - 5_000L)
        screen(startupOrigin = origin)
        compose.runOnIdle {
            connection.value = ConnectionUiState.Error(ConnectionFailureKind.UNREACHABLE,
                SessionRecoveryDisposition.AUTOMATIC_BACKOFF)
        }
        compose.mainClock.advanceTimeBy(10_000L)
        compose.settleFrames(2)
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        compose.runOnIdle { origin.value = null }
        compose.settleFrames(4)
        compose.onNodeWithTag("tv-recovery-primary").assertIsFocused()
        assertEquals(0, waits())
    }

    private fun screen(
        initial: SessionObservation = SessionObservation.create(
            sessionState = SessionState.Connecting,
            channelState = ChannelRepositoryState.Stale(catalog),
        ),
        // The profile the observation is data of, with saved logos and channel one's icon.
        profile: ServerProfile? = null,
        opener: SubscriptionOpener = SubscriptionOpener { _, _, _ -> awaitCancellation() },
        startupOrigin: androidx.compose.runtime.State<Long?>? = null,
        appInitiated: Boolean = true,
    ) {
        // A pending first frame needs a held stream, not the fake's immediate end-of-input failure.
        session = FakeTvheadendSession(initial).apply {
            scriptLivePlaybackSuccess(opener)
        }
        if (profile != null) {
            session.artwork.scriptCached(profile)
            session.publish(SessionObservation.create(sessionState = SessionState.Connecting,
                channelState = ChannelRepositoryState.Stale(ChannelCatalog.create((1..3L).map {
                    Channel.create(ChannelId(it), name = "Name $it", number = it, icon = LOGO.takeIf { _ -> it == 1L }?.let { ChannelIcon.ImageCache(it) })
                }))), profile)
        }
        val data = InMemoryPreferencesDataStore()
        val settings = PlayerSettingsStore(object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by data {
            override val data = data.data.onStart { beforeSettingsRead() }
        })
        profiles = AppProfileOwner(session, FakeServerProfileStore(), settings, Dispatchers.Main.immediate,
            readProfileForEditing = { ServerProfileEditReadResult.Missing })
        scope.launch { profiles.run() }
        val channels = ChannelsViewModel(session, ChannelTagSettingsStore(InMemoryPreferencesDataStore()), profiles.liveProfile)
        models.put("channels", channels)
        assertMainSettled("the fake channel catalog is collected") { channels.scope.value.scope.visibleChannels.size == 3 }
        player = ExoPlayer.Builder(context).build()
        val coordinator = createTvheadendPlaybackCoordinator(player).also { it.launchIn(scope) }
        val controlled = at.bernhardberger.tvhplayer.testutil.observePlayerListeners(player, playerListeners)
        runtime = AppPlaybackRuntime(controlled, session, coordinator, settings, profiles, scope,
            TvheadendAudioOutputProvider(context), PlaybackAudioFocus.None, PlaybackRuntimePolicy.fromPlayerSettings(),
            noticeCenter = at.bernhardberger.tvhplayer.notices.NoticeCenter({ 0L }) { at.bernhardberger.tvhplayer.notices.NoticeContext(0, null) })
        val video = VideoPlayerViewModel(runtime, session)
        models.put("video", video)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val notices = NoticeCenter(android.os.SystemClock::elapsedRealtime) {
            NoticeContext(profiles.configurationGeneration.value, session.observation.value.currentSession?.generationIdentity)
        }
        val statsStore = at.bernhardberger.tvhplayer.di.PlayerStatsStore()
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TVHeadendPlayerTheme { ProvideAppArtwork(session) {
                    Box(Modifier.onKeyEvent { unconsumed++; false }) {
                        if (visible.collectAsState().value) {
                            VideoPlayerScreen(video, ChannelSelectionStore(), LastPlayedChannelStore(context), TargetAspect(AspectRatioMode.FIT) {},
                                channels, session, ChannelId(1), "Name 1", {},
                                { closes++ }, runtime, notices = notices, uiSettingsStore = UiSettingsStore(data),
                                connectionUiState = connection.value, statsStore = statsStore,
                                startupTarget = if (appInitiated) at.bernhardberger.tvhplayer.core.ApplianceLaunchTarget(
                                    at.bernhardberger.tvhplayer.core.ApplianceLaunchRequest(1), ChannelId(1), "Name 1") else null,
                                startupChannelStartAtMillis = startupOrigin?.value)
                        }
                    }
                } }
            }
        }
        compose.settleFrames(10)
        if (initial.currentSession == null && appInitiated) compose.waitUntil(10_000) { waits() == 1 }
    }

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)
}
